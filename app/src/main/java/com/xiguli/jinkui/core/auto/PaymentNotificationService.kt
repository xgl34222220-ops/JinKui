package com.xiguli.jinkui.core.auto

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xiguli.jinkui.JinKuiApp

/**
 * 自动记账的通知监听（默认关闭）。用户在“设置 → 自动记账”里打开开关、并在系统设置里授予“通知使用权”后才会被系统绑定。
 *
 *  - 只看 [PaymentNotificationParser.packages] 里的微信、支付宝、云闪付与银行 App，其他应用的通知直接忽略；
 *  - 只取出金额、方向、商户和时间，通知原文不保存、不上传（金匮没有联网权限）；
 *  - 结果先加密暂存（[PendingSpool]），解锁后进入“待确认”，用户确认后才记账；
 *    只有用户另外打开“自动入账”时才直接记账。
 */
class PaymentNotificationService : NotificationListenerService() {
    /** 同一条通知更新（例如进度、重复推送）时只记一次。 */
    private val recent = ArrayDeque<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val container = (application as? JinKuiApp)?.container ?: return
        if (!container.settings.current.autoBookkeeping) return
        if (!PaymentNotificationParser.supports(sbn.packageName)) return
        if (sbn.isOngoing) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val payment = PaymentNotificationParser.parse(sbn.packageName, title, text, sbn.postTime) ?: return
        val key = "${sbn.packageName}|${payment.type.key}|${payment.amount}|${payment.merchant}|${sbn.postTime / 60_000}"
        synchronized(recent) {
            if (key in recent) return
            recent.addLast(key)
            while (recent.size > 64) recent.removeFirst()
        }
        container.capturePayment(payment)
    }
}
