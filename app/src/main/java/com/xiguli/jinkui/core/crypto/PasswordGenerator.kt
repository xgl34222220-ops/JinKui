package com.xiguli.jinkui.core.crypto

import java.security.SecureRandom
import kotlin.math.ln

data class GeneratorOptions(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    /** 去掉 0/O、1/l/I 这类容易看错的字符。 */
    val avoidAmbiguous: Boolean = true,
) {
    val hasAnySet: Boolean get() = upper || lower || digits || symbols
}

object PasswordGenerator {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 64
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*-_=+?.,:;~"
    private const val AMBIGUOUS = "0O1lI|"

    fun pools(options: GeneratorOptions): List<String> {
        val selected = buildList {
            if (options.upper) add(UPPER)
            if (options.lower) add(LOWER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }
        return if (options.avoidAmbiguous) {
            selected.map { pool -> pool.filterNot { it in AMBIGUOUS } }
        } else {
            selected
        }
    }

    fun generate(options: GeneratorOptions, random: SecureRandom = Rng.secure): String {
        val pools = pools(options)
        require(pools.isNotEmpty()) { "至少选择一种字符" }
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val all = pools.joinToString("")
        val chars = CharArray(length)
        // 每种已选字符至少出现一次，其余位置从全集中均匀抽取。
        pools.forEachIndexed { index, pool -> if (index < length) chars[index] = pool[random.nextInt(pool.length)] }
        for (i in pools.size until length) chars[i] = all[random.nextInt(all.length)]
        // Fisher–Yates 洗牌，抹掉“前几位各来自一种字符集”的位置规律。
        for (i in length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val swap = chars[i]
            chars[i] = chars[j]
            chars[j] = swap
        }
        return String(chars).also { chars.wipe() }
    }

    /** 熵的估算值（比特），用于强度提示。 */
    fun entropyBits(options: GeneratorOptions): Double {
        val size = pools(options).sumOf { it.length }
        if (size == 0) return 0.0
        return options.length.coerceIn(MIN_LENGTH, MAX_LENGTH) * ln(size.toDouble()) / ln(2.0)
    }

    /** 对用户自拟口令的粗略强度估计：按出现的字符类别估算字符集大小。 */
    fun estimateBits(password: String): Double {
        if (password.isEmpty()) return 0.0
        var size = 0
        if (password.any { it in 'a'..'z' }) size += 26
        if (password.any { it in 'A'..'Z' }) size += 26
        if (password.any { it.isDigit() }) size += 10
        if (password.any { it.code < 128 && !it.isLetterOrDigit() }) size += 32
        if (password.any { it.code >= 128 }) size += 2000
        val distinct = password.toSet().size.coerceAtLeast(1)
        val effective = minOf(password.length, distinct * 2)
        return effective * ln(size.coerceAtLeast(2).toDouble()) / ln(2.0)
    }
}
