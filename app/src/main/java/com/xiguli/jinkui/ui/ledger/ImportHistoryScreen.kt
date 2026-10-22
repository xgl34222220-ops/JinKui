package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import com.xiguli.jinkui.ui.components.SecureDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerImportBatch
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** “导入记录”里的一次导入。 */
@Immutable
data class ImportBatchUi(
    val batch: LedgerImportBatch,
    val title: String,
    val time: String,
    /** 现在账本里还剩这一批的几笔（单独删过的不算）。 */
    val remaining: Int,
    val expense: Long,
    val income: Long,
    val transfers: Int,
)

private val BATCH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun buildImportHistory(data: LedgerData, zone: ZoneId = ZoneId.systemDefault()): List<ImportBatchUi> {
    val byBatch = data.transactions.filter { it.importBatch != null }.groupBy { it.importBatch }
    return data.importBatches.sortedByDescending { it.createdAt }.map { b ->
        val txs = byBatch[b.id].orEmpty()
        ImportBatchUi(
            batch = b,
            title = when {
                b.legacy -> "1.3 之前的导入"
                b.fileName.isNotBlank() -> b.fileName
                else -> b.source.ifBlank { "账单导入" }
            },
            time = Instant.ofEpochMilli(b.createdAt).atZone(zone).format(BATCH_TIME),
            remaining = txs.size,
            expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
            income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amount },
            transfers = txs.count { it.type == TxType.TRANSFER },
        )
    }
}

class ImportHistoryActions(
    val onBack: () -> Unit = {},
    val onUndo: (ImportBatchUi) -> Unit = {},
    /** 兜底：删除某段时间内的全部流水。 */
    val onDeleteRange: () -> Unit = {},
    val onImport: () -> Unit = {},
)

@Composable
fun ImportHistoryScreen(onBack: () -> Unit, onImport: () -> Unit) {
    val container = LocalContainer.current
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val items = remember(data) { buildImportHistory(data) }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<ImportBatchUi?>(null) }
    var range by remember { mutableStateOf(false) }
    ImportHistoryContent(
        items = items,
        actions = ImportHistoryActions(onBack = onBack, onUndo = { confirm = it }, onDeleteRange = { range = true }, onImport = onImport),
    )
    confirm?.let { item ->
        JkDialog(
            title = "撤销这次导入？",
            onDismiss = { confirm = null },
            confirmText = "撤销导入",
            danger = true,
            onConfirm = {
                confirm = null
                scope.launch {
                    runCatching { container.ledger.undoImport(item.batch.id) }
                        .onSuccess { container.messages.show("已撤销导入，删除了 $it 笔") }
                        .onFailure { container.messages.show("撤销失败：${it.message ?: "未知错误"}") }
                }
            },
        ) {
            Text(
                "将删除这次导入的 ${item.remaining} 笔流水（支出 ${Money.format(item.expense)}、收入 ${Money.format(item.income)}），" +
                    "账户余额随之恢复；这次自动新建、已经没有流水的账户一并删除。手动记的账不受影响。",
                style = JkType.body,
            )
        }
    }
    if (range) {
        DeleteRangeFlow(
            onDismiss = { range = false },
            countIn = { from, until -> data.transactions.count { it.occurredAt in from until until } },
            onDelete = { from, until ->
                range = false
                scope.launch {
                    runCatching { container.ledger.deleteRange(from, until) }
                        .onSuccess { gone ->
                            container.messages.show("已删除 ${gone.size} 笔", "撤销") {
                                container.scope.launch { runCatching { container.ledger.restoreTransactions(gone) } }
                            }
                        }
                        .onFailure { container.messages.show("删除失败：${it.message ?: "未知错误"}") }
                }
            },
        )
    }
}

