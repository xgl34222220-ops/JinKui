package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 周期记账的频率。[key] 写入加密数据与备份，上线后不要改动。 */
enum class RecurFrequency(val key: String, val label: String) {
    DAILY("daily", "每天"),
    WEEKLY("weekly", "每周"),
    MONTHLY("monthly", "每月"),
    YEARLY("yearly", "每年");

    companion object {
        fun of(key: String?): RecurFrequency = entries.firstOrNull { it.key == key } ?: MONTHLY
    }
}

/**
 * 一条周期记账规则：从 [startDate] 起按 [frequency] 重复，在每次的 [timeOfDay] 记下一笔 [template] 那样的流水。
 *
 * 生成在“解锁 / 打开账本”时进行，不需要后台任务或网络。[nextIndex] 是下一次要生成的序号：
 * 第 n 次的日期永远由 [startDate] 加 n 个周期算出（而不是在上一次的基础上累加），
 * 所以 1 月 31 日起的每月规则会落在 2 月 28 日、3 月 31 日……不会越漂越早。
 *
 * 生成出的流水 ID 由规则 ID 和日期决定（[RecurringMath.transactionId]），
 * 同一天绝不会生成两笔；两台手机互相导入备份时也会按 ID 合并，不会重复。
 */
data class LedgerRecurring(
    val id: String,
    val type: TxType,
    val amount: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val frequency: RecurFrequency = RecurFrequency.MONTHLY,
    val startDate: LocalDate,
    /** 一天中的分钟数（0..1439）。 */
    val minuteOfDay: Int = 9 * 60,
    /** 含当天；null 表示一直重复。 */
    val endDate: LocalDate? = null,
    val nextIndex: Int = 0,
    val paused: Boolean = false,
    val createdAt: Long = 0L,
    /** 用户最后一次修改规则的时间。生成流水推进 [nextIndex] 时不改它，避免合并时把别处的修改盖掉。 */
    val updatedAt: Long = 0L,
) {
    val timeOfDay: LocalTime get() = LocalTime.of(minuteOfDay.coerceIn(0, 1439) / 60, minuteOfDay.coerceIn(0, 1439) % 60)
}

/** 一次待生成的流水。 */
data class RecurOccurrence(val index: Int, val date: LocalDate, val occurredAt: Long)

/** 一条规则这次要生成的流水与推进后的 [nextIndex]。 */
data class RecurPlan(val occurrences: List<RecurOccurrence>, val nextIndex: Int, val truncated: Boolean)

/** 一次生成的结果：要新写入的流水，以及推进了进度的规则（[LedgerRecurring.updatedAt] 不变）。 */
data class RecurBatch(val transactions: List<LedgerTransaction>, val rules: List<LedgerRecurring>, val truncated: Boolean) {
    val isEmpty: Boolean get() = rules.isEmpty()
}

/** 周期记账的全部计算，纯函数，便于测试。 */
object RecurringMath {
    /** 一次解锁最多补记的笔数（每条规则），防止起始日期填得很早时一次写入过多。 */
    const val MAX_PER_RUN = 400

    /** 绝对上限：再往后的序号不再生成（每天一次也够 270 多年）。 */
    const val MAX_INDEX = 100_000

    /**
     * 根据账本现状算出这次要生成的全部流水。已存在的 ID（之前生成过、或从别的手机导入过）一律跳过，
     * 所以重复调用、并发调用、导入备份后再调用都不会出现重复的流水。
     */
    fun generate(data: LedgerData, now: Long, zone: ZoneId): RecurBatch {
        val existing = data.transactions.mapTo(HashSet()) { it.id }
        // 用户删掉的生成流水不再生成；被删除的规则（例如从旧备份里带回来的）不再生成任何流水
        val deadRules = HashSet<String>()
        for (t in data.tombstones) {
            if (t.kind == LedgerTombstone.KIND_RECURRING) deadRules += t.id else existing += t.id
        }
        val transactions = ArrayList<LedgerTransaction>()
        val rules = ArrayList<LedgerRecurring>()
        var truncated = false
        for (rule in data.recurring) {
            if (rule.id in deadRules) continue
            if (blockedReason(rule, data.accounts) != null) continue
            val plan = due(rule, now, zone)
            truncated = truncated || plan.truncated
            if (plan.nextIndex == rule.nextIndex) continue
            for (occurrence in plan.occurrences) {
                val tx = transactionFor(rule, occurrence, now)
                if (existing.add(tx.id)) transactions += tx
            }
            rules += rule.copy(nextIndex = plan.nextIndex)
        }
        return RecurBatch(transactions, rules, truncated)
    }

