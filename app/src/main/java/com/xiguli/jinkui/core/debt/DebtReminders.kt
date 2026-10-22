package com.xiguli.jinkui.core.debt

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.xiguli.jinkui.MainActivity
import com.xiguli.jinkui.R
import com.xiguli.jinkui.data.ledger.DueItem
import com.xiguli.jinkui.data.repo.SettingsRepository
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 账单到期的系统通知（1.4 起，默认关闭，在“设置 → 账单与自动记账”里打开）。
 *
 * 金匮锁定时读不到账本，所以提醒只能在解锁时排好：每次解锁、账本有变化时，按当前的待还 / 待收算出下一次提醒的时间
 * （最早一项到期前 3 天的上午 9 点；已经在 3 天之内或逾期的，就是下一个上午 9 点），用 AlarmManager 的
 * 非精确闹钟排一次（[AlarmManager.set]，不需要精确闹钟权限，系统可能稍微推迟）。到时间时 [DebtReminderReceiver]
 * 只发一条不含金额、对方与账户的通用通知，点开回到金匮解锁后再看详情。
 * 金额等明文从不离开保险库；闹钟里也不带任何数据。重启手机后闹钟会被系统清掉，下次解锁时重新排上
 * （不申请开机自启权限）。关掉开关立即取消。
 */
object DebtReminders {
    const val CHANNEL = "debt_due"
    private const val REQUEST = 4101
    val REMIND_AT: LocalTime = LocalTime.of(9, 0)

    /** 下一次提醒的时间；没有需要提醒的为 null。纯函数，便于测试。 */
    fun nextFire(items: List<DueItem>, now: ZonedDateTime): ZonedDateTime? {
        val pending = items.filter { it.counts && it.amount > 0 }
        if (pending.isEmpty()) return null
        val today = now.toLocalDate()
        val firstDay = pending.minOf { it.dueDate.minusDays(com.xiguli.jinkui.data.ledger.DebtMath.REMIND_DAYS) }.coerceAtLeast(today)
        val candidate = firstDay.atTime(REMIND_AT).atZone(now.zone)
        return if (candidate.isAfter(now)) candidate else today.plusDays(1).atTime(REMIND_AT).atZone(now.zone)
    }

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, DebtReminderReceiver::class.java).setAction(ACTION)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST, intent, flags)
    }

    /** 按 [items] 排下一次提醒；[enabled] 为 false 或没有需要提醒的项时取消。 */
    fun schedule(context: Context, enabled: Boolean, items: List<DueItem>, now: ZonedDateTime = ZonedDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = if (enabled) nextFire(items, now) else null
        if (at == null) {
            pendingIntent(context, create = false)?.let {
                alarms.cancel(it)
                it.cancel()
            }
            return
        }
        val pi = pendingIntent(context, create = true) ?: return
        runCatching { alarms.set(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi) }
    }

    fun cancel(context: Context) = schedule(context, enabled = false, items = emptyList())

    /** Android 13 起发通知需要用户授予 POST_NOTIFICATIONS。 */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.debt_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.debt_channel_description)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    internal const val ACTION = "com.xiguli.jinkui.action.DEBT_REMINDER"
}

/** 闹钟到时发通用提醒。不导出，只能由金匮自己排的闹钟触发。 */
class DebtReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DebtReminders.ACTION) return
        if (!SettingsRepository(context).current.debtNotify) return
        if (!DebtReminders.canPost(context)) return
        DebtReminders.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = android.app.Notification.Builder(context, DebtReminders.CHANNEL)
            // 1.4.0-alpha03：状态栏小图标换成 B 款标志的单色剪影，着薄荷色
            .setSmallIcon(R.drawable.ic_stat_jinkui)
            .setColor(0xFF3AA084.toInt())
            .setContentTitle(context.getString(R.string.debt_notify_title))
            .setContentText(context.getString(R.string.debt_notify_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(NOTIFY_ID, notification) }
    }

    private companion object {
        const val NOTIFY_ID = 4102
    }
}
