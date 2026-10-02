package com.xiguli.jinkui.ui.components

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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Key
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
        CardType.UKEY -> Icons.Outlined.Key
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
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Jk.colors.surface)
            .border(1.dp, Jk.colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mark),
            contentDescription = null,
            tint = Jk.colors.accent,
            modifier = Modifier.size(size * 0.52f),
        )
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

/** 附件缩略图 / 大图：[load] 在后台解密并返回图片字节。 */
@Composable
fun SecureImage(
    key: String,
    maxEdge: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    load: suspend () -> ByteArray,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key) {
        value = withContext(Dispatchers.Default) {
            runCatching { decodeSampled(load(), maxEdge) }.getOrNull()
        }
    }
    Box(modifier.background(Jk.colors.surfaceAlt), contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(bitmap = current, contentDescription = "附件照片", contentScale = contentScale, modifier = Modifier.matchParentSize())
        }
    }
}
