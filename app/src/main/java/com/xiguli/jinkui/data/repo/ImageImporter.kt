package com.xiguli.jinkui.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.PendingAttachment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把用户选的证件照片读进内存、缩到合适尺寸并重新编码为 JPEG。
 * 重新编码会丢掉原图的 EXIF（含拍摄地点），明文不落盘，保存卡片时才加密写入。
 */
object ImageImporter {
    private const val MAX_SOURCE_BYTES = 30 * 1024 * 1024
    private const val MAX_EDGE = 2048
    private const val JPEG_QUALITY = 88

    suspend fun load(context: Context, uri: Uri): PendingAttachment = withContext(Dispatchers.IO) {
        val raw = (context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("无法读取图片"))
            .use { readLimited(it, MAX_SOURCE_BYTES) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw java.io.IOException("不是可识别的图片")

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw java.io.IOException("图片解码失败")

        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotationOf(raw))
        }
        val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (bitmap !== decoded) decoded.recycle()
        bitmap.recycle()

        PendingAttachment(
            id = CardTemplates.newId(),
            name = "照片.jpg",
            mime = "image/jpeg",
            bytes = out.toByteArray(),
        )
    }

    /** 边读边计数：超过上限立即停下，不会先把一个超大文件整个读进内存再判断。 */
    internal fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) throw java.io.IOException("图片过大（上限 ${limit / (1024 * 1024)} MB）")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun rotationOf(raw: ByteArray): Float = runCatching {
        when (ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
}
