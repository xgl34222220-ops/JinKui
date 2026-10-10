package com.xiguli.jinkui.ui.ledger

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
                        .background(Jk.colors.bg),
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
        compose.onNodeWithText("10月支出").assertExists()
        // 月度支出：¥32.50 + ¥18.00 + ¥3,800.00 + ¥129.90 + ¥68.00
        compose.onNodeWithText("¥4,048.40").assertExists()
        compose.onNodeWithText("今天").assertExists()
        compose.onNodeWithText("-¥32.50").assertExists()
        compose.onNodeWithText("餐饮 · 午餐").assertExists()
        compose.onNodeWithText("快速记一笔").assertExists()
        compose.onNodeWithText("明细").assertExists()
        snapshot(activity, "ledger-home-light")
    }

    @Test
    fun quickAddPicksMostUsedCategoryFirst() {
        var picked: String? = null
        show { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions(onQuickAdd = { picked = it })) }
        compose.onNodeWithText("快速记一笔").assertExists()
        // 点“快速记一笔”里的“餐饮”胶囊
        compose.onAllNodesWithText("餐饮").onFirst().performClick()
        assertEquals("cat-food", picked)
    }

    @Test
    fun ledgerCalendarLightAndDark() {
        val light = show { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions(), initialCalendar = true) }
        compose.onNodeWithContentDescription("10月10日，今天，支 ¥50.50").assertExists()
        // 默认选中今天，下面列出今天的流水（真实字体下日历更高，流水可能在首屏之外，先滚过去）
        scrollList(androidx.compose.ui.test.hasText("餐饮 · 午餐"))
        compose.onNodeWithText("餐饮 · 午餐").assertExists()
        snapshot(light, "ledger-calendar-light")
        scrollList(androidx.compose.ui.test.hasContentDescription("10月8日，支 ¥3,800.00"))
        compose.onNodeWithContentDescription("10月8日，支 ¥3,800.00").performClick()
        compose.waitForIdle()
        scrollList(androidx.compose.ui.test.hasText("居住 · 房租房贷"))
        compose.onNodeWithText("居住 · 房租房贷").assertExists()
        val dark = show(dark = true) { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions(), initialCalendar = true) }
        snapshot(dark, "ledger-calendar-dark")
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
            compose.onNodeWithText("¥4,048.40").assertExists()
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

    @Test
    fun homeTabBarCenterButtonFollowsTab() {
        val added = mutableListOf<com.xiguli.jinkui.ui.nav.HomeTab>()
        val activity = show {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                HomeTabBar(com.xiguli.jinkui.ui.nav.HomeTab.LEDGER, {}, { added += it })
            }
        }
        compose.onNodeWithContentDescription("记一笔").performClick()
        assertEquals(listOf(com.xiguli.jinkui.ui.nav.HomeTab.LEDGER), added)
        snapshot(activity, "home-tab-bar")
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
    fun filterChipKeepsVisualSizeWith48dpTouchTarget() {
        show { LedgerHome(homeUi(), TxFilter(), LazyListState(), LedgerHomeActions()) }
        compose.onNodeWithText("全部账户").assertHeightIsEqualTo(32.dp)
        compose.onNodeWithText("全部账户").assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun statsLightAndDark() {
        val stats = buildStatsUi(LedgerSamples.data, october, TxType.EXPENSE, LedgerSamples.zone)
        val light = show { LedgerStatsContent(stats, {}, {}, {}) }
        compose.onNodeWithText("合计").assertExists()
        compose.onNodeWithText("居住").assertExists()
        snapshot(light, "ledger-stats-light")
        val dark = show(dark = true) { LedgerStatsContent(stats, {}, {}, {}) }
        snapshot(dark, "ledger-stats-dark")
    }

    @Test
    fun statsEmpty() {
        val stats = buildStatsUi(LedgerData(categories = LedgerDefaults.categories()), october, TxType.INCOME, LedgerSamples.zone)
        val activity = show { LedgerStatsContent(stats, {}, {}, {}) }
        compose.onNodeWithText("这个月还没有收入记录").assertExists()
        snapshot(activity, "ledger-stats-empty")
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
