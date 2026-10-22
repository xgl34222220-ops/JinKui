package com.xiguli.jinkui.ui.ledger

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space

/** 资产总览里的一个账户。 */
@Immutable
data class AssetRow(val account: LedgerAccount, val balance: Long)

/** 资产总览：全部由现有账户与流水算出，不新增任何数据。 */
@Immutable
data class AssetsUi(
    val net: Long,
    val assets: Long,
    val liabilities: Long,
    /** 现金 + 银行卡。 */
    val savings: Long,
    /** 支付宝 + 微信 + 其他。 */
    val wallets: Long,
    /** 信用卡欠款（正数）。 */
    val creditDebt: Long,
    val credit: List<AssetRow>,
    val others: List<AssetRow>,
    /** 借出款（应收）合计。 */
    val receivable: Long = 0L,
    /** 借入款（应付）合计（正数）。 */
    val payable: Long = 0L,
    /** 借贷：每个对方一个应收 / 应付账户。 */
    val loans: List<AssetRow> = emptyList(),
    /** 1.5.0-alpha03：信用卡 / 花呗的白话状态（账户 ID → “10月23日前还 ¥1,288.00”）。 */
    val creditStatus: Map<String, String> = emptyMap(),
)

fun buildAssetsUi(data: LedgerData, today: java.time.LocalDate = java.time.LocalDate.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): AssetsUi {
    val balances = LedgerMath.balances(data.accounts, data.transactions)
    val rows = data.accounts
        .sortedBy { it.position }
        .map { AssetRow(it, balances[it.id] ?: it.openingBalance) }
        // 停用且余额为 0 的账户不再列出；“余额调整”内部账户（导入的余额校准）不计入资产
        .filter { (!it.account.archived || it.balance != 0L) && it.account.kind.countsInAssets }
    val sumOf = { kinds: Set<AccountKind> -> rows.filter { it.account.kind in kinds }.sumOf { it.balance } }
    // 与一木记账同样的三组：资金账户 / 信用账户（信用卡、花呗）/ 借贷（应收、应付，按对方分开）
    val credit = rows.filter { it.account.kind == AccountKind.CREDIT }
    val loans = rows.filter { it.account.kind == AccountKind.RECEIVABLE || it.account.kind == AccountKind.PAYABLE }
    return AssetsUi(
        net = LedgerMath.netWorth(data),
        assets = rows.filter { it.balance > 0 }.sumOf { it.balance },
        liabilities = rows.filter { it.balance < 0 }.sumOf { -it.balance },
        savings = sumOf(setOf(AccountKind.CASH, AccountKind.BANK)),
        wallets = sumOf(setOf(AccountKind.ALIPAY, AccountKind.WECHAT, AccountKind.OTHER, AccountKind.INVEST)),
        creditDebt = credit.filter { it.balance < 0 }.sumOf { -it.balance },
        credit = credit,
        others = rows.filter { it !in credit && it !in loans },
        receivable = sumOf(setOf(AccountKind.RECEIVABLE)),
        payable = -sumOf(setOf(AccountKind.PAYABLE)),
        loans = loans,
        creditStatus = credit.associate { row ->
            row.account.id to com.xiguli.jinkui.data.ledger.DebtMath.creditStatus(
                com.xiguli.jinkui.data.ledger.DebtMath.credit(row.account, data.transactions, data.installments, today, zone), today,
            )
        },
    )
}

@Composable
fun LedgerAssetsScreen(
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onDebts: () -> Unit = {},
    onCredit: (String) -> Unit = {},
    onLoans: () -> Unit = {},
    onNewLoan: (com.xiguli.jinkui.data.ledger.LoanDirection) -> Unit = {},
    /** 1.5.0-alpha01：点借贷里的一个人，打开“某一个人”页。 */
    onPerson: (String) -> Unit = {},
    /** 1.5.0-alpha02：点资金账户 / 信用卡 → 账户详情。 */
    onAccount: ((String) -> Unit)? = null,
) {
    val container = LocalContainer.current
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val ui = androidx.compose.runtime.remember(data) { buildAssetsUi(data) }
    val monthDue = androidx.compose.runtime.remember(data) {
        val zone = java.time.ZoneId.systemDefault()
        com.xiguli.jinkui.data.ledger.DebtMath.overview(data, java.time.LocalDate.now(zone), zone).let { it.monthDue to (it.overdue > 0) }
    }
    // 1.5.0-alpha01：长按账户 → 修改余额（与“账户与余额”同一个面板）
    val accountsVm = com.xiguli.jinkui.ui.nav.jkViewModel { LedgerAccountsViewModel(container) }
    val accountsUi by accountsVm.ui.collectAsStateWithLifecycle()
    var adjusting by androidx.compose.runtime.remember { mutableStateOf<LedgerAccount?>(null) }
    LedgerAssetsContent(
        ui, onBack, onAccounts, onDebts, onCredit, onLoans, monthDue.first, monthDue.second, onNewLoan,
        onPerson = onPerson, onAdjust = { adjusting = it }, onAccount = onAccount,
    )
    adjusting?.let { account ->
        BalanceAdjustSheet(
            account = account,
            current = accountsUi.balances[account.id] ?: 0L,
            recent = accountsUi.recentAdjusts[account.id].orEmpty(),
            onDismiss = { adjusting = null },
            onSave = { target, mode -> accountsVm.adjust(account, target, mode) { adjusting = null } },
            onUndo = { accountsVm.undo(it) },
        )
    }
}

