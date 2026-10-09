package com.xiguli.jinkui.ui.ledger

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.runtime.Immutable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.xiguli.jinkui.data.ledger.LedgerClock
import com.xiguli.jinkui.ui.theme.Radius
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.CategoryShare
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.MonthPoint
import com.xiguli.jinkui.data.ledger.Summary
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
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

@Immutable
data class StatsUi(
    val month: YearMonth,
    val type: TxType,
    val summary: Summary,
    val previous: Summary,
    val shares: List<CategoryShare>,
    val trend: List<MonthPoint>,
    val categoriesById: Map<String, LedgerCategory>,
    val loading: Boolean = false,
) {
    companion object {
        fun placeholder(month: YearMonth, type: TxType) =
            StatsUi(month, type, Summary.ZERO, Summary.ZERO, emptyList(), emptyList(), emptyMap(), loading = true)
    }
}

const val TREND_MONTHS = 6

fun buildStatsUi(data: LedgerData, month: YearMonth, type: TxType, zone: ZoneId): StatsUi {
    val inMonth = LedgerMath.inMonth(data.transactions, month, zone)
    val trend = LedgerMath.trend(data.transactions, month, TREND_MONTHS, zone)
    return StatsUi(
        month = month,
        type = type,
        summary = LedgerMath.summary(inMonth),
        previous = trend.getOrNull(trend.size - 2)?.summary ?: Summary.ZERO,
        shares = LedgerMath.categoryBreakdown(inMonth, data.categories, type),
        trend = trend,
        categoriesById = data.categories.associateBy { it.id },
    )
}

class LedgerStatsViewModel(private val container: AppContainer) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val type = MutableStateFlow(TxType.EXPENSE)
    private var current = YearMonth.now()

    // 第一次计算同样放到后台线程
    val ui: StateFlow<StatsUi> = combine(container.ledger.data, month, type) { data, m, t ->
        buildStatsUi(data, m, t, ZoneId.systemDefault())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsUi.placeholder(month.value, type.value))

    /** 回到前台时：跨了月且正在看本月的，跟到新的本月。 */
    fun onClockTick(now: YearMonth = YearMonth.now()) {
        month.value = LedgerClock.follow(month.value, current, now)
        current = now
    }
}

@Composable
fun LedgerStatsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { LedgerStatsViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onClockTick() }
    LedgerStatsContent(
        ui = ui,
        onBack = onBack,
        onMonth = { viewModel.month.value = it },
        onType = { viewModel.type.value = it },
    )
}

