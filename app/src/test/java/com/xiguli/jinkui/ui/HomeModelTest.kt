package com.xiguli.jinkui.ui

import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.SortOrder
import com.xiguli.jinkui.ui.home.buildHomeUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelTest {
    private fun card(
        id: String,
        type: CardType = CardType.LOGIN,
        pinned: Boolean = false,
        tags: List<String> = emptyList(),
        fields: List<CardField> = emptyList(),
        used: Long = 0,
    ) = Card(id = id, type = type, title = id, tags = tags, pinned = pinned, fields = fields, createdAt = 10, lastUsedAt = used)

    private val cards = listOf(
        card("b-bank", type = CardType.BANK, tags = listOf("工作")),
        card("a-mail", pinned = true, fields = listOf(CardField("f1", "账号", "me@example.com", sensitive = false))),
        card("c-wifi", type = CardType.WIFI, used = 50, fields = listOf(CardField("f2", "密码", "hunter22", sensitive = true, kind = FieldKind.PASSWORD))),
    )

    @Test
    fun defaultViewHasSectionsAndPinnedFirstDeck() {
        val ui = buildHomeUi(cards, "", null, SortOrder.TITLE, issues = 2)
        assertNull(ui.results)
        assertEquals(3, ui.total)
        assertEquals(2, ui.issues)
        assertEquals(listOf("a-mail"), ui.pinned.map { it.id })
        assertEquals(listOf("a-mail", "b-bank", "c-wifi"), ui.all.map { it.id })
        assertEquals("a-mail", ui.deck.first().id)
        assertEquals(listOf("c-wifi"), ui.recent.map { it.id })
    }

    @Test
    fun filtersListPinnedTypesThenTags() {
        val keys = buildHomeUi(cards, "", null, SortOrder.TITLE, 0).filters.map { it.first }
        assertEquals(listOf("p:", "t:login", "t:bank", "t:wifi", "g:工作"), keys)
    }

    @Test
    fun searchMatchesPlainFieldsButNeverSensitiveOnes() {
        assertEquals(listOf("a-mail"), buildHomeUi(cards, "example", null, SortOrder.TITLE, 0).results?.map { it.id })
        assertTrue(buildHomeUi(cards, "hunter22", null, SortOrder.TITLE, 0).results!!.isEmpty())
    }

    @Test
    fun filterByTagAndTypeAndPinned() {
        assertEquals(listOf("b-bank"), buildHomeUi(cards, "", "g:工作", SortOrder.TITLE, 0).results?.map { it.id })
        assertEquals(listOf("c-wifi"), buildHomeUi(cards, "", "t:wifi", SortOrder.TITLE, 0).results?.map { it.id })
        assertEquals(listOf("a-mail"), buildHomeUi(cards, "", "p:", SortOrder.TITLE, 0).results?.map { it.id })
    }

    @Test
    fun staleFilterIsIgnored() {
        // 选中的标签已被删掉：回到默认分区，而不是显示一个空结果。
        val ui = buildHomeUi(cards, "", "g:不存在", SortOrder.TITLE, 0)
        assertNull(ui.results)
        assertEquals(3, ui.all.size)
    }

    @Test
    fun emptyVault() {
        val ui = buildHomeUi(emptyList(), "", null, SortOrder.TITLE, 0)
        assertEquals(0, ui.total)
        assertTrue(ui.filters.isEmpty())
        assertTrue(ui.deck.isEmpty())
    }
}
