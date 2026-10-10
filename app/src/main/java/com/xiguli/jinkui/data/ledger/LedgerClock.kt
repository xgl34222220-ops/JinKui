package com.xiguli.jinkui.data.ledger

import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

/** 账本页面里和“今天是哪天”有关的纯逻辑：跨天 / 跨月时怎么跟、日均怎么算。 */
object LedgerClock {
    /**
     * 跨月时月份怎么变：打开时正在看“本月”的，自动跟到新的本月；
     * 正在翻看别的月份的，保持不动，不打断用户。
     */
    fun follow(viewing: YearMonth, previousCurrent: YearMonth, nowCurrent: YearMonth): YearMonth =
        if (viewing == previousCurrent && nowCurrent != previousCurrent) nowCurrent else viewing

    /** 距下一个本地零点的毫秒数，至少 1 秒（夏令时等边界也不会得到 0 或负数）。 */
    fun millisUntilNextDay(now: ZonedDateTime): Long {
        val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, next).toMillis().coerceAtLeast(1_000L)
    }

    /**
     * 月度日均支出（向下取整到分）：本月按已过去的天数（含今天），过去的月份按整月天数；
     * 将来的月份没有意义，返回 null。
     */
    fun dailyAverage(expense: Long, month: YearMonth, today: LocalDate): Long? {
        val current = YearMonth.from(today)
        val days = when {
            month == current -> today.dayOfMonth
            month < current -> month.lengthOfMonth()
            else -> return null
        }
        return expense / days
    }
}
