package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.DebtOverview
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.LoanEntryKind
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.PersonDebt
import com.xiguli.jinkui.data.ledger.PersonDebtMath
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId

// ── 欠款 / 借贷（1.4.0-alpha04 重做）──────────────────────────────
// 参考钱迹、一木记账的“借贷”（只借鉴交互）：首屏只有三个数——别人欠我、我欠别人、净额；
// 下面分“别人欠我 / 我欠别人”两栏，一人一行，大字写还欠多少；点进去是这个人的全部来往与收款 / 还款 / 再借 / 结清。
// 首屏不出现“本金”“冲抵”这类词。

// ── 账户余额的显示（记一笔、转账、借贷里选账户时都带上余额）────────

/** 账户余额的一句话：“¥1,200.00”；信用卡 / 花呗是“欠 ¥300.00 · 可用 ¥2,700.00”。 */
fun accountBalanceText(account: LedgerAccount, balance: Long): String = when {
    account.kind == AccountKind.CREDIT -> {
        val owed = (-balance).coerceAtLeast(0L)
        val limit = account.credit?.limit ?: 0L
        buildString {
            append(if (owed > 0) "欠 ${Money.format(owed)}" else if (balance > 0) "溢缴 ${Money.format(balance)}" else "没有欠款")
            if (limit > 0) append(" · 可用 ${Money.format((limit - owed).coerceAtLeast(0L))}")
        }
    }
    else -> Money.format(balance)
}

/** 选账户的标签上：“微信 ¥120.00”“花呗 欠 ¥300.00”。 */
fun accountChipText(account: LedgerAccount, balance: Long?): String {
    if (balance == null) return account.name
    return if (account.kind == AccountKind.CREDIT) {
        val owed = (-balance).coerceAtLeast(0L)
        if (owed > 0) "${account.name} 欠 ${Money.format(owed)}" else account.name
    } else {
        "${account.name} ${Money.format(balance)}"
    }
}

// ── 首屏 ─────────────────────────────────────────────────────────

/**
 * 欠款 / 借贷（无状态）：三个大数（别人欠我、我欠别人、净额）→ 记一笔借出 / 借入 → “别人欠我 / 我欠别人”两栏，
 * 一人一行 → 信用卡 / 花呗欠款 → 快到期的（有才显示）。
 */
