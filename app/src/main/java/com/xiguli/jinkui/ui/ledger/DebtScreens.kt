package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.CreditView
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.DebtOverview
import com.xiguli.jinkui.data.ledger.DueItem
import com.xiguli.jinkui.data.ledger.DueKind
import com.xiguli.jinkui.data.ledger.InstallmentView
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.LoanEntryKind
import com.xiguli.jinkui.data.ledger.LoanView
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkGlassIconButton
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── 状态 ──────────────────────────────────────────────────────────

/** 资金账户：能用来还款、借出、收款的账户（不是信用、借贷、余额调整账户，也没有停用）。 */
fun fundAccounts(data: LedgerData): List<LedgerAccount> = data.accounts
    .filter { !it.archived && it.kind.countsInAssets && it.kind != com.xiguli.jinkui.data.ledger.AccountKind.CREDIT && LoanDirection.of(it.kind) == null }
    .sortedBy { it.position }

class DebtViewModel(private val container: AppContainer) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate get() = LocalDate.now(zone)

    val ui: StateFlow<DebtOverview> = container.ledger.data
        .map { DebtMath.overview(it, LocalDate.now(zone), zone) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DebtMath.overview(container.ledger.data.value, LocalDate.now(zone), zone))

    /** 执行一次改动；校验没通过时把原因提示给用户。成功时显示 [done] 并调用 [after]。 */
    fun run(done: String?, after: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                done?.let { container.messages.show(it) }
                after()
            } catch (e: LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /** 1.5.0-alpha03：执行一次可撤销的借贷改动；成功后底部提示带“撤销”。 */
    fun runUndoable(done: String, after: () -> Unit = {}, block: suspend () -> com.xiguli.jinkui.data.ledger.DebtChange) {
        viewModelScope.launch {
            try {
                val undo = block()
                after()
                if (undo.isEmpty) container.messages.show(done)
                else container.messages.show(done, "撤销") { container.scope.launch { runCatching { container.ledger.undoDebtChange(undo) } } }
            } catch (e: LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }
}

// ── 欠款 / 账单总览 ────────────────────────────────────────────────

class DebtOverviewActions(
    val onBack: () -> Unit = {},
    val onCredit: (String) -> Unit = {},
    val onLoans: () -> Unit = {},
    val onItem: (DueItem) -> Unit = {},
    val onAccounts: () -> Unit = {},
    /** 1.4.0-alpha04：空状态与“借贷”一节里的“记一笔借出 / 借入”。 */
    val onNewLoan: (LoanDirection) -> Unit = {},
    /** 1.4.0-alpha04：添加信用卡（打开“添加账户”，种类预选信用卡）。 */
    val onAddCredit: () -> Unit = {},
    /** 1.4.0-alpha04：打开某一个人（对方账户）的借贷。 */
    val onPerson: (String) -> Unit = {},
)

@Composable
fun DebtOverviewScreen(
    onBack: () -> Unit,
    onCredit: (String) -> Unit,
    onLoans: () -> Unit,
    onAccounts: () -> Unit,
    onAddCredit: () -> Unit = onAccounts,
    onPerson: (String) -> Unit = {},
    startNew: LoanDirection? = null,
    initialTab: LoanDirection = LoanDirection.LEND,
) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { DebtViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val data by container.ledger.data.collectAsStateWithLifecycle()
    var newLoan by remember { mutableStateOf(startNew) }
    DebtOverviewContent(
        ui,
        DebtOverviewActions(
            onBack = onBack,
            onCredit = onCredit,
            onLoans = onLoans,
            onAccounts = onAccounts,
            onItem = { item -> if (item.kind == DueKind.LOAN) onPerson(item.accountId) else onCredit(item.accountId) },
            onNewLoan = { newLoan = it },
            onAddCredit = onAddCredit,
            onPerson = onPerson,
        ),
        initialTab = startNew ?: initialTab,
    )
    newLoan?.let { direction ->
        NewLoanSheet(
            people = data.accounts.filter { LoanDirection.of(it.kind) != null },
            funds = com.xiguli.jinkui.data.ledger.DebtMath.loanFunds(data.accounts),
            today = ui.today,
            initialDirection = direction,
            initialFund = com.xiguli.jinkui.data.ledger.DebtMath.lastLoanFund(data),
            balances = remember(data) { com.xiguli.jinkui.data.ledger.LedgerMath.balances(data.accounts, data.transactions) },
            onDismiss = { newLoan = null },
        ) { d, person, accountId, amount, fundId, date, due, interest, note ->
            viewModel.run("已记一笔${d.label} ${Money.format(amount)}", { newLoan = null }) {
                container.ledger.createLoan(d, person, accountId, amount, fundId, date, due, interest, note)
            }
        }
    }
}

/** 第一次打开（没有信用账户、也没有任何借贷）：说明这里能记什么，三个按钮直接开始。 */
@Composable
fun DebtEmptyState(actions: DebtOverviewActions, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    BotanicalCard(modifier.fillMaxWidth(), layout = BotanicalLayout.SPRIG) {
        Column(Modifier.padding(Space.x2_5)) {
            Text("还没有欠款与借贷", style = JkType.section, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Space.x05))
            Text(
                "借钱给别人、向别人借钱，或者信用卡 / 花呗的账单与分期，都记在这里。到期前会在账本首页提醒；全部只存在本机加密库里。",
                style = JkType.small,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(Space.x2))
            JkButton("记一笔借出", { actions.onNewLoan(LoanDirection.LEND) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.x1))
            JkButton("记一笔借入", { actions.onNewLoan(LoanDirection.BORROW) }, style = ButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.x1))
            JkButton("添加信用卡", actions.onAddCredit, style = ButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: String, tone: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = JkType.caption, color = Jk.colors.textSecondary)
        Text(value, style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = tone, maxLines = 1)
    }
}

/** 总览里的一张信用账户卡：已用 / 额度、最新账单剩余与到期日。 */
@Composable
internal fun CreditCard(view: CreditView, today: LocalDate, onClick: () -> Unit) {
    val colors = Jk.colors
    val latest = view.latest
    val overdue = latest?.overdue(today) == true
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "查看账单", onClick = onClick)
            .then(Modifier),
    ) {
        JkCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(accountIcon(view.account.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Space.x1_5))
                Text(view.account.name, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        view.settings == null -> "设置账单日 ›"
                        latest == null || latest.cleared -> "已还清"
                        else -> dueLabel(latest.dueDate, today)
                    },
                    style = JkType.caption.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        overdue -> colors.expense
                        view.settings == null -> colors.accent
                        else -> colors.textTertiary
                    },
                )
            }
            Spacer(Modifier.height(Space.x1_5))
            if (view.settings == null) {
                // 没设账单日也照样显示欠款，不让它“消失”
                DebtFigures(
                    Triple("当前欠款", Money.format(view.debt), if (view.debt > 0) colors.expense else null),
                    Triple("账单", "未设置", null),
                    Triple("可用额度", view.available?.let { Money.format(it) } ?: "—", null),
                )
                Spacer(Modifier.height(Space.x05))
                Text("还没有账单日与还款日：点这里设置后按流水算出每期账单。", style = JkType.caption, color = colors.textTertiary)
            } else {
                DebtFigures(
                    Triple("本期剩余", Money.format(latest?.remaining ?: 0L), if (overdue) colors.expense else null),
                    Triple("未出账单", Money.format(view.open?.amount ?: 0L), null),
                    Triple("当前欠款", Money.format(view.debt), null),
                )
                if (view.defaulted) {
                    Spacer(Modifier.height(Space.x05))
                    Text(
                        "默认 · 每月 ${view.settings?.statementDay} 号出账、${view.settings?.dueDay} 号还款，可在账单设置里修改",
                        style = JkType.caption,
                        color = colors.textTertiary,
                    )
                }
            }
            view.settings?.limit?.takeIf { it > 0 }?.let { limit ->
                Spacer(Modifier.height(Space.x1_5))
                DebtBar(view.used.toFloat() / limit, danger = view.used > limit)
                Spacer(Modifier.height(Space.x05))
                Text("已用 ${Money.format(view.used)} / 额度 ${Money.format(limit)}", style = JkType.amountCaption, color = colors.textSecondary)
            }
        }
    }
}

