package com.xiguli.jinkui.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.repo.ThemeMode
import com.xiguli.jinkui.ui.theme.FontConfig
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JinKuiTheme
import com.xiguli.jinkui.ui.theme.JkFonts
import java.io.File
import kotlinx.coroutines.cancel
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * 1.2 新增界面测试共用的骨架：起一个空 Activity、套上金匮主题、按需提供 AppContainer，
 * 截图写到 build/outputs/ui-screenshots/（CI 上传为附件）。仍然配合 [MainLooperDrainRule] 使用。
 */
class ScreenHarness(private val compose: ComposeTestRule) {
    private val controllers = mutableListOf<ActivityController<ComponentActivity>>()
    private var container: AppContainer? = null
    private var paused = false

    /** 需要 LocalContainer 的页面（首次设置、密码生成器等）用这个：锁定状态、空数据，不碰 Keystore。 */
    fun container(): AppContainer = container ?: AppContainer(RuntimeEnvironment.getApplication()).also { container = it }

    /**
     * [pausedClock]：带自动聚焦输入框的对话框（光标一直闪）用暂停的测试时钟渲染，否则 waitForIdle 永远等不到空闲。
     */
    fun show(dark: Boolean = false, withContainer: Boolean = false, pausedClock: Boolean = false, content: @Composable () -> Unit): ComponentActivity {
        paused = pausedClock
        compose.mainClock.autoAdvance = !pausedClock
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controllers += controller
        val activity = controller.get()
        val c = if (withContainer) container() else null
        activity.setContent {
            JinKuiTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize().background(Jk.colors.bg)) {
                    if (c != null) {
                        CompositionLocalProvider(LocalContainer provides c) { content() }
                    } else {
                        content()
                    }
                }
            }
        }
        ShadowLooper.idleMainLooper()
        if (pausedClock) {
            // 不等“空闲”（闪烁的光标永远不空闲），只手动推进几帧
            repeat(3) { compose.mainClock.advanceTimeByFrame() }
            ShadowLooper.idleMainLooper()
        } else {
            compose.waitForIdle()
        }
        return activity
    }

    /** 尽力而为的截图；[withDialog] 时把最上层的对话框 / 底部面板叠画在页面上。 */
    fun snapshot(activity: ComponentActivity, name: String, withDialog: Boolean = false) {
        if (paused) {
            compose.mainClock.advanceTimeBy(600)
            ShadowLooper.idleMainLooper()
        } else {
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
        }
        runCatching {
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            if (withDialog) {
                ShadowDialog.getLatestDialog()?.window?.decorView?.let { dialogView ->
                    canvas.drawARGB(0x66, 0, 0, 0)
                    dialogView.draw(canvas)
                }
            }
            val dir = File("build/outputs/ui-screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("截图 $name 失败：$it") }
    }

    fun close() {
        compose.mainClock.autoAdvance = true
        paused = false
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        container?.scope?.cancel()
        container = null
        JkFonts.config = FontConfig.System
        RuntimeEnvironment.setFontScale(1f)
    }
}
