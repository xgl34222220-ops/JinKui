package com.xiguli.jinkui.data.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseCalendarTest {
    private fun lunar(y: Int, m: Int, d: Int) = ChineseCalendar.lunar(LocalDate.of(y, m, d))!!

    @Test
    fun knownNewYears() {
        // 春节（正月初一）的公历日期
        val newYears = mapOf(
            1900 to LocalDate.of(1900, 1, 31),
            1949 to LocalDate.of(1949, 1, 29),
            2000 to LocalDate.of(2000, 2, 5),
            2024 to LocalDate.of(2024, 2, 10),
            2025 to LocalDate.of(2025, 1, 29),
            2026 to LocalDate.of(2026, 2, 17),
            2027 to LocalDate.of(2027, 2, 6),
            2050 to LocalDate.of(2050, 1, 23),
            2100 to LocalDate.of(2100, 2, 9),
        )
        for ((year, date) in newYears) {
            val l = ChineseCalendar.lunar(date)!!
            assertEquals("$year", LunarDate(year, 1, 1, false, l.monthDays), l)
            if (year > 1900) assertEquals("$year 除夕在前一天", 12, ChineseCalendar.lunar(date.minusDays(1))!!.month)
        }
    }

    @Test
    fun october2026MatchesPrintedCalendars() {
        val l = lunar(2026, 10, 10)
        assertEquals(2026, l.year)
        assertEquals(9, l.month)
        assertEquals(1, l.day)
        assertFalse(l.leap)
        assertEquals("农历九月初一", ChineseCalendar.describe(LocalDate.of(2026, 10, 10)))
        assertEquals("九月", ChineseCalendar.mark(LocalDate.of(2026, 10, 10)).label)
        assertEquals("重阳", ChineseCalendar.mark(LocalDate.of(2026, 10, 18)).label)
        assertEquals("寒露", ChineseCalendar.mark(LocalDate.of(2026, 10, 8)).label)
        assertEquals("霜降", ChineseCalendar.mark(LocalDate.of(2026, 10, 23)).label)
        assertEquals("国庆节", ChineseCalendar.mark(LocalDate.of(2026, 10, 1)).label)
        assertEquals("廿二", ChineseCalendar.mark(LocalDate.of(2026, 10, 2)).label)
        assertEquals("中秋", ChineseCalendar.mark(LocalDate.of(2026, 9, 25)).label)
        assertTrue(ChineseCalendar.mark(LocalDate.of(2026, 10, 1)).publicHoliday)
        assertFalse(ChineseCalendar.mark(LocalDate.of(2026, 10, 18)).publicHoliday)
    }

    @Test
    fun leapMonths() {
        // 2023 闰二月、2025 闰六月、2028 闰五月、2033 闰十一月
        assertEquals(LunarDate(2023, 2, 1, true, lunar(2023, 3, 22).monthDays), lunar(2023, 3, 22))
        val l2025 = lunar(2025, 7, 25)
        assertEquals(6, l2025.month)
        assertTrue(l2025.leap)
        assertEquals("闰六月", l2025.monthName)
        assertTrue(lunar(2033, 12, 22).leap)
        assertEquals(11, lunar(2033, 12, 22).month)
        assertTrue(lunar(2028, 6, 23).leap)
    }

    @Test
    fun solarTermsAndFestivals() {
        assertEquals("清明", ChineseCalendar.solarTerm(LocalDate.of(2026, 4, 5)))
        assertTrue(ChineseCalendar.mark(LocalDate.of(2026, 4, 5)).publicHoliday)
        assertEquals("冬至", ChineseCalendar.solarTerm(LocalDate.of(2026, 12, 22)))
        assertEquals("立春", ChineseCalendar.solarTerm(LocalDate.of(2026, 2, 4)))
        assertEquals("夏至", ChineseCalendar.solarTerm(LocalDate.of(2026, 6, 21)))
        assertNull(ChineseCalendar.solarTerm(LocalDate.of(2026, 10, 10)))
        assertEquals("除夕", ChineseCalendar.mark(LocalDate.of(2026, 2, 16)).label)
        assertEquals("春节", ChineseCalendar.mark(LocalDate.of(2026, 2, 17)).label)
        assertEquals("端午", ChineseCalendar.mark(LocalDate.of(2026, 6, 19)).label)
        assertEquals("母亲节", ChineseCalendar.mark(LocalDate.of(2026, 5, 10)).label)
        // 2024 年冬至是 12 月 21 日（不是 22 日）
        assertEquals("冬至", ChineseCalendar.solarTerm(LocalDate.of(2024, 12, 21)))
    }

    @Test
    fun everyDayInRangeDecodes() {
        var date = LocalDate.of(1900, 1, 31)
        val end = LocalDate.of(2100, 12, 31)
        var previous: LunarDate? = null
        while (!date.isAfter(end)) {
            val l = ChineseCalendar.lunar(date)!!
            if (previous != null) {
                // 日子一天天往后走：要么同月加一天，要么换到下一个月的初一
                if (l.day != 1) assertEquals(previous.day + 1, l.day) else assertTrue(previous.day == 29 || previous.day == 30)
            }
            previous = l
            date = date.plusDays(1)
        }
        assertNull(ChineseCalendar.lunar(LocalDate.of(1900, 1, 30)))
        assertNull(ChineseCalendar.lunar(LocalDate.of(2101, 3, 1)))
        for (year in 1900..2100) for (i in 0 until 24) {
            val d = ChineseCalendar.solarTermDate(year, i)!!
            assertEquals(ChineseCalendar.SOLAR_TERMS[i], ChineseCalendar.solarTerm(d))
        }
    }

    @Test
    fun dayNames() {
        assertEquals("初一", LunarDate.dayName(1))
        assertEquals("初十", LunarDate.dayName(10))
        assertEquals("十五", LunarDate.dayName(15))
        assertEquals("二十", LunarDate.dayName(20))
        assertEquals("廿一", LunarDate.dayName(21))
        assertEquals("三十", LunarDate.dayName(30))
    }
}
