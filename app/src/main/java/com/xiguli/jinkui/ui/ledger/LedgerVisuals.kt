package com.xiguli.jinkui.ui.ledger

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xiguli.jinkui.data.ledger.Money
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.Radius
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
 * 首页外壳：上面是当前分区，下面是底部栏——左“卡包”、右“账本”，中间一个品牌色的圆形“＋”。
 * “＋”跟着当前分区走：在卡包里新建卡片，在账本里直接打开“记一笔”（参考钱迹首页中央的“＋”）。
 * 主操作因此固定在拇指够得着的正下方，不再有浮在列表上、挡住复制按钮和金额的悬浮按钮。
 * 两个分区之间短暂淡入淡出（只有 180ms，离开的分区随即销毁）。
 */
@Composable
fun HomeTabs(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    onAdd: (HomeTab) -> Unit = {},
    content: @Composable (HomeTab) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Crossfade(
            targetState = selected,
            animationSpec = tween(180),
            label = "homeTab",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) { tab ->
            Box(Modifier.fillMaxSize()) { content(tab) }
        }
        HomeTabBar(selected, onSelect, onAdd)
    }
}

@Composable
fun HomeTabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit, onAdd: (HomeTab) -> Unit) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(HomeTab.CARDS, selected == HomeTab.CARDS, onSelect, Modifier.weight(1f))
            AddButton(
                description = if (selected == HomeTab.LEDGER) "记一笔" else "新建卡片",
                onClick = { onAdd(selected) },
            )
            TabItem(HomeTab.LEDGER, selected == HomeTab.LEDGER, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabItem(tab: HomeTab, active: Boolean, onSelect: (HomeTab) -> Unit, modifier: Modifier) {
    val colors = Jk.colors
    val haptic = LocalHapticFeedback.current
    val pill by animateColorAsState(if (active) colors.accentSoft else Color.Transparent, tween(200), label = "tabPill")
    val tint by animateColorAsState(if (active) colors.accent else colors.textTertiary, tween(200), label = "tabTint")
    val pillWidth by animateDpAsState(if (active) 60.dp else 36.dp, spring(dampingRatio = 0.8f, stiffness = 600f), label = "tabPillWidth")
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = active,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!active) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(tab)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(pillWidth)
                .height(30.dp)
                .clip(CircleShape)
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (tab) {
                    HomeTab.CARDS -> if (active) Icons.Filled.Style else Icons.Outlined.Style
                    HomeTab.LEDGER -> if (active) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(JkSize.icon),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = if (active) JkType.caption.copy(fontWeight = FontWeight.SemiBold) else JkType.caption,
            color = if (active) colors.textPrimary else colors.textTertiary,
            maxLines = 1,
        )
    }
}

/** 中间的“＋”：56dp 品牌色圆钮，按下缩小并有触感。 */
@Composable
private fun AddButton(description: String, onClick: () -> Unit) {
    val colors = Jk.colors
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.6f, stiffness = 800f), label = "addScale")
    Box(
        modifier = Modifier
            .padding(horizontal = Space.x1)
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(28.dp))
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

/** 图表用的一组低饱和色，与卡面配色同一气质；深色模式下整体提亮一档，保证在墨黑底上分得清。 */
private val ChartPalette = listOf(
    Color(0xFF3E4FC1), // 靛青（品牌色，占比最大的一项）
    Color(0xFFB8863B), // 琥珀
    Color(0xFF2F6B57), // 松绿
    Color(0xFFA8483A), // 朱砂
    Color(0xFF6B4E8C), // 黛紫
    Color(0xFF2E7A86), // 石青
    Color(0xFF8C6E4E), // 赭石
    Color(0xFF5E7D3A), // 苔绿
    Color(0xFF8C4E6B), // 绛紫
    Color(0xFF6E6A62), // 岩灰
)

private val ChartPaletteDark = listOf(
    Color(0xFF9AA6FF),
    Color(0xFFD9AE62),
    Color(0xFF6FBF9F),
    Color(0xFFE58C7C),
    Color(0xFFAE93D3),
    Color(0xFF6CC0CB),
    Color(0xFFC9A887),
    Color(0xFFA2C274),
    Color(0xFFD08EAE),
    Color(0xFFADA79D),
)

fun chartColor(index: Int, dark: Boolean = false): Color {
    val palette = if (dark) ChartPaletteDark else ChartPalette
    return palette[index.mod(palette.size)]
}

/** 当前主题下的图表色。 */
@Composable
fun chartColorOf(index: Int): Color = chartColor(index, Jk.colors.isDark)

