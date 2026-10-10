package com.xiguli.jinkui.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
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
 * 金匮的设计令牌（1.0 重做）。
 *
 *  - 品牌色只有一个：靛青（primary = accent）。按钮、选中、置顶、底部栏中央的“＋”都用它；
 *    其余全是中性灰阶，收入 / 支出 / 转账三色只在账本里出现。
 *  - 层次靠底色区分（bg → surface → surfaceAlt），默认不画描边；border 只留给输入框与分隔线。
 *  - 间距 4dp 网格、常用 8 / 12 / 16 / 20 / 24；页面左右 20dp。
 *  - 圆角：卡片与分组 20dp，控件 14dp，标签与按钮全圆角（[Radius]）。
 *  - 文字：页面大标题 28sp 粗体；正文 15sp；辅助 13 / 12sp。三级文字对比度均 ≥ 4.5:1。
 *  - 可选 Material You 动态取色（设置里开启，默认关闭）：只替换品牌色一组，中性色不变。
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
    /** 账本：支出 / 收入 / 转账的主色与浅底（图标底、图例、柱子）。深浅两套各自调过对比度。 */
    val expense: Color,
    val expenseSoft: Color,
    val income: Color,
    val incomeSoft: Color,
    val transfer: Color,
    val transferSoft: Color,
    val isDark: Boolean,
)

val LightColors = JkColors(
    bg = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDEFF3),
    border = Color(0xFFE2E5EA),
    textPrimary = Color(0xFF15171C),
    textSecondary = Color(0xFF4B515C),
    textTertiary = Color(0xFF646C7B), // 在 bg / surface / surfaceAlt 上对比度 ≥ 4.5（WCAG AA）
    primary = Color(0xFF3E4FC1),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF3E4FC1),
    accentSoft = Color(0xFFE8EAFA),
    danger = Color(0xFFC0352B),
    dangerSoft = Color(0xFFFBE9E7),
    success = Color(0xFF1E7A52),
    expense = Color(0xFFC4532D),
    expenseSoft = Color(0xFFFBEBE4),
    income = Color(0xFF1E7A52),
    incomeSoft = Color(0xFFE2F2EA),
    transfer = Color(0xFF5B6577),
    transferSoft = Color(0xFFE9ECF1),
    isDark = false,
)

val DarkColors = JkColors(
    bg = Color(0xFF0E1014),
    surface = Color(0xFF171A20),
    surfaceAlt = Color(0xFF20242C),
    border = Color(0xFF2A2F38),
    textPrimary = Color(0xFFECEEF2),
    textSecondary = Color(0xFFB3B9C4),
    textTertiary = Color(0xFF8A93A1), // 在 surfaceAlt 上对比度 ≥ 5
    primary = Color(0xFF9AA6FF),
    onPrimary = Color(0xFF0E1233),
    accent = Color(0xFF9AA6FF),
    accentSoft = Color(0xFF22274A),
    danger = Color(0xFFFF8A80),
    dangerSoft = Color(0xFF3A1E1C),
    success = Color(0xFF6FD3A2),
    expense = Color(0xFFF59A7B),
    expenseSoft = Color(0xFF3A2219),
    income = Color(0xFF6FD3A2),
    incomeSoft = Color(0xFF15302A),
    transfer = Color(0xFFA9B3C4),
    transferSoft = Color(0xFF222832),
    isDark = true,
)

/**
 * 动态取色：只拿系统配色里的 primary 一组替换品牌色，背景与文字仍用金匮自己的中性色，
 * 这样换壁纸不会把账本的收支色、卡面配色带偏。
 */
fun JkColors.withBrand(primary: Color, onPrimary: Color, soft: Color): JkColors =
    copy(primary = primary, onPrimary = onPrimary, accent = primary, accentSoft = soft)

/**
 * 分段控件里选中那一段的底色：浅色用 surface（白）；深色下 surface 比轨道（surfaceAlt）还暗，
 * 选中反而像“凹下去”，所以深色单独提亮一档。
 */
val JkColors.segmentSelected: Color
    get() = if (isDark) Color(0xFF323844) else surface

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

/** 统一的尺寸：触控区、图标、图标底。 */
object JkSize {
    /** 最小触控区（Material 无障碍要求）。 */
    val touch = 48.dp
    val iconSmall = 16.dp
    val icon = 20.dp
    val iconLarge = 22.dp
    /** 列表行左侧的图标底。 */
    val chip = 40.dp
}

object Radius {
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val control = RoundedCornerShape(14.dp)
    val field = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(50)
    val tile = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(50)
}

object JkType {
    /** 品牌字标与卡面标题用宋体，其余界面文字用系统黑体。 */
    val brand = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, letterSpacing = 2.sp)
    val display = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    /** 首页分区的大标题（“卡包”“账本”）：粗一档的黑体，比宋体字标更利落。 */
    val pageTitle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val title = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val heading = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val mono = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)

    // 金额一律用等宽数字（tnum），上下行的小数点和位数对得齐，数字滚动时也不会左右抖。
    private const val TABULAR = "tnum"
    val amountHero = display.copy(fontSize = 34.sp, lineHeight = 42.sp, fontFeatureSettings = TABULAR, letterSpacing = (-0.6).sp)
    val amountLarge = display.copy(fontFeatureSettings = TABULAR)
    val amount = bodyStrong.copy(fontFeatureSettings = TABULAR)
    val amountSmall = small.copy(fontFeatureSettings = TABULAR)
    val amountCaption = caption.copy(fontFeatureSettings = TABULAR)
    /**
     * 卡号、证件号、打码的末四位等“数字串”：系统字体 + 等宽数字（tnum）+ 略宽的字距，
     * 上下行的数字对得齐，又不像等宽字体那样生硬。密码明文仍用 [mono]，便于分辨 l / 1 / I。
     */
    val digits = body.copy(fontFeatureSettings = TABULAR, letterSpacing = 0.8.sp)
    val digitsSmall = small.copy(fontFeatureSettings = TABULAR, letterSpacing = 0.6.sp)
    val digitsLarge = title.copy(fontFeatureSettings = TABULAR, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
    /** 卡面上的卡号 / 有效期：略加粗、字距更宽，像压印在卡上。 */
    val cardNumber = small.copy(fontFeatureSettings = TABULAR, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
    /** 小节标题上方的“眉题”（例如“本月支出”）。 */
    val overline = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
}

object Jk {
    val colors: JkColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJkColors.current
}

@Composable
fun JinKuiTheme(mode: ThemeMode, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val system = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.withBrand(system.primary, system.onPrimary, system.primaryContainer.copy(alpha = if (dark) 0.55f else 0.7f))
    } else {
        base
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onPrimary,
            secondary = colors.accent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.textPrimary,
            secondaryContainer = colors.accentSoft,
            onSecondaryContainer = colors.textPrimary,
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
            onPrimary = colors.onPrimary,
            secondary = colors.accent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.textPrimary,
            secondaryContainer = colors.accentSoft,
            onSecondaryContainer = colors.textPrimary,
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
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
