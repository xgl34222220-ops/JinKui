package com.xiguli.jinkui.ui.ledger

import com.xiguli.jinkui.ui.nav.transitionOrigin
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.MintGlyph
import com.xiguli.jinkui.ui.components.MintIcon
import com.xiguli.jinkui.ui.components.confirmHaptic
import com.xiguli.jinkui.ui.components.pressScale
import com.xiguli.jinkui.ui.components.rememberJkPress
import com.xiguli.jinkui.ui.nav.LocalTabBarInset
import com.xiguli.jinkui.ui.theme.JkFonts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.customActions
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.SwipeAction
import com.xiguli.jinkui.ui.components.SwipeActionsBox
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.ButtonStyle
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import com.xiguli.jinkui.ui.theme.jkSurface
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
                // 1.5.0-alpha03：改一笔已有的流水，保存后可以撤销（回到改之前的那一版）
                val previous = container.ledger.data.value.transactions.firstOrNull { it.id == tx.id }
                container.ledger.saveTransaction(tx)
                onDone()
                if (previous != null) {
                    container.messages.show("已修改", "撤销") {
                        container.scope.launch { runCatching { container.ledger.saveTransaction(previous) } }
                    }
                }
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

    /** 多选删除：一次删掉，底部消息条可以“撤销”。 */
    fun deleteMany(ids: List<String>) {
        viewModelScope.launch {
            runCatching { container.ledger.deleteTransactions(ids) }
                .onSuccess { gone ->
                    container.messages.show("已删除 ${gone.size} 笔", "撤销") {
                        container.scope.launch { runCatching { container.ledger.restoreTransactions(gone) } }
                    }
                }
                .onFailure { container.messages.show("删除失败：${it.message ?: "未知错误"}") }
        }
    }

    fun delete(tx: LedgerTransaction) {
        viewModelScope.launch {
            // 1.5.0-alpha03：删一笔也可以撤销（同多选删除）
            runCatching { container.ledger.deleteTransactions(listOf(tx.id)) }
                .onSuccess { gone ->
                    container.messages.show("已删除这笔记录", "撤销") {
                        container.scope.launch { runCatching { container.ledger.restoreTransactions(gone) } }
                    }
                }
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
    onAssets: () -> Unit = {},
    onImport: () -> Unit = {},
    onPending: () -> Unit = {},
    onDebts: () -> Unit = {},
    onReimburse: () -> Unit = {},
    onRules: () -> Unit = {},
) {
    val container = LocalContainer.current
    val pending by container.ledger.pending.collectAsStateWithLifecycle()
    val viewModel = jkViewModel { LedgerViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val data by container.ledger.data.collectAsStateWithLifecycle()
    // 1.4：3 天内到期或已逾期的信用账单与借贷（首页玻璃提示条）
    val debtToday = ui.today ?: java.time.LocalDate.now()
    val debtReminders = remember(data, debtToday) {
        debtReminders(data, debtToday, java.time.ZoneId.systemDefault())
    }

    val host = rememberEntryHost()
    var showMore by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }

    // 每次回到账本页补记到期的周期记账（例如解锁状态下跨过了午夜）。
    LaunchedEffect(Unit) { viewModel.runRecurring() }
    // 桌面快捷方式“记一笔”：打开记账面板
    val quickRecord by container.quickRecordRequests
    LaunchedEffect(quickRecord) {
        if (quickRecord > container.quickRecordHandled) {
            container.quickRecordHandled = quickRecord
            host.create()
        }
    }
    // 从后台回来时检查日期：跨了天 / 月就刷新（只有原本在看本月时才跳到新的本月）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onClockTick() }

    LedgerHome(
        ui = ui,
        filter = filter,
        listState = viewModel.listState,
        actions = host.actions(
            LedgerHomeActions(
                onMonth = viewModel::setMonth,
                onQuery = viewModel::setQuery,
                onPickAccount = { pickAccount = true },
                onPickCategory = { pickCategory = true },
                onClearFilters = viewModel::clearFilters,
                onStats = onStats,
                onBudget = onBudget,
                onLock = { container.vault.lock() },
                onMore = { showMore = true },
                onAssets = onAssets,
                onPending = onPending,
                onDebts = onDebts,
            ),
        ),
        pendingCount = pending.size,
        debtReminders = debtReminders,
        today = debtToday,
    )

    EntryHost(host, data, viewModel)

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
                Triple("资产总览", "净资产、负债与信用账户", Icons.Outlined.Savings) to onAssets,
                Triple("欠款 / 账单", "信用卡、花呗账单与分期，借出借入", Icons.Outlined.ReceiptLong) to onDebts,
                Triple("报销", "待报销与已报销；退款在长按一笔支出里", Icons.Outlined.RequestQuote) to onReimburse,
                Triple("规则与模板", "按关键词自动分类；常记的一笔一点就记", Icons.Outlined.AutoAwesome) to onRules,
                Triple("账户与余额", "现金、银行卡、支付宝、微信、信用卡", Icons.Outlined.AccountBalanceWallet) to onAccounts,
                Triple("预算", "每月总预算与分类预算", Icons.Outlined.BarChart) to onBudget,
                Triple("周期记账", "房租、工资、订阅等固定收支自动记上", Icons.Outlined.Repeat) to onRecurring,
                Triple("导入账单", "微信支付、支付宝账单或 CSV 表格", Icons.Outlined.ReceiptLong) to onImport,
                Triple("待确认" + if (pending.isNotEmpty()) "（${pending.size}）" else "", "自动记账从通知里识别出的收支", Icons.Outlined.Inbox) to onPending,
                Triple("分类管理", "增加、改名或隐藏分类", Icons.Outlined.Category) to onCategories,
                Triple("设置", "解锁方式、自动锁定、加密备份", Icons.Outlined.Settings) to onSettings,
                Triple("立即锁定", "清掉内存里的明文，回到解锁页", Icons.Outlined.Lock) to { container.vault.lock() },
            )
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
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
    /** 头部的“资产”：净资产与负债总览。 */
    val onAssets: () -> Unit = {},
    /** “常用”分类：点一个分类，直接打开已选好这个分类的记账面板。 */
    val onQuickAdd: (String) -> Unit = {},
    /** 右滑 / 长按菜单：照这一笔再记一笔（金额、分类、账户、备注带过去，时间是现在）。 */
    val onRepeat: (LedgerTransaction) -> Unit = {},
    /** 左滑 / 长按菜单：删除（由页面弹确认）。 */
    val onDelete: (LedgerTransaction) -> Unit = {},
    /** 长按一行：修改 / 再记一笔 / 删除。 */
    val onRowMenu: (LedgerTransaction) -> Unit = {},
    /** “有 n 笔待确认”提示条。 */
    val onPending: () -> Unit = {},
    /** 到期提示条（1.4）：打开欠款 / 账单。 */
    val onDebts: () -> Unit = {},
    /** 1.4.0-alpha04：“借出 / 借入”快捷入口，打开记账面板的借贷。 */
    val onLoan: () -> Unit = {},
    /** 多选模式（1.3）：选中的流水 ID；null 表示不在多选模式。长按一行进入。 */
    val selection: Set<String>? = null,
    val onSelectStart: (LedgerTransaction) -> Unit = {},
    val onSelectToggle: (LedgerTransaction) -> Unit = {},
    /** 一次选上 / 取消一批（本日、本月全选）。 */
    val onSelectMany: (List<String>, Boolean) -> Unit = { _, _ -> },
    val onSelectDone: () -> Unit = {},
    val onDeleteSelected: (List<LedgerTransaction>) -> Unit = {},
)

