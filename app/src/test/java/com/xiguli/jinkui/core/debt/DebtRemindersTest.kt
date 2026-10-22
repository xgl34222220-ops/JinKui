package com.xiguli.jinkui.core.debt

import com.xiguli.jinkui.data.ledger.DueItem
import com.xiguli.jinkui.data.ledger.DueKind
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.4 到期提醒的排期：最早一项到期前 3 天上午 9 点；已经在 3 天内 / 逾期时是下一个上午 9 点；只是提示的项不提醒。 */
class DebtRemindersTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun item(due: LocalDate, counts: Boolean = true, amount: Long = 100) =
        DueItem("k-$due", DueKind.CREDIT, "花呗", "", amount, due, false, payable = true, counts = counts, accountId = "a")

    private fun at(m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(2026, m, d, h, min, 0, 0, zone)

    @Test
    fun firesThreeDaysBeforeAtNine() {
        val next = DebtReminders.nextFire(listOf(item(LocalDate.of(2026, 10, 20)), item(LocalDate.of(2026, 11, 1))), at(10, 11, 22))
        assertEquals(at(10, 17, 9), next)
    }

    @Test
    fun insideWindowFiresNextNineAm() {
        assertEquals(at(10, 12, 9), DebtReminders.nextFire(listOf(item(LocalDate.of(2026, 10, 13))), at(10, 11, 22)))
        assertEquals(at(10, 12, 9), DebtReminders.nextFire(listOf(item(LocalDate.of(2026, 10, 13))), at(10, 12, 7, 30)))
        // 已逾期：同样是下一个上午 9 点
        assertEquals(at(10, 12, 9), DebtReminders.nextFire(listOf(item(LocalDate.of(2026, 10, 1))), at(10, 11, 9)))
    }

    @Test
    fun hintsAndPaidItemsDoNotFire() {
        assertNull(DebtReminders.nextFire(emptyList(), at(10, 11, 9)))
        assertNull(DebtReminders.nextFire(listOf(item(LocalDate.of(2026, 10, 13), counts = false)), at(10, 11, 9)))
        assertNull(DebtReminders.nextFire(listOf(item(LocalDate.of(2026, 10, 13), amount = 0)), at(10, 11, 9)))
    }
}
