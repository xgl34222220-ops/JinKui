package com.xiguli.jinkui.data.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 农历日期。[leap] 为 true 表示闰月。 */
data class LunarDate(val year: Int, val month: Int, val day: Int, val leap: Boolean, val monthDays: Int) {
    val monthName: String get() = (if (leap) "闰" else "") + MONTH_NAMES[month - 1] + "月"
    val dayName: String get() = dayName(day)

    companion object {
        val MONTH_NAMES = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        private val TENS = listOf("初", "十", "廿", "三")
        private val UNITS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

        fun dayName(day: Int): String = when (day) {
            10 -> "初十"
            20 -> "二十"
            30 -> "三十"
            else -> TENS[day / 10] + UNITS[day % 10 - 1]
        }
    }
}

/** 日历格子里要标出的一天：节日、节气或农历日。 */
data class DayMark(
    /** 格子里显示的那一行小字：节日 > 节气 > 农历（初一显示月名）。 */
    val label: String,
    val kind: Kind,
    /** 是否法定节假日的当天（元旦、春节、清明、劳动节、端午、中秋、国庆）。只标节日当天，不含每年公布的调休安排。 */
    val publicHoliday: Boolean,
    val lunar: LunarDate?,
    val solarTerm: String?,
    val festivals: List<String>,
) {
    enum class Kind { FESTIVAL, SOLAR_TERM, LUNAR, NONE }
}

/**
 * 离线的中国农历、二十四节气与节日。
 *
 * 数据是 [LunarData]（tools/lunar/gen_lunar_table.py 按 GB/T 33661-2017 的规则用天文算法生成，范围 1900–2100），
 * 这里只做查表和日期换算：每个农历年的正月初一由月长累加得到，不含任何天文计算，也不联网。
 * 表外的日期返回 null，界面只显示公历。
 */
object ChineseCalendar {
    val SOLAR_TERMS = listOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至",
    )

    /** 各农历年正月初一的 epochDay；最后一项是表的结束边界。 */
    private val yearStarts: LongArray by lazy {
        val out = LongArray(LunarData.YEARS.size + 1)
        var day = LunarData.BASE_EPOCH_DAY
        for (i in LunarData.YEARS.indices) {
            out[i] = day
            day += yearLength(LunarData.YEARS[i])
        }
        out[LunarData.YEARS.size] = day
        check(day == LunarData.END_EPOCH_DAY) { "农历表损坏" }
        out
    }

    private fun leapOf(info: Int) = (info shr 13) and 0xF
    private fun monthCount(info: Int) = if (leapOf(info) != 0) 13 else 12
    private fun monthLength(info: Int, k: Int) = if ((info shr k) and 1 == 1) 30 else 29
    private fun yearLength(info: Int) = (0 until monthCount(info)).sumOf { monthLength(info, it) }

    fun supports(date: LocalDate): Boolean {
        val e = date.toEpochDay()
        return e >= LunarData.BASE_EPOCH_DAY && e < LunarData.END_EPOCH_DAY
    }

    /** 公历换农历；表外返回 null。 */
    fun lunar(date: LocalDate): LunarDate? {
        if (!supports(date)) return null
        val e = date.toEpochDay()
        val starts = yearStarts
        // 二分找到所在农历年
        var lo = 0
        var hi = LunarData.YEARS.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= e) lo = mid else hi = mid - 1
        }
        val info = LunarData.YEARS[lo]
        val leap = leapOf(info)
        var offset = (e - starts[lo]).toInt()
        var number = 0
        for (k in 0 until monthCount(info)) {
            val isLeap = leap != 0 && k == leap
            if (!isLeap) number++
            val len = monthLength(info, k)
            if (offset < len) {
                return LunarDate(LunarData.FIRST_YEAR + lo, if (isLeap) leap else number, offset + 1, isLeap, len)
            }
            offset -= len
        }
        return null
    }

    /** 这一天的节气名（不是节气日返回 null）。 */
    fun solarTerm(date: LocalDate): String? {
        val row = date.year - LunarData.FIRST_YEAR
        if (row !in LunarData.TERMS.indices) return null
        val terms = LunarData.TERMS[row]
        for (half in 0..1) {
            val i = (date.monthValue - 1) * 2 + half
            val day = (terms[i] - '0') + if (half == 0) 1 else 16
            if (day == date.dayOfMonth) return SOLAR_TERMS[i]
        }
        return null
    }

    /** 某年某个节气的日期（例如清明）。 */
    fun solarTermDate(year: Int, index: Int): LocalDate? {
        val row = year - LunarData.FIRST_YEAR
        if (row !in LunarData.TERMS.indices) return null
        val day = (LunarData.TERMS[row][index] - '0') + if (index % 2 == 0) 1 else 16
        return LocalDate.of(year, index / 2 + 1, day)
    }

    private val gregorianFestivals = mapOf(
        (1 to 1) to "元旦", (2 to 14) to "情人节", (3 to 8) to "妇女节", (3 to 12) to "植树节",
        (5 to 1) to "劳动节", (5 to 4) to "青年节", (6 to 1) to "儿童节", (7 to 1) to "建党节",
        (8 to 1) to "建军节", (9 to 10) to "教师节", (10 to 1) to "国庆节", (12 to 24) to "平安夜", (12 to 25) to "圣诞节",
    )

    private val lunarFestivals = mapOf(
        (1 to 1) to "春节", (1 to 15) to "元宵", (2 to 2) to "龙抬头", (5 to 5) to "端午",
        (7 to 7) to "七夕", (7 to 15) to "中元", (8 to 15) to "中秋", (9 to 9) to "重阳",
        (12 to 8) to "腊八", (12 to 23) to "小年",
    )

    private val publicHolidayNames = setOf("元旦", "春节", "清明", "劳动节", "端午", "中秋", "国庆节")

    /** 这一天的全部节日（公历、农历、母亲节 / 父亲节、除夕），按重要程度排。 */
    fun festivals(date: LocalDate, lunar: LunarDate? = lunar(date)): List<String> = buildList {
        if (lunar != null && !lunar.leap) {
            lunarFestivals[lunar.month to lunar.day]?.let(::add)
            if (lunar.month == 12 && lunar.day == lunar.monthDays) add("除夕")
        }
        gregorianFestivals[date.monthValue to date.dayOfMonth]?.let(::add)
        if (date.monthValue == 5 && date == date.with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY))) add("母亲节")
        if (date.monthValue == 6 && date == date.with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY))) add("父亲节")
    }.sortedBy { if (it in publicHolidayNames || it == "除夕") 0 else 1 }

    fun mark(date: LocalDate): DayMark {
        val lunar = lunar(date)
        val term = solarTerm(date)
        val festivals = festivals(date, lunar)
        val holiday = festivals.any { it in publicHolidayNames } || term == "清明"
        return when {
            festivals.isNotEmpty() -> DayMark(festivals.first(), DayMark.Kind.FESTIVAL, holiday, lunar, term, festivals)
            term != null -> DayMark(term, DayMark.Kind.SOLAR_TERM, holiday, lunar, term, festivals)
            lunar != null -> DayMark(if (lunar.day == 1) lunar.monthName else lunar.dayName, DayMark.Kind.LUNAR, false, lunar, null, festivals)
            else -> DayMark("", DayMark.Kind.NONE, false, null, null, festivals)
        }
    }

    /** “农历九月初一” / “农历闰六月十五”。 */
    fun describe(date: LocalDate): String? = lunar(date)?.let { "农历${it.monthName}${it.dayName}" }
}
