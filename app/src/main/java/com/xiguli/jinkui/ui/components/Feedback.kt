package com.xiguli.jinkui.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── 触感 ──────────────────────────────────────────────────────────
// 一律走 View.performHapticFeedback：跟随系统“触摸反馈”开关，不需要 VIBRATE 权限。

/** 确认类触感（复制成功、保存）。 */
fun View.confirmHaptic() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
}

/** 长按触感。 */
fun View.longPressHaptic() {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

// ── 按压反馈 ──────────────────────────────────────────────────────

/** 一个可按压元素的交互源与当前缩放；缩放用 [pressScale] 画出来，点击用 [pressClickable] 接上同一个交互源。 */
class JkPress internal constructor(val interaction: MutableInteractionSource, private val scaleState: State<Float>) {
    val scale: Float get() = scaleState.value
}

@Composable
fun rememberJkPress(pressedScale: Float = 0.98f): JkPress {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) pressedScale else 1f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "press")
    return remember(interaction) { JkPress(interaction, scale) }
}

/** 按下时轻微缩小（在绘制阶段读取缩放，不触发重组）。 */
fun Modifier.pressScale(press: JkPress): Modifier = graphicsLayer {
    val s = press.scale
    scaleX = s
    scaleY = s
}

/** 点击 / 长按，长按带触感；与 [pressScale] 共用 [press] 的交互源。 */
@Composable
fun Modifier.pressClickable(
    press: JkPress,
    onClickLabel: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
): Modifier {
    val view = LocalView.current
    return combinedClickable(
        interactionSource = press.interaction,
        indication = LocalIndication.current,
        onClickLabel = onClickLabel,
        onClick = onClick,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick?.let { action ->
            {
                view.longPressHaptic()
                action()
            }
        },
    )
}

// ── 复制反馈 ──────────────────────────────────────────────────────

/**
 * 复制之后的短暂反馈：确认触感 + 被复制那一项的图标变成对勾约 1.4 秒。
 * 提示文字（多少秒后清空剪贴板）仍由页面底部的消息条给出。
 */
class CopyFeedback internal constructor(private val view: View, private val scope: CoroutineScope) {
    var copiedKey by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    fun fire(key: String) {
        view.confirmHaptic()
        copiedKey = key
        job?.cancel()
        job = scope.launch {
            delay(1_400L)
            copiedKey = null
        }
    }

    fun isCopied(key: String): Boolean = copiedKey == key
}

@Composable
fun rememberCopyFeedback(): CopyFeedback {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    return remember(view, scope) { CopyFeedback(view, scope) }
}

/** 复制图标：复制后短暂变成对勾（收入绿），再淡回复制图标。 */
@Composable
fun CopyGlyph(copied: Boolean, tint: Color = Jk.colors.textTertiary, size: Dp = JkSize.icon) {
    val done = Jk.colors.success
    AnimatedContent(
        targetState = copied,
        transitionSpec = { (fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.6f)) togetherWith fadeOut(tween(100)) },
        label = "copyGlyph",
    ) { isCopied ->
        Icon(
            if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = if (isCopied) done else tint,
            modifier = Modifier.size(size),
        )
    }
}

/** 行尾的复制按钮：48dp 触控区，复制后图标短暂变成对勾。 */
@Composable
fun JkCopyButton(
    copied: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Jk.colors.textTertiary,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(JkSize.touch)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CopyGlyph(copied, if (enabled) tint else tint.copy(alpha = 0.4f))
    }
}
