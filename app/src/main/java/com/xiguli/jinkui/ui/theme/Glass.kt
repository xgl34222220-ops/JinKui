package com.xiguli.jinkui.ui.theme

import android.content.Context
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.ceil

/*
 * 液态玻璃（1.3）的绘制工具，全部集中在这里，页面只通过 [jkSurface] / [jkBackdrop] 使用：
 *
 *  1. 页面底 [jkBackdrop]：薄荷 / 浅水绿的竖向渐变 + 几块大而软的径向光斑 + 淡淡的叶子剪影，纯 Canvas 自绘。
 *  2. 玻璃表面 [jkSurface]：阴影（只画在形状外面）→ 裁剪 → [可选] 背后内容的实时模糊 → 半透明填充
 *     → 顶部内光泽 → 左上最亮的 1px 高光描边。
 *  3. 实时模糊（Android 12 / API 31+）：
 *     - 悬浮底栏：[rememberJkBackdrop] + [jkBackdropSource] 把底栏后面的分区内容（连同页面底）录进一个
 *       GraphicsLayer（RenderNode）；底栏在自己的形状里把这层按位置平移后，套上 RenderEffect 模糊
 *       （[BlurEffect]）再画出来，所以底栏后面滚过的卡片和文字是真的被模糊了。录制层在硬件加速下只是对
 *       RenderNode 的引用，内容滚动时不需要重新录制底栏。
 *     - 底部面板与对话框：它们在独立的窗口里，用窗口级的背景模糊（FLAG_BLUR_BEHIND + blurBehindRadius），
 *       由系统合成器模糊面板后面的整屏内容；设备关闭了跨窗口模糊（省电模式等）时退回更不透明的填充。
 *  4. Android 12 以下：没有 RenderEffect，[surfacesFor] 直接把带模糊的表面换成更不透明的磨砂填充，这里不做模糊。
 *  5. “减少透明度”：[surfacesFor] 把所有表面换成不透明填充、模糊半径为 0。
 *
 * 不依赖任何第三方库。
 */

/** 一块可被模糊“透过去看”的背景：分区内容录制成的 GraphicsLayer 与它在屏幕上的位置。 */
@Stable
class JkBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberJkBackdrop(): JkBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { JkBackdrop(layer) }
}

/**
 * 把这个节点画出的内容录进 [backdrop]，同时照常画出来。用 [jkSurface] 的 backdrop 参数消费它的表面
 * 必须是这个节点的兄弟（画在它之后），不能在它里面，否则会自己录到自己。
 */
