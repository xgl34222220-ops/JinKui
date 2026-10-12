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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerRecurring
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.RecurFrequency
import com.xiguli.jinkui.data.ledger.RecurringMath
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkEmpty
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.components.SecureDialog
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 周期记账列表的一行，后台线程算好。 */
data class RecurringRowUi(
    val rule: LedgerRecurring,
    val title: String,
    val schedule: String,
    val next: LocalDate?,
    val blocked: String?,
)

internal fun buildRecurringUi(data: LedgerData, today: LocalDate = LocalDate.now()): List<RecurringRowUi> {
    val categories = data.categories.associateBy { it.id }
    val accounts = data.accounts.associateBy { it.id }
    return data.recurring
        .sortedWith(compareBy<LedgerRecurring> { it.paused }.thenBy { RecurringMath.upcomingDate(it, today) ?: LocalDate.MAX })
        .map { rule ->
            val title = when {
                rule.note.isNotBlank() -> rule.note
                rule.type == TxType.TRANSFER ->
                    "${accounts[rule.accountId]?.name ?: "未知账户"} → ${accounts[rule.toAccountId]?.name ?: "未知账户"}"
                else -> categoryLabel(rule.categoryId, categories)
            }
            RecurringRowUi(rule, title, RecurringMath.describe(rule), RecurringMath.upcomingDate(rule, today), RecurringMath.blockedReason(rule, data.accounts))
        }
}

class LedgerRecurringViewModel(private val container: AppContainer) : ViewModel() {
    val rows: StateFlow<List<RecurringRowUi>> = container.ledger.data
        .map { buildRecurringUi(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildRecurringUi(container.ledger.data.value))

    fun save(rule: LedgerRecurring, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val before = container.ledger.data.value.transactions.size
                container.ledger.saveRecurring(rule)
                val made = container.ledger.data.value.transactions.size - before
                container.messages.show(if (made > 0) "已保存，补记了 $made 笔" else "已保存")
                onDone()
            } catch (e: LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun delete(rule: LedgerRecurring, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { container.ledger.deleteRecurring(rule.id) }
                .onSuccess {
                    container.messages.show("已删除，之前记下的流水保留")
                    onDone()
                }
                .onFailure { container.messages.show("删除失败") }
        }
    }
}

@Composable
fun LedgerRecurringScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { LedgerRecurringViewModel(container) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val colors = Jk.colors
    var editing by remember { mutableStateOf<LedgerRecurring?>(null) }

    fun newRule(): LedgerRecurring? {
        val account = data.accounts.firstOrNull { !it.archived }
        if (account == null) {
            container.messages.show("请先在“账户与余额”里添加一个账户")
            return null
        }
        return LedgerRecurring(
            id = LedgerIds.newId(),
            type = TxType.EXPENSE,
            amount = 0L,
            accountId = account.id,
            categoryId = data.categories.firstOrNull { it.type == TxType.EXPENSE && it.parentId == null && !it.archived }?.id,
            startDate = LocalDate.now(),
        )
    }

    LedgerRecurringContent(
        rows = rows,
        onBack = onBack,
        onAdd = { editing = newRule() },
        onEdit = { editing = it },
    )

    editing?.let { rule ->
        RecurringEditSheet(
            rule = rule,
            data = data,
            isNew = data.recurring.none { it.id == rule.id },
            onDismiss = { editing = null },
            onSave = { viewModel.save(it) { editing = null } },
            onDelete = { viewModel.delete(rule) { editing = null } },
        )
    }
}

@Composable
private fun RecurringRow(row: RecurringRowUi, onClick: () -> Unit) {
    val colors = Jk.colors
    val rule = row.rule
    val status = when {
        rule.paused -> "已暂停"
        row.blocked != null -> row.blocked + "，暂不生成"
        row.next == null -> "已结束"
        else -> "下次 ${shortDay(row.next)}"
    }
    JkRow(
        title = row.title,
        subtitle = "${row.schedule} · $status",
        icon = if (rule.type == TxType.TRANSFER) TransferIcon else Icons.Outlined.Repeat,
        onClick = onClick,
        trailing = {
            Text(
                text = when (rule.type) {
                    TxType.EXPENSE -> "-" + Money.format(rule.amount)
                    TxType.INCOME -> "+" + Money.format(rule.amount)
                    TxType.TRANSFER -> Money.format(rule.amount)
                },
                style = JkType.amount,
                color = if (rule.paused || row.blocked != null) colors.textTertiary else amountColor(rule.type),
            )
        },
    )
}

private fun shortDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "今天"
    today.plusDays(1) -> "明天"
    else -> if (date.year == today.year) "${date.monthValue}月${date.dayOfMonth}日" else "${date.year}/${date.monthValue}/${date.dayOfMonth}"
}

