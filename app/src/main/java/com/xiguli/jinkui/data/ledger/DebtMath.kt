package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 一期已出的账单。金额都是正数（分）。 */
data class CreditStatement(
    /** 账单日（这一期的最后一天，当天的消费算在这一期）。 */
    val statementDate: LocalDate,
    /** 这一期的第一天（上一期账单日的次日）。 */
    val cycleStart: LocalDate,
    val dueDate: LocalDate,
    /** 应还：出账时的欠款（含上期没还清的），扣掉还没到期的分期。 */
    val owed: Long,
    /** 已还：出账之后到下一期出账之间的还款 / 退款，最多算到应还。 */
    val paid: Long,
    /** 这一期新增的消费（不含分期本身，含这一期入账的分期）。 */
    val charges: Long,
    /** 其中这一期入账的分期本金与手续费。 */
    val installmentPart: Long,
) {
    val remaining: Long get() = owed - paid
    val cleared: Boolean get() = remaining <= 0L
    fun overdue(today: LocalDate): Boolean = !cleared && today > dueDate
}

/** 本期未出账单：从上期账单日次日到下一个账单日。 */
data class OpenCycle(val start: LocalDate, val statementDate: LocalDate, val dueDate: LocalDate, val amount: Long, val installmentPart: Long)

/** 分期的一期。 */
data class InstallmentPeriod(
    val index: Int,
    val principal: Long,
    val fee: Long,
    /** 实际入账日。 */
    val billingDate: LocalDate,
    /** 计入哪一期账单（账单日）。 */
    val statementDate: LocalDate,
    val dueDate: LocalDate,
    val paid: Boolean,
    val waived: Boolean,
) {
    val amount: Long get() = principal + fee
}

data class InstallmentView(
    val plan: LedgerInstallment,
    val periods: List<InstallmentPeriod>,
) {
    val paidCount: Int get() = periods.count { it.paid }
    val total: Int get() = plan.periods
    val settled: Boolean get() = paidCount >= total
    val earlyPaid: Boolean get() = plan.payoffDate != null
    val remaining: Long get() = periods.filter { !it.paid }.sumOf { it.amount }
    val next: InstallmentPeriod? get() = periods.firstOrNull { !it.paid }
}

/** 一个信用账户的账单视图。 */
data class CreditView(
    val account: LedgerAccount,
    val settings: CreditSettings?,
    /** 账户余额（欠款为负）。 */
    val balance: Long,
    /** 已用额度 = 当前欠款（含还没入账的分期）。 */
    val used: Long,
    /** 可用额度；没填额度时为 null。 */
    val available: Long?,
    val open: OpenCycle?,
    /** 已出账单，最新的在前（最多 [DebtMath.HISTORY] 期）。 */
    val statements: List<CreditStatement>,
    val installments: List<InstallmentView>,
    /** 今天消费的免息天数与最晚还款日。 */
    val freeDays: Int?,
    val freeUntil: LocalDate?,
    /** 最长免息天数（账单日次日消费）。 */
    val maxFreeDays: Int?,
) {
    val latest: CreditStatement? get() = statements.firstOrNull()
}

/** 借贷里的一笔流水（借出 / 借入 / 收款 / 还款 / 利息 / 免除）。 */
data class LoanEntry(val tx: LedgerTransaction, val kind: LoanEntryKind, val amount: Long)

enum class LoanEntryKind(val label: String) { PRINCIPAL("本金"), REPAY("还款"), INTEREST("利息"), WRITE_OFF("免除") }

data class LoanView(
    val loan: LedgerLoan,
    val account: LedgerAccount,
    /** 对方：账户名去掉“借出 · ”“借入 · ”前缀。 */
    val person: String,
    /** 落库了没有（合成的“导入的借贷”为 false）。 */
    val stored: Boolean,
    val principal: Long,
    val repaid: Long,
    val interestPaid: Long,
    val entries: List<LedgerTransaction>,
    val lastActivity: LocalDate,
) {
    val direction: LoanDirection get() = loan.direction
    val remaining: Long get() = (principal - repaid).coerceAtLeast(0L)
    val interestRemaining: Long get() = (loan.interest - interestPaid).coerceAtLeast(0L)
    val settled: Boolean get() = principal > 0L && principal - repaid <= 0L
    fun overdue(today: LocalDate): Boolean = !settled && loan.dueDate != null && today > loan.dueDate
}

enum class DueKind { CREDIT, CREDIT_OPEN, INSTALLMENT, LOAN }

