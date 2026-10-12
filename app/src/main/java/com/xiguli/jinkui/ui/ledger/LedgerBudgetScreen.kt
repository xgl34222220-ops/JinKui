package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.theme.JkSize
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
    /** 1.5.0-alpha02：这个月花了钱、还没有预算的一级支出分类（花得多的在前，最多 6 个），点一下就能设。 */
    val unbudgeted: List<Pair<LedgerCategory, Long>> = emptyList(),
)

internal fun buildBudgetUi(data: LedgerData, month: YearMonth, zone: ZoneId): BudgetUi {
    // 1.4.0-alpha04：预算按退款后的实际支出算，报销的支出不占预算
    val inMonth = LedgerMath.inMonth(com.xiguli.jinkui.data.ledger.RefundMath.forStats(data.transactions), month, zone)
    val progress = LedgerMath.budgetProgress(data.budgets, inMonth, data.categories)
    val byId = data.categories.associateBy { it.id }
    val budgeted = progress.mapNotNull { it.budget.categoryId }.toSet()
    val spentByTop = HashMap<String, Long>()
    for (tx in inMonth) {
        if (tx.type != TxType.EXPENSE) continue
        val top = tx.categoryId?.let { LedgerMath.topLevelOf(it, byId) } ?: continue
        spentByTop.merge(top, tx.amount, Long::plus)
    }
    val unbudgeted = spentByTop.entries
        .filter { it.key !in budgeted && it.value > 0 }
        .mapNotNull { (id, spent) -> byId[id]?.takeIf { it.type == TxType.EXPENSE && it.parentId == null }?.let { it to spent } }
        .sortedByDescending { it.second }
        .take(6)
    return BudgetUi(
        unbudgeted = unbudgeted,
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
            title = if (current.categoryId == null) "每月总预算" else "${ui.categoriesById[current.categoryId]?.name ?: "分类"} · 每月预算",
            current = current.current,
            spent = if (current.categoryId == null) ui.monthExpense
            else ui.categories.firstOrNull { it.budget.categoryId == current.categoryId }?.spent
                ?: ui.unbudgeted.firstOrNull { it.first.id == current.categoryId }?.second ?: 0L,
            onDismiss = { edit = null },
            onSave = { amount -> viewModel.set(current.categoryId, amount) { edit = null } },
        )
    }
}

@Composable
internal fun BudgetDialog(
    title: String,
    current: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    /** 1.5.0-alpha02：这个月已经花了多少（帮着定预算）；null 不显示。 */
    spent: Long? = null,
) {
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
        if (spent != null) {
            Text("这个月已经花了 ${Money.format(spent)}", style = JkType.caption, color = Jk.colors.textTertiary)
        }
        Text("每个月都按这个数算，下个月自动重新开始。", style = JkType.caption, color = Jk.colors.textTertiary)
        if (current > 0) {
            JkButton("不设这项预算了", { onSave(0L) }, style = ButtonStyle.Danger, compact = true)
        }
    }
}