// ── 信用账户：账单 ─────────────────────────────────────────────────

class CreditActions(
    val onBack: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRepay: () -> Unit = {},
    val onInstallment: () -> Unit = {},
    val onOpenInstallment: (InstallmentView) -> Unit = {},
)

@Composable
fun CreditStatementScreen(accountId: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { DebtViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val view = ui.credits.firstOrNull { it.account.id == accountId }
    if (view == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            JkTopBar("账单", onBack = onBack)
            Text("找不到这个信用账户。", style = JkType.body, color = Jk.colors.textTertiary, modifier = Modifier.padding(Space.x3))
        }
        return
    }
    val today = ui.today
    var sheet by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<String?>(null) }
    CreditStatementContent(
        view, today,
        CreditActions(
            onBack = onBack,
            onSettings = { sheet = "settings" },
            onRepay = { sheet = "repay" },
            onInstallment = { if (view.settings == null) sheet = "settings" else sheet = "installment" },
            onOpenInstallment = { opened = it.plan.id },
        ),
    )
    var editingPlan by remember { mutableStateOf<String?>(null) }
    val ledger = container.ledger
    when (sheet) {
        "settings" -> CreditSettingsSheet(view.account, today, { sheet = null }, initial = view.settings) { settings ->
            viewModel.run(if (settings == null) "已清除账单设置" else "已保存账单设置", { sheet = null }) { ledger.saveCreditSettings(accountId, settings) }
        }
        "repay" -> RepaySheet(view.account, view.latest?.remaining ?: 0L, view.used, fundAccounts(data), today, { sheet = null }) { from, amount, date ->
            viewModel.run("已记一笔还款 ${Money.format(amount)}", { sheet = null }) { ledger.repayCredit(accountId, from, amount, date) }
        }
        "installment" -> InstallmentSheet(view.account, today, { sheet = null }) { plan, record ->
            viewModel.run("已创建分期「${plan.title}」", { sheet = null }) { ledger.createInstallment(plan, record) }
        }
    }
    view.installments.firstOrNull { it.plan.id == opened }?.let { inst ->
        InstallmentDetailSheet(
            inst, today,
            onDismiss = { opened = null },
            onPayoff = { viewModel.run("已提前结清，剩余本金并入当期账单", { opened = null }) { ledger.payoffInstallment(inst.plan.id, today) } },
            onDelete = { viewModel.run("已删除分期「${inst.plan.title}」", { opened = null }) { ledger.deleteDebt(inst.plan.id) } },
            onEdit = { editingPlan = inst.plan.id },
        )
    }
    view.installments.firstOrNull { it.plan.id == editingPlan }?.let { inst ->
        InstallmentSheet(view.account, today, { editingPlan = null }, existing = inst.plan) { plan, _ ->
            viewModel.run("已保存分期「${plan.title}」", { editingPlan = null }) { ledger.updateInstallment(plan) }
        }
    }
}