/**
 * 资产（无状态）。1.5.0-alpha01 重做（参考钱迹资产页、一木记账“名下资产一目了然”，只借鉴交互）：
 * 头图只有净资产与“资产（我有的）/ 负债（我欠的）”，一行“净资产 = 资产 − 负债”；一行“借钱 / 欠款”入口（本月要还）；
 * 然后三组：资金账户（每个账户多少钱）→ 信用卡 / 花呗（各欠多少）→ 借给别人 / 欠别人（一人一行）。
 * 资金账户与信用卡长按 → 修改余额。不再有“储蓄现金 / 支付账户 / 信用欠款”三格和两颗记借贷按钮（借贷在欠款页与记一笔里）。
 */
@Composable
fun LedgerAssetsContent(
    ui: AssetsUi,
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onDebts: () -> Unit = {},
    onCredit: (String) -> Unit = {},
    onLoans: () -> Unit = {},
    /** “借钱 / 欠款”入口上显示的本月要还（1.4）。 */
    monthDue: Long = 0L,
    overdue: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onNewLoan: (com.xiguli.jinkui.data.ledger.LoanDirection) -> Unit = {},
    onPerson: ((String) -> Unit)? = null,
    onAdjust: (LedgerAccount) -> Unit = {},
    onAccount: ((String) -> Unit)? = null,
) {
    val colors = Jk.colors
    var hidden by rememberSaveable { mutableStateOf(false) }
    val show: (Long) -> String = { if (hidden) "****" else Money.format(it, symbol = false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar("资产", onBack = onBack, actions = {
            Text(
                "管理账户",
                style = JkType.small.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
                modifier = Modifier
                    .heightIn(min = JkSize.touch)
                    .clip(Radius.pill)
                    .clickable(role = Role.Button, onClick = onAccounts)
                    .padding(horizontal = Space.x1_5, vertical = Space.x1_5),
            )
        })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x2)
                .navigationBarsPadding()
                .padding(bottom = Space.x4),
        ) {
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.TOP_RIGHT) {
                Column(Modifier.padding(Space.x2_5)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("净资产(元)", style = JkType.small.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
                        Text(
                            if (hidden) "显示" else "隐藏",
                            style = JkType.caption.copy(fontWeight = FontWeight.Bold),
                            color = colors.accent,
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(Radius.pill)
                                .clickable(role = Role.Button, onClickLabel = if (hidden) "显示金额" else "隐藏金额") { hidden = !hidden }
                                .padding(horizontal = Space.x1, vertical = Space.x1_5),
                        )
                    }
                    Text(show(ui.net), style = JkType.amountHero.copy(fontSize = 32.sp), color = if (ui.net < 0) colors.danger else colors.accent, maxLines = 1)
                    Spacer(Modifier.height(Space.x1_5))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                        HeroCell("资产（我有的）", show(ui.assets), colors.textPrimary, Modifier.weight(1f))
                        HeroCell("负债（我欠的）", show(ui.liabilities), if (ui.liabilities > 0) colors.expense else colors.textPrimary, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Space.x05))
                    Text("净资产 = 资产 − 负债", style = JkType.caption, color = colors.textTertiary)
                }
            }
            Spacer(Modifier.height(Space.x1_5))
            // 借钱 / 欠款入口（信用账单、分期、借贷与到期提醒）
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(Radius.card)
                    .background(colors.surface)
                    .clickable(role = Role.Button, onClick = onDebts)
                    .padding(horizontal = Space.x2, vertical = Space.x1_5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.xiguli.jinkui.ui.components.MintIcon(com.xiguli.jinkui.ui.components.MintGlyph.BILL, size = 26.dp, tint = colors.textPrimary)
                Spacer(Modifier.width(Space.x1_5))
                Column(Modifier.weight(1f)) {
                    Text("借钱 / 欠款", style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text("谁欠我、我欠谁，信用卡和花呗的账单", style = JkType.caption, color = colors.textTertiary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("本月要还", style = JkType.caption, color = colors.textTertiary)
                    Text(show(monthDue), style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = if (overdue) colors.expense else colors.textPrimary)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
            // 资金账户：每个账户多少钱
            Spacer(Modifier.height(Space.x2))
            JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.x1)) {
                GroupTitle("资金账户", "合计 ${show(ui.others.sumOf { it.balance })}")
                if (ui.others.isEmpty()) {
                    Text("还没有账户，点右上角“管理账户”添加", style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(Space.x2))
                }
                ui.others.forEachIndexed { i, row ->
                    AssetLine(
                        row = row,
                        subtitle = row.account.kind.label + if (row.account.archived) " · 已停用" else "",
                        amount = show(row.balance),
                        tone = if (row.balance < 0) colors.danger else colors.textPrimary,
                        onClick = { if (onAccount != null) onAccount(row.account.id) else onAccounts() },
                        onLongClick = { onAdjust(row.account) },
                        clickLabel = if (onAccount != null) "查看流水" else null,
                    )
                    if (i < ui.others.lastIndex) JkDivider(inset = Space.x2 + JkSize.chip + Space.x1_5)
                }
            }
            if (ui.credit.isNotEmpty()) {
                Spacer(Modifier.height(Space.x2))
                JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.x1)) {
                    GroupTitle("信用卡 / 花呗", "一共欠 ${show(ui.creditDebt)}")
                    ui.credit.forEachIndexed { i, row ->
                        val debt = -row.balance
                        AssetLine(
                            row = row,
                            subtitle = if (debt < 0) "多还了 ${show(-debt)}" else "欠 · " + (ui.creditStatus[row.account.id] ?: ""),
                            amount = show(kotlin.math.abs(debt)),
                            tone = if (debt > 0) colors.expense else colors.textPrimary,
                            onClick = { if (onAccount != null) onAccount(row.account.id) else onCredit(row.account.id) },
                            onLongClick = { onAdjust(row.account) },
                            clickLabel = if (onAccount != null) "查看流水与账单" else "查看账单",
                        )
                        if (i < ui.credit.lastIndex) JkDivider(inset = Space.x2 + JkSize.chip + Space.x1_5)
                    }
                }
            }
            if (ui.loans.isNotEmpty()) {
                Spacer(Modifier.height(Space.x2))
                JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.x1)) {
                    GroupTitle("借给别人 / 欠别人", "别人欠我 ${show(ui.receivable)} · 我欠别人 ${show(ui.payable)}")
                    ui.loans.forEachIndexed { i, row ->
                        val receivable = row.account.kind == AccountKind.RECEIVABLE
                        AssetLine(
                            row = row,
                            title = com.xiguli.jinkui.data.ledger.DebtMath.personOf(row.account),
                            subtitle = if (receivable) "他欠我" else "我欠他",
                            amount = show(kotlin.math.abs(row.balance)),
                            tone = if (receivable) colors.textPrimary else colors.expense,
                            onClick = { if (onPerson != null) onPerson(row.account.id) else onLoans() },
                        )
                        if (i < ui.loans.lastIndex) JkDivider(inset = Space.x2 + JkSize.chip + Space.x1_5)
                    }
                }
            }
            Text(
                "余额随记账自动更新；点账户看它的流水，和实际不一样时长按 → 修改余额。信用卡、花呗的欠款和我欠别人的钱算负债，别人欠我的钱算资产。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = Space.x05, vertical = Space.x2),
            )
        }
    }
}

@Composable
private fun GroupTitle(title: String, summary: String) {
    val colors = Jk.colors
    Row(Modifier.padding(horizontal = Space.x2, vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = JkType.section, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
        Text(summary, style = JkType.small, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun AssetLine(
    row: AssetRow,
    subtitle: String,
    amount: String,
    tone: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    title: String = row.account.name,
) {
    val colors = Jk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(
                if (onLongClick != null) Modifier.combinedClickable(onClickLabel = clickLabel, onLongClickLabel = "修改余额", onLongClick = onLongClick, onClick = onClick)
                else Modifier.clickable(onClickLabel = clickLabel, onClick = onClick),
            )
            .padding(horizontal = Space.x2, vertical = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(JkSize.chip).clip(CircleShape).background(colors.surfaceAlt), contentAlignment = Alignment.Center) {
            Icon(accountIcon(row.account.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Text(title, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = JkType.caption, color = colors.textTertiary, maxLines = 1)
        }
        Text(amount, style = JkType.amount, color = tone)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HeroCell(label: String, value: String, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = JkType.small.copy(fontWeight = FontWeight.Medium), color = Jk.colors.textSecondary, maxLines = 1)
        Text(value, style = JkType.amount.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), color = tone, maxLines = 1)
    }
}

