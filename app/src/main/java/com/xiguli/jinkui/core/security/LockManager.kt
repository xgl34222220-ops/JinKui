package com.xiguli.jinkui.core.security

import android.os.SystemClock
import com.xiguli.jinkui.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 自动锁定：
 *  - 切到后台后按设定时间锁定（0 = 立即）；即使用户不再回来，到点也会清除内存中的密钥。
 *  - 前台长时间无操作同样锁定（按同一设定，最短 30 秒）。
 *  - 调起系统文件 / 图片选择器时短暂豁免，避免选张照片回来就被锁在外面。
 */
class LockManager(
    private val vault: VaultManager,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private var foreground = false
    private var backgroundJob: Job? = null
    private var lastInteraction = SystemClock.elapsedRealtime()
    private var holdUntil = 0L

    init {
        scope.launch {
            while (true) {
                delay(IDLE_CHECK_MS)
                if (foreground && vault.isUnlocked) {
                    val idleLimit = maxOf(settings.current.autoLockSeconds, MIN_IDLE_SECONDS) * 1000L
                    if (SystemClock.elapsedRealtime() - lastInteraction >= idleLimit) vault.lock()
                }
            }
        }
    }

    /** 任何触摸、按键、输入都算一次操作。 */
    fun touch() {
        lastInteraction = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        foreground = true
        holdUntil = 0L
        backgroundJob?.cancel()
        backgroundJob = null
        touch()
    }

    fun onBackground() {
        foreground = false
        backgroundJob?.cancel()
        if (!vault.isUnlocked) return
        val now = SystemClock.elapsedRealtime()
        val configured = settings.current.autoLockSeconds * 1000L
        val wait = maxOf(configured, holdUntil - now)
        if (wait <= 0L) {
            vault.lock()
            return
        }
        backgroundJob = scope.launch {
            delay(wait)
            vault.lock()
        }
    }

    /** 即将跳到系统选择器：在 [millis] 内暂不因切后台而锁定。 */
    fun holdForExternalActivity(millis: Long = EXTERNAL_HOLD_MS) {
        holdUntil = SystemClock.elapsedRealtime() + millis
    }

    private companion object {
        const val IDLE_CHECK_MS = 5_000L
        const val MIN_IDLE_SECONDS = 30
        const val EXTERNAL_HOLD_MS = 120_000L
    }
}
