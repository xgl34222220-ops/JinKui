package com.xiguli.jinkui.core.security

import android.content.Context
import java.util.Base64

/**
 * 解锁前就要读取的元数据。这里只有被 Keystore 包裹过的密文和计数器，没有任何明文秘密。
 * 计数器使用 commit() 同步落盘，保证“先记一次失败、再校验口令”的顺序不会被杀进程绕过。
 */
class VaultMetaStore(context: Context) {
    private val prefs = context.getSharedPreferences("jinkui_meta", Context.MODE_PRIVATE)

    val isInitialized: Boolean get() = prefs.contains(KEY_PASSWORD)

    var passwordBlob: ByteArray?
        get() = read(KEY_PASSWORD)
        set(value) = write(KEY_PASSWORD, value)

    var pinBlob: ByteArray?
        get() = read(KEY_PIN)
        set(value) = write(KEY_PIN, value)

    var biometricBlob: ByteArray?
        get() = read(KEY_BIOMETRIC)
        set(value) = write(KEY_BIOMETRIC, value)

    var failCount: Int
        get() = prefs.getInt(KEY_FAILS, 0)
        set(value) {
            prefs.edit().putInt(KEY_FAILS, value).commit()
        }

    var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LOCKOUT, value).commit()
        }

    /** 上次复制的敏感内容是否还可能留在剪贴板里。 */
    var clipboardPending: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CLIPBOARD, value).apply()
        }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun read(key: String): ByteArray? =
        prefs.getString(key, null)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    private fun write(key: String, value: ByteArray?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, Base64.getEncoder().encodeToString(value))
        editor.commit()
    }

    private companion object {
        const val KEY_PASSWORD = "password_envelope"
        const val KEY_PIN = "pin_envelope"
        const val KEY_BIOMETRIC = "biometric_envelope"
        const val KEY_FAILS = "failed_attempts"
        const val KEY_LOCKOUT = "lockout_until"
        const val KEY_CLIPBOARD = "clipboard_pending"
    }
}
