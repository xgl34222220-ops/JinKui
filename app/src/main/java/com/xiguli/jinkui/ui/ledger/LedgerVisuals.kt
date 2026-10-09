package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.nav.HomeTab
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// ── 首页分区：卡包 / 账本 ─────────────────────────────────────────

/**
 * 首页外壳：上面是当前分区，下面是分区切换栏。
 * 分区内容不再自己处理导航栏留白（由底部切换栏统一处理），卡包页面本身一行未改。
 */
@Composable
fun HomeTabs(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    content: @Composable (HomeTab) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            content(selected)
        }
        HomeTabBar(selected, onSelect)
    }
}

@Composable
private fun HomeTabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bg),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
        ) {
            for (tab in HomeTab.entries) {
                val active = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (tab == HomeTab.CARDS) Icons.Outlined.Style else Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = if (active) colors.accent else colors.textTertiary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        style = JkType.caption,
                        color = if (active) colors.textPrimary else colors.textTertiary,
                    )
                }
            }
        }
    }
}

// ── 图标与配色 ────────────────────────────────────────────────────

/** 分类图标键 → 图标。键写在加密数据里，新增可以，已有的不要改。 */
val CategoryIcons: List<Pair<String, ImageVector>> = listOf(
    "restaurant" to Icons.Outlined.Restaurant,
    "breakfast" to Icons.Outlined.FreeBreakfast,
    "lunch" to Icons.Outlined.Fastfood,
    "dinner" to Icons.Outlined.Restaurant,
    "cafe" to Icons.Outlined.LocalCafe,
    "snack" to Icons.Outlined.Fastfood,
    "bus" to Icons.Outlined.DirectionsBus,
    "subway" to Icons.Outlined.Subway,
    "taxi" to Icons.Outlined.LocalTaxi,
    "gas" to Icons.Outlined.LocalGasStation,
    "parking" to Icons.Outlined.LocalParking,
    "train" to Icons.Outlined.Train,
    "shopping" to Icons.Outlined.ShoppingBag,
    "cart" to Icons.Outlined.ShoppingCart,
    "clothes" to Icons.Outlined.Checkroom,
    "devices" to Icons.Outlined.Devices,
    "chair" to Icons.Outlined.Chair,
    "home" to Icons.Outlined.Home,
    "apartment" to Icons.Outlined.Apartment,
    "bolt" to Icons.Outlined.Bolt,
    "wifi" to Icons.Outlined.Wifi,
    "movie" to Icons.Outlined.Movie,
    "game" to Icons.Outlined.SportsEsports,
    "luggage" to Icons.Outlined.Luggage,
    "fitness" to Icons.Outlined.FitnessCenter,
    "hospital" to Icons.Outlined.LocalHospital,
    "medication" to Icons.Outlined.Medication,
    "school" to Icons.Outlined.School,
    "book" to Icons.Outlined.AutoStories,
    "gift" to Icons.Outlined.CardGiftcard,
    "redeem" to Icons.Outlined.Redeem,
    "people" to Icons.Outlined.People,
    "child" to Icons.Outlined.ChildCare,
    "pets" to Icons.Outlined.Pets,
    "payments" to Icons.Outlined.Payments,
    "trophy" to Icons.Outlined.EmojiEvents,
    "chart" to Icons.AutoMirrored.Outlined.ShowChart,
    "work" to Icons.Outlined.Work,
    "refund" to Icons.Outlined.Restore,
    "more" to Icons.Outlined.MoreHoriz,
)

private val iconMap = CategoryIcons.toMap()

fun categoryIcon(key: String?): ImageVector = iconMap[key] ?: Icons.Outlined.MoreHoriz

val TransferIcon: ImageVector get() = Icons.Outlined.SyncAlt

fun accountIcon(kind: AccountKind): ImageVector = when (kind) {
    AccountKind.CASH -> Icons.Outlined.Payments
    AccountKind.BANK -> Icons.Outlined.AccountBalance
    AccountKind.ALIPAY -> Icons.Outlined.QrCode
    AccountKind.WECHAT -> Icons.Outlined.Forum
    AccountKind.CREDIT -> Icons.Outlined.CreditCard
    AccountKind.OTHER -> Icons.Outlined.AccountBalanceWallet
}

/** 图表用的一组低饱和色，与卡面配色同一气质。 */
private val ChartPalette = listOf(
    Color(0xFFB8863B), // 赤金
    Color(0xFF3B5B8C), // 靛蓝
    Color(0xFF2F6B57), // 松绿
    Color(0xFFA8483A), // 朱砂
    Color(0xFF6B4E8C), // 黛紫
    Color(0xFF2E7A86), // 石青
    Color(0xFF8C6E4E), // 赭石
    Color(0xFF5E7D3A), // 苔绿
    Color(0xFF8C4E6B), // 绛紫
    Color(0xFF6E6A62), // 岩灰
)

fun chartColor(index: Int): Color = ChartPalette[index.mod(ChartPalette.size)]

