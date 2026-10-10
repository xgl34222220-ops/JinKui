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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.xiguli.jinkui.data.repo.ThemeMode

/**
 * 金匮的设计令牌（1.3 薄荷 · 液态玻璃）。
 *
 * 1.3 起页面底是自绘的薄荷 / 浅水绿渐变 + 大块模糊光斑 + 淡淡的植物剪影（[jkBackdrop]），
 * 卡片、底栏、面板都是磨砂玻璃：白色半透明填充、左上更亮的 1px 高光描边、柔和的宽阴影、轻微的内光泽。
 * [surface] / [surfaceAlt] / [border] 因此是半透明色；需要不透明底的地方用 [surfaceSolid]。
 * 文字对比度按“玻璃叠在最深一处背景上”的颜色核算，满足 WCAG AA（≥ 4.5:1）。
 *
 * 以下为 1.1 / 1.2 沿用的说明：
 *
 *  - 品牌色是薄荷绿：primary 用于大块填充（主按钮、选中日期、统计横幅、底栏选中），白字只放粗体大字；
 *    accent 是深一档的薄荷，用于浅底上的小字与线条图标（对比度 ≥ 4.5:1）；accentSoft / hero 是浅薄荷底。
 *  - 中性色是带一点绿意的浅灰：页面底 #F5F6F5、卡片纯白、无描边；深色模式是深绿灰。
 *  - 插画色（leaf / leafDeep / hill / flower）只给自绘的叶子、远山与小花用。
 *  - 字体见 [JkFonts]：默认跟随系统字体；可选 Oxanium 圆润数字或导入的字体。
 *  - 卡片、面板、底栏等“表面”的填充 / 模糊 / 描边集中在 [JkSurfaces]，以后换材质只改这一处。
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
    /** 不透明的卡片底（左滑露出操作时垫在行下面、减少透明度时的卡片底）。 */
    val surfaceSolid: Color = surface,
    /** 不透明的控件底（减少透明度时用）。 */
    val surfaceAltSolid: Color = surfaceAlt,
    /** 页面底的渐变（上 / 中 / 下）与三块光斑。 */
    val backdropTop: Color = bg,
    val backdropMid: Color = bg,
    val backdropBottom: Color = bg,
    val blobLight: Color = Color.White,
    val blobAqua: Color = accentSoft,
    val blobTint: Color = accentSoft,
    /** 玻璃的高光（描边、内光泽）与阴影色。 */
    val glassHighlight: Color = Color.White,
    val glassShadow: Color = barShadow,
    /** 统计图表四色：薄荷、青、珊瑚、沙。 */
    val chartMint: Color = primary,
    val chartTeal: Color = accent,
    val chartCoral: Color = expense,
    val chartSand: Color = flower,
)

val LightColors = JkColors(
    bg = Color(0xFFD6F0E6),
    // 玻璃：白色半透明。叠在最深一处背景（#B8E2D2）上约为 #E4F4EE
    surface = Color(0x8CFFFFFF),
    surfaceAlt = Color(0x5CFFFFFF),
    border = Color(0x2433715C),
    textPrimary = Color(0xFF121816),
    textSecondary = Color(0xFF3F4945),
    // 在背景最深处（#B8E2D2）上 ≥ 4.6:1，在玻璃上 ≥ 5.5:1
    textTertiary = Color(0xFF4F5A55),
    primary = Color(0xFF3AA084),
    onPrimary = Color(0xFFFFFFFF),
    // 浅底上的品牌小字：在玻璃上 ≥ 5:1，在背景最深处上 ≥ 4.5:1
    accent = Color(0xFF1A6450),
    accentSoft = Color(0xD9DDF5EC),
    danger = Color(0xFFA42A34),
    dangerSoft = Color(0xE6FCE8E8),
    success = Color(0xFF1A6450),
    expense = Color(0xFFA42A34),
    expenseSoft = Color(0xE6FCE6E6),
    income = Color(0xFF1A6450),
    incomeSoft = Color(0xD9DDF5EC),
    transfer = Color(0xFF4E5D66),
    transferSoft = Color(0xD9E8EEF0),
    isDark = false,
    hero = Color(0x99E9F7F1),
    leaf = Color(0xFF7CC4AA),
    leafDeep = Color(0xFF3F8B71),
    hill = Color(0x80C9E9DD),
    flower = Color(0xFFE9A3A0),
    bar = Color(0x80FFFFFF),
    barShadow = Color(0xFF1D4A3C),
    iconMuted = Color(0xFF545F5A),
    surfaceSolid = Color(0xFFEEF8F4),
    surfaceAltSolid = Color(0xFFE3F1EB),
    backdropTop = Color(0xFFE6F6EF),
    backdropMid = Color(0xFFCDEDE1),
    backdropBottom = Color(0xFFBFE6D7),
    blobLight = Color(0xFFFFFFFF),
    blobAqua = Color(0xFF9FDCC8),
    blobTint = Color(0xFFD9E4F7),
    glassHighlight = Color(0xFFFFFFFF),
    glassShadow = Color(0xFF1F5E4A),
    chartMint = Color(0xFF5CC6A3),
    chartTeal = Color(0xFF2E8E99),
    chartCoral = Color(0xFFEE8A78),
    chartSand = Color(0xFFDDB585),
)

