package com.xiguli.jinkui.ui.ledger

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerClock
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
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
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LedgerViewModel(private val container: AppContainer) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    val month = MutableStateFlow(YearMonth.now())
    val filter = MutableStateFlow(TxFilter())
    /** 今天的日期：跨过午夜时更新，分组头的“今天 / 昨天”与日均随之刷新。 */
    private val today = MutableStateFlow(LocalDate.now(zone))
    val listState = LazyListState()

    // 初始值只是占位：第一次计算放到后台线程，不在主线程上卡住切到账本的那一帧。
    val ui: StateFlow<LedgerUi> = combine(container.ledger.data, month, filter, today) { data, m, f, d ->
        buildLedgerUi(data, m, f, zone, d)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LedgerUi.placeholder(month.value))

    init {
        // 在账本页停留跨过午夜 / 月末时：今天的日期更新；正在看本月的，跟到新的本月。
        viewModelScope.launch {
            while (isActive) {
                delay(LedgerClock.millisUntilNextDay(ZonedDateTime.now(zone)).coerceAtMost(3_600_000L))
                onClockTick()
            }
        }
    }

    /** 回到前台、或计时到点时调用。 */
    fun onClockTick(now: LocalDate = LocalDate.now(zone)) {
        val before = today.value
        if (before == now) return
        month.value = LedgerClock.follow(month.value, YearMonth.from(before), YearMonth.from(now))
        today.value = now
    }

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

    var editing by remember { mutableStateOf<LedgerTransaction?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }

    // 每次回到账本页补记到期的周期记账（例如解锁状态下跨过了午夜）。
    LaunchedEffect(Unit) { viewModel.runRecurring() }
    // 从后台回来时检查日期：跨了天 / 月就刷新（只有原本在看本月时才跳到新的本月）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onClockTick() }

    LedgerHome(
        ui = ui,
        filter = filter,
        listState = viewModel.listState,
        actions = LedgerHomeActions(
            onMonth = viewModel::setMonth,
            onQuery = viewModel::setQuery,
            onPickAccount = { pickAccount = true },
            onPickCategory = { pickCategory = true },
            onClearFilters = viewModel::clearFilters,
            onOpen = { editing = it },
            onCreate = { creating = true },
            onStats = onStats,
            onBudget = onBudget,
            onLock = { container.vault.lock() },
            onMore = { showMore = true },
        ),
    )

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
            val entries = listOf(
                Triple("账户与余额", "现金、银行卡、支付宝、微信、信用卡", Icons.Outlined.AccountBalanceWallet) to onAccounts,
                Triple("统计", "分类占比与月度趋势", Icons.Outlined.PieChart) to onStats,
                Triple("预算", "每月总预算与分类预算", Icons.Outlined.BarChart) to onBudget,
                Triple("周期记账", "房租、工资、订阅等固定收支自动记上", Icons.Outlined.Repeat) to onRecurring,
                Triple("分类管理", "增加、改名或隐藏分类", Icons.Outlined.Category) to onCategories,
                Triple("设置", "解锁方式、自动锁定、加密备份", Icons.Outlined.Settings) to onSettings,
            )
            for ((info, go) in entries) {
                JkRow(
                    title = info.first,
                    subtitle = info.second,
                    icon = info.third,
                    modifier = Modifier.clip(Radius.control),
                    onClick = {
                        showMore = false
                        go()
                    },
                )
            }
        }
    }
}

/** 账本首页上的所有操作；打包成一个对象，[LedgerHome] 保持无状态、可以单独做界面测试。 */
@Immutable
class LedgerHomeActions(
    val onMonth: (YearMonth) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onPickAccount: () -> Unit = {},
    val onPickCategory: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onOpen: (LedgerTransaction) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onStats: () -> Unit = {},
    val onBudget: () -> Unit = {},
    val onLock: () -> Unit = {},
    val onMore: () -> Unit = {},
)

