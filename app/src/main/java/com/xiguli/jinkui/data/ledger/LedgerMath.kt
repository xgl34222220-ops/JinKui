package com.xiguli.jinkui.data.ledger

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 一个月（或任意一段流水）的收支汇总；转账不计入收支。 */
data class Summary(val expense: Long, val income: Long) {
    val balance: Long get() = income - expense

    companion object {
        val ZERO = Summary(0L, 0L)
    }
}

/** 某一天的流水与当日收支。 */
data class DayGroup(
    val date: LocalDate,
    val items: List<LedgerTransaction>,
    val summary: Summary,
)

/** 分类占比里的一项：子分类已经并入所属的一级分类。[categoryId] 为空串表示“未分类”。 */
data class CategoryShare(
    val categoryId: String,
    val amount: Long,
    val ratio: Float,
    val count: Int,
)

data class DailyAllowance(val daysLeft: Int, val perDay: Long)

data class MonthPoint(val month: YearMonth, val summary: Summary)

data class BudgetProgress(
    val budget: LedgerBudget,
    val spent: Long,
) {
    val remaining: Long get() = budget.amount - spent
    val ratio: Float get() = Money.ratio(spent, budget.amount)
    val over: Boolean get() = spent > budget.amount
}

/** 月度视图的筛选条件。 */
data class TxFilter(
    val accountId: String? = null,
    /** 选中一级分类时，其下的子分类一并算进来。 */
    val categoryId: String? = null,
    /** 在备注与标签里搜索。 */
    val query: String = "",
) {
    val isActive: Boolean get() = accountId != null || categoryId != null || query.isNotBlank()
}

/** 账本的全部计算。纯函数，不碰数据库与界面，便于测试，也方便放到后台线程里跑。 */
object LedgerMath {

    /** 每个账户的当前余额 = 期初余额 + 收入 − 支出 ± 转账。没有对应账户的流水忽略。 */
    fun balances(accounts: List<LedgerAccount>, transactions: List<LedgerTransaction>): Map<String, Long> {
        val result = HashMap<String, Long>(accounts.size * 2)
        for (account in accounts) result[account.id] = account.openingBalance
        for (tx in transactions) {
            when (tx.type) {
                TxType.EXPENSE -> result.computeIfPresent(tx.accountId) { _, v -> v - tx.amount }
                TxType.INCOME -> result.computeIfPresent(tx.accountId) { _, v -> v + tx.amount }
                TxType.TRANSFER -> {
                    result.computeIfPresent(tx.accountId) { _, v -> v - tx.amount }
                    val to = tx.toAccountId
                    if (to != null) result.computeIfPresent(to) { _, v -> v + tx.amount }
                }
            }
        }
        return result
    }

    /** 净资产：所有账户余额之和（信用卡欠款为负）。 */
    fun netWorth(balances: Map<String, Long>): Long = balances.values.sum()

    /** [month] 在 [zone] 时区里的起止时间：[start, end)。 */
    fun monthRange(month: YearMonth, zone: ZoneId): LongRange {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }

    fun monthOf(time: Long, zone: ZoneId): YearMonth = YearMonth.from(Instant.ofEpochMilli(time).atZone(zone))