val DarkColors = JkColors(
    bg = Color(0xFF0D1613),
    // 烟熏玻璃：白色 7–8% 叠在深绿灰上
    surface = Color(0x17FFFFFF),
    surfaceAlt = Color(0x12FFFFFF),
    border = Color(0x1FFFFFFF),
    textPrimary = Color(0xFFE9F1ED),
    textSecondary = Color(0xFFB8C4BF),
    textTertiary = Color(0xFFA3AFA9), // 在玻璃与光斑最亮处上 ≥ 4.5:1
    primary = Color(0xFF5FC4A6),
    onPrimary = Color(0xFF06261C),
    accent = Color(0xFF74D4B6),
    accentSoft = Color(0x4D2F7A63),
    danger = Color(0xFFFF8A84),
    dangerSoft = Color(0x4D7A2F2F),
    success = Color(0xFF74D4B6),
    expense = Color(0xFFFF8A84),
    expenseSoft = Color(0x4D7A3434),
    income = Color(0xFF74D4B6),
    incomeSoft = Color(0x4D2F7A63),
    transfer = Color(0xFFA9B8BF),
    transferSoft = Color(0x33A9B8BF),
    isDark = true,
    hero = Color(0x1A9FE0C8),
    leaf = Color(0xFF3F8C73),
    leafDeep = Color(0xFF2A6553),
    hill = Color(0x4D1F3D35),
    flower = Color(0xFFB9706E),
    bar = Color(0x1FFFFFFF),
    barShadow = Color(0xFF000000),
    iconMuted = Color(0xFFA3AFA9),
    surfaceSolid = Color(0xFF1B2622),
    surfaceAltSolid = Color(0xFF222E2A),
    backdropTop = Color(0xFF13241F),
    backdropMid = Color(0xFF0F1B17),
    backdropBottom = Color(0xFF0B1310),
    blobLight = Color(0xFF2F5E50),
    blobAqua = Color(0xFF1C4C42),
    blobTint = Color(0xFF26364A),
    glassHighlight = Color(0xFFFFFFFF),
    glassShadow = Color(0xFF000000),
    chartMint = Color(0xFF5FC4A6),
    chartTeal = Color(0xFF4FB3BF),
    chartCoral = Color(0xFFF09A8A),
    chartSand = Color(0xFFD9B98E),
)

