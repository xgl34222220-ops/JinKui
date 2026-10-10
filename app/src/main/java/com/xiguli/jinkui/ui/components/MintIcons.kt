package com.xiguli.jinkui.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.ui.theme.Jk
import kotlin.math.cos
import kotlin.math.sin

/**
 * 金匮自己画的线条图标（1.1 薄荷）：24 × 24 的网格、1.8 线宽、圆头圆角，
 * 每个图标有一处小小的薄荷色点缀（书签、日期点、钱包扣、饼图一角……）。
 * [filled] 为 true 时主体里再铺一层浅薄荷（底栏选中、“我的”宫格）。
 * 全部是 Canvas 路径，不用任何图片资源，也不取自任何第三方图标集。
 */
enum class MintGlyph {
    LEDGER, CALENDAR, CARDS, STATS, ME,
    RECURRING, BUDGET, ASSETS, CATEGORY, TAG, BACKUP, LOCK, SETTINGS,
    SEARCH, MORE, ADD_NOTE, SHIELD, ORGANIZE, KEY, INFO,
}

@Composable
fun MintIcon(
    glyph: MintGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Jk.colors.textPrimary,
    accent: Color = Jk.colors.primary,
    fill: Color = Jk.colors.accentSoft,
    filled: Boolean = false,
    contentDescription: String? = null,
) {
    Canvas(
        modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            drawGlyph(glyph, tint, accent, if (filled) fill else Color.Transparent)
        }
    }
}

private val line = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.rr(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Color, style: androidx.compose.ui.graphics.drawscope.DrawStyle = line) {
    drawRoundRect(color, Offset(l, t), Size(r - l, b - t), CornerRadius(radius), style = style)
}

private fun DrawScope.seg(x1: Float, y1: Float, x2: Float, y2: Float, color: Color) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.8f, cap = StrokeCap.Round)
}

private fun DrawScope.dot(x: Float, y: Float, r: Float, color: Color) = drawCircle(color, r, Offset(x, y))