/** 统计页（无状态）：分类构成的环形图与明细、近 6 个月的收支柱状图。 */
@Composable
fun LedgerStatsContent(
    ui: StatsUi,
    onBack: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onType: (TxType) -> Unit,
) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar("统计", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x2_5)
                .navigationBarsPadding()
                .padding(bottom = Space.x4),
        ) {
            MonthSwitcher(ui.month, onMonth, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(Space.x1))
            JkSegmented(
                options = listOf(TxType.EXPENSE to "支出", TxType.INCOME to "收入"),
                selected = ui.type,
                onSelect = onType,
            )
            Spacer(Modifier.height(Space.x2))

            val total = if (ui.type == TxType.EXPENSE) ui.summary.expense else ui.summary.income
            val before = if (ui.type == TxType.EXPENSE) ui.previous.expense else ui.previous.income
            JkCard(Modifier.fillMaxWidth().animateContentSize(tween(240)), padding = PaddingValues(Space.x2_5)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(txColor(ui.type))
                    Spacer(Modifier.width(Space.x1))
                    Text("${ui.month.monthValue}月${ui.type.label}构成", style = JkType.heading, color = colors.textPrimary)
                }
                Spacer(Modifier.height(Space.x2))
                if (ui.shares.isEmpty() && !ui.loading) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        LedgerIllustration(LedgerArt.NO_STATS)
                        Spacer(Modifier.height(Space.x1_5))
                        Text("这个月还没有${ui.type.label}记录", style = JkType.small, color = colors.textTertiary)
                        Spacer(Modifier.height(Space.x1))
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(
                            ui.shares.map { it.amount },
                            Modifier.size(188.dp),
                            description = "${ui.type.label}构成：" + ui.shares.joinToString("；") { share ->
                                "${categoryLabel(share.categoryId.ifEmpty { null }, ui.categoriesById)} ${(share.ratio * 100).roundToInt()}%"
                            },
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("合计", style = JkType.caption, color = colors.textTertiary)
                            RollingAmount(total, JkType.amount.copy(fontSize = JkType.heading.fontSize), colors.textPrimary)
                            val change = LedgerMath.change(total, before)
                            Text(
                                text = when {
                                    change == null -> "上月无记录"
                                    change == 0f -> "与上月持平"
                                    else -> "比上月" + (if (change > 0) "多 " else "少 ") + "${(abs(change) * 100).roundToInt()}%"
                                },
                                style = JkType.caption,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.x2))
                    ui.shares.forEachIndexed { index, share ->
                        ShareRow(index, share, ui.categoriesById, ui.type)
                    }
                }
            }

            Spacer(Modifier.height(Space.x2))
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(Space.x2_5)) {
                Text("近 $TREND_MONTHS 个月收支", style = JkType.heading, color = colors.textPrimary)
                Spacer(Modifier.height(Space.x1_5))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("本月支出", style = JkType.caption, color = colors.textTertiary)
                        RollingAmount(ui.summary.expense, JkType.amount, colors.textPrimary)
                        ChangeNote(LedgerMath.change(ui.summary.expense, ui.previous.expense), higherIsBad = true)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("本月收入", style = JkType.caption, color = colors.textTertiary)
                        RollingAmount(ui.summary.income, JkType.amount, colors.income)
                        ChangeNote(LedgerMath.change(ui.summary.income, ui.previous.income), higherIsBad = false)
                    }
                }
                Spacer(Modifier.height(Space.x2))
                if (ui.trend.isNotEmpty()) {
                    TrendBars(
                        expense = ui.trend.map { it.summary.expense },
                        income = ui.trend.map { it.summary.income },
                        labels = ui.trend.map { "${it.month.monthValue}月" },
                    )
                }
                Spacer(Modifier.height(Space.x1_5))
                val months = ui.trend.filter { it.summary.expense > 0 }
                if (months.isNotEmpty()) {
                    Text(
                        "有记录的月份平均每月支出 ${Money.format(months.sumOf { it.summary.expense } / months.size)}",
                        style = JkType.amountCaption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareRow(index: Int, share: CategoryShare, byId: Map<String, LedgerCategory>, type: TxType) {
    val colors = Jk.colors
    val category = byId[share.categoryId]
    val tone = chartColorOf(index)
    Row(Modifier.padding(vertical = Space.x1), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(Radius.tile)
                .background(tone.copy(alpha = if (colors.isDark) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(categoryIcon(category?.icon), contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category?.name ?: "未分类",
                    style = JkType.body,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Space.x1))
                Text("${share.count} 笔", style = JkType.amountCaption, color = colors.textTertiary)
                Spacer(Modifier.weight(1f))
                Text("${(share.ratio * 1000).roundToInt() / 10.0}%", style = JkType.amountSmall, color = colors.textSecondary)
                Spacer(Modifier.width(Space.x1_5))
                Text(Money.format(share.amount), style = JkType.amount, color = colors.textPrimary)
            }
            Spacer(Modifier.height(Space.x05))
            ShareBar(share.ratio, tone)
        }
    }
}

@Composable
private fun ShareBar(ratio: Float, tone: Color) {
    val track = Jk.colors.surfaceAlt
    val shown by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(520, easing = FastOutSlowInEasing), label = "share")
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
        drawRoundRect(
            tone,
            size = size.copy(width = size.width * shown),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
    }
}

@Composable
private fun ChangeNote(change: Float?, higherIsBad: Boolean) {
    val colors = Jk.colors
    if (change == null) {
        Text("上月无记录", style = JkType.caption, color = colors.textTertiary)
        return
    }
    val percent = (abs(change) * 100).roundToInt()
    val up = change > 0
    Text(
        text = if (percent == 0) "与上月持平" else "环比" + (if (up) "↑" else "↓") + "$percent%",
        style = JkType.caption,
        color = when {
            percent == 0 -> colors.textTertiary
            up == higherIsBad -> colors.danger
            else -> colors.success
        },
    )
}
