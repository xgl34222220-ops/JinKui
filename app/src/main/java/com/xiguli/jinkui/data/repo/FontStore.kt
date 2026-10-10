package com.xiguli.jinkui.data.repo

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导入字体失败，[message] 直接给用户看。 */
class FontImportException(message: String) : Exception(message)

/**
 * 用户导入的字体文件。经系统文件选择器（SAF）读一次，复制到应用私有目录 `files/fonts/custom.font`，
 * 确认系统能把它加载成字体后才替换旧文件；之后每次启动从私有目录加载，不再访问原文件，也不需要存储权限。
 *
 * 字体不是用户数据，不进加密备份；“清空全部数据”时一并删除。
 */
class FontStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "fonts")
    private val file = File(dir, "custom.font")

    val hasCustom: Boolean get() = file.isFile && file.length() > 0

    /** 复制、校验并替换。返回字体的显示名（文件名去掉扩展名）。 */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        dir.mkdirs()
        val temp = File(dir, "incoming.tmp")
        try {
            val input = app.contentResolver.openInputStream(uri) ?: throw FontImportException("无法读取这个文件")
            input.use { source ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw FontImportException("字体文件太大了（上限 40 MB）")
                        out.write(buffer, 0, n)
                    }
                }
            }
            val header = ByteArray(4)
            val read = temp.inputStream().use { it.read(header) }
            if (read < 4 || !looksLikeFont(header)) throw FontImportException("这不是 .ttf / .otf 字体文件")
            loadTypeface(temp) ?: throw FontImportException("系统无法加载这个字体文件")
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
            }
            name
        } catch (e: IOException) {
            throw FontImportException("读取字体文件失败")
        } finally {
            temp.delete()
        }
    }

    /** 加载已导入的字体；没有或已损坏时返回 null（界面退回系统字体）。 */
    fun load(): Typeface? = if (hasCustom) loadTypeface(file) else null

    fun reset() {
        file.delete()
    }

    /** 清空全部数据时调用。 */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun loadTypeface(source: File): Typeface? = runCatching {
        // Typeface.Builder 在字体无效且没设回落字体时返回 null，不会悄悄给出默认字体
        Typeface.Builder(source).build()
    }.getOrNull()?.takeIf { it != Typeface.DEFAULT }

    private fun displayName(uri: Uri): String {
        val raw = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "导入的字体"
        return cleanName(raw)
    }

    companion object {
        const val MAX_BYTES = 40L * 1024 * 1024

        /** TrueType（00 01 00 00 / "true"）、OpenType CFF（"OTTO"）、字体集（"ttcf"）。 */
        fun looksLikeFont(header: ByteArray): Boolean {
            if (header.size < 4) return false
            val tag = String(header, 0, 4, Charsets.ISO_8859_1)
            val trueType = header[0] == 0.toByte() && header[1] == 1.toByte() && header[2] == 0.toByte() && header[3] == 0.toByte()
            return trueType || tag == "OTTO" || tag == "true" || tag == "ttcf"
        }

        fun cleanName(raw: String): String {
            val base = raw.substringAfterLast('/').substringBeforeLast('.').trim()
            return base.ifEmpty { "导入的字体" }.take(40)
        }
    }
}
