package com.xiguli.jinkui.data

import com.xiguli.jinkui.core.crypto.BackupCodec
import com.xiguli.jinkui.core.crypto.BackupFormatException
import com.xiguli.jinkui.core.crypto.Kdf
import com.xiguli.jinkui.data.backup.BackupCard
import com.xiguli.jinkui.data.backup.BackupJson
import com.xiguli.jinkui.data.model.Attachment
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.HistoryEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    private val photo = ByteArray(4096) { (it % 200).toByte() }
    private val sample = BackupCard(
        card = Card(
            id = "c-1",
            type = CardType.BANK,
            title = "招商银行 · 储蓄卡",
            tags = listOf("银行", "常用"),
            color = 1,
            fields = listOf(
                CardField("f-1", "卡号", "6225 8800 0000 0000", sensitive = true, kind = FieldKind.NUMBER),
                CardField("f-2", "开户行", "深圳分行", sensitive = false),
            ),
            note = "换行\n与 \"引号\" 都要原样保留",
            attachments = listOf(Attachment("a-1", "正面.jpg", "image/jpeg", photo.size.toLong(), 10L)),
            pinned = true,
            createdAt = 1L,
            updatedAt = 2L,
            lastUsedAt = 3L,
        ),
        attachmentData = mapOf("a-1" to photo),
        history = listOf(HistoryEntry("h-1", "取款密码", "111111", 5L)),
    )

    @Test
    fun cardsSurviveARoundTrip() {
        val restored = BackupJson.decode(BackupJson.encode(listOf(sample), exportedAt = 99L)).single()
        assertEquals(sample.card, restored.card)
        assertArrayEquals(photo, restored.attachmentData.getValue("a-1"))
        assertEquals(sample.history, restored.history)
    }

    @Test
    fun fullPipelineThroughEncryptedFile() {
        val file = BackupCodec.encode(BackupJson.encode(listOf(sample), 99L), "主密码".toCharArray(), Kdf.MIN_ITERATIONS)
        assertFalse(String(file, Charsets.UTF_8).contains("6225"))
        val restored = BackupJson.decode(BackupCodec.decode(file, "主密码".toCharArray()))
        assertEquals("招商银行 · 储蓄卡", restored.single().card.title)
    }

    @Test
    fun foreignJsonIsRefused() {
        assertThrows(BackupFormatException::class.java) { BackupJson.decode("""{"app":"other","cards":[]}""".toByteArray()) }
        assertThrows(BackupFormatException::class.java) { BackupJson.decode("not json".toByteArray()) }
        assertThrows(BackupFormatException::class.java) { BackupJson.decode("""{"app":"jinkui","schema":1}""".toByteArray()) }
    }

    @Test
    fun newerSchemaIsRefused() {
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decode("""{"app":"jinkui","schema":99,"cards":[]}""".toByteArray())
        }
    }

    @Test
    fun pathTraversalIdsAreRefused() {
        val evil = """{"app":"jinkui","schema":1,"cards":[{"id":"../../x","title":"t","fields":[]}]}"""
        assertThrows(BackupFormatException::class.java) { BackupJson.decode(evil.toByteArray()) }
    }

    @Test
    fun unknownTypeFallsBackToCustom() {
        val json = """{"app":"jinkui","schema":1,"cards":[{"id":"c1","type":"hologram","title":"t","fields":[]}]}"""
        assertEquals(CardType.CUSTOM, BackupJson.decode(json.toByteArray()).single().card.type)
    }

    @Test
    fun everyTypeHasATemplateAndStableKey() {
        for (type in CardType.entries) {
            assertEquals(type, CardType.of(type.key))
            val fields = CardTemplates.fieldsFor(type)
            assertEquals(fields.size, fields.map { it.id }.toSet().size)
        }
        assertTrue(CardTemplates.fieldsFor(CardType.LOGIN).any { it.sensitive && it.kind == FieldKind.PASSWORD })
    }

    @Test
    fun faceHintShowsLastFourOfNumbersButNeverPasswords() {
        assertEquals("•••• 0000", sample.card.faceHint)
        val login = Card(
            id = "c-2",
            type = CardType.LOGIN,
            title = "邮箱",
            fields = listOf(
                CardField("f-1", "账号", "me@example.com", sensitive = false),
                CardField("f-2", "密码", "S3cret-Passw0rd-1234", sensitive = true, kind = FieldKind.PASSWORD),
            ),
        )
        assertEquals("me@example.com", login.faceHint)
        assertFalse(login.faceHint.contains("1234"))
        val empty = Card(id = "c-3", type = CardType.WIFI, title = "家")
        assertEquals("Wi-Fi", empty.faceHint)
    }

    @Test
    fun longNumbersAreGroupedForDisplayOnly() {
        assertEquals("6225 8800 0000 0000", com.xiguli.jinkui.data.model.groupDigits("6225880000000000"))
        assertEquals("6225 8800 0000 0000", com.xiguli.jinkui.data.model.groupDigits("6225 8800 0000 0000"))
        assertEquals("123456", com.xiguli.jinkui.data.model.groupDigits("123456"))
        assertEquals("abc1234567890123", com.xiguli.jinkui.data.model.groupDigits("abc1234567890123"))
    }

    @Test
    fun searchMatchesTitleTagsAndNoteButNotSecrets() {
        val card = sample.card
        assertTrue(card.matches("招商"))
        assertTrue(card.matches("常用 银行"))
        assertTrue(card.matches("引号"))
        assertTrue(card.matches("  "))
        assertFalse(card.matches("6225"))
        assertFalse(card.matches("招商 不存在"))
    }
}
