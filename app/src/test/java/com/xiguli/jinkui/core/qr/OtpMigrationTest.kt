package com.xiguli.jinkui.core.qr

import com.xiguli.jinkui.core.crypto.Base32
import com.xiguli.jinkui.core.crypto.Totp
import com.xiguli.jinkui.core.crypto.TotpAlgorithm
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.OtpImport
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google 身份验证器导出码（otpauth-migration）的离线解析。测试里自己按公开的字段编号拼 protobuf。 */
class OtpMigrationTest {
    private class Proto {
        val out = ByteArrayOutputStream()
        fun varint(v: Long): Proto {
            var x = v
            while (true) {
                if (x and 0x7FL.inv() == 0L) { out.write(x.toInt()); return this }
                out.write(((x and 0x7F) or 0x80).toInt())
                x = x ushr 7
            }
        }
        fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
        fun bytes(field: Int, b: ByteArray) = tag(field, 2).varint(b.size.toLong()).also { out.write(b) }
        fun string(field: Int, s: String) = bytes(field, s.toByteArray())
        fun int(field: Int, v: Long) = tag(field, 0).varint(v)
        fun build(): ByteArray = out.toByteArray()
    }

    private val secretA = "12345678901234567890".toByteArray()
    private val secretB = ByteArray(32) { (it * 7).toByte() }

    private fun entry(secret: ByteArray?, name: String, issuer: String, algorithm: Int = 1, digits: Int = 1, type: Int = 2): ByteArray {
        val p = Proto()
        if (secret != null) p.bytes(1, secret)
        p.string(2, name).string(3, issuer).int(4, algorithm.toLong()).int(5, digits.toLong()).int(6, type.toLong())
        p.int(7, 0) // counter（TOTP 不用）
        return p.build()
    }

    private fun uri(vararg entries: ByteArray, batchSize: Int = 1, batchIndex: Int = 0, extra: (Proto) -> Unit = {}): String {
        val p = Proto()
        for (e in entries) p.bytes(1, e)
        p.int(2, 1).int(3, batchSize.toLong()).int(4, batchIndex.toLong()).int(5, 123456)
        extra(p)
        val data = Base64.getEncoder().encodeToString(p.build())
        return "otpauth-migration://offline?data=" + URLEncoder.encode(data, "UTF-8")
    }

    @Test
    fun parsesBatchWithIssuerAccountAlgorithmAndDigits() {
        val text = uri(
            entry(secretA, "Google:me@example.com", "Google"),
            entry(secretB, "alice", "GitHub", algorithm = 2, digits = 2),
        )
        val batch = OtpMigration.parse(text)
        assertEquals(2, batch.entries.size)
        val g = batch.entries[0]
        assertEquals("Google", g.issuer)
        assertEquals("me@example.com", g.account) // 去掉重复的“发行方:”前缀
        assertTrue(g.config!!.secret.contentEquals(secretA))
        assertEquals(TotpAlgorithm.SHA1, g.config!!.algorithm)
        assertEquals(6, g.config!!.digits)
        // RFC 6238 测试向量（SHA1，59 秒）：解析出的密钥能算出正确的验证码
        assertEquals("287082", Totp.code(g.config!!, 59_000L).code)
        val h = batch.entries[1]
        assertEquals(TotpAlgorithm.SHA256, h.config!!.algorithm)
        assertEquals(8, h.config!!.digits)
        assertEquals("GitHub", h.title)
        // 规范化的 otpauth 链接能被 Totp 再次解析
        assertTrue(Totp.parse(h.config!!.toUri()).secret.contentEquals(secretB))
    }

    @Test
    fun unsupportedEntriesAreMarkedIndividually() {
        val batch = OtpMigration.parse(
            uri(
                entry(secretA, "hotp-user", "Bank", type = 1),
                entry(secretA, "md5-user", "Old", algorithm = 4),
                entry(null, "nokey", "Empty"),
                entry(secretB, "ok", "Fine"),
            ),
        )
        assertEquals(4, batch.entries.size)
        assertEquals(1, batch.usableCount)
        assertTrue(batch.entries[0].problem!!.contains("HOTP"))
        assertTrue(batch.entries[1].problem!!.contains("MD5"))
        assertEquals("缺少密钥", batch.entries[2].problem)
        assertNull(batch.entries[3].problem)
    }

