package com.xiguli.jinkui.ui.ledger

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.CategoryShare
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerClock
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.Summary
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.nav.LocalTabBarInset
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkFonts
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** 统计的时间范围：月统计 / 年统计。 */
enum class StatsMode(val label: String) { MONTH("月统计"), YEAR("年统计") }

/** 柱状图与热力图看的是哪一项。 */
enum class Measure(val label: String) { EXPENSE("支出"), INCOME("收入"), BALANCE("结余") }

fun Summary.of(measure: Measure): Long = when (measure) {
    Measure.EXPENSE -> expense
    Measure.INCOME -> income
    Measure.BALANCE -> balance
}

/** 一个月的每日收支，热力图用。[days] 的第 i 项是 i + 1 日。 */
@Immutable
data class HeatMonth(val month: YearMonth, val days: List<Summary>)

/** 柱状图的一根柱子。 */
@Immutable
data class BarPoint(val label: String, val summary: Summary, val current: Boolean)

@Immutable
data class StatsUi(
    val mode: StatsMode,
    val year: Int,
    val month: YearMonth,
    val summary: Summary,
    /** 上一期（上个月 / 去年）的汇总，算环比。 */
    val previous: Summary,
    val bars: List<BarPoint>,
    val heat: List<HeatMonth>,
    val expenseShares: List<CategoryShare>,
    val incomeShares: List<CategoryShare>,
    val categoriesById: Map<String, LedgerCategory>,
    /** 可以选的年份（有记录的年份与今年），新的在前。 */
    val years: List<Int>,
    val today: LocalDate,
    val loading: Boolean = false,
) {
    fun shares(type: TxType) = if (type == TxType.INCOME) incomeShares else expenseShares

    companion object {
        fun placeholder(mode: StatsMode, month: YearMonth, today: LocalDate) = StatsUi(
            mode, month.year, month, Summary.ZERO, Summary.ZERO, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(),
            listOf(today.year), today, loading = true,
        )
    }
}

const val TREND_MONTHS = 6

/** 每天的收支（只统计 [from, to) 之间的流水）。 */
private fun dailySums(transactions: List<LedgerTransaction>, zone: ZoneId): Map<LocalDate, Summary> {
    val expense = HashMap<LocalDate, Long>()
    val income = HashMap<LocalDate, Long>()
    for (tx in transactions) {
        val d = LedgerMath.dateOf(tx.occurredAt, zone)
        when (tx.type) {
            TxType.EXPENSE -> expense[d] = (expense[d] ?: 0L) + tx.amount
            TxType.INCOME -> income[d] = (income[d] ?: 0L) + tx.amount
            TxType.TRANSFER -> Unit
        }
    }
    return (expense.keys + income.keys).associateWith { Summary(expense[it] ?: 0L, income[it] ?: 0L) }
}

private fun heatOf(month: YearMonth, sums: Map<LocalDate, Summary>) =
    HeatMonth(month, (1..month.lengthOfMonth()).map { sums[month.atDay(it)] ?: Summary.ZERO })

/** 统计页需要的一切，纯函数，后台线程算。 */
fun buildStatsUi(
    data: LedgerData,
    mode: StatsMode,
    month: YearMonth,
    year: Int,
    zone: ZoneId,
    today: LocalDate = LocalDate.now(zone),
): StatsUi {
    val years = (data.transactions.map { LedgerMath.monthOf(it.occurredAt, zone).year } + today.year).distinct().sortedDescending()
    val byId = data.categories.associateBy { it.id }
    return when (mode) {
        StatsMode.MONTH -> {
            val inMonth = LedgerMath.inMonth(data.transactions, month, zone)
            val trend = LedgerMath.trend(data.transactions, month, TREND_MONTHS, zone)
            StatsUi(
                mode = mode,
                year = month.year,
                month = month,
                summary = LedgerMath.summary(inMonth),
                previous = trend.getOrNull(trend.size - 2)?.summary ?: Summary.ZERO,
                bars = trend.map { BarPoint("${it.month.monthValue}月", it.summary, it.month == month) },
                heat = listOf(heatOf(month, dailySums(inMonth, zone))),
                expenseShares = LedgerMath.categoryBreakdown(inMonth, data.categories, TxType.EXPENSE),
                incomeShares = LedgerMath.categoryBreakdown(inMonth, data.categories, TxType.INCOME),
                categoriesById = byId,
                years = years,
                today = today,
            )
        }
        StatsMode.YEAR -> {
            val months = (1..12).map { YearMonth.of(year, it) }
            val perMonth = months.map { LedgerMath.inMonth(data.transactions, it, zone) }
            val all = perMonth.flatten()
            val sums = dailySums(all, zone)
            val last = months.last()
            val lastYear = LedgerMath.trend(data.transactions, YearMonth.of(year - 1, 12), 12, zone)
            StatsUi(
                mode = mode,
                year = year,
                month = month,
                summary = LedgerMath.summary(all),
                previous = Summary(lastYear.sumOf { it.summary.expense }, lastYear.sumOf { it.summary.income }),
                bars = months.mapIndexed { i, m -> BarPoint("${m.monthValue}", LedgerMath.summary(perMonth[i]), m == YearMonth.from(today)) },
                heat = months.map { heatOf(it, sums) },
                expenseShares = LedgerMath.categoryBreakdown(all, data.categories, TxType.EXPENSE),
                incomeShares = LedgerMath.categoryBreakdown(all, data.categories, TxType.INCOME),
                categoriesById = byId,
                years = years,
                today = today,
            ).also { check(last.year == year) }
        }
    }
}

