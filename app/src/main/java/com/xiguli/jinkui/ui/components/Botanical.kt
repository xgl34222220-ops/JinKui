package com.xiguli.jinkui.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkColors
import com.xiguli.jinkui.ui.theme.Radius
import kotlin.math.cos
import kotlin.math.sin

/**
 * 自绘的植物插画（1.1 薄荷）：远山、几片长叶、小花与草籽，全部是 Canvas 路径，
 * 不用任何图片资源。只是装饰，读屏跳过（clearAndSetSemantics）。
 *
 * [Layout.RIGHT]：叶丛在右下角（账本头图）；[Layout.BOTH]：左右两侧各一丛（“我的”头图）；
 * [Layout.SPRIG]：右上角一小枝（资产头图）。
 */
enum class BotanicalLayout { RIGHT, BOTH, SPRIG }

@Composable
fun BotanicalCard(
    modifier: Modifier = Modifier,
    layout: BotanicalLayout = BotanicalLayout.RIGHT,
    shape: Shape = Radius.card,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Jk.colors
    Box(modifier.clip(shape)) {
        Canvas(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics { },
        ) {
            drawRect(colors.hero)
            drawBotanical(colors, layout)
        }
        content()
    }
}

fun DrawScope.drawBotanical(colors: JkColors, layout: BotanicalLayout) {
    val w = size.width
    val h = size.height
    // 远山：两层柔和的山脊，越远越淡
    val far = Path().apply {
        moveTo(0f, h * 0.78f)
        cubicTo(w * 0.18f, h * 0.62f, w * 0.30f, h * 0.70f, w * 0.42f, h * 0.60f)
        cubicTo(w * 0.55f, h * 0.48f, w * 0.66f, h * 0.36f, w * 0.80f, h * 0.44f)
        cubicTo(w * 0.90f, h * 0.50f, w * 0.96f, h * 0.46f, w, h * 0.40f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(far, colors.hill.copy(alpha = 0.55f))
    val near = Path().apply {
        moveTo(0f, h * 0.90f)
        cubicTo(w * 0.20f, h * 0.80f, w * 0.34f, h * 0.92f, w * 0.52f, h * 0.80f)
        cubicTo(w * 0.66f, h * 0.70f, w * 0.80f, h * 0.76f, w, h * 0.66f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(near, colors.hill.copy(alpha = 0.9f))

    val unit = minOf(w, h)
    when (layout) {
        BotanicalLayout.RIGHT -> {
            seeds(colors, Offset(w * 0.52f, h * 0.55f), unit * 0.08f)
            cluster(colors, Offset(w * 0.86f, h * 1.02f), unit * 0.62f, mirror = false)
            flower(colors, Offset(w * 0.79f, h * 0.50f), unit * 0.065f)
            flower(colors, Offset(w * 0.70f, h * 0.84f), unit * 0.05f)
            fern(colors, Offset(w * 0.60f, h * 0.98f), unit * 0.34f, -18f)
        }
        BotanicalLayout.BOTH -> {
            cluster(colors, Offset(w * 0.06f, h * 1.04f), unit * 0.48f, mirror = true)
            cluster(colors, Offset(w * 0.94f, h * 1.04f), unit * 0.55f, mirror = false)
            flower(colors, Offset(w * 0.16f, h * 0.72f), unit * 0.05f, white = true)
            flower(colors, Offset(w * 0.84f, h * 0.70f), unit * 0.05f, white = true)
            flower(colors, Offset(w * 0.76f, h * 0.86f), unit * 0.04f, white = true)
            seeds(colors, Offset(w * 0.22f, h * 0.24f), unit * 0.07f)
        }
        BotanicalLayout.SPRIG -> {
            fern(colors, Offset(w * 0.46f, h * 0.02f), unit * 0.30f, 200f)
            cluster(colors, Offset(w * 1.0f, h * 1.02f), unit * 0.42f, mirror = false)
            cluster(colors, Offset(w * 0.0f, h * 1.04f), unit * 0.30f, mirror = true)
            flower(colors, Offset(w * 0.92f, h * 0.80f), unit * 0.07f)
            seeds(colors, Offset(w * 0.93f, h * 0.30f), unit * 0.07f)
            seeds(colors, Offset(w * 0.10f, h * 0.10f), unit * 0.06f)
        }
    }
}

/** 一片长叶：从 [base] 朝 [angle]（度，0 为正上）伸出，带一条叶脉。 */
private fun DrawScope.leaf(base: Offset, length: Float, width: Float, angle: Float, color: Color, vein: Color) {
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
        drawLine(vein, Offset(0f, -length * 0.05f), Offset(0f, -length * 0.92f), strokeWidth = (width * 0.10f).coerceAtLeast(1f), cap = StrokeCap.Round)
        // 几道细侧脉
        for (i in 1..4) {
            val y = -length * (0.18f + i * 0.15f)
            drawLine(vein.copy(alpha = 0.6f), Offset(0f, y), Offset(width * 0.45f, y - length * 0.08f), strokeWidth = (width * 0.06f).coerceAtLeast(0.8f), cap = StrokeCap.Round)
            drawLine(vein.copy(alpha = 0.6f), Offset(0f, y), Offset(-width * 0.45f, y - length * 0.08f), strokeWidth = (width * 0.06f).coerceAtLeast(0.8f), cap = StrokeCap.Round)
        }
    }
}

/** 一丛叶子：深浅几片交错。 */
private fun DrawScope.cluster(colors: JkColors, base: Offset, size: Float, mirror: Boolean) {
    val s = if (mirror) -1f else 1f
    val vein = colors.hero.copy(alpha = 0.55f)
    leaf(base, size * 0.95f, size * 0.20f, s * -38f, colors.leaf.copy(alpha = 0.85f), vein)
    leaf(base, size * 1.10f, size * 0.22f, s * -14f, colors.leafDeep, vein)
    leaf(base, size * 0.80f, size * 0.18f, s * 10f, colors.leaf, vein)
    leaf(base + Offset(s * size * -0.25f, 0f), size * 0.62f, size * 0.17f, s * -60f, colors.leafDeep.copy(alpha = 0.9f), vein)
    leaf(base + Offset(s * size * 0.08f, 0f), size * 0.55f, size * 0.15f, s * 32f, colors.leafDeep.copy(alpha = 0.8f), vein)
}

/** 小花：五片圆瓣 + 花心。 */
private fun DrawScope.flower(colors: JkColors, center: Offset, r: Float, white: Boolean = false) {
    val petal = if (white) Color.White.copy(alpha = if (colors.isDark) 0.75f else 0.95f) else colors.flower
    for (i in 0 until 5) {
        val a = Math.toRadians(i * 72.0 - 90.0)
        drawCircle(petal, r * 0.55f, center + Offset((cos(a) * r * 0.62f).toFloat(), (sin(a) * r * 0.62f).toFloat()))
    }
    drawCircle(if (white) colors.leaf else colors.leafDeep.copy(alpha = 0.8f), r * 0.32f, center)
}

/** 一枝蕨叶：一根弯曲的茎，两侧小叶。 */
private fun DrawScope.fern(colors: JkColors, base: Offset, length: Float, angle: Float) {
    rotate(angle, pivot = base) {
        val stem = Path().apply {
            moveTo(base.x, base.y)
            quadraticBezierTo(base.x + length * 0.12f, base.y - length * 0.5f, base.x - length * 0.05f, base.y - length)
        }
        drawPath(stem, colors.leafDeep.copy(alpha = 0.7f), style = Stroke(width = (length * 0.025f).coerceAtLeast(1.2f), cap = StrokeCap.Round))
        for (i in 1..6) {
            val t = i / 7f
            val p = Offset(base.x + length * 0.08f * (1 - t) * 1.2f, base.y - length * t)
            val l = length * 0.22f * (1f - t * 0.6f)
            leaf(p, l, l * 0.28f, -58f, colors.leaf.copy(alpha = 0.75f), Color.Transparent)
            leaf(p, l, l * 0.28f, 58f, colors.leaf.copy(alpha = 0.75f), Color.Transparent)
        }
    }
}

/** 几粒草籽 / 花粉点。 */
private fun DrawScope.seeds(colors: JkColors, at: Offset, spread: Float) {
    val tone = colors.flower.copy(alpha = 0.8f)
    val pts = listOf(0f to 0f, 0.9f to -0.6f, 1.6f to 0.3f, 0.4f to 1.1f, -0.8f to 0.5f)
    pts.forEachIndexed { i, (x, y) ->
        drawCircle(tone, spread * (if (i % 2 == 0) 0.11f else 0.08f), at + Offset(x * spread, y * spread))
    }
}

/** 日历空状态：一页台历（浅薄荷格子、圈出的一天）和一枝小叶，底下一块柔和的影子。 */
@Composable
fun EmptyCalendarArt(modifier: Modifier = Modifier, size: Dp = 150.dp) {
    val colors = Jk.colors
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val u = this.size.minDimension / 100f
        drawOval(colors.surfaceAlt, Offset(14 * u, 74 * u), Size(72 * u, 16 * u))
        drawCircle(colors.hero, 40 * u, Offset(50 * u, 46 * u))
        // 台历
        val l = 26 * u; val t = 22 * u; val r = 74 * u; val b = 76 * u
        drawRoundRect(colors.surface, Offset(l, t), Size(r - l, b - t), CornerRadius(7 * u))
        drawRoundRect(colors.textPrimary.copy(alpha = 0.75f), Offset(l, t), Size(r - l, b - t), CornerRadius(7 * u), style = Stroke(1.8f * u))
        drawRoundRect(colors.primary.copy(alpha = 0.85f), Offset(l, t), Size(r - l, 10 * u), CornerRadius(7 * u, 7 * u))
        drawRect(colors.primary.copy(alpha = 0.85f), Offset(l, t + 5 * u), Size(r - l, 5 * u))
        for (x in listOf(36f, 64f)) {
            drawLine(colors.textPrimary.copy(alpha = 0.75f), Offset(x * u, 16 * u), Offset(x * u, 27 * u), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
        }
        for (row in 0 until 3) for (col in 0 until 4) {
            val x = l + 5 * u + col * 11 * u
            val y = t + 16 * u + row * 11 * u
            val cell = if (row == 1 && col == 2) colors.primary.copy(alpha = 0.25f) else colors.hero
            drawRoundRect(cell, Offset(x, y), Size(8 * u, 7 * u), CornerRadius(2 * u))
        }
        drawRoundRect(colors.primary, Offset(l + 5 * u + 22 * u - 1.2f * u, t + 27 * u - 1.2f * u), Size(10.4f * u, 9.4f * u), CornerRadius(2.6f * u), style = Stroke(1.4f * u, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.4f * u, 1.6f * u))))
        // 一枝小叶
        leaf(Offset(78 * u, 80 * u), 26 * u, 6 * u, -22f, colors.leaf, colors.hero.copy(alpha = 0.6f))
        leaf(Offset(78 * u, 80 * u), 20 * u, 5 * u, 14f, colors.leafDeep, colors.hero.copy(alpha = 0.6f))
        flower(colors, Offset(20 * u, 66 * u), 4.5f * u)
    }
}

/** 头图里的应用字标：薄荷圆底上一枚线条“匣子”。 */
@Composable
fun AppGlyph(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val colors = Jk.colors
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val u = this.size.minDimension / 24f
        drawCircle(colors.surface, 12 * u)
        drawCircle(colors.primary, 10.4f * u)
        val ink = colors.onPrimary
        // 匣子：盒身 + 盖 + 锁扣
        drawRoundRect(ink, Offset(6.5f * u, 10f * u), Size(11f * u, 7.5f * u), CornerRadius(1.6f * u), style = Stroke(1.3f * u))
        drawRoundRect(ink, Offset(5.8f * u, 7.2f * u), Size(12.4f * u, 3f * u), CornerRadius(1.2f * u), style = Stroke(1.3f * u))
        drawCircle(colors.hero, 1.3f * u, Offset(12f * u, 13f * u))
        // 一片小叶
        leaf(Offset(16.6f * u, 7.4f * u), 4.6f * u, 1.3f * u, 30f, colors.hero, Color.Transparent)
    }
}

/** 头图卡片里铺满宽度的一条插画（不含内容），用于需要单独放插画的地方。 */
@Composable
fun BotanicalStrip(modifier: Modifier = Modifier, layout: BotanicalLayout = BotanicalLayout.RIGHT) {
    val colors = Jk.colors
    Canvas(modifier.fillMaxWidth().clearAndSetSemantics { }) { drawBotanical(colors, layout) }
}
