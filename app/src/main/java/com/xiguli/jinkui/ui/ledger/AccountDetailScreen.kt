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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkEmpty
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.theme.jkSurface
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkFonts
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space

/** 账户详情页上的操作；界面测试直接传入。 */
class AccountDetailActions(
    val onBack: () -> Unit = {},
    /** 记一笔（账户预选好）。 */
    val onRecord: () -> Unit = {},
    /** 转账（从这个账户转出）。 */
    val onTransfer: () -> Unit = {},
    val onAdjust: () -> Unit = {},
    val onEdit: () -> Unit = {},
    /** 信用卡 / 花呗：还款。 */
    val onRepay: () -> Unit = {},
    /** 信用卡 / 花呗：账单与分期（原“账单”页）。 */
    val onStatements: () -> Unit = {},
    val onOpen: (LedgerTransaction) -> Unit = {},
    val onRowMenu: (LedgerTransaction) -> Unit = {},
    val onRepeat: (LedgerTransaction) -> Unit = {},
    val onDelete: (LedgerTransaction) -> Unit = {},
)

/**
 * 1.5.0-alpha02：账户详情（参考钱迹、一木记账点进一个账户后的页面，只借鉴交互）。
 * 资产页 / 账户与余额里点一个账户进来：余额大字（信用卡 / 花呗写“当前欠款”与“可用额度”），
 * 本月流入 / 流出，四个按钮“记一笔 / 转账 / 修改余额 / 编辑”，信用卡另有账单日、还款日与“还款”；
 * 下面是这个账户自己的流水，按天分组（余额调整也在里面）。点一笔修改，长按菜单，右滑再记，左滑删除。
 */
@Composable
fun AccountDetailScreen(
    accountId: String,
    onBack: () -> Unit,
    onStatements: (String) -> Unit = {},
) {
    val container = LocalContainer.current
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val zone = remember { java.time.ZoneId.systemDefault() }
    val ui = remember(data, accountId) { buildAccountDetailUi(data, accountId, zone) }
    if (ui == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            JkTopBar("账户", onBack = onBack)
            Text("找不到这个账户，可能已经删除。", style = JkType.body, color = Jk.colors.textTertiary, modifier = Modifier.padding(Space.x3))
        }
        return
    }
    val ledgerVm = jkViewModel { LedgerViewModel(container) }
    val accountsVm = jkViewModel { LedgerAccountsViewModel(container) }
    val accountsUi by accountsVm.ui.collectAsStateWithLifecycle()
    val debtVm = jkViewModel { DebtViewModel(container) }
    val cards by container.repository.cards.collectAsStateWithLifecycle()
    val host = rememberEntryHost()
    var adjusting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var repaying by remember { mutableStateOf(false) }
    val account = ui.account

    AccountDetailContent(
        ui = ui,
        actions = AccountDetailActions(
            onBack = onBack,
            onRecord = { host.create(account = account.id) },
            onTransfer = { host.create(account = account.id, type = TxType.TRANSFER) },
            onAdjust = { adjusting = true },
            onEdit = { editing = true },
            onRepay = { repaying = true },
            onStatements = { onStatements(account.id) },
            onOpen = { host.editing = it },
            onRowMenu = { host.menuFor = it },
            onRepeat = { host.create(from = it) },
            onDelete = { host.confirmDelete = it },
        ),
    )

    EntryHost(host, data, ledgerVm)

    if (adjusting) {
        BalanceAdjustSheet(
            account = account,
            current = ui.balance,
            recent = accountsUi.recentAdjusts[account.id].orEmpty(),
            onDismiss = { adjusting = false },
            onSave = { target, mode -> accountsVm.adjust(account, target, mode) { adjusting = false } },
            onUndo = { accountsVm.undo(it) },
        )
    }
    if (editing) {
        AccountEditSheet(
            account = account,
            isNew = false,
            usage = accountsUi.usage[account.id] ?: 0,
            bankCards = cards.filter { it.type == CardType.BANK },
            onDismiss = { editing = false },
            onSave = { accountsVm.save(it) { editing = false } },
            onDelete = { accountsVm.delete(account) { editing = false; onBack() } },
            onDebt = {
                editing = false
                onStatements(account.id)
            },
            balance = ui.balance,
            onAdjust = {
                editing = false
                adjusting = true
            },
        )
    }
    if (repaying) {
        val today = remember { java.time.LocalDate.now(zone) }
        RepaySheet(
            account, ui.credit?.statementRemaining ?: 0L, (ui.credit?.debt ?: 0L).coerceAtLeast(0L), fundAccounts(data), today,
            { repaying = false },
        ) { from, amount, date ->
            debtVm.run("已记一笔还款 ${Money.format(amount)}", { repaying = false }) { container.ledger.repayCredit(account.id, from, amount, date) }
        }
    }
}

