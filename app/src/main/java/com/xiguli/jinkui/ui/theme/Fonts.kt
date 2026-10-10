package com.xiguli.jinkui.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.xiguli.jinkui.R
import com.xiguli.jinkui.data.repo.FontMode

/**
 * 一种字体设置：正文用什么字体族、数字用什么字体族、品牌字标用什么字体族。
 *
 * [FontFamily.Default] 就是系统默认字体，所以“跟随系统字体”时 MIUI / HyperOS / ColorOS 等系统里
 * 用户换的字体会直接生效（数字也一样）。
 */
class FontConfig(
    val mode: FontMode,
    val text: FontFamily,
    val numeral: FontFamily,
    val brand: FontFamily,
) {
    companion object {
        /** Oxanium（SIL OFL 1.1，见仓库 licenses/Oxanium-OFL.txt）。只有拉丁字符，中文自动回落到系统字体。 */
        val Oxanium: FontFamily = FontFamily(
            Font(R.font.oxanium_500, FontWeight.Normal),
            Font(R.font.oxanium_500, FontWeight.Medium),
            Font(R.font.oxanium_600, FontWeight.SemiBold),
            Font(R.font.oxanium_700, FontWeight.Bold),
        )

        /** 默认：全部文字（含数字）跟随系统字体。 */
        val System = FontConfig(FontMode.SYSTEM, FontFamily.Default, FontFamily.Default, FontFamily.Serif)

        /** 圆润数字：金额、日期、卡号用 Oxanium，其余跟随系统。 */
        val Rounded = FontConfig(FontMode.ROUNDED, FontFamily.Default, Oxanium, FontFamily.Serif)

        /** 导入的字体：全部文字都用它；粗体由系统按需合成，字体里没有的字回落到系统字体。 */
        fun custom(typeface: android.graphics.Typeface): FontConfig {
            val family = FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
            return FontConfig(FontMode.CUSTOM, family, family, family)
        }
    }
}

/**
 * 当前生效的字体。由 AppContainer 按设置写入（不在组合里写），[JkType] 的每个样式都从这里读，
 * 所以改了字体设置，整个界面立即换字体，不用重启。
 */
object JkFonts {
    var config: FontConfig by mutableStateOf(FontConfig.System)

    /** 当前的数字字体（“跟随系统”时就是系统字体）。 */
    val numeral: FontFamily get() = config.numeral

    /** 当前的正文字体。 */
    val text: FontFamily get() = config.text
}
