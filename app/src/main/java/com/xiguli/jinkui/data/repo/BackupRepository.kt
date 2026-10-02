package com.xiguli.jinkui.data.repo

import android.content.Context
import android.net.Uri
import com.xiguli.jinkui.core.crypto.BackupCodec
import com.xiguli.jinkui.core.crypto.BackupFormatException
import com.xiguli.jinkui.core.crypto.Kdf
import com.xiguli.jinkui.core.crypto.WrongSecretException
import com.xiguli.jinkui.core.crypto.wipe
import com.xiguli.jinkui.core.security.VaultManager
import com.xiguli.jinkui.data.backup.BackupJson
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 加密备份的导出与导入。金匮不提供任何明文导出：
 * 离开本机的只有经主密码加密、带完整性校验的 .jkbak 文件。
 */
class BackupRepository(
    private val context: Context,
    private val vault: VaultManager,
    private val repository: VaultRepository,
) {
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "金匮备份-$stamp.${BackupCodec.FILE_EXTENSION}"
    }

    /** 导出前必须再次输入主密码；备份文件就用这个主密码加密。返回导出的卡片数。 */
    @Throws(WrongSecretException::class)
    suspend fun export(target: Uri, password: String): Int = withContext(Dispatchers.IO) {
        if (!vault.verifyPassword(password)) throw WrongSecretException("主密码不正确")
        val items = repository.snapshot()
        val payload = BackupJson.encode(items, System.currentTimeMillis())
        val chars = Kdf.normalize(password)
        val file = try {
            BackupCodec.encode(payload, chars)
        } finally {
            chars.wipe()
            payload.wipe()
        }
        val stream = context.contentResolver.openOutputStream(target, "wt")
            ?: throw java.io.IOException("无法写入所选位置")
        stream.use { it.write(file) }
        items.size
    }

    /** 导入：先校验文件格式与完整性（GCM 认证），全部通过后才写入本机。 */
    @Throws(WrongSecretException::class, BackupFormatException::class)
    suspend fun import(source: Uri, password: String): ImportSummary = withContext(Dispatchers.IO) {
        val file = readLimited(source)
        val chars = Kdf.normalize(password)
        val payload = try {
            BackupCodec.decode(file, chars)
        } finally {
            chars.wipe()
        }
        val items = try {
            BackupJson.decode(payload)
        } finally {
            payload.wipe()
        }
        repository.restore(items)
    }

    private fun readLimited(source: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw java.io.IOException("无法读取所选文件")
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_FILE_BYTES) throw BackupFormatException("文件过大，不是有效的金匮备份")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 256L * 1024 * 1024
    }
}
