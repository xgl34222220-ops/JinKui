package com.xiguli.jinkui.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardPalette
import com.xiguli.jinkui.data.model.Expiry
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space

/** 一张卡面的配色：斜向渐变的两端，加一个用于图标、标签和纹理细节的点缀色。 */
class CardTheme(val start: Color, val end: Color, val accent: Color)

/**
 * 每种卡片类型有自己的一套卡面语言——配色、纹理、标志物各不相同，一眼就能分开；
 * 但版式、圆角、描边和字体完全一致，放在一起仍是同一个系列。
 *
 * | 类型 | 底色 | 纹理与标志物 |
 * | --- | --- | --- |
 * | 登录密码 | 靛青 | 渐隐的密码点阵 |
 * | 银行卡 | 玄黑描金 | 斜纹底 + 金色芯片 |
 * | U 卡（虚拟卡） | 钢青 | 电路走线 + 节点环 + 描线芯片 |
 * | 身份证 | 证件蓝 | 防伪同心纹 + 人像框 |
 * | 护照 | 绛红烫金 | 经纬地球 |
 * | 社保卡 | 松绿 | 十字纹 |
 * | 驾驶证 | 沥青灰 | 向远处收拢的车道线 |
 * | 会员卡 | 黛紫 | 斜向缎带 + 星芒 |
 * | 门禁卡 | 岩灰 | 感应波纹 |
 * | Wi-Fi | 湖蓝 | 信号弧 |
 * | 自定义 | 暖墨 | 简册竖纹 + 一道金色编绳 |
 *
 * 用户在编辑页另选颜色后，底色跟着换，纹理仍按类型保留。
 */
fun cardTheme(type: CardType, colorIndex: Int, brand: Color? = null): CardTheme {
    if (brand != null && colorIndex < 0) {
        // 银行品牌色：亮端略压暗、暗端压到接近墨色，白字与金色芯片都保持足够对比
        return CardTheme(lerp(brand, Color.Black, 0.12f), lerp(brand, Color.Black, 0.64f), Color(0xFFE8C77E))
    }
    if (colorIndex >= 0) {
        val base = Color(CardPalette.argb(colorIndex))
        return CardTheme(lerp(base, Color.White, 0.10f), lerp(base, Color.Black, 0.26f), Color.White)
    }
    return when (type) {
        CardType.LOGIN -> CardTheme(Color(0xFF3D4380), Color(0xFF1A1D3F), Color(0xFFB7BEFF))
        CardType.BANK -> CardTheme(Color(0xFF3A342A), Color(0xFF14110C), Color(0xFFDDB86E))
        CardType.UKEY -> CardTheme(Color(0xFF1F656E), Color(0xFF0B2A30), Color(0xFF93E3EA))
        CardType.ID_CARD -> CardTheme(Color(0xFF40629A), Color(0xFF1A2C4D), Color(0xFFC2D6F7))
        CardType.PASSPORT -> CardTheme(Color(0xFF7F2C38), Color(0xFF390F15), Color(0xFFE9C57E))
        CardType.SOCIAL -> CardTheme(Color(0xFF2E7758), Color(0xFF103325), Color(0xFFAEE8C9))
        CardType.DRIVER -> CardTheme(Color(0xFF47515E), Color(0xFF1B2027), Color(0xFFF2C54F))
        CardType.MEMBER -> CardTheme(Color(0xFF6C4494), Color(0xFF2B1840), Color(0xFFE5C8FF))
        CardType.ACCESS -> CardTheme(Color(0xFF6B655D), Color(0xFF2C2925), Color(0xFFEAE4D8))
        CardType.WIFI -> CardTheme(Color(0xFF1D76A4), Color(0xFF0B344F), Color(0xFFA6DEFF))
        CardType.CUSTOM -> CardTheme(Color(0xFF56483B), Color(0xFF231C16), Color(0xFFD9AE5C))
    }
}

/**
 * 卡面：用在首页置顶区、详情页顶部、编辑页预览和“新建卡片”的类型选择里。
 * [mini] 为类型选择用的小号卡面，只显示图标与类型名。
 */
