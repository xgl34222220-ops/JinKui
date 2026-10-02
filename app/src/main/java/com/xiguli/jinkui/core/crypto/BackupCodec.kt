package com.xiguli.jinkui.core.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 加密备份文件（.jkbak）。
 *
 * 布局：魔数 "JKBK"(4) ‖ 版本(1) ‖ 迭代次数(4) ‖ 盐(16) ‖ AES-256-GCM(gzip(内容))
 * 整个头部作为 AAD：头部或密文任何一个比特被改动，导入时都会校验失败。
 * 备份密钥只由主密码派生，与本机 Keystore 无关，因此换手机后仍可用主密码恢复。
 */
object BackupCodec {
    private val MAGIC = byteArrayOf('J'.code.toByte(), 'K'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    private const val HEADER = 4 + 1 + 4 + Kdf.SALT_BYTES
    const val FILE_EXTENSION = "jkbak"

    fun looksLikeBackup(file: ByteArray): Boolean =
        file.size >= HEADER + AesGcm.OVERHEAD && file.copyOfRange(0, 4).contentEquals(MAGIC)

    fun encode(payload: ByteArray, password: CharArray, iterations: Int = Kdf.DEFAULT_ITERATIONS): ByteArray {
        val salt = Rng.bytes(Kdf.SALT_BYTES)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).putInt(iterations).put(salt).array()
        val key = Kdf.pbkdf2(password, salt, iterations)
        try {
            return header + AesGcm.encrypt(key, gzip(payload), header)
        } finally {
            key.wipe()
        }
    }

    @Throws(BackupFormatException::class, WrongSecretException::class)
    fun decode(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < HEADER + AesGcm.OVERHEAD) throw BackupFormatException("文件过短，不是金匮备份")
        if (!file.copyOfRange(0, 4).contentEquals(MAGIC)) throw BackupFormatException("不是金匮备份文件")
        if (file[4] != VERSION) throw BackupFormatException("备份版本 ${file[4]} 暂不支持，请升级金匮")
        val buffer = ByteBuffer.wrap(file, 5, HEADER - 5)
        val iterations = buffer.int
        if (iterations !in Kdf.MIN_ITERATIONS..Kdf.MAX_ITERATIONS) {
            throw BackupFormatException("备份参数异常，文件可能已损坏")
        }
        val salt = ByteArray(Kdf.SALT_BYTES).also { buffer.get(it) }
        val header = file.copyOfRange(0, HEADER)
        val key = Kdf.pbkdf2(password, salt, iterations)
        val packed = try {
            AesGcm.decrypt(key, file.copyOfRange(HEADER, file.size), header)
        } catch (e: WrongSecretException) {
            throw WrongSecretException("主密码不正确，或备份文件已损坏 / 被篡改")
        } finally {
            key.wipe()
        }
        return try {
            gunzip(packed)
        } catch (e: java.io.IOException) {
            throw BackupFormatException("备份内容无法解压")
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
