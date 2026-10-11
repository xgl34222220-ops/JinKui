package com.xiguli.jinkui.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.4.0-alpha04 卡包回收站：满 30 天才彻底删除（边界）、剩余天数向上取整、列表最近删的在前、不在回收站的不算。合成数据。 */
class TrashMathTest {
    private val day = TrashMath.DAY_MS
    private val now = 1_800_000_000_000L
    private fun card(id: String, deletedAt: Long?) = Card(id = id, type = CardType.LOGIN, title = id, deletedAt = deletedAt)

    @Test
    fun expiresExactlyAfterThirtyDays() {
        assertFalse(TrashMath.isExpired(null, now))
        assertFalse(TrashMath.isExpired(now - 30 * day + 1, now))
        assertTrue(TrashMath.isExpired(now - 30 * day, now))
        assertTrue(TrashMath.isExpired(now - 90 * day, now))
        val cards = listOf(card("live", null), card("fresh", now - day), card("old", now - 31 * day), card("edge", now - 30 * day))
        assertEquals(listOf("old", "edge"), TrashMath.expired(cards, now).map { it.id })
    }

    @Test
    fun daysLeftRoundsUpAndNeverNegative() {
        assertEquals(30, TrashMath.daysLeft(card("a", now), now))
        assertEquals(30, TrashMath.daysLeft(card("b", now - 1), now))
        assertEquals(29, TrashMath.daysLeft(card("c", now - day), now))
        assertEquals(1, TrashMath.daysLeft(card("d", now - 30 * day + 1), now))
        assertEquals(0, TrashMath.daysLeft(card("e", now - 40 * day), now))
        assertEquals(30, TrashMath.daysLeft(card("f", null), now))
    }

    @Test
    fun sortedShowsOnlyTrashedNewestFirst() {
        val cards = listOf(card("live", null), card("first", now - 5 * day), card("latest", now - day), card("mid", now - 3 * day))
        assertEquals(listOf("latest", "mid", "first"), TrashMath.sorted(cards).map { it.id })
    }
}