/** 支出、收入、转账的主色（图标、图例、柱子）。 */
@Composable
fun txColor(type: TxType): Color = when (type) {
    TxType.EXPENSE -> Jk.colors.expense
    TxType.INCOME -> Jk.colors.income
    TxType.TRANSFER -> Jk.colors.transfer
}

/** 支出、收入、转账的浅底（图标底）。 */
@Composable
fun txSoft(type: TxType): Color = when (type) {
    TxType.EXPENSE -> Jk.colors.expenseSoft
    TxType.INCOME -> Jk.colors.incomeSoft
    TxType.TRANSFER -> Jk.colors.transferSoft
}

/** 列表里的金额颜色：支出用正文色（克制），收入用收入色，转账用转账色。 */
@Composable
fun amountColor(type: TxType): Color = when (type) {
    TxType.EXPENSE -> Jk.colors.textPrimary
    TxType.INCOME -> Jk.colors.income
    TxType.TRANSFER -> Jk.colors.transfer
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

/** 分组头的主标签：“今天”“昨天”或“10月9日”（不是今年的带上年份）。 */
fun shortDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> if (date.year == today.year) "${date.monthValue}月${date.dayOfMonth}日" else "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}

fun weekdayLabel(date: LocalDate): String = weekdays.getValue(date.dayOfWeek)

fun monthLabel(month: YearMonth): String = "${month.year}年${month.monthValue}月"

// ── 通用小部件 ────────────────────────────────────────────────────

/**
 * 左右切换月份。默认不能翻到未来；[latest] 晚于本月时（例如导入了日期在将来的记录），
 * 可以一直翻到 [latest]，不会有记录被藏起来看不到。
 */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    latest: YearMonth? = null,
) {
    val colors = Jk.colors
    val current = YearMonth.now()
    val limit = if (latest != null && latest > current) latest else current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // 48dp 触控区，视觉上仍是 36dp 的圆
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "上个月") { onChange(month.minusMonths(1)) },
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
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .clickable(enabled = month != current, role = Role.Button, onClickLabel = "回到本月") { onChange(current) },
        )
        val canForward = month < limit
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = canForward, role = Role.Button, onClickLabel = "下个月") { onChange(month.plusMonths(1)) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "下个月",
                tint = if (canForward) colors.textSecondary else colors.textTertiary.copy(alpha = 0.4f),
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

/** 流水行左侧的分类图标底：圆角方块，底色与图标色按支出 / 收入 / 转账区分。 */
@Composable
fun CategoryChip(icon: ImageVector, type: TxType, modifier: Modifier = Modifier, size: Dp = JkSize.chip) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Radius.tile)
            .background(txSoft(type)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = txColor(type), modifier = Modifier.size(size * 0.5f))
    }
}

/** 细进度条；超出时整条变成警示色。进度变化时平滑过渡。 */
@Composable
fun BudgetBar(ratio: Float, over: Boolean, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val fill by animateColorAsState(
        when {
            over -> colors.danger
            ratio >= 0.8f -> colors.expense // 快用完：账本里的支出橙作提醒
            else -> colors.accent // 正常：唯一的品牌靛青
        },
        tween(240),
        label = "budgetColor",
    )
    val target = ratio.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, tween(520, easing = FastOutSlowInEasing), label = "budgetRatio")
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.surfaceAlt)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(6.dp)
                .clip(CircleShape)
                .background(fill),
        )
    }
}

/**
 * 环形占比图。[values] 为各段金额；全为 0 时画一圈底色。
 * 段与段之间留固定角度的缝（不靠底色盖住），数据变化时从 12 点方向顺时针展开。
 */
@Composable
fun Donut(
    values: List<Long>,
    modifier: Modifier = Modifier,
    thickness: Dp = 20.dp,
    description: String? = null,
) {
    val track = Jk.colors.surfaceAlt
    val dark = Jk.colors.isDark
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(640, easing = FastOutSlowInEasing))
    }
    Canvas(if (description != null) modifier.semantics { contentDescription = description } else modifier) {
        val stroke = thickness.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val total = values.sum().toFloat()
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (total <= 0f) return@Canvas
        val segments = values.count { it > 0 }
        // 缝的角度：约 2dp 的弧长
        val gap = if (segments > 1) (2.dp.toPx() / (diameter / 2f)) * (180f / Math.PI.toFloat()) else 0f
        val reveal = 360f * progress.value
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value / total * 360f
            if (sweep <= 0f) return@forEachIndexed
            val visibleEnd = minOf(start + sweep, -90f + reveal)
            val drawn = visibleEnd - start - gap
            if (drawn > 0f) {
                drawArc(chartColor(index, dark), start + gap / 2f, drawn, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            }
            start += sweep
        }
    }
}