/** 待还 / 待收列表里的一项。 */
data class DueItem(
    val key: String,
    val kind: DueKind,
    val title: String,
    val subtitle: String,
    val amount: Long,
    val dueDate: LocalDate,
    val overdue: Boolean,
    /** 我要还（信用账单、借入）为 true；别人还我（借出）为 false。 */
    val payable: Boolean,
    /** 计入“本月待还”与提醒；分期（已经含在信用账单里）与未出账单只是提示。 */
    val counts: Boolean,
    val accountId: String,
    val debtId: String? = null,
)

data class PersonTotal(val person: String, val receivable: Long, val payable: Long, val open: Int)

data class DebtOverview(
    val today: LocalDate,
    /** 本月待还（含已逾期的）。 */
    val monthDue: Long,
    val overdue: Long,
    val monthReceivable: Long,
    val items: List<DueItem>,
    val credits: List<CreditView>,
    val loans: List<LoanView>,
    val receivable: Long,
    val payable: Long,
    val people: List<PersonTotal>,
) {
    /** 3 天内到期或已逾期、还没还清的（首页提示条、系统通知）。 */
    val reminders: List<DueItem> get() = items.filter { it.counts && it.amount > 0 && it.dueDate <= today.plusDays(DebtMath.REMIND_DAYS) }

    companion object {
        val EMPTY = DebtOverview(LocalDate.MIN, 0, 0, 0, emptyList(), emptyList(), emptyList(), 0, 0, emptyList())
    }
}

/**
 * 欠款 / 账单的全部计算（1.4 起）。纯函数，不碰数据库与界面。一木记账的 .xls 只有流水，没有账单日、还款日与分期，
 * 这里全部由账户设置与流水推出来：
 *
 *  - 某一天结束时的欠款 = −（期初余额 + 截至当天的全部收支与转账）；
 *  - 一期账单的应还 = 账单日当天结束时的欠款 − 还没入账的分期（本金 + 手续费），不小于 0；
 *    上期没还清的部分自然含在本期应还里（与银行的“账单金额”一致）；
 *  - 已还 = 账单日次日到下一个账单日之间转入 / 退回这个账户的钱，最多算到应还；
 *  - 分期的第 k 期在它的入账日所在的那一期账单里出现；账单还清后，这一期之前入账的各期都算已还。
 */
object DebtMath {
    /** 账单历史最多列出的期数。 */
    const val HISTORY = 24
    const val REMIND_DAYS = 3L

    /** 合成的“导入的借贷”的 ID：每个对方账户一笔。 */
    fun legacyLoanId(accountId: String): String = ("ln-$accountId").take(64)

    // ── 日期 ──────────────────────────────────────────────────────

    /** [month] 的账单日（月份没有这一天时取月末）。 */
    fun statementDate(month: YearMonth, settings: CreditSettings): LocalDate =
        month.atDay(minOf(settings.statementDay, month.lengthOfMonth()))

    /** 不晚于 [date] 的最近一个账单日。 */
    fun statementOnOrBefore(date: LocalDate, settings: CreditSettings): LocalDate {
        val month = YearMonth.from(date)
        val here = statementDate(month, settings)
        return if (here <= date) here else statementDate(month.minusMonths(1), settings)
    }

    /** 不早于 [date] 的最近一个账单日：[date] 这天的消费计入这一期。 */
    fun statementOnOrAfter(date: LocalDate, settings: CreditSettings): LocalDate {
        val month = YearMonth.from(date)
        val here = statementDate(month, settings)
        return if (here >= date) here else statementDate(month.plusMonths(1), settings)
    }

    fun nextStatement(statement: LocalDate, settings: CreditSettings): LocalDate =
        statementDate(YearMonth.from(statement).plusMonths(1), settings)

    /**
     * 某期账单的还款日。固定日：还款日大于账单日时在当月，否则在次月；月份没有这一天时取月末。
     * 出账后 N 天：账单日加 N 天。
     */
    fun dueDate(statement: LocalDate, settings: CreditSettings): LocalDate = when (settings.dueMode) {
        DueMode.AFTER_STATEMENT -> statement.plusDays(settings.dueDay.toLong())
        DueMode.DAY_OF_MONTH -> {
            val base = YearMonth.from(statement)
            val month = if (settings.dueDay > settings.statementDay) base else base.plusMonths(1)
            month.atDay(minOf(settings.dueDay, month.lengthOfMonth()))
        }
    }