/**
 * 信用账户的账单（无状态）：本期应还头图（应还 / 已还 / 剩余、到期日，逾期标红）与“还款”，
 * 额度与免息期，本期未出账单，分期（已还 X/N 期），已出账单列表。
 */
@Composable
fun CreditStatementContent(view: CreditView, today: LocalDate, actions: CreditActions) {
    val colors = Jk.colors
    val latest = view.latest
    val overdue = latest?.overdue(today) == true
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(view.account.name, onBack = actions.onBack) { DebtLink("账单设置", actions.onSettings) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x2)
                .navigationBarsPadding()
                .padding(bottom = Space.x4),
        ) {
            if (view.settings == null) {
                JkCard(Modifier.fillMaxWidth()) {
                    Text("还没有设置账单日", style = JkType.bodyStrong, color = colors.textPrimary)
                    Spacer(Modifier.height(Space.x05))
                    Text("设好账单日与还款日后，按流水算出每期账单的应还、已还与到期日；也可以在这里记分期。", style = JkType.small, color = colors.textTertiary)
                    Spacer(Modifier.height(Space.x1_5))
                    JkButton("设置账单日与还款日", actions.onSettings, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Space.x2))
            } else if (view.defaulted) {
                JkCard(Modifier.fillMaxWidth()) {
                    Text("默认账单日：每月 ${view.settings?.statementDay} 号出账、${view.settings?.dueDay} 号还款", style = JkType.bodyStrong, color = colors.textPrimary)
                    Spacer(Modifier.height(Space.x05))
                    Text("与一木记账一样按花呗的默认日期算出账单；不对的话改成你自己的账单日与还款日。", style = JkType.small, color = colors.textTertiary)
                    Spacer(Modifier.height(Space.x1_5))
                    JkButton("修改账单日", actions.onSettings, style = ButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Space.x2))
            }
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.SPRIG) {
                Column(Modifier.padding(Space.x2_5)) {
                    Text(
                        if (latest != null) "${latest.statementDate.monthValue}月账单 · 剩余应还(元)" else "当前欠款(元)",
                        style = JkType.small.copy(fontWeight = FontWeight.Medium),
                        color = colors.textSecondary,
                    )
                    Text(
                        Money.format(latest?.remaining ?: view.used, symbol = false),
                        style = JkType.amountHero.copy(fontSize = 32.sp),
                        color = if (overdue) colors.expense else colors.textPrimary,
                        maxLines = 1,
                    )
                    if (latest != null) {
                        Text(
                            if (latest.cleared) "已还清 · ${debtDate(latest.dueDate, today)}到期" else "${debtDate(latest.dueDate, today)}到期 · ${dueLabel(latest.dueDate, today)}",
                            style = JkType.small.copy(fontWeight = FontWeight.Bold),
                            color = if (overdue) colors.expense else colors.accent,
                        )
                        Spacer(Modifier.height(Space.x1_5))
                        DebtFigures(
                            Triple("应还", Money.format(latest.owed), null),
                            Triple("已还", Money.format(latest.paid), null),
                            Triple("剩余", Money.format(latest.remaining), if (overdue) colors.expense else null),
                        )
                    }
                    Spacer(Modifier.height(Space.x2))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                        JkButton("还款", actions.onRepay, modifier = Modifier.weight(1f))
                        JkButton("分期", actions.onInstallment, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(Space.x2))
            JkCard(Modifier.fillMaxWidth()) {
                val limit = view.settings?.limit?.takeIf { it > 0 }
                DebtFigures(
                    Triple("已用额度", Money.format(view.used), null),
                    Triple("可用额度", view.available?.let { Money.format(it) } ?: "—", if ((view.available ?: 0L) < 0) colors.expense else null),
                    Triple("总额度", limit?.let { Money.format(it) } ?: "未填", null),
                )
                if (limit != null) {
                    Spacer(Modifier.height(Space.x1))
                    DebtBar(view.used.toFloat() / limit, danger = view.used > limit)
                }
                val settings = view.settings
                if (settings != null && view.freeUntil != null) {
                    Spacer(Modifier.height(Space.x1_5))
                    Text(
                        "账单日每月 ${settings.statementDay} 号 · 还款日" +
                            (if (settings.dueMode == com.xiguli.jinkui.data.ledger.DueMode.DAY_OF_MONTH) "每月 ${settings.dueDay} 号" else "出账后第 ${settings.dueDay} 天"),
                        style = JkType.small,
                        color = colors.textSecondary,
                    )
                    Text(
                        "免息期：今天消费 ${debtDate(view.freeUntil, today)}前还，免息 ${view.freeDays} 天；最长 ${view.maxFreeDays} 天",
                        style = JkType.small,
                        color = colors.textSecondary,
                    )
                }
            }

            view.open?.let { open ->
                DebtSection("本期未出账单")
                JkCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${debtDate(open.start, today)} – ${debtDate(open.statementDate, today)}", style = JkType.bodyStrong, color = colors.textPrimary)
                            Text(
                                "${debtDate(open.statementDate, today)}出账 · ${debtDate(open.dueDate, today)}到期" +
                                    if (open.installmentPart > 0) " · 含分期 ${Money.format(open.installmentPart)}" else "",
                                style = JkType.caption,
                                color = colors.textTertiary,
                            )
                        }
                        Text(Money.format(open.amount), style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    }
                }
            }

            DebtSection("分期") { if (view.settings != null) DebtLink("新增分期", actions.onInstallment) }
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                if (view.installments.isEmpty()) {
                    Text("没有分期。", style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(Space.x2))
                }
                view.installments.forEachIndexed { i, inst ->
                    if (i > 0) JkDivider(inset = Space.x2)
                    InstallmentRow(inst, today) { actions.onOpenInstallment(inst) }
                }
            }

            DebtSection("已出账单")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                if (view.statements.isEmpty()) {
                    Text(if (view.settings == null) "设置账单日后按流水算出。" else "还没有出过账单。", style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(Space.x2))
                }
                view.statements.forEachIndexed { i, st ->
                    if (i > 0) JkDivider(inset = Space.x2)
                    val late = st.overdue(today) && i == 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .padding(horizontal = Space.x2, vertical = Space.x1)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${st.statementDate.year}年${st.statementDate.monthValue}月账单，应还 ${Money.format(st.owed)}，已还 ${Money.format(st.paid)}，剩余 ${Money.format(st.remaining)}"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${st.statementDate.year}年${st.statementDate.monthValue}月账单", style = JkType.bodyStrong, color = colors.textPrimary)
                            Text(
                                "${debtDate(st.cycleStart, today)} – ${debtDate(st.statementDate, today)} · ${debtDate(st.dueDate, today)}到期" +
                                    if (st.installmentPart > 0) " · 含分期 ${Money.format(st.installmentPart)}" else "",
                                style = JkType.caption,
                                color = colors.textTertiary,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(st.owed), style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = if (late) colors.expense else colors.textPrimary)
                            Text(
                                when {
                                    st.cleared -> if (st.owed == 0L) "无需还款" else "已还清"
                                    i == 0 -> "已还 ${Money.format(st.paid)} · 剩余 ${Money.format(st.remaining)}"
                                    else -> "剩余 ${Money.format(st.remaining)} 已并入下期"
                                },
                                style = JkType.caption,
                                color = if (late) colors.expense else colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallmentRow(inst: InstallmentView, today: LocalDate, onClick: () -> Unit) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(inst.plan.title, style = JkType.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "已还 ${inst.paidCount}/${inst.total} 期",
                style = JkType.small.copy(fontWeight = FontWeight.Bold),
                color = if (inst.settled) colors.textTertiary else colors.accent,
            )
        }
        Spacer(Modifier.height(Space.x05))
        DebtBar(inst.paidCount.toFloat() / inst.total)
        Spacer(Modifier.height(Space.x05))
        Text(
            buildString {
                append("总额 ${Money.format(inst.plan.total)}")
                if (inst.plan.feePerPeriod > 0) append(" · 每期手续费 ${Money.format(inst.plan.feePerPeriod)}")
                if (inst.earlyPaid) append(" · 提前结清")
                inst.next?.let { append(" · 下期 ${Money.format(it.amount)}（${debtDate(it.dueDate, today)}到期）") }
                    ?: append(" · 已结清")
            },
            style = JkType.caption,
            color = colors.textTertiary,
        )
    }
}

