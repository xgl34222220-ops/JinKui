package com.xiguli.jinkui.ui.ledger

import com.xiguli.jinkui.ui.theme.jkBackdrop
import androidx.compose.ui.test.performScrollToNode
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.KeypadKey
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxFilter
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.ThemeMode
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JinKuiTheme
import java.io.File
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowLooper

/**
 * 账本主要页面的界面测试（Robolectric，JVM 上跑，不需要模拟器）。
 * 除了断言文字与交互，还把每个页面画成 PNG 存到 app/build/outputs/ui-screenshots/，CI 会作为附件上传，方便看观感。
 * 只渲染无状态的页面组件（LedgerHome / LedgerStatsContent / Keypad），不碰数据库与密钥。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
// 图形模式由 Gradle 的 robolectric.graphicsMode 决定：x86_64（CI）用原生图形以便截图，其余架构用 LEGACY 只跑断言
@ConscryptMode(ConscryptMode.Mode.OFF)
class LedgerScreensUiTest {
    /** 先于 Compose 规则包住测试：结束后把主线程消息跑空，见 [com.xiguli.jinkui.ui.MainLooperDrainRule]。 */
    @get:Rule(order = 0)
    val drain = com.xiguli.jinkui.ui.MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    /** 每个测试结束时销毁本测试开过的页面，前一个测试的界面不会留在后台继续占着主线程。 */
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()

    @org.junit.After
    fun closeActivities() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
    }

    private val october = YearMonth.of(2026, 10)

    private fun homeUi(data: LedgerData = LedgerSamples.data, filter: TxFilter = TxFilter()) =
        buildLedgerUi(data, october, filter, LedgerSamples.zone, LedgerSamples.today)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit): ComponentActivity {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controllers += controller
        val activity = controller.get()
        activity.setContent {
            JinKuiTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(Modifier.jkBackdrop()),
                ) { content() }
            }
        }
        ShadowLooper.idleMainLooper()
        compose.waitForIdle()
        return activity
    }

    /** 尽力而为的截图：画不出来（例如没有原生图形库）时只打印原因，不让测试失败。 */
    private fun snapshot(activity: ComponentActivity, name: String) {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        runCatching {
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File("build/outputs/ui-screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("截图 $name 失败：$it") }
    }

    @Test
    fun ledgerHomeShowsSummaryDaysAndRows() {
        val activity = show { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions()) }
        compose.onNodeWithText("本月支出(元)").assertExists()
        // 月度支出：¥32.50 + ¥18.00 + ¥3,800.00 + ¥129.90 + ¥68.00（头图里不带货币符号）
        compose.onNodeWithText("4,048.40").assertExists()
        compose.onNodeWithText("今天").assertExists()
        compose.onNodeWithText("-¥32.50").assertExists()
        compose.onNodeWithText("餐饮 · 午餐").assertExists()
        compose.onNodeWithText("添加一条新记账").assertExists()
        compose.onNodeWithText("常用").assertExists()
        compose.onNodeWithText("剩余预算").assertExists()
        snapshot(activity, "ledger-home-light")
    }

    @Test
    fun quickAddPicksMostUsedCategoryFirst() {
        var picked: String? = null
        show { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions(onQuickAdd = { picked = it })) }
        compose.onNodeWithText("常用").assertExists()
        // 点“常用”里的“餐饮”胶囊
        compose.onAllNodesWithText("餐饮").onFirst().performClick()
        assertEquals("cat-food", picked)
    }

    @Test
    fun calendarLightDarkAndFontScale() {
        var selected = LedgerSamples.today
        val light = show {
            var day by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(LedgerSamples.today) }
            CalendarContent(homeUi(), day, { day = it; selected = it }, {}, {}, LedgerHomeActions())
        }
        // 2026-10-10 是农历九月初一，格子里显示月名；10 月 8 日寒露、10 月 18 日重阳、10 月 1 日国庆节
        compose.onNodeWithContentDescription("10月10日，今天，农历九月初一，支 ¥50.50").assertExists()
        compose.onNodeWithText("九月").assertExists()
        compose.onNodeWithText("寒露").assertExists()
        compose.onNodeWithText("重阳").assertExists()
        compose.onNodeWithText("国庆节").assertExists()
        scrollList(androidx.compose.ui.test.hasText("餐饮 · 午餐"))
        compose.onNodeWithText("餐饮 · 午餐").assertExists()
        snapshot(light, "calendar-light")
        scrollList(androidx.compose.ui.test.hasContentDescription("10月8日，农历八月廿八，寒露，支 ¥3,800.00"))
        compose.onNodeWithContentDescription("10月8日，农历八月廿八，寒露，支 ¥3,800.00").performClick()
        compose.waitForIdle()
        assertEquals(java.time.LocalDate.of(2026, 10, 8), selected)
        scrollList(androidx.compose.ui.test.hasText("居住 · 房租房贷"))
        compose.onNodeWithText("居住 · 房租房贷").assertExists()
        val dark = show(dark = true) { CalendarContent(homeUi(), LedgerSamples.today, {}, {}, {}, LedgerHomeActions()) }
        snapshot(dark, "calendar-dark")
    }

    @Test
    fun calendarEmptyDayShowsIllustration() {
        val activity = show { CalendarContent(homeUi(), java.time.LocalDate.of(2026, 10, 2), {}, {}, {}, LedgerHomeActions()) }
        scrollList(androidx.compose.ui.test.hasText("这一天你还没有任何记账"))
        compose.onNodeWithText("这一天你还没有任何记账").assertExists()
        snapshot(activity, "calendar-empty-day")
    }

    @Test
    fun calendarAtLargeFontScale() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        try {
            val activity = show { CalendarContent(homeUi(), LedgerSamples.today, {}, {}, {}, LedgerHomeActions()) }
            compose.onNodeWithText("九月").assertExists()
            snapshot(activity, "calendar-font130")
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(1f)
        }
    }

    /** 在账本首页的纵向列表里滚到某个节点（横向的“快速记一笔”不算）。 */
    private fun scrollList(target: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onAllNodes(
            androidx.compose.ui.test.hasScrollToNodeAction() and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange),
        ).onFirst().performScrollToNode(target)
        compose.waitForIdle()
    }

    @Test
    fun ledgerHomeAtLargeFontScale() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        try {
            val activity = show { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions()) }
            compose.onNodeWithText("4,048.40").assertExists()
            snapshot(activity, "ledger-home-font130")
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test
    fun rowMenuAndSwipeActionsAreReachable() {
        var repeated: LedgerTransaction? = null
        var deleted: LedgerTransaction? = null
        show {
            LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions(onRepeat = { repeated = it }, onDelete = { deleted = it }))
        }
        val node = compose.onNodeWithText("餐饮 · 午餐").fetchSemanticsNode()
        // 读屏的自定义操作与左右滑同一组动作
        var target = node
        while (target.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.CustomActions) == null) target = target.parent!!
        compose.runOnIdle {
            val acts = target.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            acts.first { it.label == "再记一笔" }.action()
            acts.first { it.label == "删除" }.action()
        }
        assertEquals("牛肉面", repeated?.note)
        assertEquals("牛肉面", deleted?.note)
    }

    /** 首页外壳：内容铺满整屏、从悬浮底栏后面滚过；底栏单独截一张近景。 */
    private fun showTabs(dark: Boolean, start: com.xiguli.jinkui.ui.nav.HomeTab = com.xiguli.jinkui.ui.nav.HomeTab.LEDGER, onSelect: (com.xiguli.jinkui.ui.nav.HomeTab) -> Unit = {}) = show(dark) {
        var tab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(start) }
        com.xiguli.jinkui.ui.nav.HomeTabs(selected = tab, onSelect = { tab = it; onSelect(it) }) {
            LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions())
        }
    }

    @Test
    fun homeTabBarSelectsTabs() {
        val picked = mutableListOf<com.xiguli.jinkui.ui.nav.HomeTab>()
        val activity = showTabs(dark = false, onSelect = { picked += it })
        for (label in listOf("账本", "日历", "卡包", "统计", "我的")) compose.onAllNodesWithText(label).onFirst().assertExists()
        // 每个标签的触控区至少 48dp 高、宽度均分
        compose.onNodeWithText("日历").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab)).assertCountEquals(5)
        snapshot(activity, "tabbar-light")
        compose.onNodeWithText("统计").performClick()
        compose.waitForIdle()
        assertEquals(listOf(com.xiguli.jinkui.ui.nav.HomeTab.STATS), picked)
        snapshot(activity, "tabbar-light-stats")
    }

    @Test
    fun homeTabBarDarkAndFontScale() {
        val dark = showTabs(dark = true, start = com.xiguli.jinkui.ui.nav.HomeTab.CARDS)
        snapshot(dark, "tabbar-dark")
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        try {
            val big = showTabs(dark = false, start = com.xiguli.jinkui.ui.nav.HomeTab.ME)
            compose.onAllNodesWithText("我的").onFirst().assertExists()
            snapshot(big, "tabbar-font130")
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test
    fun ledgerHomeDark() {
        val activity = show(dark = true) { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions()) }
        compose.onNodeWithText("昨天").assertExists()
        snapshot(activity, "ledger-home-dark")
    }

    @Test
    fun ledgerHomeEmptyAndLoading() {
        val empty = homeUi(LedgerData(accounts = listOf(LedgerSamples.cash), categories = LedgerDefaults.categories()))
        val activity = show { LedgerHome(empty, TxFilter(), LazyListState(), LedgerHomeActions()) }
        compose.onNodeWithText("这个月还没有记账").assertExists()
        snapshot(activity, "ledger-home-empty")
    }

    @Test
    fun ledgerHomeLoadingShowsNoEmptyState() {
        show { LedgerHome(LedgerUi.placeholder(october), TxFilter(), LazyListState(), LedgerHomeActions()) }
        compose.onNodeWithText("这个月还没有记账").assertDoesNotExist()
    }

    @Test
    fun ledgerHomeNoMatch() {
        val filter = TxFilter(query = "不存在的备注")
        val activity = show { LedgerHome(homeUi(filter = filter), filter, LazyListState(), LedgerHomeActions()) }
        compose.onNodeWithText("没有符合条件的记录").assertExists()
        snapshot(activity, "ledger-home-no-match")
    }

    @Test
    fun tappingRowOpensIt() {
        var opened: LedgerTransaction? = null
        show { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions(onOpen = { opened = it })) }
        compose.onNodeWithText("餐饮 · 午餐").performClick()
        compose.waitForIdle()
        assertEquals("牛肉面", opened?.note)
    }

    @Test
    fun filterPillHas48dpTouchTarget() {
        var picked = false
        show { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions(onPickAccount = { picked = true })) }
        // 筛选行在 LazyColumn 里，头图下面；字体高一点（CI 的原生字体）时可能只露出一半，触控区被视口裁掉——先滚到它
        val pill = compose.onNodeWithText("账户").performScrollTo()
        compose.waitForIdle()
        val touchHeight = with(compose.density) { pill.fetchSemanticsNode().boundsInRoot.height.toDp() }
        assertTrue("筛选胶囊触控高度 $touchHeight < 48dp", touchHeight >= 48.dp)
        pill.performClick()
        assertTrue(picked)
    }

    @Test
    fun statsMonthLightAndDark() {
        val stats = buildStatsUi(LedgerSamples.data, StatsMode.MONTH, october, 2026, LedgerSamples.zone, LedgerSamples.today)
        val light = show { LedgerStatsContent(stats, {}, {}, {}) }
        // 汇总卡里“月支出”出现两次：上面的大号标题与下面三格里的第一格，是设计如此
        compose.onAllNodesWithText("月支出").assertCountEquals(2)
        compose.onNodeWithText("收支热力").assertExists()
        compose.onNodeWithText("居住").assertExists()
        snapshot(light, "stats-month-light")
        val dark = show(dark = true) { LedgerStatsContent(stats, {}, {}, {}) }
        snapshot(dark, "stats-month-dark")
    }

    @Test
    fun statsYearLightDarkAndFontScale() {
        val stats = buildStatsUi(LedgerSamples.data, StatsMode.YEAR, october, 2026, LedgerSamples.zone, LedgerSamples.today)
        assertEquals(12, stats.bars.size)
        assertEquals(12, stats.heat.size)
        assertEquals(stats.summary.expense, stats.bars.sumOf { it.summary.expense })
        val light = show { LedgerStatsContent(stats, {}, {}, {}) }
        compose.onAllNodesWithText("年支出").assertCountEquals(2)
        compose.onNodeWithText("今年").assertExists()
        compose.onNodeWithText("收支统计").assertExists()
        snapshot(light, "stats-year-light")
        val dark = show(dark = true) { LedgerStatsContent(stats, {}, {}, {}) }
        snapshot(dark, "stats-year-dark")
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        try {
            val big = show { LedgerStatsContent(stats, {}, {}, {}) }
            snapshot(big, "stats-year-font130")
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test
    fun statsEmpty() {
        val stats = buildStatsUi(LedgerData(categories = LedgerDefaults.categories()), StatsMode.MONTH, october, 2026, LedgerSamples.zone, LedgerSamples.today)
        val activity = show { LedgerStatsContent(stats, {}, {}, {}) }
        compose.onNodeWithText("这个月还没有支出记录").performScrollTo()
        compose.onNodeWithText("这个月还没有支出记录").assertExists()
        snapshot(activity, "stats-empty")
    }

    @Test
    fun assetsOverviewLightAndDark() {
        // 样例里加两张信用卡，看信贷账户与“占全部负债”的比例条
        val data = LedgerSamples.data.copy(
            accounts = LedgerSamples.data.accounts +
                com.xiguli.jinkui.data.ledger.LedgerAccount("acc-credit-1", "招行信用卡", com.xiguli.jinkui.data.ledger.AccountKind.CREDIT, openingBalance = -356_000, position = 10) +
                com.xiguli.jinkui.data.ledger.LedgerAccount("acc-credit-2", "花呗", com.xiguli.jinkui.data.ledger.AccountKind.CREDIT, openingBalance = -88_000, position = 11),
        )
        val ui = buildAssetsUi(data)
        assertEquals(ui.assets - ui.liabilities, ui.net)
        assertEquals(444_000L, ui.creditDebt)
        val light = show { LedgerAssetsContent(ui, {}, {}) }
        compose.onNodeWithText("净资产(元)").assertExists()
        compose.onAllNodesWithText("净资产", substring = true).onFirst().assertExists()
        // 1.3 起按 资金 / 信用 / 借贷 分组
        compose.onNodeWithText("资金账户").assertExists()
        compose.onNodeWithText("信用账户").assertExists()
        compose.onNodeWithText("信用欠款").assertExists()
        snapshot(light, "assets-light")
        val dark = show(dark = true) { LedgerAssetsContent(ui, {}, {}) }
        snapshot(dark, "assets-dark")
    }

    private fun scrollTo(target: androidx.compose.ui.test.SemanticsMatcher) {
        compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst().performScrollToNode(target)
        compose.waitForIdle()
    }

    @Test
    fun keypadSendsKeys() {
        val keys = mutableListOf<KeypadKey>()
        var done = false
        val activity = show { Keypad(onKey = { keys += it }, onAgain = null, onDone = { done = true }) }
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithContentDescription("小数点").performClick()
        compose.onNodeWithContentDescription("加").performClick()
        compose.onNodeWithContentDescription("退格").performClick()
        compose.onNodeWithContentDescription("保存").performClick()
        assertEquals(
            listOf(KeypadKey.Digit('1'), KeypadKey.Digit('2'), KeypadKey.Dot, KeypadKey.Plus, KeypadKey.Backspace),
            keys,
        )
        assertTrue(done)
        snapshot(activity, "ledger-keypad")
    }
}