/** 支出、收入、转账在列表里的金额颜色。 */
@Composable
fun amountColor(type: TxType): Color = when (type) {
    TxType.EXPENSE -> Jk.colors.textPrimary
    TxType.INCOME -> Jk.colors.success
    TxType.TRANSFER -> Jk.colors.textSecondary
}

/** 子分类显示为“餐饮 · 午餐”。 */
fun categoryLabel(id: String?, byId: Map<String, LedgerCategory>): String {
    if (id == null) return "未分类"
    val category = byId[id] ?: return "未分类"
    val parent = category.parentId?.let(byId::get)
    return if (parent != null) "${parent.name} · ${category.name}" else category.name
}

// ── 日期 ──────────────────────────────────────────────────────────

private val weekdays = mapOf(
    DayOfWeek.MONDAY to "周一",
    DayOfWeek.TUESDAY to "周二",
    DayOfWeek.WEDNESDAY to "周三",
    DayOfWeek.THURSDAY to "周四",
    DayOfWeek.FRIDAY to "周五",
    DayOfWeek.SATURDAY to "周六",
    DayOfWeek.SUNDAY to "周日",
)

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val base = "${date.monthValue}月${date.dayOfMonth}日 ${weekdays.getValue(date.dayOfWeek)}"
    return when (date) {
        today -> "今天 · $base"
        today.minusDays(1) -> "昨天 · $base"
        else -> base
    }
}

fun monthLabel(month: YearMonth): String = "${month.year}年${month.monthValue}月"

// ── 通用小部件 ────────────────────────────────────────────────────

/** 左右切换月份；不能翻到未来。 */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    val current = YearMonth.now()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onChange(month.minusMonths(1)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月", tint = colors.textSecondary)
        }
        Text(
            text = monthLabel(month),
            style = JkType.heading,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(120.dp)
                .clickable(enabled = month != current) { onChange(current) },
        )
        val canForward = month < current
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(enabled = canForward) { onChange(month.plusMonths(1)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "下个月",
                tint = if (canForward) colors.textSecondary else colors.border,
            )
        }
    }
}

/** 圆底图标。 */
@Composable
fun RoundIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** 细进度条；超出时整条变成警示色。 */
@Composable
fun BudgetBar(ratio: Float, over: Boolean, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val fill = when {
        over -> colors.danger
        ratio >= 0.8f -> colors.accent
        else -> colors.success
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.surfaceAlt),
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(fill),
        )
    }
}

/** 环形占比图。[values] 为各段金额；全为 0 时画一圈底色。 */
@Composable
fun Donut(
    values: List<Long>,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
) {
    val track = Jk.colors.surfaceAlt
    val bg = Jk.colors.surface
    Canvas(modifier) {
        val stroke = thickness.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val total = values.sum().toFloat()
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (total <= 0f) return@Canvas
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value / total * 360f
            if (sweep <= 0f) return@forEachIndexed
            drawArc(chartColor(index), start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            // 段与段之间留一道细缝
            if (values.count { it > 0 } > 1) {
                drawArc(bg, start + sweep - 0.6f, 1.2f, false, topLeft, arcSize, style = Stroke(stroke + 2f))
            }
            start += sweep
        }
    }
}

/** 月度收支柱状图：每月两根柱子（支出 / 收入），最后一根是当前选中的月份。 */
@Composable
fun TrendBars(
    expense: List<Long>,
    income: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    val expenseColor = colors.accent
    val incomeColor = colors.success
    val gridColor = colors.border
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val max = (expense + income).maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 1f
            val n = labels.size.coerceAtLeast(1)
            val slot = size.width / n
            val bar = (slot * 0.26f).coerceAtMost(14.dp.toPx())
            val gap = 3.dp.toPx()
            val bottom = size.height
            for (line in 1..3) {
                val y = bottom - bottom * line / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            drawLine(gridColor, Offset(0f, bottom - 0.5f), Offset(size.width, bottom - 0.5f), strokeWidth = 1f)
            for (i in 0 until n) {
                val center = slot * i + slot / 2f
                val e = expense.getOrElse(i) { 0L } / max * (bottom - 4f)
                val inc = income.getOrElse(i) { 0L } / max * (bottom - 4f)
                val alpha = if (i == n - 1) 1f else 0.55f
                if (e > 0f) {
                    drawRoundRect(
                        color = expenseColor.copy(alpha = alpha),
                        topLeft = Offset(center - gap / 2f - bar, bottom - e),
                        size = Size(bar, e),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                }
                if (inc > 0f) {
                    drawRoundRect(
                        color = incomeColor.copy(alpha = alpha),
                        topLeft = Offset(center + gap / 2f, bottom - inc),
                        size = Size(bar, inc),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.x05))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = JkType.caption,
                    color = if (index == labels.lastIndex) colors.textPrimary else colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Space.x1))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(expenseColor)
            Text("支出", style = JkType.caption, color = colors.textSecondary, modifier = Modifier.padding(start = Space.x05, end = Space.x2))
            LegendDot(incomeColor)
            Text("收入", style = JkType.caption, color = colors.textSecondary, modifier = Modifier.padding(start = Space.x05))
        }
    }
}

@Composable
fun LegendDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