fun Modifier.jkBackdropSource(backdrop: JkBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/** 页面底：渐变 + 光斑 + 叶子剪影。整屏铺一次，不随内容滚动。 */
@Composable
fun Modifier.jkBackdrop(): Modifier {
    val c = Jk.colors
    return this.drawWithCache {
        val gradient = Brush.verticalGradient(listOf(c.backdropTop, c.backdropMid, c.backdropBottom))
        val w = size.width
        val h = size.height
        val dark = c.isDark
        onDrawBehind {
            drawRect(gradient)
            blob(c.blobLight, if (dark) 0.35f else 0.85f, Offset(w * 0.92f, h * 0.06f), w * 0.75f)
            blob(c.blobAqua, if (dark) 0.40f else 0.55f, Offset(w * 1.02f, h * 0.46f), w * 0.62f)
            blob(c.blobLight, if (dark) 0.18f else 0.60f, Offset(w * -0.05f, h * 0.58f), w * 0.58f)
            blob(c.blobTint, if (dark) 0.22f else 0.35f, Offset(w * 0.08f, h * 0.92f), w * 0.62f)
            blob(c.blobAqua, if (dark) 0.30f else 0.45f, Offset(w * 0.02f, h * 0.02f), w * 0.50f)
            // 淡淡的植物剪影：右下一丛、左上一枝
            val leafColor = c.leafDeep.copy(alpha = if (dark) 0.10f else 0.06f)
            val u = w / 100f
            silhouetteLeaf(Offset(w * 0.98f, h * 1.0f), 46 * u, 12 * u, -28f, leafColor)
            silhouetteLeaf(Offset(w * 0.98f, h * 1.0f), 38 * u, 10 * u, -52f, leafColor)
            silhouetteLeaf(Offset(w * 0.90f, h * 1.0f), 30 * u, 8 * u, -8f, leafColor)
            silhouetteLeaf(Offset(w * -0.02f, h * 0.20f), 34 * u, 9 * u, 118f, leafColor)
            silhouetteLeaf(Offset(w * -0.02f, h * 0.20f), 26 * u, 7 * u, 146f, leafColor)
        }
    }
}

private fun DrawScope.blob(color: Color, alpha: Float, center: Offset, radius: Float) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.55f to color.copy(alpha = alpha * 0.45f),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.silhouetteLeaf(base: Offset, length: Float, width: Float, angle: Float, color: Color) {
    withTransform({
        translate(base.x, base.y)
        rotate(angle, pivot = Offset.Zero)
    }) {
        val p = Path().apply {
            moveTo(0f, 0f)
            cubicTo(width, -length * 0.25f, width * 0.9f, -length * 0.75f, 0f, -length)
            cubicTo(-width * 0.9f, -length * 0.75f, -width, -length * 0.25f, 0f, 0f)
            close()
        }
        drawPath(p, color)
    }
}

/**
 * 按 [surface] 画出一块玻璃表面，裁剪成 [shape]。
 * [backdrop] 不为空且 [JkSurface.blur] > 0（Android 12+、未减少透明度）时，形状里先画出模糊后的背后内容。
 */
fun Modifier.jkSurface(surface: JkSurface, shape: Shape, backdrop: JkBackdrop? = null): Modifier {
    var m = this
    if (surface.shadow > 0f) m = m.glassShadow(shape, surface.shadowColor, surface.shadow)
    m = m.clip(shape)
    if (backdrop != null && surface.blur > 0.dp && blurSupported) m = m.backdropBlur(backdrop, surface.blur)
    m = m.background(surface.fill, shape)
    if (surface.gloss > 0f) m = m.glassGloss(surface.highlightColor, surface.gloss)
    if (surface.highlight > 0f) m = m.border(1.dp, specularBrush(surface.highlightColor, surface.highlight), shape)
    if (surface.strokeWidth > 0.dp && surface.stroke.alpha > 0f) m = m.border(surface.strokeWidth, surface.stroke, shape)
    return m
}

/** 1px 高光描边：左上最亮，往右下淡出，右下角再有一点点反光。 */
fun specularBrush(color: Color, strength: Float): Brush = Brush.linearGradient(
    0f to color.copy(alpha = 0.95f * strength),
    0.35f to color.copy(alpha = 0.35f * strength),
    0.7f to color.copy(alpha = 0.06f * strength),
    1f to color.copy(alpha = 0.30f * strength),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** 内光泽：顶部一层由白到透明的渐变，像玻璃上沿的反光。 */
private fun Modifier.glassGloss(color: Color, strength: Float): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(
        0f to color.copy(alpha = 0.55f * strength),
        0.45f to color.copy(alpha = 0.10f * strength),
        1f to color.copy(alpha = 0f),
        startY = 0f,
        endY = size.height,
    )
    onDrawBehind { drawRect(brush) }
}

/**
 * 柔和的宽阴影：形状外面一圈圈外扩、几乎透明的轮廓叠出来，稍往下偏。只画在形状外（ClipOp.Difference），
 * 不会透过半透明的玻璃把内部压暗。不依赖系统硬件阴影，截图、低版本和深色下都一致。
 */
private fun Modifier.glassShadow(shape: Shape, color: Color, alphaPerLayer: Float): Modifier = drawWithCache {
    val layers = 7
    val step = 2.2.dp.toPx()
    val dy = 5.dp.toPx()
    val inside = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val rings = (1..layers).map { i ->
        val spread = i * step
        val s = Size(size.width + spread * 2, size.height + spread * 2)
        Triple(shape.createOutline(s, layoutDirection, this@drawWithCache), Offset(-spread, -spread + dy * (0.4f + i / layers.toFloat())), alphaPerLayer * (1f - (i - 1f) / (layers + 1f)))
    }
    onDrawBehind {
        clipPath(inside, ClipOp.Difference) {
            for ((outline, offset, a) in rings) {
                translate(offset.x, offset.y) { drawOutline(outline, color.copy(alpha = a)) }
            }
        }
    }
}

/** 在自己的位置上画出 [backdrop] 的模糊副本（调用方已经裁剪成形状）。仅 Android 12+。 */
private fun Modifier.backdropBlur(backdrop: JkBackdrop, radius: Dp): Modifier = composed {
    val holder = remember { PositionHolder() }
    this
        .onGloballyPositioned { holder.position = it.positionInRoot() }
        .drawWithCache {
            val blurred = obtainGraphicsLayer()
            val r = radius.toPx()
            val pad = ceil(r * 2f)
            blurred.renderEffect = BlurEffect(r, r, TileMode.Clamp)
            onDrawBehind {
                val dx = backdrop.origin.x - holder.position.x
                val dy = backdrop.origin.y - holder.position.y
                blurred.record(size = IntSize((size.width + pad * 2).toInt(), (size.height + pad * 2).toInt())) {
                    translate(dx + pad, dy + pad) { drawLayer(backdrop.layer) }
                }
                translate(-pad, -pad) { drawLayer(blurred) }
            }
        }
}

@Stable
private class PositionHolder {
    var position by mutableStateOf(Offset.Zero)
}

/**
 * 底部面板 / 对话框窗口的背景模糊（Android 12+）：让系统模糊窗口后面的整屏内容。
 * 返回实际是否在模糊；为 false 时调用方应改用更不透明的填充。
 */
@Composable
fun jkWindowBlur(radius: Dp): Boolean {
    if (!blurSupported || radius <= 0.dp) return false
    val context = LocalContext.current
    val enabled = remember(context) { WindowBlurApi.crossWindowBlurEnabled(context) }
    if (!enabled) return false
    val view = LocalView.current
    val px = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, px) {
        (view.parent as? DialogWindowProvider)?.window?.let { WindowBlurApi.blurBehind(it, px) }
        onDispose { }
    }
    return true
}

/** 当前材质下面板 / 对话框该用的表面：系统不做跨窗口模糊时，换成回退的不透明磨砂填充。 */
@Composable
fun windowGlass(surface: JkSurface, opaqueFallback: Color): JkSurface {
    val blurring = jkWindowBlur(surface.blur)
    return if (surface.blur > 0.dp && !blurring) surface.copy(fill = opaqueFallback, blur = 0.dp) else surface
}

@RequiresApi(Build.VERSION_CODES.S)
private object WindowBlurApi {
    fun crossWindowBlurEnabled(context: Context): Boolean =
        runCatching { (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled }.getOrDefault(false)

    fun blurBehind(window: Window, px: Int) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = px }
    }
}
