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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.core.auto.PaySource
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.PendingEntry
import com.xiguli.jinkui.data.ledger.PendingMath
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkEmpty
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** 待确认列表的一行（建议的分类与账户已经算好）。 */
data class PendingRowUi(
    val entry: PendingEntry,
    val suggestion: LedgerTransaction?,
    val categoryLabel: String,
    val accountName: String?,
    val time: String,
)

private val PENDING_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")

fun buildPendingUi(pending: List<PendingEntry>, data: LedgerData, zone: ZoneId = ZoneId.systemDefault()): List<PendingRowUi> {
    val categories = data.categories.associateBy { it.id }
    val accounts = data.accounts.associateBy { it.id }
    return pending.sortedByDescending { it.occurredAt }.map { e ->
        val s = PendingMath.suggestion(e, data, zone)
        PendingRowUi(
            entry = e,
            suggestion = s,
            categoryLabel = categoryLabel(s?.categoryId, categories),
            accountName = s?.accountId?.let { accounts[it]?.name },
            time = Instant.ofEpochMilli(e.occurredAt).atZone(zone).format(PENDING_TIME),
        )
    }
}

class PendingInboxViewModel(private val container: AppContainer) : ViewModel() {
    fun confirm(rows: List<PendingRowUi>) {
        val ready = rows.filter { it.suggestion != null }
        if (ready.isEmpty()) {
            container.messages.show("请先在“账户与余额”里添加一个账户")
            return
        }
        viewModelScope.launch {
            runCatching { container.ledger.confirmPending(ready.map { it.entry.id }, ready.mapNotNull { it.suggestion }) }
                .onSuccess { container.messages.show(if (ready.size == 1) "已记下这一笔" else "已记下 ${ready.size} 笔") }
                .onFailure { container.messages.show((it as? LedgerInputException)?.message ?: "记账失败") }
        }
    }

    fun confirmEdited(entry: PendingEntry, tx: LedgerTransaction, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { container.ledger.confirmPending(listOf(entry.id), listOf(tx)) }
                .onSuccess {
                    container.messages.show("已记下这一笔")
                    onDone()
                }
                .onFailure { container.messages.show((it as? LedgerInputException)?.message ?: "记账失败") }
        }
    }

    fun ignore(ids: List<String>) {
        viewModelScope.launch {
            runCatching { container.ledger.ignorePending(ids) }
                .onSuccess { container.messages.show(if (ids.size == 1) "已忽略" else "已忽略 ${ids.size} 笔") }
                .onFailure { container.messages.show("操作失败") }
        }
    }
}

class PendingInboxActions(
    val onBack: () -> Unit = {},
    val onConfirm: (PendingRowUi) -> Unit = {},
    val onEdit: (PendingRowUi) -> Unit = {},
    val onIgnore: (PendingRowUi) -> Unit = {},
    val onConfirmAll: () -> Unit = {},
    val onIgnoreAll: () -> Unit = {},
    val onSettings: () -> Unit = {},
)

@Composable
fun PendingInboxScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { PendingInboxViewModel(container) }
    val pending by container.ledger.pending.collectAsStateWithLifecycle()
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val rows = remember(pending, data) { buildPendingUi(pending, data) }
    var editing by remember { mutableStateOf<PendingRowUi?>(null) }
    var confirmIgnoreAll by remember { mutableStateOf(false) }

    PendingInboxContent(
        rows = rows,
        enabled = settings.autoBookkeeping,
        actions = PendingInboxActions(
            onBack = onBack,
            onConfirm = { viewModel.confirm(listOf(it)) },
            onEdit = { editing = it },
            onIgnore = { viewModel.ignore(listOf(it.entry.id)) },
            onConfirmAll = { viewModel.confirm(rows) },
            onIgnoreAll = { confirmIgnoreAll = true },
            onSettings = onSettings,
        ),
    )

    editing?.let { row ->
        val seed = row.suggestion ?: PendingMath.suggestion(row.entry, data)
        EntrySheet(
            data = data,
            existing = null,
            template = seed,
            initialTime = row.entry.occurredAt,
            onDismiss = { editing = null },
            onSave = { tx, _, _ -> viewModel.confirmEdited(row.entry, tx) { editing = null } },
            onDelete = {},
        )
    }
    if (confirmIgnoreAll) {
        JkDialog(
            title = "全部忽略？",
            onDismiss = { confirmIgnoreAll = false },
            confirmText = "全部忽略",
            danger = true,
            onConfirm = {
                confirmIgnoreAll = false
                viewModel.ignore(rows.map { it.entry.id })
            },
        ) {
            Text("这 ${rows.size} 笔待确认会被删除，不会记进账本。", style = JkType.body)
        }
    }
}

