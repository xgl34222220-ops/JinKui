package com.xiguli.jinkui.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun roundTripRestoresPlaintext() {
        val plain = "6222 0000 1234 5678".toByteArray()
        val blob = AesGcm.encrypt(key, plain, "ctx".toByteArray())
        assertEquals(plain.size + AesGcm.OVERHEAD, blob.size)
        assertArrayEquals(plain, AesGcm.decrypt(key, blob, "ctx".toByteArray()))
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        val plain = ByteArray(64)
        val a = AesGcm.encrypt(key, plain)
        val b = AesGcm.encrypt(key, plain)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(0, AesGcm.IV_BYTES).contentEquals(b.copyOfRange(0, AesGcm.IV_BYTES)))
    }

    @Test
    fun flippingAnyByteIsRejected() {
        val blob = AesGcm.encrypt(key, "secret".toByteArray())
        for (index in blob.indices) {
            val tampered = blob.copyOf()
            tampered[index] = (tampered[index].toInt() xor 0x01).toByte()
            assertThrows(WrongSecretException::class.java) { AesGcm.decrypt(key, tampered) }
        }
    }

    @Test
    fun wrongKeyIsRejected() {
        val blob = AesGcm.encrypt(key, "secret".toByteArray())
        val other = ByteArray(32) { 7 }
        assertThrows(WrongSecretException::class.java) { AesGcm.decrypt(other, blob) }
    }

    @Test
    fun aadMismatchIsRejected() {
        val blob = AesGcm.encrypt(key, "secret".toByteArray(), "card-a".toByteArray())
        assertThrows(WrongSecretException::class.java) { AesGcm.decrypt(key, blob, "card-b".toByteArray()) }
        assertThrows(WrongSecretException::class.java) { AesGcm.decrypt(key, blob, null) }
    }

    @Test
    fun truncatedBlobIsRejected() {
        assertThrows(WrongSecretException::class.java) { AesGcm.decrypt(key, ByteArray(10)) }
    }

    @Test
    fun shortKeyIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { AesGcm.encrypt(ByteArray(16), ByteArray(1)) }
    }

    @Test
    fun emptyPlaintextRoundTrips() {
        val blob = AesGcm.encrypt(key, ByteArray(0))
        assertEquals(0, AesGcm.decrypt(key, blob).size)
    }
}
