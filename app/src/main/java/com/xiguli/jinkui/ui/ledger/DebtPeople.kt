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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
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
                        // 1.5.0-alpha03：点大数切到下面对应的那一栏（不是死数字）
                        BigFigure("别人欠我", Money.format(ui.receivable, symbol = false), colors.textPrimary, Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "看别人欠我的") { tab = LoanDirection.LEND })
                        BigFigure("我欠别人", Money.format(ui.payable, symbol = false), if (ui.payable > 0) colors.expense else colors.textPrimary, Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "看我欠别人的") { tab = LoanDirection.BORROW })
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
    /** 1.5.0-alpha03：点来往记录里的一条：直接改这条（类型、对方、金额、日期、账户……）或删除。 */
    val onEntry: (com.xiguli.jinkui.data.ledger.LedgerTransaction) -> Unit = {},
    /** 1.5.0-alpha03：某一笔借款卡片上的“收款 / 还款”。 */
    val onPayLoan: (com.xiguli.jinkui.data.ledger.LoanView) -> Unit = {},
    /** 点一笔借款的卡片：编辑它。 */
    val onLoanCard: (com.xiguli.jinkui.data.ledger.LoanView) -> Unit = {},
    /** 编辑这个人：改名 / 合并到另一个人。 */
    val onEditPerson: () -> Unit = {},
    /** 修改欠款：直接改现在还欠多少。 */
    val onOwed: () -> Unit = {},
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
    var openedTx by remember { mutableStateOf<String?>(null) }
    var payLoan by remember { mutableStateOf<String?>(null) }
    var legacyLoan by remember { mutableStateOf<String?>(null) }
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
            onPayLoan = { payLoan = it.loan.id },
            onLoanCard = { loan ->
                // 一笔借款：点开改它的借出 / 借入那一条（金额、日期、账户、约定都在一个“编辑”面板里）；导入的改约定
                val principal = loan.entries.lastOrNull { DebtMath.entryKind(loan, it) == LoanEntryKind.PRINCIPAL }
                // 导入的借贷也一样打开那一条流水（能改金额、日期、账户，能删）；只有一条流水都没有时才退回改约定
                if (principal != null) openedTx = principal.id else legacyLoan = loan.loan.id
            },
            onEntry = { openedTx = it.id },
            onEditPerson = { sheet = "person" },
            onOwed = { sheet = "owed" },
        ),
    )
    // 改动后可以撤销：底部提示里点“撤销”
    fun runUndoable(done: String, after: () -> Unit = {}, block: suspend () -> com.xiguli.jinkui.data.ledger.DebtChange) {
        viewModel.runUndoable(done, after, block)
    }
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
        "person" -> PersonEditSheet(
            person = person,
            others = data.accounts.filter { it.kind == person.account.kind && it.id != person.account.id && !it.archived },
            onDismiss = { sheet = null },
            onRename = { name -> runUndoable("已改名为$name", { sheet = null }) { ledger.renamePerson(person.account.id, name) } },
            onMerge = { into ->
                val target = data.accounts.firstOrNull { it.id == into }
                runUndoable("已合并到${target?.let(DebtMath::personOf).orEmpty()}", { sheet = null; onBack() }) { ledger.mergePerson(person.account.id, into) }
            },
        )
        "owed" -> PersonOwedSheet(person, today, { sheet = null }) { target, date ->
            runUndoable("已改成还欠 ${Money.format(target)}", { sheet = null }) { ledger.setPersonOwed(person.account.id, target, date) }
        }
        "settle" -> SettleSheet(person, today, { sheet = null }) { date ->
            runUndoable(if (lend) "已记坏账" else "已记免还", { sheet = null }) {
                // 每笔没还清的借款各记一笔（坏账 = 支出，免还 = 收入）；撤销时一起撤
                val before = ledger.data.value
                for (loan in person.open) ledger.forgiveLoan(loan.loan.id, loan.account.id, loan.remaining, date)
                val after = ledger.data.value
                val added = after.transactions.map { it.id }.toSet() - before.transactions.map { it.id }.toSet()
                com.xiguli.jinkui.data.ledger.DebtChange(deleteTransactions = added.toList())
            }
        }
    }
    openedTx?.let { id -> DebtRecordHost(id, data) { openedTx = null } }
    person.loans.firstOrNull { it.loan.id == payLoan }?.let { loan ->
        LoanPaySheet(loan, DebtMath.loanFunds(data.accounts), today, { payLoan = null }, DebtMath.lastLoanFund(data), balances) { principal, interest, fundId, date ->
            viewModel.run(if (lend) "已记收款" else "已记还款", { payLoan = null }) {
                ledger.loanPayment(loan.loan.id, loan.account.id, principal, interest, fundId, date)
            }
        }
    }
    person.loans.firstOrNull { it.loan.id == legacyLoan }?.let { loan ->
        LoanEditSheet(loan, today, { legacyLoan = null }) { edited ->
            viewModel.run("已保存", { legacyLoan = null }) { ledger.saveLoan(edited) }
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
        JkTopBar(person.person, onBack = actions.onBack) {
            com.xiguli.jinkui.ui.components.JkGlassIconButton(Icons.Outlined.Edit, "编辑${person.person}", actions.onEditPerson)
        }
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
                    // 点大字也能直接改还欠多少（同一木记账点余额改）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Money.format(person.remaining),
                            style = JkType.amountHero.copy(fontSize = 34.sp),
                            color = if (!lend && !person.settled || overdue) colors.expense else colors.textPrimary,
                            maxLines = 1,
                            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "修改欠款") { actions.onOwed() },
                        )
                        Spacer(Modifier.width(Space.x1))
                        DebtLink("修改欠款", actions.onOwed)
                    }
                    Spacer(Modifier.height(Space.x1))
                    DebtBar(person.progress)
                    Spacer(Modifier.height(Space.x05))
                    Text(
                        (if (lend) "已收回" else "已还") + " ${Money.format(person.repaid)} / 共 ${Money.format(person.total)}" +
                            if (person.interestPaid > 0) "，另外利息 ${Money.format(person.interestPaid)}" else "",
                        style = JkType.small,
                        color = colors.textSecondary,
                        // 右边是插画：小字只占左边 70%，长了就换行，不压在叶子上
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                    person.due?.takeIf { !person.settled }?.let { due ->
                        Text(
                            "约定 ${debtDate(due, today)} 还 · ${dueLabel(due, today)}",
                            style = JkType.small.copy(fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal),
                            color = if (overdue) colors.expense else colors.textSecondary,
                            modifier = Modifier.fillMaxWidth(0.7f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.x1_5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkButton(if (lend) "收款" else "还款", actions.onPay, enabled = !person.settled, modifier = Modifier.weight(1f))
                JkButton(if (lend) "再借出" else "再借入", actions.onMore, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
                JkButton(if (lend) "收不回了" else "不用还了", actions.onSettle, style = ButtonStyle.Ghost, enabled = !person.settled, modifier = Modifier.weight(1f))
            }
            Text(
                if (lend) "收款 = ${person.person}还我钱；收不回了 = 剩下的记成坏账。" else "还款 = 我还${person.person}钱；不用还了 = 对方说剩下的不用还（记成免还）。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x1),
            )

            DebtSection(if (lend) "每一笔借出" else "每一笔借入")
            for (loan in person.loans.sortedWith(compareBy<com.xiguli.jinkui.data.ledger.LoanView> { it.settled }.thenByDescending { it.loan.date })) {
                LoanCard(loan, today, onClick = { actions.onLoanCard(loan) }, onPay = { actions.onPayLoan(loan) })
                Spacer(Modifier.height(Space.x1))
            }

            DebtSection("来往记录")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                val entries = person.entries
                if (entries.isEmpty()) Text("还没有记录。", style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(Space.x2))
                entries.forEachIndexed { i, tx ->
                    if (i > 0) JkDivider(inset = Space.x2)
                    val loan = person.loans.first { l -> l.entries.any { it.id == tx.id } }
                    val kind = DebtMath.entryKind(loan, tx)
                    val correction = tx.note == com.xiguli.jinkui.data.ledger.PersonEdit.CORRECTION_NOTE
                    val label = if (correction) "修改欠款" else when (kind) {
                        LoanEntryKind.PRINCIPAL -> if (lend) "借出" else "借入"
                        LoanEntryKind.REPAY -> if (lend) "收款" else "还款"
                        LoanEntryKind.INTEREST -> "利息"
                        LoanEntryKind.WRITE_OFF -> if (lend) "坏账（收不回了）" else "免还（对方说不用还了）"
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
                            .clickable(role = Role.Button, onClickLabel = "编辑这条记录") { actions.onEntry(tx) }
                            .padding(horizontal = Space.x2, vertical = Space.x1)
                            .semantics(mergeDescendants = true) { contentDescription = "$label ${Money.format(tx.amount)}" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = JkType.bodyStrong, color = colors.textPrimary)
                            Text(
                                debtDate(LedgerMath.dateOf(tx.occurredAt, zone), today) +
                                    // 默认备注（“收款 · 小李”“利息 · 小李”）和标题重复，不再写一遍
                                    (if (tx.note.isNotBlank() && !correction && !isDefaultNote(tx.note, person.person)) " · ${tx.note}" else "") +
                                    if (correction) " · 不计入收支" else "",
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
                "“+”是借出 / 借入让欠款变多，“−”是收款 / 还款让欠款变少。点一条可以直接改（金额、对方、日期、账户都能改）或删除。",
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
            LoanEntryMath.overpayHint(lend, if (amount.isBlank()) 0L else Money.parse(amount), person.remaining)?.let {
                Text(it, style = JkType.small, color = Jk.colors.textSecondary)
            }
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
                else -> null
            }
            // 多收 / 多还的部分记成利息
            if (error == null) {
                val principal = minOf(p!!, person.remaining)
                onSubmit(principal, i!! + (p - principal), fund, date)
            }
        }, modifier = Modifier.fillMaxWidth())
    }
}


/**
 * 一笔借款的卡片（参考随手记的借贷人页：每笔一张卡，行内“还债”按钮）：
 * “借出 ¥800.00　剩 ¥800.00”/“9月1日借出 · 10月8日到期（已逾期 3 天）”/“约定利息 ¥20.00，已收 ¥0.00”。
 */
@Composable
fun LoanCard(loan: com.xiguli.jinkui.data.ledger.LoanView, today: LocalDate, onClick: () -> Unit, onPay: () -> Unit) {
    val colors = Jk.colors
    val lend = loan.direction == LoanDirection.LEND
    val overdue = loan.overdue(today)
    val label = (if (lend) "借出" else "借入") + if (!loan.stored || loan.loan.legacy) "（导入）" else ""
    JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "编辑这笔$label") { onClick() }
                .padding(horizontal = Space.x2, vertical = Space.x1_5)
                .semantics(mergeDescendants = true) { contentDescription = "$label ${Money.format(loan.principal)}，剩 ${Money.format(loan.remaining)}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$label ${Money.format(loan.principal)}", style = JkType.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(Space.x1))
                    Text(
                        if (loan.settled) "已还清" else "剩 ${Money.format(loan.remaining)}",
                        style = JkType.bodyStrong,
                        color = when {
                            loan.settled -> colors.textTertiary
                            overdue || !lend -> colors.expense
                            else -> colors.textPrimary
                        },
                    )
                }
                Text(
                    buildString {
                        append("${debtDate(loan.loan.date, today)}${if (lend) "借出" else "借入"}")
                        loan.loan.dueDate?.takeIf { !loan.settled }?.let { append(" · ${debtDate(it, today)}到期（${dueLabel(it, today)}）") }
                    },
                    style = JkType.caption.copy(fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal),
                    color = if (overdue) colors.expense else colors.textTertiary,
                )
                if (loan.loan.interest > 0) {
                    Text("约定利息 ${Money.format(loan.loan.interest)}，已${if (lend) "收" else "付"} ${Money.format(loan.interestPaid)}", style = JkType.caption, color = colors.textTertiary)
                }
                if (loan.loan.note.isNotBlank()) Text(loan.loan.note, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!loan.settled) {
                Spacer(Modifier.width(Space.x1))
                JkButton(if (lend) "收款" else "还款", onPay, style = ButtonStyle.Secondary, compact = true)
            }
        }
    }
}