/** 账本首页（无状态）：标题栏、月份切换、月度汇总、筛选条、按天分组的流水与“记一笔”。 */
@Composable
fun LedgerHome(
    ui: LedgerUi,
    filter: TxFilter,
    listState: LazyListState,
    actions: LedgerHomeActions,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    var showSearch by rememberSaveable { mutableStateOf(false) }
    // 往下滚动后“记一笔”收成圆形按钮，不挡住流水的金额
    val collapsed by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item(key = "header", contentType = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.x2_5, end = Space.x1, top = Space.x1_5, bottom = Space.x05),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("账本", style = JkType.brand, color = colors.textPrimary)
                        Text("每一笔都加密保存在本机", style = JkType.caption, color = colors.textTertiary)
                    }
                    JkIconButton(Icons.Outlined.Search, "搜索", { showSearch = !showSearch })
                    JkIconButton(Icons.Outlined.BarChart, "统计", actions.onStats)
                    JkIconButton(Icons.Outlined.Lock, "立即锁定", actions.onLock)
                    JkIconButton(Icons.Outlined.MoreHoriz, "更多", actions.onMore)
                }
            }
            item(key = "month", contentType = "month") {
                MonthSwitcher(
                    month = ui.month,
                    onChange = actions.onMonth,
                    modifier = Modifier.padding(horizontal = Space.x1_5),
                    latest = ui.latestMonth,
                )
            }
            item(key = "summary", contentType = "summary") {
                MonthSummaryCard(ui, actions.onBudget, Modifier.padding(horizontal = Space.x2_5, vertical = Space.x05))
            }
            item(key = "filters", contentType = "filters") {
                Column(Modifier.animateContentSize(tween(220))) {
                    if (showSearch || filter.query.isNotEmpty()) {
                        JkTextField(
                            value = filter.query,
                            onValueChange = actions.onQuery,
                            placeholder = "搜索备注或标签",
                            leading = Icons.Outlined.Search,
                            modifier = Modifier.padding(start = Space.x2_5, end = Space.x2_5, top = Space.x1),
                            trailing = {
                                JkIconButton(Icons.Outlined.Close, "清除", {
                                    actions.onQuery("")
                                    showSearch = false
                                })
                            },
                        )
                    }
                    // JkChip 的布局高度是 48dp（视觉 32dp），这里不再额外留上下边距
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.x2_5),
                        horizontalArrangement = Arrangement.spacedBy(Space.x1),
                        modifier = Modifier.padding(top = Space.x05),
                    ) {
                        item(key = "account") {
                            JkChip(
                                text = filter.accountId?.let { ui.accountsById[it]?.name } ?: "全部账户",
                                selected = filter.accountId != null,
                                onClick = actions.onPickAccount,
                            )
                        }
                        item(key = "category") {
                            JkChip(
                                text = filter.categoryId?.let { categoryLabel(it, ui.categoriesById) } ?: "全部分类",
                                selected = filter.categoryId != null,
                                onClick = actions.onPickCategory,
                            )
                        }
                        if (ui.filterActive) {
                            item(key = "clear") { JkChip("清除筛选", false, actions.onClearFilters) }
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
            if (ui.days.isEmpty() && !ui.loading) {
                item(key = "empty", contentType = "empty") {
                    JkEmpty(
                        title = if (ui.filterActive) "没有符合条件的记录" else "这个月还没有记账",
                        message = if (ui.filterActive) "换个筛选条件试试" else "点右下角“记一笔”，三秒记完一笔开销。",
                        illustration = { LedgerIllustration(if (ui.filterActive) LedgerArt.NO_MATCH else LedgerArt.EMPTY_MONTH) },
                    )
                }
            }
            for (day in ui.days) {
                stickyHeader(key = "d-${day.date}", contentType = "day") {
                    DayHeader(day)
                }
                itemsIndexed(day.rows, key = { _, row -> row.id }, contentType = { _, _ -> "tx" }) { index, row ->
                    TransactionRow(row, showDivider = index < day.rows.lastIndex, onClick = { actions.onOpen(row.tx) })
                }
            }
        }

        NewEntryButton(
            collapsed = collapsed,
            onClick = actions.onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(Space.x2_5),
        )
    }
}

/** 右下角的“记一笔”：按下时轻轻缩小并震动一下；列表往下滚后收成圆形。 */
@Composable
private fun NewEntryButton(collapsed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.6f, stiffness = 800f), label = "fabScale")
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(52.dp)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClickLabel = "记一笔") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))
            .padding(horizontal = if (collapsed) 14.dp else Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = if (collapsed) "记一笔" else null, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
        if (!collapsed) {
            Spacer(Modifier.width(Space.x1))
            Text("记一笔", style = JkType.bodyStrong, color = colors.onPrimary, modifier = Modifier.padding(end = Space.x05))
        }
    }
}

