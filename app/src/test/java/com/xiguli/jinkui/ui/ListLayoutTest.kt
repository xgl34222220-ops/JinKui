package com.xiguli.jinkui.ui

import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.SortOrder
import com.xiguli.jinkui.ui.home.ListLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListLayoutTest {
    private fun card(id: String, used: Long = 0, pinned: Boolean = false) =
        Card(id = id, type = CardType.LOGIN, title = id, pinned = pinned, createdAt = 10, lastUsedAt = used)

    @Test
    fun recentIsHiddenForSmallVaults() {
        val few = (1..5).map { card("c$it", used = 100L + it) }
        assertTrue(ListLayout.recent(few).isEmpty())
        assertTrue(ListLayout.recent(emptyList()).isEmpty())
    }

    @Test
    fun recentShowsLatestUsedAndSkipsPinnedAndNeverUsed() {
        val cards = listOf(
            card("a", used = 50),
            card("b", used = 90),
            card("c", used = 10),           // 创建后没用过
            card("d", used = 99, pinned = true),
            card("e", used = 70),
            card("f", used = 60),
            card("g", used = 80),
        )
        assertEquals(listOf("b", "g", "e", "f"), ListLayout.recent(cards).map { it.id })
    }

    @Test
    fun sortCyclesThroughEveryOrderAndBack() {
        var order = SortOrder.TITLE
        val seen = ArrayList<SortOrder>()
        repeat(SortOrder.entries.size) {
            seen += order
            order = ListLayout.nextSort(order)
        }
        assertEquals(SortOrder.entries.toList(), seen)
        assertEquals(SortOrder.TITLE, order)
    }

    @Test
    fun copyTargetPrefersTheSecretField() {
        val login = Card(
            id = "l",
            type = CardType.LOGIN,
            title = "邮箱",
            fields = listOf(
                CardField("1", "账号", "me@example.com", false),
                CardField("2", "密码", "S3cret!", true, FieldKind.PASSWORD),
            ),
        )
        assertEquals("密码", ListLayout.copyTarget(login)?.label)
        val plainOnly = login.copy(fields = login.fields.take(1))
        assertEquals("账号", ListLayout.copyTarget(plainOnly)?.label)
        assertNull(ListLayout.copyTarget(Card(id = "e", type = CardType.CUSTOM, title = "空")))
    }

    @Test
    fun maskedTextIsRecognised() {
        assertTrue(ListLayout.isMasked("•••• 5972"))
        assertFalse(ListLayout.isMasked("me@example.com"))
        assertFalse(ListLayout.isMasked(""))
    }
}
