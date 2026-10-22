package com.xiguli.jinkui.core.quick

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.TileService
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.xiguli.jinkui.MainActivity
import com.xiguli.jinkui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 1.4.0-alpha04 快捷设置磁贴与桌面小部件“记一笔”：都只发出 ACTION_QUICK_RECORD 打开金匮（照常要求解锁），
 * 小部件界面只有“记一笔”、不含任何数字或金额；磁贴只有系统能绑定；小部件接收器不导出；没有新增申请的权限。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class QuickRecordTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun intentOpensMainActivityWithQuickRecord() {
        val intent = QuickRecord.intent(app)
        assertEquals(MainActivity.ACTION_QUICK_RECORD, intent.action)
        assertEquals(ComponentName(app, MainActivity::class.java), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        // 不携带任何数据
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
        val pending = QuickRecord.pendingIntent(app, QuickRecord.REQUEST_WIDGET)
        assertTrue(pending.isImmutable)
        val saved = shadowOf(pending).savedIntent
        assertEquals(MainActivity.ACTION_QUICK_RECORD, saved.action)
        assertTrue(shadowOf(pending).isActivity)
    }

    @Test
    fun widgetShowsOnlyRecordLabelAndNoAmounts() {
        val parent = FrameLayout(app)
        val view = QuickRecord.widgetViews(app).apply(app, parent)
        val texts = mutableListOf<String>()
        fun walk(v: View) {
            if (v is TextView) texts += v.text.toString()
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(view)
        assertTrue("记一笔" in texts)
        assertEquals(listOf("记一笔", "解锁后记账"), texts)
        // 任何时候都不显示数字、金额符号
        assertFalse(texts.any { t -> t.any { it.isDigit() } || "¥" in t || "￥" in t })
        assertTrue(view.findViewById<View>(R.id.widget_quick_record_root).hasOnClickListeners())
    }

    @Test
    fun manifestDeclaresTileAndWidgetWithMinimalSurface() {
        val pm = app.packageManager
        val tile = pm.getServiceInfo(ComponentName(app, QuickRecordTileService::class.java), PackageManager.GET_META_DATA)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tile.permission)
        assertTrue(tile.exported)
        val tiles = pm.queryIntentServices(Intent(TileService.ACTION_QS_TILE).setPackage(app.packageName), 0)
        assertEquals(listOf(QuickRecordTileService::class.java.name), tiles.map { it.serviceInfo.name })

        val widget = pm.getReceiverInfo(ComponentName(app, QuickRecordWidget::class.java), PackageManager.GET_META_DATA)
        assertFalse(widget.exported)
        assertEquals(R.xml.widget_quick_record, widget.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
        assertNotNull(widget)

        // 申请的权限没有新增（磁贴与小部件不需要任何权限，也不联网）
        val requested = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        val allowed = setOf("android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT", "android.permission.POST_NOTIFICATIONS")
        assertEquals(emptySet<String>(), requested.filterNot { it in allowed || it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }.toSet())
        assertFalse("android.permission.INTERNET" in requested)
    }
}