/** 月度收支柱状图：每月两根柱子（支出 / 收入），最后一根是当前选中的月份，柱顶标出金额。 */
@Composable
fun TrendBars(
    expense: List<Long>,
    income: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    val chartDescription = labels.indices.joinToString("；") { i ->
        "${labels[i]} 支出 ${Money.format(expense.getOrElse(i) { 0L })}，收入 ${Money.format(income.getOrElse(i) { 0L })}"
    }
    val expenseColor = colors.expense
    val incomeColor = colors.income
    val gridColor = colors.border
    val grow = remember { Animatable(0f) }
    LaunchedEffect(expense, income) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .semantics { contentDescription = chartDescription },
        ) {
            val max = (expense + income).maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 1f
            val n = labels.size.coerceAtLeast(1)
            val slot = size.width / n
            val bar = (slot * 0.24f).coerceAtMost(12.dp.toPx())
            val gap = 3.dp.toPx()
            val bottom = size.height
            val top = 6.dp.toPx()
            val usable = bottom - top
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            for (line in 1..2) {
                val y = bottom - usable * line / 2f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, pathEffect = dash)
            }
            drawLine(gridColor, Offset(0f, bottom - 0.5f), Offset(size.width, bottom - 0.5f), strokeWidth = 1f)
            val corner = CornerRadius(bar / 2f)
            for (i in 0 until n) {
                val center = slot * i + slot / 2f
                val e = expense.getOrElse(i) { 0L } / max * usable * grow.value
                val inc = income.getOrElse(i) { 0L } / max * usable * grow.value
                val current = i == n - 1
                if (current) {
                    // 选中月份：一条淡淡的底带
                    drawRoundRect(
                        color = gridColor.copy(alpha = 0.45f),
                        topLeft = Offset(center - slot * 0.42f, 0f),
                        size = Size(slot * 0.84f, bottom),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                }
                val alpha = if (current) 1f else 0.5f
                if (e > 0f) {
                    drawRoundRect(expenseColor.copy(alpha = alpha), Offset(center - gap / 2f - bar, bottom - e), Size(bar, e), corner)
                }
                if (inc > 0f) {
                    drawRoundRect(incomeColor.copy(alpha = alpha), Offset(center + gap / 2f, bottom - inc), Size(bar, inc), corner)
                }
            }
        }
        Spacer(Modifier.height(Space.x05))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = if (index == labels.lastIndex) JkType.caption.copy(fontWeight = FontWeight.Medium) else JkType.caption,
                    color = if (index == labels.lastIndex) colors.textPrimary else colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Space.x1_5))
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

// ── 金额滚动 ──────────────────────────────────────────────────────

/**
 * 会“滚”的金额：数值变化时，变了的那几位上下滚动（这一位变大向上、变小向下），没变的位不动。
 * 字符按“从右往左第几位”对齐，配合等宽数字（tnum），小数点不会左右跳。
 * 读屏与测试读到的是完整的金额文字。
 */
@Composable
fun RollingAmount(
    cents: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    format: (Long) -> String = { Money.format(it) },
) {
    val text = format(cents)
    Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        val chars = text.toCharArray()
        chars.forEachIndexed { index, ch ->
            key(chars.size - index) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        // 方向只由这一位自己的新旧值决定，不依赖外部状态，重组多少次都一样
                        val up = targetState > initialState
                        val enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { h -> if (up) h else -h } + fadeIn(tween(200))
                        val exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { h -> if (up) -h else h } + fadeOut(tween(160))
                        (enter togetherWith exit).using(SizeTransform(clip = true))
                    },
                    label = "roll",
                ) { c ->
                    Text(c.toString(), style = style, color = color, maxLines = 1)
                }
            }
        }
    }
}

// ── 空状态插画（Compose 自绘，不用图片资源） ───────────────────────

enum class LedgerArt { EMPTY_MONTH, NO_MATCH, NO_STATS }

/**
 * 账本的空状态插画：只用描边、浅底与一两处品牌色，与卡包的空状态同一笔法。
 *  - [LedgerArt.EMPTY_MONTH]：一本摊开的账簿，旁边一枚铜钱
 *  - [LedgerArt.NO_MATCH]：一张小票与放大镜
 *  - [LedgerArt.NO_STATS]：一圈虚线空环与三根矮柱
 */
