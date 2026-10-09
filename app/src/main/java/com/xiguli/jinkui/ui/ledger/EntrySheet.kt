package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.AmountInput
import com.xiguli.jinkui.data.ledger.KeypadKey
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.LocalUserActivity
import com.xiguli.jinkui.ui.components.SecureDialog
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 记一笔 / 改一笔。金额用面板自带的数字键盘输入（不经过系统输入法），支持简单加减；
 * 备注与标签用普通输入框。[onSave] 的第三个参数在保存成功、需要继续记下一笔时调用，用来清空金额与备注。
 */
@Composable
fun EntrySheet(
    data: LedgerData,
    existing: LedgerTransaction?,
    onDismiss: () -> Unit,
    onSave: (LedgerTransaction, Boolean, () -> Unit) -> Unit,
    onDelete: (LedgerTransaction) -> Unit,
) {
    val container = LocalContainer.current
    val touch = LocalUserActivity.current
    val colors = Jk.colors
    val zone = remember { ZoneId.systemDefault() }
    val active = remember(data.accounts) { data.accounts.filter { !it.archived } }
    // 新记一笔时默认用上一次记账的账户
    val lastAccount = remember(data) {
        data.transactions.maxByOrNull { it.createdAt }?.accountId?.takeIf { id -> active.any { it.id == id } }
    }

    var id by remember { mutableStateOf(existing?.id ?: LedgerIds.newId()) }
    var type by remember { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(existing?.amount?.let(Money::plain) ?: "") }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var accountId by remember { mutableStateOf(existing?.accountId ?: lastAccount ?: active.firstOrNull()?.id) }
    // 转账默认转入另一个账户，避免一打开就是“同一账户转给自己”
    var toAccountId by remember { mutableStateOf(existing?.toAccountId ?: active.firstOrNull { it.id != accountId }?.id) }
    var time by remember { mutableLongStateOf(existing?.occurredAt ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var tags by remember { mutableStateOf(existing?.tags?.joinToString(" ") ?: "") }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val byId = remember(data.categories) { data.categories.associateBy { it.id } }
    val accountsById = remember(data.accounts) { data.accounts.associateBy { it.id } }
    val tops = remember(data.categories, type) {
        data.categories.filter { it.type == type && it.parentId == null && !it.archived }.sortedBy { it.position }
    }
    // 当前分类不属于这个类型时（例如刚从支出切到收入），默认选第一个
    val selectedCategory = categoryId?.let(byId::get)?.takeIf { it.type == type }
    val selectedTop = selectedCategory?.let { it.parentId?.let(byId::get) ?: it }
    val children = remember(data.categories, selectedTop?.id) {
        selectedTop?.let { top -> data.categories.filter { it.parentId == top.id && !it.archived }.sortedBy { it.position } }.orEmpty()
    }

    fun press(key: KeypadKey) {
        touch()
        amount = AmountInput.press(amount, key)
    }

    fun submit(keepOpen: Boolean) {
        val cents = Money.evaluate(amount)
        if (cents == null || cents <= 0L) {
            container.messages.show("请输入大于 0 的金额")
            return
        }
        val from = accountId
        if (from == null) {
            container.messages.show("请先在“账户与余额”里添加一个账户")
            return
        }
        val tx = LedgerTransaction(
            id = id,
            type = type,
            amount = cents,
            occurredAt = time,
            accountId = from,
            toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
            categoryId = if (type == TxType.TRANSFER) null else (selectedCategory?.id ?: tops.firstOrNull()?.id),
            note = note,
            tags = LedgerMath.parseTags(tags),
            createdAt = existing?.createdAt ?: 0L,
        )
        onSave(tx, keepOpen && existing == null) {
            id = LedgerIds.newId()
            amount = ""
            note = ""
            tags = ""
        }
    }

    JkSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JkSegmented(
                    options = TxType.entries.map { it to it.label },
                    selected = type,
                    onSelect = { type = it },
                    modifier = Modifier.weight(1f),
                )
                if (existing != null) {
                    JkIconButton(Icons.Outlined.DeleteOutline, "删除这笔", { confirmDelete = true }, tint = colors.danger)
                }
            }

            if (type == TxType.TRANSFER) {
                Spacer(Modifier.height(Space.x1_5))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountChip("转出", accountId?.let(accountsById::get), { pickFrom = true }, Modifier.weight(1f))
                    Text("→", style = JkType.heading, color = colors.textTertiary, modifier = Modifier.padding(horizontal = Space.x1))
                    AccountChip("转入", toAccountId?.let(accountsById::get), { pickTo = true }, Modifier.weight(1f))
                }
            } else {
                Spacer(Modifier.height(Space.x1_5))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    verticalArrangement = Arrangement.spacedBy(Space.x1),
                ) {
                    for ((index, top) in tops.withIndex()) {
                        val chosen = selectedTop?.id == top.id || (selectedTop == null && index == 0)
                        CategoryTile(top, chosen, onClick = { categoryId = top.id })
                    }
                }
                if (children.isNotEmpty()) {
                    Spacer(Modifier.height(Space.x1))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                        items(children, key = { it.id }) { child ->
                            JkChip(
                                text = child.name,
                                selected = selectedCategory?.id == child.id,
                                onClick = { categoryId = if (selectedCategory?.id == child.id) child.parentId else child.id },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.x1_5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    placeholder = "备注",
                    modifier = Modifier.weight(3f),
                )
                JkTextField(
                    value = tags,
                    onValueChange = { tags = it.take(120) },
                    placeholder = "标签",
                    modifier = Modifier.weight(2f),
                )
            }
        }

        // 信息条：账户、日期、时间
        Spacer(Modifier.height(Space.x1_5))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalAlignment = Alignment.CenterVertically) {
            if (type != TxType.TRANSFER) {
                InfoChip(
                    icon = accountId?.let(accountsById::get)?.kind?.let(::accountIcon) ?: accountIcon(AccountKind.OTHER),
                    text = accountId?.let(accountsById::get)?.name ?: "选账户",
                    onClick = { pickFrom = true },
                )
            }
            val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
            val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
            InfoChip(Icons.Outlined.CalendarMonth, shortDate(date), { showDate = true })
            InfoChip(Icons.Outlined.Schedule, "%02d:%02d".format(clock.hour, clock.minute), { showTime = true })
        }

        // 金额
        Spacer(Modifier.height(Space.x1_5))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text(
                text = "¥ " + amount.ifEmpty { "0" },
                style = JkType.display,
                color = if (amount.isEmpty()) colors.textTertiary else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            if (AmountInput.hasOperator(amount)) {
                val value = Money.evaluate(amount)
                Text(
                    text = if (value != null) "= ${Money.format(value)}" else "算式不完整",
                    style = JkType.small,
                    color = if (value != null && value > 0) colors.textSecondary else colors.danger,
                )
            }
        }

        // 数字键盘
        Spacer(Modifier.height(Space.x1))
        Keypad(
            onKey = ::press,
            onAgain = if (existing == null) ({ submit(keepOpen = true) }) else null,
            onDone = { submit(keepOpen = false) },
        )
    }

    if (pickFrom || pickTo) {
        val picking = if (pickTo) "转入账户" else if (type == TxType.TRANSFER) "转出账户" else "选择账户"
        AccountPickerSheet(
            title = picking,
            // 选转入账户时不列出转出账户本身
            accounts = if (pickTo) active.filter { it.id != accountId } else active,
            onDismiss = {
                pickFrom = false
                pickTo = false
            },
            onPick = { account ->
                if (pickTo) {
                    toAccountId = account.id
                } else {
                    // 新的转出账户正好是转入账户时，两者对调，而不是留下一个注定保存失败的组合
                    if (type == TxType.TRANSFER && account.id == toAccountId) toAccountId = accountId
                    accountId = account.id
                }
                pickFrom = false
                pickTo = false
            },
        )
    }

    if (showDate) {
        val initial = Instant.ofEpochMilli(time).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // 只能选到今天：将来的开销请用“周期记账”。DatePicker 的毫秒是 UTC 零点。
        val todayUtc = LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc || utcTimeMillis == initial
                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now(zone).year || year == LocalDate.ofEpochDay(initial / 86_400_000L).year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            properties = SecureDialog,
            confirmButton = {
                JkButton("确定", {
                    val picked = state.selectedDateMillis
                    if (picked != null) {
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
                        time = date.atTime(clock).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }, compact = true, modifier = Modifier.padding(end = Space.x1, bottom = Space.x1))
            },
            dismissButton = {
                JkButton("取消", { showDate = false }, style = ButtonStyle.Ghost, compact = true, modifier = Modifier.padding(bottom = Space.x1))
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }

    if (showTime) {
        val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
        val state = rememberTimePickerState(initialHour = clock.hour, initialMinute = clock.minute, is24Hour = true)
        JkDialog(
            title = "选择时间",
            onDismiss = { showTime = false },
            confirmText = "确定",
            onConfirm = {
                val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                time = date.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli()
                showTime = false
            },
        ) {
            TimePicker(state = state)
        }
    }

    if (confirmDelete && existing != null) {
        JkDialog(
            title = "删除这笔记录？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                onDelete(existing)
            },
        ) {
            Text("删除后账户余额会随之调整，无法撤销。", style = JkType.body)
        }
    }
}

private fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> if (date.year == today.year) "${date.monthValue}月${date.dayOfMonth}日" else "${date.year}/${date.monthValue}/${date.dayOfMonth}"
}

@Composable
private fun CategoryTile(category: LedgerCategory, selected: Boolean, onClick: () -> Unit) {
    val colors = Jk.colors
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(Radius.tile)
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.border, Radius.tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            categoryIcon(category.icon),
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = category.name,
            style = JkType.caption,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountChip(label: String, account: LedgerAccount?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    Row(
        modifier = modifier
            .clip(Radius.control)
            .border(1.dp, colors.border, Radius.control)
            .clickable(role = Role.Button, onClickLabel = "选择$label" + "账户", onClick = onClick)
            .padding(horizontal = Space.x1_5, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account != null) {
            Icon(accountIcon(account.kind), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.x1))
        }
        Column {
            Text(label, style = JkType.caption, color = colors.textTertiary)
            Text(account?.name ?: "选择账户", style = JkType.body, color = if (account != null) colors.textPrimary else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            // 视觉高度 32dp，触控区补足到 48dp
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(Radius.chip)
            .background(colors.surfaceAlt)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.x05))
        Text(text, style = JkType.small, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
fun AccountPickerSheet(
    title: String,
    accounts: List<LedgerAccount>,
    onDismiss: () -> Unit,
    onPick: (LedgerAccount) -> Unit,
) {
    JkSheet(onDismiss = onDismiss, title = title) {
        if (accounts.isEmpty()) {
            Text("还没有可用的账户，先到“账户与余额”里添加一个。", style = JkType.small, color = Jk.colors.textTertiary)
        }
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            for (account in accounts) {
                JkRow(
                    title = account.name,
                    subtitle = account.kind.label,
                    icon = accountIcon(account.kind),
                    modifier = Modifier.clip(Radius.control),
                    trailing = {},
                    onClick = { onPick(account) },
                )
            }
        }
    }
}

/** 4×4 数字键盘：最后一列是退格、加、减与“完成”。 */
@Composable
private fun Keypad(
    onKey: (KeypadKey) -> Unit,
    onAgain: (() -> Unit)?,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (d in "123") DigitKey(d.toString(), { onKey(KeypadKey.Digit(d)) })
            IconKey(Icons.AutoMirrored.Outlined.Backspace, "退格", { onKey(KeypadKey.Backspace) }, onLongClick = { onKey(KeypadKey.Clear) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (d in "456") DigitKey(d.toString(), { onKey(KeypadKey.Digit(d)) })
            DigitKey("+", { onKey(KeypadKey.Plus) }, subtle = true, description = "加")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (d in "789") DigitKey(d.toString(), { onKey(KeypadKey.Digit(d)) })
            DigitKey("−", { onKey(KeypadKey.Minus) }, subtle = true, description = "减")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            DigitKey(".", { onKey(KeypadKey.Dot) }, description = "小数点")
            DigitKey("0", { onKey(KeypadKey.Digit('0')) })
            if (onAgain != null) {
                DigitKey("再记", onAgain, subtle = true, small = true, description = "保存并再记一笔")
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(Radius.control)
                    .background(Jk.colors.primary)
                    .clickable(role = Role.Button, onClickLabel = "保存", onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text("完成", style = JkType.bodyStrong, color = Jk.colors.onPrimary)
            }
        }
    }
}

@Composable
private fun RowScope.DigitKey(
    text: String,
    onClick: () -> Unit,
    subtle: Boolean = false,
    small: Boolean = false,
    description: String? = null,
) {
    val colors = Jk.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(Radius.control)
            .background(if (subtle) colors.surfaceAlt else colors.surface)
            .border(1.dp, colors.border, Radius.control)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description; role = Role.Button } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (small) JkType.bodyStrong else JkType.title, color = colors.textPrimary)
    }
}

@Composable
private fun RowScope.IconKey(icon: ImageVector, description: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = Jk.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(Radius.control)
            .background(colors.surfaceAlt)
            .border(1.dp, colors.border, Radius.control)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
    }
}
