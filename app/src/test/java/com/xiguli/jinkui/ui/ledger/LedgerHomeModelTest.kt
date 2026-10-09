package com.xiguli.jinkui.ui.ledger

import com.xiguli.jinkui.data.ledger.TxFilter
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerHomeModelTest {
    private val october = YearMonth.of(2026, 10)
    private fun ui(filter: TxFilter = TxFilter(), month: YearMonth = october) =
        buildLedgerUi(LedgerSamples.data, month, filter, LedgerSamples.zone, LedgerSamples.today)

    @Test
    fun groupsAndLabelsDays() {
        val ui = ui()
        assertFalse(ui.loading)
        assertEquals(listOf("今天", "昨天", "10月8日", "10月6日", "10月3日"), ui.days.map { it.label })
        assertEquals("周六", ui.days.first().weekday)
        assertEquals("支 ¥50.50", ui.days.first().totals)
        // 转账不计入当日收支
        assertEquals("收 ¥18,500.00", ui.days[1].totals)
    }

    @Test
    fun rowTextIsPrecomputed() {
        val rows = ui().days.flatMap { it.rows }
        val noodles = rows.first { it.tx.note == "牛肉面" }
        assertEquals("餐饮 · 午餐", noodles.title)
        assertEquals("12:18　微信　牛肉面", noodles.subtitle)
        assertEquals("-¥32.50", noodles.amountText)
        assertEquals("lunch", noodles.iconKey)

        val salary = rows.first { it.type == TxType.INCOME }
        assertEquals("+¥18,500.00", salary.amountText)
        assertEquals("payments", salary.iconKey)

        val transfer = rows.first { it.type == TxType.TRANSFER }
        assertEquals("转账", transfer.title)
        assertEquals("10:30　招商银行 → 微信", transfer.subtitle)
        assertEquals("¥2,000.00", transfer.amountText)
        assertNull(transfer.iconKey)

        val rent = rows.first { it.tx.tags.isNotEmpty() }
        assertTrue(rent.subtitle.endsWith("#房租"))
        // 同一天里时间新的在前
        assertEquals(listOf("12:18", "08:40"), ui().days.first().rows.map { it.subtitle.take(5) })
    }

    @Test
    fun summaryAverageAndBudget() {
        val ui = ui()
        assertEquals(3_250L + 1_800 + 380_000 + 12_990 + 6_800, ui.summary.expense)
        assertEquals(1_850_000L, ui.summary.income)
        // 10 月 10 日：按 10 天算日均
        assertEquals(ui.summary.expense / 10, ui.dailyAverage)
        assertEquals(600_000L, ui.totalBudget?.budget?.amount)
        assertEquals(ui.summary.expense, ui.totalBudget?.spent)
        // 将来的月份没有日均
        assertNull(ui(month = YearMonth.of(2026, 11)).dailyAverage)
        // 过去的月份按整月
        assertEquals(210_000L / 30, ui(month = YearMonth.of(2026, 9)).dailyAverage)
    }

    @Test
    fun filterKeepsMonthCount() {
        val filtered = ui(TxFilter(query = "耳机"))
        assertTrue(filtered.filterActive)
        assertEquals(1, filtered.days.sumOf { it.rows.size })
        assertEquals(7, filtered.monthCount)
    }

    @Test
    fun placeholderIsLoadingAndEmpty() {
        val placeholder = LedgerUi.placeholder(october)
        assertTrue(placeholder.loading)
        assertTrue(placeholder.days.isEmpty())
    }

    @Test
    fun shortDayLabels() {
        val today = LocalDate.of(2026, 1, 1)
        assertEquals("今天", shortDayLabel(today, today))
        assertEquals("昨天", shortDayLabel(LocalDate.of(2025, 12, 31), today))
        assertEquals("2025年12月30日", shortDayLabel(LocalDate.of(2025, 12, 30), today))
        assertEquals("1月5日", shortDayLabel(LocalDate.of(2026, 1, 5), today))
    }
}
