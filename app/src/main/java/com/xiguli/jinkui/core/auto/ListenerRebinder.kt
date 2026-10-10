package com.xiguli.jinkui.core.auto

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * HyperOS / MIUI 等系统常在后台停掉通知监听且不再绑定。打开金匮时如果已授权、开关开着、但监听没连上：
 *  1. 先 [NotificationListenerService.requestRebind] 请系统重新绑定；
 *  2. 仍不行时（[force]）把监听组件禁用再启用一次，系统会把它当成新组件重新绑定。
 * 只操作金匮自己的组件，不需要任何权限。
 */
object ListenerRebinder {
    fun component(context: Context) = ComponentName(context, PaymentNotificationService::class.java)

    fun accessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun requestRebind(context: Context) {
        runCatching { NotificationListenerService.requestRebind(component(context)) }
    }

    fun toggleComponent(context: Context) {
        val pm = context.packageManager
        val name = component(context)
        runCatching {
            pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        requestRebind(context)
    }

    /** 需要时请求重连；[force] 时再切换一次组件状态。返回是否发出了请求。 */
    fun ensure(context: Context, enabled: Boolean, live: Boolean, force: Boolean = false): Boolean {
        if (!enabled || live || !accessGranted(context)) return false
        if (force) toggleComponent(context) else requestRebind(context)
        return true
    }
}
