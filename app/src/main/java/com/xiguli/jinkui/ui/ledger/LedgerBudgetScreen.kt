package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.BudgetProgress
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.Money
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetUi(
    val month: YearMonth,
    val total: BudgetProgress?,
    val categories: List<BudgetProgress>,
    val monthExpense: Long,
    val categoriesById: Map<String, LedgerCategory>,
    val allCategories: List<LedgerCategory>,
)

internal fun buildBudgetUi(data: LedgerData, month: YearMonth, zone: ZoneId): BudgetUi {
    val inMonth = LedgerMath.inMonth(data.transactions, month, zone)
    val progress = LedgerMath.budgetProgress(data.budgets, inMonth, data.categories)
    return BudgetUi(
        month = month,
        total = progress.firstOrNull { it.budget.categoryId == null },
        categories = progress.filter { it.budget.categoryId != null }.sortedByDescending { it.ratio },
        monthExpense = LedgerMath.summary(inMonth).expense,
        categoriesById = data.categories.associateBy { it.id },
        allCategories = data.categories,
    )
}

class LedgerBudgetViewModel(private val container: AppContainer) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())

    val ui: StateFlow<BudgetUi> = combine(container.ledger.data, month) { data, m -> buildBudgetUi(data, m, ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildBudgetUi(container.ledger.data.value, month.value, ZoneId.systemDefault()))

    fun set(categoryId: String?, amount: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                container.ledger.setBudget(categoryId, amount)
                container.messages.show(if (amount == 0L) "已取消预算" else "预算已保存")
                onDone()
            } catch (e: Exception) {
                container.messages.show(e.message ?: "保存失败")
            }
        }
    }
}

/** 正在编辑的预算：[categoryId] 为 null 是总预算。 */
private class BudgetEdit(val categoryId: String?, val current: Long)

@Composable
fun LedgerBudgetScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { LedgerBudgetViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = Jk.colors
    var edit by remember { mutableStateOf<BudgetEdit?>(null) }
    var pickCategory by remember { mutableStateOf(false) }

    LedgerBudgetContent(
        ui = ui,
        onBack = onBack,
        onMonth = { viewModel.month.value = it },
        onEdit = { id, amount -> edit = BudgetEdit(id, amount) },
        onAddCategory = { pickCategory = true },
    )

    if (pickCategory) {
        CategoryPickerSheet(
            categories = ui.allCategories.filter { it.parentId == null },
            title = "给哪个分类设预算",
            type = TxType.EXPENSE,
            onDismiss = { pickCategory = false },
            onPick = { id ->
                pickCategory = false
                if (id != null) {
                    val existing = ui.categories.firstOrNull { it.budget.categoryId == id }?.budget?.amount ?: 0L
                    edit = BudgetEdit(id, existing)
                }
            },
        )
    }

    edit?.let { current ->
        BudgetDialog(
            title = if (current.categoryId == null) "每月总预算" else "${ui.categoriesById[current.categoryId]?.name ?: "分类"}预算",
            current = current.current,
            onDismiss = { edit = null },
            onSave = { amount -> viewModel.set(current.categoryId, amount) { edit = null } },
        )
    }
}

@Composable
private fun BudgetDialog(title: String, current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) Money.plain(current) else "") }
    var error by remember { mutableStateOf<String?>(null) }
    JkDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = "保存",
        onConfirm = {
            val cents = Money.parse(text)
            when {
                cents == null || cents <= 0L -> error = "请输入大于 0 的金额，最多两位小数"
                else -> onSave(cents)
            }
        },
    ) {
        JkTextField(
            value = text,
            onValueChange = {
                text = it.take(16)
                error = null
            },
            placeholder = "例如 3000",
            keyboardType = KeyboardType.Decimal,
            error = error,
        )
        if (current > 0) {
            JkButton("取消这项预算", { onSave(0L) }, style = ButtonStyle.Danger, compact = true)
        }
    }
}