/**
 * 账本首页（无状态，1.1 薄荷）：标题栏（资产 / 搜索 / 更多）→ 带植物插画的月度头图（收入、结余、剩余预算、支出）
 * → 整行的“记一笔” → 资产 / 借钱·欠款 / 预算三个小入口 → 一行不抢眼的常用分类 → 月份与筛选 → 按天分组的流水（每天一块白色分组，头上是当天的支出 / 收入）。
 * 1.5.0-alpha01：月度卡先写支出，再写收入 / 结余 / 剩余预算（参考钱迹、一木记账首页）。
 * 日历挪到了底栏的“日历”分区。
 */
@Composable
fun LedgerHome(
    ui: LedgerUi,
    filter: TxFilter,
    listState: LazyListState,
    actions: LedgerHomeActions,
    modifier: Modifier = Modifier,
    /** 自动记账的待确认笔数；大于 0 时在“记一笔”下面出现一条提示。 */
    pendingCount: Int = 0,
    /** 1.4：3 天内到期或已逾期的欠款 / 账单；不为空时出现一条玻璃提示条。 */
    debtReminders: List<com.xiguli.jinkui.data.ledger.DueItem> = emptyList(),
    today: java.time.LocalDate = java.time.LocalDate.now(),
) {
    val colors = Jk.colors
    var showSearch by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = LocalTabBarInset.current + Space.x2),
    ) {
        val selection = actions.selection
        if (selection != null) {
            item(key = "selection", contentType = "selection") {
                SelectionBar(ui, selection, actions)
            }
        } else item(key = "header", contentType = "header") {
            PageHeader(
                title = "账本",
                glyph = MintGlyph.LEDGER,
                actions = {
                    MintIconButton(MintGlyph.ASSETS, "资产总览", actions.onAssets)
                    MintIconButton(MintGlyph.SEARCH, "搜索", { showSearch = !showSearch })
                    MintIconButton(MintGlyph.MORE, "更多", actions.onMore)
                },
            )
        }
        item(key = "summary", contentType = "summary") {
            MonthSummaryCard(ui, actions.onBudget, Modifier.padding(horizontal = Space.x2, vertical = Space.x05))
        }
        item(key = "add", contentType = "add") {
            AddEntryButton(actions.onCreate, Modifier.padding(start = Space.x2, end = Space.x2, top = Space.x1_5))
        }
        // 1.5.0-alpha01：两块借贷大按钮换成一排三个小入口（资产 / 借钱·欠款 / 预算），参考一木记账首页的数据小卡片；
        // 借出 / 借入在“记一笔”的“借贷”里
        if (selection == null) {
            item(key = "debt-shortcuts", contentType = "debt-shortcuts") {
                HomeShortcuts(actions.onAssets, actions.onDebts, actions.onBudget, Modifier.padding(start = Space.x2, end = Space.x2, top = Space.x1))
            }
        }
        if (pendingCount > 0) {
            item(key = "pending", contentType = "pending") {
                PendingBanner(pendingCount, actions.onPending, Modifier.padding(start = Space.x2, end = Space.x2, top = Space.x1))
            }
        }
        if (debtReminders.isNotEmpty() && selection == null) {
            item(key = "debts", contentType = "debts") {
                DebtBanner(debtReminders, today, actions.onDebts, Modifier.padding(start = Space.x2, end = Space.x2, top = Space.x1))
            }
        }
        if (ui.quickCategories.isNotEmpty() && !ui.filterActive) {
            item(key = "quick", contentType = "quick") {
                QuickAddRow(ui.quickCategories, actions.onQuickAdd)
            }
        }
        item(key = "filters", contentType = "filters") {
            Column(Modifier.animateContentSize(tween(220))) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Space.x05, end = Space.x2, top = Space.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthSwitcher(month = ui.month, onChange = actions.onMonth, latest = ui.latestMonth)
                    Spacer(Modifier.weight(1f))
                    FilterPill(
                        text = filter.accountId?.let { ui.accountsById[it]?.name } ?: "账户",
                        active = filter.accountId != null,
                        onClick = actions.onPickAccount,
                    )
                    Spacer(Modifier.width(Space.x05))
                    FilterPill(
                        text = filter.categoryId?.let { categoryLabel(it, ui.categoriesById) } ?: "分类",
                        active = filter.categoryId != null,
                        onClick = actions.onPickCategory,
                    )
                }
                if (showSearch || filter.query.isNotEmpty()) {
                    JkTextField(
                        value = filter.query,
                        onValueChange = actions.onQuery,
                        placeholder = "搜索备注或标签",
                        leading = Icons.Outlined.Search,
                        modifier = Modifier.padding(start = Space.x2, end = Space.x2, top = Space.x05),
                        trailing = {
                            JkIconButton(Icons.Outlined.Close, "清除", {
                                actions.onQuery("")
                                showSearch = false
                            })
                        },
                    )
                }
                if (ui.filterActive) {
                    Text(
                        "清除筛选",
                        style = JkType.small,
                        color = colors.accent,
                        modifier = Modifier
                            .padding(start = Space.x2)
                            .heightIn(min = JkSize.touch)
                            .clip(Radius.pill)
                            .clickable(role = Role.Button, onClick = actions.onClearFilters)
                            .padding(horizontal = Space.x1)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        }
        if (ui.unreadable > 0) {
            item(key = "unreadable", contentType = "unreadable") {
                Text(
                    "有 ${ui.unreadable} 条记录数据损坏，无法解密，已略过",
                    style = JkType.small,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = Space.x2, vertical = Space.x05),
                )
            }
        }
        if (ui.days.isEmpty() && !ui.loading) {
            item(key = "empty", contentType = "empty") {
                JkEmpty(
                    title = if (ui.filterActive) "没有符合条件的记录" else "这个月还没有记账",
                    message = if (ui.filterActive) "换个筛选条件试试" else "点上面的“记一笔”，选分类、输金额，三秒记完。",
                    illustration = { LedgerIllustration(if (ui.filterActive) LedgerArt.NO_MATCH else LedgerArt.EMPTY_MONTH) },
                )
            }
        }
        for (day in ui.days) {
            // 1.3：日期头不再吸顶（玻璃背景下吸顶条会挡住半透明的行），随列表滚动
            item(key = "d-${day.date}", contentType = "day") {
                DayHeader(day, selection = actions.selection, onSelectDay = { on -> actions.onSelectMany(day.rows.map { it.id }, on) })
            }
            dayRows(day, actions)
        }
        if (ui.days.isNotEmpty()) {
            item(key = "hint", contentType = "hint") {
                Text(
                    "右滑再记一笔，左滑删除，长按多选（可整天、整月删除）",
                    style = JkType.caption,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.x3, vertical = Space.x2),
                )
            }
        }
    }
}

