package com.xiguli.jinkui.ui.me

import com.xiguli.jinkui.ui.theme.jkBackdrop
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
import androidx.compose.ui.test.performScrollTo
import com.xiguli.jinkui.data.ledger.LedgerBudget
import com.xiguli.jinkui.data.ledger.LedgerRecurring
import com.xiguli.jinkui.data.ledger.RecurFrequency
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.ThemeMode
import com.xiguli.jinkui.ui.ledger.LedgerSamples
import com.xiguli.jinkui.ui.me.MeActions
import com.xiguli.jinkui.ui.me.MeContent
import com.xiguli.jinkui.ui.me.buildMeUi
import com.xiguli.jinkui.ui.theme.JinKuiTheme
import com.xiguli.jinkui.ui.theme.Jk
import java.io.File
import java.time.LocalDate
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

/** “我的”分区的界面测试与截图（只渲染无状态的 [MeContent]）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class MeScreenUiTest {
    @get:Rule(order = 0)
    val drain = com.xiguli.jinkui.ui.MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<ComponentActivity>>()

    @org.junit.After
    fun closeActivities() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
    }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit): ComponentActivity {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controllers += controller
        val activity = controller.get()
        activity.setContent {
            JinKuiTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize().then(Modifier.jkBackdrop())) { content() }
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
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val dir = java.io.File("build/outputs/ui-screenshots").apply { mkdirs() }
            java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("截图 $name 失败：$it") }
    }

    private val ui = buildMeUi(LedgerSamples.data, cards = 5, today = LedgerSamples.today, zone = LedgerSamples.zone, version = "1.1.0-beta")

    @Test
    fun countsDaysAndRecords() {
        val first = LedgerSamples.data.transactions.minOf { it.occurredAt }
        val firstDay = java.time.Instant.ofEpochMilli(first).atZone(LedgerSamples.zone).toLocalDate()
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(firstDay, LedgerSamples.today).toInt() + 1, ui.days)
        assertEquals(LedgerSamples.data.transactions.size, ui.records)
        assertEquals(null, buildMeUi(com.xiguli.jinkui.data.ledger.LedgerData(), 0, LedgerSamples.today, LedgerSamples.zone).days)
    }

    @Test
    fun meLightDarkAndFontScale() {
        var budget = false
        var locked = false
        var debts = false
        val light = show { MeContent(ui, MeActions(onBudget = { budget = true }, onLock = { locked = true }, onDebts = { debts = true })) }
        compose.onNodeWithText("坚持记账的第 ${ui.days} 天").assertExists()
        // 1.5.0-alpha01：宫格是账本最常用的 8 个；下面按账本工具 / 卡包 / 数据与安全 / 关于分组
        for (label in listOf("资产账户", "借钱欠款", "预算", "周期记账", "分类管理", "报销", "导入账单", "自动记账")) {
            compose.onNodeWithText(label).assertExists()
        }
        for (group in listOf("账本工具", "卡包", "数据与安全", "关于")) {
            compose.onNodeWithText(group).performScrollTo().assertExists()
        }
        for (row in listOf("规则与模板", "账户与余额", "密码生成器", "安全体检", "标签整理", "回收站", "加密备份与恢复", "设置")) {
            compose.onNodeWithText(row).performScrollTo().assertExists()
        }
        compose.onNodeWithText("预算").performScrollTo().performClick()
        // 宫格里的“借钱欠款”打开欠款 / 借贷；锁定在“数据与安全 · 立即锁定”
        compose.onNodeWithText("借钱欠款").performScrollTo().performClick()
        compose.onNodeWithText("立即锁定").performScrollTo().performClick()
        assertTrue(budget)
        assertTrue(debts)
        assertTrue(locked)
        snapshot(light, "me-light")
        val dark = show(dark = true) { MeContent(ui, MeActions()) }
        snapshot(dark, "me-dark")
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        try {
            val big = show { MeContent(ui, MeActions()) }
            snapshot(big, "me-font130")
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(1f)
        }
    }
}
