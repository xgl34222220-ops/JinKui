package com.xiguli.jinkui.ui.ledger

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.RefundMath
import com.xiguli.jinkui.data.ledger.RefundStatus
import com.xiguli.jinkui.data.ledger.ReimburseOverview
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkCheck
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

// ── 退款与报销（1.4.0-alpha04）────────────────────────────────────
// 参考一木记账 / 钱迹的做法（只借鉴交互，不涉及任何代码）：退款记成关联原支出的一笔收入，统计里冲减原支出；
// 报销的支出不算自己的支出，报销到账可以记一笔关联的收入（不算收入），也可以只标记。

/** 报销页的一行。 */
@Immutable
data class ReimburseRowUi(val tx: LedgerTransaction, val title: String, val subtitle: String)

@Immutable
data class ReimburseUi(
    val overview: ReimburseOverview,
    val pending: List<ReimburseRowUi>,
    val done: List<ReimburseRowUi>,
)

fun buildReimburseUi(data: LedgerData, zone: ZoneId = ZoneId.systemDefault()): ReimburseUi {
    val overview = RefundMath.reimbursements(data.transactions)
    val categories = data.categories.associateBy { it.id }
    val accounts = data.accounts.associateBy { it.id }
    fun row(tx: LedgerTransaction): ReimburseRowUi {
        val date = Instant.ofEpochMilli(tx.occurredAt).atZone(zone).toLocalDate()
        val where = accounts[tx.accountId]?.name ?: "未知账户"
        return ReimburseRowUi(
            tx,
            categoryLabel(tx.categoryId, categories),
            listOf("${date.monthValue}月${date.dayOfMonth}日", where, tx.note).filter { it.isNotBlank() }.joinToString("　"),
        )
    }
    return ReimburseUi(overview, overview.pending.map(::row), overview.done.map(::row))
}

class ReimburseActions(
    val onBack: () -> Unit = {},
    /** 选中的几笔标记已报销（由页面弹出选择到账账户的面板）。 */
    val onMark: (List<LedgerTransaction>) -> Unit = {},
    /** 撤回一笔已报销（回到待报销）。 */
    val onRevert: (LedgerTransaction) -> Unit = {},
    /** 不再报销（去掉待报销标记，重新算作支出）。 */
    val onUnmark: (LedgerTransaction) -> Unit = {},
)

@Composable
fun ReimburseScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val ui = remember(data) { buildReimburseUi(data) }
    val scope = rememberCoroutineScope()
    var marking by remember { mutableStateOf<List<LedgerTransaction>?>(null) }
    fun run(done: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                container.messages.show(done)
            } catch (e: LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }
    ReimburseContent(
        ui,
        ReimburseActions(
            onBack = onBack,
            onMark = { marking = it },
            onRevert = { tx -> run("已撤回，回到待报销") { container.ledger.revertReimbursed(listOf(tx.id)) } },
            onUnmark = { tx -> run("已取消报销，重新算作支出") { container.ledger.setReimburse(listOf(tx.id), com.xiguli.jinkui.data.ledger.Reimburse.NONE) } },
        ),
    )
    marking?.let { txs ->
        MarkReimbursedSheet(
            expenses = txs,
            accounts = fundAccounts(data),
            today = LocalDate.now(),
            onDismiss = { marking = null },
        ) { accountId, date ->
            marking = null
            run(if (accountId != null) "已标记报销，记了一笔到账 ${Money.format(txs.sumOf { it.amount })}" else "已标记报销") {
                container.ledger.markReimbursed(txs.map { it.id }, accountId, date)
            }
        }
    }
}