/** 分区页的标题栏：左边一枚薄荷线条图标 + 粗黑体标题，右边几个图标按钮。 */
@Composable
fun PageHeader(
    title: String,
    glyph: MintGlyph?,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = Jk.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.x2, end = Space.x05, top = Space.x1_5, bottom = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            MintIcon(glyph, size = 30.dp, tint = colors.accent, accent = colors.primary, filled = true)
            Spacer(Modifier.width(Space.x1_5))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = JkType.pageTitle, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            subtitle?.invoke()
        }
        actions()
    }
}

/** 线条图标按钮：48dp 触控区，图标 24dp。 */
@Composable
fun MintIconButton(glyph: MintGlyph, description: String, onClick: () -> Unit, tint: Color = Jk.colors.textPrimary) {
    Box(
        Modifier
            .size(JkSize.touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        MintIcon(glyph, size = 24.dp, tint = tint)
    }
}

/** 筛选用的小胶囊：选中时浅薄荷底、深薄荷字。 */
@Composable
private fun FilterPill(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .heightIn(min = JkSize.touch)
            .clip(Radius.pill)
            .selectable(selected = active, role = Role.Button, onClick = onClick)
            .padding(vertical = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .jkSurface(if (active) Jk.surfaces.chipSelected else Jk.surfaces.chip, Radius.pill)
                .padding(horizontal = Space.x1_5, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = JkType.small,
                color = if (active) colors.accent else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = if (active) colors.accent else colors.textTertiary, modifier = Modifier.size(16.dp))
        }
    }
}

