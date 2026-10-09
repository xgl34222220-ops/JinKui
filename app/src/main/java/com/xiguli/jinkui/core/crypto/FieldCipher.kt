package com.xiguli.jinkui.core.crypto

import java.util.Base64

/**
 * 字段级加密：在 SQLCipher 整库加密之内，再对每个字段值、备注和附件单独做 AES-256-GCM。
 *
 * [context] 作为 AAD 参与认证（例如 "field:<卡片ID>:<字段ID>"），
 * 所以一段密文被挪到别的记录上时会解密失败。
 */
class FieldCipher(key: ByteArray) {
    private val key = key.copyOf()

    /** 一旦清零就永久作废。先置位再清零，所以加密结束后再查一次就能发现“用了半截被清零的密钥”。 */
    @Volatile
    private var wiped = false

    val isWiped: Boolean get() = wiped

    fun encrypt(plain: String, context: String): String =
        Base64.getEncoder().encodeToString(encryptBytes(plain.toByteArray(Charsets.UTF_8), context))

    @Throws(WrongSecretException::class)
    fun decrypt(token: String, context: String): String {
        val raw = try {
            Base64.getDecoder().decode(token)
        } catch (e: IllegalArgumentException) {
            throw WrongSecretException("密文编码无效")
        }
        return String(decryptBytes(raw, context), Charsets.UTF_8)
    }

    fun encryptBytes(plain: ByteArray, context: String): ByteArray {
        ensureUsable()
        val sealed = byteArrayOf(VERSION) + AesGcm.encrypt(key, plain, aad(context))
        // 加密途中被锁定：结果可能是用（部分）清零的密钥算出来的，绝不能交给调用方写盘。
        ensureUsable()
        return sealed
    }

    @Throws(WrongSecretException::class)
    fun decryptBytes(blob: ByteArray, context: String): ByteArray {
        ensureUsable()
        if (blob.isEmpty() || blob[0] != VERSION) throw WrongSecretException("不支持的密文版本")
        val plain = AesGcm.decrypt(key, blob.copyOfRange(1, blob.size), aad(context))
        ensureUsable()
        return plain
    }

    fun wipe() {
        wiped = true
        key.wipe()
    }

    private fun ensureUsable() {
        if (wiped) throw IllegalStateException("金匮已锁定")
    }

    private fun aad(context: String) = ("jinkui/v1/$context").toByteArray(Charsets.UTF_8)

    private companion object {
        const val VERSION: Byte = 1
    }
}
