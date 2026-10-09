package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.BudgetProgress
import com.xiguli.jinkui.data.ledger.DayGroup
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.Summary
import com.xiguli.jinkui.data.ledger.TxFilter
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkEmpty
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
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

/** 月度视图需要的一切，在后台线程一次算好。 */
data class LedgerUi(
    val month: YearMonth,
    val summary: Summary,
    val days: List<DayGroup>,
    val totalBudget: BudgetProgress?,
    val accounts: List<LedgerAccount>,
    val accountsById: Map<String, LedgerAccount>,
    val categoriesById: Map<String, LedgerCategory>,
    val filterActive: Boolean,
    val monthCount: Int,
    val unreadable: Int,
    /** 流水里最晚的月份，允许月份切换翻到将来日期的记录。 */
    val latestMonth: YearMonth?,
)

fun buildLedgerUi(data: LedgerData, month: YearMonth, filter: TxFilter, zone: ZoneId): LedgerUi {
    val inMonth = LedgerMath.inMonth(data.transactions, month, zone)
    val filtered = LedgerMath.filter(inMonth, filter, data.categories)
    return LedgerUi(
        month = month,
        // 汇总跟随筛选：筛了某个分类，就看这个分类的收支
        summary = LedgerMath.summary(filtered),
        days = LedgerMath.groupByDay(filtered, zone),
        totalBudget = LedgerMath.budgetProgress(data.budgets.filter { it.categoryId == null }, inMonth, data.categories).firstOrNull(),
        accounts = data.accounts,
        accountsById = data.accounts.associateBy { it.id },
        categoriesById = data.categories.associateBy { it.id },
        filterActive = filter.isActive,
        monthCount = inMonth.size,
        unreadable = data.unreadable,
        latestMonth = LedgerMath.latestMonth(data.transactions, zone),
    )
}

class LedgerViewModel(private val container: AppContainer) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val filter = MutableStateFlow(TxFilter())
    val listState = LazyListState()

    val ui: StateFlow<LedgerUi> = combine(container.ledger.data, month, filter) { data, m, f ->
        buildLedgerUi(data, m, f, ZoneId.systemDefault())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildLedgerUi(container.ledger.data.value, month.value, filter.value, ZoneId.systemDefault()))

    fun setMonth(value: YearMonth) {
        month.value = value
    }

    fun setQuery(text: String) {
        filter.value = filter.value.copy(query = text)
    }

    fun setAccount(id: String?) {
        filter.value = filter.value.copy(accountId = id)
    }

    fun setCategory(id: String?) {
        filter.value = filter.value.copy(categoryId = id)
    }

    fun clearFilters() {
        filter.value = TxFilter()
    }

    fun save(tx: LedgerTransaction, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                container.ledger.saveTransaction(tx)
                onDone()
            } catch (e: LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun runRecurring() {
        viewModelScope.launch {
            val made = runCatching { container.ledger.runRecurring() }.getOrDefault(0)
            if (made > 0) container.messages.show("周期记账已自动记上 $made 笔")
        }
    }

    fun delete(tx: LedgerTransaction) {
        viewModelScope.launch {
            runCatching { container.ledger.deleteTransaction(tx.id) }
                .onSuccess { container.messages.show("已删除这笔记录") }
                .onFailure { container.messages.show("删除失败") }
        }
    }
}

