package com.xiguli.jinkui.ui.components

import com.xiguli.jinkui.ui.theme.jkSurface

import androidx.compose.ui.semantics.clearAndSetSemantics

import androidx.compose.foundation.layout.requiredSize

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Loyalty
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.R
import com.xiguli.jinkui.data.model.CardPalette
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CardType.icon: ImageVector
    get() = when (this) {
        CardType.LOGIN -> Icons.Outlined.Password
        CardType.BANK -> Icons.Outlined.AccountBalance
        CardType.UKEY -> Icons.Outlined.CreditCard
        CardType.ID_CARD -> Icons.Outlined.Badge
        CardType.PASSPORT -> Icons.Outlined.Flight
        CardType.SOCIAL -> Icons.Outlined.HealthAndSafety
        CardType.DRIVER -> Icons.Outlined.DirectionsCar
        CardType.MEMBER -> Icons.Outlined.Loyalty
        CardType.ACCESS -> Icons.Outlined.MeetingRoom
        CardType.WIFI -> Icons.Outlined.Wifi
        CardType.CUSTOM -> Icons.Outlined.Category
    }

fun cardColor(index: Int): Color = Color(CardPalette.argb(index))

/**
 * 列表里的小图标块：卡片颜色的淡底 + 同色图标。
 * 深色主题下把颜色提亮，保证在暖墨黑底上依然看得清。
 */
@Composable
fun CardTile(type: CardType, colorIndex: Int, size: Dp = 40.dp) {
    val base = cardTheme(type, colorIndex).start
    val dark = Jk.colors.isDark
    val foreground = if (dark) lerp(base, Color.White, 0.55f) else base
    val background = if (dark) lerp(base, Color.White, 0.3f).copy(alpha = 0.2f) else base.copy(alpha = 0.12f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type.icon, contentDescription = null, tint = foreground, modifier = Modifier.size(size * 0.5f))
    }
}

/** 品牌标记：描边方印里一只带锁孔的匣子。 */
@Composable
fun BrandMark(size: Dp = 64.dp) {
    // 1.3：与桌面图标同一枚标志（方案 B：薄荷渐变上两张玻璃卡片与小叶子），外面一圈玻璃高光与柔和阴影
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = Modifier
            .size(size)
            .jkSurface(Jk.surfaces.bubble, shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // 自适应图标的 108dp 画布里只有中间 72dp 可见：放大 1.5 倍后裁到形状里
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}

/** 把图片字节解码为不超过 [maxEdge] 的位图；失败返回 null。 */
fun decodeSampled(bytes: ByteArray, maxEdge: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

private object ImageLoadFailed

/** 附件缩略图 / 大图：[load] 在后台解密并返回图片字节。 */
@Composable
fun SecureImage(
    key: String,
    maxEdge: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    load: suspend () -> ByteArray,
) {
    // null：读取中；Failed：解密或解码失败；否则为位图。
    val result by produceState<Any?>(initialValue = null, key) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = load()
                try {
                    decodeSampled(bytes, maxEdge)
                } finally {
                    // 解码完就清掉明文图片字节，不在堆里多留一份。
                    bytes.fill(0)
                }
            }.getOrNull() ?: ImageLoadFailed
        }
    }
    Box(modifier.background(Jk.colors.surfaceAlt), contentAlignment = Alignment.Center) {
        when (val current = result) {
            is ImageBitmap -> Image(
                bitmap = current,
                contentDescription = "附件照片",
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
            ImageLoadFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.BrokenImage,
                    contentDescription = null,
                    tint = Jk.colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Text("无法读取", style = JkType.caption, color = Jk.colors.textTertiary)
            }
            else -> Unit
        }
    }
}