/** “自动记账有 n 笔待确认”：浅薄荷的一行，点进待确认。 */
@Composable
fun PendingBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = JkSize.touch)
            .clip(Radius.control)
            .background(colors.accentSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x2, vertical = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Inbox, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.x1_5))
        Text("自动记账有 $count 笔待确认", style = JkType.bodyStrong, color = colors.accent, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
    }
}

/** 整行的“记一笔”：宽玻璃按钮、薄荷色的加号与文字，按下轻微缩小（弹簧）并有触感。 */
@Composable
fun AddEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier, text: String = "记一笔") {
    val colors = Jk.colors
    val view = LocalView.current
    val press = rememberJkPress(0.97f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressScale(press)
            .jkSurface(Jk.surfaces.chipSelected, Radius.card)
            .clickable(interactionSource = press.interaction, indication = LocalIndication.current, role = Role.Button) {
                view.confirmHaptic()
                onClick()
            }
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(Space.x1_5))
        Text(text, style = JkType.heading.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = colors.accent)
    }
}

/**
 * 1.5.0-alpha01：首页“记一笔”下面一排三个小入口——资产（每个账户有多少钱）、借钱 / 欠款、预算。
 * 参考一木记账首页的数据小卡片与钱迹左滑进资产，只借鉴交互。
 */