@Composable
fun LedgerScreen(
    onAccounts: () -> Unit,
    onStats: () -> Unit,
    onBudget: () -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
    onRecurring: () -> Unit = {},
) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { LedgerViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val colors = Jk.colors

    var editing by remember { mutableStateOf<LedgerTransaction?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    // 每次回到账本页补记到期的周期记账（例如解锁状态下跨过了午夜）。
    LaunchedEffect(Unit) { viewModel.runRecurring() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = viewModel.listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.x2_5, end = Space.x1, top = Space.x1_5, bottom = Space.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("账本", style = JkType.brand, color = colors.textPrimary)
                        Text("每一笔都加密保存在本机", style = JkType.caption, color = colors.textTertiary)
                    }
                    JkIconButton(Icons.Outlined.Search, "搜索", { showSearch = !showSearch })
                    JkIconButton(Icons.Outlined.BarChart, "统计", onStats)
                    JkIconButton(Icons.Outlined.Lock, "立即锁定", { container.vault.lock() })
                    JkIconButton(Icons.Outlined.MoreHoriz, "更多", { showMore = true })
                }
            }
            item(key = "month") {
                MonthSwitcher(
                    month = ui.month,
                    onChange = viewModel::setMonth,
                    modifier = Modifier.padding(horizontal = Space.x1_5),
                    latest = ui.latestMonth,
                )
            }
            item(key = "summary") {
                SummaryCard(ui, onBudget, Modifier.padding(horizontal = Space.x2_5, vertical = Space.x1))
            }
            item(key = "filters") {
                Column {
                    if (showSearch || filter.query.isNotEmpty()) {
                        JkTextField(
                            value = filter.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = "搜索备注或标签",
                            leading = Icons.Outlined.Search,
                            modifier = Modifier.padding(horizontal = Space.x2_5, vertical = Space.x05),
                            trailing = {
                                JkIconButton(Icons.Outlined.Close, "清除", {
                                    viewModel.setQuery("")
                                    showSearch = false
                                })
                            },
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.x2_5),
                        horizontalArrangement = Arrangement.spacedBy(Space.x1),
                        modifier = Modifier.padding(vertical = Space.x1),
                    ) {
                        item {
                            JkChip(
                                text = filter.accountId?.let { ui.accountsById[it]?.name } ?: "全部账户",
                                selected = filter.accountId != null,
                                onClick = { pickAccount = true },
                            )
                        }
                        item {
                            JkChip(
                                text = filter.categoryId?.let { categoryLabel(it, ui.categoriesById) } ?: "全部分类",
                                selected = filter.categoryId != null,
                                onClick = { pickCategory = true },
                            )
                        }
                        if (ui.filterActive) {
                            item { JkChip("清除筛选", false, viewModel::clearFilters) }
                        }
                    }
                }
            }
            if (ui.unreadable > 0) {
                item(key = "unreadable") {
                    Text(
                        "有 ${ui.unreadable} 条记录数据损坏，无法解密，已略过",
                        style = JkType.small,
                        color = colors.danger,
                        modifier = Modifier.padding(horizontal = Space.x2_5, vertical = Space.x05),
                    )
                }
            }
            if (ui.days.isEmpty()) {
                item(key = "empty") {
                    JkEmpty(
                        title = if (ui.filterActive) "没有符合条件的记录" else "这个月还没有记账",
                        message = if (ui.filterActive) "换个筛选条件试试" else "点右下角“记一笔”，三秒记完一笔开销。",
                    )
                }
            }
            for (day in ui.days) {
                item(key = "d-${day.date}") {
                    DayHeader(day)
                }
                items(day.items, key = { it.id }) { tx ->
                    TransactionRow(tx, ui, onClick = { editing = tx })
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(Space.x2_5)
                .height(52.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(role = Role.Button) { creating = true }
                .padding(start = Space.x2, end = Space.x2_5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.x1))
            Text("记一笔", style = JkType.bodyStrong, color = colors.onPrimary)
        }
    }

    if (creating || editing != null) {
        EntrySheet(
            data = data,
            existing = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { tx, keepOpen, reset ->
                viewModel.save(tx) {
                    if (!keepOpen) {
                        creating = false
                        editing = null
                    } else {
                        reset()
                        container.messages.show("已记下，继续下一笔")
                    }
                }
            },
            onDelete = { tx ->
                viewModel.delete(tx)
                editing = null
            },
        )
    }

    if (pickAccount) {
        JkSheet(onDismiss = { pickAccount = false }, title = "按账户筛选") {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                JkRow(title = "全部账户", modifier = Modifier.clip(Radius.control), trailing = {}, onClick = {
                    viewModel.setAccount(null)
                    pickAccount = false
                })
                for (account in ui.accounts) {
                    JkRow(
                        title = account.name,
                        subtitle = account.kind.label + if (account.archived) " · 已停用" else "",
                        icon = accountIcon(account.kind),
                        modifier = Modifier.clip(Radius.control),
                        trailing = {},
                        onClick = {
                            viewModel.setAccount(account.id)
                            pickAccount = false
                        },
                    )
                }
            }
        }
    }

    if (pickCategory) {
        CategoryPickerSheet(
            categories = data.categories,
            title = "按分类筛选",
            includeAll = true,
            onDismiss = { pickCategory = false },
            onPick = {
                viewModel.setCategory(it)
                pickCategory = false
            },
        )
    }

    if (showMore) {
        JkSheet(onDismiss = { showMore = false }, title = "账本") {
            JkRow(
                title = "账户与余额",
                subtitle = "现金、银行卡、支付宝、微信、信用卡",
                icon = Icons.Outlined.AccountBalanceWallet,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onAccounts()
                },
            )
            JkRow(
                title = "统计",
                subtitle = "分类占比与月度趋势",
                icon = Icons.Outlined.PieChart,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onStats()
                },
            )
            JkRow(
                title = "预算",
                subtitle = "每月总预算与分类预算",
                icon = Icons.Outlined.BarChart,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onBudget()
                },
            )
            JkRow(
                title = "周期记账",
                subtitle = "房租、工资、订阅等固定收支自动记上",
                icon = Icons.Outlined.Repeat,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onRecurring()
                },
            )
            JkRow(
                title = "分类管理",
                subtitle = "增加、改名或隐藏分类",
                icon = Icons.Outlined.Category,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onCategories()
                },
            )
            JkRow(
                title = "设置",
                subtitle = "解锁方式、自动锁定、加密备份",
                icon = Icons.Outlined.Settings,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onSettings()
                },
            )
        }
    }
}