@Composable
fun DebtOverviewContent(ui: DebtOverview, actions: DebtOverviewActions, initialTab: LoanDirection = LoanDirection.LEND) {
    val colors = Jk.colors
    val today = ui.today
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val lent = remember(ui) { PersonDebtMath.people(ui.loans, LoanDirection.LEND, today) }
    val borrowed = remember(ui) { PersonDebtMath.people(ui.loans, LoanDirection.BORROW, today) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar("欠款 / 借贷", onBack = actions.onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x2)
                .navigationBarsPadding()
                .padding(bottom = Space.x4),
        ) {
            if (ui.credits.isEmpty() && ui.loans.isEmpty()) {
                DebtEmptyState(actions)
                Text(
                    "信用卡、花呗、白条请添加种类为“信用卡”的账户，设好账单日与还款日后，这里会算出每期要还多少。",
                    style = JkType.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = Space.x05, vertical = Space.x2),
                )
                return@Column
            }
            val net = ui.receivable - ui.payable
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.SPRIG) {
                Column(Modifier.padding(horizontal = Space.x2_5, vertical = Space.x2_5)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                        BigFigure("别人欠我", Money.format(ui.receivable, symbol = false), colors.textPrimary, Modifier.weight(1f))
                        BigFigure("我欠别人", Money.format(ui.payable, symbol = false), if (ui.payable > 0) colors.expense else colors.textPrimary, Modifier.weight(1f))
                        BigFigure(
                            "净额",
                            (if (net > 0) "+" else if (net < 0) "-" else "") + Money.format(kotlin.math.abs(net), symbol = false),
                            if (net < 0) colors.expense else colors.accent,
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Space.x1))
                    Text(
                        if (net >= 0) "净额 = 别人欠我 − 我欠别人。别人欠我的比我欠别人的多 ${Money.format(net)}。"
                        else "净额 = 别人欠我 − 我欠别人。我欠别人的比别人欠我的多 ${Money.format(-net)}。",
                        style = JkType.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(Space.x1_5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkButton("记一笔借出", { actions.onNewLoan(LoanDirection.LEND) }, modifier = Modifier.weight(1f))
                JkButton("记一笔借入", { actions.onNewLoan(LoanDirection.BORROW) }, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
            }
            Text(
                "借出 = 我把钱借给别人；借入 = 我向别人借钱。以前的欠账也可以记，不用经过任何账户。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x1),
            )

            Spacer(Modifier.height(Space.x2))
            JkSegmented(
                listOf(
                    LoanDirection.LEND to "别人欠我 · ${lent.count { !it.settled }} 人",
                    LoanDirection.BORROW to "我欠别人 · ${borrowed.count { !it.settled }} 人",
                ),
                tab,
                { tab = it },
            )
            Spacer(Modifier.height(Space.x1_5))
            val people = if (tab == LoanDirection.LEND) lent else borrowed
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                if (people.isEmpty()) {
                    Text(
                        if (tab == LoanDirection.LEND) "还没有人欠你钱。点“记一笔借出”，记下借给了谁、借了多少。"
                        else "你没有欠别人的钱。向别人借了钱，点“记一笔借入”记下来。",
                        style = JkType.small,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(Space.x2),
                    )
                }
                people.forEachIndexed { i, p ->
                    if (i > 0) JkDivider(inset = Space.x2 + 40.dp + Space.x1_5)
                    PersonRow(p, today) { actions.onPerson(p.account.id) }
                }
            }

            DebtSection("信用卡 / 花呗") { DebtLink("管理账户", actions.onAccounts) }
            if (ui.credits.isEmpty()) {
                JkCard(Modifier.fillMaxWidth()) {
                    Text("还没有信用卡或花呗。添加一个种类为“信用卡”的账户（花呗、白条也选它），就能看到每期要还多少。", style = JkType.small, color = colors.textTertiary)
                    DebtLink("添加信用卡", actions.onAddCredit)
                }
            } else {
                Text(
                    "一共欠 ${Money.format(ui.creditDebt)}" + if (ui.monthDue > 0) "，本月要还 ${Money.format(ui.monthDue)}" else "",
                    style = JkType.bodyStrong,
                    color = if (ui.overdue > 0) colors.expense else colors.textPrimary,
                    modifier = Modifier.padding(start = Space.x05, bottom = Space.x1),
                )
                for (credit in ui.credits) {
                    CreditCard(credit, today) { actions.onCredit(credit.account.id) }
                    Spacer(Modifier.height(Space.x1_5))
                }
            }

            if (ui.items.isNotEmpty()) {
                DebtSection("快到期的")
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    ui.items.forEachIndexed { i, item ->
                        if (i > 0) JkDivider(inset = Space.x2 + 48.dp + Space.x1_5)
                        DueRow(item, today) { actions.onItem(item) }
                    }
                }
            }
            Text(
                "全部只存在本机加密库里。到期前 3 天会在账本首页提醒。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = Space.x05, vertical = Space.x2),
            )
        }
    }
}

@Composable
private fun BigFigure(label: String, value: String, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = JkType.small.copy(fontWeight = FontWeight.Medium), color = Jk.colors.textSecondary, maxLines = 1)
        Text(value, style = JkType.amountHero.copy(fontSize = 22.sp), color = tone, maxLines = 1)
    }
}

/** 头像：名字的第一个字，薄荷圆底。 */
@Composable
private fun Avatar(name: String, size: Int = 40) {
    val colors = Jk.colors
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), style = JkType.bodyStrong.copy(fontSize = (size * 0.42f).sp), color = colors.accent)
    }
}

/** 一人一行：头像、名字、进度（已还 x / 共 y）、到期；右边大字还欠多少。 */
@Composable
fun PersonRow(p: PersonDebt, today: LocalDate, onClick: () -> Unit) {
    val colors = Jk.colors
    val lend = p.direction == LoanDirection.LEND
    val overdue = p.overdue(today)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClickLabel = "查看${p.person}") { onClick() }
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.person)
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Text(p.person, style = JkType.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            DebtBar(p.progress)
            Spacer(Modifier.height(4.dp))
            Text(
                (if (lend) "已收回" else "已还") + " ${Money.format(p.repaid)} / 共 ${Money.format(p.total)}",
                style = JkType.caption,
                color = colors.textTertiary,
                maxLines = 1,
            )
            p.due?.takeIf { !p.settled }?.let { due ->
                Text(
                    "${debtDate(due, today)}到期 · ${dueLabel(due, today)}",
                    style = JkType.caption.copy(fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal),
                    color = if (overdue) colors.expense else colors.textTertiary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(Space.x1))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (p.settled) "已还清" else Money.format(p.remaining),
                style = if (p.settled) JkType.small else JkType.amount.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                color = when {
                    p.settled -> colors.textTertiary
                    overdue || !lend -> colors.expense
                    else -> colors.textPrimary
                },
                maxLines = 1,
            )
            if (!p.settled) Text(if (lend) "他还欠我" else "我还欠他", style = JkType.caption, color = colors.textTertiary)
        }
    }
}