/**
 * 预算（无状态）。1.5.0-alpha02 重做（参考钱迹、一木记账的预算页，只借鉴交互）：
 * 头图先回答“这个月还能花多少”（超了就写“已超支”），一条进度，下面“预算 / 已花 / 每天还能花”；
 * 分类预算每行“已花 x，预算 y”与“还能花 / 超支”，一条进度；这个月花了钱但没预算的分类列在下面，点一下就能设。
 * [onEdit] 的分类为 null 时是总预算。
 */
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
    val thisMonth = YearMonth.from(today) == ui.month
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
                .padding(horizontal = Space.x2)
                .navigationBarsPadding()
                .padding(bottom = Space.x4),
        ) {
            MonthSwitcher(ui.month, onMonth, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(Space.x1))
            val total = ui.total
            com.xiguli.jinkui.ui.components.BotanicalCard(Modifier.fillMaxWidth(), layout = com.xiguli.jinkui.ui.components.BotanicalLayout.TOP_RIGHT) {
                Column(Modifier.padding(Space.x2_5)) {
                    if (total == null) {
                        Text("${ui.month.monthValue}月已花(元)", style = JkType.small.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
                        Text(Money.format(ui.monthExpense, symbol = false), style = JkType.amountHero.copy(fontSize = 32.sp), color = colors.textPrimary, maxLines = 1)
                        Spacer(Modifier.height(Space.x05))
                        Text("还没有设每月总预算。设一个数，金匮帮你算还能花多少、每天能花多少。", style = JkType.small, color = colors.textTertiary)
                        Spacer(Modifier.height(Space.x1_5))
                        JkButton("设每月总预算", { onEdit(null, 0L) }, compact = true)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (total.over) "${ui.month.monthValue}月已超支(元)" else if (thisMonth) "本月还能花(元)" else "${ui.month.monthValue}月还剩(元)",
                                style = JkType.small.copy(fontWeight = FontWeight.Medium),
                                color = colors.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "改预算",
                                style = JkType.caption.copy(fontWeight = FontWeight.Bold),
                                color = colors.accent,
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .clip(Radius.pill)
                                    .clickable(role = Role.Button, onClickLabel = "修改每月总预算") { onEdit(null, total.budget.amount) }
                                    .padding(horizontal = Space.x1, vertical = Space.x1_5),
                            )
                        }
                        Text(
                            Money.format(if (total.over) -total.remaining else total.remaining, symbol = false),
                            style = JkType.amountHero.copy(fontSize = 32.sp),
                            color = if (total.over) colors.danger else colors.accent,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(Space.x1))
                        BudgetBar(total.ratio, total.over)
                        Spacer(Modifier.height(Space.x1_5))
                        val allowance = LedgerMath.dailyAllowance(total, ui.month, today)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                            BudgetCell("每月预算", Money.format(total.budget.amount, symbol = false), colors.textPrimary, Modifier.weight(1f))
                            BudgetCell("已花", Money.format(total.spent, symbol = false), if (total.over) colors.danger else colors.textPrimary, Modifier.weight(1f))
                            if (allowance != null) {
                                BudgetCell("每天还能花", Money.format(allowance.perDay, symbol = false), colors.textPrimary, Modifier.weight(1f))
                            } else {
                                BudgetCell("用了", "${(total.ratio * 100).toInt().coerceAtMost(999)}%", if (total.over) colors.danger else colors.textPrimary, Modifier.weight(1f))
                            }
                        }
                        if (allowance != null) {
                            Text("本月还有 ${allowance.daysLeft} 天（含今天）", style = JkType.caption, color = colors.textTertiary, modifier = Modifier.padding(top = Space.x05))
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(start = Space.x05, end = Space.x05, top = Space.x3, bottom = Space.x1), verticalAlignment = Alignment.CenterVertically) {
                Text("分类预算", style = JkType.section, color = colors.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                val sum = ui.categories.sumOf { it.budget.amount }
                if (ui.categories.isNotEmpty()) Text("合计 ${Money.format(sum)}", style = JkType.small, color = colors.textTertiary)
            }
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x1)) {
                if (ui.categories.isEmpty()) {
                    Text(
                        "可以给餐饮、购物这些单独设预算，子分类花的钱会算进它所属的分类。",
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
                            .clickable(onClickLabel = "修改预算") { onEdit(id, progress.budget.amount) }
                            .padding(horizontal = Space.x2, vertical = Space.x1_5),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryChip(categoryIcon(category?.icon), TxType.EXPENSE, size = 36.dp)
                            Spacer(Modifier.width(Space.x1_5))
                            Column(Modifier.weight(1f)) {
                                Text(category?.name ?: "已删除的分类", style = JkType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "已花 ${Money.format(progress.spent)}，预算 ${Money.format(progress.budget.amount)}",
                                    style = JkType.caption,
                                    color = colors.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(Space.x1))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (progress.over) "超支" else "还能花", style = JkType.caption, color = if (progress.over) colors.danger else colors.textTertiary)
                                Text(
                                    Money.format(if (progress.over) -progress.remaining else progress.remaining),
                                    style = JkType.amount.copy(fontWeight = FontWeight.Bold),
                                    color = if (progress.over) colors.danger else colors.textPrimary,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.x1))
                        BudgetBar(progress.ratio, progress.over)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = JkSize.touch)
                        .clip(Radius.control)
                        .clickable(role = Role.Button) { onAddCategory() }
                        .padding(horizontal = Space.x2, vertical = Space.x1_5),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(Space.x1))
                    Text("添加分类预算", style = JkType.body, color = colors.accent)
                }
            }
            if (ui.unbudgeted.isNotEmpty()) {
                Text(
                    "这个月花了钱、还没设预算的分类",
                    style = JkType.small.copy(fontWeight = FontWeight.Medium),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = Space.x05, top = Space.x2, bottom = Space.x1),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                    for ((category, spent) in ui.unbudgeted) {
                        JkChip("${category.name} 已花 ${Money.format(spent)}", false, { onEdit(category.id, 0L) })
                    }
                }
            }
            val sum = ui.categories.sumOf { it.budget.amount }
            if (total != null && sum > total.budget.amount) {
                Text(
                    "分类预算加起来 ${Money.format(sum)}，比总预算多了 ${Money.format(sum - total.budget.amount)}",
                    style = JkType.caption,
                    color = colors.danger,
                    modifier = Modifier.padding(start = Space.x05, top = Space.x1),
                )
            }
            Text(
                "预算每个月自动重新开始，只算支出，转账、借钱不算；退款会扣回去，待报销的不算。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x2),
            )
        }
    }
}

@Composable
private fun BudgetCell(label: String, value: String, tone: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = JkType.caption.copy(fontWeight = FontWeight.Medium), color = Jk.colors.textSecondary, maxLines = 1)
        Text(value, style = JkType.amount.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
