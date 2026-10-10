package com.xiguli.jinkui.core.crypto

import com.xiguli.jinkui.data.backup.BackupCard
import com.xiguli.jinkui.data.backup.BackupJson
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.copyText
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpTest {
    // RFC 6238 附录 B 的测试密钥（ASCII）
    private val sha1Key = "12345678901234567890".toByteArray()
    private val sha256Key = "12345678901234567890123456789012".toByteArray()
    private val sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    @Test
    fun rfc4226HotpVectors() {
        val expected = listOf("755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583", "399871", "520489")
        expected.forEachIndexed { counter, code -> assertEquals(code, Totp.hotp(sha1Key, counter.toLong())) }
    }

    @Test
    fun rfc6238TotpVectors() {
        // 时间（秒）, SHA1, SHA256, SHA512 —— 8 位、30 秒
        val vectors = listOf(
            Triple(59L, "94287082", "46119246") to "90693936",
            Triple(1111111109L, "07081804", "68084774") to "25091201",
            Triple(1111111111L, "14050471", "67062674") to "99943326",
            Triple(1234567890L, "89005924", "91819424") to "93441116",
            Triple(2000000000L, "69279037", "90698825") to "38618901",
            Triple(20000000000L, "65353130", "77737706") to "47863826",
        )
        for ((triple, sha512) in vectors) {
            val (time, sha1, sha256) = triple
            assertEquals(sha1, Totp.code(TotpConfig(sha1Key, TotpAlgorithm.SHA1, 8, 30), time * 1000).code)
            assertEquals(sha256, Totp.code(TotpConfig(sha256Key, TotpAlgorithm.SHA256, 8, 30), time * 1000).code)
            assertEquals(sha512, Totp.code(TotpConfig(sha512Key, TotpAlgorithm.SHA512, 8, 30), time * 1000).code)
        }
    }

    @Test
    fun sixDigitsAndCountdown() {
        val config = TotpConfig(sha1Key)
        // 6 位是 8 位结果的末 6 位
        assertEquals("287082", Totp.code(config, 59_000).code)
        assertEquals(1, Totp.code(config, 59_000).secondsLeft)
        assertEquals(30, Totp.code(config, 60_000).secondsLeft)
        // 60 秒周期：59 秒和 0 秒在同一个计数
        val sixty = TotpConfig(sha1Key, period = 60)
        assertEquals(Totp.code(sixty, 0).code, Totp.code(sixty, 59_999).code)
        assertEquals(Totp.hotp(sha1Key, 1), Totp.code(sixty, 60_000).code)
        assertEquals(1, Totp.code(sixty, 59_999).secondsLeft)
    }

    @Test
    fun base32RoundTripAndLenientDecode() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Base32.encode(sha1Key))
        assertArrayEquals(sha1Key, Base32.decode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
        assertNull(Base32.decode("NOT-BASE32-1!"))
        assertNull(Base32.decode(""))
    }

    @Test
    fun parsesOtpauthUri() {
        val config = Totp.parse("otpauth://totp/Example%20Co:alice@example.com?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP&issuer=Example%20Co&algorithm=SHA256&digits=8&period=60")
        assertEquals(TotpAlgorithm.SHA256, config.algorithm)
        assertEquals(8, config.digits)
        assertEquals(60, config.period)
        assertEquals("Example Co", config.issuer)
        assertEquals("alice@example.com", config.account)
        // 规范化后再解析，结果一致
        val again = Totp.parse(config.toUri())
        assertArrayEquals(config.secret, again.secret)
        assertEquals(config.issuer, again.issuer)
        assertEquals(config.account, again.account)
        assertEquals(config.toUri(), again.toUri())
        // 缺省参数：SHA1、6 位、30 秒；issuer 取自标签
        val minimal = Totp.parse("otpauth://totp/GitHub:me?secret=JBSWY3DPEHPK3PXP")
        assertEquals(TotpAlgorithm.SHA1, minimal.algorithm)
        assertEquals(6, minimal.digits)
        assertEquals(30, minimal.period)
        assertEquals("GitHub", minimal.issuer)
        assertEquals("me", minimal.account)
    }

    @Test
    fun parsesBareSecret() {
        val config = Totp.parse("  jbsw y3dp ehpk 3pxp  ")
        assertEquals(TotpAlgorithm.SHA1, config.algorithm)
        assertEquals(6, config.digits)
        assertTrue(config.toUri().startsWith("otpauth://totp/"))
        assertTrue(config.toUri().contains("secret=JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun rejectsBadInput() {
        for (bad in listOf(
            "",
            "short",
            "JBSWY3DP", // 5 字节，太短
            "otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1",
            "otpauth://totp/x?issuer=nosecret",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=7",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&period=45",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&algorithm=MD5",
            "otpauth://totp/x?secret=JBSWY3DP!!",
        )) {
            assertThrows(bad, IllegalArgumentException::class.java) { Totp.parse(bad) }
        }
        assertNull(Totp.errorOf(""))
        assertNotNull(Totp.errorOf("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&digits=7"))
    }

    @Test
    fun totpFieldIsNeverSearchableOrQuickCopiedAndCopiesTheCode() {
        val uri = TotpConfig(sha1Key, issuer = "GitHub", account = "me").toUri()
        val totp = CardField("f-totp", "两步验证码", uri, sensitive = true, kind = FieldKind.TOTP)
        val card = Card(
            id = "c-1", type = CardType.LOGIN, title = "GitHub",
            fields = listOf(totp, CardField("f-acc", "账号", "me", false), CardField("f-pw", "密码", "hunter2", true, FieldKind.PASSWORD)),
        )
        val secret = Base32.encode(sha1Key)
        assertFalse(card.matches(secret.lowercase()))
        assertFalse(card.matches("otpauth"))
        // 一键复制取密码，不取两步验证密钥
        assertEquals(listOf("f-acc", "f-pw"), card.quickCopyFields.map { it.id })
        // 复制的是此刻的验证码，不是密钥
        assertEquals("287082", totp.copyText(59_000))
        assertNull(totp.copy(value = "garbage").copyText(59_000))
        assertEquals("hunter2", card.fields[2].copyText())
        // 即便被误标为“不敏感”，也不参与搜索
        assertFalse(card.copy(fields = listOf(totp.copy(sensitive = false))).matches(secret))
    }

    @Test
    fun totpFieldSurvivesBackup() {
        val uri = TotpConfig(sha256Key, TotpAlgorithm.SHA256, 8, 60, "Bank", "me").toUri()
        val card = Card(id = "c-1", type = CardType.LOGIN, title = "银行", fields = listOf(CardField("f-1", "两步验证码", uri, true, FieldKind.TOTP)))
        val restored = BackupJson.decodeAll(BackupJson.encode(listOf(BackupCard(card, emptyMap(), emptyList())), 1L)).cards.single().card
        assertEquals(FieldKind.TOTP, restored.fields.single().kind)
        assertEquals(uri, restored.fields.single().value)
        assertTrue(restored.fields.single().sensitive)
        // 整份备份文件里看不到密钥
        val file = BackupCodec.encode(BackupJson.encode(listOf(BackupCard(card, emptyMap(), emptyList())), 1L), "pw".toCharArray(), Kdf.MIN_ITERATIONS)
        assertFalse(String(file, Charsets.ISO_8859_1).contains(Base32.encode(sha256Key)))
    }
}