/** 报销（无状态）：待报销合计 → 待报销列表（多选后“标记已报销”）→ 已报销（可撤回）。 */
@Composable
fun ReimburseContent(ui: ReimburseUi, actions: ReimburseActions, initialSelection: Set<String> = emptySet()) {
    val colors = Jk.colors
    var selected by remember(ui.pending.map { it.tx.id }) { mutableStateOf(initialSelection.filter { id -> ui.pending.any { it.tx.id == id } }.toSet()) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar("报销", onBack = actions.onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2)
                .padding(bottom = Space.x4),
        ) {
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.SPRIG) {
                Column(Modifier.padding(Space.x2_5)) {
                    Text("待报销(元)", style = JkType.small.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
                    Text(Money.format(ui.overview.pendingTotal, symbol = false), style = JkType.amountHero.copy(fontSize = 32.sp), color = colors.textPrimary, maxLines = 1)
                    Text(
                        "${ui.pending.size} 笔待报销 · 已报销 ${ui.done.size} 笔，共 ${Money.format(ui.overview.doneTotal)}",
                        style = JkType.caption,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Section("待报销")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                if (ui.pending.isEmpty()) {
                    Text(
                        "没有待报销的支出。在账本里长按一笔支出，选“标记待报销”，替单位或别人垫付的钱就不会算进你的支出。",
                        style = JkType.small,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(Space.x2),
                    )
                }
                ui.pending.forEachIndexed { i, row ->
                    if (i > 0) JkDivider(inset = Space.x2 + 22.dp + Space.x1_5)
                    val on = row.tx.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .toggleable(on, role = Role.Checkbox) { selected = if (it) selected + row.tx.id else selected - row.tx.id }
                            .padding(horizontal = Space.x2, vertical = Space.x1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        JkCheck(on)
                        Spacer(Modifier.width(Space.x1_5))
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = JkType.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.subtitle, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(Money.format(row.tx.amount), style = JkType.amountSmall, color = colors.textPrimary)
                    }
                }
            }
            if (ui.pending.isNotEmpty()) {
                Spacer(Modifier.height(Space.x1_5))
                val chosen = ui.pending.filter { it.tx.id in selected }.map { it.tx }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                    JkButton(
                        if (chosen.isEmpty()) "全选" else "取消选择",
                        { selected = if (chosen.isEmpty()) ui.pending.map { it.tx.id }.toSet() else emptySet() },
                        style = ButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    JkButton(
                        if (chosen.isEmpty()) "标记已报销" else "标记已报销（${chosen.size} 笔 ${Money.format(chosen.sumOf { it.amount })}）",
                        { actions.onMark(chosen) },
                        enabled = chosen.isNotEmpty(),
                        modifier = Modifier.weight(2f),
                    )
                }
                if (chosen.size == 1) {
                    Spacer(Modifier.height(Space.x05))
                    JkButton("不再报销（重新算作支出）", { actions.onUnmark(chosen.single()) }, style = ButtonStyle.Ghost, compact = true)
                }
            }
            Section("已报销")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                if (ui.done.isEmpty()) {
                    Text("还没有已报销的记录。", style = JkType.small, color = colors.textTertiary, modifier = Modifier.padding(Space.x2))
                }
                ui.done.forEachIndexed { i, row ->
                    if (i > 0) JkDivider(inset = Space.x2)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .padding(start = Space.x2, end = Space.x05, top = Space.x1, bottom = Space.x1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = JkType.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.subtitle, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(Money.format(row.tx.amount), style = JkType.amountSmall, color = colors.textSecondary)
                        JkButton("撤回", { actions.onRevert(row.tx) }, style = ButtonStyle.Ghost, compact = true)
                    }
                }
            }
            Text(
                "报销的支出不算你的支出、也不占预算；报销到账记的那笔收入同样不算收入，账户余额照实计算。撤回会把同一笔到账关联的几笔一起退回待报销，并删掉那笔到账。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = Space.x05, vertical = Space.x2),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = JkType.overline,
        color = Jk.colors.textTertiary,
        modifier = Modifier.padding(start = Space.x05, top = Space.x3, bottom = Space.x1).semantics { heading() },
    )
}

/** 标记已报销：钱到了哪个账户（记一笔关联的到账收入），或者只标记（冲抵）。 */
@Composable
fun MarkReimbursedSheet(
    expenses: List<LedgerTransaction>,
    accounts: List<LedgerAccount>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (accountId: String?, date: LocalDate) -> Unit,
) {
    var record by remember { mutableStateOf(accounts.isNotEmpty()) }
    var account by remember { mutableStateOf(accounts.firstOrNull { a -> expenses.any { it.accountId == a.id } }?.id ?: accounts.firstOrNull()?.id) }
    var date by remember { mutableStateOf(today) }
    JkSheet(onDismiss = onDismiss, title = "标记已报销") {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
            Text("${expenses.size} 笔，合计 ${Money.format(expenses.sumOf { it.amount })}", style = JkType.bodyStrong, color = Jk.colors.textPrimary)
            JkSegmented(listOf(true to "记一笔到账", false to "只标记（冲抵）"), record, { record = it })
            if (record) {
                Text("报销的钱到了哪个账户", style = JkType.small, color = Jk.colors.textSecondary)
                AccountChips(accounts, account) { account = it }
                DateField("到账日期", date, today, { date = it })
            } else {
                Text("不单独记到账（例如报销款并进了工资，或已经在别处记过）。这几笔仍然不算支出。", style = JkType.small, color = Jk.colors.textTertiary)
            }
        }
        Spacer(Modifier.height(Space.x2))
        JkButton("确定", { onSubmit(if (record) account else null, date) }, enabled = !record || account != null, modifier = Modifier.fillMaxWidth())
    }
}

/** 退款：全额或部分，退回哪个账户、哪天。 */
@Composable
fun RefundSheet(
    status: RefundStatus,
    category: String,
    accounts: List<LedgerAccount>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (amount: Long, accountId: String, date: LocalDate, note: String) -> Unit,
) {
    val colors = Jk.colors
    var amount by remember { mutableStateOf(Money.plain(status.remaining)) }
    var account by remember { mutableStateOf(accounts.firstOrNull { it.id == status.expense.accountId }?.id ?: accounts.firstOrNull()?.id) }
    var date by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    JkSheet(onDismiss = onDismiss, title = "退款") {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
            Text(
                "$category · 原支出 ${Money.format(status.expense.amount)}" + if (status.refunded > 0) "，已退 ${Money.format(status.refunded)}" else "",
                style = JkType.bodyStrong,
                color = colors.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkButton("全额 ${Money.format(status.remaining)}", { amount = Money.plain(status.remaining); error = null }, style = ButtonStyle.Secondary, compact = true)
                JkButton("一半", { amount = Money.plain(status.remaining / 2); error = null }, style = ButtonStyle.Secondary, compact = true)
            }
            JkTextField(value = amount, onValueChange = { amount = it.take(14); error = null }, label = "退款金额", placeholder = "0.00", keyboardType = KeyboardType.Decimal, error = error)
            Text("退回哪个账户", style = JkType.small, color = colors.textSecondary)
            AccountChips(accounts, account) { account = it }
            DateField("退款日期", date, today, { date = it })
            JkTextField(value = note, onValueChange = { note = it.take(200) }, label = "备注（可选）", placeholder = "默认：退款 · 原备注")
            Text("退款记成这个账户上的一笔收入（分类“退款”），统计里冲减原支出，不算收入。", style = JkType.caption, color = colors.textTertiary)
        }
        Spacer(Modifier.height(Space.x2))
        JkButton("确定退款", {
            val cents = if (amount.isBlank()) null else Money.parse(amount)
            val acc = account
            error = when {
                cents == null || cents <= 0 -> "请输入大于 0 的金额"
                cents > status.remaining -> "最多还能退 ${Money.format(status.remaining)}"
                acc == null -> "请选择账户"
                else -> null
            }
            if (error == null) onSubmit(cents!!, acc!!, date, note.trim())
        }, modifier = Modifier.fillMaxWidth())
    }
}

/** 退款可以退回的账户：在用、计入资产的账户（信用卡退款退回信用卡）。 */
fun refundAccounts(data: LedgerData): List<LedgerAccount> =
    data.accounts.filter { !it.archived && it.kind.countsInAssets }.sortedBy { it.position }