@Composable
fun HomeShortcuts(onAssets: () -> Unit, onDebts: () -> Unit, onBudget: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
        MiniShortcut(MintGlyph.ASSETS, "资产", onAssets, Modifier.weight(1f))
        MiniShortcut(MintGlyph.BILL, "借钱 / 欠款", onDebts, Modifier.weight(1f))
        MiniShortcut(MintGlyph.BUDGET, "预算", onBudget, Modifier.weight(1f))
    }
}

@Composable
private fun MiniShortcut(glyph: MintGlyph, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val press = rememberJkPress(0.96f)
    Row(
        modifier
            .heightIn(min = JkSize.touch)
            .pressScale(press)
            .jkSurface(Jk.surfaces.card, Radius.pill)
            .clickable(interactionSource = press.interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x1, vertical = Space.x1),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MintIcon(glyph, size = 20.dp, tint = colors.accent)
        Spacer(Modifier.width(6.dp))
        Text(title, style = JkType.small.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** 一天的流水：同一天的几笔放在一块白底分组里。 */
internal fun androidx.compose.foundation.lazy.LazyListScope.dayRows(day: DayUi, actions: LedgerHomeActions) {
    val selection = actions.selection
    itemsIndexed(day.rows, key = { _, row -> row.id }, contentType = { _, _ -> "tx" }) { index, row ->
        TransactionRow(
            row = row,
            shape = rowShape(index, day.rows.size),
            showDivider = index < day.rows.lastIndex,
            onClick = { if (selection != null) actions.onSelectToggle(row.tx) else actions.onOpen(row.tx) },
            // 1.3：长按进入多选（修改 = 点按，再记一笔 = 右滑，删除 = 左滑，读屏另有自定义操作）
            onLongClick = { if (selection != null) actions.onSelectToggle(row.tx) else actions.onSelectStart(row.tx) },
            onRepeat = { actions.onRepeat(row.tx) },
            onDelete = { actions.onDelete(row.tx) },
            selected = selection?.let { row.id in it },
        )
    }
}

/** 多选模式的标题栏：已选几笔、本月全选、删除、完成。 */
@Composable
private fun SelectionBar(ui: LedgerUi, selection: Set<String>, actions: LedgerHomeActions) {
    val colors = Jk.colors
    val monthIds = remember(ui.days) { ui.days.flatMap { d -> d.rows.map { it.id } } }
    val allMonth = monthIds.isNotEmpty() && selection.containsAll(monthIds)
    androidx.activity.compose.BackHandler(onBack = actions.onSelectDone)
    Column(Modifier.fillMaxWidth().padding(start = Space.x2, end = Space.x2, top = Space.x1_5, bottom = Space.x1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "已选 ${selection.size} 笔",
                style = JkType.title.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            JkChip("完成", selected = false, onClick = actions.onSelectDone)
        }
        Spacer(Modifier.height(Space.x1))
        Row(verticalAlignment = Alignment.CenterVertically) {
            JkChip(if (allMonth) "取消本月全选" else "本月全选（${monthIds.size}）", selected = allMonth, onClick = { actions.onSelectMany(monthIds, !allMonth) })
            Spacer(Modifier.weight(1f))
            JkButton(
                text = "删除 ${selection.size} 笔",
                onClick = {
                    val chosen = ui.days.flatMap { d -> d.rows.filter { it.id in selection }.map { it.tx } }
                    actions.onDeleteSelected(chosen)
                },
                style = ButtonStyle.Danger,
                compact = true,
                enabled = selection.isNotEmpty(),
            )
        }
    }
}

internal fun rowShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val r = 22.dp
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
        index == count - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}

/** 常用分类：一行小号文字胶囊，不抢“记一笔”的风头；点一下带着这个分类打开记账面板。 */
@Composable
private fun QuickAddRow(categories: List<LedgerCategory>, onPick: (String) -> Unit) {
    val colors = Jk.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.x2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Space.x05),
    ) {
        item(key = "label", contentType = "label") {
            Text("常用", style = JkType.caption, color = colors.textTertiary, modifier = Modifier.padding(end = Space.x05).semantics { heading() })
        }
        items(categories, key = { it.id }, contentType = { "ledgerscreen-row" }) { category ->
            Box(
                Modifier
                    .heightIn(min = JkSize.touch)
                    .clip(Radius.pill)
                    .clickable(role = Role.Button, onClickLabel = "记一笔${category.name}") { onPick(category.id) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .jkSurface(Jk.surfaces.chip, Radius.pill)
                        .padding(start = Space.x1, end = Space.x1_5, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(categoryIcon(category.icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(category.name, style = JkType.small, color = colors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** 月度头图：浅薄荷底、右下角一丛叶子与小花；上排收入 / 结余 / 剩余预算，下面是大号的支出。 */
@Composable
fun MonthSummaryCard(ui: LedgerUi, onBudget: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val current = ui.today?.let { YearMonth.from(it) } == ui.month
    val prefix = when {
        ui.filterActive -> "筛选"
        current -> "本月"
        else -> "${ui.month.monthValue}月"
    }
    val roomy = androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f
    BotanicalCard(modifier.fillMaxWidth().animateContentSize(tween(240)), layout = BotanicalLayout.TOP_RIGHT) {
        Column(Modifier.padding(horizontal = Space.x2_5, vertical = Space.x2_5)) {
            // 1.5.0-alpha01：先回答“花了多少”（参考钱迹首页：月收入 / 月支出 / 本月结余），再是收入、结余、剩余预算
            Text("${prefix}支出(元)", style = JkType.small.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
            Spacer(Modifier.height(2.dp))
            RollingAmount(ui.summary.expense, JkType.amountHero, colors.accent, format = { Money.format(it, symbol = false) })
            val average = ui.dailyAverage?.takeIf { !ui.filterActive && it > 0 }
            if (average != null) {
                Text("平均每天 ${Money.format(average, symbol = false)}", style = JkType.amountCaption, color = colors.textSecondary)
            }
            Spacer(Modifier.height(Space.x2))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                SummaryCell("${prefix}收入", ui.summary.income, colors.textPrimary, Modifier.weight(1f))
                SummaryCell("${prefix}结余", ui.summary.balance, if (ui.summary.balance < 0) colors.danger else colors.textPrimary, Modifier.weight(1f))
                if (roomy) BudgetCell(ui, onBudget, Modifier.weight(1f))
            }
            if (!roomy) {
                Spacer(Modifier.height(Space.x1))
                BudgetCell(ui, onBudget)
            }
            Spacer(Modifier.height(Space.x05))
            Text("结余 = 收入 − 支出", style = JkType.caption, color = colors.textTertiary)
        }
    }
}

@Composable
private fun SummaryCell(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = JkType.small.copy(fontWeight = FontWeight.Medium), color = Jk.colors.textSecondary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        RollingAmount(cents, JkType.amount.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold), color, format = { Money.format(it, symbol = false) })
    }
}

/** 剩余预算：没设时写“点击设置”。整格可点，进预算页。 */
@Composable
private fun BudgetCell(ui: LedgerUi, onBudget: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val budget = ui.totalBudget
    Column(
        modifier
            .clip(Radius.tile)
            .clickable(role = Role.Button, onClickLabel = if (budget == null) "设置预算" else "查看预算", onClick = onBudget),
    ) {
        Text(if (budget?.over == true) "超支" else "剩余预算", style = JkType.small.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        if (budget == null) {
            Text("点击设置", style = JkType.heading.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1)
        } else {
            RollingAmount(
                if (budget.over) -budget.remaining else budget.remaining,
                JkType.amount.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                if (budget.over) colors.danger else colors.textPrimary,
                format = { Money.format(it, symbol = false) },
            )
            Spacer(Modifier.height(4.dp))
            BudgetBar(budget.ratio, budget.over, Modifier.width(88.dp))
        }
    }
}

/** 按天分组的头：吸顶，左边“今天 · 周六”，右边当日“收 / 支”（收薄荷、支红）。 */
@Composable
internal fun DayHeader(day: DayUi, selection: Set<String>? = null, onSelectDay: (Boolean) -> Unit = {}) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x2_5, end = Space.x2_5, top = Space.x2, bottom = Space.x1)
            .semantics(mergeDescendants = selection == null) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selection != null) {
            val all = day.rows.isNotEmpty() && day.rows.all { it.id in selection }
            Box(
                Modifier
                    .size(JkSize.touch)
                    .clip(CircleShape)
                    .clickable(role = Role.Checkbox, onClickLabel = if (all) "取消本日全选" else "本日全选") { onSelectDay(!all) }
                    .semantics { contentDescription = if (all) "本日已全选" else "本日全选" },
                contentAlignment = Alignment.Center,
            ) { com.xiguli.jinkui.ui.components.JkCheck(all) }
        }
        Text(day.label, style = JkType.heading.copy(fontWeight = FontWeight.Bold, fontFamily = JkFonts.numeral), color = colors.textPrimary, maxLines = 1)
        Spacer(Modifier.width(Space.x1))
        Text(day.weekday, style = JkType.small, color = colors.textTertiary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        // 1.5.0-alpha01：写全“支出 / 收入”，为 0 的一项不显示（参考钱迹每天单独汇总）
        if (day.expense != 0L || day.income == 0L) {
            Text("支出", style = JkType.small, color = colors.textTertiary)
            Spacer(Modifier.width(4.dp))
            Text(Money.format(day.expense, symbol = false), style = JkType.amountSmall, color = colors.expense, maxLines = 1)
        }
        if (day.income != 0L) {
            Spacer(Modifier.width(Space.x1_5))
            Text("收入", style = JkType.small, color = colors.textTertiary)
            Spacer(Modifier.width(4.dp))
            Text(Money.format(day.income, symbol = false), style = JkType.amountSmall, color = colors.income, maxLines = 1)
        }
    }
}

/**
 * 一笔流水：左边线条分类图标，中间粗体分类、下面数字字体的时间与备注，右边金额、下面账户。
 * 点 → 修改；长按 → 菜单；右滑 → 再记一笔；左滑 → 删除（页面会先确认）。
 */
@Composable
internal fun TransactionRow(
    row: TxRowUi,
    shape: androidx.compose.ui.graphics.Shape,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
    /** 多选模式下是否选中；null 表示不在多选模式（多选时不能滑动）。 */
    selected: Boolean? = null,
) {
    val colors = Jk.colors
    val haptic = LocalHapticFeedback.current
    // 不用 clip：圆角只画在底色上（clip 会让触摸命中检测依赖圆角轮廓）
    Box(Modifier.padding(horizontal = Space.x2).transitionOrigin()) {
        SwipeActionsBox(
            start = if (selected == null) SwipeAction("再记一笔", Icons.Outlined.ContentCopy, colors.accentSoft, colors.accent, onRepeat) else null,
            end = if (selected == null) SwipeAction("删除", Icons.Outlined.DeleteOutline, colors.danger, Color.White, onDelete) else null,
            shape = shape,
        ) {
            Column(Modifier.background(colors.surface, shape)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .combinedClickable(
                            onClickLabel = if (selected == null) "修改这笔" else if (selected) "取消选择" else "选择",
                            onClick = onClick,
                            onLongClickLabel = if (selected == null) "多选" else null,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongClick()
                            },
                        )
                        .semantics {
                            if (selected == null) {
                                customActions = listOf(
                                    CustomAccessibilityAction("再记一笔") { onRepeat(); true },
                                    CustomAccessibilityAction("删除") { onDelete(); true },
                                )
                            } else {
                                stateDescription = if (selected) "已选中" else "未选中"
                            }
                        }
                        .padding(horizontal = Space.x2, vertical = Space.x1_5),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected != null) {
                        Box(Modifier.size(JkSize.chip), contentAlignment = Alignment.Center) { com.xiguli.jinkui.ui.components.JkCheck(selected) }
                    } else Box(
                        Modifier
                            .size(JkSize.chip)
                            .then(
                                if (row.type == TxType.EXPENSE) Modifier.jkSurface(Jk.surfaces.chipSelected, CircleShape)
                                else Modifier.clip(CircleShape).background(txSoft(row.type)),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (row.type == TxType.TRANSFER) TransferIcon else categoryIcon(row.iconKey),
                            contentDescription = null,
                            tint = if (row.type == TxType.EXPENSE) colors.textPrimary else txColor(row.type),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(Space.x1_5))
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = JkType.bodyStrong.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.time.ifEmpty { row.subtitle.take(5) }, style = JkType.date, color = colors.textTertiary, maxLines = 1)
                            if (row.detail.isNotEmpty()) {
                                Spacer(Modifier.width(Space.x1))
                                Text(row.detail, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.width(Space.x1_5))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = row.amountText,
                            style = JkType.amount,
                            color = amountColor(row.type),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                        if (row.where.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(row.where, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                        }
                    }
                }
                if (showDivider) {
                    // 分隔线从文字处开始，与图标底对齐
                    JkDivider(inset = Space.x2 + JkSize.chip + Space.x1_5)
                }
            }
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
                            items(children, key = { it.id }, contentType = { "ledgerscreen-row" }) { child ->
                                JkChip(child.name, false, { onPick(child.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
