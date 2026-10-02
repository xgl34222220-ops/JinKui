package com.xiguli.jinkui.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val iterations = Kdf.MIN_ITERATIONS
    private val payload = """{"app":"jinkui","cards":[{"title":"招商银行","no":"6225880000000000"}]}""".toByteArray()

    @Test
    fun roundTripWithCorrectPassword() {
        val file = BackupCodec.encode(payload, "master".toCharArray(), iterations)
        assertTrue(BackupCodec.looksLikeBackup(file))
        assertArrayEquals(payload, BackupCodec.decode(file, "master".toCharArray()))
    }

    @Test
    fun fileNeverContainsPlaintext() {
        val file = BackupCodec.encode(payload, "master".toCharArray(), iterations)
        val text = String(file, Charsets.ISO_8859_1)
        assertFalse(text.contains("6225880000000000"))
        assertFalse(text.contains("jinkui"))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val file = BackupCodec.encode(payload, "master".toCharArray(), iterations)
        assertThrows(WrongSecretException::class.java) { BackupCodec.decode(file, "Master".toCharArray()) }
    }

    @Test
    fun anyBitFlipFailsIntegrityCheck() {
        val file = BackupCodec.encode(payload, "master".toCharArray(), iterations)
        // 头部（盐）、密文中段、认证标签各改一位。
        for (index in listOf(12, file.size / 2 + 10, file.size - 1)) {
            val tampered = file.copyOf()
            tampered[index] = (tampered[index].toInt() xor 0x80).toByte()
            assertThrows(WrongSecretException::class.java) { BackupCodec.decode(tampered, "master".toCharArray()) }
        }
    }

    @Test
    fun truncatedFileFailsIntegrityCheck() {
        val file = BackupCodec.encode(payload, "master".toCharArray(), iterations)
        val truncated = file.copyOf(file.size - 5)
        assertThrows(WrongSecretException::class.java) { BackupCodec.decode(truncated, "master".toCharArray()) }
    }

    @Test
    fun foreignFilesAreRefusedWithFormatError() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(ByteArray(200), "x".toCharArray()) }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("hello".toByteArray(), "x".toCharArray()) }
        assertFalse(BackupCodec.looksLikeBackup(ByteArray(200)))
    }

    @Test
    fun unknownVersionIsRefused() {
        val file = BackupCodec.encode(payload, "master".toCharArray(), iterations)
        file[4] = 9
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(file, "master".toCharArray()) }
    }

    @Test
    fun outOfRangeIterationsAreRefused() {
        val file = BackupCodec.encode(payload, "master".toCharArray(), iterations)
        file[5] = 0x7F
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(file, "master".toCharArray()) }
    }

    @Test
    fun largePayloadRoundTrips() {
        val big = ByteArray(2_000_000) { (it * 31 % 256).toByte() }
        val file = BackupCodec.encode(big, "master".toCharArray(), iterations)
        assertArrayEquals(big, BackupCodec.decode(file, "master".toCharArray()))
    }
}
