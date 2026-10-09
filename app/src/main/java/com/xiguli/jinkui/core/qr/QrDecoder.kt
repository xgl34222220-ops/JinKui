package com.xiguli.jinkui.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.xiguli.jinkui.core.crypto.Totp

/**
 * 离线识别图片里的二维码（ZXing core，纯 Java，不联网、不需要相机）。
 * 只接收已经解码好的 ARGB 像素；读图、缩放在 [com.xiguli.jinkui.data.repo.QrImageReader] 里。
 */
object QrDecoder {
    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.CHARACTER_SET to "UTF-8",
    )

    /** 找到二维码时返回其中的文字；找不到返回 null。依次尝试两种二值化、以及反色（深底浅码的截图）。 */
    fun decode(argb: IntArray, width: Int, height: Int): String? {
        require(width > 0 && height > 0 && argb.size >= width * height) { "像素尺寸不对" }
        val source: LuminanceSource = RGBLuminanceSource(width, height, argb)
        for (candidate in listOf(source, source.invert())) {
            for (binarizer in listOf(HybridBinarizer(candidate), GlobalHistogramBinarizer(candidate))) {
                try {
                    return QRCodeReader().decode(BinaryBitmap(binarizer), hints).text
                } catch (e: NotFoundException) {
                    continue
                } catch (e: ReaderException) {
                    continue
                }
            }
        }
        return null
    }
}

/** 二维码内容对“两步验证码”字段意味着什么。 */
sealed interface OtpQrResult {
    /** 可以直接填进字段的 otpauth://totp 链接。 */
    data class Ok(val uri: String) : OtpQrResult
    /** Google 身份验证器的批量导出码：一批账号，让用户预览后选择要添加哪些。 */
    class Batch(val batch: MigrationBatch) : OtpQrResult
    /** 认得出是什么，但用不了；[message] 直接给用户看。 */
    data class Rejected(val message: String) : OtpQrResult
}

object OtpQr {
    /**
     * 判断识别出的文字能不能当作两步验证码：必须是 Totp 能解析的 otpauth://totp 链接；
     * Google 身份验证器的批量导出（otpauth-migration）离线解析成一批账号（0.9.0 起）；HOTP、普通网址等给出明确提示。
     * 失败时的提示里不会带上二维码内容本身（里面可能有密钥）。
     */
    fun interpret(text: String?): OtpQrResult {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return OtpQrResult.Rejected("没有在图片里找到二维码，换一张更清晰、只含二维码的截图试试")
        if (OtpMigration.isMigration(value)) {
            return try {
                OtpQrResult.Batch(OtpMigration.parse(value))
            } catch (e: MigrationFormatException) {
                OtpQrResult.Rejected(e.message ?: "导出二维码无法识别")
            }
        }
        if (!value.startsWith("otpauth:", ignoreCase = true)) {
            return OtpQrResult.Rejected("这个二维码不是两步验证码（应以 otpauth:// 开头）")
        }
        val error = Totp.errorOf(value)
        return if (error == null) OtpQrResult.Ok(value) else OtpQrResult.Rejected(error)
    }
}
