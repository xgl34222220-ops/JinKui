package com.xiguli.jinkui.core.crypto

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM。输出布局：IV(12) ‖ 密文 ‖ 认证标签(16)。
 * 每次加密都使用新的随机 IV；AAD 用来把密文绑定到它所属的上下文。
 */
object AesGcm {
    const val KEY_BYTES = 32
    const val IV_BYTES = 12
    const val TAG_BITS = 128
    const val OVERHEAD = IV_BYTES + TAG_BITS / 8
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun newKey(): ByteArray = Rng.bytes(KEY_BYTES)

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 需要 32 字节密钥" }
        val iv = Rng.bytes(IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        if (aad != null) cipher.updateAAD(aad)
        val body = cipher.doFinal(plaintext)
        return iv + body
    }

    @Throws(WrongSecretException::class)
    fun decrypt(key: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        require(key.size == KEY_BYTES) { "AES-256 需要 32 字节密钥" }
        if (blob.size < OVERHEAD) throw WrongSecretException("密文长度不足")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES),
        )
        if (aad != null) cipher.updateAAD(aad)
        return try {
            cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (e: AEADBadTagException) {
            throw WrongSecretException()
        }
    }
}
