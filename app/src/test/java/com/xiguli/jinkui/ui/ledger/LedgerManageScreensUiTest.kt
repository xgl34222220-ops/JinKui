package com.xiguli.jinkui.ui.ledger

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xiguli.jinkui.data.ledger.LedgerBudget
import com.xiguli.jinkui.data.ledger.LedgerRecurring
import com.xiguli.jinkui.data.ledger.RecurFrequency
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.ThemeMode
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JinKuiTheme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowLooper

/**
 * 账本的管理页（账户与余额、预算、周期记账）的界面测试与截图。
 * 只渲染无状态的 *Content 组件，不碰数据库与密钥。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class LedgerManageScreensUiTest {
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
    fun accountsListShowsNetWorthAndBalances() {
        val ui = buildAccountsUi(LedgerSamples.data)
        var edited: String? = null
        val activity = show { LedgerAccountsContent(ui, emptyList(), {}, {}, { edited = it.id }) }
        compose.onNodeWithText("净资产").assertExists()
        compose.onNodeWithText("招商银行").performClick()
        assertEquals("a-bank", edited)
        snapshot(activity, "ledger-accounts-light")
        val dark = show(dark = true) { LedgerAccountsContent(ui, emptyList(), {}, {}, {}) }
        snapshot(dark, "ledger-accounts-dark")
    }

    @Test
    fun budgetShowsTotalAndCategories() {
        val data = LedgerSamples.data.copy(
            budgets = LedgerSamples.data.budgets + LedgerBudget("b-food", "cat-food", 150_000) + LedgerBudget("b-shop", "cat-shopping", 50_000),
        )
        val ui = buildBudgetUi(data, october, LedgerSamples.zone)
        val activity = show { LedgerBudgetContent(ui, {}, {}, { _, _ -> }, {}, today = LedgerSamples.today) }
        compose.onNodeWithText("每月总预算").assertExists()
        compose.onNodeWithText("添加分类预算").assertExists()
        snapshot(activity, "ledger-budget-light")
    }

    @Test
    fun recurringListShowsRules() {
        val data = LedgerSamples.data.copy(
            recurring = listOf(
                LedgerRecurring(
                    id = "r-rent", type = TxType.EXPENSE, amount = 380_000, accountId = "a-bank",
                    categoryId = "cat-housing-1", note = "房租", frequency = RecurFrequency.MONTHLY,
                    startDate = LocalDate.of(2026, 1, 8),
                ),
                LedgerRecurring(
                    id = "r-salary", type = TxType.INCOME, amount = 1_850_000, accountId = "a-bank",
                    categoryId = "cat-salary", frequency = RecurFrequency.MONTHLY,
                    startDate = LocalDate.of(2026, 1, 9), paused = true,
                ),
            ),
        )
        val rows = buildRecurringUi(data)
        val activity = show { LedgerRecurringContent(rows, {}, {}, {}) }
        compose.onNodeWithText("房租").assertExists()
        snapshot(activity, "ledger-recurring-light")
    }
}
