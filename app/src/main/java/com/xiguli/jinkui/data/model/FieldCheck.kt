package com.xiguli.jinkui.data.model

/**
 * 编辑页字段的即时提示（纯函数，便于测试）。只做“看起来不对”的软提示，不阻止保存——
 * 卡号里带空格、日期写成“长期”都是真实存在的写法。两步验证码的校验另见 Totp.errorOf，那个会阻止保存。
 */
object FieldCheck {
    private val DIGITS_AND_SEPARATORS = Regex("[0-9\\s\\-]+")
    private val PHONE = Regex("[0-9+\\-\\s()]+")

    fun issue(kind: FieldKind, value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return when (kind) {
            FieldKind.NUMBER -> if (!DIGITS_AND_SEPARATORS.matches(text)) "这里通常只有数字（可以带空格或短横线）" else null
            FieldKind.PHONE -> when {
                !PHONE.matches(text) -> "电话号码里有不像号码的字符"
                text.count { it.isDigit() } < 3 -> "号码似乎太短"
                else -> null
            }
            FieldKind.URL -> when {
                text.contains(' ') -> "网址里不应有空格"
                !text.contains('.') && !text.startsWith("http", ignoreCase = true) -> "看起来不像网址，例如 example.com"
                else -> null
            }
            FieldKind.DATE -> if (text.none { it.isDigit() } && text != "长期") "日期里没有数字，例如 08/29 或 2030-05-20" else null
            else -> null
        }
    }

    /** 标题的校验：必填。 */
    fun titleIssue(title: String): String? = if (title.isBlank()) "请填写名称，例如“招商银行储蓄卡”" else null
}
