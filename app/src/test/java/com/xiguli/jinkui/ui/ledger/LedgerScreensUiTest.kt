package com.xiguli.jinkui.ui.ledger

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
import androidx.compose.ui.test.performClick
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
    @get:Rule
    val compose = createEmptyComposeRule()

    private val october = YearMonth.of(2026, 10)

    private fun homeUi(data: LedgerData = LedgerSamples.data, filter: TxFilter = TxFilter()) =
        buildLedgerUi(data, october, filter, LedgerSamples.zone, LedgerSamples.today)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
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
        compose.onNodeWithText("记一笔").assertExists()
        snapshot(activity, "ledger-home-light")
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