    // ── 信用账户 ──────────────────────────────────────────────────

    /** 一笔流水对 [accountId] 余额的影响（正数让余额变大，即还款 / 退款）。 */
    fun effect(tx: LedgerTransaction, accountId: String): Long = when (tx.type) {
        TxType.EXPENSE -> if (tx.accountId == accountId) -tx.amount else 0L
        TxType.INCOME -> if (tx.accountId == accountId) tx.amount else 0L
        TxType.TRANSFER -> (if (tx.toAccountId == accountId) tx.amount else 0L) - (if (tx.accountId == accountId) tx.amount else 0L)
    }

    /** 按日期累计的余额变动：用于快速求“某天结束时的余额”和“某段时间里的转入”。 */
    private class Ledger(account: LedgerAccount, txs: List<LedgerTransaction>, zone: ZoneId) {
        val opening = account.openingBalance
        private val days: LongArray
        private val net: LongArray
        private val credits: LongArray

        init {
            val byDay = java.util.TreeMap<Long, LongArray>()
            for (tx in txs) {
                val e = effect(tx, account.id)
                if (e == 0L) continue
                val day = LedgerMath.dateOf(tx.occurredAt, zone).toEpochDay()
                val slot = byDay.getOrPut(day) { LongArray(2) }
                slot[0] += e
                if (e > 0) slot[1] += e
            }
            days = LongArray(byDay.size)
            net = LongArray(byDay.size)
            credits = LongArray(byDay.size)
            var i = 0
            var runNet = 0L
            var runCredit = 0L
            for ((day, slot) in byDay) {
                runNet += slot[0]
                runCredit += slot[1]
                days[i] = day
                net[i] = runNet
                credits[i] = runCredit
                i++
            }
        }

        val firstDay: LocalDate? get() = days.firstOrNull()?.let(LocalDate::ofEpochDay)

        /** 最后一个不晚于 [date] 的下标，没有时为 -1。 */
        private fun upTo(date: LocalDate): Int {
            val key = date.toEpochDay()
            var lo = 0
            var hi = days.size - 1
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (days[mid] <= key) { found = mid; lo = mid + 1 } else hi = mid - 1
            }
            return found
        }

        /** [date] 当天结束时的欠款（正数）。 */
        fun debtAt(date: LocalDate): Long {
            val i = upTo(date)
            return -(opening + if (i >= 0) net[i] else 0L)
        }

        /** (after, until] 之间转入 / 退回的合计。 */
        fun creditsBetween(after: LocalDate, until: LocalDate): Long {
            val a = upTo(after)
            val b = upTo(until)
            return (if (b >= 0) credits[b] else 0L) - (if (a >= 0) credits[a] else 0L)
        }