    /**
     * 保存规则时的 [LedgerRecurring.nextIndex]：新规则从起始日期开始（起始日期早于今天时会补记过去的各期）；
     * 只改了金额、备注等内容时保持原进度；改了起始日期、频率或时间时，从“上一次已生成的日期之后”继续，
     * 不会把已经记过的日子再记一遍。
     */
    fun indexAfterEdit(previous: LedgerRecurring?, edited: LedgerRecurring): Int {
        if (previous == null) return 0
        val scheduleChanged = previous.startDate != edited.startDate ||
            previous.frequency != edited.frequency ||
            previous.minuteOfDay != edited.minuteOfDay
        if (!scheduleChanged) return previous.nextIndex
        val lastGenerated = if (previous.nextIndex > 0) dateOf(previous, previous.nextIndex - 1) else null
        return resumeIndex(edited, lastGenerated)
    }

    /** 第 [index] 次的日期；月末与 2 月 29 日会落到当月最后一天。 */
    fun dateOf(rule: LedgerRecurring, index: Int): LocalDate {
        val n = index.toLong()
        return when (rule.frequency) {
            RecurFrequency.DAILY -> rule.startDate.plusDays(n)
            RecurFrequency.WEEKLY -> rule.startDate.plusWeeks(n)
            RecurFrequency.MONTHLY -> rule.startDate.plusMonths(n)
            RecurFrequency.YEARLY -> rule.startDate.plusYears(n)
        }
    }

    /** 第 [index] 次在 [zone] 里的时刻。夏令时跳过的时刻按 java.time 规则顺延。 */
    fun instantOf(rule: LedgerRecurring, index: Int, zone: ZoneId): Long =
        LocalDateTime.of(dateOf(rule, index), rule.timeOfDay).atZone(zone).toInstant().toEpochMilli()

    /**
     * 截至 [now]（含）还没生成的各次。暂停、已结束的规则返回空。
     * 每次最多 [limit] 笔；超出时 [RecurPlan.truncated] 为 true，剩下的下次解锁接着补。
     */
    fun due(rule: LedgerRecurring, now: Long, zone: ZoneId, limit: Int = MAX_PER_RUN): RecurPlan {
        if (rule.paused) return RecurPlan(emptyList(), rule.nextIndex, false)
        val result = ArrayList<RecurOccurrence>()
        var index = rule.nextIndex.coerceAtLeast(0)
        while (index < MAX_INDEX) {
            val date = dateOf(rule, index)
            if (rule.endDate != null && date > rule.endDate) break
            val at = instantOf(rule, index, zone)
            if (at > now) break
            if (result.size >= limit) return RecurPlan(result, index, true)
            result += RecurOccurrence(index, date, at)
            index++
        }
        return RecurPlan(result, index, false)
    }

    /** 下一次（还没生成的）日期；规则已结束时为 null。 */
    fun nextDate(rule: LedgerRecurring): LocalDate? {
        if (rule.nextIndex >= MAX_INDEX) return null
        val date = dateOf(rule, rule.nextIndex)
        return if (rule.endDate != null && date > rule.endDate) null else date
    }

    /**
     * 列表上“下次”显示的日期：还没生成的各次里第一个不早于 [today] 的日期；规则已结束时为 null。
     *
     * 1.1 及之前直接显示 [nextDate]，新建的规则在第一次生成之前（或暂停、账户停用时）会显示一个已经过去的开始日期，
     * 例如“下次 9月1日”。过去的那几次会在下次解锁时补记，真正的“下一次”是今天或以后。
     */
    fun upcomingDate(rule: LedgerRecurring, today: LocalDate): LocalDate? {
        var index = rule.nextIndex.coerceAtLeast(0)
        if (index >= MAX_INDEX) return null
        if (dateOf(rule, index) < today) index = maxOf(index, resumeIndex(rule, today.minusDays(1)))
        if (index >= MAX_INDEX) return null
        val date = dateOf(rule, index)
        return if (rule.endDate != null && date > rule.endDate) null else date
    }

