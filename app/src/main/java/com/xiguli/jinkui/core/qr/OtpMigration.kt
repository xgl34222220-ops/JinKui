package com.xiguli.jinkui.core.qr

import com.xiguli.jinkui.core.crypto.TotpAlgorithm
import com.xiguli.jinkui.core.crypto.TotpConfig
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/** 批量导出码里的一个账号。[config] 为 null 时用不了，[problem] 说明原因（不含密钥）。 */
class MigrationEntry(
    val index: Int,
    val issuer: String,
    val account: String,
    val config: TotpConfig?,
    val problem: String?,
) {
    val usable: Boolean get() = config != null

    /** 预览里显示的名称：“发行方 · 账号”或其中之一。 */
    val title: String
        get() = issuer.ifBlank { account.ifBlank { "未命名账号" } }
}

/** 一张批量导出码的解析结果。[batchSize] > 1 时这是多张码中的第 [batchIndex] + 1 张。 */
class MigrationBatch(
    val entries: List<MigrationEntry>,
    val batchIndex: Int,
    val batchSize: Int,
) {
    val usableCount: Int get() = entries.count { it.usable }
}

/** 解析失败；[message] 直接给用户看，不含二维码内容。 */
class MigrationFormatException(message: String) : Exception(message)

/**
 * Google 身份验证器“导出账号”二维码（otpauth-migration://offline?data=…）的离线解析。
 *
 * data 参数是 Base64 编码的 protobuf（MigrationPayload）。这里按公开的字段编号手写了一个
 * 只读、限长的 protobuf 读取器，不引入 protobuf 库、不联网：
 *
 *  - MigrationPayload：1 = otp_parameters（重复）、2 = version、3 = batch_size、4 = batch_index、5 = batch_id
 *  - OtpParameters：1 = secret（bytes）、2 = name、3 = issuer、4 = algorithm（1 SHA1 / 2 SHA256 / 3 SHA512 / 4 MD5）、
 *    5 = digits（1 六位 / 2 八位）、6 = type（1 HOTP / 2 TOTP）、7 = counter
 *
 * 只接受 TOTP；HOTP、MD5、不认识的位数逐条标为不可用，不影响同一批里的其他账号。
 * 未知字段按 wire type 跳过；长度越界、嵌套异常一律当作格式错误，不会读出数组边界。
 */
object OtpMigration {
    private const val SCHEME = "otpauth-migration:"
    private const val MAX_DATA_CHARS = 64 * 1024
    private const val MAX_ENTRIES = 200

    /** 一个普通的 otpauth://totp 链接也包装成只有一个账号的批次，便于走同一套预览。 */
    fun single(uri: String): MigrationBatch {
        val config = com.xiguli.jinkui.core.crypto.Totp.parse(uri)
        return MigrationBatch(listOf(MigrationEntry(0, config.issuer, config.account, config, null)), 0, 1)
    }

    fun isMigration(text: String?): Boolean = text?.trim()?.startsWith(SCHEME, ignoreCase = true) == true

