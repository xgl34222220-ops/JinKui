package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId

/**
 * “记一笔”里的借贷（1.4.0-alpha04）：借出 / 借入 / 收款 / 还款，直接在记账面板里记，不用先找到“欠款 / 账单”。
 *  - 借出 / 借入：新建一笔借贷（对方账户没有就按名字新建，“借出 · 小李”），可以填预计还款日与约定利息；
 *  - 收款 / 还款：选一笔没结清的借贷，本金可以只还一部分，另付的利息单独记。
 * 实际写入用的是欠款页同一套 [com.xiguli.jinkui.data.repo.LedgerRepository.createLoan] / loanPayment。
 */
enum class LoanAction(val label: String, val direction: LoanDirection, val principal: Boolean, val hint: String) {
    LEND("借出", LoanDirection.LEND, true, "借出 = 我把钱借给别人（别人欠我）"),
    BORROW("借入", LoanDirection.BORROW, true, "借入 = 我向别人借钱（我欠别人）"),
    COLLECT("收款", LoanDirection.LEND, false, "收款 = 别人还我钱"),
    REPAY("还款", LoanDirection.BORROW, false, "还款 = 我还别人钱");

    /** 钱从资金账户流出（借出、还款）还是流入（借入、收款），决定颜色。 */
    val tone: TxType get() = if (this == LEND || this == REPAY) TxType.EXPENSE else TxType.INCOME
}

/** 可以选的对方：借出 / 借入时是已有的对方账户；收款 / 还款时是每一笔没结清的借贷。 */
data class LoanTarget(
    val accountId: String,
    val person: String,
    /** 收款 / 还款对应的借贷。 */
    val loanId: String? = null,
    /** 还剩多少没还（收款 / 还款时）。 */
    val remaining: Long = 0L,
) {
    val label: String get() = if (loanId != null) "$person · 剩 ${Money.format(remaining)}" else person
}

data class LoanEntryRequest(
    val action: LoanAction,
    val person: String,
    val accountId: String?,
    val loanId: String?,
    val amount: Long,
    /** 资金账户；null = 不经过账户（只记欠款）。 */
    val fundId: String?,
    val date: LocalDate,
    val due: LocalDate?,
    val interest: Long,
    val note: String,
)

object LoanEntryMath {
    fun targets(data: LedgerData, action: LoanAction, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<LoanTarget> =
        if (action.principal) {
            data.accounts
                .filter { !it.archived && it.kind == action.direction.accountKind }
                .sortedBy { it.position }
                .map { LoanTarget(it.id, DebtMath.personOf(it)) }
        } else {
            DebtMath.overview(data, today, zone).loans
                .filter { it.direction == action.direction && !it.settled && it.remaining > 0 }
                .sortedBy { it.loan.date }
                .map { LoanTarget(it.account.id, it.person, it.loan.id, it.remaining) }
        }

    /** 保存前的校验；通过时返回 null。 */
    fun validate(r: LoanEntryRequest, target: LoanTarget?): String? = when {
        r.amount <= 0 && (r.action.principal || r.interest <= 0) -> "请输入大于 0 的金额"
        r.amount > Money.MAX_CENTS || r.interest > Money.MAX_CENTS -> "金额太大了"
        r.interest < 0 -> "利息格式不对"
        r.action.principal && r.person.isBlank() && r.accountId == null -> "请填写对方"
        !r.action.principal && r.loanId == null -> if (r.action == LoanAction.COLLECT) "请选择要收款的借出" else "请选择要还的借入"
        !r.action.principal && target != null && r.amount > target.remaining -> "超过了还剩的 ${Money.format(target.remaining)}"
        r.due != null && r.due < r.date -> "预计还款日不能早于借款日期"
        else -> null
    }
}

/** 记账面板里借贷的四个类型。 */
@Composable
internal fun LoanActionChips(selected: LoanAction, onSelect: (LoanAction) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (a in LoanAction.entries) JkChip(a.label, a == selected, { onSelect(a) })
        }
        Spacer(Modifier.height(Space.x05))
        Text(selected.hint, style = JkType.caption, color = Jk.colors.textTertiary)
    }
}

@Composable
internal fun LoanTargetChips(targets: List<LoanTarget>, selected: LoanTarget?, onSelect: (LoanTarget?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
        items(targets, key = { it.loanId ?: it.accountId }) { t ->
            val on = selected == t
            JkChip(t.label, on, { onSelect(if (on) null else t) })
        }
    }
}

/** 1.4.0-alpha04：借贷的资金账户可以不选——只记谁欠谁、欠多少，不改任何账户的余额（例如早就花掉的借款）。 */
const val NO_FUND_LABEL = "不经过账户（只记欠款）"

/**
 * 借贷经过哪个账户：第一个是“不经过账户（只记欠款）”，后面是全部在用的资金账户（含信用卡 / 花呗）。
 * [selected] 为 null 表示不经过账户。
 */
@Composable
internal fun LoanFundChips(
    funds: List<com.xiguli.jinkui.data.ledger.LedgerAccount>,
    selected: String?,
    balances: Map<String, Long> = emptyMap(),
    onSelect: (String?) -> Unit,
) {
    // 1.4.0-alpha04：换行排开（不再横向滑动），每个账户都看得见、点得到；每个账户带上当前余额（信用卡 / 花呗显示欠款）
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        JkChip(NO_FUND_LABEL, selected == null, { onSelect(null) })
        for (a in funds) JkChip(accountChipText(a, balances[a.id]), a.id == selected, { onSelect(a.id) })
    }
}

/** 选了“不经过账户”时的一句说明。 */
internal fun noFundHint(action: LoanAction): String = when (action) {
    LoanAction.LEND, LoanAction.BORROW -> "只记谁欠谁、欠多少，不改任何账户的余额（例如以前的借款、早就花掉的钱）"
    LoanAction.COLLECT, LoanAction.REPAY -> "只减少欠款，不改任何账户的余额（例如已经在别处结清）；另付的利息仍计入收支"
}
