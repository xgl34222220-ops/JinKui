package com.xiguli.jinkui.core.crypto

import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Kdf {
    /** OWASP 对 PBKDF2-HMAC-SHA256 的建议值。迭代次数随密文一起保存，日后可上调。 */
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 10_000
    const val MAX_ITERATIONS = 5_000_000
    const val SALT_BYTES = 16

    /** 统一口令的 Unicode 形态，避免同一口令在不同输入法下得到不同密钥。 */
    fun normalize(password: String): CharArray =
        Normalizer.normalize(password, Normalizer.Form.NFKC).toCharArray()

    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, lengthBytes: Int = 32): ByteArray {
        require(iterations > 0) { "迭代次数必须为正" }
        val spec = PBEKeySpec(password, salt, iterations, lengthBytes * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** HKDF-SHA256（RFC 5869）：从保险库密钥派生出用途各自独立的子密钥。 */
    fun hkdf(ikm: ByteArray, info: ByteArray, lengthBytes: Int = 32, salt: ByteArray? = null): ByteArray {
        require(lengthBytes in 1..255 * 32) { "HKDF 输出长度超出范围" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt ?: ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(lengthBytes)
        var block = ByteArray(0)
        var filled = 0
        var counter = 1
        while (filled < lengthBytes) {
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            block = mac.doFinal()
            val take = minOf(block.size, lengthBytes - filled)
            System.arraycopy(block, 0, out, filled, take)
            filled += take
            counter++
        }
        prk.wipe()
        block.wipe()
        return out
    }

    fun hkdf(ikm: ByteArray, info: String, lengthBytes: Int = 32): ByteArray =
        hkdf(ikm, info.toByteArray(Charsets.UTF_8), lengthBytes)
}
