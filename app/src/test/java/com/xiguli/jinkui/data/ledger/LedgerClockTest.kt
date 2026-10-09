package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerClockTest {
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val aug = YearMonth.of(2026, 8)

    @Test
    fun followsToNewMonthOnlyWhenViewingCurrent() {
        // 停在本月，跨过月末：跟到新的本月
        assertEquals(oct, LedgerClock.follow(viewing = sep, previousCurrent = sep, nowCurrent = oct))
        // 正在翻看上个月：不动
        assertEquals(aug, LedgerClock.follow(viewing = aug, previousCurrent = sep, nowCurrent = oct))
        // 没有跨月：不动
        assertEquals(sep, LedgerClock.follow(viewing = sep, previousCurrent = sep, nowCurrent = sep))
        // 跨年
        assertEquals(YearMonth.of(2027, 1), LedgerClock.follow(YearMonth.of(2026, 12), YearMonth.of(2026, 12), YearMonth.of(2027, 1)))
    }

    @Test
    fun millisUntilNextDay() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.of(2026, 10, 10, 23, 59, 30).atZone(zone)
        assertEquals(30_000L, LedgerClock.millisUntilNextDay(now))
        val midnight = LocalDateTime.of(2026, 10, 10, 0, 0).atZone(zone)
        assertEquals(86_400_000L, LedgerClock.millisUntilNextDay(midnight))
        // 有夏令时的时区也不会得到 0 或负数
        val ny = LocalDateTime.of(2026, 3, 7, 23, 59, 59, 999_000_000).atZone(ZoneId.of("America/New_York"))
        assertTrue(LedgerClock.millisUntilNextDay(ny) >= 1_000L)
    }

    @Test
    fun dailyAverage() {
        val today = LocalDate.of(2026, 10, 10)
        // 本月按已过去的 10 天
        assertEquals(1_000L, LedgerClock.dailyAverage(10_000L, oct, today))
        // 向下取整到分
        assertEquals(333L, LedgerClock.dailyAverage(3_339L, oct, today))
        // 过去的月份按整月（9 月 30 天）
        assertEquals(100L, LedgerClock.dailyAverage(3_000L, sep, today))
        // 2 月 29 天（闰年 2028）
        assertEquals(100L, LedgerClock.dailyAverage(2_900L, YearMonth.of(2028, 2), LocalDate.of(2028, 5, 1)))
        // 将来的月份没有日均
        assertNull(LedgerClock.dailyAverage(100L, YearMonth.of(2026, 11), today))
        assertEquals(0L, LedgerClock.dailyAverage(0L, oct, today))
    }
}