class LedgerStatsViewModel(private val container: AppContainer) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    val mode = MutableStateFlow(StatsMode.MONTH)
    val month = MutableStateFlow(YearMonth.now(zone))
    val year = MutableStateFlow(YearMonth.now(zone).year)
    private var current = YearMonth.now(zone)

    val ui: StateFlow<StatsUi> = combine(container.ledger.data, mode, month, year) { data, md, m, y ->
        buildStatsUi(data, md, m, y, zone)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsUi.placeholder(mode.value, month.value, LocalDate.now(zone)))

    /** 回到前台时：跨了月且正在看本月的，跟到新的本月。 */
    fun onClockTick(now: YearMonth = YearMonth.now(zone)) {
        month.value = LedgerClock.follow(month.value, current, now)
        if (year.value == current.year && now.year != current.year) year.value = now.year
        current = now
    }
}

/** 底栏“统计”分区。 */
@Composable
fun LedgerStatsScreen() {
    val container = LocalContainer.current
    val viewModel = jkViewModel { LedgerStatsViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onClockTick() }
    LedgerStatsContent(
        ui = ui,
        onMode = { viewModel.mode.value = it },
        onMonth = { viewModel.month.value = it },
        onYear = { viewModel.year.value = it },
    )
}

/**
 * 统计（无状态，1.1）：顶部“月统计 / 年统计”与年份胶囊（或月份切换）→ 薄荷色汇总横幅（支出 / 收入 / 结余）
 * → “收支统计”柱状图（圆头柱子、柱顶标金额）→ “收支热力”点阵（每天一个点，越深花得越多）→ 分类构成环形图与明细。
 */
@Composable
fun LedgerStatsContent(
    ui: StatsUi,
    onMode: (StatsMode) -> Unit,
    onMonth: (YearMonth) -> Unit,
    onYear: (Int) -> Unit,
    initialMeasure: Measure = Measure.EXPENSE,
) {
    val colors = Jk.colors
    var barMeasure by rememberSaveable { mutableStateOf(initialMeasure) }
    var heatMeasure by rememberSaveable { mutableStateOf(initialMeasure) }
    var shareType by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(Modifier.background(colors.surface)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.x2, end = Space.x2, top = Space.x1_5, bottom = Space.x05),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (mode in StatsMode.entries) {
                    ModeTab(mode.label, ui.mode == mode) { onMode(mode) }
                    Spacer(Modifier.width(Space.x05))
                }
            }
            if (ui.mode == StatsMode.YEAR) {
                YearChips(ui.years, ui.year, ui.today.year, onYear)
            } else {
                MonthSwitcher(ui.month, onMonth, Modifier.padding(start = Space.x05, bottom = Space.x05))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x2)
                .padding(top = Space.x2, bottom = LocalTabBarInset.current + Space.x2),
        ) {
            SummaryBanner(ui)
            Spacer(Modifier.height(Space.x2))
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2)) {
                CardTitle(if (ui.mode == StatsMode.YEAR) "收支统计" else "近 $TREND_MONTHS 个月") {
                    MeasureSegment(barMeasure) { barMeasure = it }
                }
                Spacer(Modifier.height(Space.x2))
                ValueBars(ui.bars, barMeasure)
            }
            Spacer(Modifier.height(Space.x2))
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2)) {
                CardTitle("收支热力") { MeasureSegment(heatMeasure) { heatMeasure = it } }
                Spacer(Modifier.height(Space.x1_5))
                HeatDots(ui.heat, heatMeasure, ui.today, large = ui.mode == StatsMode.MONTH)
            }
            Spacer(Modifier.height(Space.x2))
            ShareCard(ui, shareType) { shareType = it }
        }
    }
}