@Composable
private fun SummaryCard(ui: LedgerUi, onBudget: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    JkCard(modifier.fillMaxWidth()) {
        Text(if (ui.filterActive) "筛选结果 · 支出" else "${ui.month.monthValue}月支出", style = JkType.small, color = colors.textTertiary)
        Text(Money.format(ui.summary.expense), style = JkType.display, color = colors.textPrimary)
        Spacer(Modifier.height(Space.x1_5))
        Row {
            Column(Modifier.weight(1f)) {
                Text("收入", style = JkType.caption, color = colors.textTertiary)
                Text(Money.format(ui.summary.income), style = JkType.bodyStrong, color = colors.success)
            }
            Column(Modifier.weight(1f)) {
                Text("结余", style = JkType.caption, color = colors.textTertiary)
                Text(
                    Money.format(ui.summary.balance),
                    style = JkType.bodyStrong,
                    color = if (ui.summary.balance < 0) colors.danger else colors.textPrimary,
                )
            }
        }
        val budget = ui.totalBudget
        Spacer(Modifier.height(Space.x1_5))
        if (budget != null) {
            Column(
                Modifier
                    .clip(Radius.chip)
                    .clickable(onClick = onBudget),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text("月预算 ${Money.format(budget.budget.amount)}", style = JkType.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(
                        if (budget.over) "超支 ${Money.format(-budget.remaining)}" else "剩余 ${Money.format(budget.remaining)}",
                        style = JkType.caption,
                        color = if (budget.over) colors.danger else colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(Space.x05))
                BudgetBar(budget.ratio, budget.over)
            }
        } else {
            Text(
                "设置每月预算 ›",
                style = JkType.caption,
                color = colors.accent,
                modifier = Modifier
                    .clip(Radius.chip)
                    .clickable(onClick = onBudget)
                    .padding(vertical = Space.x05),
            )
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x2_5, end = Space.x2_5, top = Space.x2, bottom = Space.x05),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayLabel(day.date), style = JkType.small, color = colors.textSecondary, modifier = Modifier.weight(1f))
        val parts = buildList {
            if (day.summary.expense > 0) add("支 ${Money.format(day.summary.expense)}")
            if (day.summary.income > 0) add("收 ${Money.format(day.summary.income)}")
        }
        Text(parts.joinToString("　"), style = JkType.caption, color = colors.textTertiary)
    }
    JkDivider(Modifier.padding(horizontal = Space.x2_5))
}