@Composable
fun LedgerIllustration(art: LedgerArt, modifier: Modifier = Modifier) {
    val line = Jk.colors.border
    val ink = Jk.colors.textTertiary.copy(alpha = 0.55f)
    val fill = Jk.colors.surface
    val soft = Jk.colors.surfaceAlt
    val accent = Jk.colors.accent
    val accentSoft = Jk.colors.accentSoft
    Canvas(modifier.size(width = 132.dp, height = 96.dp)) {
        val u = 1.dp.toPx()
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.4f * u)
        when (art) {
            LedgerArt.EMPTY_MONTH -> {
                // 账簿：左右两页，中缝
                val left = 18f * u
                val top = 16f * u
                val pageW = 44f * u
                val pageH = 64f * u
                val corner = CornerRadius(8f * u)
                drawRoundRect(soft, Offset(left + 4f * u, top + 4f * u), Size(pageW * 2f, pageH), corner)
                drawRoundRect(fill, Offset(left, top), Size(pageW * 2f, pageH), corner)
                drawRoundRect(line, Offset(left, top), Size(pageW * 2f, pageH), corner, style = stroke)
                drawLine(line, Offset(left + pageW, top), Offset(left + pageW, top + pageH), strokeWidth = 1.4f * u)
                for (row in 0 until 4) {
                    val y = top + 14f * u + row * 12f * u
                    drawLine(line, Offset(left + 8f * u, y), Offset(left + pageW - 8f * u, y), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
                    val end = if (row % 2 == 0) pageW - 20f * u else pageW - 12f * u
                    drawLine(line, Offset(left + pageW + 8f * u, y), Offset(left + pageW + end, y), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
                }
                // 铜钱：外圆内方
                val c = Offset(w - 22f * u, h - 22f * u)
                drawCircle(accentSoft, 15f * u, c)
                drawCircle(accent, 15f * u, c, style = Stroke(1.8f * u))
                drawRect(accent, Offset(c.x - 4.5f * u, c.y - 4.5f * u), Size(9f * u, 9f * u), style = Stroke(1.8f * u))
            }
            LedgerArt.NO_MATCH -> {
                // 小票：底边锯齿
                val left = 30f * u
                val top = 10f * u
                val rw = 52f * u
                val rh = 70f * u
                val path = Path().apply {
                    moveTo(left, top)
                    lineTo(left + rw, top)
                    lineTo(left + rw, top + rh)
                    val teeth = 6
                    val step = rw / teeth
                    for (t in teeth - 1 downTo 0) {
                        lineTo(left + step * t + step / 2f, top + rh - 5f * u)
                        lineTo(left + step * t, top + rh)
                    }
                    close()
                }
                drawPath(path, fill)
                drawPath(path, line, style = stroke)
                for (row in 0 until 3) {
                    val y = top + 16f * u + row * 12f * u
                    drawLine(line, Offset(left + 9f * u, y), Offset(left + rw - (if (row == 1) 22f else 12f) * u, y), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
                }
                // 放大镜
                val c = Offset(left + rw + 12f * u, top + rh - 18f * u)
                drawCircle(fill, 13f * u, c)
                drawCircle(accent, 13f * u, c, style = Stroke(2f * u))
                drawLine(accent, Offset(c.x + 9.5f * u, c.y + 9.5f * u), Offset(c.x + 19f * u, c.y + 19f * u), strokeWidth = 3.2f * u, cap = StrokeCap.Round)
            }
            LedgerArt.NO_STATS -> {
                val c = Offset(44f * u, h / 2f)
                drawCircle(line, 28f * u, c, style = Stroke(8f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * u, 5f * u))))
                drawCircle(accent, 3f * u, c)
                val baseY = h - 18f * u
                val heights = listOf(18f, 30f, 22f)
                heights.forEachIndexed { i, bh ->
                    val x = 86f * u + i * 13f * u
                    drawRoundRect(
                        if (i == 1) accentSoft else soft,
                        Offset(x, baseY - bh * u),
                        Size(8f * u, bh * u),
                        CornerRadius(4f * u),
                    )
                    drawRoundRect(if (i == 1) accent else ink, Offset(x, baseY - bh * u), Size(8f * u, bh * u), CornerRadius(4f * u), style = Stroke(1.2f * u))
                }
                drawLine(line, Offset(80f * u, baseY + 0.7f * u), Offset(w - 6f * u, baseY + 0.7f * u), strokeWidth = 1.4f * u, cap = StrokeCap.Round)
            }
        }
    }
}
