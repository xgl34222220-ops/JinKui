package com.xiguli.jinkui.ui.ledger

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.data.calendar.ChineseCalendar
import com.xiguli.jinkui.data.calendar.DayMark
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.EmptyCalendarArt
import com.xiguli.jinkui.ui.components.MintGlyph
import com.xiguli.jinkui.ui.nav.LocalTabBarInset
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkFonts
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** 底栏“日历”分区：自己的月份状态，记账流程与账本首页同一套。 */
@Composable
fun CalendarScreen() {
    val container = LocalContainer.current
    val viewModel = jkViewModel(key = "calendar") { LedgerViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val host = rememberEntryHost()
    val listState = rememberLazyListState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onClockTick() }

    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val today = ui.today ?: LocalDate.now()
    val day = selected?.takeIf { YearMonth.from(it) == ui.month } ?: defaultDay(ui.month, today)

    CalendarContent(
        ui = ui,
        selected = day,
        onSelect = { selected = it },
        onMonth = {
            viewModel.setMonth(it)
            selected = defaultDay(it, today)
        },
        onAdd = {
            val zone = ZoneId.systemDefault()
            val date = if (day > today) today else day
            val time = if (date == today) System.currentTimeMillis() else date.atTime(LocalTime.now(zone).withSecond(0).withNano(0)).atZone(zone).toInstant().toEpochMilli()
            host.create(time = time)
        },
        actions = host.actions(multiSelect = false),
        listState = listState,
    )
    EntryHost(host, data, viewModel)
}

private fun defaultDay(month: YearMonth, today: LocalDate): LocalDate =
    if (YearMonth.from(today) == month) today else month.atDay(1)

/**
 * 日历（无状态）：标题“2026年10月”与当月收 / 支 / 余，周日开头的月格子——每格是公历日期、
 * 农历（初一显示月名）/ 节气 / 节日，法定节日标“休”，有支出的日子下面一个红点、只有收入是薄荷点；
 * 选中的那天是实心薄荷圆角方块。格子上左右滑动换月。下面列出选中那天的流水，没有时是一张小插画。
 */
