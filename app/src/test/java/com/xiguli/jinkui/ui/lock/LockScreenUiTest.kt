package com.xiguli.jinkui.ui.lock

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
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import com.xiguli.jinkui.data.repo.ThemeMode
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JinKuiTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowLooper

/** 锁屏的界面测试与截图：只渲染无状态的 [LockContent]，不碰密钥。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class LockScreenUiTest {
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

    /** PIN 键盘（浅 / 深）：品牌、提示与数字键可用；左下生物识别键与右下删除键有读屏标签，删除键长按清空。 */
    @Test
    fun pinPadLightAndDark() {
        val digits = mutableListOf<Char>()
        var biometric = 0
        var backspace = 0
        var cleared = 0
        val activity = show {
            LockContent(
                LockUiState(usePin = true, secretLength = 3, pinEnabled = true, biometricReady = true),
                onDigit = { digits += it },
                onBiometric = { biometric++ },
                onBackspace = { backspace++ },
                onClear = { cleared++ },
            )
        }
        compose.onNodeWithText("金匮").assertExists()
        compose.onNodeWithText("输入 PIN 解锁").assertExists()
        compose.onNodeWithContentDescription("已输入 3 位").assertExists()
        compose.onNodeWithText("7").performClick()
        assertEquals(listOf('7'), digits)
        compose.onNodeWithContentDescription("使用指纹或面容解锁").assertHasClickAction().performClick()
        assertEquals(1, biometric)
        compose.onNodeWithContentDescription("删除一位").performClick()
        assertEquals(1, backspace)
        compose.onNodeWithContentDescription("删除一位").performTouchInput { longClick() }
        assertEquals(1, cleared)
        compose.onNodeWithText("改用主密码").assertExists()
        compose.onNodeWithText("忘记主密码？").assertExists()
        compose.onNodeWithText("连续输错 10 次将清空全部数据").assertExists()
        snapshot(activity, "lock-light")
        val dark = show(dark = true) {
            LockContent(LockUiState(usePin = true, secretLength = 4, pinEnabled = true, biometricReady = true))
        }
        snapshot(dark, "lock-dark")
    }

    /** 输错 PIN：圆点变成危险色并在读屏里说明，玻璃提示条显示还剩几次（浅 / 深）。 */
    @Test
    fun wrongPinLightAndDark() {
        val message = "PIN 不正确。再输错 7 次，将清空全部数据"
        // 两个页面同时在：深色那页用另一条文字，查找时不会撞上浅色那页
        val darkMessage = "PIN 不正确。再输错 6 次，将清空全部数据"
        val activity = show {
            LockContent(LockUiState(usePin = true, secretLength = 6, pinEnabled = true, biometricReady = true, error = message))
        }
        compose.onNodeWithText(message).assertExists()
        compose.onNodeWithContentDescription("已输入 6 位，PIN 不正确").assertExists()
        snapshot(activity, "lock-pin-error-light")
        val dark = show(dark = true) {
            LockContent(LockUiState(usePin = true, secretLength = 6, pinEnabled = true, error = darkMessage))
        }
        compose.onNodeWithText(darkMessage).assertExists()
        snapshot(dark, "lock-pin-error-dark")
    }

    /** 主密码模式：冷却中时提示倒计时、“解锁”不可用；有生物识别时卡片里多一个按钮（浅 / 深）。 */
    @Test
    fun passwordModeLightAndDark() {
        val activity = show { LockContent(LockUiState(usePin = false, waitSeconds = 28, pinEnabled = true, secret = "abc")) }
        compose.onNodeWithText("输错次数较多，请等待 28 秒").assertExists()
        compose.onNodeWithText("解锁").assertIsNotEnabled()
        compose.onNodeWithText("改用 PIN").assertExists()
        snapshot(activity, "lock-password-light")
        var biometric = 0
        val dark = show(dark = true) {
            LockContent(LockUiState(usePin = false, biometricReady = true), onBiometric = { biometric++ })
        }
        compose.onNodeWithText("输入主密码解锁").assertExists()
        compose.onNodeWithText("使用指纹或面容").performClick()
        assertEquals(1, biometric)
        snapshot(dark, "lock-password-dark")
    }
}