/** 收不回了（坏账，借出）/ 对方说不用还了（免还，借入）：选日期，剩下的记成一笔支出 / 收入。 */
@Composable
fun SettleSheet(person: PersonDebt, today: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val lend = person.direction == LoanDirection.LEND
    var date by remember { mutableStateOf(today) }
    JkSheet(onDismiss = onDismiss, title = if (lend) "收不回了（坏账）" else "对方说不用还了（免还）") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
            Text(
                if (lend) "${person.person}还欠你的 ${Money.format(person.remaining)} 不再收回：记成一笔“其他支出”（坏账），这个人就算还清了。"
                else "你还欠${person.person}的 ${Money.format(person.remaining)} 不用还了：记成一笔“其他收入”（免还），这个人就算还清了。",
                style = JkType.body,
                color = Jk.colors.textPrimary,
            )
            DateField(if (lend) "哪天算收不回" else "哪天说的不用还", date, today, { date = it })
            Text("记错了可以在来往记录里点那一条改金额、日期或删除，也可以马上点“撤销”。", style = JkType.caption, color = Jk.colors.textTertiary)
        }
        Spacer(Modifier.height(Space.x2))
        JkButton(if (lend) "记为坏账 ${Money.format(person.remaining)}" else "记为免还 ${Money.format(person.remaining)}", { onConfirm(date) }, modifier = Modifier.fillMaxWidth())
    }
}


/** 记借贷时自动写上的备注（“借出 · 小李”“收款 · 小李”“利息 · 小李”……），来往记录里不重复显示。 */
internal fun isDefaultNote(note: String, person: String): Boolean =
    listOf("借出", "借入", "收款", "还款", "利息", "免除", "对方免还", "坏账", "免还").any { note == "$it · $person" }
