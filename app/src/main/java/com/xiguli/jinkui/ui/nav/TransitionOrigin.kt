package com.xiguli.jinkui.ui.nav

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.remember

/**
 * 容器变换（从列表里的一张卡片 / 一笔流水展开到详情）的起点。
 *
 * 为什么不用 SharedTransitionLayout：金匮的导航刻意只保留当前一页——离开的页面连同其中的明文立即销毁，
 * 而共享元素过渡要求新旧两页在动画期间同时存在于组合里。这里改为“只做进入”的容器变换：
 * 手指按下列表项时记下它在屏幕上的位置，详情页进入时从这个矩形展开到全屏（圆角收为 0、内容淡入），旧页面照旧立刻销毁。
 */
object TransitionOrigin {
    private var rect: Rect? = null
    private var at = 0L

    fun mark(bounds: Rect) {
        rect = bounds
        at = SystemClock.uptimeMillis()
    }

    /** 取走最近 [maxAgeMs] 毫秒内按下的起点（只用一次）。 */
    fun take(maxAgeMs: Long = 1_200L): Rect? {
        val r = rect ?: return null
        rect = null
        return r.takeIf { SystemClock.uptimeMillis() - at <= maxAgeMs }
    }
}

/** 列表项：按下时把自己的位置记为容器变换的起点（不消费事件，不影响点击、长按与滑动）。 */
fun Modifier.transitionOrigin(): Modifier = composed {
    val holder = remember { arrayOfNulls<Rect>(1) }
    this
        .onGloballyPositioned { holder[0] = it.boundsInRoot() }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                holder[0]?.let { TransitionOrigin.mark(it) }
            }
        }
}

/** 从 [origin] 展开到整个容器的圆角矩形；[progress] 0 → 1。 */
class RevealShape(private val origin: Rect, private val progress: Float, private val radiusPx: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = progress.coerceIn(0f, 1f)
        val r = Rect(
            lerp(origin.left, 0f, p),
            lerp(origin.top, 0f, p),
            lerp(origin.right, size.width, p),
            lerp(origin.bottom, size.height, p),
        )
        return Outline.Rounded(RoundRect(r, CornerRadius(radiusPx * (1f - p))))
    }
}