// ── 某一个人 ─────────────────────────────────────────────────────

class PersonActions(
    val onBack: () -> Unit = {},
    /** 收款（别人欠我）/ 还款（我欠别人）。 */
    val onPay: () -> Unit = {},
    /** 再借：同一个人、同一个方向再记一笔。 */
    val onMore: () -> Unit = {},
    /** 结清：剩下的不再收 / 不再还。 */
    val onSettle: () -> Unit = {},
    /** 点一笔借出 / 借入：改预计还款日、利息、备注或删除。 */
    val onLoan: (com.xiguli.jinkui.data.ledger.LoanView) -> Unit = {},
)

@Composable
fun LoanPersonScreen(accountId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { DebtViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val today = ui.today
    val person = remember(ui, accountId) { PersonDebtMath.person(ui.loans, accountId, today) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var loanSheet by remember { mutableStateOf<String?>(null) }
    var openedLoan by remember { mutableStateOf<String?>(null) }
    val ledger = container.ledger
    if (person == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            JkTopBar("借贷", onBack = onBack)
            Text("这个人的借贷已经不在了。", style = JkType.small, color = Jk.colors.textTertiary, modifier = Modifier.padding(Space.x2))
        }
        return
    }
    val lend = person.direction == LoanDirection.LEND
    val balances = remember(data) { LedgerMath.balances(data.accounts, data.transactions) }
    LoanPersonContent(
        person, today,
        PersonActions(
            onBack = onBack,
            onPay = { sheet = "pay" },
            onMore = { sheet = "more" },
            onSettle = { sheet = "settle" },
            onLoan = { openedLoan = it.loan.id },
        ),
    )
    when (sheet) {
        "pay" -> PersonPaySheet(
            person = person,
            funds = DebtMath.loanFunds(data.accounts),
            balances = balances,
            initialFund = DebtMath.lastLoanFund(data),
            today = today,
            onDismiss = { sheet = null },
        ) { principal, interest, fundId, date ->
            viewModel.run(if (lend) "已记收款" else "已记还款", { sheet = null }) {
                ledger.personPayment(person.account.id, principal, interest, fundId, date)
            }
        }
        "more" -> NewLoanSheet(
            people = data.accounts.filter { LoanDirection.of(it.kind) != null },
            funds = DebtMath.loanFunds(data.accounts),
            today = today,
            onDismiss = { sheet = null },
            initialDirection = person.direction,
            initialFund = DebtMath.lastLoanFund(data),
            initialPersonId = person.account.id,
            balances = balances,
        ) { direction, name, accountId2, amount, fundId, date, due, interest, note ->
            viewModel.run("已记一笔${direction.label} ${Money.format(amount)}", { sheet = null }) {
                ledger.createLoan(direction, name, accountId2, amount, fundId, date, due, interest, note)
            }
        }
        "settle" -> JkDialog(
            title = "和${person.person}结清？",
            onDismiss = { sheet = null },
            confirmText = "结清",
            onConfirm = {
                sheet = null
                viewModel.run("已结清") {
                    for (loan in person.open) ledger.forgiveLoan(loan.loan.id, loan.account.id, loan.remaining, today)
                }
            },
        ) {
            Text(
                if (lend) "${person.person}还欠你的 ${Money.format(person.remaining)} 不再收回（记成一笔支出），这个人就算还清了。"
                else "你还欠${person.person}的 ${Money.format(person.remaining)} 对方不要了（记成一笔收入），这个人就算还清了。",
                style = JkType.body,
            )
        }
    }
    person.loans.firstOrNull { it.loan.id == openedLoan }?.let { loan ->
        when (loanSheet) {
            "edit" -> LoanEditSheet(loan, today, { loanSheet = null }) { edited ->
                viewModel.run("已保存", { loanSheet = null }) { ledger.saveLoan(edited) }
            }
            else -> LoanDetailSheet(
                loan, today,
                onDismiss = { openedLoan = null },
                onPay = {
                    openedLoan = null
                    sheet = "pay"
                },
                onEdit = { loanSheet = "edit" },
                onForgive = { viewModel.run("已结清", { openedLoan = null }) { ledger.forgiveLoan(loan.loan.id, loan.account.id, loan.remaining, today) } },
                onDelete = { viewModel.run(if (loan.stored && !loan.loan.legacy) "已删除这笔借贷" else "已清除约定", { openedLoan = null }) { ledger.deleteDebt(loan.loan.id) } },
            )
        }
    }
}

/** 某一个人（无状态）：还欠多少（大字）、进度、到期 → 收款 / 还款、再借、结清 → 全部来往（新的在前）。 */
@Composable
fun LoanPersonContent(person: PersonDebt, today: LocalDate, actions: PersonActions, zone: ZoneId = ZoneId.systemDefault()) {
    val colors = Jk.colors
    val lend = person.direction == LoanDirection.LEND
    val overdue = person.overdue(today)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(person.person, onBack = actions.onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x2)
                .navigationBarsPadding()
                .padding(bottom = Space.x4),
        ) {
            // 文字都在左边：插画只放右边（SPRIG 的顶部蕨叶与左下角叶子会压住“我还欠某某”和进度文字）
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.RIGHT) {
                Column(Modifier.padding(Space.x2_5)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(person.person, 44)
                        Spacer(Modifier.width(Space.x1_5))
                        Text(
                            if (person.settled) (if (lend) "${person.person}已经还清了" else "已经还清${person.person}了")
                            else if (lend) "${person.person}还欠我" else "我还欠${person.person}",
                            style = JkType.bodyStrong,
                            color = colors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(Space.x1))
                    Text(
                        Money.format(person.remaining),
                        style = JkType.amountHero.copy(fontSize = 34.sp),
                        color = if (!lend && !person.settled || overdue) colors.expense else colors.textPrimary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(Space.x1))
                    DebtBar(person.progress)
                    Spacer(Modifier.height(Space.x05))
                    Text(
                        (if (lend) "已收回" else "已还") + " ${Money.format(person.repaid)} / 共 ${Money.format(person.total)}" +
                            if (person.interestPaid > 0) "，另外利息 ${Money.format(person.interestPaid)}" else "",
                        style = JkType.small,
                        color = colors.textSecondary,
                    )
                    person.due?.takeIf { !person.settled }?.let { due ->
                        Text(
                            "约定 ${debtDate(due, today)} 还 · ${dueLabel(due, today)}",
                            style = JkType.small.copy(fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal),
                            color = if (overdue) colors.expense else colors.textSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.x1_5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkButton(if (lend) "收款" else "还款", actions.onPay, enabled = !person.settled, modifier = Modifier.weight(1f))
                JkButton(if (lend) "再借出" else "再借入", actions.onMore, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
                JkButton("结清", actions.onSettle, style = ButtonStyle.Ghost, enabled = !person.settled, modifier = Modifier.weight(1f))
            }
            Text(
                if (lend) "收款 = ${person.person}还我钱；结清 = 剩下的不要了。" else "还款 = 我还${person.person}钱；结清 = 对方说剩下的不用还了。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x1),
            )

            DebtSection("来往记录")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                val entries = person.entries
                if (entries.isEmpty()) Text("还没有记录。", style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(Space.x2))
                entries.forEachIndexed { i, tx ->
                    if (i > 0) JkDivider(inset = Space.x2)
                    val loan = person.loans.first { l -> l.entries.any { it.id == tx.id } }
                    val kind = DebtMath.entryKind(loan, tx)
                    val label = when (kind) {
                        LoanEntryKind.PRINCIPAL -> if (lend) "借出" else "借入"
                        LoanEntryKind.REPAY -> if (lend) "收款" else "还款"
                        LoanEntryKind.INTEREST -> "利息"
                        LoanEntryKind.WRITE_OFF -> if (lend) "结清（不再收回）" else "结清（对方免还）"
                    }
                    // 借出 / 收款对“他欠我”的影响：借出加、收款减
                    val sign = when (kind) {
                        LoanEntryKind.PRINCIPAL -> "+"
                        LoanEntryKind.REPAY, LoanEntryKind.WRITE_OFF -> "−"
                        LoanEntryKind.INTEREST -> ""
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClickLabel = "查看这笔借款") { actions.onLoan(loan) }
                            .padding(horizontal = Space.x2, vertical = Space.x1)
                            .semantics(mergeDescendants = true) { contentDescription = "$label ${Money.format(tx.amount)}" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = JkType.bodyStrong, color = colors.textPrimary)
                            Text(
                                debtDate(LedgerMath.dateOf(tx.occurredAt, zone), today) + (if (tx.note.isNotBlank()) " · ${tx.note}" else ""),
                                style = JkType.caption,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            sign + Money.format(tx.amount),
                            style = JkType.amount,
                            color = when (kind) {
                                LoanEntryKind.PRINCIPAL -> if (lend) colors.textPrimary else colors.expense
                                LoanEntryKind.REPAY, LoanEntryKind.WRITE_OFF -> colors.income
                                LoanEntryKind.INTEREST -> colors.textSecondary
                            },
                        )
                    }
                }
            }
            Text(
                "“+”是借出 / 借入让欠款变多，“−”是收款 / 还款让欠款变少。点一条可以改约定的还款日、利息或删除那一笔。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = Space.x05, vertical = Space.x2),
            )
        }
    }
}

