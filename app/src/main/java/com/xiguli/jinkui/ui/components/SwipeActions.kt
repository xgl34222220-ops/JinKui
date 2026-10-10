package com.xiguli.jinkui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 行的一个滑动动作：露出的底色、图标与文字。 */
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val background: Color,
    val content: Color,
    val onAction: () -> Unit,
)

/**
 * 左右滑动的行（iOS 邮件 / Cashew 式）：向右滑露出 [start]，向左滑露出 [end]；
 * 滑过 88dp 松手执行动作并轻震一下，然后行弹回原位——不会真的把行滑走（删除之类由页面先确认）。
 * 只用 draggable：过了触摸阈值才算拖动，普通点按与长按完全不受影响。
 */
@Composable
fun SwipeActionsBox(
    start: SwipeAction?,
    end: SwipeAction?,
    modifier: Modifier = Modifier,
    /** 露出的底色的形状（与行所在分组的圆角一致）。 */
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(density) { 88.dp.toPx() }
    val limit = with(density) { 160.dp.toPx() }
    var armed by remember { mutableStateOf(false) }
    val latestStart by rememberUpdatedState(start)
    val latestEnd by rememberUpdatedState(end)
    val drag = rememberDraggableState { delta ->
        val min = if (latestEnd != null) -limit else 0f
        val max = if (latestStart != null) limit else 0f
        val next = (offset.value + delta).coerceIn(min, max)
        val nowArmed = abs(next) >= threshold
        if (nowArmed && !armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        armed = nowArmed
        scope.launch { offset.snapTo(next) }
    }
    Box(
        modifier.draggable(
            state = drag,
            orientation = Orientation.Horizontal,
            enabled = start != null || end != null,
            onDragStopped = {
                val value = offset.value
                if (value >= threshold) latestStart?.let { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it.onAction() }
                if (value <= -threshold) latestEnd?.let { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it.onAction() }
                armed = false
                offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f))
            },
        ),
    ) {
        val value = offset.value
        val action = when {
            value > 0f -> start
            value < 0f -> end
            else -> null
        }
        if (action != null) {
            Row(
                Modifier
                    .matchParentSize()
                    .background(action.background, shape)
                    .padding(horizontal = Space.x2_5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (value < 0f) Spacer(Modifier.weight(1f))
                SwipeLabel(action, emphasized = abs(value) >= threshold)
            }
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) {
            content()
        }
    }
}

@Composable
private fun RowScope.SwipeLabel(action: SwipeAction, emphasized: Boolean) {
    Icon(action.icon, contentDescription = null, tint = action.content, modifier = Modifier.size(if (emphasized) 22.dp else 20.dp))
    Spacer(Modifier.width(Space.x1))
    Text(action.label, style = JkType.bodyStrong, color = action.content)
}
