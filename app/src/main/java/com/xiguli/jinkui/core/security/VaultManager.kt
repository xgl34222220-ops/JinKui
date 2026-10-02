package com.xiguli.jinkui.core.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.xiguli.jinkui.core.crypto.AesGcm
import com.xiguli.jinkui.core.crypto.FieldCipher
import com.xiguli.jinkui.core.crypto.Kdf
import com.xiguli.jinkui.core.crypto.PasswordEnvelope
import com.xiguli.jinkui.core.crypto.WrongSecretException
import com.xiguli.jinkui.core.crypto.wipe
import com.xiguli.jinkui.data.db.AttachmentStore
import com.xiguli.jinkui.data.db.VaultDatabase
import com.xiguli.jinkui.data.repo.SettingsRepository
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class VaultState { Uninitialized, Locked, Unlocked }

sealed interface UnlockResult {
    data object Success : UnlockResult

    /** 口令错误。[remaining] 为触发自毁前剩余的尝试次数；自毁关闭时为 null。 */
    data class Wrong(val remaining: Int?) : UnlockResult

    /** 连续失败后的冷却期，[untilMillis] 之前不接受尝试。 */
    data class LockedOut(val untilMillis: Long) : UnlockResult

    /** 失败次数达到上限，数据已清空。 */
    data object Wiped : UnlockResult

    data class Error(val message: String) : UnlockResult
}

/** 解锁期间才存在于内存的密钥；锁定时清零。 */
class SessionKeys(
    val vaultKey: ByteArray,
    val field: FieldCipher,
    val attachment: FieldCipher,
) {
    fun wipe() {
        vaultKey.wipe()
        field.wipe()
        attachment.wipe()
    }
}

/**
 * 保险库的密钥层级与解锁流程。
 *
 * ```
 * 主密码 ──PBKDF2──▶ 口令密钥 ──AES-GCM──▶ [口令信封] ──Keystore 主密钥 AES-GCM──▶ 落盘
 *                                              │
 *                                         保险库密钥 VK（随机 256 位，仅解锁期间在内存）
 *                                              │ HKDF
 *                       ┌──────────────────────┼──────────────────────┐
 *                  SQLCipher 库密钥        字段密钥               附件密钥
 * ```
 *
 * 解开保险库同时需要“这台设备的 Keystore”和“正确的口令”，缺一不可。
 */
