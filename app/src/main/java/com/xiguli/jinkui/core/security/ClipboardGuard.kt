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
        clip.description.extras = PersistableBundle().apply {
            putBoolean(EXTRA_IS_SENSITIVE, true)
            putBoolean(EXTRA_OWNER, true)
        }
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
        // 用户在倒计时期间又从别的应用复制了东西：那是他自己的内容，不该被金匮清掉。
        // 系统不让后台应用读剪贴板时拿不到描述（null），这时按原逻辑清空，宁可多清不可漏清。
        val owned = runCatching { manager.primaryClipDescription }.getOrNull()?.let { isOwnClip(it.extras) }
        if (!shouldClear(owned)) {
            meta.clipboardPending = false
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        meta.clipboardPending = false
    }

    companion object {
        // 等同于 ClipDescription.EXTRA_IS_SENSITIVE（API 33 才有常量，旧系统会忽略它）。
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

        /** 标记“这条剪贴内容是金匮放进去的”。 */
        private const val EXTRA_OWNER = "com.xiguli.jinkui.extra.CLIP_OWNER"

        private fun isOwnClip(extras: PersistableBundle?): Boolean = extras?.getBoolean(EXTRA_OWNER, false) == true

        /**
         * [owned]：剪贴板当前内容是否是金匮复制的；null 表示无法得知（读不到或剪贴板为空）。
         * 只有确知内容已被别的应用替换时才跳过清空。
         */
        fun shouldClear(owned: Boolean?): Boolean = owned != false
    }
}