    @Throws(MigrationFormatException::class)
    fun parse(text: String): MigrationBatch {
        val raw = text.trim()
        if (!isMigration(raw)) throw MigrationFormatException("不是身份验证器的导出二维码")
        val query = try {
            URI(raw).rawQuery
        } catch (e: Exception) {
            null
        } ?: throw MigrationFormatException("导出二维码内容不完整")
        val encoded = query.split('&')
            .firstOrNull { it.startsWith("data=") }
            ?.removePrefix("data=")
            ?: throw MigrationFormatException("导出二维码里没有账号数据")
        if (encoded.length > MAX_DATA_CHARS) throw MigrationFormatException("导出二维码内容过长")
        val bytes = try {
            decodeBase64(URLDecoder.decode(encoded, "UTF-8"))
        } catch (e: IllegalArgumentException) {
            throw MigrationFormatException("导出二维码的数据无法解码")
        }
        try {
            return readPayload(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    /** 标准 Base64；有些生成器用 URL 安全字母表或省略补齐，一并容忍。 */
    private fun decodeBase64(text: String): ByteArray {
        val cleaned = text.trim().replace("\n", "").replace(" ", "+")
        return if (cleaned.contains('-') || cleaned.contains('_')) {
            Base64.getUrlDecoder().decode(cleaned)
        } else {
            Base64.getMimeDecoder().decode(cleaned)
        }
    }

    private fun readPayload(bytes: ByteArray): MigrationBatch {
        val reader = ProtoReader(bytes, 0, bytes.size)
        val entries = ArrayList<MigrationEntry>()
        var batchSize = 1
        var batchIndex = 0
        while (reader.hasMore()) {
            val (field, wire) = reader.tag()
            when {
                field == 1 && wire == ProtoReader.LEN -> {
                    if (entries.size >= MAX_ENTRIES) throw MigrationFormatException("导出二维码里的账号数量异常")
                    val (start, end) = reader.lengthDelimited()
                    entries += readEntry(bytes, start, end, entries.size)
                }
                field == 3 && wire == ProtoReader.VARINT -> batchSize = reader.varint().toInt().coerceIn(1, 1000)
                field == 4 && wire == ProtoReader.VARINT -> batchIndex = reader.varint().toInt().coerceIn(0, 999)
                else -> reader.skip(wire)
            }
        }
        if (entries.isEmpty()) throw MigrationFormatException("导出二维码里没有账号")
        return MigrationBatch(entries, batchIndex, batchSize)
    }

    private fun readEntry(bytes: ByteArray, start: Int, end: Int, index: Int): MigrationEntry {
        val reader = ProtoReader(bytes, start, end)
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        var algorithm = 0
        var digits = 0
        var type = 0
        while (reader.hasMore()) {
            val (field, wire) = reader.tag()
            when {
                field == 1 && wire == ProtoReader.LEN -> {
                    val (s, e) = reader.lengthDelimited()
                    secret?.fill(0)
                    secret = bytes.copyOfRange(s, e)
                }
                field == 2 && wire == ProtoReader.LEN -> name = reader.string()
                field == 3 && wire == ProtoReader.LEN -> issuer = reader.string()
                field == 4 && wire == ProtoReader.VARINT -> algorithm = reader.varint().toInt()
                field == 5 && wire == ProtoReader.VARINT -> digits = reader.varint().toInt()
                field == 6 && wire == ProtoReader.VARINT -> type = reader.varint().toInt()
                else -> reader.skip(wire)
            }
        }
        // name 常写成 “发行方:账号”；发行方单独给了时去掉重复的前缀
        val cleanIssuer = issuer.trim().take(60)
        var account = name.trim()
        if (cleanIssuer.isNotEmpty() && account.startsWith("$cleanIssuer:")) account = account.removePrefix("$cleanIssuer:").trim()
        account = account.take(120)
        val problem = when {
            type == 1 -> "计数器型（HOTP）验证码，金匮只支持按时间的验证码"
            type != 0 && type != 2 -> "不认识的验证码类型"
            secret == null || secret.isEmpty() -> "缺少密钥"
            algorithm == 4 -> "MD5 算法不受支持"
            algorithm !in 0..3 -> "不认识的算法"
            digits !in 0..2 -> "不认识的位数"
            else -> null
        }
        val config = if (problem == null) {
            TotpConfig(
                secret = secret!!,
                algorithm = when (algorithm) {
                    2 -> TotpAlgorithm.SHA256
                    3 -> TotpAlgorithm.SHA512
                    else -> TotpAlgorithm.SHA1
                },
                digits = if (digits == 2) 8 else 6,
                period = 30,
                issuer = cleanIssuer,
                account = account,
            )
        } else {
            secret?.fill(0)
            null
        }
        return MigrationEntry(index, cleanIssuer, account, config, problem)
    }
}

/** 只读、带边界检查的最小 protobuf 读取器。越界一律抛 [MigrationFormatException]。 */
private class ProtoReader(private val bytes: ByteArray, private var pos: Int, private val end: Int) {
    fun hasMore() = pos < end

    fun tag(): Pair<Int, Int> {
        val key = varint()
        val field = (key ushr 3).toInt()
        if (field <= 0) bad()
        return field to (key and 7).toInt()
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (pos >= end) bad()
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        bad()
    }

    fun lengthDelimited(): Pair<Int, Int> {
        val length = varint()
        if (length < 0 || length > (end - pos).toLong()) bad()
        val start = pos
        pos += length.toInt()
        return start to pos
    }

    fun string(): String {
        val (s, e) = lengthDelimited()
        return String(bytes, s, e - s, Charsets.UTF_8)
    }

    fun skip(wire: Int) {
        when (wire) {
            VARINT -> varint()
            I64 -> advance(8)
            LEN -> lengthDelimited()
            I32 -> advance(4)
            else -> bad()
        }
    }

    private fun advance(n: Int) {
        if (end - pos < n) bad()
        pos += n
    }

    private fun bad(): Nothing = throw MigrationFormatException("导出二维码的数据已损坏")

    companion object {
        const val VARINT = 0
        const val I64 = 1
        const val LEN = 2
        const val I32 = 5
    }
}
