package com.xiguli.jinkui.ui.settings

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
import com.xiguli.jinkui.data.repo.AppSettings
import com.xiguli.jinkui.data.repo.ThemeMode
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JinKuiTheme
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowLooper

/** 设置页（含加密备份一节）的界面测试与截图：只渲染无状态的 [SettingsContent]，不碰密钥、数据库与文件选择器。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class SettingsScreenUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val controllers = mutableListOf<ActivityController<ComponentActivity>>()

    @After
    fun closeActivities() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
    }

    private val backedUp = AppSettings(
        lastBackupAt = LocalDateTime.of(2026, 10, 8, 21, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

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
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File("build/outputs/ui-screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("截图 $name 失败：$it") }
    }

    /** 滚到页面底部：加密备份、两步验证码、其他三节都在一屏里，截图能看清整节备份。 */
    private fun scrollToBackup() {
        compose.onNodeWithText("未申请任何联网权限", substring = true).performScrollTo()
        compose.waitForIdle()
    }

    private fun settings(
        value: AppSettings = backedUp,
        actions: SettingsActions = SettingsActions(),
    ): @Composable () -> Unit = {
        SettingsContent(
            settings = value,
            biometricAvailable = true,
            biometricEnabled = true,
            pinEnabled = false,
            scanningOtp = false,
            version = "1.0.0-beta",
            actions = actions,
        )
    }

    @Test
    fun settingsTopAndBackupSection() {
        val calls = mutableListOf<String>()
        val activity = show(content = settings(actions = SettingsActions(onExport = { calls += "export" }, onImport = { calls += "import" })))
        compose.onNodeWithText("解锁与锁定").assertExists()
        compose.onNodeWithText("未设置").assertExists() // PIN
        snapshot(activity, "settings-light")
        scrollToBackup()
        compose.onNodeWithText("上次备份：", substring = true).assertExists()
        snapshot(activity, "settings-backup-light")
        compose.onNodeWithText("导出加密备份").performClick()
        compose.onNodeWithText("导入加密备份").performScrollTo().performClick()
        assertEquals(listOf("export", "import"), calls)
    }

    /** 1.5.0-alpha02：说明文字白话化（自动锁定、PIN、历史密码、清空全部数据）；深色顶部截图。 */
    @Test
    fun settingsPlainWordingDark() {
        val activity = show(dark = true, content = settings())
        compose.onNodeWithText("切到后台或一段时间不操作，就自动锁上").assertExists()
        compose.onNodeWithText("指纹、面容用不了时，用", substring = true).assertExists()
        snapshot(activity, "settings-dark")
    }

    @Test
    fun backupNeverDoneDark() {
        val activity = show(dark = true, content = settings(value = AppSettings()))
        scrollToBackup()
        compose.onNodeWithText("还没有备份过", substring = true).assertExists()
        snapshot(activity, "settings-backup-dark")
    }

    @Test
    fun settingsAtLargeFontScale() {
        RuntimeEnvironment.setFontScale(1.3f)
        try {
            val activity = show(content = settings())
            scrollToBackup()
            snapshot(activity, "settings-backup-font130")
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }
}