/** 分期详情：每一期的本金、手续费、计入哪期账单与是否已还；提前结清、删除。 */
@Composable
fun InstallmentDetailSheet(inst: InstallmentView, today: LocalDate, onDismiss: () -> Unit, onPayoff: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit = {}) {
    val colors = Jk.colors
    var confirm by remember { mutableStateOf<String?>(null) }
    JkSheet(onDismiss = onDismiss, title = "${inst.plan.title} · 已还 ${inst.paidCount}/${inst.total} 期") {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            for (p in inst.periods) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${p.index + 1} 期", style = JkType.small, color = colors.textSecondary, modifier = Modifier.width(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "本金 ${Money.format(p.principal)}" + if (p.fee > 0) " + 手续费 ${Money.format(p.fee)}" else if (p.waived) "（免手续费）" else "",
                            style = JkType.small,
                            color = colors.textPrimary,
                        )
                        Text("计入${p.statementDate.monthValue}月账单 · ${debtDate(p.dueDate, today)}到期", style = JkType.caption, color = colors.textTertiary)
                    }
                    Text(if (p.paid) "已还" else "待还", style = JkType.caption.copy(fontWeight = FontWeight.Bold), color = if (p.paid) colors.textTertiary else colors.accent)
                }
            }
        }
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkButton("删除", { confirm = "delete" }, style = ButtonStyle.Danger, modifier = Modifier.weight(1f))
            JkButton("编辑", onEdit, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
            JkButton("提前结清", { confirm = "payoff" }, enabled = !inst.earlyPaid && !inst.settled, modifier = Modifier.weight(1f))
        }
    }
    when (confirm) {
        "payoff" -> JkDialog("提前结清这笔分期？", { confirm = null }, "提前结清", { confirm = null; onPayoff() }) {
            Text("还没入账的本金 ${Money.format(inst.periods.filter { it.billingDate > today }.sumOf { it.principal })} 并入今天所在的这一期账单，之后各期的手续费不再收取。")
        }
        "delete" -> JkDialog("删除这笔分期？", { confirm = null }, "删除", { confirm = null; onDelete() }, danger = true) {
            Text("分期时记上的消费与手续费流水会一起删除；已经还的款保留。")
        }
    }
}

// ── 借贷 ──────────────────────────────────────────────────────────

/** 给账本首页用：3 天内到期或已逾期的项。 */
fun debtReminders(data: LedgerData, today: LocalDate, zone: ZoneId): List<DueItem> =
    if (data.accounts.none { it.kind == com.xiguli.jinkui.data.ledger.AccountKind.CREDIT || LoanDirection.of(it.kind) != null }) emptyList()
    else DebtMath.overview(data, today, zone).reminders
