package com.xiguli.jinkui.ui.ledger

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
)

fun buildAssetsUi(data: LedgerData): AssetsUi {
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
        net = rows.sumOf { it.balance },
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
    )
}

@Composable
fun LedgerAssetsScreen(
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onDebts: () -> Unit = {},
    onCredit: (String) -> Unit = {},
    onLoans: () -> Unit = {},
) {
    val container = LocalContainer.current
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val ui = androidx.compose.runtime.remember(data) { buildAssetsUi(data) }
    val monthDue = androidx.compose.runtime.remember(data) {
        val zone = java.time.ZoneId.systemDefault()
        com.xiguli.jinkui.data.ledger.DebtMath.overview(data, java.time.LocalDate.now(zone), zone).let { it.monthDue to (it.overdue > 0) }
    }
    LedgerAssetsContent(ui, onBack, onAccounts, onDebts, onCredit, onLoans, monthDue.first, monthDue.second)
}

/**
 * 资产总览（无状态）：带植物插画的净资产头图（可以一键隐藏金额）、总资产 / 负资产，
 * 三块小卡（储蓄与现金、支付账户、信用欠款），信用账户与欠款占比条，其余账户的余额。
 */
@Composable
fun LedgerAssetsContent(
    ui: AssetsUi,
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onDebts: () -> Unit = {},
    onCredit: (String) -> Unit = {},
    onLoans: () -> Unit = {},
    /** 欠款 / 账单入口上显示的本月待还（1.4）。 */
    monthDue: Long = 0L,
    overdue: Boolean = false,
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
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.SPRIG) {
                Row(Modifier.padding(Space.x2_5), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.1f)) {
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
                        Text(show(ui.net), style = JkType.amountHero.copy(fontSize = 30.sp), color = if (ui.net < 0) colors.danger else colors.accent, maxLines = 1)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                        HeroLine("总资产", show(ui.assets))
                        HeroLine("负资产", show(ui.liabilities))
                    }
                }
            }
            Spacer(Modifier.height(Space.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                Tile("储蓄现金", show(ui.savings), colors.textPrimary, Modifier.weight(1f))
                Tile("支付账户", show(ui.wallets), colors.textPrimary, Modifier.weight(1f))
                Tile("信用欠款", show(ui.creditDebt), if (ui.creditDebt > 0) colors.expense else colors.textPrimary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.x1_5))
            // 1.4：欠款 / 账单入口（信用账单、分期、借贷与到期提醒）
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
                    Text("欠款 / 账单", style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    Text("信用账单、分期、借贷与到期提醒", style = JkType.caption, color = colors.textTertiary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("本月待还", style = JkType.caption, color = colors.textTertiary)
                    Text(show(monthDue), style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = if (overdue) colors.expense else colors.textPrimary)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
            if (ui.loans.isNotEmpty()) {
                Spacer(Modifier.height(Space.x2))
                JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.x1)) {
                    Row(Modifier.padding(horizontal = Space.x2, vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
                        Text("借贷", style = JkType.section, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                        Text("应收 ${show(ui.receivable)} · 应付 ${show(ui.payable)}", style = JkType.small, color = colors.textSecondary)
                    }
                    ui.loans.forEachIndexed { i, row ->
                        val receivable = row.account.kind == AccountKind.RECEIVABLE
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable(onClick = onLoans)
                                .padding(horizontal = Space.x2, vertical = Space.x1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(JkSize.chip).clip(CircleShape).background(colors.surfaceAlt), contentAlignment = Alignment.Center) {
                                Icon(accountIcon(row.account.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(Space.x1_5))
                            Column(Modifier.weight(1f)) {
                                Text(row.account.name, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (receivable) "应收（借出）" else "应付（借入）", style = JkType.caption, color = colors.textTertiary)
                            }
                            Text(show(kotlin.math.abs(row.balance)), style = JkType.amount, color = if (receivable) colors.textPrimary else colors.expense)
                        }
                        if (i < ui.loans.lastIndex) JkDivider(inset = Space.x2 + JkSize.chip + Space.x1_5)
                    }
                }
            }
            if (ui.credit.isNotEmpty()) {
                Spacer(Modifier.height(Space.x2))
                JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(Space.x2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("信用账户", style = JkType.section, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                        Text(show(ui.creditDebt), style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    }
                    for (row in ui.credit) {
                        Spacer(Modifier.height(Space.x2))
                        Box(Modifier.clip(Radius.control).clickable(onClickLabel = "查看账单") { onCredit(row.account.id) }) {
                            CreditRow(row, ui.liabilities, show)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.x2))
            JkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.x1)) {
                Text(
                    "资金账户",
                    style = JkType.section,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = Space.x2, vertical = Space.x1).semantics { heading() },
                )
                if (ui.others.isEmpty()) {
                    Text("还没有账户，点右上角“管理账户”添加", style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(Space.x2))
                }
                ui.others.forEachIndexed { i, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .clickable(onClick = onAccounts)
                            .padding(horizontal = Space.x2, vertical = Space.x1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(JkSize.chip).clip(CircleShape).background(colors.surfaceAlt), contentAlignment = Alignment.Center) {
                            Icon(accountIcon(row.account.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(Space.x1_5))
                        Column(Modifier.weight(1f)) {
                            Text(row.account.name, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.account.kind.label + if (row.account.archived) " · 已停用" else "", style = JkType.caption, color = colors.textTertiary)
                        }
                        Text(show(row.balance), style = JkType.amount, color = if (row.balance < 0) colors.danger else colors.textPrimary)
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                    }
                    if (i < ui.others.lastIndex) JkDivider(inset = Space.x2 + JkSize.chip + Space.x1_5)
                }
            }
            Text(
                "净资产 = 资产 − 负债。余额由“期初余额 + 收支 ± 转账”算出；信用卡、花呗的欠款与借入（应付）记为负数，借出（应收）计入资产；占比条是它在全部负债里的份额。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = Space.x05, vertical = Space.x2),
            )
        }
    }
}

@Composable
private fun HeroLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = JkType.small.copy(fontWeight = FontWeight.Medium), color = Jk.colors.textSecondary)
        Spacer(Modifier.width(Space.x1))
        Text(value, style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = Jk.colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun Tile(title: String, value: String, tone: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(Radius.card)
            .background(Jk.colors.surface)
            .padding(vertical = Space.x2, horizontal = Space.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = Jk.colors.textPrimary, maxLines = 1)
        Spacer(Modifier.height(Space.x1))
        Text(value, style = JkType.amountSmall, color = tone, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CreditRow(row: AssetRow, liabilities: Long, show: (Long) -> String) {
    val colors = Jk.colors
    val debt = (-row.balance).coerceAtLeast(0L)
    val ratio = if (liabilities > 0) debt.toFloat() / liabilities else 0f
    val shown by animateFloatAsState(ratio, tween(520, easing = FastOutSlowInEasing), label = "credit")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "${row.account.name}，欠款 ${Money.format(debt)}，占全部负债 ${(ratio * 100).toInt()}%"
    }) {
        Box(Modifier.size(JkSize.chip).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            Icon(accountIcon(row.account.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.account.name, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(show(debt), style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(7.dp)) {
                drawRoundRect(colors.surfaceAlt, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(colors.primary, size = size.copy(width = size.width * shown), cornerRadius = CornerRadius(size.height / 2))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (row.balance > 0) "溢缴 ${show(row.balance)}" else "占全部负债 ${(ratio * 100).toInt()}%",
                style = JkType.amountCaption,
                color = colors.textSecondary,
            )
        }
    }
}
