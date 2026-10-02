package com.xiguli.jinkui.ui.home

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 牌堆的几何与手势计算。纯函数，不依赖界面框架，便于单测。
 *
 * 用一个连续的 position 表示“现在看到第几张”（可以是 2.37 这样的中间值）。
 * 每张牌与它的距离 d = 序号 - position 决定了这张牌的姿态：
 *
 *  - d = 0      当前卡，原样展示；
 *  - d > 0      还没看到的牌，叠在后面向右露边，越远越小、越“雾”；
 *  - d < 0      已经翻过的牌，向左滑出，在屏幕左缘留一条窄边。
 */
object DeckLayout {
    /** 当前卡后面能看到几张。 */
    const val VISIBLE_BEHIND = 3

    /** 牌堆向右展开占用的总宽度（dp）。 */
    const val FAN_DP = 48f

    /** 翻过的牌与当前卡之间的间隙（dp）。 */
    const val GAP_DP = 8f

    private const val SCALE_STEP = 0.08f
    private const val MAX_DEPTH = 4f

    /** 缩放：每深一层缩小 8%。翻过的牌不缩放。 */
    fun scale(d: Float): Float = if (d <= 0f) 1f else 1f - SCALE_STEP * min(d, MAX_DEPTH)

    /** 第 d 层的右边缘比当前卡的右边缘多露出多少（dp）：20、36、48，越往后露得越少。 */
    fun protrusionDp(d: Float): Float {
        val depth = d.coerceIn(0f, MAX_DEPTH)
        return 22f * depth - 2f * depth * depth
    }

    /** 水平位移（px）。缩放以卡片中心为原点，所以要把缩小带来的内收补回来。 */
    fun translationX(d: Float, widthPx: Float, unitPx: Float): Float =
        if (d >= 0f) {
            protrusionDp(d) * unitPx + widthPx * (1f - scale(d)) / 2f
        } else {
            d * (widthPx + GAP_DP * unitPx)
        }

    /** 透明度：第 3 层之后淡出；翻过一张以上的牌不再显示。 */
    fun alpha(d: Float): Float = when {
        d > VISIBLE_BEHIND -> (VISIBLE_BEHIND + 1f - d).coerceIn(0f, 1f)
        d < -1f -> (2f + d).coerceIn(0f, 1f)
        else -> 1f
    }

    /** “雾化”程度：后面的牌盖上一层页面底色，越远越淡。 */
    fun haze(d: Float): Float = if (d <= 0f) 0f else (0.24f * d).coerceAtMost(0.8f)

    /** 拖到第一张之前或最后一张之后时的阻尼：可以拉动一点，但越拉越费劲。 */
    fun rubberBand(raw: Float, count: Int): Float {
        val max = (count - 1).coerceAtLeast(0).toFloat()
        return when {
            raw < 0f -> raw * 0.25f
            raw > max -> max + (raw - max) * 0.25f
            else -> raw
        }
    }

    /**
     * 松手后停在哪一张。[velocityCards] 是“每秒翻过几张”，向后翻为正。
     * 甩得够快就按方向翻一张，否则停到最近的一张。
     */
    fun settleTarget(position: Float, velocityCards: Float, count: Int): Int {
        if (count <= 1) return 0
        val target = when {
            velocityCards > FLING -> floor(position).toInt() + 1
            velocityCards < -FLING -> ceil(position).toInt() - 1
            else -> position.roundToInt()
        }
        return target.coerceIn(0, count - 1)
    }

    private const val FLING = 1.6f
}
