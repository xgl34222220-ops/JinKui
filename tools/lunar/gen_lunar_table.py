#!/usr/bin/env python3
"""
生成金匮日历用的农历与二十四节气表（1900–2100），输出 Kotlin 源文件。

只在开发机上运行一次，结果作为数据提交进仓库；App 里不含天文计算库，也不联网。
算法（中国农历的现行规则，GB/T 33661-2017《农历的编算和颁行》）：
  1. 用 PyEphem（只作开发工具，不打包进 App）算出每次朔（新月）与每个中气 / 节气的时刻，换算成北京时间（UTC+8）取日期；
  2. 含冬至的朔望月为十一月；两个冬至月之间有 13 个朔望月时置闰，第一个不含中气的月为闰月；
  3. 月大 30 天、月小 29 天。
运行：pip install ephem && python3 gen_lunar_table.py > ../../app/src/main/java/com/xiguli/jinkui/data/calendar/LunarData.kt
"""
import datetime as dt
import math
import sys

import ephem

TZ = dt.timedelta(hours=8)


def bj_date(edate):
    return (ephem.Date(edate).datetime() + TZ).date()


def sun_lon(d):
    s = ephem.Sun()
    s.compute(d, epoch=d)
    ecl = ephem.Ecliptic(ephem.Equatorial(s.g_ra, s.g_dec, epoch=d), epoch=d)
    return math.degrees(ecl.lon) % 360.0


def term_time(year, target):
    """太阳视黄经到达 target 度的时刻（target 为 15 的倍数）。先粗估，再二分。"""
    # 春分约 3 月 20 日，黄经 0°；每度约 1.0146 天
    approx = ephem.Date(dt.datetime(year, 3, 20)) + ((target % 360) / 360.0) * 365.2422
    if target >= 285:  # 小寒、大寒在当年 1 月
        approx -= 365.2422
    lo, hi = approx - 10, approx + 10

    def diff(t):
        return ((sun_lon(t) - target + 180) % 360) - 180

    for _ in range(60):
        mid = (lo + hi) / 2
        if diff(mid) < 0:
            lo = mid
        else:
            hi = mid
    t = (lo + hi) / 2
    assert abs(diff(t)) < 1e-6, (year, target)  # 区间里确实有根，而不是收敛到端点
    return ephem.Date(t)


# 二十四节气，按公历一年里的顺序：小寒(285°)、大寒(300°)、立春(315°)……冬至(270°)
TERM_DEG = [(285 + 15 * i) % 360 for i in range(24)]


def solar_terms(year):
    return [bj_date(term_time(year, deg)) for deg in TERM_DEG]


def new_moons(start, end):
    out = []
    d = ephem.next_new_moon(ephem.Date(start) - 1)
    while ephem.Date(d).datetime() < end:
        out.append(bj_date(d))
        d = ephem.next_new_moon(ephem.Date(d) + 1)
    return out


