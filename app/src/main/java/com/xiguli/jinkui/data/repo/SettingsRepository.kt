package com.xiguli.jinkui.data.repo

import android.content.Context
import com.xiguli.jinkui.data.model.SortOrder
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

/** 字体设置（1.2 起）。[key] 写入本机偏好，上线后不要改动。 */
enum class FontMode(val key: String, val label: String) {
    /** 全部文字（含数字）用系统默认字体，系统里换的字体直接生效。默认。 */
    SYSTEM("system", "跟随系统字体"),
    /** 金额、日期等数字用 Oxanium 圆润数字，其余跟随系统。1.1 的样子。 */
    ROUNDED("rounded", "圆润数字"),
    /** 用户导入的 .ttf / .otf 字体文件，用于全部文字。 */
    CUSTOM("custom", "导入的字体");

    companion object {
        fun of(key: String?): FontMode = entries.firstOrNull { it.key == key } ?: SYSTEM
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
    /** 首页用牌堆还是列表。纯界面偏好。1.0 起默认列表（搜索在最上面，找卡更快）；已经选过的保持不变。 */
    val deckView: Boolean = false,
    /** 上次成功导出加密备份的时间；0 表示还没备份过。 */
    val lastBackupAt: Long = 0L,
    /** 列表与牌堆的排序方式。 */
    val sortOrder: SortOrder = SortOrder.TITLE,
    /** Android 12 起可选：品牌色跟随系统壁纸取色（Material You）。默认关闭。 */
    val dynamicColor: Boolean = false,
    /** 字体（1.2 起）。默认跟随系统字体。只是本机偏好，不进备份。 */
    val fontMode: FontMode = FontMode.SYSTEM,
    /** 导入的字体文件的显示名（文件本身在应用私有目录）；空表示没有导入过。 */
    val customFontName: String = "",
    /** 每次导入新字体时更新，用来让界面重新加载字体文件。 */
    val customFontStamp: Long = 0L,
    /** 自动记账：读取微信 / 支付宝 / 银行通知生成待确认记录。默认关闭，需要用户在系统里授予通知使用权。 */
    val autoBookkeeping: Boolean = false,
    /** 自动入账：识别出的通知不经确认直接记账。默认关闭。 */
    val autoSave: Boolean = false,
    /** 减少透明度（1.3 起）：玻璃表面换成不透明填充、不做背景模糊。只是本机偏好，不进备份。 */
    val reduceTransparency: Boolean = false,
    /** 账单到期的系统通知（1.4 起）。默认关闭；打开时申请通知权限，通知里不含金额。 */
    val debtNotify: Boolean = false,
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
            .putBoolean(KEY_DECK, next.deckView)
            .putLong(KEY_LAST_BACKUP, next.lastBackupAt)
            .putString(KEY_SORT, next.sortOrder.key)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putString(KEY_FONT, next.fontMode.key)
            .putString(KEY_FONT_NAME, next.customFontName)
            .putLong(KEY_FONT_STAMP, next.customFontStamp)
            .putBoolean(KEY_AUTO, next.autoBookkeeping)
            .putBoolean(KEY_AUTO_SAVE, next.autoSave)
            .putBoolean(KEY_REDUCE_TRANSPARENCY, next.reduceTransparency)
            .putBoolean(KEY_DEBT_NOTIFY, next.debtNotify)
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
            deckView = prefs.getBoolean(KEY_DECK, defaults.deckView),
            lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, defaults.lastBackupAt),
            sortOrder = SortOrder.of(prefs.getString(KEY_SORT, null)),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, defaults.dynamicColor),
            fontMode = FontMode.of(prefs.getString(KEY_FONT, null)),
            customFontName = prefs.getString(KEY_FONT_NAME, null) ?: "",
            customFontStamp = prefs.getLong(KEY_FONT_STAMP, 0L),
            autoBookkeeping = prefs.getBoolean(KEY_AUTO, defaults.autoBookkeeping),
            autoSave = prefs.getBoolean(KEY_AUTO_SAVE, defaults.autoSave),
            reduceTransparency = prefs.getBoolean(KEY_REDUCE_TRANSPARENCY, defaults.reduceTransparency),
            debtNotify = prefs.getBoolean(KEY_DEBT_NOTIFY, defaults.debtNotify),
        )
    }

    companion object {
        private const val KEY_AUTO_LOCK = "auto_lock_seconds"
        private const val KEY_CLIPBOARD = "clipboard_seconds"
        private const val KEY_THEME = "theme"
        private const val KEY_WIPE = "wipe_on_failure"
        private const val KEY_HISTORY = "keep_history"
        private const val KEY_DECK = "home_deck_view"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_SORT = "sort_order"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_FONT = "font_mode"
        private const val KEY_FONT_NAME = "custom_font_name"
        private const val KEY_FONT_STAMP = "custom_font_stamp"
        private const val KEY_AUTO = "auto_bookkeeping"
        private const val KEY_AUTO_SAVE = "auto_save"
        private const val KEY_REDUCE_TRANSPARENCY = "reduce_transparency"
        private const val KEY_DEBT_NOTIFY = "debt_notify"

        val AUTO_LOCK_OPTIONS = listOf(0 to "立即", 30 to "30 秒", 60 to "1 分钟", 300 to "5 分钟", 600 to "10 分钟")
        val CLIPBOARD_OPTIONS = listOf(15 to "15 秒", 30 to "30 秒", 60 to "1 分钟", 120 to "2 分钟")

        fun autoLockLabel(seconds: Int): String =
            AUTO_LOCK_OPTIONS.firstOrNull { it.first == seconds }?.second ?: "$seconds 秒"

        fun clipboardLabel(seconds: Int): String =
            CLIPBOARD_OPTIONS.firstOrNull { it.first == seconds }?.second ?: "$seconds 秒"
    }
}
