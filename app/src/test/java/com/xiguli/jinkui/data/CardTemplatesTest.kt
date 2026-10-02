package com.xiguli.jinkui.data

import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.Expiry
import com.xiguli.jinkui.data.model.FieldKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTemplatesTest {
    private fun filled(type: CardType, values: Map<String, String>): Card =
        Card(
            id = "c",
            type = type,
            title = "测试",
            fields = CardTemplates.fieldsFor(type).map { it.copy(value = values[it.label].orEmpty()) },
        )

    @Test
    fun everyPresetTypeHasFieldsWithUniqueLabels() {
        for (type in CardType.entries) {
            val fields = CardTemplates.fieldsFor(type)
            if (type != CardType.CUSTOM) assertTrue(fields.size >= 5)
            assertEquals(fields.size, fields.map { it.label }.toSet().size)
        }
    }

    @Test
    fun ukeyTemplateHasExpiryAndSecurityCode() {
        val fields = CardTemplates.fieldsFor(CardType.UKEY).associateBy { it.label }
        assertEquals(FieldKind.DATE, fields.getValue("有效期").kind)
        assertFalse(fields.getValue("有效期").sensitive)
        assertEquals(FieldKind.NUMBER, fields.getValue("安全码").kind)
        assertTrue(fields.getValue("安全码").sensitive)
    }

    @Test
    fun ukeyIsAVirtualCreditCardNow() {
        assertEquals("U 卡", CardType.UKEY.label)
        assertEquals("ukey", CardType.UKEY.key)
        val fields = CardTemplates.fieldsFor(CardType.UKEY)
        assertEquals(listOf("卡号", "持卡人", "有效期", "安全码", "卡组织", "账单地址", "账单邮编", "发卡机构"), fields.map { it.label })
        assertTrue(fields.first { it.label == "卡号" }.sensitive)
        assertEquals(FieldKind.NUMBER, fields.first { it.label == "卡号" }.kind)
        assertFalse(fields.any { it.label.contains("PIN") || it.label.contains("序列号") })

        val card = filled(CardType.UKEY, mapOf("卡号" to "4111 1111 1111 4321", "持卡人" to "ZHANG SAN", "有效期" to "11/28", "安全码" to "987"))
        val face = CardTemplates.face(card)
        assertEquals("•••• 4321", face.primary)
        assertEquals("有效期", face.secondaryLabel)
        assertEquals("11/28", face.secondary)
        assertFalse((face.primary + face.secondary).contains("987"))
        assertFalse(face.primary.contains("4111"))
    }

    @Test
    fun legacyHardwareKeyCardsKeepTheirFieldsAndStillRender() {
        // 0.4.0 及更早按“硬件密钥”模板存下来的卡片
        val legacy = Card(
            id = "old",
            type = CardType.UKEY,
            title = "旧 U 盾",
            fields = listOf(
                CardField("1", "所属机构", "某银行", false),
                CardField("2", "卡号 / 序列号", "SN-0012345678", true),
                CardField("3", "有效期", "03/27", false, FieldKind.DATE),
                CardField("4", "安全码", "456", true, FieldKind.NUMBER),
                CardField("5", "PIN 码", "135246", true, FieldKind.PASSWORD),
                CardField("6", "恢复码", "abcd-efgh", true, FieldKind.PASSWORD),
            ),
        )
        val shown = CardTemplates.displayOrder(legacy)
        assertEquals(6, shown.size)
        assertEquals(legacy.fields.map { it.id }.toSet(), shown.map { it.id }.toSet())
        assertEquals(listOf("有效期", "安全码"), shown.take(2).map { it.label })
        val face = CardTemplates.face(legacy)
        assertEquals("•••• 5678", face.primary)
        assertEquals("03/27", face.secondary)
        assertFalse(face.primary.contains("135246"))
        // 保存的字段顺序与内容原样不动
        assertEquals("所属机构", legacy.fields.first().label)
    }

    @Test
    fun secretsInTemplatesAreMarkedSensitive() {
        for (type in CardType.entries) {
            for (field in CardTemplates.fieldsFor(type)) {
                if (field.kind == FieldKind.PASSWORD) assertTrue(field.sensitive)
                if (field.label in setOf("安全码", "取款密码", "公民身份号码", "护照号码")) assertTrue(field.sensitive)
            }
        }
        val bank = CardTemplates.fieldsFor(CardType.BANK)
        assertEquals("卡号", bank.first().label)
        assertEquals(FieldKind.PHONE, bank.first { it.label == "预留手机号" }.kind)
    }

    @Test
    fun bankFaceShowsLastFourAndExpiryButNoSecrets() {
        val card = filled(CardType.BANK, mapOf("卡号" to "6225 8800 1234 5678", "有效期" to "08/29", "安全码" to "321", "取款密码" to "135790"))
        val face = CardTemplates.face(card)
        assertEquals("•••• 5678", face.primary)
        assertEquals("有效期", face.secondaryLabel)
        assertEquals("08/29", face.secondary)
        val all = face.primary + face.secondary
        assertFalse(all.contains("321"))
        assertFalse(all.contains("135790"))
    }

    @Test
    fun faceNeverShowsPasswordKindFields() {
        val login = filled(CardType.LOGIN, mapOf("账号" to "me@example.com", "密码" to "Passw0rd-1234-5678"))
        assertEquals("me@example.com", CardTemplates.face(login).primary)
        val onlyPassword = filled(CardType.LOGIN, mapOf("密码" to "Passw0rd-1234-5678"))
        assertFalse(CardTemplates.face(onlyPassword).primary.contains("5678"))
        val wifi = filled(CardType.WIFI, mapOf("网络名称" to "Home-5G", "密码" to "wifi-secret-99", "加密方式" to "WPA3"))
        assertEquals("Home-5G", CardTemplates.face(wifi).primary)
        assertEquals("WPA3", CardTemplates.face(wifi).secondary)
    }

    @Test
    fun idCardFaceMasksNumberAndShowsName() {
        val card = filled(CardType.ID_CARD, mapOf("姓名" to "张三", "公民身份号码" to "11010519900101123X"))
        val face = CardTemplates.face(card)
        assertEquals("•••• 123X", face.primary)
        assertEquals("张三", face.secondary)
    }

    @Test
    fun emptyCardFallsBackToTypeLabel() {
        assertEquals("护照", CardTemplates.face(Card(id = "c", type = CardType.PASSPORT, title = "t")).primary)
    }

    @Test
    fun displayOrderFollowsTemplateAndKeepsCustomFieldsLast() {
        val card = Card(
            id = "c",
            type = CardType.BANK,
            title = "旧卡",
            fields = listOf(
                CardField("1", "备注电话", "400", false),
                CardField("2", "开户行", "深圳分行", false),
                CardField("3", "卡号", "6225880012345678", true, FieldKind.NUMBER),
                CardField("4", "自定义", "x", false),
            ),
        )
        assertEquals(listOf("卡号", "开户行", "备注电话", "自定义"), CardTemplates.displayOrder(card).map { it.label })
        // 保存的顺序不变
        assertEquals(listOf("备注电话", "开户行", "卡号", "自定义"), card.fields.map { it.label })
    }

    @Test
    fun missingFieldsOnlyAddsWhatIsAbsent() {
        val old = listOf(CardField("1", "所属机构", "某银行", false), CardField("2", "PIN 码", "1234", true, FieldKind.PASSWORD))
        val missing = CardTemplates.missingFields(CardType.UKEY, old).map { it.label }
        assertTrue(missing.containsAll(listOf("有效期", "安全码")))
        assertFalse(missing.contains("所属机构"))
        assertFalse(missing.contains("PIN 码"))
        assertTrue(CardTemplates.missingFields(CardType.UKEY, CardTemplates.fieldsFor(CardType.UKEY)).isEmpty())
    }

    @Test
    fun hintsExistForDateFieldsOnly() {
        assertTrue(CardTemplates.hintFor(CardType.BANK, "有效期").contains("08/29"))
        assertEquals("", CardTemplates.hintFor(CardType.BANK, "不存在的字段"))
    }

    @Test
    fun expiryParsesCommonFormats() {
        assertEquals(LocalDate.of(2029, 8, 31), Expiry.parse("08/29"))
        assertEquals(LocalDate.of(2029, 8, 31), Expiry.parse("8/2029"))
        assertEquals(LocalDate.of(2034, 5, 20), Expiry.parse("2034-05-20"))
        assertEquals(LocalDate.of(2034, 5, 20), Expiry.parse("2034年5月20日"))
        assertEquals(LocalDate.of(2034, 5, 20), Expiry.parse("20340520"))
        assertEquals(LocalDate.of(2031, 3, 31), Expiry.parse("2031.03"))
        assertEquals(LocalDate.of(2040, 1, 1), Expiry.parse("2020.01.01-2040.01.01"))
        assertEquals(LocalDate.of(2028, 3, 1), Expiry.parse("2022-03-01 至 2028-03-01"))
    }

    @Test
    fun expiryRejectsWhatItCannotRead() {
        assertNull(Expiry.parse("长期"))
        assertNull(Expiry.parse(""))
        assertNull(Expiry.parse("明年到期"))
        assertNull(Expiry.parse("13/29"))
        assertNull(Expiry.parse("2034-02-30"))
    }

    @Test
    fun daysLeftCountsFromToday() {
        val today = LocalDate.of(2026, 10, 3)
        val card = filled(CardType.BANK, mapOf("有效期" to "10/26"))
        assertEquals(28L, Expiry.daysLeft(card, today))
        assertTrue(Expiry.daysLeft(filled(CardType.BANK, mapOf("有效期" to "01/25")), today)!! < 0)
        assertNull(Expiry.daysLeft(filled(CardType.BANK, mapOf("卡号" to "6225880012345678")), today))
        assertNull(Expiry.daysLeft(filled(CardType.ID_CARD, mapOf("有效期限" to "长期")), today))
    }
}