@Composable
private fun ModeTab(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = Jk.colors
    Box(
        Modifier
            .heightIn(min = JkSize.touch)
            .clip(Radius.control)
            .background(if (active) colors.primary else Color.Transparent)
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Space.x2, vertical = Space.x1),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = JkType.title.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), color = if (active) colors.onPrimary else colors.textSecondary)
    }
}

@Composable
private fun YearChips(years: List<Int>, selected: Int, thisYear: Int, onYear: (Int) -> Unit) {
    val colors = Jk.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.x1_5),
        horizontalArrangement = Arrangement.spacedBy(Space.x05),
        modifier = Modifier.padding(bottom = Space.x05),
    ) {
        items(years, key = { it }) { y ->
            val active = y == selected
            val label = when (y) {
                thisYear -> "今年"
                thisYear - 1 -> "去年"
                else -> "${y}年"
            }
            Column(
                Modifier
                    .heightIn(min = JkSize.touch)
                    .clip(Radius.pill)
                    .selectable(selected = active, role = Role.Tab, onClick = { onYear(y) })
                    .padding(horizontal = Space.x1_5, vertical = Space.x05),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = JkType.heading.copy(fontFamily = JkFonts.numeral, fontWeight = FontWeight.Bold),
                    color = if (active) colors.accent else colors.textTertiary,
                )
                // 选中年份下面一枚小三角
                Canvas(Modifier.size(10.dp, 6.dp)) {
                    if (active) {
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width / 2, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
                        }
                        drawPath(p, colors.primary)
                    }
                }
            }
        }
    }
}

/** 薄荷色汇总横幅。 */
@Composable
private fun SummaryBanner(ui: StatsUi) {
    val colors = Jk.colors
    val prefix = if (ui.mode == StatsMode.YEAR) "年" else "月"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(colors.primary)
            .padding(vertical = Space.x2_5, horizontal = Space.x1),
    ) {
        for ((label, value) in listOf("${prefix}支出" to ui.summary.expense, "${prefix}收入" to ui.summary.income, "${prefix}结余" to ui.summary.balance)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.onPrimary)
                Spacer(Modifier.height(4.dp))
                // “万”是中文，用系统字体会比数字大一号：拆出来单独用小字号排在数字后面
                val wan = abs(value) >= 1_000_000L
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingAmount(value, JkType.amount.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), colors.onPrimary, format = { bannerAmount(it).removeSuffix("万") })
                    if (wan) Text("万", style = JkType.small.copy(fontWeight = FontWeight.Bold), color = colors.onPrimary, modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
                }
            }
        }
    }
    val change = LedgerMath.change(ui.summary.expense, ui.previous.expense)
    Text(
        text = when {
            change == null -> if (ui.mode == StatsMode.YEAR) "去年无记录" else "上月无记录"
            (abs(change) * 100).roundToInt() == 0 -> "支出与" + (if (ui.mode == StatsMode.YEAR) "去年" else "上月") + "持平"
            else -> "支出比" + (if (ui.mode == StatsMode.YEAR) "去年" else "上月") + (if (change > 0) "多 " else "少 ") + "${(abs(change) * 100).roundToInt()}%"
        },
        style = JkType.caption,
        color = colors.textTertiary,
        modifier = Modifier.padding(start = Space.x05, top = Space.x1),
    )
}

/** 横幅里的金额：一万以上写成“16.14万”，否则两位小数。 */
fun bannerAmount(cents: Long): String {
    val yuan = abs(cents) / 100.0
    val sign = if (cents < 0) "-" else ""
    return if (yuan >= 10_000) sign + "%.2f万".format(yuan / 10_000) else Money.format(cents, symbol = false)
}

@Composable
private fun CardTitle(title: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = JkType.section, color = Jk.colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 1)
        trailing()
    }
}

/** 支出 / 收入 / 结余：小号分段，选中段薄荷实底。 */
@Composable
fun MeasureSegment(selected: Measure, options: List<Measure> = Measure.entries, onSelect: (Measure) -> Unit) {
    val colors = Jk.colors
    Row(
        Modifier
            .clip(Radius.control)
            .background(colors.accentSoft.copy(alpha = if (colors.isDark) 0.7f else 0.6f))
            .padding(2.dp),
    ) {
        for (m in options) {
            val active = m == selected
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(Radius.control)
                    .background(if (active) colors.primary else Color.Transparent)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(m) })
                    .padding(horizontal = Space.x1_5),
                contentAlignment = Alignment.Center,
            ) {
                Text(m.label, style = JkType.small.copy(fontWeight = FontWeight.Bold), color = if (active) colors.onPrimary else colors.accent, maxLines = 1)
            }
        }
    }
}