/** “减少透明度”：玻璃换成不透明填充（光斑与渐变背景保留）。 */
fun JkColors.opaque(): JkColors = copy(
    surface = surfaceSolid,
    surfaceAlt = surfaceAltSolid,
    bar = surfaceSolid,
    hero = surfaceSolid,
    accentSoft = accentSoft.compositeOver(surfaceSolid),
    dangerSoft = dangerSoft.compositeOver(surfaceSolid),
    expenseSoft = expenseSoft.compositeOver(surfaceSolid),
    incomeSoft = incomeSoft.compositeOver(surfaceSolid),
    transferSoft = transferSoft.compositeOver(surfaceSolid),
    border = border.compositeOver(surfaceSolid),
    hill = hill.compositeOver(surfaceSolid),
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
    get() = if (isDark) Color(0x33FFFFFF) else Color(0xE6FFFFFF)

val LocalJkColors = staticCompositionLocalOf { LightColors }

/**
 * 一种“表面”的材质（1.3 液态玻璃）：
 *  - [fill]：半透明的白（深色为烟熏白）；
 *  - [blur]：背后内容的模糊半径。只给悬浮底栏、面板、对话框用（Android 12+ RenderEffect / 窗口模糊），
 *    滚动列表里的卡片一律为 0，用静态磨砂填充，不做实时模糊；
 *  - [highlight]：1px 高光描边的强度，左上最亮、向右下淡出；
 *  - [gloss]：内光泽（顶部一层由白到透明的渐变）的强度；
 *  - [shadow]：柔和宽阴影的强度（只画在形状外面，不会透过玻璃把内部压暗）。
 *
 * 卡片、底部面板、对话框、底栏、控件底、遮罩的材质都只从 [JkSurfaces] 取，由 [surfacesFor] 统一决定：
 * Android 12 以下或打开“减少透明度”时，带模糊的表面换成更不透明的磨砂填充（[fill] 已在这里换好）。
 */
@Immutable
data class JkSurface(
    val fill: Color,
    /** 背后内容的模糊半径；0 表示不模糊。 */
    val blur: Dp = 0.dp,
    val stroke: Color = Color.Transparent,
    val strokeWidth: Dp = 0.dp,
    val highlight: Float = 0f,
    val gloss: Float = 0f,
    val shadow: Float = 0f,
    val highlightColor: Color = Color.White,
    val shadowColor: Color = Color.Black,
)

@Immutable
data class JkSurfaces(
    /** 分组卡片、列表块（静态磨砂，不模糊）。 */
    val card: JkSurface,
    /** 底部面板。 */
    val sheet: JkSurface,
    /** 居中对话框。 */
    val dialog: JkSurface,
    /** 底部悬浮栏。 */
    val bar: JkSurface,
    /** 底栏里选中项那颗凸起的亮玻璃泡。 */
    val bubble: JkSurface,
    /** 浅灰控件底（输入框、分段控件轨道、数字键）。 */
    val control: JkSurface,
    /** 玻璃胶囊（标签、筛选、小按钮）。 */
    val chip: JkSurface,
    /** 选中的玻璃胶囊 / 分段控件里滑动的那颗“药丸”。 */
    val chipSelected: JkSurface,
    /** 面板 / 对话框后面的遮罩。 */
    val scrim: Color,
    /** 当前是否真的做背景模糊（Android 12+ 且没打开“减少透明度”）。 */
    val blurEnabled: Boolean,
)

/** 当前设备是否支持 RenderEffect 背景模糊（Android 12 / API 31 起）。 */
val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 液态玻璃的表面材质。[reduceTransparency] 或系统低于 Android 12 时，模糊表面退回更不透明的磨砂填充。 */
fun surfacesFor(colors: JkColors, reduceTransparency: Boolean = false, blur: Boolean = blurSupported): JkSurfaces {
    val dark = colors.isDark
    val hl = colors.glassHighlight
    val sh = colors.glassShadow
    val useBlur = blur && !reduceTransparency
    fun glass(alphaLight: Float, alphaDark: Float, highlight: Float, gloss: Float, shadow: Float, blurRadius: Dp = 0.dp, fallbackLight: Float = alphaLight, fallbackDark: Float = alphaDark): JkSurface {
        val fill = when {
            reduceTransparency -> colors.surfaceSolid
            blurRadius > 0.dp && !useBlur -> if (dark) colors.surfaceSolid.copy(alpha = fallbackDark) else Color.White.copy(alpha = fallbackLight)
            dark -> Color.White.copy(alpha = alphaDark)
            else -> Color.White.copy(alpha = alphaLight)
        }
        return JkSurface(
            fill = fill,
            blur = if (useBlur) blurRadius else 0.dp,
            highlight = if (reduceTransparency) highlight * 0.5f else highlight,
            gloss = if (reduceTransparency) 0f else gloss,
            shadow = shadow,
            highlightColor = hl,
            shadowColor = sh,
        )
    }
    return JkSurfaces(
        card = glass(0.55f, 0.09f, highlight = if (dark) 0.22f else 0.95f, gloss = if (dark) 0.05f else 0.35f, shadow = if (dark) 0.10f else 0.035f),
        sheet = glass(0.72f, 0.10f, highlight = if (dark) 0.25f else 1f, gloss = if (dark) 0.05f else 0.3f, shadow = 0f, blurRadius = 28.dp, fallbackLight = 0.94f, fallbackDark = 0.97f),
        dialog = glass(0.74f, 0.11f, highlight = if (dark) 0.25f else 1f, gloss = if (dark) 0.05f else 0.3f, shadow = if (dark) 0.12f else 0.05f, blurRadius = 28.dp, fallbackLight = 0.95f, fallbackDark = 0.97f),
        bar = glass(0.50f, 0.10f, highlight = if (dark) 0.28f else 1f, gloss = if (dark) 0.06f else 0.4f, shadow = if (dark) 0.12f else 0.045f, blurRadius = 22.dp, fallbackLight = 0.88f, fallbackDark = 0.94f),
        bubble = glass(0.82f, 0.16f, highlight = if (dark) 0.35f else 1f, gloss = if (dark) 0.08f else 0.5f, shadow = if (dark) 0.14f else 0.06f),
        control = if (reduceTransparency) JkSurface(colors.surfaceAltSolid, highlight = 0.3f, highlightColor = hl) else JkSurface(colors.surfaceAlt, highlight = if (dark) 0.14f else 0.7f, highlightColor = hl),
        chip = glass(0.50f, 0.08f, highlight = if (dark) 0.22f else 0.95f, gloss = if (dark) 0.04f else 0.3f, shadow = if (dark) 0.06f else 0.025f),
        chipSelected = glass(0.88f, 0.18f, highlight = if (dark) 0.35f else 1f, gloss = if (dark) 0.08f else 0.45f, shadow = if (dark) 0.12f else 0.05f),
        scrim = Color.Black.copy(alpha = if (dark) 0.5f else 0.28f),
        blurEnabled = useBlur,
    )
}

val LocalJkSurfaces = staticCompositionLocalOf { surfacesFor(LightColors) }

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
    val card = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val dialog = RoundedCornerShape(28.dp)
    val control = RoundedCornerShape(16.dp)
    val field = RoundedCornerShape(18.dp)
    val chip = RoundedCornerShape(50)
    val tile = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(50)
}