@Composable
private fun TransactionRow(tx: LedgerTransaction, ui: LedgerUi, onClick: () -> Unit) {
    val colors = Jk.colors
    val category = tx.categoryId?.let { ui.categoriesById[it] }
    val account = ui.accountsById[tx.accountId]
    val title: String
    val subtitle: String
    if (tx.type == TxType.TRANSFER) {
        val to = tx.toAccountId?.let { ui.accountsById[it] }
        title = "转账"
        subtitle = "${account?.name ?: "未知账户"} → ${to?.name ?: "未知账户"}"
    } else {
        title = categoryLabel(tx.categoryId, ui.categoriesById)
        subtitle = account?.name ?: "未知账户"
    }
    val detail = listOf(tx.note, tx.tags.joinToString(" ") { "#$it" }).filter { it.isNotBlank() }.joinToString("　")
    val time = java.time.Instant.ofEpochMilli(tx.occurredAt).atZone(ZoneId.systemDefault()).toLocalTime()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "修改这笔", onClick = onClick)
            .padding(horizontal = Space.x2_5, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val parentIcon = category?.parentId?.let { ui.categoriesById[it]?.icon }
        RoundIcon(
            icon = if (tx.type == TxType.TRANSFER) TransferIcon else categoryIcon(category?.icon ?: parentIcon),
            tint = when (tx.type) {
                TxType.EXPENSE -> colors.accent
                TxType.INCOME -> colors.success
                TxType.TRANSFER -> colors.textSecondary
            },
        )
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Text(title, style = JkType.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "%02d:%02d　%s".format(time.hour, time.minute, subtitle) + if (detail.isNotEmpty()) "　$detail" else "",
                style = JkType.caption,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.x1))
        Text(
            text = when (tx.type) {
                TxType.EXPENSE -> "-" + Money.format(tx.amount)
                TxType.INCOME -> "+" + Money.format(tx.amount)
                TxType.TRANSFER -> Money.format(tx.amount)
            },
            style = JkType.bodyStrong,
            color = amountColor(tx.type),
        )
    }
}

/** 选分类：一级分类在上，点开显示子分类；也可以直接选一级分类。 */
@Composable
fun CategoryPickerSheet(
    categories: List<LedgerCategory>,
    title: String,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    includeAll: Boolean = false,
    type: TxType? = null,
) {
    JkSheet(onDismiss = onDismiss, title = title) {
        Column(
            Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (includeAll) {
                JkRow(title = "全部分类", modifier = Modifier.clip(Radius.control), trailing = {}, onClick = { onPick(null) })
            }
            val types = if (type != null) listOf(type) else listOf(TxType.EXPENSE, TxType.INCOME)
            for (t in types) {
                if (types.size > 1) {
                    Text(
                        t.label,
                        style = JkType.small,
                        color = Jk.colors.textTertiary,
                        modifier = Modifier.padding(start = Space.x2, top = Space.x1_5, bottom = Space.x05),
                    )
                }
                val tops = categories.filter { it.type == t && it.parentId == null && !it.archived }.sortedBy { it.position }
                for (top in tops) {
                    JkRow(
                        title = top.name,
                        icon = categoryIcon(top.icon),
                        modifier = Modifier.clip(Radius.control),
                        trailing = {},
                        onClick = { onPick(top.id) },
                    )
                    val children = categories.filter { it.parentId == top.id && !it.archived }.sortedBy { it.position }
                    if (children.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(start = Space.x6 + Space.x05, end = Space.x2),
                            horizontalArrangement = Arrangement.spacedBy(Space.x1),
                            modifier = Modifier.padding(bottom = Space.x1),
                        ) {
                            items(children, key = { it.id }) { child ->
                                JkChip(child.name, false, { onPick(child.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
