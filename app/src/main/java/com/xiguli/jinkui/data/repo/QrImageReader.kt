package com.xiguli.jinkui.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.xiguli.jinkui.core.qr.QrDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从用户在相册 / 文件里选的一张图片中识别二维码。
 * 通过系统选择器拿到单张图片的临时读取授权，不需要存储或相机权限；
 * 图片只在内存里解码，用完即回收、像素数组清零，不落盘、不进附件。
 */
object QrImageReader {
    private const val MAX_SOURCE_BYTES = 30 * 1024 * 1024
    private val EDGES = intArrayOf(1600, 800)

    suspend fun read(context: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
        val raw = withContext(Dispatchers.IO) {
            (context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("无法读取图片"))
                .use { ImageImporter.readLimited(it, MAX_SOURCE_BYTES) }
        }
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw java.io.IOException("不是可识别的图片")
            // 先用较大的尺寸找，找不到再缩小一档（大图里很小的码，缩小后反而更好认）
            for (edge in EDGES) {
                val text = decodeAt(raw, bounds.outWidth, bounds.outHeight, edge)
                if (text != null) return@withContext text
            }
            null
        } finally {
            raw.fill(0)
        }
    }

    private fun decodeAt(raw: ByteArray, width: Int, height: Int, maxEdge: Int): String? {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            raw, 0, raw.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        return try {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            QrDecoder.decode(pixels, bitmap.width, bitmap.height)
        } finally {
            pixels.fill(0)
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }
}
