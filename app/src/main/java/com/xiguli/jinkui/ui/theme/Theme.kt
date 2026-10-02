package com.xiguli.jinkui.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.xiguli.jinkui.data.repo.ThemeMode

/**
 * 金匮的设计令牌。
 *
 *  - 间距：统一 8dp 节奏（[Space]）。
 *  - 文字：标题亮 / 正文适中 / 辅助灰 三级（textPrimary / textSecondary / textTertiary）。
 *  - 层次：用 1dp 极淡描边（border）区分，不用投影。
 *  - 圆角：卡片与面板 24dp，控件 14dp（[Radius]）。
 *  - 配色：浅色暖纸白，深色暖墨黑；赤金只作点缀（置顶、选中、品牌）。
 */
@Immutable
data class JkColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val isDark: Boolean,
)

val LightColors = JkColors(
    bg = Color(0xFFF7F6F3),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF0EEE9),
    border = Color(0xFFE6E3DC),
    textPrimary = Color(0xFF1A1815),
    textSecondary = Color(0xFF5A554D),
    textTertiary = Color(0xFF8C867B),
    primary = Color(0xFF1A1815),
    onPrimary = Color(0xFFF7F6F3),
    accent = Color(0xFF946616),
    accentSoft = Color(0xFFF3EBDA),
    danger = Color(0xFFB3261E),
    dangerSoft = Color(0xFFF9E7E5),
    success = Color(0xFF2B6B4F),
    isDark = false,
)

val DarkColors = JkColors(
    bg = Color(0xFF141210),
    surface = Color(0xFF1C1A17),
    surfaceAlt = Color(0xFF25221E),
    border = Color(0xFF2F2B26),
    textPrimary = Color(0xFFF2EEE7),
    textSecondary = Color(0xFFB8B1A6),
    textTertiary = Color(0xFF857E73),
    primary = Color(0xFFF2EEE7),
    onPrimary = Color(0xFF141210),
    accent = Color(0xFFD4A857),
    accentSoft = Color(0xFF2E2718),
    danger = Color(0xFFF2857A),
    dangerSoft = Color(0xFF34201D),
    success = Color(0xFF7FC9A4),
    isDark = true,
)

val LocalJkColors = staticCompositionLocalOf { LightColors }

object Space {
    val x05 = 4.dp
    val x1 = 8.dp
    val x1_5 = 12.dp
    val x2 = 16.dp
    val x2_5 = 20.dp
    val x3 = 24.dp
    val x4 = 32.dp
    val x5 = 40.dp
    val x6 = 48.dp
}

object Radius {
    val card = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val control = RoundedCornerShape(14.dp)
    val field = RoundedCornerShape(20.dp)
    val chip = RoundedCornerShape(10.dp)
    val tile = RoundedCornerShape(12.dp)
}

object JkType {
    /** 品牌字标与卡面标题用宋体，其余界面文字用系统黑体。 */
    val brand = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, letterSpacing = 2.sp)
    val display = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val title = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val heading = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val mono = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
}

object Jk {
    val colors: JkColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJkColors.current
}

@Composable
fun JinKuiTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.bg,
            secondary = colors.accent,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.bg,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surfaceAlt,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.surface,
            secondary = colors.accent,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.surface,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            surfaceContainerHighest = colors.surfaceAlt,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.danger,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalJkColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                titleLarge = JkType.title,
                titleMedium = JkType.heading,
                bodyLarge = JkType.body,
                bodyMedium = JkType.body,
                bodySmall = JkType.small,
                labelLarge = JkType.bodyStrong,
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
