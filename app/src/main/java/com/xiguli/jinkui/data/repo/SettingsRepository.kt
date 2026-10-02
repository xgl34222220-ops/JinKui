package com.xiguli.jinkui.data.repo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class AppSettings(
    /** 切到后台后多少秒锁定；0 表示立即。前台无操作同样按此时间锁定（最短 30 秒）。 */
    val autoLockSeconds: Int = 60,
    val clipboardSeconds: Int = 30,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 连续输错达到上限后清空全部数据。默认开启。 */
    val wipeOnFailure: Boolean = true,
    val keepHistory: Boolean = true,
)

/** 非敏感偏好设置。主题等需要在解锁前读取，因此不放进加密库。 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jinkui_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val current: AppSettings get() = state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        prefs.edit()
            .putInt(KEY_AUTO_LOCK, next.autoLockSeconds)
            .putInt(KEY_CLIPBOARD, next.clipboardSeconds)
            .putString(KEY_THEME, next.theme.key)
            .putBoolean(KEY_WIPE, next.wipeOnFailure)
            .putBoolean(KEY_HISTORY, next.keepHistory)
            .commit()
        state.value = next
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            autoLockSeconds = prefs.getInt(KEY_AUTO_LOCK, defaults.autoLockSeconds),
            clipboardSeconds = prefs.getInt(KEY_CLIPBOARD, defaults.clipboardSeconds),
            theme = ThemeMode.of(prefs.getString(KEY_THEME, null)),
            wipeOnFailure = prefs.getBoolean(KEY_WIPE, defaults.wipeOnFailure),
            keepHistory = prefs.getBoolean(KEY_HISTORY, defaults.keepHistory),
        )
    }

    companion object {
        private const val KEY_AUTO_LOCK = "auto_lock_seconds"
        private const val KEY_CLIPBOARD = "clipboard_seconds"
        private const val KEY_THEME = "theme"
        private const val KEY_WIPE = "wipe_on_failure"
        private const val KEY_HISTORY = "keep_history"

        val AUTO_LOCK_OPTIONS = listOf(0 to "立即", 30 to "30 秒", 60 to "1 分钟", 300 to "5 分钟", 600 to "10 分钟")
        val CLIPBOARD_OPTIONS = listOf(15 to "15 秒", 30 to "30 秒", 60 to "1 分钟", 120 to "2 分钟")

        fun autoLockLabel(seconds: Int): String =
            AUTO_LOCK_OPTIONS.firstOrNull { it.first == seconds }?.second ?: "$seconds 秒"

        fun clipboardLabel(seconds: Int): String =
            CLIPBOARD_OPTIONS.firstOrNull { it.first == seconds }?.second ?: "$seconds 秒"
    }
}