        /** (after, until] 之间的消费合计（正数）。 */
        fun chargesBetween(after: LocalDate, until: LocalDate): Long {
            val a = upTo(after)
            val b = upTo(until)
            val netDelta = (if (b >= 0) net[b] else 0L) - (if (a >= 0) net[a] else 0L)
            return creditsBetween(after, until) - netDelta
        }
    }

    /** [date] 当天结束时还没入账的分期（本金 + 手续费）。分期发生前（[LedgerInstallment.startDate] 之前）不算。 */
    fun unbilled(plans: List<LedgerInstallment>, date: LocalDate): Long = plans.sumOf { plan ->
        if (plan.startDate > date) 0L
        else (0 until plan.periods).sumOf { k -> if (plan.billingDate(k) > date) plan.amountOf(k) else 0L }
    }

    /** 入账日落在 (after, until] 里的分期金额。 */
    private fun billedBetween(plans: List<LedgerInstallment>, after: LocalDate, until: LocalDate): Long = plans.sumOf { plan ->
        (0 until plan.periods).sumOf { k ->
            val d = plan.billingDate(k)
            if (d > after && d <= until) plan.amountOf(k) else 0L
        }
    }

    /**
     * 一个信用账户的账单。[settings] 为空（还没设置账单日）时只给出余额与已用额度。
     * [today] 是用户当地的今天。
     */
    fun credit(
        account: LedgerAccount,
        transactions: List<LedgerTransaction>,
        installments: List<LedgerInstallment>,
        today: LocalDate,
        zone: ZoneId,
    ): CreditView {
        val txs = transactions.filter { it.accountId == account.id || it.toAccountId == account.id }
        val ledger = Ledger(account, txs, zone)
        val balance = -ledger.debtAt(LocalDate.MAX.minusDays(1))
        val used = (-balance).coerceAtLeast(0L)
        val settings = account.credit
        val limit = settings?.limit?.takeIf { it > 0 }
        val available = limit?.let { it - used }
        if (settings == null) {
            return CreditView(account, null, balance, used, available, null, emptyList(), emptyList(), null, null, null)
        }
        val plans = installments.filter { it.accountId == account.id }
        val planIds = plans.mapTo(HashSet()) { it.id }
        // 分期时一并记上的消费与手续费（日期, 金额）：它们由各期入账代替，不算进当期“新增消费”
        val planTxs = txs.filter { it.debtId in planIds && it.type == TxType.EXPENSE && it.accountId == account.id }
            .map { LedgerMath.dateOf(it.occurredAt, zone) to it.amount }
        val firstActivity = listOfNotNull(ledger.firstDay, plans.minOfOrNull { it.startDate })
            .minOrNull()
            ?: LedgerMath.dateOf(account.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(), zone).coerceAtMost(today)
        val lastClosed = statementOnOrBefore(today, settings)
        var first = statementOnOrAfter(firstActivity, settings)
        // 全部从第一笔流水算起（已还的分期期数要看所有已出账单），列表只显示最近 HISTORY 期
        val all = ArrayList<CreditStatement>()
        if (first <= lastClosed) {
            // 极端情况下（几十年的流水）也只往回算 600 期
            val floor = lastClosed.minusMonths(600)
            if (first < floor) first = statementOnOrAfter(floor, settings)
            var sd = first
            var previous = statementDate(YearMonth.from(first).minusMonths(1), settings)
            while (sd <= lastClosed) {
                val next = nextStatement(sd, settings)
                val owed = (ledger.debtAt(sd) - unbilled(plans, sd)).coerceAtLeast(0L)
                val paid = minOf(owed, ledger.creditsBetween(sd, minOf(next, today)))
                val installmentPart = billedBetween(plans, previous, sd)
                // 本期新增消费：流水里的消费减去分期发生时一次记上的消费与手续费，加上本期入账的各期
                val financed = planTxs.filter { (d, _) -> d > previous && d <= sd }.sumOf { it.second }
                val charges = (ledger.chargesBetween(previous, sd) - financed + installmentPart).coerceAtLeast(0L)
                all += CreditStatement(sd, previous.plusDays(1), dueDate(sd, settings), owed, paid, charges, installmentPart)
                previous = sd
                sd = next
            }
        }
        val latest = all.lastOrNull()
        val upcoming = nextStatement(lastClosed, settings)
        val openStart = lastClosed.plusDays(1)
        val openInstallments = billedBetween(plans, lastClosed, upcoming)
        val openAmount = (ledger.debtAt(today) - unbilled(plans, upcoming) - (latest?.remaining ?: 0L)).coerceAtLeast(0L)
        val open = OpenCycle(openStart, upcoming, dueDate(upcoming, settings), openAmount, openInstallments)
        val lastCleared = all.lastOrNull { it.cleared }?.statementDate
        val views = plans.sortedByDescending { it.firstDate }.map { plan ->
            InstallmentView(
                plan,
                (0 until plan.periods).map { k ->
                    val billing = plan.billingDate(k)
                    val sd = statementOnOrAfter(billing, settings)
                    InstallmentPeriod(
                        index = k,
                        principal = plan.principalOf(k),
                        fee = plan.feeOf(k),
                        billingDate = billing,
                        statementDate = sd,
                        dueDate = dueDate(sd, settings),
                        paid = lastCleared != null && sd <= lastCleared,
                        waived = plan.waived(k),
                    )
                },
            )
        }
        // 今天消费：计入今天所在的这一期（今天是账单日时就是今天这一期）
        val todayStatement = statementOnOrAfter(today, settings)
        val freeUntil = dueDate(todayStatement, settings)
        val bestStart = statementOnOrBefore(today, settings).plusDays(1)
        val maxFree = ChronoUnit.DAYS.between(bestStart, dueDate(statementOnOrAfter(bestStart, settings), settings)).toInt()
        return CreditView(
            account = account,
            settings = settings,
            balance = balance,
            used = used,
            available = available,
            open = open,
            statements = all.takeLast(HISTORY).asReversed().toList(),
            installments = views,
            freeDays = ChronoUnit.DAYS.between(today, freeUntil).toInt(),
            freeUntil = freeUntil,
            maxFreeDays = maxFree,
        )
    }

    // ── 借贷 ──────────────────────────────────────────────────────

    /** 账户名里的对方：“借出 · 小李” → “小李”。 */
    fun personOf(account: LedgerAccount): String =
        account.name.removePrefix("借出 · ").removePrefix("借入 · ").removePrefix("借出·").removePrefix("借入·").trim().ifEmpty { account.name }

    /**
     * 全部借贷。每个应收 / 应付账户上：带着某笔借贷 ID 的流水归那一笔；其余（导入的、期初余额、
     * 指向已经不存在的借贷的）归这个账户的“导入的借贷”，没有任何金额时不列出。
     */
    fun loans(data: LedgerData, zone: ZoneId): List<LoanView> {
        val loanAccounts = data.accounts.filter { LoanDirection.of(it.kind) != null }
        if (loanAccounts.isEmpty()) return emptyList()
        val byId = data.loans.associateBy { it.id }
        val result = ArrayList<LoanView>()
        val byDebt = data.transactions.groupBy { it.debtId }
        for (account in loanAccounts) {
            val direction = LoanDirection.of(account.kind)!!
            val sign = if (direction == LoanDirection.LEND) 1L else -1L
            val touching = data.transactions.filter { it.accountId == account.id || it.toAccountId == account.id }
            val ownLoans = data.loans.filter { it.accountId == account.id && !it.legacy }
            val ownIds = ownLoans.mapTo(HashSet()) { it.id }
            fun view(loan: LedgerLoan, stored: Boolean, entries: List<LedgerTransaction>, opening: Long): LoanView? {
                var principal = (opening * sign).coerceAtLeast(0L)
                var repaid = (-(opening * sign)).coerceAtLeast(0L)
                var interest = 0L
                for (tx in entries) {
                    val e = effect(tx, account.id) * sign
                    when {
                        e > 0 -> principal += e
                        e < 0 -> repaid += -e
                        else -> interest += if (tx.type != TxType.TRANSFER) tx.amount else 0L
                    }
                }
                if (!stored && principal == 0L && repaid == 0L) return null
                val dates = entries.map { LedgerMath.dateOf(it.occurredAt, zone) }
                return LoanView(
                    loan = if (stored) loan else loan.copy(date = dates.minOrNull() ?: loan.date),
                    account = account,
                    person = personOf(account),
                    stored = stored,
                    principal = principal,
                    repaid = repaid,
                    interestPaid = interest,
                    entries = entries.sortedByDescending { it.occurredAt },
                    lastActivity = dates.maxOrNull() ?: loan.date,
                )
            }
            for (loan in ownLoans) {
                val entries = byDebt[loan.id].orEmpty()
                view(loan, true, entries, 0L)?.let(result::add)
            }
            val legacyId = legacyLoanId(account.id)
            val legacyEntries = touching.filter { it.debtId == null || it.debtId !in ownIds } +
                byDebt[legacyId].orEmpty().filter { it.accountId != account.id && it.toAccountId != account.id }
            val record = byId[legacyId]?.takeIf { it.accountId == account.id }
            val legacy = record ?: LedgerLoan(
                id = legacyId,
                accountId = account.id,
                direction = direction,
                date = LedgerMath.dateOf(account.createdAt, zone),
                legacy = true,
            )
            view(legacy, record != null, legacyEntries.distinctBy { it.id }, account.openingBalance)?.let(result::add)
        }
        return result.sortedWith(compareBy<LoanView> { it.settled }.thenByDescending { it.lastActivity })
    }

    /** 借贷的一笔流水是什么（详情页列表用）。 */
    fun entryKind(view: LoanView, tx: LedgerTransaction): LoanEntryKind {
        val sign = if (view.direction == LoanDirection.LEND) 1L else -1L
        val e = effect(tx, view.account.id) * sign
        return when {
            e > 0 -> LoanEntryKind.PRINCIPAL
            e < 0 && tx.type == TxType.TRANSFER -> LoanEntryKind.REPAY
            e < 0 -> LoanEntryKind.WRITE_OFF
            else -> LoanEntryKind.INTEREST
        }
    }

    // ── 总览 ──────────────────────────────────────────────────────

    fun overview(data: LedgerData, today: LocalDate, zone: ZoneId): DebtOverview {
        val credits = data.accounts
            .filter { it.kind == AccountKind.CREDIT && (!it.archived || data.transactions.any { t -> t.accountId == it.id || t.toAccountId == it.id }) }
            .sortedBy { it.position }
            .map { credit(it, data.transactions, data.installments, today, zone) }
        val loans = loans(data, zone)
        val items = ArrayList<DueItem>()
        for (c in credits) {
            val latest = c.latest
            if (latest != null && latest.remaining > 0) {
                items += DueItem(
                    key = "st-${c.account.id}-${latest.statementDate}",
                    kind = DueKind.CREDIT,
                    title = c.account.name,
                    subtitle = "${latest.statementDate.monthValue}月账单",
                    amount = latest.remaining,
                    dueDate = latest.dueDate,
                    overdue = latest.overdue(today),
                    payable = true,
                    counts = true,
                    accountId = c.account.id,
                )
            }
            val open = c.open
            if (open != null && open.amount > 0) {
                items += DueItem(
                    key = "open-${c.account.id}",
                    kind = DueKind.CREDIT_OPEN,
                    title = c.account.name,
                    subtitle = "本期未出账单 · ${open.statementDate.monthValue}月${open.statementDate.dayOfMonth}日出账",
                    amount = open.amount,
                    dueDate = open.dueDate,
                    overdue = false,
                    payable = true,
                    counts = false,
                    accountId = c.account.id,
                )
            }
            for (inst in c.installments) {
                val next = inst.next ?: continue
                items += DueItem(
                    key = "in-${inst.plan.id}",
                    kind = DueKind.INSTALLMENT,
                    title = inst.plan.title,
                    subtitle = "${c.account.name} · 第 ${next.index + 1}/${inst.total} 期，计入${next.statementDate.monthValue}月账单",
                    amount = next.amount,
                    dueDate = next.dueDate,
                    overdue = false,
                    payable = true,
                    counts = false,
                    accountId = c.account.id,
                    debtId = inst.plan.id,
                )
            }
        }
        for (loan in loans) {
            val due = loan.loan.dueDate ?: continue
            if (loan.settled) continue
            val amount = loan.remaining + loan.interestRemaining
            if (amount <= 0) continue
            val lend = loan.direction == LoanDirection.LEND
            items += DueItem(
                key = "ln-${loan.loan.id}",
                kind = DueKind.LOAN,
                title = loan.person,
                subtitle = if (lend) "借出 · 待收" else "借入 · 待还",
                amount = amount,
                dueDate = due,
                overdue = today > due,
                payable = !lend,
                counts = true,
                accountId = loan.account.id,
                debtId = loan.loan.id,
            )
        }
        items.sortWith(compareBy<DueItem> { it.dueDate }.thenBy { it.kind.ordinal })
        val month = YearMonth.from(today)
        val thisMonth = { item: DueItem -> item.overdue || YearMonth.from(item.dueDate) == month }
        val monthDue = items.filter { it.counts && it.payable && thisMonth(it) }.sumOf { it.amount }
        val overdue = items.filter { it.counts && it.payable && it.overdue }.sumOf { it.amount }
        val monthReceivable = items.filter { it.counts && !it.payable && thisMonth(it) }.sumOf { it.amount }
        val open = loans.filter { !it.settled }
        val people = open.groupBy { it.person }.map { (person, list) ->
            PersonTotal(
                person,
                list.filter { it.direction == LoanDirection.LEND }.sumOf { it.remaining },
                list.filter { it.direction == LoanDirection.BORROW }.sumOf { it.remaining },
                list.size,
            )
        }.sortedByDescending { it.receivable + it.payable }
        return DebtOverview(
            today = today,
            monthDue = monthDue,
            overdue = overdue,
            monthReceivable = monthReceivable,
            items = items,
            credits = credits,
            loans = loans,
            receivable = open.filter { it.direction == LoanDirection.LEND }.sumOf { it.remaining },
            payable = open.filter { it.direction == LoanDirection.BORROW }.sumOf { it.remaining },
            people = people,
        )
    }

    // ── 分期 ──────────────────────────────────────────────────────

    /** 按费率（万分之一）算每期手续费：总额 × 费率，四舍五入到分。 */
    fun feeFromRate(total: Long, rateBp: Int): Long = (total * rateBp + 5_000) / 10_000

    /**
     * 提前结清：[date] 之后的各期本金并入 [date] 所在的那一期账单，之后各期的手续费不再收取
     * （手续费那一笔流水由仓库改成 [LedgerInstallment.chargedFees]）。
     */
    fun payoff(plan: LedgerInstallment, date: LocalDate): LedgerInstallment {
        val effective = if (date < plan.startDate) plan.startDate else date
        return plan.copy(payoffDate = effective)
    }
}
