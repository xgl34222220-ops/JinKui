package com.xiguli.jinkui.core.crypto

import java.security.SecureRandom

/** 用完即擦：密钥与口令的字节/字符数组在离开作用域前清零。 */
fun ByteArray.wipe() = fill(0)

fun CharArray.wipe() = fill('\u0000')

internal object Rng {
    val secure: SecureRandom = SecureRandom()

    fun bytes(size: Int): ByteArray = ByteArray(size).also(secure::nextBytes)
}

/** 口令或密文校验失败：口令错误，或数据已损坏 / 被篡改（GCM 无法区分两者）。 */
class WrongSecretException(message: String = "口令错误或数据已损坏") : Exception(message)

/** 备份文件格式不合法（魔数、版本、长度等结构性问题）。 */
class BackupFormatException(message: String) : Exception(message)
