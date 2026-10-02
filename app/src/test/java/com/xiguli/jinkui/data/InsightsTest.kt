package com.xiguli.jinkui.data

import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.HealthKind
import com.xiguli.jinkui.data.model.PasswordHealth
import com.xiguli.jinkui.data.model.SortOrder
import com.xiguli.jinkui.data.model.deckOrder
import com.xiguli.jinkui.data.model.sortCards
import com.xiguli.jinkui.data.model.tagCounts
import com.xiguli.jinkui.data.model.withTagRenamed
import com.xiguli.jinkui.data.model.withoutTag
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private val today = LocalDate.of(2026, 10, 3)

    private fun login(id: String, password: String, title: String = id) = Card(
        id = id,
        type = CardType.LOGIN,
        title = title,
        fields = listOf(
            CardField("$id-a", "账号", "me@example.com", false),
            CardField("$id-p", "密码", password, true, FieldKind.PASSWORD),
        ),
    )

    @Test
    fun sortOrdersBehaveAsNamed() {
        val a = Card(id = "a", type = CardType.WIFI, title = "b", updatedAt = 30, lastUsedAt = 1)
        val b = Card(id = "b", type = CardType.LOGIN, title = "A", updatedAt = 10, lastUsedAt = 3)
        val c = Card(id = "c", type = CardType.BANK, title = "c", updatedAt = 20, lastUsedAt = 2)
        val all = listOf(a, b, c)
        assertEquals(listOf("b", "a", "c"), sortCards(all, SortOrder.TITLE).map { it.id })
        assertEquals(listOf("a", "c", "b"), sortCards(all, SortOrder.UPDATED).map { it.id })
        assertEquals(listOf("b", "c", "a"), sortCards(all, SortOrder.USED).map { it.id })
        assertEquals(listOf("b", "c", "a"), sortCards(all, SortOrder.TYPE).map { it.id })
        assertEquals(SortOrder.TITLE, SortOrder.of("unknown"))
        for (order in SortOrder.entries) assertEquals(order, SortOrder.of(order.key))
    }

    @Test
    fun deckKeepsPinnedFirstUnderAnySort() {
        val a = Card(id = "a", type = CardType.WIFI, title = "a", updatedAt = 1)
        val b = Card(id = "b", type = CardType.WIFI, title = "b", updatedAt = 9, pinned = true)
        val c = Card(id = "c", type = CardType.WIFI, title = "c", updatedAt = 5)
        assertEquals(listOf("b", "c", "a"), deckOrder(listOf(a, b, c), SortOrder.UPDATED).map { it.id })
        assertEquals(listOf("b", "a", "c"), deckOrder(listOf(a, b, c)).map { it.id })
    }

    @Test
    fun weakPasswordsAreFlagged() {
        assertTrue(PasswordHealth.isWeak("123456"))
        assertTrue(PasswordHealth.isWeak("abcdefgh"))
        assertTrue(PasswordHealth.isWeak("aaaaaaaaaaaa"))
        assertFalse(PasswordHealth.isWeak("kT7#pQ2m!vX9"))
        val report = PasswordHealth.analyze(listOf(login("x", "123456"), login("y", "kT7#pQ2m!vX9")), today)
        assertEquals(listOf("x"), report.of(HealthKind.WEAK).map { it.card.id })
        assertEquals(2, report.checkedPasswords)
    }

    @Test
    fun reuseIsDetectedAcrossCardsOnly() {
        val shared = "Shared-Pass-2026!"
        val report = PasswordHealth.analyze(
            listOf(login("a", shared), login("b", shared), login("c", shared), login("d", "Another-One-77#")),
            today,
        )
        val reused = report.of(HealthKind.REUSED)
        assertEquals(setOf("a", "b", "c"), reused.map { it.card.id }.toSet())
        assertTrue(reused.all { it.detail.contains("另外 2 张") })

        // 同一张卡片里两个字段相同，不算“重复使用”
        val twice = Card(
            id = "t",
            type = CardType.WIFI,
            title = "路由器",
            fields = listOf(
                CardField("1", "密码", "Same-Wifi-Pass-9!", true, FieldKind.PASSWORD),
                CardField("2", "管理密码", "Same-Wifi-Pass-9!", true, FieldKind.PASSWORD),
            ),
        )
        assertTrue(PasswordHealth.analyze(listOf(twice), today).of(HealthKind.REUSED).isEmpty())
    }

    @Test
    fun reportNeverContainsPasswordText() {
        val report = PasswordHealth.analyze(listOf(login("a", "hunter2"), login("b", "hunter2")), today)
        assertFalse(report.isClean)
        assertTrue(report.issues.none { it.detail.contains("hunter2") })
    }

    @Test
    fun numericPinsAndBlankFieldsAreNotJudged() {
        val bank = Card(
            id = "b",
            type = CardType.BANK,
            title = "储蓄卡",
            fields = listOf(
                CardField("1", "取款密码", "135790", true, FieldKind.NUMBER),
                CardField("2", "密码", "", true, FieldKind.PASSWORD),
            ),
        )
        val report = PasswordHealth.analyze(listOf(bank), today)
        assertTrue(report.isClean)
        assertEquals(0, report.checkedPasswords)
    }

    @Test
    fun expiryIssuesAreSplitIntoExpiredAndExpiring() {
        fun card(id: String, expiry: String) = Card(
            id = id,
            type = CardType.BANK,
            title = id,
            fields = listOf(CardField("$id-e", "有效期", expiry, false, FieldKind.DATE)),
        )
        val report = PasswordHealth.analyze(listOf(card("old", "01/25"), card("soon", "11/26"), card("far", "12/30")), today)
        assertEquals(listOf("old"), report.of(HealthKind.EXPIRED).map { it.card.id })
        assertEquals(listOf("soon"), report.of(HealthKind.EXPIRING).map { it.card.id })
        assertTrue(PasswordHealth.analyze(listOf(card("far", "12/30")), today).isClean)
    }

    @Test
    fun tagsCanBeCountedRenamedMergedAndRemoved() {
        val a = Card(id = "a", type = CardType.LOGIN, title = "a", tags = listOf("工作", "常用"))
        val b = Card(id = "b", type = CardType.LOGIN, title = "b", tags = listOf("工作"))
        assertEquals(listOf("工作" to 2, "常用" to 1), tagCounts(listOf(a, b)))
        assertEquals(listOf("公司", "常用"), a.withTagRenamed("工作", "公司").tags)
        // 改成已有的名字时合并，不产生重复
        assertEquals(listOf("常用"), a.withTagRenamed("工作", "常用").tags)
        assertEquals(a, a.withTagRenamed("不存在", "x"))
        assertEquals(a, a.withTagRenamed("工作", "  "))
        assertEquals(listOf("常用"), a.withoutTag("工作").tags)
        assertEquals(b, b.withoutTag("常用"))
    }

    @Test
    fun searchCoversPlainFieldValuesButNeverSensitiveOnes() {
        val bank = Card(
            id = "b",
            type = CardType.BANK,
            title = "工资卡",
            fields = listOf(
                CardField("1", "卡号", "6225880012345678", true, FieldKind.NUMBER),
                CardField("2", "开户行", "深圳南山支行", false),
            ),
        )
        assertTrue(bank.matches("南山"))
        assertTrue(bank.matches("工资 深圳"))
        assertFalse(bank.matches("6225"))
        assertFalse(bank.matches("5678"))
    }
}