/** 预算（无状态）：总预算卡与分类预算列表。[onEdit] 的分类为 null 时是总预算。 */
@Composable
fun LedgerBudgetContent(
    ui: BudgetUi,
    onBack: () -> Unit,
    onMonth: (java.time.YearMonth) -> Unit,
    onEdit: (String?, Long) -> Unit,
    onAddCategory: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val colors = Jk.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar("预算", onBack = onBack)
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
            JkCard(Modifier.fillMaxWidth()) {
                Text("每月总预算", style = JkType.small, color = colors.textTertiary)
                val total = ui.total
                if (total == null) {
                    Text("未设置", style = JkType.title, color = colors.textPrimary)
                    Text("${ui.month.monthValue}月已支出 ${Money.format(ui.monthExpense)}", style = JkType.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(Space.x1_5))
                    JkButton("设置总预算", { onEdit(null, 0L) }, compact = true)
                } else {
                    RollingAmount(total.budget.amount, JkType.amountLarge, colors.textPrimary)
                    Spacer(Modifier.height(Space.x1))
                    BudgetBar(total.ratio, total.over)
                    Spacer(Modifier.height(Space.x1))
                    Row {
                        Text("已用 ${Money.format(total.spent)}", style = JkType.small, color = colors.textSecondary, modifier = Modifier.weight(1f))
                        Text(
                            if (total.over) "超支 ${Money.format(-total.remaining)}" else "剩余 ${Money.format(total.remaining)}",
                            style = JkType.small,
                            color = if (total.over) colors.danger else colors.textSecondary,
                        )
                    }
                    val allowance = LedgerMath.dailyAllowance(total, ui.month, today)
                    if (allowance != null) {
                        Text(
                            "本月还剩 ${allowance.daysLeft} 天，每天可用约 ${Money.format(allowance.perDay)}",
                            style = JkType.caption,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(top = Space.x05),
                        )
                    }
                    Spacer(Modifier.height(Space.x1_5))
                    JkButton("修改", { onEdit(null, total.budget.amount) }, style = ButtonStyle.Secondary, compact = true)
                }
            }

            JkSectionLabel("分类预算")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x1)) {
                if (ui.categories.isEmpty()) {
                    Text(
                        "可以给餐饮、购物等单独设预算，子分类的支出会算进所属分类。",
                        style = JkType.small,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = Space.x2, vertical = Space.x1),
                    )
                }
                for (progress in ui.categories) {
                    val id = progress.budget.categoryId
                    val category = id?.let(ui.categoriesById::get)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(id, progress.budget.amount) }
                            .padding(horizontal = Space.x2, vertical = Space.x1_5),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryChip(categoryIcon(category?.icon), TxType.EXPENSE, size = 36.dp)
                            Spacer(Modifier.width(Space.x1_5))
                            Column(Modifier.weight(1f)) {
                                Text(category?.name ?: "已删除的分类", style = JkType.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${Money.format(progress.spent)} / ${Money.format(progress.budget.amount)}",
                                    style = JkType.caption,
                                    color = colors.textTertiary,
                                )
                            }
                            Text(
                                if (progress.over) "超支 ${Money.format(-progress.remaining)}" else "剩 ${Money.format(progress.remaining)}",
                                style = JkType.small,
                                color = if (progress.over) colors.danger else colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(Space.x1))
                        BudgetBar(progress.ratio, progress.over)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.control)
                        .clickable { onAddCategory() }
                        .padding(horizontal = Space.x2, vertical = Space.x1_5),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(Space.x1))
                    Text("添加分类预算", style = JkType.body, color = colors.accent)
                }
            }
            val sum = ui.categories.sumOf { it.budget.amount }
            val total = ui.total
            if (total != null && sum > total.budget.amount) {
                Text(
                    "分类预算合计 ${Money.format(sum)}，已超过总预算",
                    style = JkType.caption,
                    color = colors.danger,
                    modifier = Modifier.padding(start = Space.x05, top = Space.x1),
                )
            }
            Text(
                "预算按月循环生效，只统计支出，不含转账。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x1),
            )
        }
    }
}