    /**
     * 修改了起始日期或频率之后的 [LedgerRecurring.nextIndex]：
     * 从新的规则里第一个“晚于 [after]”的日期继续，不会把已经过去的日子再补记一遍。
     * [after] 一般是今天之前最后一次已生成的日期；从没生成过时传 null，从起始日期开始。
     */
    fun resumeIndex(rule: LedgerRecurring, after: LocalDate?): Int {
        if (after == null) return 0
        if (rule.startDate > after) return 0
        // 粗估再逐个前进，避免从 0 开始循环上万次
        val approx = when (rule.frequency) {
            RecurFrequency.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(rule.startDate, after)
            RecurFrequency.WEEKLY -> java.time.temporal.ChronoUnit.WEEKS.between(rule.startDate, after)
            RecurFrequency.MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(rule.startDate, after)
            RecurFrequency.YEARLY -> java.time.temporal.ChronoUnit.YEARS.between(rule.startDate, after)
        }.coerceIn(0L, MAX_INDEX.toLong()).toInt()
        var index = (approx - 1).coerceAtLeast(0)
        while (index < MAX_INDEX && dateOf(rule, index) <= after) index++
        return index
    }

    private val DAY = DateTimeFormatter.BASIC_ISO_DATE

    /** 生成流水的 ID：规则 ID + 日期。同一规则同一天只会有这一个 ID。 */
    fun transactionId(ruleId: String, date: LocalDate): String = "r${date.format(DAY)}-$ruleId".take(64)

    private val GENERATED_ID = Regex("r\\d{8}-[A-Za-z0-9-]+")

    /** 是不是周期记账生成的流水 ID（手动记的流水是 UUID，不会以 r + 8 位日期开头）。 */
    fun isGeneratedId(id: String): Boolean = GENERATED_ID.matches(id)

    /** 按规则生成一笔流水（时间戳由调用方给出，以便测试）。 */
    fun transactionFor(rule: LedgerRecurring, occurrence: RecurOccurrence, now: Long): LedgerTransaction =
        LedgerTransaction(
            id = transactionId(rule.id, occurrence.date),
            type = rule.type,
            amount = rule.amount,
            occurredAt = occurrence.occurredAt,
            accountId = rule.accountId,
            toAccountId = if (rule.type == TxType.TRANSFER) rule.toAccountId else null,
            categoryId = if (rule.type == TxType.TRANSFER) null else rule.categoryId,
            note = rule.note,
            tags = rule.tags,
            createdAt = now,
            updatedAt = now,
        )

    /**
     * 规则不能生成时的原因（账户被停用或删除等）；可以生成时返回 null。
     * 不能生成的规则不推进 [LedgerRecurring.nextIndex]，账户恢复后再补上。
     */
    fun blockedReason(rule: LedgerRecurring, accounts: List<LedgerAccount>): String? {
        val byId = accounts.associateBy { it.id }
        val from = byId[rule.accountId]
        return when {
            rule.amount <= 0L || rule.amount > Money.MAX_CENTS -> "金额无效"
            from == null -> "账户已删除"
            from.archived -> "账户「${from.name}」已停用"
            rule.type == TxType.TRANSFER && (rule.toAccountId == null || byId[rule.toAccountId] == null) -> "转入账户已删除"
            rule.type == TxType.TRANSFER && byId[rule.toAccountId]?.archived == true -> "转入账户已停用"
            rule.type == TxType.TRANSFER && rule.toAccountId == rule.accountId -> "转出和转入是同一个账户"
            else -> null
        }
    }

    /** 保存规则前的校验；通过时返回 null。 */
    fun validate(rule: LedgerRecurring, accounts: List<LedgerAccount>): String? {
        val probe = LedgerTransaction(
            id = "probe", type = rule.type, amount = rule.amount, occurredAt = 0L,
            accountId = rule.accountId, toAccountId = rule.toAccountId, categoryId = rule.categoryId, note = rule.note,
        )
        LedgerMath.validate(probe, accounts)?.let { return it }
        if (rule.endDate != null && rule.endDate < rule.startDate) return "结束日期不能早于开始日期"
        if (rule.minuteOfDay !in 0..1439) return "时间无效"
        return null
    }

    /** 列表上的一句说明："每月 15 日 09:00"、"每周一 08:30"、"每年 3 月 1 日"。 */
    fun describe(rule: LedgerRecurring): String {
        val time = "%02d:%02d".format(rule.minuteOfDay / 60, rule.minuteOfDay % 60)
        val d = rule.startDate
        return when (rule.frequency) {
            RecurFrequency.DAILY -> "每天 $time"
            RecurFrequency.WEEKLY -> "每" + WEEKDAYS[d.dayOfWeek.value - 1] + " $time"
            RecurFrequency.MONTHLY -> if (d.dayOfMonth >= 29) "每月 ${d.dayOfMonth} 日（小月为月末） $time" else "每月 ${d.dayOfMonth} 日 $time"
            RecurFrequency.YEARLY -> "每年 ${d.monthValue} 月 ${d.dayOfMonth} 日 $time"
        }
    }

    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
}