@Composable
fun CalendarContent(
    ui: LedgerUi,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onMonth: (YearMonth) -> Unit,
    onAdd: () -> Unit,
    actions: LedgerHomeActions,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    val today = ui.today
    val day = ui.days.firstOrNull { it.date == selected }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = LocalTabBarInset.current + Space.x2),
    ) {
        item(key = "header", contentType = "header") {
          Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.x2, end = Space.x05, top = Space.x1_5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    monthLabel(ui.month),
                    style = JkType.amountLarge.copy(fontSize = 26.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                ChevronButton(true, "上个月") { onMonth(ui.month.minusMonths(1)) }
                ChevronButton(false, "下个月") { onMonth(ui.month.plusMonths(1)) }
                val viewingToday = today != null && selected == today
                if (!viewingToday && today != null) {
                    Box(
                        Modifier
                            .size(JkSize.touch)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "回到今天") {
                                if (YearMonth.from(today) != ui.month) onMonth(YearMonth.from(today))
                                onSelect(today)
                            }
                            .semantics { contentDescription = "回到今天" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("今", style = JkType.heading.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                    }
                }
                MintIconButton(MintGlyph.ADD_NOTE, "在这一天记一笔", onAdd, tint = colors.accent)
            }
            // 当月收 / 支 / 余单独一行，不和右边的按钮抢宽度
            Row(Modifier.padding(horizontal = Space.x2), verticalAlignment = Alignment.CenterVertically) {
                MonthTotal("收", ui.summary.income, colors.income)
                Spacer(Modifier.width(Space.x2))
                MonthTotal("支", ui.summary.expense, colors.expense)
                Spacer(Modifier.width(Space.x2))
                MonthTotal("余", ui.summary.balance, if (ui.summary.balance < 0) colors.danger else colors.textPrimary)
            }
          }
        }
        item(key = "grid", contentType = "grid") {
            LunarMonthGrid(
                month = ui.month,
                days = ui.days,
                today = today,
                selected = selected,
                onSelect = onSelect,
                onSwipe = { forward -> onMonth(if (forward) ui.month.plusMonths(1) else ui.month.minusMonths(1)) },
                modifier = Modifier.padding(horizontal = Space.x1, vertical = Space.x1),
            )
        }
        item(key = "day-title", contentType = "day-title") {
            val mark = remember(selected) { ChineseCalendar.mark(selected) }
            Column(Modifier.padding(start = Space.x2, end = Space.x2, top = Space.x1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selected.monthValue}月${selected.dayOfMonth}日",
                        style = JkType.heading.copy(fontWeight = FontWeight.Bold, fontFamily = JkFonts.numeral),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(Space.x1))
                    Text(weekdayLabel(selected), style = JkType.small, color = colors.textTertiary)
                    Spacer(Modifier.weight(1f))
                    if (day != null) {
                        Text("收", style = JkType.small, color = colors.textTertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(Money.format(day.income, symbol = false), style = JkType.amountSmall, color = colors.income)
                        Spacer(Modifier.width(Space.x1_5))
                        Text("支", style = JkType.small, color = colors.textTertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(Money.format(day.expense, symbol = false), style = JkType.amountSmall, color = colors.expense)
                    }
                }
                val extra = listOfNotNull(
                    ChineseCalendar.describe(selected),
                    mark.solarTerm,
                ) + mark.festivals
                if (extra.isNotEmpty()) {
                    Text(extra.joinToString(" · "), style = JkType.caption, color = colors.textTertiary)
                }
                Spacer(Modifier.height(Space.x1))
            }
        }
        if (day != null) {
            dayRows(day, actions)
        } else if (!ui.loading) {
            item(key = "empty", contentType = "empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.x3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyCalendarArt()
                    Spacer(Modifier.height(Space.x1_5))
                    Text("这一天你还没有任何记账", style = JkType.bodyStrong, color = colors.textSecondary)
                    if (today == null || selected <= today) {
                        Text(
                            "记一笔",
                            style = JkType.small.copy(fontWeight = FontWeight.Bold),
                            color = colors.accent,
                            modifier = Modifier
                                .padding(top = Space.x05)
                                .heightIn(min = JkSize.touch)
                                .clip(Radius.pill)
                                .clickable(role = Role.Button, onClick = onAdd)
                                .padding(horizontal = Space.x2, vertical = Space.x1_5),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthTotal(label: String, cents: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = JkType.small.copy(fontWeight = FontWeight.Medium), color = Jk.colors.textSecondary)
        Spacer(Modifier.width(3.dp))
        Text(Money.format(cents, symbol = false), style = JkType.amountSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun ChevronButton(back: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp, JkSize.touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (back) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight, contentDescription = null, tint = Jk.colors.textSecondary)
    }
}

/** 周日开头的月格子。 */
@Composable
fun LunarMonthGrid(
    month: YearMonth,
    days: List<DayUi>,
    today: LocalDate?,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onSwipe: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    val byDate = remember(days) { days.associateBy { it.date } }
    val maxExpense = remember(days) { days.maxOfOrNull { it.expense }?.coerceAtLeast(1L) ?: 1L }
    val marks = remember(month) { (1..month.lengthOfMonth()).map { ChineseCalendar.mark(month.atDay(it)) } }
    val lead = month.atDay(1).dayOfWeek.value % 7 // 周日开头
    val rows = (lead + month.lengthOfMonth() + 6) / 7
    // 1.3：月份格子跟手左右滑动，松手超过阈值换月——新月份从同一侧滑入、弹簧归位；不够就弹回
    val slide = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val latestSwipe by androidx.compose.runtime.rememberUpdatedState(onSwipe)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = slide.value
                alpha = 1f - (kotlin.math.abs(slide.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.7f
            }
            .pointerInput(month) {
                var drag = 0f
                val spring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.82f, stiffness = 420f)
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragCancel = { scope.launch { slide.animateTo(0f, spring) } },
                    onDragEnd = {
                        val width = size.width.toFloat()
                        val forward = drag < 0f
                        if (kotlin.math.abs(drag) > 120f) {
                            scope.launch {
                                slide.snapTo(if (forward) width * 0.35f else -width * 0.35f)
                                latestSwipe(forward)
                                slide.animateTo(0f, spring)
                            }
                        } else {
                            scope.launch { slide.animateTo(0f, spring) }
                        }
                        drag = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        drag += amount
                        // 跟手但带一点阻力
                        scope.launch { slide.snapTo(drag * 0.6f) }
                    },
                )
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.x1)) {
            for (w in listOf("日", "一", "二", "三", "四", "五", "六")) {
                Text(w, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c - lead
                    if (index < 0 || index >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val date = month.atDay(index + 1)
                    DayCell(
                        date = date,
                        mark = marks[index],
                        day = byDate[date],
                        heat = byDate[date]?.let { (it.expense.toFloat() / maxExpense).coerceIn(0f, 1f) } ?: 0f,
                        isToday = date == today,
                        isSelected = date == selected,
                        future = today != null && date > today,
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    mark: DayMark,
    day: DayUi?,
    heat: Float,
    isToday: Boolean,
    isSelected: Boolean,
    future: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = Jk.colors
    val shape = RoundedCornerShape(12.dp)
    val special = mark.kind == DayMark.Kind.FESTIVAL || mark.kind == DayMark.Kind.SOLAR_TERM
    val numberColor = when {
        isSelected -> colors.onPrimary
        isToday || mark.publicHoliday -> colors.accent
        future -> colors.textSecondary
        else -> colors.textPrimary
    }
    val labelColor = when {
        isSelected -> colors.onPrimary.copy(alpha = 0.92f)
        mark.kind == DayMark.Kind.FESTIVAL -> colors.accent
        special -> colors.textSecondary
        else -> colors.textTertiary
    }
    Box(
        modifier
            .padding(2.dp)
            .heightIn(min = 62.dp)
            .clip(shape)
            .background(if (isSelected) colors.primary else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, colors.primary, shape) else Modifier)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("${date.monthValue}月${date.dayOfMonth}日")
                    if (isToday) append("，今天")
                    ChineseCalendar.describe(date)?.let { append("，$it") }
                    if (mark.festivals.isNotEmpty()) append("，" + mark.festivals.joinToString("、"))
                    mark.solarTerm?.let { append("，$it") }
                    if (day != null && day.totals.isNotEmpty()) append("，${day.totals}") else append("，没有记录")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 5.dp)) {
            Text("${date.dayOfMonth}", style = JkType.amount.copy(fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold), color = numberColor, maxLines = 1)
            Text(
                mark.label,
                style = JkType.caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = if (special) FontWeight.Medium else FontWeight.Normal),
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            // 当日收支的小点：支出越多越红；只有收入时是薄荷点
            val dot = when {
                day == null -> Color.Transparent
                day.expense > 0 -> if (isSelected) colors.onPrimary else colors.expense.copy(alpha = 0.45f + 0.55f * heat)
                day.income > 0 -> if (isSelected) colors.onPrimary else colors.income
                else -> Color.Transparent
            }
            Spacer(Modifier.height(3.dp))
            Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
        }
        if (mark.publicHoliday) {
            Text(
                "休",
                style = JkType.caption.copy(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold),
                color = if (isSelected) colors.onPrimary else colors.accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 4.dp),
            )
        }
    }
}