private fun measureColor(measure: Measure, value: Long, expense: Color, income: Color): Color = when (measure) {
    Measure.EXPENSE -> expense
    Measure.INCOME -> income
    Measure.BALANCE -> if (value < 0) expense else income
}

/** 柱状图：圆头柱子，柱顶写金额（简写），下面是月份；结余为负时柱子朝下。 */
@Composable
fun ValueBars(bars: List<BarPoint>, measure: Measure, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val measurer = rememberTextMeasurer()
    val values = bars.map { it.summary.of(measure) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }
    val labelStyle = JkType.amountCaption.copy(fontSize = 9.5.sp, color = colors.textSecondary)
    val axisStyle = JkType.amountCaption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
    val description = bars.indices.joinToString("；") { "${bars[it].label}${if (bars[it].label.endsWith("月")) "" else "月"} ${measure.label} ${Money.format(values[it])}" }
    val expense = colors.expense
    val income = colors.income
    val baseline = colors.border
    Canvas(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = description },
    ) {
        val n = bars.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = (slot * 0.46f).coerceAtMost(18.dp.toPx())
        val axisH = 22.dp.toPx()
        val labelH = 16.dp.toPx()
        val hasNeg = values.any { it < 0 }
        val maxPos = values.maxOrNull()?.coerceAtLeast(0L) ?: 0L
        val maxNeg = -(values.minOrNull()?.coerceAtMost(0L) ?: 0L)
        val chartTop = labelH
        val chartBottom = size.height - axisH
        val span = (maxPos + maxNeg).coerceAtLeast(1L).toFloat()
        val zeroY = if (hasNeg) chartTop + (chartBottom - chartTop) * (maxPos / span) else chartBottom
        drawLine(baseline, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            val h = (chartBottom - chartTop) * (abs(v) / span) * grow.value
            val color = measureColor(measure, v, expense, income)
            if (v != 0L) {
                val minH = h.coerceAtLeast(3.dp.toPx())
                val top = if (v > 0) zeroY - minH else zeroY
                drawRoundRect(color.copy(alpha = if (bars[i].current || bars.none { it.current }) 1f else 0.8f), Offset(cx - barW / 2, top), Size(barW, minH), CornerRadius(barW / 2))
                val text = Money.compact(abs(v)).let { if (v < 0) "-$it" else it }
                val layout = measurer.measure(text, labelStyle)
                val ty = if (v > 0) top - layout.size.height - 2.dp.toPx() else top + minH + 2.dp.toPx()
                drawText(layout, topLeft = Offset((cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), ty.coerceAtLeast(0f)))
            }
            val axis = measurer.measure(bars[i].label, axisStyle.copy(color = if (bars[i].current) colors.accent else colors.textSecondary))
            drawText(axis, topLeft = Offset(cx - axis.size.width / 2f, size.height - axis.size.height))
        }
    }
}

/**
 * 收支热力：每天一个圆点，按周排成 7 列；花得越多（或赚得越多）点越深，没有记录是浅灰，将来的日子更淡。
 * 年统计是 12 个小月份（4 列），月统计是一个大月份。
 */
