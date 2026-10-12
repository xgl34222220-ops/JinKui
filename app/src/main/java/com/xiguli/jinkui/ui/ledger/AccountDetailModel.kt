package com.xiguli.jinkui.ui.ledger

import androidx.compose.runtime.Immutable
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.BalanceAdjust
import com.xiguli.jinkui.data.ledger.CreditView
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.DueMode
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.RefundMath
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 1.5.0-alpha02：一个账户的详情页（参考钱迹 / 一木记账点进账户后的“账户流水”，只借鉴交互）。
 * 余额（信用卡 / 花呗是“当前欠款 / 可用额度”）、本月流入 / 流出、这个账户自己的流水（按天分组，含余额调整）。
 */
@Immutable
data class AccountDayUi(
    val date: LocalDate,
    val label: String,
    val weekday: String,
    /** 这一天流进 / 流出这个账户的钱（分，正数；含转账与余额调整）。 */
    val inflow: Long,
    val outflow: Long,
    val rows: List<TxRowUi>,
)

@Immutable
data class AccountCreditUi(
    /** 当前欠款（正数；多还了是负数）。 */
    val debt: Long,
    /** 额度；没填为 null。 */
    val limit: Long?,
    val available: Long?,
    /** “每月 5 号”；没设账单日为 null。 */
    val statementDay: String?,
    /** “每月 23 号” / “出账后 20 天”。 */
    val dueDay: String?,
    /** 本期还要还多少（最近一期账单的剩余）；没有账单为 null。 */
    val statementRemaining: Long?,
    val dueDate: LocalDate?,
    val defaulted: Boolean,
)

@Immutable
data class AccountDetailUi(
    val account: LedgerAccount,
    val balance: Long,
    val monthIn: Long,
    val monthOut: Long,
    val month: YearMonth,
    val days: List<AccountDayUi>,
    val count: Int,
    val credit: AccountCreditUi?,
    val canAdjust: Boolean,
)

/** 一笔流水对 [accountId] 余额的影响（分）；与这个账户无关时为 0。 */
fun accountEffect(tx: LedgerTransaction, accountId: String): Long = when (tx.type) {
    TxType.EXPENSE -> if (tx.accountId == accountId) -tx.amount else 0L
    TxType.INCOME -> if (tx.accountId == accountId) tx.amount else 0L
    TxType.TRANSFER -> (if (tx.toAccountId == accountId) tx.amount else 0L) - (if (tx.accountId == accountId) tx.amount else 0L)
}

fun buildAccountDetailUi(
    data: LedgerData,
    accountId: String,
    zone: ZoneId,
    today: LocalDate = LocalDate.now(zone),
): AccountDetailUi? {
    val account = data.accounts.firstOrNull { it.id == accountId } ?: return null
    val balance = LedgerMath.balances(data.accounts, data.transactions)[accountId] ?: account.openingBalance
    val accountsById = data.accounts.associateBy { it.id }
    val categoriesById = data.categories.associateBy { it.id }
    val mine = data.transactions.filter { it.accountId == accountId || it.toAccountId == accountId }
    val month = YearMonth.from(today)
    var monthIn = 0L
    var monthOut = 0L
    for (tx in LedgerMath.inMonth(mine, month, zone)) {
        val e = accountEffect(tx, accountId)
        if (e > 0) monthIn += e else monthOut -= e
    }
    val refunds = RefundMath.refundTotals(data.transactions)
    val days = LedgerMath.groupByDay(mine, zone).map { day ->
        var inflow = 0L
        var outflow = 0L
        val rows = day.items.map { tx ->
            val e = accountEffect(tx, accountId)
            if (e > 0) inflow += e else outflow -= e
            val base = txRow(tx, accountsById, categoriesById, zone, refunds[tx.id] ?: 0L)
            if (tx.type == TxType.TRANSFER && BalanceAdjust.adjustmentOf(tx, accountsById) == null) {
                // 转账写清楚方向：“转出到 招行” / “从 现金 转入”
                val other = accountsById[if (e < 0) tx.toAccountId else tx.accountId]?.name ?: "未知账户"
                base.copy(
                    title = if (e < 0) "转出" else "转入",
                    where = if (e < 0) "到 $other" else "从 $other",
                    amountText = Money.format(e, signed = true),
                )
            } else {
                base.copy(where = "", amountText = Money.format(e, signed = true))
            }
        }
        AccountDayUi(day.date, shortDayLabel(day.date, today), weekdayLabel(day.date), inflow, outflow, rows)
    }
    val credit = if (account.kind == AccountKind.CREDIT) creditUi(data, account, balance, today, zone) else null
    return AccountDetailUi(
        account = account,
        balance = balance,
        monthIn = monthIn,
        monthOut = monthOut,
        month = month,
        days = days,
        count = mine.size,
        credit = credit,
        canAdjust = BalanceAdjust.canAdjust(account),
    )
}

private fun creditUi(data: LedgerData, account: LedgerAccount, balance: Long, today: LocalDate, zone: ZoneId): AccountCreditUi {
    val view: CreditView? = runCatching { DebtMath.overview(data, today, zone).credits.firstOrNull { it.account.id == account.id } }.getOrNull()
    val settings = view?.settings ?: account.credit
    val limit = (settings?.limit ?: 0L).takeIf { it > 0 }
    val debt = view?.debt ?: -balance
    return AccountCreditUi(
        debt = debt,
        limit = limit,
        available = view?.available ?: limit?.let { it - debt },
        statementDay = settings?.let { "每月 ${it.statementDay} 号" },
        dueDay = settings?.let { if (it.dueMode == DueMode.AFTER_STATEMENT) "出账后 ${it.dueDay} 天" else "每月 ${it.dueDay} 号" },
        statementRemaining = view?.latest?.remaining,
        dueDate = view?.latest?.dueDate,
        defaulted = view?.defaulted == true,
    )
}
