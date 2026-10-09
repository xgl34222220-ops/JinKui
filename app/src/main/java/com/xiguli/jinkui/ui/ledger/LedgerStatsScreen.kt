package com.xiguli.jinkui.ui.ledger

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

data class StatsUi(
    val month: YearMonth,
    val type: TxType,
    val summary: Summary,
    val previous: Summary,
    val shares: List<CategoryShare>,
    val trend: List<MonthPoint>,
    val categoriesById: Map<String, LedgerCategory>,
)

private const val TREND_MONTHS = 6

private fun buildStatsUi(data: LedgerData, month: YearMonth, type: TxType, zone: ZoneId): StatsUi {
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

    val ui: StateFlow<StatsUi> = combine(container.ledger.data, month, type) { data, m, t ->
        buildStatsUi(data, m, t, ZoneId.systemDefault())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildStatsUi(container.ledger.data.value, month.value, type.value, ZoneId.systemDefault()),
        )
}

@Composable
fun LedgerStatsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { LedgerStatsViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
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
            MonthSwitcher(ui.month, { viewModel.month.value = it }, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(Space.x1))
            JkSegmented(
                options = listOf(TxType.EXPENSE to "支出", TxType.INCOME to "收入"),
                selected = ui.type,
                onSelect = { viewModel.type.value = it },
            )
            Spacer(Modifier.height(Space.x2))

            val total = if (ui.type == TxType.EXPENSE) ui.summary.expense else ui.summary.income
            val before = if (ui.type == TxType.EXPENSE) ui.previous.expense else ui.previous.income
            JkCard(Modifier.fillMaxWidth()) {
                Text("${ui.month.monthValue}月${ui.type.label}构成", style = JkType.heading, color = colors.textPrimary)
                Spacer(Modifier.height(Space.x2))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Donut(ui.shares.map { it.amount }, Modifier.size(180.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("合计", style = JkType.caption, color = colors.textTertiary)
                        Text(Money.format(total), style = JkType.heading, color = colors.textPrimary)
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
                if (ui.shares.isEmpty()) {
                    Text("这个月还没有${ui.type.label}记录", style = JkType.small, color = colors.textTertiary)
                }
                ui.shares.forEachIndexed { index, share ->
                    ShareRow(index, share, ui.categoriesById)
                }
            }

            Spacer(Modifier.height(Space.x2))
            JkCard(Modifier.fillMaxWidth()) {
                Text("近 $TREND_MONTHS 个月收支", style = JkType.heading, color = colors.textPrimary)
                Spacer(Modifier.height(Space.x1))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("本月支出", style = JkType.caption, color = colors.textTertiary)
                        Text(Money.format(ui.summary.expense), style = JkType.bodyStrong, color = colors.textPrimary)
                        ChangeNote(LedgerMath.change(ui.summary.expense, ui.previous.expense), higherIsBad = true)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("本月收入", style = JkType.caption, color = colors.textTertiary)
                        Text(Money.format(ui.summary.income), style = JkType.bodyStrong, color = colors.success)
                        ChangeNote(LedgerMath.change(ui.summary.income, ui.previous.income), higherIsBad = false)
                    }
                }
                Spacer(Modifier.height(Space.x2))
                TrendBars(
                    expense = ui.trend.map { it.summary.expense },
                    income = ui.trend.map { it.summary.income },
                    labels = ui.trend.map { "${it.month.monthValue}月" },
                )
                Spacer(Modifier.height(Space.x1_5))
                val months = ui.trend.filter { it.summary.expense > 0 }
                if (months.isNotEmpty()) {
                    Text(
                        "有记录的月份平均每月支出 ${Money.format(months.sumOf { it.summary.expense } / months.size)}",
                        style = JkType.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareRow(index: Int, share: CategoryShare, byId: Map<String, LedgerCategory>) {
    val colors = Jk.colors
    val category = byId[share.categoryId]
    Column(Modifier.padding(vertical = Space.x1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(chartColor(index))
            Spacer(Modifier.width(Space.x1))
            Text(
                category?.name ?: "未分类",
                style = JkType.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${share.count} 笔", style = JkType.caption, color = colors.textTertiary)
            Spacer(Modifier.width(Space.x1_5))
            Text("${(share.ratio * 1000).roundToInt() / 10.0}%", style = JkType.small, color = colors.textSecondary)
            Spacer(Modifier.width(Space.x1_5))
            Text(Money.format(share.amount), style = JkType.bodyStrong, color = colors.textPrimary)
        }
        Spacer(Modifier.height(Space.x05))
        Box(Modifier.padding(start = Space.x2)) {
            ShareBar(share.ratio, index)
        }
    }
}

@Composable
private fun ShareBar(ratio: Float, index: Int) {
    val track = Jk.colors.surfaceAlt
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
        drawRoundRect(
            chartColor(index),
            size = size.copy(width = size.width * ratio.coerceIn(0f, 1f)),
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
