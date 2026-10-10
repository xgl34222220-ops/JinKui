package com.xiguli.jinkui.core.crypto

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** TOTP 使用的 HMAC 算法。[key] 是 otpauth URI 里的写法。 */
enum class TotpAlgorithm(val key: String, val jca: String) {
    SHA1("SHA1", "HmacSHA1"),
    SHA256("SHA256", "HmacSHA256"),
    SHA512("SHA512", "HmacSHA512");

    companion object {
        fun of(text: String?): TotpAlgorithm? =
            if (text.isNullOrBlank()) SHA1 else entries.firstOrNull { it.key.equals(text.replace("-", ""), ignoreCase = true) }
    }
}

/**
 * 一个两步验证（TOTP）配置。[secret] 是解码后的原始密钥字节，只在内存里使用；
 * 落盘时整份配置以 otpauth URI 文本的形式作为敏感字段，和其他敏感字段一样做字段级 AES-GCM。
 */
class TotpConfig(
    val secret: ByteArray,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
    val issuer: String = "",
    val account: String = "",
) {
    init {
        require(secret.isNotEmpty()) { "密钥为空" }
        require(digits == 6 || digits == 8) { "位数只能是 6 或 8" }
        require(period == 30 || period == 60) { "周期只能是 30 或 60 秒" }
    }

    /** 规范化的 otpauth URI，作为字段值保存。 */
    fun toUri(): String {
        val label = when {
            issuer.isNotEmpty() && account.isNotEmpty() -> "$issuer:$account"
            else -> issuer.ifEmpty { account }
        }
        val params = buildList {
            add("secret=" + Base32.encode(secret))
            if (issuer.isNotEmpty()) add("issuer=" + encode(issuer))
            add("algorithm=" + algorithm.key)
            add("digits=$digits")
            add("period=$period")
        }
        return "otpauth://totp/" + encode(label) + "?" + params.joinToString("&")
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

/** 某一时刻的验证码与剩余秒数。 */
data class TotpCode(val code: String, val secondsLeft: Int, val period: Int)

/**
 * RFC 6238 TOTP（基于 RFC 4226 HOTP）。纯 Kotlin + JCA，不联网、不依赖 Android。
 */
object Totp {
    /** RFC 4226 HOTP：HMAC 后动态截断取 [digits] 位。 */
    fun hotp(secret: ByteArray, counter: Long, digits: Int = 6, algorithm: TotpAlgorithm = TotpAlgorithm.SHA1): String {
        val mac = Mac.getInstance(algorithm.jca)
        mac.init(SecretKeySpec(secret, "RAW"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val modulo = if (digits == 8) 100_000_000 else 1_000_000
        return (binary % modulo).toString().padStart(digits, '0')
    }

    /** [epochMillis] 时刻的验证码。时钟早于 1970 时按 0 计。 */
    fun code(config: TotpConfig, epochMillis: Long): TotpCode {
        val seconds = Math.floorDiv(epochMillis, 1000L).coerceAtLeast(0L)
        val counter = seconds / config.period
        val left = (config.period - (seconds % config.period)).toInt()
        return TotpCode(hotp(config.secret, counter, config.digits, config.algorithm), left, config.period)
    }

    /**
     * 解析用户输入：可以是 otpauth://totp/… URI，也可以是一段 Base32 密钥（允许空格、小写、不带填充）。
     * 不合法时抛 [IllegalArgumentException]，message 直接给用户看。
     */
    fun parse(input: String): TotpConfig {
        val text = input.trim()
        require(text.isNotEmpty()) { "请填写密钥" }
        if (!text.startsWith("otpauth:", ignoreCase = true)) {
            return TotpConfig(decodeSecret(text))
        }
        val rest = text.substringAfter(':').removePrefix("//")
        val type = rest.substringBefore('/').lowercase()
        require(type == "totp") { if (type == "hotp") "暂不支持基于计数器的 HOTP" else "不是 otpauth://totp 链接" }
        val afterType = rest.substringAfter('/', "")
        val label = decode(afterType.substringBefore('?'))
        val query = afterType.substringAfter('?', "")
        val params = query.split('&').filter { it.isNotEmpty() }.associate { part ->
            decode(part.substringBefore('=')).lowercase() to decode(part.substringAfter('=', ""))
        }
        val secret = decodeSecret(params["secret"] ?: throw IllegalArgumentException("链接里没有 secret"))
        val algorithm = TotpAlgorithm.of(params["algorithm"]) ?: throw IllegalArgumentException("不支持的算法：${params["algorithm"]}")
        val digits = params["digits"]?.toIntOrNull() ?: if (params.containsKey("digits")) -1 else 6
        require(digits == 6 || digits == 8) { "验证码位数只支持 6 或 8 位" }
        val period = params["period"]?.toIntOrNull() ?: if (params.containsKey("period")) -1 else 30
        require(period == 30 || period == 60) { "刷新周期只支持 30 或 60 秒" }
        val labelIssuer = if (label.contains(':')) label.substringBefore(':').trim() else ""
        val account = if (label.contains(':')) label.substringAfter(':').trim() else label.trim()
        val issuer = params["issuer"]?.trim().orEmpty().ifEmpty { labelIssuer }
        return TotpConfig(secret, algorithm, digits, period, issuer.take(64), account.take(128))
    }

    /** 能否解析；编辑页用来即时提示。 */
    fun errorOf(input: String): String? =
        if (input.isBlank()) null else try {
            parse(input)
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "密钥无效"
        }

    private fun decodeSecret(raw: String): ByteArray {
        val bytes = Base32.decode(raw) ?: throw IllegalArgumentException("密钥不是有效的 Base32（只含 A–Z 与 2–7）")
        require(bytes.size >= 10) { "密钥太短，至少 16 个字符" }
        require(bytes.size <= 128) { "密钥太长" }
        return bytes
    }

    private fun decode(text: String): String =
        try {
            URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("链接编码无效")
        }
}

/** RFC 4648 Base32（大写字母表），解码时忽略空格、连字符、大小写与结尾的 '='。 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    /** 不合法时返回 null。 */
    fun decode(text: String): ByteArray? {
        val clean = text.filterNot { it == ' ' || it == '-' || it == '\t' || it == '\n' }.trimEnd('=').uppercase()
        if (clean.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (ch in clean) {
            val value = ALPHABET.indexOf(ch)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