private fun DrawScope.drawGlyph(g: MintGlyph, ink: Color, accent: Color, fill: Color) {
    when (g) {
        MintGlyph.LEDGER -> {
            rr(5f, 3f, 19f, 21f, 3f, fill, Fill)
            rr(5f, 3f, 19f, 21f, 3f, ink)
            seg(8.8f, 3.6f, 8.8f, 20.4f, ink)
            // 书签
            val p = Path().apply {
                moveTo(12.5f, 3f); lineTo(16.5f, 3f); lineTo(16.5f, 10f); lineTo(14.5f, 8.4f); lineTo(12.5f, 10f); close()
            }
            drawPath(p, accent)
            seg(11.5f, 14.5f, 16f, 14.5f, ink)
            seg(11.5f, 17.5f, 14.5f, 17.5f, ink)
        }
        MintGlyph.CALENDAR -> {
            rr(3.5f, 5.5f, 20.5f, 20.5f, 3.5f, fill, Fill)
            rr(3.5f, 5.5f, 20.5f, 20.5f, 3.5f, ink)
            seg(8f, 3.5f, 8f, 7.5f, ink)
            seg(16f, 3.5f, 16f, 7.5f, ink)
            seg(4f, 10f, 20f, 10f, ink)
            dot(8f, 14.6f, 1.15f, ink)
            dot(12f, 14.6f, 1.6f, accent)
            dot(16f, 14.6f, 1.15f, ink)
            dot(8f, 17.6f, 1.15f, ink)
            dot(12f, 17.6f, 1.15f, ink)
        }
        MintGlyph.CARDS -> {
            rr(3f, 6.5f, 21f, 20f, 3.5f, fill, Fill)
            rr(3f, 6.5f, 21f, 20f, 3.5f, ink)
            val flap = Path().apply { moveTo(5.5f, 6.5f); lineTo(15.5f, 3.6f); quadraticBezierTo(17.2f, 3.2f, 17.6f, 4.8f); lineTo(18.2f, 6.5f) }
            drawPath(flap, ink, style = line)
            rr(14f, 10.6f, 21f, 15.9f, 2.6f, ink)
            dot(16.9f, 13.25f, 1.35f, accent)
        }
        MintGlyph.STATS -> {
            // 饼图：大半圆 + 右上角挪出来的一块
            val r = Rect(Offset(11f, 13f) - Offset(8f, 8f), Size(16f, 16f))
            drawArc(fill, 0f, 270f, useCenter = true, topLeft = r.topLeft, size = r.size)
            drawArc(ink, 0f, 270f, useCenter = false, topLeft = r.topLeft, size = r.size, style = line)
            seg(11f, 5f, 11f, 13f, ink)
            seg(11f, 13f, 19f, 13f, ink)
            val slice = Rect(Offset(13.2f, 10.8f) - Offset(7.4f, 7.4f), Size(14.8f, 14.8f))
            drawArc(accent, 270f, 90f, useCenter = true, topLeft = slice.topLeft, size = slice.size)
        }
        MintGlyph.ME -> {
            dot(12f, 12f, 9f, fill)
            drawCircle(ink, 9f, Offset(12f, 12f), style = line)
            dot(9.1f, 10.3f, 1.25f, ink)
            dot(14.9f, 10.3f, 1.25f, ink)
            drawArc(ink, 25f, 130f, useCenter = false, topLeft = Offset(8f, 9f), size = Size(8f, 7f), style = line)
            dot(7.2f, 13.6f, 1.15f, accent.copy(alpha = 0.75f))
            dot(16.8f, 13.6f, 1.15f, accent.copy(alpha = 0.75f))
        }
        MintGlyph.RECURRING -> {
            dot(12f, 12f, 8.5f, fill)
            drawArc(ink, 300f, 300f, useCenter = false, topLeft = Offset(3.5f, 3.5f), size = Size(17f, 17f), style = line)
            // 箭头
            seg(19.2f, 3.8f, 19f, 7.6f, ink)
            seg(19f, 7.6f, 15.3f, 7.2f, ink)
            seg(12f, 8f, 12f, 12f, ink)
            seg(12f, 12f, 14.8f, 13.6f, ink)
            dot(12f, 12f, 1.6f, accent)
        }
        MintGlyph.BUDGET -> {
            dot(12f, 12f, 8.5f, fill)
            drawCircle(ink, 8.5f, Offset(12f, 12f), style = line)
            drawCircle(ink, 4.6f, Offset(12f, 12f), style = line)
            dot(12f, 12f, 2f, accent)
            seg(12f, 12f, 20.5f, 3.5f, ink)
        }
        MintGlyph.ASSETS -> {
            // 一摞钱币
            rr(3.5f, 14.5f, 15.5f, 19.5f, 2.5f, fill, Fill)
            rr(3.5f, 14.5f, 15.5f, 19.5f, 2.5f, ink)
            rr(5f, 9.5f, 17f, 14.5f, 2.5f, ink)
            rr(3.5f, 4.5f, 15.5f, 9.5f, 2.5f, ink)
            dot(18.3f, 17.2f, 3f, accent)
        }
        MintGlyph.CATEGORY -> {
            rr(3.5f, 3.5f, 10.5f, 10.5f, 2.6f, fill, Fill)
            rr(3.5f, 3.5f, 10.5f, 10.5f, 2.6f, ink)
            rr(13.5f, 3.5f, 20.5f, 10.5f, 2.6f, ink)
            rr(3.5f, 13.5f, 10.5f, 20.5f, 2.6f, ink)
            dot(17f, 17f, 3.6f, accent)
        }
        MintGlyph.TAG -> {
            val p = Path().apply {
                moveTo(3.5f, 5.5f); quadraticBezierTo(3.5f, 3.5f, 5.5f, 3.5f); lineTo(11.6f, 3.5f)
                lineTo(20.2f, 12.1f); quadraticBezierTo(21f, 13f, 20.2f, 13.9f); lineTo(13.9f, 20.2f)
                quadraticBezierTo(13f, 21f, 12.1f, 20.2f); lineTo(3.5f, 11.6f); close()
            }
            drawPath(p, fill)
            drawPath(p, ink, style = line)
            dot(8.2f, 8.2f, 1.8f, accent)
            seg(11.5f, 14.5f, 14.5f, 11.5f, ink)
        }
        MintGlyph.BACKUP -> {
            // 带锁扣的匣子 + 向下的箭头：存进加密备份
            rr(3.5f, 8.5f, 20.5f, 20f, 3f, fill, Fill)
            rr(3.5f, 8.5f, 20.5f, 20f, 3f, ink)
            seg(6f, 4.5f, 18f, 4.5f, ink)
            seg(12f, 10.8f, 12f, 16.6f, ink)
            seg(9.6f, 14.4f, 12f, 16.8f, ink)
            seg(14.4f, 14.4f, 12f, 16.8f, ink)
            dot(17.4f, 11.6f, 1.4f, accent)
        }
        MintGlyph.LOCK -> {
            rr(4.5f, 10f, 19.5f, 20.5f, 3f, fill, Fill)
            rr(4.5f, 10f, 19.5f, 20.5f, 3f, ink)
            drawArc(ink, 180f, 180f, useCenter = false, topLeft = Offset(7.8f, 3.6f), size = Size(8.4f, 9.4f), style = line)
            seg(7.8f, 8.3f, 7.8f, 10f, ink)
            seg(16.2f, 8.3f, 16.2f, 10f, ink)
            dot(12f, 14.6f, 1.9f, accent)
            seg(12f, 15.5f, 12f, 17.6f, ink)
        }
        MintGlyph.SETTINGS -> {
            val gear = Path()
            val teeth = 8
            for (i in 0 until teeth * 2) {
                val a = Math.PI * 2 * i / (teeth * 2) - Math.PI / 2
                val rad = if (i % 2 == 0) 9f else 7f
                // 每个齿画成两个点，形成略带平顶的齿
                for (d in listOf(-0.16, 0.16)) {
                    val x = 12f + rad * cos(a + d).toFloat()
                    val y = 12f + rad * sin(a + d).toFloat()
                    if (i == 0 && d < 0) gear.moveTo(x, y) else gear.lineTo(x, y)
                }
            }
            gear.close()
            drawPath(gear, fill)
            drawPath(gear, ink, style = line)
            dot(12f, 12f, 3.1f, accent)
        }
        MintGlyph.SEARCH -> {
            dot(10.5f, 10.5f, 6.5f, fill)
            drawCircle(ink, 6.5f, Offset(10.5f, 10.5f), style = line)
            seg(15.4f, 15.4f, 20f, 20f, ink)
            dot(8.4f, 8.4f, 1.3f, accent)
        }
        MintGlyph.MORE -> {
            dot(12f, 5.5f, 1.9f, accent)
            dot(12f, 12f, 1.9f, ink)
            dot(12f, 18.5f, 1.9f, ink)
        }
        MintGlyph.ADD_NOTE -> {
            rr(4f, 3.5f, 18f, 20.5f, 3f, fill, Fill)
            rr(4f, 3.5f, 18f, 20.5f, 3f, ink)
            seg(7.5f, 8f, 14.5f, 8f, ink)
            seg(7.5f, 11.5f, 12f, 11.5f, ink)
            dot(17.5f, 17f, 4.2f, accent)
            drawLine(Color.White, Offset(17.5f, 15f), Offset(17.5f, 19f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(15.5f, 17f), Offset(19.5f, 17f), strokeWidth = 1.6f, cap = StrokeCap.Round)
        }
        MintGlyph.SHIELD -> {
            val p = Path().apply {
                moveTo(12f, 3f); lineTo(19.5f, 6f); lineTo(19.5f, 11.5f)
                quadraticBezierTo(19.5f, 18f, 12f, 21f); quadraticBezierTo(4.5f, 18f, 4.5f, 11.5f); lineTo(4.5f, 6f); close()
            }
            drawPath(p, fill)
            drawPath(p, ink, style = line)
            seg(8.8f, 12f, 11.2f, 14.4f, accent)
            seg(11.2f, 14.4f, 15.6f, 9.8f, accent)
        }
        MintGlyph.ORGANIZE -> {
            rr(3.5f, 4f, 20.5f, 9f, 2.5f, fill, Fill)
            rr(3.5f, 4f, 20.5f, 9f, 2.5f, ink)
            rr(3.5f, 11.5f, 20.5f, 16.5f, 2.5f, ink)
            seg(5f, 20f, 19f, 20f, ink)
            dot(17.2f, 6.5f, 1.4f, accent)
        }
        MintGlyph.KEY -> {
            dot(8f, 15.5f, 4.5f, fill)
            drawCircle(ink, 4.5f, Offset(8f, 15.5f), style = line)
            seg(11.2f, 12.3f, 20f, 3.5f, ink)
            seg(17f, 6.5f, 19.5f, 9f, ink)
            seg(14.6f, 8.9f, 16.4f, 10.7f, ink)
            dot(8f, 15.5f, 1.6f, accent)
        }
        MintGlyph.INFO -> {
            dot(12f, 12f, 8.5f, fill)
            drawCircle(ink, 8.5f, Offset(12f, 12f), style = line)
            seg(12f, 11f, 12f, 16.5f, ink)
            dot(12f, 7.8f, 1.4f, accent)
        }
    }
}
