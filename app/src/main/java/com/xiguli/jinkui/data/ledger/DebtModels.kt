package com.xiguli.jinkui.data.ledger

import java.time.LocalDate

/** 还款日的定法（1.4 起）。[key] 写入加密数据与备份，上线后不要改动。 */
enum class DueMode(val key: String, val label: String) {
    /** 每月固定某一天（大于账单日时为当月，否则为次月；月份没有这一天时取月末）。 */
    DAY_OF_MONTH("day", "每月固定日"),
    /** 出账后第 N 天。 */
    AFTER_STATEMENT("after", "出账后 N 天");

    companion object {
        fun of(key: String?): DueMode = entries.firstOrNull { it.key == key } ?: DAY_OF_MONTH
    }
}

/**
 * 信用账户（信用卡、花呗、白条等）的账单设置（1.4 起）。存在账户的加密载荷里、随账户进备份。
 *  - [statementDay]：账单日 1–31，月份没有这一天时取月末（例如 31 号在 2 月取 28 / 29 号）；
 *    账单日当天的消费计入当期账单，下一个周期从账单日次日开始。
 *  - [dueDay]：[DueMode.DAY_OF_MONTH] 时是 1–31 号，[DueMode.AFTER_STATEMENT] 时是出账后 1–60 天。
 *  - [limit]：额度（分），0 表示没填。
 */
data class CreditSettings(
    val statementDay: Int,
    val dueDay: Int,
    val dueMode: DueMode = DueMode.DAY_OF_MONTH,
    val limit: Long = 0L,
) {
    /** 不合法时返回给用户看的原因。 */
    fun problem(): String? = when {
        statementDay !in 1..31 -> "账单日要在 1 到 31 号之间"
        dueMode == DueMode.DAY_OF_MONTH && dueDay !in 1..31 -> "还款日要在 1 到 31 号之间"
        dueMode == DueMode.AFTER_STATEMENT && dueDay !in 1..60 -> "出账后天数要在 1 到 60 天之间"
        limit < 0 || limit > Money.MAX_CENTS -> "额度超出范围"
        else -> null
    }
}

/** 借贷方向。[key] 写入加密数据与备份，上线后不要改动。 */
enum class LoanDirection(val key: String, val label: String) {
    /** 借出：别人欠我（应收）。 */
    LEND("lend", "借出"),
    /** 借入：我欠别人（应付）。 */
    BORROW("borrow", "借入");

    val accountKind: AccountKind get() = if (this == LEND) AccountKind.RECEIVABLE else AccountKind.PAYABLE

    companion object {
        fun of(key: String?): LoanDirection = entries.firstOrNull { it.key == key } ?: LEND
        fun of(kind: AccountKind): LoanDirection? = when (kind) {
            AccountKind.RECEIVABLE -> LEND
            AccountKind.PAYABLE -> BORROW
            else -> null
        }
    }
}

/**
 * 信用账户上的一笔分期（1.4 起）。分期本身不改余额：消费（可选，由分期时一并记上）与手续费都是挂在信用账户上的普通支出，
 * 带着 [LedgerTransaction.debtId] = [id]；账单计算时把还没到期的各期本金与手续费从应还里扣掉，每期在它的入账日
 * 所在的那一期账单里出现（见 [DebtMath]）。
 *  - 第 k 期（从 0 数）的入账日 = [firstDate] 加 k 个月（月份没有这一天时取月末）；
 *  - 每期本金 = 总额 / 期数，除不尽的零头放在第一期；
 *  - [payoffDate]：提前结清的日期。之后的各期本金都并入结清当天所在的账单，之后各期的手续费不再收取。
 */
data class LedgerInstallment(
    val id: String,
    val accountId: String,
    val title: String,
    val total: Long,
    val periods: Int,
    /** 每期手续费（分）。按费率填时由费率算出。 */
    val feePerPeriod: Long = 0L,
    /** 每期费率，单位万分之一（0.6% = 60）；按固定金额填时为 null。只用于显示。 */
    val feeRateBp: Int? = null,
    val firstDate: LocalDate,
    /** 分期发生的日期（消费 / 手续费流水的日期）；不晚于 [firstDate]。 */
    val startDate: LocalDate = firstDate,
    val payoffDate: LocalDate? = null,
    val note: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun principalOf(period: Int): Long = total / periods + if (period == 0) total % periods else 0L

    /** 原定的入账日（不考虑提前结清）。 */
    fun scheduledDate(period: Int): LocalDate = firstDate.plusMonths(period.toLong())

    /** 提前结清后不再收取的一期。 */
    fun waived(period: Int): Boolean = payoffDate != null && scheduledDate(period) > payoffDate

    /** 实际入账日：提前结清后，之后的各期都在结清当天入账。 */
    fun billingDate(period: Int): LocalDate {
        val scheduled = scheduledDate(period)
        val payoff = payoffDate ?: return scheduled
        return if (scheduled > payoff) payoff else scheduled
    }

    fun feeOf(period: Int): Long = if (waived(period)) 0L else feePerPeriod

    fun amountOf(period: Int): Long = principalOf(period) + feeOf(period)

    /** 实际收取的手续费合计（提前结清后免掉的不算）。 */
    val chargedFees: Long get() = (0 until periods).sumOf { feeOf(it) }

    fun problem(): String? = when {
        title.isBlank() -> "请填写分期名称"
        total <= 0L -> "分期总额要大于 0"
        total > Money.MAX_CENTS -> "金额太大了"
        periods !in 2..60 -> "期数要在 2 到 60 期之间"
        feePerPeriod < 0L || feePerPeriod > Money.MAX_CENTS / 60 -> "手续费超出范围"
        startDate > firstDate -> "首期日期不能早于分期日期"
        else -> null
    }
}

/**
 * 一笔借贷（1.4 起）。对方是一个应收（借出）/ 应付（借入）账户——与 1.3 导入一样每人一个账户，
 * 这笔借贷的借出 / 借入、每次收款 / 还款都是这个账户与资金账户之间的转账，带着 [LedgerTransaction.debtId] = [id]；
 * 利息是资金账户上的收入（借出）/ 支出（借入），同样带着这个 ID。金额全部由这些流水算出，这里只存约定。
 *
 * [legacy] 为 true 的是“导入的借贷”：对方账户上所有没有归到某一笔借贷名下的流水（1.3 导入的一木借出 / 借入、
 * 期初余额）都算它的。没有这条记录时由 [DebtMath] 按账户合成一笔（ID = [DebtMath.legacyLoanId]），
 * 用户给它设了预计还款日等以后才落库。
 */
data class LedgerLoan(
    val id: String,
    val accountId: String,
    val direction: LoanDirection,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    /** 约定的利息（分），0 表示无息。 */
    val interest: Long = 0L,
    val note: String = "",
    val legacy: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