def main():
    first, last = 1900, 2100
    moons = new_moons(dt.datetime(first - 1, 10, 1), dt.datetime(last + 2, 3, 1))
    terms = {y: solar_terms(y) for y in range(first - 1, last + 2)}
    # 中气：大寒、雨水……冬至（下标为奇数）
    zhongqi = sorted(d for y in terms for i, d in enumerate(terms[y]) if i % 2 == 1)
    winter = {y: terms[y][23] for y in terms}  # 冬至

    def month_start_index(day):
        """含 day 的朔望月在 moons 里的下标。"""
        idx = max(i for i, m in enumerate(moons) if m <= day)
        return idx

    def has_zhongqi(i):
        a, b = moons[i], moons[i + 1]
        return any(a <= z < b for z in zhongqi)

    # 先给每个朔望月编上（农历年，月，是否闰月）
    labels = {}
    for y in range(first - 1, last + 2):
        if y + 1 not in winter:
            break
        a = month_start_index(winter[y])      # 上一个十一月
        b = month_start_index(winter[y + 1])  # 下一个十一月
        n = b - a
        leap_done = n == 12
        month = 11
        for i in range(a, b):
            if i == a:
                labels[i] = (month, False)
                continue
            if not leap_done and not has_zhongqi(i):
                labels[i] = (labels[i - 1][0], True)
                leap_done = True
                continue
            month = month % 12 + 1
            labels[i] = (month, False)

    # 汇总成每个农历年：正月初一的公历日期、各月天数、闰月
    years = {}
    for i in sorted(labels):
        if i + 1 >= len(moons):
            break
        m, leap = labels[i]
        start = moons[i]
        length = (moons[i + 1] - moons[i]).days
        ly = start.year if not (m >= 11 and start.month <= 2 and False) else start.year
        # 十一、十二月（及其闰月）属于上一个农历年
        if m >= 11 and start.month <= 3:
            ly = start.year - 1
        elif m <= 2 and start.month >= 11:
            ly = start.year + 1
        years.setdefault(ly, []).append((m, leap, length, start))

    rows = []
    for y in range(first, last + 1):
        months = years[y]
        assert months[0][0] == 1 and not months[0][1], (y, months[:2])
        leap = next((m for m, l, _, _ in months if l), 0)
        bits = 0
        for k, (m, l, length, _) in enumerate(months):
            if length == 30:
                bits |= 1 << k
        rows.append((months[0][3], len(months), leap, bits))
    # 2101 年正月初一（表的结束边界）
    end = years[last + 1][0][3]

    base = rows[0][0]
    w = sys.stdout.write
    w("package com.xiguli.jinkui.data.calendar\n\n")
    w("// 由 tools/lunar/gen_lunar_table.py 生成，请勿手改。\n")
    w("// 农历：按 GB/T 33661-2017 的规则，用天文算法算出的朔与中气（北京时间）编排。\n\n")
    w("internal object LunarData {\n")
    w(f"    /** 表里第一个农历正月初一：{base.isoformat()}。 */\n")
    w(f"    const val FIRST_YEAR = {first}\n    const val LAST_YEAR = {last}\n")
    w(f"    const val BASE_EPOCH_DAY = {(base - dt.date(1970,1,1)).days}L\n")
    w(f"    /** {last + 1} 年正月初一（表的结束边界，不含）。 */\n")
    w(f"    const val END_EPOCH_DAY = {(end - dt.date(1970,1,1)).days}L\n\n")
    w("    /**\n     * 每个农历年一项：低 13 位为各月大小（第 k 位为 1 表示第 k 个月 30 天，闰月按出现顺序计入），\n")
    w("     * 13–16 位为闰月月份（0 为无闰月）。正月初一不单独存，由 BASE_EPOCH_DAY 起按月长累加得到。\n     */\n")
    w("    val YEARS: IntArray = intArrayOf(\n")
    for k in range(0, len(rows), 8):
        chunk = rows[k:k + 8]
        w("        " + ", ".join("0x%05x" % ((leap << 13) | bits) for (_, n, leap, bits) in chunk) + ",\n")
    w("    )\n\n")
    w("    /**\n     * 二十四节气的公历日期，每年 24 位数字：按小寒、大寒、立春……冬至的顺序，\n")
    w("     * 偶数位（月内第一个节气）为“日期 - 1”，奇数位（月内第二个节气）为“日期 - 16”。\n     */\n")
    w("    val TERMS: Array<String> = arrayOf(\n")
    for y in range(first, last + 1):
        s = ""
        for i, d in enumerate(terms[y]):
            assert d.month == i // 2 + 1, (y, i, d)
            v = d.day - (1 if i % 2 == 0 else 16)
            assert 0 <= v <= 9, (y, i, d)
            s += str(v)
        w(f'        "{s}",\n')
    w("    )\n}\n")
    # 自检：月长累加的结果与天文朔日一致
    total = base
    for (start, n, leap, bits), y in zip(rows, range(first, last + 1)):
        assert total == start, (y, total, start)
        total += dt.timedelta(days=sum(30 if bits >> k & 1 else 29 for k in range(n)))
    assert total == end


main()