/** 按人收款 / 还款：金额（默认还欠的全部）、可选利息、经过哪个账户（可以不经过）、日期。 */
@Composable
fun PersonPaySheet(
    person: PersonDebt,
    funds: List<LedgerAccount>,
    balances: Map<String, Long>,
    initialFund: String?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (principal: Long, interest: Long, fundId: String?, date: LocalDate) -> Unit,
) {
    val lend = person.direction == LoanDirection.LEND
    var amount by remember { mutableStateOf(person.remaining.takeIf { it > 0 }?.let(Money::plain) ?: "") }
    var interest by remember { mutableStateOf("") }
    var fund by remember { mutableStateOf(initialFund?.takeIf { id -> funds.any { it.id == id } }) }
    var date by remember { mutableStateOf(today) }
    var error by remember { mutableStateOf<String?>(null) }
    JkSheet(onDismiss = onDismiss, title = if (lend) "${person.person}还我钱" else "我还${person.person}钱") {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
            Text(
                (if (lend) "${person.person}还欠你 " else "你还欠${person.person} ") + Money.format(person.remaining),
                style = JkType.bodyStrong,
                color = Jk.colors.textPrimary,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                item(key = "all") { JkChip("全部 ${Money.format(person.remaining)}", Money.parse(amount) == person.remaining, { amount = Money.plain(person.remaining); error = null }) }
                if (person.remaining >= 2) item(key = "half") { JkChip("一半", Money.parse(amount) == person.remaining / 2, { amount = Money.plain(person.remaining / 2); error = null }) }
            }
            JkTextField(value = amount, onValueChange = { amount = it.take(14); error = null }, label = if (lend) "这次收回多少（可以只收一部分）" else "这次还多少（可以只还一部分）", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            JkTextField(value = interest, onValueChange = { interest = it.take(14); error = null }, label = "另付利息（可选）", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            Text(if (lend) "钱进哪个账户" else "钱从哪个账户出", style = JkType.small, color = Jk.colors.textSecondary)
            LoanFundChips(funds, fund, balances) { fund = it }
            if (fund == null) Text(noFundHint(if (lend) LoanAction.COLLECT else LoanAction.REPAY), style = JkType.caption, color = Jk.colors.textTertiary)
            DateField(if (lend) "收款日期" else "还款日期", date, today, { date = it })
            error?.let { Text(it, style = JkType.small, color = Jk.colors.danger) }
        }
        Spacer(Modifier.height(Space.x2))
        JkButton(if (lend) "确认收款" else "确认还款", {
            val p = if (amount.isBlank()) 0L else Money.parse(amount)
            val i = if (interest.isBlank()) 0L else Money.parse(interest)
            error = when {
                p == null || i == null -> "金额格式不对，最多两位小数"
                p <= 0 && i <= 0 -> "请输入大于 0 的金额"
                p > person.remaining -> "最多${if (lend) "收" else "还"} ${Money.format(person.remaining)}"
                else -> null
            }
            if (error == null) onSubmit(p!!, i!!, fund, date)
        }, modifier = Modifier.fillMaxWidth())
    }
}
