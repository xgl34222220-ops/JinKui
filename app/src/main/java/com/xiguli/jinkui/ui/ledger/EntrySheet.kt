package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.xiguli.jinkui.data.ledger.EntryDefaults
import com.xiguli.jinkui.ui.components.JkBareField
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import com.xiguli.jinkui.ui.theme.JkSize
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
 * 记一笔 / 改一笔（1.0 重做，参考钱迹 / iCost 的一屏记账）：
 * 最上面是类型切换与大号金额，中间是分类宫格（默认选中第一个），下面一行账户 / 日期 / 时间与备注，
 * 最底下是数字键盘。打开面板 → 输金额 → 点“完成”，不用离开这一屏。
 * 账户默认跟着分类走：选了分类，就用这个分类上一次用的账户（[EntryDefaults.accountFor]）；手动改过账户后不再自动换。
 * 金额用面板自带的数字键盘输入（不经过系统输入法），支持简单加减。
 * [onSave] 的第三个参数在保存成功、需要继续记下一笔时调用，用来清空金额与备注。
 */
@Composable
fun EntrySheet(
    data: LedgerData,
    existing: LedgerTransaction?,
    onDismiss: () -> Unit,
    onSave: (LedgerTransaction, Boolean, () -> Unit) -> Unit,
    onDelete: (LedgerTransaction) -> Unit,
    /** 校验提示；默认走全局消息条。界面测试传入自己的实现，就不需要 AppContainer。 */
    onMessage: ((String) -> Unit)? = null,
    /** 新记一笔时的初始时间（测试用固定值）。 */
    initialTime: Long? = null,
    /** 新记一笔时预选的分类（首页“快速记一笔”）。 */
    initialCategory: String? = null,
    /** “再记一笔”：照这一笔预填类型、金额、分类、账户、备注与标签；ID 与时间是新的。 */
    template: LedgerTransaction? = null,
) {
    val message: (String) -> Unit = onMessage ?: LocalContainer.current.messages::show
    val touch = LocalUserActivity.current
    val colors = Jk.colors
    val zone = remember { ZoneId.systemDefault() }
    val active = remember(data.accounts) { data.accounts.filter { !it.archived } }
    val seed = existing ?: template
    val presetType = remember(initialCategory, data.categories) {
        initialCategory?.let { id -> data.categories.firstOrNull { it.id == id }?.type }
    }
    // 新记一笔时默认用这个分类上一次用的账户；没有就用最近一笔的账户
    fun defaultAccount(category: String?): String? =
        EntryDefaults.accountFor(category, data.transactions, data.categories, active)

    fun firstTop(t: TxType): String? =
        data.categories.filter { it.type == t && it.parentId == null && !it.archived }.minByOrNull { it.position }?.id

    var id by remember { mutableStateOf(existing?.id ?: LedgerIds.newId()) }
    var type by remember { mutableStateOf(seed?.type ?: presetType ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(seed?.amount?.let(Money::plain) ?: "") }
    var categoryId by remember { mutableStateOf(seed?.categoryId ?: initialCategory) }
    var accountId by remember {
        mutableStateOf(seed?.accountId?.takeIf { id -> existing != null || active.any { it.id == id } } ?: defaultAccount(seed?.categoryId ?: initialCategory ?: firstTop(seed?.type ?: presetType ?: TxType.EXPENSE)))
    }
    /** 用户手动选过账户后，换分类不再自动改账户。改一笔时也不自动改。 */
    var accountTouched by remember { mutableStateOf(existing != null || template != null) }
    // 转账默认转入另一个账户，避免一打开就是“同一账户转给自己”
    var toAccountId by remember { mutableStateOf(seed?.toAccountId ?: active.firstOrNull { it.id != accountId }?.id) }
    var time by remember { mutableLongStateOf(existing?.occurredAt ?: initialTime ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(seed?.note ?: "") }
    var tags by remember { mutableStateOf(seed?.tags?.joinToString(" ") ?: "") }

    fun pickCategory(newId: String) {
        categoryId = newId
        if (!accountTouched && type != TxType.TRANSFER) defaultAccount(newId)?.let { accountId = it }
    }

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
            message("请输入大于 0 的金额")
            return
        }
        val from = accountId
        if (from == null) {
            message("请先在“账户与余额”里添加一个账户")
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

        // 金额：整块最显眼。左边是这笔的类型与分类，右边等宽数字的大号金额（颜色随类型）
        Spacer(Modifier.height(Space.x1_5))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radius.control)
                .background(txSoft(type).copy(alpha = if (colors.isDark) 0.6f else 0.5f))
                .animateContentSize(tween(200))
                .padding(horizontal = Space.x2, vertical = Space.x1_5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(type.label, style = JkType.overline, color = txColor(type))
                Text(
                    text = if (type == TxType.TRANSFER) "账户之间" else (selectedCategory?.let { categoryLabel(it.id, byId) } ?: tops.firstOrNull()?.name ?: "未分类"),
                    style = JkType.small,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥", style = JkType.title, color = colors.textTertiary, modifier = Modifier.padding(end = Space.x05, bottom = 4.dp))
                    Text(
                        text = amount.ifEmpty { "0" },
                        style = JkType.amountHero,
                        color = if (amount.isEmpty()) colors.textTertiary else amountColor(type),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.semantics { contentDescription = "金额 ${amount.ifEmpty { "0" }}，用下方键盘输入" },
                    )
                }
                if (AmountInput.hasOperator(amount)) {
                    val value = Money.evaluate(amount)
                    Text(
                        text = if (value != null) "= ${Money.format(value)}" else "算式不完整",
                        style = JkType.amountSmall,
                        color = if (value != null && value > 0) colors.textSecondary else colors.danger,
                    )
                }
            }
        }

        Column(
            Modifier
                .padding(top = Space.x1)
                .heightIn(max = 236.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (type == TxType.TRANSFER) {
                Spacer(Modifier.height(Space.x05))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountChip("转出", accountId?.let(accountsById::get), { pickFrom = true }, Modifier.weight(1f))
                    Text("→", style = JkType.heading, color = colors.textTertiary, modifier = Modifier.padding(horizontal = Space.x1))
                    AccountChip("转入", toAccountId?.let(accountsById::get), { pickTo = true }, Modifier.weight(1f))
                }
            } else {
                // 分类宫格：每行 5 个，圆形图标底 + 名称，选中的填上类型色
                for (rowItems in tops.chunked(5)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (top in rowItems) {
                            val chosen = selectedTop?.id == top.id || (selectedTop == null && top.id == tops.firstOrNull()?.id)
                            CategoryTile(top, chosen, onClick = { pickCategory(top.id) }, modifier = Modifier.weight(1f))
                        }
                        repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (children.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                        items(children, key = { it.id }) { child ->
                            JkChip(
                                text = child.name,
                                selected = selectedCategory?.id == child.id,
                                onClick = { pickCategory(if (selectedCategory?.id == child.id) child.parentId ?: child.id else child.id) },
                            )
                        }
                    }
                }
            }
        }

        // 信息条：账户、日期、时间；下面是备注与标签
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            if (type != TxType.TRANSFER) {
                InfoChip(
                    icon = accountId?.let(accountsById::get)?.kind?.let(::accountIcon) ?: accountIcon(AccountKind.OTHER),
                    text = accountId?.let(accountsById::get)?.name ?: "选账户",
                    onClick = { pickFrom = true },
                    description = "账户",
                )
            }
            val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
            val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
            InfoChip(Icons.Outlined.CalendarMonth, shortDate(date), { showDate = true }, description = "日期")
            InfoChip(Icons.Outlined.Schedule, "%02d:%02d".format(clock.hour, clock.minute), { showTime = true }, description = "时间")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            CompactField(note, { note = it.take(500) }, "添加备注", Modifier.weight(3f))
            CompactField(tags, { tags = it.take(120) }, "#标签", Modifier.weight(2f))
        }

        // 数字键盘
        Spacer(Modifier.height(Space.x1_5))
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
                    accountTouched = true
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
private fun CategoryTile(category: LedgerCategory, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val tone = txColor(category.type)
    val bg by animateColorAsState(if (selected) tone else colors.surfaceAlt, tween(160), label = "tileBg")
    Column(
        modifier = modifier
            .clip(Radius.tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                categoryIcon(category.icon),
                contentDescription = null,
                tint = if (selected) (if (colors.isDark) colors.bg else Color.White) else colors.textSecondary,
                modifier = Modifier.size(JkSize.icon),
            )
        }
        Spacer(Modifier.height(Space.x05))
        Text(
            text = category.name,
            style = if (selected) JkType.caption.copy(fontWeight = FontWeight.Medium) else JkType.caption,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一行的小输入框：浅灰胶囊，不画描边。 */
@Composable
private fun CompactField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(Radius.pill)
            .background(colors.surfaceAlt)
            .padding(horizontal = Space.x2),
        contentAlignment = Alignment.CenterStart,
    ) {
        JkBareField(value = value, onValueChange = onChange, placeholder = placeholder, textStyle = JkType.small, imeAction = ImeAction.Done)
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
private fun InfoChip(icon: ImageVector, text: String, onClick: () -> Unit, description: String? = null) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            // 视觉高度 32dp，触控区补足到 48dp
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(Radius.pill)
            .background(colors.surfaceAlt)
            .clickable(role = Role.Button, onClickLabel = description?.let { "选择$it" }, onClick = onClick)
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

/** 4×4 数字键盘：最后一列是退格、加、减与“完成”。每个键按下有轻微的缩放、变色与触感反馈。 */
@Composable
internal fun Keypad(
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
            }
            // 没有“再记”（修改已有流水）时，“完成”占两格，不在键盘上留一个空洞
            PressableKey(
                onClick = onDone,
                weight = if (onAgain != null) 1f else 2f,
                background = Jk.colors.primary,
                pressedBackground = Jk.colors.primary.copy(alpha = 0.86f),
                border = null,
                haptic = confirmHaptic(),
                modifier = Modifier.semantics { contentDescription = "保存"; role = Role.Button },
            ) {
                Text("完成", style = JkType.bodyStrong, color = Jk.colors.onPrimary)
            }
        }
    }
}

