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
import com.xiguli.jinkui.data.ledger.RefundMath
import com.xiguli.jinkui.data.ledger.Reimburse
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.RequestQuote
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import kotlinx.coroutines.launch

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
    /** 1.4.0-alpha04：打开时直接进入借贷的某一类。 */
    var loanAction by mutableStateOf<LoanAction?>(null)
    var confirmDelete by mutableStateOf<LedgerTransaction?>(null)
    /** 1.4.0-alpha04：给这笔支出记退款。 */
    var refundFor by mutableStateOf<LedgerTransaction?>(null)
    var menuFor by mutableStateOf<LedgerTransaction?>(null)
    /** 多选模式（长按一行进入）：选中的流水 ID；null 表示不在多选模式。 */
    var selection by mutableStateOf<Set<String>?>(null)
    /** 多选后点“删除”：等确认的那几笔。 */
    var confirmBulk by mutableStateOf<List<LedgerTransaction>?>(null)

    fun toggle(id: String) {
        val current = selection ?: emptySet()
        val next = if (id in current) current - id else current + id
        selection = next
    }

    fun selectAll(ids: List<String>, on: Boolean) {
        val current = selection ?: emptySet()
        selection = if (on) current + ids else current - ids.toSet()
    }

    fun create(category: String? = null, from: LedgerTransaction? = null, time: Long? = null, loan: LoanAction? = null) {
        presetCategory = category
        template = from
        initialTime = time
        loanAction = loan
        creating = true
    }

    fun close() {
        creating = false
        editing = null
        presetCategory = null
        template = null
        initialTime = null
        loanAction = null
    }

    /** 首页 / 日历用的那组行操作。 */
    /** [multiSelect] 为 false（日历分区）时长按仍打开菜单，不进入多选。 */
    fun actions(base: LedgerHomeActions = LedgerHomeActions(), multiSelect: Boolean = true): LedgerHomeActions = LedgerHomeActions(
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
        onPending = base.onPending,
        onLoan = { create(loan = LoanAction.LEND) },
        selection = selection.takeIf { multiSelect },
        onSelectStart = { if (multiSelect) selection = setOf(it.id) else menuFor = it },
        onSelectToggle = { toggle(it.id) },
        onSelectMany = { ids, on -> selectAll(ids, on) },
        onSelectDone = { selection = null },
        onDeleteSelected = { txs -> if (txs.isNotEmpty()) confirmBulk = txs },
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun reimburse(ids: List<String>, to: Reimburse, done: String) {
        scope.launch {
            try {
                container.ledger.setReimburse(ids, to)
                container.messages.show(done)
            } catch (e: com.xiguli.jinkui.data.repo.LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }
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
            initialLoan = state.loanAction,
            onLoan = { r ->
                scope.launch {
                    try {
                        val ledger = container.ledger
                        if (r.action.principal) {
                            ledger.createLoan(r.action.direction, r.person, r.accountId, r.amount, r.fundId, r.date, r.due, r.interest, r.note)
                        } else {
                            ledger.loanPayment(r.loanId!!, r.accountId!!, r.amount, r.interest, r.fundId, r.date, r.note)
                        }
                        state.close()
                        container.messages.show("已记一笔${r.action.label} ${Money.format(r.amount + if (r.action.principal) 0 else r.interest)}")
                    } catch (e: com.xiguli.jinkui.data.repo.LedgerInputException) {
                        container.messages.show(e.message ?: "保存失败")
                    } catch (e: Exception) {
                        container.messages.show("保存失败：${e.message ?: "未知错误"}")
                    }
                }
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
                // 1.4.0-alpha04：退款与报销（只对普通支出；分期、借贷的流水不行）
                if (tx.type == TxType.EXPENSE && tx.debtId == null) {
                    val refund = RefundMath.status(tx, data.transactions)
                    if (tx.reimburse == Reimburse.NONE && refund.remaining > 0) {
                        JkRow(
                            title = "退款",
                            subtitle = if (refund.refunded > 0) "已退 ${Money.format(refund.refunded)}，还能退 ${Money.format(refund.remaining)}" else "全额或部分退款，统计里冲减这笔支出",
                            icon = Icons.AutoMirrored.Outlined.Undo, modifier = Modifier.clip(Radius.control), trailing = {},
                            onClick = {
                                state.menuFor = null
                                state.refundFor = tx
                            },
                        )
                    }
                    if (refund.refunds.isEmpty()) {
                        when (tx.reimburse) {
                            Reimburse.NONE -> JkRow(
                                title = "标记待报销", subtitle = "替单位或别人垫付的钱，不算你的支出",
                                icon = Icons.Outlined.RequestQuote, modifier = Modifier.clip(Radius.control), trailing = {},
                                onClick = {
                                    state.menuFor = null
                                    reimburse(listOf(tx.id), Reimburse.PENDING, "已标记待报销，可在“我的 · 报销”里查看")
                                },
                            )
                            Reimburse.PENDING -> JkRow(
                                title = "取消待报销", subtitle = "重新算作你的支出",
                                icon = Icons.Outlined.RequestQuote, modifier = Modifier.clip(Radius.control), trailing = {},
                                onClick = {
                                    state.menuFor = null
                                    reimburse(listOf(tx.id), Reimburse.NONE, "已取消报销标记")
                                },
                            )
                            Reimburse.DONE -> Unit
                        }
                    }
                }
                JkRow(title = "删除", icon = Icons.Outlined.DeleteOutline, danger = true, modifier = Modifier.clip(Radius.control), onClick = {
                    state.menuFor = null
                    state.confirmDelete = tx
                })
            }
        }
    }

    state.refundFor?.let { tx ->
        val status = RefundMath.status(tx, data.transactions)
        RefundSheet(
            status = status,
            category = categoryLabel(tx.categoryId, data.categories.associateBy { it.id }),
            accounts = refundAccounts(data),
            today = java.time.LocalDate.now(),
            onDismiss = { state.refundFor = null },
        ) { amount, accountId, date, note ->
            state.refundFor = null
            scope.launch {
                try {
                    container.ledger.refund(tx.id, amount, accountId, date, note)
                    container.messages.show(if (amount >= status.remaining) "已记全额退款 ${Money.format(amount)}" else "已记退款 ${Money.format(amount)}")
                } catch (e: com.xiguli.jinkui.data.repo.LedgerInputException) {
                    container.messages.show(e.message ?: "保存失败")
                } catch (e: Exception) {
                    container.messages.show("保存失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    state.confirmBulk?.let { txs ->
        val total = txs.filter { it.type != TxType.TRANSFER }.sumOf { it.amount }
        JkDialog(
            title = "删除选中的 ${txs.size} 笔？",
            onDismiss = { state.confirmBulk = null },
            confirmText = "删除 ${txs.size} 笔",
            danger = true,
            onConfirm = {
                state.confirmBulk = null
                state.selection = null
                viewModel.deleteMany(txs.map { it.id })
            },
        ) {
            Text("收支合计 ${Money.format(total)}。删除后账户余额会随之调整，删除后几秒内可以在底部点“撤销”恢复。", style = JkType.body)
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