    @Test
    fun multiPartExportsReportTheirPosition() {
        val batch = OtpMigration.parse(uri(entry(secretA, "a", "A"), batchSize = 3, batchIndex = 1))
        assertEquals(3, batch.batchSize)
        assertEquals(1, batch.batchIndex)
    }

    @Test
    fun unknownFieldsAreSkipped() {
        val text = uri(entry(secretA, "a", "A")) { p ->
            p.tag(15, 5).out.write(byteArrayOf(1, 2, 3, 4)) // fixed32
            p.tag(16, 1).out.write(ByteArray(8)) // fixed64
            p.string(17, "future")
        }
        assertEquals(1, OtpMigration.parse(text).usableCount)
    }

    @Test
    fun corruptPayloadsAreRejectedWithoutLeakingContent() {
        val good = Proto().bytes(1, entry(secretA, "a", "A")).build()
        // 长度字段声称比实际长
        val truncated = good.copyOf(good.size - 3)
        val texts = listOf(
            "otpauth-migration://offline?data=" + Base64.getEncoder().encodeToString(truncated),
            "otpauth-migration://offline?data=%%%",
            "otpauth-migration://offline",
            "otpauth-migration://offline?data=",
            "otpauth-migration://offline?data=" + Base64.getEncoder().encodeToString(byteArrayOf(0x0F)), // wire type 7
        )
        for (t in texts) {
            val e = assertThrows(MigrationFormatException::class.java) { OtpMigration.parse(t) }
            assertFalse(e.message!!.contains("data="))
        }
    }

    @Test
    fun interpretRoutesMigrationToBatch() {
        val result = OtpQr.interpret(uri(entry(secretA, "a", "A")))
        assertTrue(result is OtpQrResult.Batch)
        val bad = OtpQr.interpret("otpauth-migration://offline?data=CjEKCkhlbGxvId6tvu8SGEV4YW1wbGU")
        assertTrue(bad is OtpQrResult.Rejected)
        assertFalse((bad as OtpQrResult.Rejected).message.contains("CjEK"))
    }

    @Test
    fun importBuildsLoginCardsAndFlagsDuplicates() {
        val batch = OtpMigration.parse(uri(entry(secretA, "me@example.com", "Google"), entry(secretB, "bob", "")))
        val existingCard = Card(
            id = "c-1", type = CardType.LOGIN, title = "老的 Google",
            fields = listOf(CardField("f-1", "两步验证码", "otpauth://totp/x?secret=" + Base32.encode(secretA), true, FieldKind.TOTP)),
        )
        val existing = OtpImport.existingSecrets(listOf(existingCard))
        assertTrue(OtpImport.isDuplicate(batch.entries[0], existing))
        assertFalse(OtpImport.isDuplicate(batch.entries[1], existing))

        var n = 0
        val card = OtpImport.cardFor(batch.entries[0]) { "id-${n++}" }!!
        assertEquals(CardType.LOGIN, card.type)
        assertEquals("Google", card.title)
        assertEquals(listOf("账号", "两步验证码"), card.fields.map { it.label })
        val totp = card.fields.last()
        assertEquals(FieldKind.TOTP, totp.kind)
        assertTrue(totp.sensitive)
        assertTrue(Totp.parse(totp.value).secret.contentEquals(secretA))
        // 没有发行方时用账号当名称
        assertEquals("bob", OtpImport.cardFor(batch.entries[1])!!.title)
    }

    @Test
    fun singleOtpauthLinkBecomesOneEntryBatch() {
        val batch = OtpMigration.single("otpauth://totp/Example:alice?secret=" + Base32.encode(secretA) + "&issuer=Example")
        assertEquals(1, batch.entries.size)
        assertEquals("Example", batch.entries[0].issuer)
        assertEquals("alice", batch.entries[0].account)
    }
}
