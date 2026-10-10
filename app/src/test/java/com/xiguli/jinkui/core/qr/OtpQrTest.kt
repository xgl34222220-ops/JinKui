package com.xiguli.jinkui.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpQrTest {
    private val uri = "otpauth://totp/GitHub:alice@example.com?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30"

    /** 用 ZXing 自己的编码器生成一张“截图”：白底黑码，四周留白，每个模块 [scale] 像素。 */
    private fun render(text: String, scale: Int = 6, invert: Boolean = false): Triple<IntArray, Int, Int> {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 4))
        val w = matrix.width * scale
        val h = matrix.height * scale
        val dark = if (invert) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val light = if (invert) 0xFF101010.toInt() else 0xFFFFFFFF.toInt()
        val pixels = IntArray(w * h) { i ->
            val x = (i % w) / scale
            val y = (i / w) / scale
            if (matrix[x, y]) dark else light
        }
        return Triple(pixels, w, h)
    }

    @Test
    fun decodesQrFromPixels() {
        val (pixels, w, h) = render(uri)
        assertEquals(uri, QrDecoder.decode(pixels, w, h))
    }

    @Test
    fun decodesInvertedScreenshot() {
        // 深色模式下截的图：浅色码、深色底
        val (pixels, w, h) = render(uri, invert = true)
        assertEquals(uri, QrDecoder.decode(pixels, w, h))
    }

    @Test
    fun noQrReturnsNull() {
        val w = 200
        val h = 120
        assertNull(QrDecoder.decode(IntArray(w * h) { 0xFFFFFFFF.toInt() }, w, h))
        assertNull(QrDecoder.decode(IntArray(w * h) { i -> if ((i / 7) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }, w, h))
    }

    @Test
    fun interpretAcceptsTotpUri() {
        val result = OtpQr.interpret(uri)
        assertTrue(result is OtpQrResult.Ok)
        assertEquals(uri, (result as OtpQrResult.Ok).uri)
        // 前后空白去掉
        assertEquals(OtpQrResult.Ok(uri), OtpQr.interpret("  $uri\n"))
    }

    @Test
    fun interpretRejectsOthersWithoutLeakingContent() {
        val secretish = "otpauth-migration://offline?data=CjEKCkhlbGxvId6tvu8SGEV4YW1wbGU"
        val migration = OtpQr.interpret(secretish)
        assertTrue(migration is OtpQrResult.Rejected)
        assertFalse((migration as OtpQrResult.Rejected).message.contains("CjEK"))

        val hotp = OtpQr.interpret("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&counter=1")
        assertTrue(hotp is OtpQrResult.Rejected)
        assertTrue((hotp as OtpQrResult.Rejected).message.contains("HOTP"))

        val url = OtpQr.interpret("https://example.com/?secret=JBSWY3DPEHPK3PXP")
        assertTrue(url is OtpQrResult.Rejected)
        assertFalse((url as OtpQrResult.Rejected).message.contains("JBSWY"))

        assertTrue(OtpQr.interpret(null) is OtpQrResult.Rejected)
        assertTrue(OtpQr.interpret("   ") is OtpQrResult.Rejected)
        // 位数不支持
        assertTrue(OtpQr.interpret("otpauth://totp/x?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&digits=7") is OtpQrResult.Rejected)
    }

    @Test
    fun endToEndFromRenderedQr() {
        val (pixels, w, h) = render(uri, scale = 3)
        assertEquals(OtpQrResult.Ok(uri), OtpQr.interpret(QrDecoder.decode(pixels, w, h)))
    }
}
