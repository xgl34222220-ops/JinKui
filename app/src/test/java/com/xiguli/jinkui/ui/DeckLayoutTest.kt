package com.xiguli.jinkui.ui

import com.xiguli.jinkui.ui.home.DeckLayout
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckLayoutTest {
    private val width = 840f
    private val unit = 3f

    /** 第 d 层的右边缘相对当前卡右边缘的位置（px）。 */
    private fun rightEdge(d: Float): Float =
        width / 2f + DeckLayout.scale(d) * width / 2f + DeckLayout.translationX(d, width, unit) - width

    @Test
    fun frontCardIsShownAsIs() {
        assertEquals(1f, DeckLayout.scale(0f), 0f)
        assertEquals(0f, DeckLayout.translationX(0f, width, unit), 0f)
        assertEquals(1f, DeckLayout.alpha(0f), 0f)
        assertEquals(0f, DeckLayout.haze(0f), 0f)
    }

    @Test
    fun stackFansRightInDecreasingStepsWithinBudget() {
        val edges = (1..3).map { rightEdge(it.toFloat()) / unit }
        assertEquals(20f, edges[0], 0.01f)
        assertEquals(36f, edges[1], 0.01f)
        assertEquals(48f, edges[2], 0.01f)
        assertTrue(edges[1] - edges[0] > edges[2] - edges[1])
        assertTrue(edges.all { it <= DeckLayout.FAN_DP + 0.01f })
    }

    @Test
    fun cardsBehindShrinkAndFadeIntoThePage() {
        var previousScale = 1f
        var previousHaze = 0f
        for (depth in 1..3) {
            val d = depth.toFloat()
            assertTrue(DeckLayout.scale(d) < previousScale)
            assertTrue(DeckLayout.haze(d) > previousHaze)
            assertTrue(DeckLayout.haze(d) <= 0.8f)
            assertEquals(1f, DeckLayout.alpha(d), 0f)
            previousScale = DeckLayout.scale(d)
            previousHaze = DeckLayout.haze(d)
        }
        assertEquals(0.5f, DeckLayout.alpha(3.5f), 0.001f)
        assertEquals(0f, DeckLayout.alpha(4f), 0f)
        assertEquals(0f, DeckLayout.alpha(9f), 0f)
    }

    @Test
    fun passedCardSlidesLeftAndLeavesOnlyOneBehind() {
        assertEquals(-(width + DeckLayout.GAP_DP * unit), DeckLayout.translationX(-1f, width, unit), 0.01f)
        assertEquals(1f, DeckLayout.scale(-1f), 0f)
        assertEquals(0f, DeckLayout.haze(-1f), 0f)
        assertEquals(1f, DeckLayout.alpha(-1f), 0f)
        assertEquals(0f, DeckLayout.alpha(-2f), 0f)
    }

    @Test
    fun poseIsContinuousAroundTheFrontCard() {
        val before = DeckLayout.translationX(-0.001f, width, unit)
        val after = DeckLayout.translationX(0.001f, width, unit)
        assertTrue(abs(before - after) < 2f)
        assertTrue(abs(DeckLayout.scale(-0.001f) - DeckLayout.scale(0.001f)) < 0.001f)
    }

    @Test
    fun rubberBandOnlyResistsBeyondTheEnds() {
        assertEquals(0f, DeckLayout.rubberBand(0f, 5), 0f)
        assertEquals(2.4f, DeckLayout.rubberBand(2.4f, 5), 0f)
        assertEquals(4f, DeckLayout.rubberBand(4f, 5), 0f)
        assertEquals(-0.25f, DeckLayout.rubberBand(-1f, 5), 0.0001f)
        assertEquals(4.25f, DeckLayout.rubberBand(5f, 5), 0.0001f)
        assertTrue(DeckLayout.rubberBand(6f, 5) > DeckLayout.rubberBand(5f, 5))
        assertEquals(0.25f, DeckLayout.rubberBand(1f, 1), 0.0001f)
    }

    @Test
    fun settlingSnapsToNearestUnlessFlung() {
        assertEquals(2, DeckLayout.settleTarget(2.4f, 0f, 5))
        assertEquals(3, DeckLayout.settleTarget(2.6f, 0f, 5))
        // 轻轻一甩也翻页
        assertEquals(3, DeckLayout.settleTarget(2.1f, 3f, 5))
        assertEquals(3, DeckLayout.settleTarget(2.0f, 3f, 5))
        assertEquals(2, DeckLayout.settleTarget(2.9f, -3f, 5))
        assertEquals(1, DeckLayout.settleTarget(2.0f, -3f, 5))
        // 速度不够时不算甩
        assertEquals(2, DeckLayout.settleTarget(2.2f, 1f, 5))
    }

    @Test
    fun settlingNeverLeavesTheDeck() {
        assertEquals(0, DeckLayout.settleTarget(-0.2f, -5f, 5))
        assertEquals(4, DeckLayout.settleTarget(4.2f, 5f, 5))
        assertEquals(4, DeckLayout.settleTarget(4f, 5f, 5))
        assertEquals(0, DeckLayout.settleTarget(0.7f, 5f, 1))
        assertEquals(0, DeckLayout.settleTarget(0f, 0f, 0))
    }
}
