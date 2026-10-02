package com.xiguli.jinkui.core.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.xiguli.jinkui.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 复制敏感内容并在设定时间后清空剪贴板。
 *
 *  - 剪贴内容标记为“敏感”，Android 13+ 的剪贴板预览不会显示明文。
 *  - 计时在应用进程内进行；万一进程在到点前被系统回收，下次启动或锁定时会补做清空。
 */
class ClipboardGuard(
    context: Context,
    private val settings: SettingsRepository,
    private val meta: VaultMetaStore,
    private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var clearJob: Job? = null

    /** 返回清空前的秒数，供界面提示。 */
    fun copy(label: String, text: String): Int {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
        manager.setPrimaryClip(clip)
        meta.clipboardPending = true
        val seconds = settings.current.clipboardSeconds
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(seconds * 1000L)
            clearNow()
        }
        return seconds
    }

    fun clearNow() {
        clearJob?.cancel()
        clearJob = null
        if (!meta.clipboardPending) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        meta.clipboardPending = false
    }

    private companion object {
        // 等同于 ClipDescription.EXTRA_IS_SENSITIVE（API 33 才有常量，旧系统会忽略它）。
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