class VaultManager(
    private val meta: VaultMetaStore,
    private val keystore: KeystoreManager,
    private val database: VaultDatabase,
    private val attachments: AttachmentStore,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(
        if (meta.isInitialized) VaultState.Locked else VaultState.Uninitialized,
    )
    val state: StateFlow<VaultState> = mutableState.asStateFlow()

    @Volatile
    var keys: SessionKeys? = null
        private set

    /** 解锁后、对外宣布 Unlocked 之前执行（仓库在这里加载数据）。 */
    var onUnlocked: (suspend () -> Unit)? = null

    /** 锁定或清空时执行（仓库在这里丢弃明文缓存）。可能在后台线程调用。 */
    var onLocked: (() -> Unit)? = null

    val isUnlocked: Boolean get() = mutableState.value == VaultState.Unlocked
    val pinEnabled: Boolean get() = meta.pinBlob != null
    val biometricEnabled: Boolean get() = meta.biometricBlob != null
    val failedAttempts: Int get() = meta.failCount
    val lockoutUntil: Long get() = meta.lockoutUntil

    // ── 创建与解锁 ────────────────────────────────────────────────

    suspend fun create(password: String) = withContext(Dispatchers.Default) {
        mutex.withLock {
            check(!meta.isInitialized) { "保险库已存在" }
            val vaultKey = AesGcm.newKey()
            val chars = Kdf.normalize(password)
            try {
                val envelope = PasswordEnvelope.seal(vaultKey, chars, PURPOSE_PASSWORD)
                meta.passwordBlob = keystore.wrap(envelope, AAD_PASSWORD)
                meta.failCount = 0
                meta.lockoutUntil = 0L
                openSession(vaultKey)
            } finally {
                chars.wipe()
            }
        }
    }

    suspend fun unlockWithPassword(password: String): UnlockResult =
        attempt(meta.passwordBlob, password, PURPOSE_PASSWORD, AAD_PASSWORD)

    suspend fun unlockWithPin(pin: String): UnlockResult =
        attempt(meta.pinBlob, pin, PURPOSE_PIN, AAD_PIN)

    private suspend fun attempt(
        wrapped: ByteArray?,
        secret: String,
        purpose: String,
        aad: ByteArray,
    ): UnlockResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (wrapped == null) return@withLock UnlockResult.Error("这种解锁方式尚未设置")
            if (isUnlocked) return@withLock UnlockResult.Success
            val now = System.currentTimeMillis()
            val until = meta.lockoutUntil
            if (until > now) return@withLock UnlockResult.LockedOut(until)

            // 先把这次尝试记为失败并同步落盘，再去校验；校验通过才清零。
            val count = meta.failCount + 1
            meta.failCount = count

            val chars = Kdf.normalize(secret)
            try {
                val envelope = keystore.unwrap(wrapped, aad)
                val vaultKey = PasswordEnvelope.open(envelope, chars, purpose)
                meta.failCount = 0
                meta.lockoutUntil = 0L
                openSession(vaultKey)
                UnlockResult.Success
            } catch (e: WrongSecretException) {
                onWrongSecret(count)
            } catch (e: Exception) {
                // 不是口令问题（Keystore / 数据库异常）：这次不计入失败次数。
                meta.failCount = count - 1
                UnlockResult.Error(e.message ?: "解锁失败")
            } finally {
                chars.wipe()
            }
        }
    }

    private fun onWrongSecret(count: Int): UnlockResult {
        val wipe = settings.current.wipeOnFailure
        if (wipe && count >= MAX_ATTEMPTS) {
            wipeLocked()
            return UnlockResult.Wiped
        }
        val cooldown = cooldownSeconds(count)
        if (cooldown > 0) meta.lockoutUntil = System.currentTimeMillis() + cooldown * 1000L
        return UnlockResult.Wrong(if (wipe) MAX_ATTEMPTS - count else null)
    }

    private suspend fun openSession(vaultKey: ByteArray) {
        val dbKey = Kdf.hkdf(vaultKey, "jinkui/db")
        val fieldKey = Kdf.hkdf(vaultKey, "jinkui/field")
        val attachmentKey = Kdf.hkdf(vaultKey, "jinkui/attachment")
        try {
            database.open(dbKey)
            keys = SessionKeys(vaultKey, FieldCipher(fieldKey), FieldCipher(attachmentKey))
        } catch (e: Exception) {
            vaultKey.wipe()
            throw e
        } finally {
            dbKey.wipe()
            fieldKey.wipe()
            attachmentKey.wipe()
        }
        try {
            onUnlocked?.invoke()
        } catch (e: Exception) {
            lockInternal()
            throw e
        }
        mutableState.value = VaultState.Unlocked
    }

    // ── 生物识别 ──────────────────────────────────────────────────

    /** 开启生物识别的第一步：拿到待认证的加密 Cipher。 */
    fun newBiometricEnrollCipher(): Cipher = keystore.newBiometricEncryptCipher()

    /** 认证通过后，用已解锁的 Cipher 封装保险库密钥。 */
    fun enableBiometric(cipher: Cipher) {
        val session = keys ?: throw IllegalStateException("金匮已锁定")
        val body = cipher.doFinal(session.vaultKey)
        meta.biometricBlob = cipher.iv + body
    }

    fun disableBiometric() {
        meta.biometricBlob = null
        keystore.deleteBiometricKey()
    }

    /** 解锁用的 Cipher；生物特征发生变更导致密钥失效时会自动关闭生物识别并返回 null。 */
    fun biometricUnlockCipher(): Cipher? {
        val blob = meta.biometricBlob ?: return null
        return try {
            keystore.biometricDecryptCipher(blob.copyOfRange(0, KeystoreManager.IV_BYTES))
        } catch (e: KeyPermanentlyInvalidatedException) {
            disableBiometric()
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun unlockWithBiometric(cipher: Cipher): UnlockResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (isUnlocked) return@withLock UnlockResult.Success
            val blob = meta.biometricBlob ?: return@withLock UnlockResult.Error("生物识别解锁未开启")
            try {
                val vaultKey = cipher.doFinal(blob, KeystoreManager.IV_BYTES, blob.size - KeystoreManager.IV_BYTES)
                meta.failCount = 0
                meta.lockoutUntil = 0L
                openSession(vaultKey)
                UnlockResult.Success
            } catch (e: Exception) {
                UnlockResult.Error("生物识别解锁失败，请使用主密码")
            }
        }
    }

    // ── PIN 与主密码管理（需已解锁）────────────────────────────────

    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val session = keys ?: throw IllegalStateException("金匮已锁定")
            val chars = Kdf.normalize(pin)
            try {
                val envelope = PasswordEnvelope.seal(session.vaultKey, chars, PURPOSE_PIN)
                meta.pinBlob = keystore.wrap(envelope, AAD_PIN)
            } finally {
                chars.wipe()
            }
        }
    }

    fun removePin() {
        meta.pinBlob = null
    }

    /** 校验主密码但不改变锁定状态，也不计入失败次数（用于已解锁状态下的二次确认）。 */
    suspend fun verifyPassword(password: String): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock { verifyLocked(password) }
    }

    /** 修改主密码：保险库密钥不变，只是换一个新的口令信封。 */
    suspend fun changePassword(oldPassword: String, newPassword: String): Boolean =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val session = keys ?: throw IllegalStateException("金匮已锁定")
                if (!verifyLocked(oldPassword)) return@withLock false
                val chars = Kdf.normalize(newPassword)
                try {
                    val envelope = PasswordEnvelope.seal(session.vaultKey, chars, PURPOSE_PASSWORD)
                    meta.passwordBlob = keystore.wrap(envelope, AAD_PASSWORD)
                } finally {
                    chars.wipe()
                }
                true
            }
        }

    private fun verifyLocked(password: String): Boolean {
        val wrapped = meta.passwordBlob ?: return false
        val chars = Kdf.normalize(password)
        return try {
            PasswordEnvelope.open(keystore.unwrap(wrapped, AAD_PASSWORD), chars, PURPOSE_PASSWORD).wipe()
            true
        } catch (e: WrongSecretException) {
            false
        } finally {
            chars.wipe()
        }
    }

    // ── 锁定与清空 ────────────────────────────────────────────────

    fun lock() {
        if (mutableState.value != VaultState.Unlocked) return
        mutableState.value = VaultState.Locked
        lockInternal()
    }

    private fun lockInternal() {
        runCatching { onLocked?.invoke() }
        keys?.wipe()
        keys = null
        database.close()
    }

    /** 永久清空本机全部数据：数据库、附件、密钥信封与 Keystore 密钥。 */
    suspend fun wipeAll() = withContext(Dispatchers.Default) {
        mutex.withLock { wipeLocked() }
    }

    private fun wipeLocked() {
        runCatching { onLocked?.invoke() }
        keys?.wipe()
        keys = null
        runCatching { database.destroy() }
        attachments.deleteAll()
        meta.clear()
        keystore.deleteAll()
        mutableState.value = VaultState.Uninitialized
    }

    companion object {
        const val MAX_ATTEMPTS = 10
        const val MIN_PASSWORD_LENGTH = 8
        const val MIN_PIN_LENGTH = 6
        const val MAX_PIN_LENGTH = 12
        private const val PURPOSE_PASSWORD = "jinkui/master-password"
        private const val PURPOSE_PIN = "jinkui/pin"
        private val AAD_PASSWORD = "jinkui/keystore/password".toByteArray()
        private val AAD_PIN = "jinkui/keystore/pin".toByteArray()

        /** 连续失败后的冷却：第 3 次起逐级加长。 */
        fun cooldownSeconds(failures: Int): Int = when {
            failures < 3 -> 0
            failures == 3 -> 5
            failures == 4 -> 15
            failures == 5 -> 30
            failures == 6 -> 60
            failures == 7 -> 120
            else -> 300
        }
    }
}