/** 导入记录（无状态）：每次导入一张玻璃卡片，可以整批撤销；底部是“删除某段时间内的全部流水”。 */
@Composable
fun ImportHistoryContent(items: List<ImportBatchUi>, actions: ImportHistoryActions) {
    val colors = Jk.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar("导入记录", onBack = actions.onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2_5)
                .padding(top = Space.x05, bottom = Space.x5),
        ) {
            if (items.isEmpty()) {
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2_5)) {
                    Text("还没有导入过账单", style = JkType.section, color = colors.textPrimary)
                    Spacer(Modifier.height(Space.x05))
                    Text("从微信、支付宝或记账 App 导入的账单会列在这里，可以整批撤销。", style = JkType.small, color = colors.textSecondary)
                    Spacer(Modifier.height(Space.x1_5))
                    JkButton("导入账单", actions.onImport, style = ButtonStyle.Secondary, compact = true)
                }
            }
            items.forEachIndexed { i, item ->
                if (i > 0) Spacer(Modifier.height(Space.x1_5))
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2)) {
                    Text(item.title, style = JkType.bodyStrong, color = colors.textPrimary, maxLines = 1)
                    Text(
                        item.time + (if (item.batch.source.isNotBlank()) " · ${item.batch.source}" else "") +
                            " · 现有 ${item.remaining} 笔" + (if (!item.batch.legacy && item.batch.count != item.remaining) "（导入时 ${item.batch.count} 笔）" else ""),
                        style = JkType.caption.copy(fontFamily = JkType.date.fontFamily),
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(Space.x1))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        Figure("支出", Money.format(item.expense))
                        Figure("收入", Money.format(item.income))
                        if (item.transfers > 0) Figure("转账 / 校准", "${item.transfers} 笔")
                    }
                    Spacer(Modifier.height(Space.x1_5))
                    JkButton(
                        "撤销导入",
                        { actions.onUndo(item) },
                        style = ButtonStyle.Danger,
                        compact = true,
                        enabled = item.remaining > 0,
                    )
                }
            }
            Text(
                "其他清理",
                style = JkType.overline,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x3, bottom = Space.x1).semantics { heading() },
            )
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2)) {
                Text("删除某段时间内的全部流水", style = JkType.bodyStrong, color = colors.textPrimary)
                Spacer(Modifier.height(Space.x05))
                Text(
                    "导入记录里找不到、又想一次清掉的流水（例如很早以前导入的），选一个日期范围整段删除。删除前会确认，删除后几秒内可以撤销。",
                    style = JkType.small,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(Space.x1_5))
                JkButton("选择日期范围", actions.onDeleteRange, style = ButtonStyle.Secondary, compact = true)
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = JkType.caption, color = Jk.colors.textTertiary)
        Text(value, style = JkType.amountSmall, color = Jk.colors.textPrimary)
    }
}

/** 选起止日期（两次日期选择）→ 确认删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteRangeFlow(
    onDismiss: () -> Unit,
    countIn: (Long, Long) -> Int,
    onDelete: (Long, Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    val s = start
    val e = end
    when {
        s == null || e == null -> {
            val picking = if (s == null) "开始日期" else "结束日期"
            val state = rememberDatePickerState(
                initialSelectedDateMillis = (s ?: LocalDate.now(zone)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            // 1.4.0-alpha03：与其他日期选择一样带防截屏标记，按钮换成金匮自己的按钮
            DatePickerDialog(
                onDismissRequest = onDismiss,
                properties = SecureDialog,
                confirmButton = {
                    JkButton("下一步", {
                        val picked = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        if (picked != null) {
                            if (s == null) start = picked else end = if (picked.isBefore(s)) s.also { start = picked } else picked
                        }
                    }, compact = true, modifier = Modifier.padding(end = Space.x1, bottom = Space.x1))
                },
                dismissButton = {
                    JkButton("取消", onDismiss, style = ButtonStyle.Ghost, compact = true, modifier = Modifier.padding(bottom = Space.x1))
                },
            ) {
                Text(
                    "选择$picking",
                    style = JkType.section,
                    modifier = Modifier.padding(start = Space.x3, top = Space.x2),
                )
                DatePicker(state = state, title = null, headline = null, showModeToggle = true)
            }
        }
        else -> {
            val from = s.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = e.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val n = remember(from, until) { countIn(from, until) }
            JkDialog(
                title = "删除 $s 至 $e 的全部流水？",
                onDismiss = onDismiss,
                confirmText = if (n > 0) "删除 $n 笔" else "好",
                danger = n > 0,
                onConfirm = { if (n > 0) onDelete(from, until) else onDismiss() },
            ) {
                Text(
                    if (n > 0) "这段时间里共有 $n 笔流水（含手动记的），会全部删除，账户余额随之调整。删除后几秒内可以在底部点“撤销”。"
                    else "这段时间里没有流水。",
                    style = JkType.body,
                )
            }
        }
    }
}
