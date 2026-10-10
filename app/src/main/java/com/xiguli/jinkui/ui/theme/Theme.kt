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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.xiguli.jinkui.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.xiguli.jinkui.data.repo.ThemeMode

/**
 * 金匮的设计令牌（1.1 薄荷）。
 *
 *  - 品牌色是薄荷绿：primary 用于大块填充（主按钮、选中日期、统计横幅、底栏选中），白字只放粗体大字；
 *    accent 是深一档的薄荷，用于浅底上的小字与线条图标（对比度 ≥ 4.5:1）；accentSoft / hero 是浅薄荷底。
 *  - 中性色是带一点绿意的浅灰：页面底 #F5F6F5、卡片纯白、无描边；深色模式是深绿灰。
 *  - 插画色（leaf / leafDeep / hill / flower）只给自绘的叶子、远山与小花用。
 *  - 数字与日期用 Oxanium（OFL，圆角方正的“数码”数字，见 [JkFonts]），中文仍是系统黑体。
 *  - 间距 4dp 网格；页面左右 16dp；卡片 18dp 圆角、内边距 16–20dp。
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
    /** 带插画的头图卡底色（浅薄荷）。 */
    val hero: Color = accentSoft,
    /** 插画：浅叶、深叶、远山、小花。 */
    val leaf: Color = accent,
    val leafDeep: Color = accent,
    val hill: Color = accentSoft,
    val flower: Color = expense,
    /** 底部悬浮栏：底色与选中图标里的浅薄荷填充。 */
    val bar: Color = surface,
    val barShadow: Color = Color.Black,
    /** 未选中的图标（中灰）。 */
    val iconMuted: Color = textTertiary,
)

val LightColors = JkColors(
    bg = Color(0xFFF5F6F5),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEEF1F0),
    border = Color(0xFFE3E7E5),
    textPrimary = Color(0xFF161A18),
    textSecondary = Color(0xFF4A524E),
    textTertiary = Color(0xFF66706B), // 在 bg / surface / surfaceAlt 上对比度 ≥ 4.5（WCAG AA）
    primary = Color(0xFF3AA084),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF23785F),
    accentSoft = Color(0xFFDFF1EA),
    danger = Color(0xFFC8363F),
    dangerSoft = Color(0xFFFBE8E8),
    success = Color(0xFF23785F),
    expense = Color(0xFFD1404A),
    expenseSoft = Color(0xFFFCE6E6),
    income = Color(0xFF23785F),
    incomeSoft = Color(0xFFDFF1EA),
    transfer = Color(0xFF5B6A73),
    transferSoft = Color(0xFFE8EDEF),
    isDark = false,
    hero = Color(0xFFE2F2EE),
    leaf = Color(0xFF6DBA9E),
    leafDeep = Color(0xFF2F7D63),
    hill = Color(0xFFC9E6DD),
    flower = Color(0xFFE9A3A0),
    bar = Color(0xFFFFFFFF),
    barShadow = Color(0xFF1D3B31),
    iconMuted = Color(0xFF7D8782),
)

val DarkColors = JkColors(
    bg = Color(0xFF0F1513),
    surface = Color(0xFF18201D),
    surfaceAlt = Color(0xFF222B28),
    border = Color(0xFF2C3632),
    textPrimary = Color(0xFFE7EEEA),
    textSecondary = Color(0xFFB2BDB8),
    textTertiary = Color(0xFF8C9893), // 在 surfaceAlt 上对比度 ≥ 4.5
    primary = Color(0xFF5FC4A6),
    onPrimary = Color(0xFF06261C),
    accent = Color(0xFF6FD0B2),
    accentSoft = Color(0xFF1D3A31),
    danger = Color(0xFFFF8A84),
    dangerSoft = Color(0xFF3A1F1F),
    success = Color(0xFF6FD0B2),
    expense = Color(0xFFFF8A84),
    expenseSoft = Color(0xFF3A2222),
    income = Color(0xFF6FD0B2),
    incomeSoft = Color(0xFF173329),
    transfer = Color(0xFFA9B8BF),
    transferSoft = Color(0xFF212A2D),
    isDark = true,
    hero = Color(0xFF17302A),
    leaf = Color(0xFF3F8C73),
    leafDeep = Color(0xFF2A6553),
    hill = Color(0xFF1F3D35),
    flower = Color(0xFFB9706E),
    bar = Color(0xFF1E2724),
    barShadow = Color(0xFF000000),
    iconMuted = Color(0xFF8C9893),
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
    get() = if (isDark) Color(0xFF34403B) else surface

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
    val card = RoundedCornerShape(18.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val control = RoundedCornerShape(14.dp)
    val field = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(50)
    val tile = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(50)
}

/** 数字字体：Oxanium（SIL OFL 1.1，见仓库 licenses/Oxanium-OFL.txt）。只放了拉丁字符，中文自动回落到系统字体。 */
object JkFonts {
    val numeral = FontFamily(
        Font(R.font.oxanium_500, FontWeight.Normal),
        Font(R.font.oxanium_500, FontWeight.Medium),
        Font(R.font.oxanium_600, FontWeight.SemiBold),
        Font(R.font.oxanium_700, FontWeight.Bold),
    )
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

    // 金额、日期、卡号一律用 Oxanium：圆角方正的数码数字，本身就是等宽的（tnum 留着无害），上下行对得齐。
    private const val TABULAR = "tnum"
    private val num = TextStyle(fontFamily = JkFonts.numeral, fontFeatureSettings = TABULAR)
    val amountHero = num.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
    val amountLarge = num.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val amount = num.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val amountSmall = num.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val amountCaption = num.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    /** 日期、时间等小号数字。 */
    val date = num.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    /**
     * 卡号、证件号、打码的末四位等“数字串”：Oxanium + 略宽的字距。密码明文仍用 [mono]，便于分辨 l / 1 / I。
     */
    val digits = num.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
    val digitsSmall = num.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
    val digitsLarge = num.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
    /** 卡面上的卡号 / 有效期：略加粗、字距更宽，像压印在卡上。 */
    val cardNumber = num.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
    /** 分区标题（“收支统计”“信贷账户”）：粗黑体。 */
    val section = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
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
