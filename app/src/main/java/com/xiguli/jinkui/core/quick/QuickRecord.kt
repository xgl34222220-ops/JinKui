package com.xiguli.jinkui.core.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.xiguli.jinkui.MainActivity
import com.xiguli.jinkui.R

/**
 * 1.4.0-alpha04：快捷设置磁贴与桌面小部件“记一笔”。
 *
 * 两者都只做一件事：打开金匮并发出与桌面长按快捷方式相同的 [MainActivity.ACTION_QUICK_RECORD]，
 * 金匮照常要求解锁，解锁后账本页直接打开记账面板。它们不读取任何账本或卡包数据，
 * 所以无论手机或金匮是否锁定，都不会显示金额、余额或任何记录。不需要任何新权限（也不联网）。
 */
object QuickRecord {
    /** 打开金匮并请求“记一笔”的 Intent（显式指向 MainActivity）。 */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_QUICK_RECORD)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun pendingIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode, intent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 小部件的界面：只有图标与“记一笔”，没有任何数字。 */
    fun widgetViews(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_quick_record).apply {
        setOnClickPendingIntent(R.id.widget_quick_record_root, pendingIntent(context, REQUEST_WIDGET))
    }

    const val REQUEST_WIDGET = 41
    const val REQUEST_TILE = 42
}

/** 快捷设置里的“记一笔”磁贴。只有系统能绑定（BIND_QUICK_SETTINGS_TILE）。 */
class QuickRecordTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.shortcut_record_short)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = getString(R.string.app_name)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // 手机锁屏时先让用户解锁手机，再打开金匮（金匮自己还会要求解锁）
        if (isLocked) unlockAndRun(::open) else open()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(QuickRecord.pendingIntent(this, QuickRecord.REQUEST_TILE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(QuickRecord.intent(this))
        }
    }
}

/** 桌面小部件“记一笔”：静态界面，不随账本变化，所以不需要定时刷新。 */
class QuickRecordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = QuickRecord.widgetViews(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