    fun dateOf(time: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun inMonth(transactions: List<LedgerTransaction>, month: YearMonth, zone: ZoneId): List<LedgerTransaction> {
        val range = monthRange(month, zone)
        return transactions.filter { it.occurredAt in range }
    }

    fun summary(transactions: List<LedgerTransaction>): Summary {
        var expense = 0L
        var income = 0L
        for (tx in transactions) {
            when (tx.type) {
                TxType.EXPENSE -> expense += tx.amount
                TxType.INCOME -> income += tx.amount
                TxType.TRANSFER -> Unit
            }
        }
        return Summary(expense, income)
    }

    /** 一个分类自身加上它的全部子分类。 */
    fun withChildren(categoryId: String, categories: List<LedgerCategory>): Set<String> =
        buildSet {
            add(categoryId)
            categories.filter { it.parentId == categoryId }.forEach { add(it.id) }
        }

    /** 子分类返回其一级分类，一级分类返回自己；找不到时原样返回。 */
    fun topLevelOf(categoryId: String, byId: Map<String, LedgerCategory>): String =
        byId[categoryId]?.parentId?.takeIf { byId.containsKey(it) } ?: categoryId

    fun filter(
        transactions: List<LedgerTransaction>,
        filter: TxFilter,
        categories: List<LedgerCategory>,
    ): List<LedgerTransaction> {
        if (!filter.isActive) return transactions
        val categoryIds = filter.categoryId?.let { withChildren(it, categories) }
        val tokens = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return transactions.filter { tx ->
            (filter.accountId == null || tx.accountId == filter.accountId || tx.toAccountId == filter.accountId) &&
                (categoryIds == null || tx.categoryId in categoryIds) &&
                (tokens.isEmpty() || matches(tx, tokens))
        }
    }

    private fun matches(tx: LedgerTransaction, tokens: List<String>): Boolean {
        val haystack = buildString {
            append(tx.note).append('\n')
            tx.tags.forEach { append(it).append('\n') }
        }.lowercase()
        return tokens.all { haystack.contains(it) }
    }

    /** 按天分组，日期新的在前；同一天里时间新的在前。 */
    fun groupByDay(transactions: List<LedgerTransaction>, zone: ZoneId): List<DayGroup> =
        transactions
            .groupBy { dateOf(it.occurredAt, zone) }
            .map { (date, items) ->
                val sorted = items.sortedWith(compareByDescending<LedgerTransaction> { it.occurredAt }.thenByDescending { it.createdAt })
                DayGroup(date, sorted, summary(sorted))
            }
            .sortedByDescending { it.date }

    /** 指定类型（支出或收入）按一级分类汇总，金额大的在前。 */
    fun categoryBreakdown(
        transactions: List<LedgerTransaction>,
        categories: List<LedgerCategory>,
        type: TxType,
    ): List<CategoryShare> {
        val byId = categories.associateBy { it.id }
        val amounts = LinkedHashMap<String, Long>()
        val counts = HashMap<String, Int>()
        var total = 0L
        for (tx in transactions) {
            if (tx.type != type) continue
            val key = tx.categoryId?.let { topLevelOf(it, byId) }?.takeIf { byId.containsKey(it) } ?: ""
            amounts[key] = (amounts[key] ?: 0L) + tx.amount
            counts[key] = (counts[key] ?: 0) + 1
            total += tx.amount
        }
        return amounts.entries
            .map { (id, amount) -> CategoryShare(id, amount, Money.ratio(amount, total), counts[id] ?: 0) }
            .sortedByDescending { it.amount }
    }

    /** 截至 [end]（含）的最近 [months] 个月的收支，按时间先后排列。 */
    fun trend(transactions: List<LedgerTransaction>, end: YearMonth, months: Int, zone: ZoneId): List<MonthPoint> {
        val first = end.minusMonths((months - 1).toLong())
        val sums = HashMap<YearMonth, Pair<Long, Long>>()
        val range = monthRange(first, zone).first until monthRange(end, zone).last + 1
        for (tx in transactions) {
            if (tx.type == TxType.TRANSFER || tx.occurredAt !in range) continue
            val month = monthOf(tx.occurredAt, zone)
            val (expense, income) = sums[month] ?: (0L to 0L)
            sums[month] = if (tx.type == TxType.EXPENSE) (expense + tx.amount) to income else expense to (income + tx.amount)
        }
        return (0 until months).map { offset ->
            val month = first.plusMonths(offset.toLong())
            val (expense, income) = sums[month] ?: (0L to 0L)
            MonthPoint(month, Summary(expense, income))
        }
    }

    /** 环比变化：(本期 − 上期) / 上期。上期为 0 时无法计算，返回 null。 */
    fun change(current: Long, previous: Long): Float? =
        if (previous == 0L) null else (current - previous).toFloat() / previous.toFloat()

    /**
     * 预算执行情况：总预算统计当月全部支出；分类预算统计该分类及其子分类的支出。
     * 金额为 0 的预算视为未设置。返回顺序：总预算在前，其余按 [budgets] 原顺序。
     */
    fun budgetProgress(
        budgets: List<LedgerBudget>,
        monthTransactions: List<LedgerTransaction>,
        categories: List<LedgerCategory>,
    ): List<BudgetProgress> {
        val expenses = monthTransactions.filter { it.type == TxType.EXPENSE }
        return budgets
            .filter { it.amount > 0 }
            .sortedBy { if (it.categoryId == null) 0 else 1 }
            .map { budget ->
                val spent = if (budget.categoryId == null) {
                    expenses.sumOf { it.amount }
                } else {
                    val ids = withChildren(budget.categoryId, categories)
                    expenses.filter { it.categoryId in ids }.sumOf { it.amount }
                }
                BudgetProgress(budget, spent)
            }
    }

    /** 一次遍历算出每个账户被多少笔流水引用（转入、转出都算；同一笔转进转出同一账户只算一次）。 */
    fun usageCounts(transactions: List<LedgerTransaction>): Map<String, Int> {
        val result = HashMap<String, Int>()
        for (tx in transactions) {
            result.merge(tx.accountId, 1, Int::plus)
            val to = tx.toAccountId
            if (to != null && to != tx.accountId) result.merge(to, 1, Int::plus)
        }
        return result
    }

    /** 流水里最晚的月份；没有流水时为 null。用来允许月份切换翻到“将来”的记录。 */
    fun latestMonth(transactions: List<LedgerTransaction>, zone: ZoneId): YearMonth? =
        transactions.maxOfOrNull { it.occurredAt }?.let { monthOf(it, zone) }

    /**
     * 本月剩余每天可用的预算：剩余额度 ÷ 含今天在内的剩余天数（向下取整到分）。
     * 只对“今天所在的月份”有意义，其余月份、已超支或没有剩余时返回 null。
     */
    fun dailyAllowance(progress: BudgetProgress, month: YearMonth, today: LocalDate): DailyAllowance? {
        if (YearMonth.from(today) != month) return null
        val remaining = progress.remaining
        if (remaining <= 0L) return null
        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        return DailyAllowance(daysLeft, remaining / daysLeft)
    }

    /** 某个账户被多少笔流水引用（转入、转出都算）。为 0 才允许删除。 */
    fun usageOf(accountId: String, transactions: List<LedgerTransaction>): Int =
        transactions.count { it.accountId == accountId || it.toAccountId == accountId }

    /** 保存前的校验；通过时返回 null，否则返回给用户看的原因。 */
    fun validate(tx: LedgerTransaction, accounts: List<LedgerAccount>): String? {
        val ids = accounts.map { it.id }.toSet()
        return when {
            tx.amount <= 0L -> "金额要大于 0"
            tx.amount > Money.MAX_CENTS -> "金额太大了"
            tx.accountId !in ids -> "请选择账户"
            tx.type == TxType.TRANSFER && tx.toAccountId == null -> "请选择转入账户"
            tx.type == TxType.TRANSFER && tx.toAccountId !in ids -> "请选择转入账户"
            tx.type == TxType.TRANSFER && tx.toAccountId == tx.accountId -> "转出和转入不能是同一个账户"
            tx.note.length > 500 -> "备注太长了"
            else -> null
        }
    }

    /** 标签输入："早饭 公司，报销" → ["早饭", "公司", "报销"]，去重并去掉开头的 #。 */
    fun parseTags(text: String): List<String> =
        text.split(Regex("[\\s,，、;；]+"))
            .map { it.trim().trimStart('#', '＃') }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(12)
}

/** 导入备份时账本的合并结果：[toWrite] 是需要写进本机的记录，其余保持不动。 */
class LedgerMergePlan(
    val toWrite: LedgerData,
    val added: Int,
    val updated: Int,
    val skipped: Int,
)

/**
 * 账本合并规则，与卡片一致：本机没有的记录直接加入；两边都有时保留更新时间较新的一份；
 * 不会删除本机已有的任何记录。同一份备份里重复的 ID 以更新时间最新的为准。
 */
object LedgerMerge {
    fun plan(local: LedgerData, incoming: LedgerData): LedgerMergePlan {
        var added = 0
        var updated = 0
        var skipped = 0
        fun <T> pick(local: List<T>, incoming: List<T>, id: (T) -> String, time: (T) -> Long): List<T> {
            val existing = local.associateBy(id)
            val latest = incoming.groupBy(id).values.map { group -> group.maxBy(time) }
            return latest.filter { item ->
                val current = existing[id(item)]
                when {
                    current == null -> { added++; true }
                    time(current) >= time(item) -> { skipped++; false }
                    else -> { updated++; true }
                }
            }
        }
        val write = LedgerData(
            accounts = pick(local.accounts, incoming.accounts, { it.id }, { it.updatedAt }),
            categories = pick(local.categories, incoming.categories, { it.id }, { it.updatedAt }),
            transactions = pick(local.transactions, incoming.transactions, { it.id }, { it.updatedAt }),
            budgets = pick(local.budgets, incoming.budgets, { it.id }, { it.updatedAt }),
        )
        return LedgerMergePlan(write, added, updated, skipped)
    }
}