@Composable
fun HeatDots(months: List<HeatMonth>, measure: Measure, today: LocalDate, large: Boolean, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val max = months.flatMap { m -> m.days.map { abs(it.of(measure)) } }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val columns = if (large) 1 else 4
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        for (row in months.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                for (m in row) {
                    Column(Modifier.weight(1f)) {
                        if (!large) {
                            Text("${m.month.monthValue}月", style = JkType.amountSmall.copy(fontWeight = FontWeight.Bold), color = colors.textSecondary)
                            Spacer(Modifier.height(Space.x05))
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                for (w in listOf("日", "一", "二", "三", "四", "五", "六")) {
                                    Text(w, style = JkType.caption, color = colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(Space.x05))
                        }
                        MonthDots(m, measure, max, today, large)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("少", style = JkType.caption, color = colors.textTertiary)
            for (level in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(9.dp).clip(CircleShape).background(heatColor(level, measure, 1L)))
            }
            Spacer(Modifier.width(4.dp))
            Text("多", style = JkType.caption, color = colors.textTertiary)
        }
    }
}

@Composable
private fun heatColor(level: Float, measure: Measure, value: Long): Color {
    val colors = Jk.colors
    if (level <= 0f) return colors.surfaceAlt
    val base = measureColor(measure, value, colors.expense, colors.income)
    return base.copy(alpha = 0.22f + 0.78f * level)
}

@Composable
private fun MonthDots(m: HeatMonth, measure: Measure, max: Long, today: LocalDate, large: Boolean) {
    val colors = Jk.colors
    val lead = m.month.atDay(1).dayOfWeek.value % 7
    val cells = lead + m.days.size
    val rows = (cells + 6) / 7
    val gap = if (large) 6.dp else 2.dp
    val desc = "${m.month.monthValue}月${measure.label}热力，" + m.days.withIndex().filter { it.value.of(measure) != 0L }
        .joinToString("；") { "${it.index + 1}日 ${Money.format(it.value.of(measure))}" }.ifEmpty { "没有记录" }
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc }, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until 7) {
                    val i = r * 7 + c - lead
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (i in m.days.indices) {
                            val v = m.days[i].of(measure)
                            val level = if (v == 0L) 0f else (abs(v).toFloat() / max).coerceIn(0.08f, 1f)
                            val future = m.month.atDay(i + 1) > today
                            val tone = heatColor(level, measure, v).let { if (future) it.copy(alpha = it.alpha * 0.45f) else it }
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(tone))
                            if (large) {
                                Text(
                                    "${i + 1}",
                                    style = JkType.amountCaption.copy(fontSize = 11.sp),
                                    color = if (level > 0.55f) Color.White else colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分类构成：环形图 + 明细，支出 / 收入可切换。 */
@Composable
private fun ShareCard(ui: StatsUi, type: TxType, onType: (TxType) -> Unit) {
    val colors = Jk.colors
    val shares = ui.shares(type)
    val total = shares.sumOf { it.amount }
    JkCard(Modifier.fillMaxWidth().animateContentSize(tween(240)), padding = PaddingValues(Space.x2)) {
        CardTitle("分类构成") {
            MeasureSegment(
                selected = if (type == TxType.INCOME) Measure.INCOME else Measure.EXPENSE,
                options = listOf(Measure.EXPENSE, Measure.INCOME),
            ) { onType(if (it == Measure.INCOME) TxType.INCOME else TxType.EXPENSE) }
        }
        Spacer(Modifier.height(Space.x2))
        if (shares.isEmpty() && !ui.loading) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LedgerIllustration(LedgerArt.NO_STATS)
                Spacer(Modifier.height(Space.x1_5))
                Text(
                    (if (ui.mode == StatsMode.YEAR) "这一年" else "这个月") + "还没有${type.label}记录",
                    style = JkType.small,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(Space.x1))
            }
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Donut(
                    shares.map { it.amount },
                    Modifier.size(180.dp),
                    description = "${type.label}构成：" + shares.joinToString("；") { share ->
                        "${categoryLabel(share.categoryId.ifEmpty { null }, ui.categoriesById)} ${(share.ratio * 100).roundToInt()}%"
                    },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("合计", style = JkType.caption, color = colors.textTertiary)
                    RollingAmount(total, JkType.amount.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), colors.textPrimary)
                }
            }
            Spacer(Modifier.height(Space.x2))
            shares.forEachIndexed { index, share ->
                ShareRow(index, share, ui.categoriesById)
            }
        }
    }
}

@Composable
private fun ShareRow(index: Int, share: CategoryShare, byId: Map<String, LedgerCategory>) {
    val colors = Jk.colors
    val category = byId[share.categoryId]
    val tone = chartColorOf(index)
    Row(Modifier.padding(vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = if (colors.isDark) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(categoryIcon(category?.icon), contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category?.name ?: "未分类",
                    style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 名称最多占 120dp，金额始终贴右对齐
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                Spacer(Modifier.width(Space.x1))
                Text("${share.count} 笔", style = JkType.amountCaption, color = colors.textTertiary)
                Spacer(Modifier.weight(1f))
                Text("${(share.ratio * 1000).roundToInt() / 10.0}%", style = JkType.amountSmall, color = colors.textSecondary)
                Spacer(Modifier.width(Space.x1_5))
                Text(Money.format(share.amount), style = JkType.amount, color = colors.textPrimary)
            }
            Spacer(Modifier.height(6.dp))
            ShareBar(share.ratio, tone)
        }
    }
}

@Composable
private fun ShareBar(ratio: Float, tone: Color) {
    val track = Jk.colors.surfaceAlt
    val shown by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(520, easing = FastOutSlowInEasing), label = "share")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f))
        drawRoundRect(tone, size = size.copy(width = size.width * shown), cornerRadius = CornerRadius(size.height / 2f))
    }
}
