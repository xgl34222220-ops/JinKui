package com.xiguli.jinkui.ui

import android.app.Application
import android.content.ComponentName
import com.xiguli.jinkui.MainActivity
import com.xiguli.jinkui.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 1.4.0-alpha04：指纹解锁弹窗（系统 BiometricPrompt）、桌面、启动画面用的都是清单里的应用图标。
 * 这里确认所有入口都指向 B 款图标（ic_launcher_b / ic_launcher_b_round），旧的“匣子”图标与旧资源名一个都不剩。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AppIconResourcesTest {
    private val res = File("src/main/res")
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun manifestPointsEveryIconAtB() {
        assertTrue(manifest.contains("""android:icon="@mipmap/ic_launcher_b""""))
        assertTrue(manifest.contains("""android:roundIcon="@mipmap/ic_launcher_b_round""""))
        assertTrue(manifest.contains("""android:logo="@mipmap/ic_launcher_b""""))
        // 没有 activity-alias（不会有第二套入口图标）
        assertFalse(manifest.contains("activity-alias"))
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertEquals(R.mipmap.ic_launcher_b, info.icon)
    }

    @Test
    fun noLegacyIconResourcesRemain() {
        val all = res.walkTopDown().filter { it.isFile }.toList()
        // 旧资源名（ic_launcher / ic_launcher_round 以及没有 _b 的图层）
        val legacyNames = Regex("ic_launcher(_round|_background|_foreground|_monochrome)?\\.(png|webp|xml)")
        assertEquals(emptyList<String>(), all.filter { legacyNames.matches(it.name) }.map { it.path })
        // 旧图标的颜色与“匣子”矢量（0.1–1.2 的 ic_mark / icon_gold / icon_background）
        val text = all.filter { it.extension == "xml" }.joinToString("\n") { it.readText() }
        for (old in listOf("icon_gold", "@color/icon_background", "ic_mark", "M36,48v-4a8,8")) {
            assertFalse("旧图标残留：$old", text.contains(old))
        }
        // 每个密度都有 B 款的方形与圆形 PNG（Android 8 以下用），尺寸正确
        val sizes = mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        for ((density, px) in sizes) {
            for (name in listOf("ic_launcher_b.png", "ic_launcher_b_round.png")) {
                val f = File(res, "mipmap-$density/$name")
                assertTrue("缺少 ${f.path}", f.exists())
                // PNG 头：8 字节签名 + IHDR，宽高是第 16–23 字节（大端）
                val head = f.readBytes().copyOfRange(0, 24)
                fun int(at: Int) = (0 until 4).fold(0) { acc, i -> (acc shl 8) or (head[at + i].toInt() and 0xFF) }
                assertEquals(0x89.toByte(), head[0])
                assertEquals(px, int(16))
                assertEquals(px, int(20))
            }
        }
    }

    @Test
    fun roundIconIsTheSameAdaptiveB() {
        for (name in listOf("ic_launcher_b.xml", "ic_launcher_b_round.xml")) {
            val xml = File(res, "mipmap-anydpi-v26/$name").readText()
            assertTrue(xml.contains("@drawable/ic_launcher_b_background"))
            assertTrue(xml.contains("@drawable/ic_launcher_b_foreground"))
            assertTrue(xml.contains("@drawable/ic_launcher_b_monochrome"))
        }
        assertEquals(
            File(res, "mipmap-anydpi-v26/ic_launcher_b.xml").readText(),
            File(res, "mipmap-anydpi-v26/ic_launcher_b_round.xml").readText(),
        )
        assertTrue(File(res, "values-v31/themes.xml").readText().contains("@mipmap/ic_launcher_b<"))
    }
}