/** 账户详情（无状态）。 */
@Composable
fun AccountDetailContent(ui: AccountDetailUi, actions: AccountDetailActions) {
    val colors = Jk.colors
    val account = ui.account
    val credit = ui.credit
    var hidden by rememberSaveable { mutableStateOf(false) }
    val show: (Long) -> String = { if (hidden) "****" else Money.format(it, symbol = false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(account.name, onBack = actions.onBack) {
            Text(
                "编辑",
                style = JkType.small.copy(fontWeight = FontWeight.Bold),
                color = colors.accent,
                modifier = Modifier
                    .heightIn(min = JkSize.touch)
                    .clip(Radius.pill)
                    .clickable(role = Role.Button, onClickLabel = "编辑账户", onClick = actions.onEdit)
                    .padding(horizontal = Space.x1_5, vertical = Space.x1_5),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Space.x6),
        ) {
            item(key = "hero", contentType = "hero") {
                BotanicalCard(Modifier.fillMaxWidth().padding(horizontal = Space.x2), layout = BotanicalLayout.TOP_RIGHT) {
                    Column(Modifier.padding(Space.x2_5)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(colors.surfaceAlt), contentAlignment = Alignment.Center) {
                                Icon(accountIcon(account.kind), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(Space.x1))
                            Text(
                                (if (credit != null) "当前欠款(元)" else "余额(元)") + " · " + account.kind.label + if (account.archived) " · 已停用" else "",
                                style = JkType.small.copy(fontWeight = FontWeight.Medium),
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
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
                        val big = credit?.debt ?: ui.balance
                        Text(
                            show(big),
                            style = JkType.amountHero.copy(fontSize = 32.sp),
                            color = when {
                                credit != null && big > 0 -> colors.expense
                                credit == null && big < 0 -> colors.danger
                                else -> colors.accent
                            },
                            maxLines = 1,
                        )
                        if (credit != null && credit.debt < 0) {
                            Text("多还了 ${show(-credit.debt)}，下次消费先用它", style = JkType.caption, color = colors.textTertiary)
                        }
                        Spacer(Modifier.height(Space.x1_5))
                        if (credit != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                                DetailCell("可用额度", credit.available?.let(show) ?: "未填额度", colors.textPrimary, Modifier.weight(1f))
                                DetailCell("总额度", credit.limit?.let(show) ?: "—", colors.textPrimary, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(Space.x1))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                            DetailCell("本月流入", show(ui.monthIn), colors.income, Modifier.weight(1f))
                            DetailCell("本月流出", show(ui.monthOut), colors.textPrimary, Modifier.weight(1f))
                        }
                    }
                }
            }
            item(key = "actions", contentType = "actions") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.x2, vertical = Space.x1_5),
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                ) {
                    ActionTile("记一笔", Icons.Outlined.Add, actions.onRecord, Modifier.weight(1f), primary = true)
                    ActionTile("转账", TransferIcon, actions.onTransfer, Modifier.weight(1f))
                    if (ui.canAdjust) ActionTile("修改余额", Icons.Outlined.Tune, actions.onAdjust, Modifier.weight(1f))
                    ActionTile("编辑", Icons.Outlined.Edit, actions.onEdit, Modifier.weight(1f))
                }
            }
            if (credit != null) {
                item(key = "credit", contentType = "credit") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.x2)
                            .jkSurface(Jk.surfaces.card, Radius.card)
                            .padding(Space.x2),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("账单日", style = JkType.caption, color = colors.textTertiary)
                                Text(credit.statementDay ?: "还没设置", style = JkType.bodyStrong, color = colors.textPrimary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("还款日", style = JkType.caption, color = colors.textTertiary)
                                Text(credit.dueDay ?: "还没设置", style = JkType.bodyStrong, color = colors.textPrimary)
                            }
                        }
                        // 1.5.0-alpha03：一句白话状态，不再是“本期还要还 —”
                        Spacer(Modifier.height(Space.x1))
                        Text(
                            credit.status,
                            style = JkType.bodyStrong,
                            color = if ((credit.statementRemaining ?: 0L) > 0) colors.expense else colors.textPrimary,
                        )
                        if (credit.defaulted) {
                            Text("按默认日期估算，不对可以在“账单与分期”里改", style = JkType.caption, color = colors.textTertiary)
                        }
                        Spacer(Modifier.height(Space.x1_5))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                            JkButton("还款", actions.onRepay, modifier = Modifier.weight(1f))
                            JkButton("账单与分期", actions.onStatements, modifier = Modifier.weight(1f), style = com.xiguli.jinkui.ui.components.ButtonStyle.Secondary)
                        }
                    }
                }
            }
            item(key = "flow-title", contentType = "title") {
                Row(Modifier.fillMaxWidth().padding(start = Space.x2_5, end = Space.x2_5, top = Space.x2_5), verticalAlignment = Alignment.CenterVertically) {
                    Text("这个账户的流水", style = JkType.section, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                    Text("共 ${ui.count} 笔", style = JkType.small, color = colors.textTertiary)
                }
            }
            if (ui.days.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    JkEmpty(
                        title = "这个账户还没有流水",
                        message = "点上面的“记一笔”，记下的钱会算进这个账户。",
                        illustration = { LedgerIllustration(LedgerArt.EMPTY_MONTH) },
                    )
                }
            }
            for (day in ui.days) {
                item(key = "d-${day.date}", contentType = "day") { AccountDayHeader(day, show) }
                itemsIndexed(day.rows, key = { _, row -> row.id }, contentType = { _, _ -> "tx" }) { index, row ->
                    TransactionRow(
                        row = if (hidden) row.copy(amountText = "****") else row,
                        shape = rowShape(index, day.rows.size),
                        showDivider = index < day.rows.lastIndex,
                        onClick = { actions.onOpen(row.tx) },
                        onLongClick = { actions.onRowMenu(row.tx) },
                        onRepeat = { actions.onRepeat(row.tx) },
                        onDelete = { actions.onDelete(row.tx) },
                    )
                }
            }
            if (ui.days.isNotEmpty()) {
                item(key = "hint", contentType = "hint") {
                    Text(
                        "余额 = 期初余额 + 流入 − 流出；和实际不一样时点“修改余额”。点一笔修改，长按更多操作。",
                        style = JkType.caption,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.x3, vertical = Space.x2),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountDayHeader(day: AccountDayUi, show: (Long) -> String) {
    val colors = Jk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x2_5, end = Space.x2_5, top = Space.x2, bottom = Space.x1)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(day.label, style = JkType.heading.copy(fontWeight = FontWeight.Bold, fontFamily = JkFonts.numeral), color = colors.textPrimary, maxLines = 1)
        Spacer(Modifier.width(Space.x1))
        Text(day.weekday, style = JkType.small, color = colors.textTertiary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (day.outflow != 0L || day.inflow == 0L) {
            Text("流出", style = JkType.small, color = colors.textTertiary)
            Spacer(Modifier.width(4.dp))
            Text(show(day.outflow), style = JkType.amountSmall, color = colors.expense, maxLines = 1)
        }
        if (day.inflow != 0L) {
            Spacer(Modifier.width(Space.x1_5))
            Text("流入", style = JkType.small, color = colors.textTertiary)
            Spacer(Modifier.width(4.dp))
            Text(show(day.inflow), style = JkType.amountSmall, color = colors.income, maxLines = 1)
        }
    }
}

@Composable
private fun DetailCell(label: String, value: String, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = JkType.caption.copy(fontWeight = FontWeight.Medium), color = Jk.colors.textSecondary, maxLines = 1)
        Text(value, style = JkType.amount.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val colors = Jk.colors
    Column(
        modifier
            .heightIn(min = 68.dp)
            .clip(Radius.control)
            .then(if (primary) Modifier.background(colors.primary) else Modifier.jkSurface(Jk.surfaces.card, Radius.control))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.x1_5, horizontal = Space.x05),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) colors.onPrimary else colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = JkType.small.copy(fontWeight = FontWeight.Bold), color = if (primary) colors.onPrimary else colors.textPrimary, maxLines = 1)
    }
}