/**
 * 文字样式（1.2 起随字体设置变化）：
 *  - 跟随系统字体（默认）：中文、正文与数字都用系统默认字体族，MIUI / HyperOS 等系统里换的字体直接生效；
 *  - 圆润数字：金额、日期等数字用 Oxanium，其余仍是系统字体；
 *  - 导入的字体：用户自己导入的 .ttf / .otf 用于全部文字（字体里没有的字会自动回落到系统字体）。
 *
 * 每个样式是一个属性读取：读到的是当前 [JkFonts.config] 下的样式，读取发生在组合里时，
 * 换字体会让用到它的界面自动重组。字号一律是 sp，跟随系统字号缩放（含 1.3 倍）。
 */
object JkType {
    private var cachedFor: FontConfig? = null
    private var cached: JkTypeSet? = null

    private val set: JkTypeSet
        get() {
            val config = JkFonts.config
            val hit = cached
            if (hit != null && cachedFor === config) return hit
            return JkTypeSet(config).also {
                cached = it
                cachedFor = config
            }
        }

    /** 品牌字标与卡面标题用宋体（导入字体时用导入的字体），其余界面文字用系统黑体。 */
    val brand: TextStyle get() = set.brand
    val display: TextStyle get() = set.display
    /** 首页分区的大标题（“卡包”“账本”）：粗一档的黑体，比宋体字标更利落。 */
    val pageTitle: TextStyle get() = set.pageTitle
    val title: TextStyle get() = set.title
    val heading: TextStyle get() = set.heading
    val body: TextStyle get() = set.body
    val bodyStrong: TextStyle get() = set.bodyStrong
    val small: TextStyle get() = set.small
    val caption: TextStyle get() = set.caption
    /** 密码明文：等宽，便于分辨 l / 1 / I。不随字体设置变化。 */
    val mono: TextStyle get() = set.mono
    val amountHero: TextStyle get() = set.amountHero
    val amountLarge: TextStyle get() = set.amountLarge
    val amount: TextStyle get() = set.amount
    val amountSmall: TextStyle get() = set.amountSmall
    val amountCaption: TextStyle get() = set.amountCaption
    /** 日期、时间等小号数字。 */
    val date: TextStyle get() = set.date
    /** 卡号、证件号、打码的末四位等“数字串”：数字字体 + 略宽的字距。 */
    val digits: TextStyle get() = set.digits
    val digitsSmall: TextStyle get() = set.digitsSmall
    val digitsLarge: TextStyle get() = set.digitsLarge
    /** 卡面上的卡号 / 有效期：略加粗、字距更宽，像压印在卡上。 */
    val cardNumber: TextStyle get() = set.cardNumber
    /** 分区标题（“收支统计”“信贷账户”）：粗黑体。 */
    val section: TextStyle get() = set.section
    /** 小节标题上方的“眉题”（例如“本月支出”）。 */
    val overline: TextStyle get() = set.overline
}

