package com.xiguli.jinkui.core.auto

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xiguli.jinkui.JinKuiApp

/**
 * 自动记账的通知监听（默认关闭）。用户在“设置 → 自动记账”里打开开关、并在系统设置里授予“通知使用权”后才会被系统绑定。
 *
 *  - 只看 [PaymentNotificationParser.packages] 里的微信、支付宝、云闪付与银行 App，其他应用的通知直接忽略；
 *  - 读标题、正文、长文本、副文本、多行文本与 tickerText，只取出金额、方向、商户和时间，通知原文不保存、不上传（金匮没有联网权限）；
 *  - 结果先加密暂存（[PendingSpool]），解锁后进入“待确认”，用户确认后才记账；只有用户另外打开“自动入账”时才直接记账；
 *  - 像是收付款但没认全的，把数字换成 # 后加密记进“未识别通知”（[UnrecognizedLog]，最多 20 条），方便用户手动记一笔；
 *  - 连上 / 断开、最近看到受支持应用通知的时间（只有包名）记进 [AutoStatus]，设置页据此显示状态；断开时请求系统重新绑定。
 */
class PaymentNotificationService : NotificationListenerService() {
    /** 同一条通知更新（例如进度、重复推送）时只记一次。 */
    private val recent = ArrayDeque<String>()

    private val container get() = (application as? JinKuiApp)?.container

    override fun onListenerConnected() {
        super.onListenerConnected()
        container?.autoStatus?.connected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        container?.autoStatus?.disconnected()
        // HyperOS 等系统断开后常常不再主动绑定：请系统重新绑定一次
        ListenerRebinder.requestRebind(this)
    }

    override fun onDestroy() {
        container?.autoStatus?.let { if (it.current.live) it.disconnected() }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val container = container ?: return
        if (!PaymentNotificationParser.supports(sbn.packageName)) return
        if (!container.settings.current.autoBookkeeping) return
        container.autoStatus.seen(sbn.packageName)
        if (sbn.isOngoing) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        // 群组摘要（“3 条新消息”）只是折叠头，不是一条通知
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG))?.toString().orEmpty()
        val texts = buildList {
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { add(it.toString()) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
            notification.tickerText?.let { add(it.toString()) }
        }
        val chat = extras.containsKey(Notification.EXTRA_MESSAGES) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION))
        when (val outcome = PaymentNotificationParser.analyze(sbn.packageName, title, texts, sbn.postTime, chat)) {
            is PaymentNotificationParser.Outcome.Payment -> {
                val payment = outcome.payment
                val key = "${sbn.packageName}|${payment.type.key}|${payment.amount}|${payment.merchant}|${sbn.postTime / 60_000}"
                synchronized(recent) {
                    if (key in recent) return
                    recent.addLast(key)
                    while (recent.size > 64) recent.removeFirst()
                }
                container.autoStatus.recognized()
                container.capturePayment(payment)
            }
            is PaymentNotificationParser.Outcome.Unrecognized -> {
                val source = PaymentNotificationParser.packages[sbn.packageName] ?: return
                val shape = PaymentNotificationParser.mask(title, texts)
                val key = "u|${sbn.packageName}|$shape"
                synchronized(recent) {
                    if (key in recent) return
                    recent.addLast(key)
                    while (recent.size > 64) recent.removeFirst()
                }
                container.autoStatus.unrecognized()
                runCatching { container.unrecognized.add(source, sbn.postTime, shape) }
            }
            is PaymentNotificationParser.Outcome.NotPayment -> Unit
        }
    }
}
