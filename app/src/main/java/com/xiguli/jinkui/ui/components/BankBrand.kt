package com.xiguli.jinkui.ui.components

import androidx.compose.ui.graphics.Color
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardPalette
import com.xiguli.jinkui.data.model.CardType

/**
 * 银行卡的品牌色：从卡片名称或“开户行”里认出常见银行，卡面底色带上该行的主色（仍是同一套斜纹与金色芯片）。
 * 只在“默认卡面”时生效；用户另选了颜色就按用户的来。纯本地的关键字匹配，不联网、不含任何标志图形。
 */
object BankBrand {
    // 顺序有意义：先匹配更长、更具体的名称（“中国工商银行”要先于“中国银行”）
    private val BRANDS: List<Pair<List<String>, Long>> = listOf(
        listOf("工商", "工行", "ICBC") to 0xFFC7000B,
        listOf("建设银行", "建行", "CCB") to 0xFF0A5CA8,
        listOf("农业银行", "农行", "ABC") to 0xFF00866B,
        listOf("邮储", "邮政储蓄", "PSBC") to 0xFF0B7A3B,
        listOf("交通银行", "交行", "BOCOM") to 0xFF1E2F87,
        listOf("招商", "招行", "CMB") to 0xFFC4162B,
        listOf("浦发", "SPDB") to 0xFF1A4C9B,
        listOf("中信", "CITIC") to 0xFFD8141E,
        listOf("光大", "CEB") to 0xFF5F2B86,
        listOf("民生", "CMBC") to 0xFF1F86C4,
        listOf("兴业", "CIB") to 0xFF0B4A94,
        listOf("平安") to 0xFFE85A1E,
        listOf("华夏") to 0xFFCF1A1F,
        listOf("广发") to 0xFFC81E2B,
        listOf("北京银行") to 0xFFD71D3A,
        listOf("上海银行") to 0xFF1B4E9C,
        listOf("中国银行", "中行", "BOC") to 0xFFB31B20,
    )

    /** 认出的银行主色；认不出时为 null。 */
    fun colorOf(text: String): Color? {
        if (text.isBlank()) return null
        val upper = text.uppercase()
        for ((keys, argb) in BRANDS) {
            if (keys.any { key -> if (key.all { it in 'A'..'Z' }) Regex("(^|[^A-Z])$key([^A-Z]|$)").containsMatchIn(upper) else text.contains(key) }) {
                return Color(argb)
            }
        }
        return null
    }

    /** 这张卡片的品牌色：只看银行卡 / U 卡的名称与“开户行 / 发卡机构”，且只在默认卡面时。 */
    fun of(card: Card): Color? = of(card.type, card.color, card.title, card.fields.firstOrNull { it.label == "开户行" || it.label == "发卡机构" }?.value.orEmpty())

    fun of(type: CardType, colorIndex: Int, title: String, issuer: String): Color? {
        if (colorIndex != CardPalette.AUTO || type != CardType.BANK) return null
        return colorOf(issuer) ?: colorOf(title)
    }
}
