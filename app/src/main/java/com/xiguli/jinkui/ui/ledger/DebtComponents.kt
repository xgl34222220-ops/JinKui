package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.DueItem
import com.xiguli.jinkui.data.ledger.DueKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.SecureDialog
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import com.xiguli.jinkui.ui.theme.jkSurface
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// 欠款 / 账单（1.4）几页共用的小部件。逾期一律用支出色（colors.expense）标红。

/** “10月10日”；不是今年时带年份。 */
fun debtDate(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) "${date.monthValue}月${date.dayOfMonth}日" else "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

/** 到期说明：“今天到期”“3 天后到期”“已逾期 2 天”。 */
fun dueLabel(due: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days < 0 -> "已逾期 ${-days} 天"
        days == 0L -> "今天到期"
        days == 1L -> "明天到期"
        else -> "$days 天后到期"
    }
}

/** 小节标题（读屏当作标题）。 */
@Composable
fun DebtSection(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Space.x05, top = Space.x2_5, bottom = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = JkType.section, color = Jk.colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}

/** 文字按钮（小节右侧的“查看全部”“设置”等）。 */
@Composable
fun DebtLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = JkType.small.copy(fontWeight = FontWeight.Bold),
        color = Jk.colors.accent,
        modifier = modifier
            .heightIn(min = JkSize.touch)
            .clip(Radius.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x1_5, vertical = Space.x1_5),
    )
}

/** 待还 / 待收列表的一行：左边日期块，中间名称与说明，右边金额；逾期标红。 */
@Composable
fun DueRow(item: DueItem, today: LocalDate, onClick: () -> Unit) {
    val colors = Jk.colors
    val tone = when {
        item.overdue -> colors.expense
        !item.counts -> colors.textSecondary
        else -> colors.textPrimary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x2, vertical = Space.x1)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.title}，${item.subtitle}，${Money.format(item.amount)}，${debtDate(item.dueDate, today)}${if (item.counts) dueLabel(item.dueDate, today) else ""}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .size(width = 48.dp, height = 48.dp)
                .clip(Radius.tile)
                .background(if (item.overdue) colors.expenseSoft else colors.accentSoft),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("${item.dueDate.monthValue}月", style = JkType.caption, color = if (item.overdue) colors.expense else colors.accent)
            Text("${item.dueDate.dayOfMonth}", style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = if (item.overdue) colors.expense else colors.accent)
        }
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = JkType.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(item.amount), style = JkType.amount.copy(fontWeight = FontWeight.Bold), color = tone)
            if (item.counts) {
                Text(
                    (if (item.payable) "" else "待收 · ") + dueLabel(item.dueDate, today),
                    style = JkType.caption,
                    color = if (item.overdue) colors.expense else colors.textTertiary,
                )
            } else if (item.kind == DueKind.INSTALLMENT || item.kind == DueKind.CREDIT_OPEN) {
                Text("预计", style = JkType.caption, color = colors.textTertiary)
            }
        }
    }
}

/**
 * 账本首页的到期提示（玻璃条）：3 天内到期或已逾期时出现。有逾期时文字用支出色。
 */
@Composable
fun DebtBanner(reminders: List<DueItem>, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (reminders.isEmpty()) return
    val colors = Jk.colors
    val overdue = reminders.count { it.overdue }
    val first = reminders.first()
    val text = when {
        reminders.size == 1 -> "${first.title}${if (first.kind == DueKind.CREDIT) "账单" else ""} ${Money.format(first.amount)} · ${dueLabel(first.dueDate, today)}"
        overdue > 0 -> "${reminders.size} 笔欠款 / 账单待处理，其中 $overdue 笔已逾期"
        else -> "${reminders.size} 笔欠款 / 账单 3 天内到期"
    }
    val tone = if (overdue > 0) colors.expense else colors.accent
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = JkSize.touch)
            .jkSurface(Jk.surfaces.chipSelected, Radius.control)
            .clickable(role = Role.Button, onClickLabel = "查看欠款与账单", onClick = onClick)
            .padding(horizontal = Space.x2, vertical = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.x1_5))
        Text(text, style = JkType.bodyStrong, color = tone, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
    }
}

/** 账户选择：一行玻璃标签。 */
@Composable
fun AccountChips(accounts: List<LedgerAccount>, selected: String?, onSelect: (String) -> Unit) {
    if (accounts.isEmpty()) {
        Text("还没有资金账户，请先在“账户与余额”里添加。", style = JkType.small, color = Jk.colors.textTertiary)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
        items(accounts, key = { it.id }, contentType = { "account" }) { a ->
            JkChip(a.name, a.id == selected, { onSelect(a.id) })
        }
    }
}

/** 一个日期字段：点开系统日期选择（可选将来的日期）；[onClear] 不为空时可以清掉。 */
@Composable
fun DateField(label: String, date: LocalDate?, today: LocalDate, onPick: (LocalDate) -> Unit, onClear: (() -> Unit)? = null, placeholder: String = "不设置") {
    val colors = Jk.colors
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .jkSurface(Jk.surfaces.control, Radius.field)
            .clickable(role = Role.Button, onClickLabel = "选择$label") { open = true }
            .padding(start = Space.x2, end = Space.x05),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Event, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.x1_5))
        Text(label, style = JkType.small, color = colors.textSecondary)
        Spacer(Modifier.width(Space.x1))
        Text(
            date?.let { debtDate(it, today) } ?: placeholder,
            style = JkType.bodyStrong,
            color = if (date == null) colors.textTertiary else colors.textPrimary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
        if (onClear != null && date != null) {
            DebtLink("清除", onClear)
        } else {
            Spacer(Modifier.width(Space.x1_5))
        }
    }
    if (open) {
        val initial = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            properties = SecureDialog,
            confirmButton = {
                JkButton("确定", {
                    state.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }, compact = true, modifier = Modifier.padding(end = Space.x1, bottom = Space.x1))
            },
            dismissButton = {
                JkButton("取消", { open = false }, style = ButtonStyle.Ghost, compact = true, modifier = Modifier.padding(bottom = Space.x1))
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

/** 额度 / 进度条：底色轨道 + 薄荷（超过时用支出色）。 */
@Composable
fun DebtBar(ratio: Float, modifier: Modifier = Modifier, danger: Boolean = false) {
    val colors = Jk.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(colors.surfaceAlt),
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(CircleShape)
                .background(if (danger) colors.expense else colors.primary),
        )
    }
}

/** 三列小数字（应还 / 已还 / 剩余等）。 */
@Composable
fun DebtFigures(vararg figures: Triple<String, String, Color?>) {
    Row(Modifier.fillMaxWidth()) {
        for ((label, value, tone) in figures) {
            Column(Modifier.weight(1f)) {
                Text(label, style = JkType.caption, color = Jk.colors.textTertiary)
                Text(value, style = JkType.amountSmall.copy(fontWeight = FontWeight.Bold), color = tone ?: Jk.colors.textPrimary, maxLines = 1)
            }
        }
    }
}