/** 月度汇总：本月支出（数字滚动）、收入 / 结余 / 日均，以及预算进度。 */
@Composable
fun MonthSummaryCard(ui: LedgerUi, onBudget: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    JkCard(modifier.fillMaxWidth().animateContentSize(tween(240)), padding = PaddingValues(Space.x2_5)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(colors.expense)
            Spacer(Modifier.width(Space.x1))
            Text(
                if (ui.filterActive) "筛选结果 · 支出" else "${ui.month.monthValue}月支出",
                style = JkType.overline,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(Space.x05))
        RollingAmount(ui.summary.expense, JkType.amountHero, colors.textPrimary)
        Spacer(Modifier.height(Space.x2))
        Row(Modifier.fillMaxWidth()) {
            SummaryCell("收入", ui.summary.income, colors.income, Modifier.weight(1f))
            SummaryCell(
                "结余",
                ui.summary.balance,
                if (ui.summary.balance < 0) colors.danger else colors.textPrimary,
                Modifier.weight(1f),
            )
            val average = ui.dailyAverage
            if (average != null && !ui.filterActive) {
                SummaryCell("日均支出", average, colors.textSecondary, Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        val budget = ui.totalBudget
        Spacer(Modifier.height(Space.x2))
        JkDivider()
        if (budget != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.chip)
                    .clickable(onClickLabel = "查看预算", onClick = onBudget)
                    .padding(top = Space.x1_5, bottom = Space.x05),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text("月预算 ${Money.format(budget.budget.amount)}", style = JkType.amountCaption, color = colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(
                        if (budget.over) "超支 ${Money.format(-budget.remaining)}" else "剩余 ${Money.format(budget.remaining)}",
                        style = JkType.amountCaption,
                        color = if (budget.over) colors.danger else colors.textSecondary,
                    )
                }
                Spacer(Modifier.height(Space.x1))
                BudgetBar(budget.ratio, budget.over)
            }
        } else {
            Text(
                "设置每月预算 ›",
                style = JkType.caption,
                color = colors.accent,
                modifier = Modifier
                    .padding(top = Space.x05)
                    .heightIn(min = 40.dp)
                    .clip(Radius.chip)
                    .clickable(role = Role.Button, onClick = onBudget)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = JkType.caption, color = Jk.colors.textTertiary)
        Spacer(Modifier.height(2.dp))
        RollingAmount(cents, JkType.amount, color)
    }
}

/** 按天分组的头：吸顶，左边“今天 / 10月9日 周四”，右边当日收支。 */
@Composable
private fun DayHeader(day: DayUi) {
    val colors = Jk.colors
    Column(Modifier.background(colors.bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.x2_5, end = Space.x2_5, top = Space.x2, bottom = Space.x1)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(day.label, style = JkType.bodyStrong, color = colors.textPrimary)
            Spacer(Modifier.width(Space.x1))
            Text(day.weekday, style = JkType.caption, color = colors.textTertiary, modifier = Modifier.padding(bottom = 2.dp))
            Spacer(Modifier.weight(1f))
            Text(day.totals, style = JkType.amountCaption, color = colors.textTertiary, modifier = Modifier.padding(bottom = 2.dp))
        }
        JkDivider(Modifier.padding(horizontal = Space.x2_5))
    }
}

/** 一笔流水：左边分类图标底，中间分类与时间 / 账户 / 备注，右边等宽数字的金额（右对齐）。 */
@Composable
private fun TransactionRow(row: TxRowUi, showDivider: Boolean, onClick: () -> Unit) {
    val colors = Jk.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = "修改这笔", onClick = onClick)
                .padding(horizontal = Space.x2_5, vertical = Space.x1_5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryChip(
                icon = if (row.type == TxType.TRANSFER) TransferIcon else categoryIcon(row.iconKey),
                type = row.type,
            )
            Spacer(Modifier.width(Space.x1_5))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = JkType.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(row.subtitle, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.x1_5))
            Text(
                text = row.amountText,
                style = JkType.amount,
                color = amountColor(row.type),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        if (showDivider) {
            // 分隔线从文字处开始，与图标底对齐
            JkDivider(Modifier.padding(end = Space.x2_5), inset = Space.x2_5 + JkSize.chip + Space.x1_5)
        }
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