/** API 30 起才有 CONFIRM；更早的系统退回到长按的触感。 */
private fun confirmHaptic(): Int =
    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS

/** 键盘上的一个键：按下时缩到 96%、底色加深一档，并发出键盘触感（跟随系统触感设置）。 */
@Composable
private fun RowScope.PressableKey(
    onClick: () -> Unit,
    background: Color,
    pressedBackground: Color,
    border: Color?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    haptic: Int = HapticFeedbackConstants.KEYBOARD_TAP,
    weight: Float = 1f,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "keyScale")
    val fill by animateColorAsState(if (pressed) pressedBackground else background, tween(90), label = "keyFill")
    Box(
        modifier = Modifier
            .weight(weight)
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(Radius.control)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, Radius.control) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = onLongClick?.let { long ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        long()
                    }
                },
                onClick = {
                    view.performHapticFeedback(haptic)
                    onClick()
                },
            )
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
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
    PressableKey(
        onClick = onClick,
        background = if (subtle) colors.accentSoft else colors.surfaceAlt,
        pressedBackground = colors.border,
        border = null,
        modifier = if (description != null) Modifier.clearAndSetSemantics { contentDescription = description; role = Role.Button } else Modifier,
    ) {
        Text(text, style = if (small) JkType.bodyStrong else JkType.title.copy(fontFeatureSettings = "tnum"), color = colors.textPrimary)
    }
}

@Composable
private fun RowScope.IconKey(icon: ImageVector, description: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = Jk.colors
    PressableKey(
        onClick = onClick,
        onLongClick = onLongClick,
        background = colors.accentSoft,
        pressedBackground = colors.border,
        border = null,
    ) {
        Icon(icon, contentDescription = description, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
    }
}
