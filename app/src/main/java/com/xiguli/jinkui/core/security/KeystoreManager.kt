package com.xiguli.jinkui.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore 中的密钥不存在或无法使用（例如系统凭据被清除）。 */
class KeystoreUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Android Keystore 中的两把 AES-256-GCM 密钥，均不可导出，密钥材料永不进入应用进程：
 *
 *  - 主密钥：包裹“口令信封”，把保险库绑定在这台设备上。
 *  - 生物识别密钥：每次使用都必须通过强生物识别认证；新增指纹 / 面容后自动失效。
 *
 * 设备带 StrongBox 安全芯片时优先在其中生成，否则退回 TEE。
 */
class KeystoreManager {
    private val store: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    /** 用主密钥加密。返回 IV(12) ‖ 密文 ‖ 标签。 */
    fun wrap(plain: ByteArray, aad: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(create = true))
            cipher.updateAAD(aad)
            val body = cipher.doFinal(plain)
            return cipher.iv + body
        } catch (e: GeneralSecurityException) {
            throw KeystoreUnavailableException("无法使用设备密钥库", e)
        }
    }

    fun unwrap(blob: ByteArray, aad: ByteArray): ByteArray {
        if (blob.size <= IV_BYTES) throw KeystoreUnavailableException("密钥数据不完整")
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, masterKey(create = false), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
            cipher.updateAAD(aad)
            return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw KeystoreUnavailableException("设备密钥不可用，无法解锁", e)
        }
    }

    /** 新建生物识别密钥并返回加密用 Cipher；需经 BiometricPrompt 认证后才能 doFinal。 */
    fun newBiometricEncryptCipher(): Cipher {
        deleteBiometricKey()
        val key = generate(BIOMETRIC_ALIAS) {
            setUserAuthenticationRequired(true)
            setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
        }
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** 可能抛出 KeyPermanentlyInvalidatedException（生物特征有变更），调用方应据此关闭生物识别解锁。 */
    fun biometricDecryptCipher(iv: ByteArray): Cipher {
        val key = store.getKey(BIOMETRIC_ALIAS, null) as? SecretKey
            ?: throw KeystoreUnavailableException("生物识别密钥不存在")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
    }

    fun deleteBiometricKey() {
        runCatching { store.deleteEntry(BIOMETRIC_ALIAS) }
    }

    fun deleteAll() {
        runCatching { store.deleteEntry(BIOMETRIC_ALIAS) }
        runCatching { store.deleteEntry(MASTER_ALIAS) }
    }

    private fun masterKey(create: Boolean): SecretKey {
        (store.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) throw KeystoreUnavailableException("设备主密钥不存在")
        return generate(MASTER_ALIAS) {}
    }

    private fun generate(alias: String, configure: KeyGenParameterSpec.Builder.() -> Unit): SecretKey {
        fun build(strongBox: Boolean): SecretKey {
            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
            builder.configure()
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            generator.init(builder.build())
            return generator.generateKey()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return build(strongBox = true)
            } catch (e: Exception) {
                // 没有 StrongBox 或其不支持该参数：退回 TEE。
                runCatching { store.deleteEntry(alias) }
            }
        }
        return build(strongBox = false)
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MASTER_ALIAS = "jinkui.master.v1"
        private const val BIOMETRIC_ALIAS = "jinkui.biometric.v1"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
