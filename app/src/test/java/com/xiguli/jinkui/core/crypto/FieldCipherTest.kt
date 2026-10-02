package com.xiguli.jinkui.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FieldCipherTest {
    private val cipher = FieldCipher(ByteArray(32) { (it * 3).toByte() })

    @Test
    fun roundTripKeepsUnicode() {
        val value = "密码：P@ssw0rd-金匮 🔐"
        val token = cipher.encrypt(value, "field:c1:f1")
        assertFalse(token.contains("P@ssw0rd"))
        assertEquals(value, cipher.decrypt(token, "field:c1:f1"))
    }

    @Test
    fun sameValueEncryptsDifferentlyEachTime() {
        assertNotEquals(cipher.encrypt("123456", "field:c1:f1"), cipher.encrypt("123456", "field:c1:f1"))
    }

    @Test
    fun ciphertextCannotBeMovedToAnotherField() {
        val token = cipher.encrypt("123456", "field:c1:f1")
        assertThrows(WrongSecretException::class.java) { cipher.decrypt(token, "field:c1:f2") }
        assertThrows(WrongSecretException::class.java) { cipher.decrypt(token, "field:c2:f1") }
    }

    @Test
    fun garbageInputIsRejectedNotCrashed() {
        assertThrows(WrongSecretException::class.java) { cipher.decrypt("not base64 !!", "x") }
        assertThrows(WrongSecretException::class.java) { cipher.decrypt("", "x") }
        assertThrows(WrongSecretException::class.java) { cipher.decrypt("AAAA", "x") }
    }

    @Test
    fun otherKeyCannotDecrypt() {
        val token = cipher.encrypt("123456", "field:c1:f1")
        val other = FieldCipher(ByteArray(32) { 1 })
        assertThrows(WrongSecretException::class.java) { other.decrypt(token, "field:c1:f1") }
    }

    @Test
    fun bytesRoundTripForAttachments() {
        val photo = ByteArray(100_000) { (it % 251).toByte() }
        val blob = cipher.encryptBytes(photo, "att:a1")
        assertEquals(photo.toList(), cipher.decryptBytes(blob, "att:a1").toList())
        assertThrows(WrongSecretException::class.java) { cipher.decryptBytes(blob, "att:a2") }
    }

    @Test
    fun wipedCipherNoLongerDecrypts() {
        val local = FieldCipher(ByteArray(32) { 5 })
        val token = local.encrypt("123456", "f")
        local.wipe()
        assertThrows(WrongSecretException::class.java) { local.decrypt(token, "f") }
    }
}