@Composable
internal fun RecurringEditSheet(
    rule: LedgerRecurring,
    data: LedgerData,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (LedgerRecurring) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Jk.colors
    val active = remember(data.accounts) { data.accounts.filter { !it.archived } }
    val accountsById = remember(data.accounts) { data.accounts.associateBy { it.id } }
    val categoriesById = remember(data.categories) { data.categories.associateBy { it.id } }

    var type by remember { mutableStateOf(rule.type) }
    var amount by remember { mutableStateOf(if (rule.amount > 0) Money.plain(rule.amount) else "") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var accountId by remember { mutableStateOf(rule.accountId) }
    var toAccountId by remember { mutableStateOf(rule.toAccountId ?: active.firstOrNull { it.id != rule.accountId }?.id) }
    var categoryId by remember { mutableStateOf(rule.categoryId) }
    var frequency by remember { mutableStateOf(rule.frequency) }
    var start by remember { mutableStateOf(rule.startDate) }
    var minute by remember { mutableStateOf(rule.minuteOfDay) }
    var end by remember { mutableStateOf(rule.endDate) }
    var note by remember { mutableStateOf(rule.note) }
    var tags by remember { mutableStateOf(rule.tags.joinToString(" ")) }
    var paused by remember { mutableStateOf(rule.paused) }

    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val category = categoryId?.let(categoriesById::get)?.takeIf { it.type == type }

    JkSheet(onDismiss = onDismiss, title = if (isNew) "添加周期记账" else "编辑周期记账") {
        Column(
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            JkSegmented(
                options = TxType.entries.map { it to it.label },
                selected = type,
                onSelect = { picked ->
                    type = picked
                    if (picked != TxType.TRANSFER && categoryId?.let(categoriesById::get)?.type != picked) {
                        categoryId = data.categories.firstOrNull { it.type == picked && it.parentId == null && !it.archived }?.id
                    }
                },
            )
            Spacer(Modifier.height(Space.x1_5))
            JkTextField(
                value = amount,
                onValueChange = {
                    amount = it.take(16)
                    amountError = null
                },
                label = "每次金额",
                placeholder = "例如 3500",
                keyboardType = KeyboardType.Decimal,
                error = amountError,
            )
            Spacer(Modifier.height(Space.x1))
            if (type == TxType.TRANSFER) {
                PickRow("转出账户", accountsById[accountId]?.name ?: "选择账户") { pickFrom = true }
                PickRow("转入账户", toAccountId?.let(accountsById::get)?.name ?: "选择账户") { pickTo = true }
            } else {
                PickRow("账户", accountsById[accountId]?.name ?: "选择账户") { pickFrom = true }
                PickRow("分类", if (category != null) categoryLabel(category.id, categoriesById) else "选择分类") { pickCategory = true }
            }

            JkSectionLabel("重复")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                items(RecurFrequency.entries, key = { it.name }, contentType = { "ledgerrecurringscreen-option" }) { f -> JkChip(f.label, frequency == f, { frequency = f }) }
            }
            Spacer(Modifier.height(Space.x1))
            PickRow("开始日期", "${start.year}年${start.monthValue}月${start.dayOfMonth}日") { pickStart = true }
            PickRow("记账时间", "%02d:%02d".format(minute / 60, minute % 60)) { pickTime = true }
            PickRow("结束日期", end?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: "一直重复") { pickEnd = true }
            val preview = LedgerRecurring(
                id = rule.id, type = type, amount = 1, accountId = accountId,
                frequency = frequency, startDate = start, minuteOfDay = minute, endDate = end,
            )
            Text(
                RecurringMath.describe(preview) + if (isNew && start < LocalDate.now()) "。开始日期早于今天，保存后会把至今到期的各期一次补记上。" else "",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x05),
            )

            Spacer(Modifier.height(Space.x1_5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkTextField(value = note, onValueChange = { note = it.take(500) }, placeholder = "备注（如 房租）", modifier = Modifier.weight(3f))
                JkTextField(value = tags, onValueChange = { tags = it.take(120) }, placeholder = "标签", modifier = Modifier.weight(2f))
            }
            if (!isNew) {
                Spacer(Modifier.height(Space.x1))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("暂停", style = JkType.body, color = colors.textPrimary)
                        Text("暂停时不自动记；恢复后，暂停期间该记的会一次补上", style = JkType.caption, color = colors.textTertiary)
                    }
                    JkSwitch(paused, { paused = it })
                }
            }
        }
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) {
                JkIconButton(Icons.Outlined.DeleteOutline, "删除这条周期记账", { confirmDelete = true }, tint = colors.danger)
            }
            JkButton(
                "保存",
                {
                    val cents = Money.parse(amount)
                    if (cents == null || cents <= 0L) {
                        amountError = "请输入大于 0 的金额，最多两位小数"
                        return@JkButton
                    }
                    onSave(
                        rule.copy(
                            type = type,
                            amount = cents,
                            accountId = accountId,
                            toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
                            categoryId = if (type == TxType.TRANSFER) null else category?.id,
                            note = note,
                            tags = LedgerMath.parseTags(tags),
                            frequency = frequency,
                            startDate = start,
                            minuteOfDay = minute,
                            endDate = end,
                            paused = paused,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (pickFrom || pickTo) {
        AccountPickerSheet(
            title = if (pickTo) "转入账户" else if (type == TxType.TRANSFER) "转出账户" else "选择账户",
            accounts = if (pickTo) active.filter { it.id != accountId } else active,
            balances = remember(data.accounts, data.transactions) { LedgerMath.balances(data.accounts, data.transactions) },
            onDismiss = {
                pickFrom = false
                pickTo = false
            },
            onPick = { account: LedgerAccount ->
                if (pickTo) {
                    toAccountId = account.id
                } else {
                    if (type == TxType.TRANSFER && account.id == toAccountId) toAccountId = accountId
                    accountId = account.id
                }
                pickFrom = false
                pickTo = false
            },
        )
    }

    if (pickCategory) {
        CategoryPickerSheet(
            categories = data.categories,
            title = "选择分类",
            type = type,
            onDismiss = { pickCategory = false },
            onPick = { id ->
                if (id != null) categoryId = id
                pickCategory = false
            },
        )
    }

    if (pickStart || pickEnd) {
        val initialDate = if (pickEnd) end ?: start else start
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = {
                pickStart = false
                pickEnd = false
            },
            properties = SecureDialog,
            confirmButton = {
                JkButton("确定", {
                    val picked = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    if (picked != null) {
                        if (pickEnd) end = picked else start = picked
                    }
                    pickStart = false
                    pickEnd = false
                }, compact = true, modifier = Modifier.padding(end = Space.x1, bottom = Space.x1))
            },
            dismissButton = {
                if (pickEnd && end != null) {
                    JkButton("一直重复", {
                        end = null
                        pickEnd = false
                    }, style = ButtonStyle.Ghost, compact = true, modifier = Modifier.padding(bottom = Space.x1))
                } else {
                    JkButton("取消", {
                        pickStart = false
                        pickEnd = false
                    }, style = ButtonStyle.Ghost, compact = true, modifier = Modifier.padding(bottom = Space.x1))
                }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        JkDialog(
            title = "记账时间",
            onDismiss = { pickTime = false },
            confirmText = "确定",
            onConfirm = {
                minute = state.hour * 60 + state.minute
                pickTime = false
            },
        ) {
            TimePicker(state = state)
        }
    }

    if (confirmDelete) {
        JkDialog(
            title = "删除这条周期记账？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        ) {
            Text("以后不再自动记账；之前已经记下的流水保留。", style = JkType.body)
        }
    }
}

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(Radius.control)
            .clickable(role = Role.Button, onClickLabel = "选择$label", onClick = onClick)
            .padding(horizontal = Space.x05),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = JkType.body, color = colors.textSecondary, modifier = Modifier.width(88.dp))
        Text(
            value,
            style = JkType.body,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = JkType.body, color = colors.textTertiary)
    }
}

/** 周期记账列表（无状态）。 */
@Composable
fun LedgerRecurringContent(
    rows: List<RecurringRowUi>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (LedgerRecurring) -> Unit,
) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar("周期记账", onBack = onBack) {
            JkIconButton(Icons.Outlined.Add, "添加周期记账", onAdd)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.x2_5, end = Space.x2_5, bottom = Space.x6),
        ) {
            item(key = "ledgerrecurringscreen-524", contentType = "ledgerrecurringscreen-static") {
                Text(
                    "房租、工资、会员订阅这类固定收支，设一次就好：每次解锁金匮时，把到期的各期自动记上。不需要后台任务，也不联网。",
                    style = JkType.small,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = Space.x05, vertical = Space.x1),
                )
            }
            if (rows.isEmpty()) {
                item(key = "ledgerrecurringscreen-533", contentType = "ledgerrecurringscreen-static") {
                    JkEmpty(
                        title = "还没有周期记账",
                        message = "点右上角的 + 添加一条，例如每月 10 日的工资。",
                    )
                }
            } else {
                item(key = "ledgerrecurringscreen-540", contentType = "ledgerrecurringscreen-static") {
                    JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                        rows.forEachIndexed { index, row ->
                            if (index > 0) JkDivider(inset = Space.x6 + Space.x05)
                            RecurringRow(row, onClick = { onEdit(row.rule) })
                        }
                    }
                }
            }
        }
    }
}
