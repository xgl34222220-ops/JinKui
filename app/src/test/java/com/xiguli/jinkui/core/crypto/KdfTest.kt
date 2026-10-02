package com.xiguli.jinkui.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KdfTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    @Test
    fun pbkdf2MatchesPublishedVectors() {
        val salt = "salt".toByteArray()
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            Kdf.pbkdf2("password".toCharArray(), salt, 1).toHex(),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            Kdf.pbkdf2("password".toCharArray(), salt, 4096).toHex(),
        )
    }

    @Test
    fun pbkdf2DependsOnSaltAndPassword() {
        val a = Kdf.pbkdf2("金匮".toCharArray(), ByteArray(16) { 1 }, 1000)
        val b = Kdf.pbkdf2("金匮".toCharArray(), ByteArray(16) { 2 }, 1000)
        val c = Kdf.pbkdf2("金柜".toCharArray(), ByteArray(16) { 1 }, 1000)
        assertFalse(a.contentEquals(b))
        assertFalse(a.contentEquals(c))
        assertEquals(32, a.size)
    }

    @Test
    fun hkdfMatchesRfc5869Case1() {
        val okm = Kdf.hkdf(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            lengthBytes = 42,
            salt = hex("000102030405060708090a0b0c"),
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHex(),
        )
    }

    @Test
    fun hkdfMatchesRfc5869Case3WithoutSaltOrInfo() {
        val okm = Kdf.hkdf(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            info = ByteArray(0),
            lengthBytes = 42,
        )
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.toHex(),
        )
    }

    @Test
    fun hkdfLabelsGiveIndependentKeys() {
        val vaultKey = ByteArray(32) { 9 }
        val db = Kdf.hkdf(vaultKey, "jinkui/db")
        val field = Kdf.hkdf(vaultKey, "jinkui/field")
        assertEquals(32, db.size)
        assertFalse(db.contentEquals(field))
        assertArrayEquals(db, Kdf.hkdf(vaultKey, "jinkui/db"))
    }

    @Test
    fun normalizeFoldsCompatibilityForms() {
        // 全角 "Ａ１" 与半角 "A1" 应得到同一口令。
        assertArrayEquals("A1".toCharArray(), Kdf.normalize("\uFF21\uFF11"))
    }
}
