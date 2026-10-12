package com.xiguli.jinkui.data.ledger

/**
 * 金额工具。账本里所有金额都是以“分”为单位的 Long，从不经过浮点数，
 * 0.1 + 0.2 这种误差不会出现在账上。
 */
object Money {
    /** 单笔金额上限：9,999,999,999.99 元。 */
    const val MAX_CENTS = 999_999_999_999L

    /**
     * 把用户输入的金额（"12"、"12.5"、"12.34"、"1,234.56"、"-3"）解析成分。
     * 小数超过两位、含有其他字符或超出上限时返回 null。
     */
    fun parse(text: String): Long? {
        var s = text.trim().replace(",", "").replace("，", "").replace("¥", "").replace("￥", "").trim()
        if (s.isEmpty()) return null
        // 只敲了一个小数点（数字键盘上很容易）当成 0，不报“格式不对”
        if (s == "." || s == "。") return 0L
        var negative = false
        if (s[0] == '-' || s[0] == '−') {
            negative = true
            s = s.substring(1)
        } else if (s[0] == '+') {
            s = s.substring(1)
        }
        if (s.isEmpty() || s == ".") return null
        val dot = s.indexOf('.')
        val whole = if (dot < 0) s else s.substring(0, dot)
        val fraction = if (dot < 0) "" else s.substring(dot + 1)
        if (!whole.all(Char::isDigit) || !fraction.all(Char::isDigit) || fraction.length > 2) return null
        if (whole.length > 12) return null
        val yuan = if (whole.isEmpty()) 0L else whole.toLong()
        val cents = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        val total = yuan * 100 + cents
        if (total > MAX_CENTS) return null
        return if (negative) -total else total
    }

    /**
     * 计算键盘上的简单加减算式（"12.5+3-0.5"）。算式不完整时（结尾是运算符）忽略结尾；
     * 任何一段不是合法金额、或结果超出上限时返回 null。结果可能是负数，由调用方决定是否接受。
     */
    fun evaluate(expression: String): Long? {
        val text = expression.trim().replace('−', '-').trimEnd('+', '-')
        if (text.isEmpty()) return null
        var total = 0L
        var sign = 1L
        val current = StringBuilder()
        fun flush(): Boolean {
            if (current.isEmpty()) return false
            val value = parse(current.toString()) ?: return false
            total += sign * value
            current.clear()
            return true
        }
        for ((index, ch) in text.withIndex()) {
            if (ch == '+' || ch == '-') {
                if (index == 0 && ch == '-') {
                    sign = -1L
                    continue
                }
                if (!flush()) return null
                sign = if (ch == '+') 1L else -1L
            } else {
                current.append(ch)
            }
        }
        if (!flush()) return null
        if (kotlin.math.abs(total) > MAX_CENTS) return null
        return total
    }

    /** "¥1,234.56"；负数为 "-¥1,234.56"。[signed] 为 true 时正数前加 "+"。 */
    fun format(cents: Long, symbol: Boolean = true, signed: Boolean = false): String {
        val negative = cents < 0
        val abs = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100
        val grouped = yuan.toString().reversed().chunked(3).joinToString(",").reversed()
        val body = (if (symbol) "¥" else "") + grouped + "." + fen.toString().padStart(2, '0')
        return when {
            negative -> "-$body"
            signed && cents > 0 -> "+$body"
            else -> body
        }
    }

    /** 不带货币符号与千分位、去掉多余的零，用来回填到输入框："12.5"、"12"、"0.05"。 */
    fun plain(cents: Long): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100
        val text = when {
            fen == 0L -> "$yuan"
            fen % 10 == 0L -> "$yuan.${fen / 10}"
            else -> "$yuan.${fen.toString().padStart(2, '0')}"
        }
        return if (negative) "-$text" else text
    }

    /** 简短写法，图表坐标用："3.2万"、"860"。 */
    fun compact(cents: Long): String {
        val yuan = cents / 100
        val abs = kotlin.math.abs(yuan)
        return when {
            abs >= 100_000_000 -> trimZero(yuan / 100_000_000.0) + "亿"
            abs >= 10_000 -> trimZero(yuan / 10_000.0) + "万"
            else -> yuan.toString()
        }
    }

    private fun trimZero(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    /** part / whole，whole 为 0 时为 0。 */
    fun ratio(part: Long, whole: Long): Float = if (whole == 0L) 0f else part.toFloat() / whole.toFloat()
}

/** 记账键盘上的按键。 */
sealed interface KeypadKey {
    data class Digit(val digit: Char) : KeypadKey
    data object Dot : KeypadKey
    data object Plus : KeypadKey
    data object Minus : KeypadKey
    data object Backspace : KeypadKey
    data object Clear : KeypadKey
}

/**
 * 记账键盘的输入规则（纯函数，便于测试）：
 * 每段数字最多两位小数、只能有一个小数点；不允许前导零；运算符连按时替换；总长度有上限。
 */
object AmountInput {
    private const val MAX_LENGTH = 32
    private const val MAX_WHOLE_DIGITS = 10

    fun press(current: String, key: KeypadKey): String {
        val segment = current.substringAfterLast('+').substringAfterLast('-')
        return when (key) {
            KeypadKey.Clear -> ""
            KeypadKey.Backspace -> current.dropLast(1)
            KeypadKey.Plus, KeypadKey.Minus -> {
                val op = if (key == KeypadKey.Plus) '+' else '-'
                when {
                    current.isEmpty() -> current
                    current.last() == '+' || current.last() == '-' -> current.dropLast(1) + op
                    current.last() == '.' -> current.dropLast(1) + op
                    current.length >= MAX_LENGTH -> current
                    else -> current + op
                }
            }
            KeypadKey.Dot -> when {
                current.length >= MAX_LENGTH -> current
                segment.contains('.') -> current
                segment.isEmpty() -> current + "0."
                else -> "$current."
            }
            is KeypadKey.Digit -> {
                if (current.length >= MAX_LENGTH) return current
                val dot = segment.indexOf('.')
                when {
                    dot >= 0 && segment.length - dot - 1 >= 2 -> current
                    dot < 0 && segment == "0" -> current.dropLast(1) + key.digit
                    dot < 0 && segment.length >= MAX_WHOLE_DIGITS -> current
                    else -> current + key.digit
                }
            }
        }
    }

    /** 输入里是否含有运算（用来决定是否显示“= 结果”）。 */
    fun hasOperator(current: String): Boolean = current.drop(1).any { it == '+' || it == '-' }
}
