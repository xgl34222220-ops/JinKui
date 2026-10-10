package com.xiguli.jinkui.core.auto

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 自动记账的运行状态（非敏感，明文存在本机偏好里，便于排查“为什么没效果”）：
 * 监听服务最近一次连上 / 断开的时间、最近一次看到受支持应用通知的时间与包名（只有包名，绝不含通知内容）、
 * 今天识别出 / 没认出的条数。
 */
class AutoStatus(context: Context) {
    data class Snapshot(
        val connectedAt: Long = 0L,
        val disconnectedAt: Long = 0L,
        val lastSeenAt: Long = 0L,
        val lastSeenPackage: String = "",
        val day: String = "",
        val recognizedToday: Int = 0,
        val unrecognizedToday: Int = 0,
        /** 监听服务此刻是否在本进程里连着（进程级真值；偏好里的时间只用来显示）。 */
        val live: Boolean = false,
    )

    private val prefs = context.applicationContext.getSharedPreferences("jinkui_auto_status", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<Snapshot> = state.asStateFlow()
    val current: Snapshot get() = state.value

    private fun load() = Snapshot(
        connectedAt = prefs.getLong(K_CONNECTED, 0L),
        disconnectedAt = prefs.getLong(K_DISCONNECTED, 0L),
        lastSeenAt = prefs.getLong(K_SEEN_AT, 0L),
        lastSeenPackage = prefs.getString(K_SEEN_PKG, null).orEmpty(),
        day = prefs.getString(K_DAY, null).orEmpty(),
        recognizedToday = prefs.getInt(K_OK, 0),
        unrecognizedToday = prefs.getInt(K_MISS, 0),
    ).let { rollDay(it) }

    private fun rollDay(s: Snapshot, today: String = LocalDate.now().toString()): Snapshot =
        if (s.day == today) s else s.copy(day = today, recognizedToday = 0, unrecognizedToday = 0)

    @Synchronized
    private fun update(transform: (Snapshot) -> Snapshot) {
        val next = transform(rollDay(state.value))
        prefs.edit()
            .putLong(K_CONNECTED, next.connectedAt)
            .putLong(K_DISCONNECTED, next.disconnectedAt)
            .putLong(K_SEEN_AT, next.lastSeenAt)
            .putString(K_SEEN_PKG, next.lastSeenPackage)
            .putString(K_DAY, next.day)
            .putInt(K_OK, next.recognizedToday)
            .putInt(K_MISS, next.unrecognizedToday)
            .apply()
        state.value = next
    }

    fun connected(now: Long = System.currentTimeMillis()) = update { it.copy(connectedAt = now, live = true) }
    fun disconnected(now: Long = System.currentTimeMillis()) = update { it.copy(disconnectedAt = now, live = false) }
    fun seen(packageName: String, now: Long = System.currentTimeMillis()) = update { it.copy(lastSeenAt = now, lastSeenPackage = packageName) }
    fun recognized() = update { it.copy(recognizedToday = it.recognizedToday + 1) }
    fun unrecognized() = update { it.copy(unrecognizedToday = it.unrecognizedToday + 1) }
    /** 换日后重新算“今天”的计数（界面打开时调用）。 */
    fun refreshDay() = update { it }

    private companion object {
        const val K_CONNECTED = "connected_at"
        const val K_DISCONNECTED = "disconnected_at"
        const val K_SEEN_AT = "last_seen_at"
        const val K_SEEN_PKG = "last_seen_package"
        const val K_DAY = "day"
        const val K_OK = "recognized_today"
        const val K_MISS = "unrecognized_today"
    }
}