@Composable
fun CardFace(
    title: String,
    type: CardType,
    colorIndex: Int,
    hint: String,
    modifier: Modifier = Modifier,
    secondaryLabel: String = "",
    secondary: String = "",
    pinned: Boolean = false,
    aspect: Float = 1.586f,
    mini: Boolean = false,
    /** 银行品牌色（[BankBrand]），null 时用类型默认卡面。 */
    brand: Color? = null,
) {
    val theme = cardTheme(type, colorIndex, brand)
    val ink = Color.White
    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(if (mini) Radius.field else Radius.card)
            .background(Brush.linearGradient(listOf(theme.start, theme.end)))
            .border(1.dp, ink.copy(alpha = 0.14f), if (mini) Radius.field else Radius.card),
    ) {
        Canvas(Modifier.matchParentSize()) { drawCardTexture(type, theme.accent, mini) }
        if (mini) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Space.x1_5),
            ) {
                Icon(type.icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.weight(1f))
                Text(type.label, style = JkType.bodyStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Space.x2_5),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(type.icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.x1))
                    Text(type.label, style = JkType.caption, color = theme.accent, modifier = Modifier.weight(1f))
                    if (pinned) {
                        Icon(Icons.Outlined.PushPin, contentDescription = "已置顶", tint = theme.accent, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = hint,
                            style = JkType.cardNumber,
                            color = ink.copy(alpha = 0.74f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Space.x05))
                        Text(
                            text = title.ifBlank { "未命名" },
                            style = JkType.title.copy(fontFamily = FontFamily.Serif),
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 右下角的次要信息：有效期、姓名、准驾车型……各类型不同。
                    if (secondary.isNotBlank()) {
                        Spacer(Modifier.width(Space.x1_5))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(secondaryLabel, style = JkType.caption, color = theme.accent, maxLines = 1)
                            Text(
                                text = secondary,
                                style = JkType.cardNumber,
                                color = ink.copy(alpha = 0.92f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 各类型的纹理与标志物。全部用低透明度的线条画在卡面右侧与上半部，
 * 不与左下角的文字争抢；矮卡面（编辑页预览、小号卡面）会省掉体积大的标志物。
 */
private fun DrawScope.drawCardTexture(type: CardType, accent: Color, mini: Boolean) {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    val ink = Color.White
    val roomy = !mini && h > 150f * u
    val hair = Stroke(width = 1.2f * u)

    when (type) {
        CardType.LOGIN -> {
            for (row in 0..4) {
                for (col in 0..9) {
                    drawCircle(
                        color = accent,
                        radius = 2.2f * u,
                        center = Offset(w - 22f * u - col * 15f * u, h * 0.20f + row * 15f * u),
                        alpha = (0.34f - col * 0.034f).coerceAtLeast(0.03f),
                    )
                }
            }
        }

        CardType.BANK -> {
            for (i in 0..20) {
                val x = w * 0.40f + i * 13f * u
                drawLine(color = accent.copy(alpha = 0.07f), start = Offset(x, 0f), end = Offset(x - h * 0.55f, h), strokeWidth = u)
            }
            if (roomy) {
                val left = 20f * u
                val top = h * 0.36f
                val cw = 42f * u
                val ch = 31f * u
                drawRoundRect(color = accent, topLeft = Offset(left, top), size = Size(cw, ch), cornerRadius = CornerRadius(6f * u, 6f * u), alpha = 0.92f)
                val groove = Color.Black.copy(alpha = 0.30f)
                drawLine(color = groove, start = Offset(left, top + ch / 3f), end = Offset(left + cw, top + ch / 3f), strokeWidth = u)
                drawLine(color = groove, start = Offset(left, top + ch * 2f / 3f), end = Offset(left + cw, top + ch * 2f / 3f), strokeWidth = u)
                drawLine(color = groove, start = Offset(left + cw * 0.36f, top), end = Offset(left + cw * 0.36f, top + ch / 3f), strokeWidth = u)
                drawLine(color = groove, start = Offset(left + cw * 0.64f, top + ch * 2f / 3f), end = Offset(left + cw * 0.64f, top + ch), strokeWidth = u)
            }
        }

        CardType.UKEY -> {
            val cx = w * 0.80f
            val cy = h * 0.42f
            for (i in 0..2) {
                drawCircle(color = accent, radius = (13f + i * 9f) * u, center = Offset(cx, cy), alpha = 0.30f - i * 0.08f, style = hair)
            }
            drawCircle(color = accent, radius = 4f * u, center = Offset(cx, cy), alpha = 0.5f)
            // 从触摸环向左引出的三条走线，末端各有一个焊点
            // 虚拟卡没有实体芯片：只描一个芯片轮廓，和银行卡的实心金色芯片区分开。
            if (roomy) {
                val left = 20f * u
                val top = h * 0.36f
                val cw = 42f * u
                val ch = 31f * u
                drawRoundRect(color = accent, topLeft = Offset(left, top), size = Size(cw, ch), cornerRadius = CornerRadius(6f * u, 6f * u), alpha = 0.5f, style = hair)
                drawLine(color = accent.copy(alpha = 0.3f), start = Offset(left, top + ch / 2f), end = Offset(left + cw, top + ch / 2f), strokeWidth = u)
                drawLine(color = accent.copy(alpha = 0.3f), start = Offset(left + cw / 2f, top), end = Offset(left + cw / 2f, top + ch), strokeWidth = u)
            }
            val trace = accent.copy(alpha = 0.16f)
            for (i in 0..2) {
                val y = cy - 18f * u + i * 18f * u
                val bend = cx - (52f + i * 16f) * u
                val end = bend - 34f * u
                drawLine(color = trace, start = Offset(cx - 34f * u, y), end = Offset(bend, y), strokeWidth = 1.2f * u)
                drawLine(color = trace, start = Offset(bend, y), end = Offset(end, y + (i - 1) * 14f * u), strokeWidth = 1.2f * u)
                drawCircle(color = accent, radius = 2.4f * u, center = Offset(end, y + (i - 1) * 14f * u), alpha = 0.3f)
            }
        }

        CardType.ID_CARD -> {
            for (i in 1..14) {
                drawCircle(color = accent, radius = i * 15f * u, center = Offset(w * 0.12f, h * 1.12f), alpha = 0.07f, style = Stroke(width = u))
            }
            if (roomy) {
                val fw = 54f * u
                val fh = 66f * u
                val left = w - 20f * u - fw
                val top = h * 0.20f
                drawRoundRect(color = accent, topLeft = Offset(left, top), size = Size(fw, fh), cornerRadius = CornerRadius(8f * u, 8f * u), alpha = 0.32f, style = hair)
                drawCircle(color = accent, radius = 10f * u, center = Offset(left + fw / 2f, top + fh * 0.36f), alpha = 0.3f, style = hair)
                drawArc(
                    color = accent,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(left + fw * 0.2f, top + fh * 0.62f),
                    size = Size(fw * 0.6f, fh * 0.5f),
                    alpha = 0.3f,
                    style = hair,
                )
            }
        }

        CardType.PASSPORT -> {
            val r = h * 0.27f
            val cx = w * 0.79f
            val cy = h * 0.42f
            drawCircle(color = accent, radius = r, center = Offset(cx, cy), alpha = 0.34f, style = hair)
            drawOval(color = accent, topLeft = Offset(cx - r * 0.5f, cy - r), size = Size(r, r * 2f), alpha = 0.26f, style = hair)
            drawLine(color = accent.copy(alpha = 0.26f), start = Offset(cx, cy - r), end = Offset(cx, cy + r), strokeWidth = 1.2f * u)
            drawLine(color = accent.copy(alpha = 0.26f), start = Offset(cx - r, cy), end = Offset(cx + r, cy), strokeWidth = 1.2f * u)
            val half = r * 0.87f
            drawLine(color = accent.copy(alpha = 0.2f), start = Offset(cx - half, cy - r * 0.5f), end = Offset(cx + half, cy - r * 0.5f), strokeWidth = 1.2f * u)
            drawLine(color = accent.copy(alpha = 0.2f), start = Offset(cx - half, cy + r * 0.5f), end = Offset(cx + half, cy + r * 0.5f), strokeWidth = 1.2f * u)
        }

        CardType.SOCIAL -> {
            val step = 24f * u
            val arm = 4f * u
            var row = 0
            var y = h * 0.14f
            while (y < h * 0.92f) {
                var col = 0
                var x = w - 20f * u
                while (x > w * 0.42f) {
                    val alpha = (0.26f - col * 0.035f).coerceAtLeast(0.03f)
                    val cross = accent.copy(alpha = alpha)
                    val ox = if (row % 2 == 0) 0f else step / 2f
                    drawLine(color = cross, start = Offset(x - ox - arm, y), end = Offset(x - ox + arm, y), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                    drawLine(color = cross, start = Offset(x - ox, y - arm), end = Offset(x - ox, y + arm), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                    x -= step
                    col++
                }
                y += step
                row++
            }
        }

        CardType.DRIVER -> {
            val vx = w * 0.80f
            val vy = h * 0.14f
            val edge = ink.copy(alpha = 0.16f)
            drawLine(color = edge, start = Offset(w * 0.46f, h), end = Offset(vx, vy), strokeWidth = 1.6f * u)
            drawLine(color = edge, start = Offset(w * 1.04f, h), end = Offset(vx, vy), strokeWidth = 1.6f * u)
            // 中线：越远越短的五段虚线
            val bx = w * 0.75f
            for (i in 0..4) {
                val t0 = i / 5f + 0.03f
                val t1 = t0 + 0.11f
                drawLine(
                    color = accent.copy(alpha = 0.55f - i * 0.08f),
                    start = Offset(bx + (vx - bx) * t0, h + (vy - h) * t0),
                    end = Offset(bx + (vx - bx) * t1, h + (vy - h) * t1),
                    strokeWidth = (3.2f - i * 0.5f) * u,
                    cap = StrokeCap.Round,
                )
            }
        }

        CardType.MEMBER -> {
            for (i in 0..3) {
                val x = w * 0.58f + i * 38f * u
                drawLine(
                    color = accent.copy(alpha = 0.07f + i * 0.025f),
                    start = Offset(x, -10f * u),
                    end = Offset(x - h * 0.5f, h + 10f * u),
                    strokeWidth = 15f * u,
                )
            }
            val sx = w - 34f * u
            val sy = h * 0.24f
            val spark = accent.copy(alpha = 0.7f)
            drawLine(color = spark, start = Offset(sx - 9f * u, sy), end = Offset(sx + 9f * u, sy), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            drawLine(color = spark, start = Offset(sx, sy - 9f * u), end = Offset(sx, sy + 9f * u), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            drawLine(color = spark, start = Offset(sx - 4f * u, sy - 4f * u), end = Offset(sx + 4f * u, sy + 4f * u), strokeWidth = u, cap = StrokeCap.Round)
            drawLine(color = spark, start = Offset(sx - 4f * u, sy + 4f * u), end = Offset(sx + 4f * u, sy - 4f * u), strokeWidth = u, cap = StrokeCap.Round)
        }

        CardType.ACCESS -> {
            val cx = w * 0.72f
            val cy = h * 0.42f
            drawCircle(color = accent, radius = 3.4f * u, center = Offset(cx, cy), alpha = 0.6f)
            for (i in 0..3) {
                val r = (13f + i * 11f) * u
                drawArc(
                    color = accent,
                    startAngle = -48f,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f),
                    alpha = 0.42f - i * 0.09f,
                    style = Stroke(width = 2.4f * u, cap = StrokeCap.Round),
                )
            }
        }

        CardType.WIFI -> {
            val cx = w * 0.80f
            val cy = h * 0.66f
            drawCircle(color = accent, radius = 3.6f * u, center = Offset(cx, cy), alpha = 0.7f)
            for (i in 0..4) {
                val r = (15f + i * 14f) * u
                drawArc(
                    color = accent,
                    startAngle = 225f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2f, r * 2f),
                    alpha = 0.46f - i * 0.085f,
                    style = Stroke(width = 2.8f * u, cap = StrokeCap.Round),
                )
            }
        }

        CardType.CUSTOM -> {
            var x = w - 18f * u
            var index = 0
            while (x > w * 0.46f) {
                drawLine(color = ink.copy(alpha = (0.11f - index * 0.008f).coerceAtLeast(0.02f)), start = Offset(x, 0f), end = Offset(x, h), strokeWidth = u)
                x -= 17f * u
                index++
            }
            drawLine(color = accent.copy(alpha = 0.55f), start = Offset(w * 0.46f, h * 0.30f), end = Offset(w, h * 0.30f), strokeWidth = 1.2f * u)
        }
    }
}

/** 到期提醒：有效期在 90 天内或已过期时显示一行小字，其余情况什么都不画。 */
@Composable
fun ExpiryNote(card: Card, modifier: Modifier = Modifier) {
    val days = remember(card) { Expiry.daysLeft(card, java.time.LocalDate.now()) } ?: return
    if (days > Expiry.WARN_DAYS) return
    Text(
        text = when {
            days < 0 -> "已过期"
            days == 0L -> "今天到期"
            else -> "$days 天后到期"
        },
        style = JkType.caption,
        color = if (days < 0) Jk.colors.danger else Jk.colors.accent,
        maxLines = 1,
        modifier = modifier,
    )
}
