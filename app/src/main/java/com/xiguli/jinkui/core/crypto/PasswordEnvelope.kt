package com.xiguli.jinkui.core.crypto

import java.nio.ByteBuffer

/**
 * 用口令（主密码或 PIN）封装保险库密钥。
 *
 * 布局：版本(1) ‖ 迭代次数(4, 大端) ‖ 盐(16) ‖ AES-GCM(IV ‖ 密文 ‖ 标签)
 * 头部与用途标签一起作为 AAD，篡改迭代次数或把主密码信封当作 PIN 信封使用都会校验失败。
 * 口令是否正确由 GCM 标签判定，本机不保存任何口令哈希。
 */
object PasswordEnvelope {
    private const val VERSION: Byte = 1
    private const val HEADER = 1 + 4 + Kdf.SALT_BYTES

    fun seal(
        vaultKey: ByteArray,
        password: CharArray,
        purpose: String,
        iterations: Int = Kdf.DEFAULT_ITERATIONS,
    ): ByteArray {
        val salt = Rng.bytes(Kdf.SALT_BYTES)
        val header = ByteBuffer.allocate(HEADER).put(VERSION).putInt(iterations).put(salt).array()
        val kek = Kdf.pbkdf2(password, salt, iterations)
        try {
            return header + AesGcm.encrypt(kek, vaultKey, aad(header, purpose))
        } finally {
            kek.wipe()
        }
    }

    @Throws(WrongSecretException::class)
    fun open(blob: ByteArray, password: CharArray, purpose: String): ByteArray {
        if (blob.size < HEADER + AesGcm.OVERHEAD || blob[0] != VERSION) {
            throw WrongSecretException("密钥信封格式无效")
        }
        val buffer = ByteBuffer.wrap(blob, 1, HEADER - 1)
        val iterations = buffer.int
        if (iterations !in Kdf.MIN_ITERATIONS..Kdf.MAX_ITERATIONS) {
            throw WrongSecretException("密钥信封参数无效")
        }
        val salt = ByteArray(Kdf.SALT_BYTES).also { buffer.get(it) }
        val header = blob.copyOfRange(0, HEADER)
        val kek = Kdf.pbkdf2(password, salt, iterations)
        try {
            return AesGcm.decrypt(kek, blob.copyOfRange(HEADER, blob.size), aad(header, purpose))
        } finally {
            kek.wipe()
        }
    }

    private fun aad(header: ByteArray, purpose: String) = header + purpose.toByteArray(Charsets.UTF_8)
}
