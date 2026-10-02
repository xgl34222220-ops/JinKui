package com.xiguli.jinkui.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PasswordEnvelopeTest {
    private val iterations = Kdf.MIN_ITERATIONS
    private val vaultKey = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun correctPasswordOpensEnvelope() {
        val blob = PasswordEnvelope.seal(vaultKey, "correct horse".toCharArray(), "pw", iterations)
        assertArrayEquals(vaultKey, PasswordEnvelope.open(blob, "correct horse".toCharArray(), "pw"))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val blob = PasswordEnvelope.seal(vaultKey, "correct horse".toCharArray(), "pw", iterations)
        assertThrows(WrongSecretException::class.java) {
            PasswordEnvelope.open(blob, "correct horsf".toCharArray(), "pw")
        }
    }

    @Test
    fun purposeIsBound() {
        val blob = PasswordEnvelope.seal(vaultKey, "123456".toCharArray(), "pin", iterations)
        assertThrows(WrongSecretException::class.java) { PasswordEnvelope.open(blob, "123456".toCharArray(), "pw") }
    }

    @Test
    fun tamperedIterationCountIsRejected() {
        val blob = PasswordEnvelope.seal(vaultKey, "pw".toCharArray(), "pw", iterations)
        val tampered = blob.copyOf()
        tampered[4] = (tampered[4].toInt() xor 0x01).toByte()
        assertThrows(WrongSecretException::class.java) { PasswordEnvelope.open(tampered, "pw".toCharArray(), "pw") }
    }

    @Test
    fun absurdIterationCountIsRefusedBeforeDerivation() {
        val blob = PasswordEnvelope.seal(vaultKey, "pw".toCharArray(), "pw", iterations)
        val tampered = blob.copyOf()
        tampered[1] = 0x7F
        assertThrows(WrongSecretException::class.java) { PasswordEnvelope.open(tampered, "pw".toCharArray(), "pw") }
    }

    @Test
    fun saltMakesEnvelopesUnique() {
        val a = PasswordEnvelope.seal(vaultKey, "pw".toCharArray(), "pw", iterations)
        val b = PasswordEnvelope.seal(vaultKey, "pw".toCharArray(), "pw", iterations)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun truncatedEnvelopeIsRejected() {
        assertThrows(WrongSecretException::class.java) { PasswordEnvelope.open(ByteArray(8), "pw".toCharArray(), "pw") }
    }
}