/** 某一种字体设置下的全部文字样式。 */
class JkTypeSet(config: FontConfig) {
    private val text = config.text
    private val base = TextStyle(fontFamily = text)
    val brand = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, fontFamily = config.brand, letterSpacing = 2.sp)
    val display = base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val pageTitle = base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val title = base.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
    val heading = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val body = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val small = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val mono = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)

    // 金额、日期、卡号：数字字体 + 等宽数字（tnum），上下行对得齐。系统字体多数支持 tnum，不支持时原样显示。
    private val num = TextStyle(fontFamily = config.numeral, fontFeatureSettings = "tnum")
    val amountHero = num.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
    val amountLarge = num.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val amount = num.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val amountSmall = num.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val amountCaption = num.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val date = num.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val digits = num.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
    val digitsSmall = num.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
    val digitsLarge = num.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
    val cardNumber = num.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
    val section = base.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
    val overline = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
}

object Jk {
    val colors: JkColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJkColors.current

    val surfaces: JkSurfaces
        @Composable
        @ReadOnlyComposable
        get() = LocalJkSurfaces.current
}

@Composable
fun JinKuiTheme(mode: ThemeMode, dynamicColor: Boolean = false, reduceTransparency: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val branded = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val system = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.withBrand(system.primary, system.onPrimary, system.primaryContainer.copy(alpha = if (dark) 0.55f else 0.7f))
    } else {
        base
    }
    val colors = if (reduceTransparency) branded.opaque() else branded
    // Material 组件（日期 / 时间选择器等系统样式的弹窗）用不透明的底：玻璃只给金匮自己的表面
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
            surface = colors.surfaceSolid,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceAltSolid,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.surfaceSolid,
            surfaceContainerLow = colors.surfaceSolid,
            surfaceContainer = colors.surfaceSolid,
            surfaceContainerHigh = colors.surfaceSolid,
            surfaceContainerHighest = colors.surfaceAltSolid,
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
            surface = colors.surfaceSolid,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceAltSolid,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.surfaceSolid,
            surfaceContainerLow = colors.surfaceSolid,
            surfaceContainer = colors.surfaceSolid,
            surfaceContainerHigh = colors.surfaceSolid,
            surfaceContainerHighest = colors.surfaceAltSolid,
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

    CompositionLocalProvider(LocalJkColors provides colors, LocalJkSurfaces provides surfacesFor(colors, reduceTransparency)) {
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
