package com.xiguli.jinkui.data.db

import android.content.Context
import java.io.File

/** 附件密文文件。写入前已由附件密钥做 AES-256-GCM，这里只负责读写字节。 */
class AttachmentStore(context: Context) {
    private val directory = File(context.filesDir, "attachments")

    fun write(id: String, cipherBytes: ByteArray) {
        directory.mkdirs()
        val target = file(id)
        val temp = File(directory, "$id.tmp")
        temp.writeBytes(cipherBytes)
        if (!temp.renameTo(target)) {
            target.writeBytes(cipherBytes)
            temp.delete()
        }
    }

    fun read(id: String): ByteArray = file(id).readBytes()

    fun delete(id: String) {
        runCatching { file(id).delete() }
    }

    fun deleteAll() {
        runCatching { directory.deleteRecursively() }
    }

    private fun file(id: String): File {
        require(ID_PATTERN.matches(id)) { "非法附件 ID" }
        return File(directory, "$id.bin")
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
    }
}
