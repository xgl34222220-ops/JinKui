package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius

/**
 * “记一笔”面板、长按菜单与删除确认的状态。账本首页与日历分区共用一套，
 * 两边看到的记账流程完全一样（日历里新记一笔时，日期预填为选中的那天）。
 */
@Stable
class EntryHostState {
    var creating by mutableStateOf(false)
    var editing by mutableStateOf<LedgerTransaction?>(null)
    var presetCategory by mutableStateOf<String?>(null)
    var template by mutableStateOf<LedgerTransaction?>(null)
    var initialTime by mutableStateOf<Long?>(null)
    var confirmDelete by mutableStateOf<LedgerTransaction?>(null)
    var menuFor by mutableStateOf<LedgerTransaction?>(null)

    fun create(category: String? = null, from: LedgerTransaction? = null, time: Long? = null) {
        presetCategory = category
        template = from
        initialTime = time
        creating = true
    }

    fun close() {
        creating = false
        editing = null
        presetCategory = null
        template = null
        initialTime = null
    }

    /** 首页 / 日历用的那组行操作。 */
    fun actions(base: LedgerHomeActions = LedgerHomeActions()): LedgerHomeActions = LedgerHomeActions(
        onMonth = base.onMonth,
        onQuery = base.onQuery,
        onPickAccount = base.onPickAccount,
        onPickCategory = base.onPickCategory,
        onClearFilters = base.onClearFilters,
        onOpen = { editing = it },
        onCreate = { create() },
        onStats = base.onStats,
        onBudget = base.onBudget,
        onLock = base.onLock,
        onMore = base.onMore,
        onAssets = base.onAssets,
        onQuickAdd = { create(category = it) },
        onRepeat = { create(from = it) },
        onDelete = { confirmDelete = it },
        onRowMenu = { menuFor = it },
    )
}

@Composable
fun rememberEntryHost(): EntryHostState = remember { EntryHostState() }

@Composable
fun EntryHost(
    state: EntryHostState,
    data: LedgerData,
    viewModel: LedgerViewModel,
) {
    val container = LocalContainer.current
    if (state.creating || state.editing != null) {
        EntrySheet(
            data = data,
            existing = state.editing,
            initialCategory = state.presetCategory,
            template = state.template,
            initialTime = state.initialTime,
            onDismiss = { state.close() },
            onSave = { tx, keepOpen, reset ->
                viewModel.save(tx) {
                    if (!keepOpen) {
                        state.close()
                    } else {
                        reset()
                        container.messages.show("已记下，继续下一笔")
                    }
                }
            },
            onDelete = { tx ->
                viewModel.delete(tx)
                state.editing = null
            },
        )
    }

    state.menuFor?.let { tx ->
        JkSheet(
            onDismiss = { state.menuFor = null },
            title = categoryLabel(tx.categoryId, data.categories.associateBy { it.id }).takeIf { tx.type != TxType.TRANSFER } ?: "转账",
        ) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                JkRow(title = "修改", icon = Icons.Outlined.Edit, modifier = Modifier.clip(Radius.control), trailing = {}, onClick = {
                    state.menuFor = null
                    state.editing = tx
                })
                JkRow(title = "再记一笔", subtitle = "金额、分类、账户和备注照搬，时间是现在", icon = Icons.Outlined.ContentCopy, modifier = Modifier.clip(Radius.control), trailing = {}, onClick = {
                    state.menuFor = null
                    state.create(from = tx)
                })
                JkRow(title = "删除", icon = Icons.Outlined.DeleteOutline, danger = true, modifier = Modifier.clip(Radius.control), onClick = {
                    state.menuFor = null
                    state.confirmDelete = tx
                })
            }
        }
    }

    state.confirmDelete?.let { tx ->
        JkDialog(
            title = "删除这笔记录？",
            onDismiss = { state.confirmDelete = null },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                state.confirmDelete = null
                viewModel.delete(tx)
            },
        ) {
            Text("${Money.format(tx.amount)}，删除后账户余额会随之调整，无法撤销。", style = JkType.body)
        }
    }
}
