package com.xiguli.jinkui.data.model

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 有效期识别：从“有效期 / 有效期至 / 有效期限”字段里读出到期日，用于到期提醒。
 * 认得常见写法；认不出就当作没有到期日，不做任何猜测。
 */
object Expiry {
    val LABELS = setOf("有效期", "有效期至", "有效期限")

    private val FULL = Regex("""(\d{4})\s*[-./年]\s*(\d{1,2})\s*[-./月]\s*(\d{1,2})""")
    private val COMPACT = Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})(?!\d)""")
    private val YEAR_MONTH = Regex("""(\d{4})\s*[-./年]\s*(\d{1,2})(?!\d)""")
    private val MONTH_YEAR = Regex("""(?<!\d)(\d{1,2})\s*/\s*(\d{4}|\d{2})(?!\d)""")

    /**
     * 到期日（含当天有效）。支持：
     * `2034-05-20`、`2034.05.20`、`2034年5月20日`、`20340520`、`2034-05`、`08/29`、`08/2029`，
     * 以及 `2020.01.01-2040.01.01` 这样的起止写法（取后一个日期）。“长期”返回 null。
     */
    fun parse(text: String): LocalDate? {
        if (text.isBlank() || text.contains("长期")) return null
        return try {
            FULL.findAll(text).lastOrNull()?.let { m ->
                return LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }
            COMPACT.findAll(text).lastOrNull()?.let { m ->
                return LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }
            YEAR_MONTH.findAll(text).lastOrNull()?.let { m ->
                return YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()).atEndOfMonth()
            }
            MONTH_YEAR.findAll(text).lastOrNull()?.let { m ->
                val year = m.groupValues[2].toInt().let { if (it < 100) 2000 + it else it }
                return YearMonth.of(year, m.groupValues[1].toInt()).atEndOfMonth()
            }
            null
        } catch (e: DateTimeException) {
            null
        }
    }

    /** 距到期还有几天；负数表示已过期；没有可识别的有效期时为 null。标为敏感的有效期不参与提醒。 */
    fun daysLeft(card: Card, today: LocalDate): Long? {
        val field = card.fields.firstOrNull { it.label.trim() in LABELS && !it.sensitive && it.value.isNotBlank() }
            ?: return null
        val date = parse(field.value) ?: return null
        return ChronoUnit.DAYS.between(today, date)
    }

    /** 提前多少天开始提醒。 */
    const val WARN_DAYS = 90L
}