/** “待确认”（无状态）：自动记账从通知里识别出来、等你确认的收支。 */
@Composable
fun PendingInboxContent(rows: List<PendingRowUi>, enabled: Boolean, actions: PendingInboxActions) {
    val colors = Jk.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(title = "待确认", onBack = actions.onBack) {
            JkIconButton(Icons.Outlined.Settings, "自动记账设置", actions.onSettings)
        }
        if (rows.isEmpty()) {
            JkEmpty(
                title = "没有待确认的记录",
                message = if (enabled) {
                    "微信、支付宝或银行 App 有收付款通知时，识别出的金额会先放在这里，等你确认后才记账。"
                } else {
                    "自动记账还没有打开。打开后，收付款通知里的金额会先放在这里，等你确认。"
                },
                illustration = { LedgerIllustration(LedgerArt.EMPTY_MONTH) },
                action = if (!enabled) ({ JkButton("去打开自动记账", actions.onSettings, style = ButtonStyle.Secondary) }) else null,
            )
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Space.x2, end = Space.x2, bottom = Space.x2),
        ) {
            item(key = "hint", contentType = "hint") {
                Text(
                    "共 ${rows.size} 笔 · 点“记账”按建议的分类和账户记下，点一行可以先修改",
                    style = JkType.small,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = Space.x05, top = Space.x05, bottom = Space.x1),
                )
            }
            items(rows, key = { it.entry.id }, contentType = { "pendinginboxscreen-row" }) { row ->
                val shape = when {
                    rows.size == 1 -> Radius.card
                    row === rows.first() -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    row === rows.last() -> androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                    else -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                }
                Column(Modifier.clip(shape).background(colors.surface)) {
                    PendingRow(row, actions)
                    if (row !== rows.last()) JkDivider(inset = 68.dp)
                }
            }
        }
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.x2, vertical = Space.x1_5),
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            JkButton("全部忽略", actions.onIgnoreAll, style = ButtonStyle.Ghost, modifier = Modifier.weight(1f))
            JkButton("全部记账", actions.onConfirmAll, modifier = Modifier.weight(2f))
        }
    }
}

private fun sourceIcon(source: PaySource) = when (source) {
    PaySource.WECHAT -> Icons.Outlined.Chat
    PaySource.ALIPAY -> Icons.Outlined.QrCode
    PaySource.BANK, PaySource.UNIONPAY -> Icons.Outlined.AccountBalance
}

@Composable
private fun PendingRow(row: PendingRowUi, actions: PendingInboxActions) {
    val colors = Jk.colors
    val e = row.entry
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "修改后记账", onClick = { actions.onEdit(row) })
            .padding(start = Space.x2, end = Space.x2, top = Space.x1_5, bottom = Space.x1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryChip(sourceIcon(e.source), e.type)
            Spacer(Modifier.width(Space.x1_5))
            Column(Modifier.weight(1f)) {
                Text(
                    e.merchant.ifEmpty { e.source.label + if (e.type == TxType.INCOME) "收款" else "付款" },
                    style = JkType.bodyStrong,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${e.source.label} · ${row.time} · ${row.categoryLabel}" + (row.accountName?.let { " · $it" } ?: ""),
                    style = JkType.small,
                    color = colors.textTertiary,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(Space.x1))
            Text(
                (if (e.type == TxType.INCOME) "+" else "-") + Money.format(e.amount, symbol = false),
                style = JkType.amount,
                color = if (e.type == TxType.INCOME) colors.income else colors.textPrimary,
            )
        }
        Spacer(Modifier.height(Space.x05))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            JkButton("忽略", { actions.onIgnore(row) }, style = ButtonStyle.Ghost, compact = true, modifier = Modifier.heightIn(min = 48.dp))
            JkButton("修改", { actions.onEdit(row) }, style = ButtonStyle.Ghost, compact = true, modifier = Modifier.heightIn(min = 48.dp))
            Spacer(Modifier.weight(1f))
            JkButton("记账", { actions.onConfirm(row) }, style = ButtonStyle.Secondary, compact = true, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}
