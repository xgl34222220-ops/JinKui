package com.xiguli.jinkui.data.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesYuanIntoCents() {
        assertEquals(1200L, Money.parse("12"))
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals(1234L, Money.parse("12.34"))
        assertEquals(5L, Money.parse("0.05"))
        assertEquals(50L, Money.parse(".5"))
        assertEquals(123456L, Money.parse("¥1,234.56"))
        assertEquals(-300L, Money.parse("-3"))
        assertEquals(0L, Money.parse("0"))
    }

    @Test
    fun rejectsInvalidAmounts() {
        assertNull(Money.parse(""))
        assertEquals(0L, Money.parse("."))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12元"))
        assertNull(Money.parse("99999999999999"))
        assertNull(Money.parse("10000000000.00"))
        assertEquals(Money.MAX_CENTS, Money.parse("9999999999.99"))
    }

    @Test
    fun noFloatingPointDrift() {
        // 0.1 + 0.2 用浮点数是 0.30000000000000004，这里必须正好 30 分
        assertEquals(30L, Money.evaluate("0.1+0.2"))
        val many = (1..1000).fold(0L) { acc, _ -> acc + Money.parse("0.01")!! }
        assertEquals(1000L, many)
        assertEquals("¥10.00", Money.format(many))
    }

    @Test
    fun evaluatesSimpleExpressions() {
        assertEquals(1500L, Money.evaluate("12.5+3-0.5"))
        assertEquals(1250L, Money.evaluate("12.5+"))
        assertEquals(-250L, Money.evaluate("1-3.5"))
        assertEquals(-100L, Money.evaluate("-1"))
        assertNull(Money.evaluate(""))
        assertNull(Money.evaluate("+"))
        assertNull(Money.evaluate("1++2"))
        assertNull(Money.evaluate("1.234+1"))
    }

    @Test
    fun formatsWithGroupingAndSign() {
        assertEquals("¥0.00", Money.format(0))
        assertEquals("¥0.05", Money.format(5))
        assertEquals("¥1,234.56", Money.format(123456))
        assertEquals("¥1,000,000.00", Money.format(100_000_000))
        assertEquals("-¥12.30", Money.format(-1230))
        assertEquals("+¥12.30", Money.format(1230, signed = true))
        assertEquals("12.30", Money.format(1230, symbol = false))
    }

    @Test
    fun plainDropsTrailingZeros() {
        assertEquals("12", Money.plain(1200))
        assertEquals("12.5", Money.plain(1250))
        assertEquals("12.34", Money.plain(1234))
        assertEquals("0.05", Money.plain(5))
        assertEquals("-3", Money.plain(-300))
        for (cents in listOf(0L, 1L, 10L, 99L, 100L, 12345L, 999_999L)) {
            assertEquals(cents, Money.parse(Money.plain(cents)))
        }
    }

    @Test
    fun compactForChartAxes() {
        assertEquals("860", Money.compact(86_000))
        assertEquals("3.2万", Money.compact(3_200_000))
        assertEquals("1万", Money.compact(1_000_000))
        assertEquals("1.5亿", Money.compact(15_000_000_000))
    }

    @Test
    fun ratioHandlesZero() {
        assertEquals(0f, Money.ratio(5, 0))
        assertEquals(0.5f, Money.ratio(50, 100))
    }

    private fun type(keys: String): String =
        keys.fold("") { acc, ch ->
            AmountInput.press(
                acc,
                when (ch) {
                    '.' -> KeypadKey.Dot
                    '+' -> KeypadKey.Plus
                    '-' -> KeypadKey.Minus
                    '<' -> KeypadKey.Backspace
                    else -> KeypadKey.Digit(ch)
                },
            )
        }

    @Test
    fun keypadKeepsInputWellFormed() {
        assertEquals("12.34", type("12.345"))
        assertEquals("0.5", type(".5"))
        assertEquals("1.5", type("1..5"))
        assertEquals("5", type("05"))
        assertEquals("0.05", type("0.05"))
        assertEquals("12-3", type("12+-3"))
        assertEquals("", type("+"))
        assertEquals("12+0.5", type("12+.5"))
        assertEquals("12+3.25", type("12+3.25"))
        assertEquals("1", type("12<"))
        assertEquals("1+", type("1.+"))
        assertEquals("1234567890", type("12345678901"))
        assertTrue(AmountInput.hasOperator("1+2"))
        assertFalse(AmountInput.hasOperator("12"))
        assertEquals("", AmountInput.press("12.3", KeypadKey.Clear))
    }
}
