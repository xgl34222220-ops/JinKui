package com.xiguli.jinkui.data.ledger

import com.xiguli.jinkui.core.crypto.FieldCipher
import com.xiguli.jinkui.data.backup.BackupJson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val cash = LedgerAccount("acc-cash", "现金", AccountKind.CASH)
    private val bank = LedgerAccount("a-bank", "招行", AccountKind.BANK)
    private val accounts = listOf(cash, bank)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun rule(
        frequency: RecurFrequency,
        start: LocalDate,
        minute: Int = 9 * 60,
        end: LocalDate? = null,
        next: Int = 0,
        type: TxType = TxType.EXPENSE,
        account: String = cash.id,
        to: String? = null,
    ) = LedgerRecurring(
        id = "r-1", type = type, amount = 350_000, accountId = account, toAccountId = to,
        categoryId = if (type == TxType.TRANSFER) null else "cat-housing-1", note = "房租",
        frequency = frequency, startDate = start, minuteOfDay = minute, endDate = end, nextIndex = next,
    )

    @Test
    fun monthlyOnThe31stClampsWithoutDrifting() {
        val r = rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 1, 31))
        val dates = (0..4).map { RecurringMath.dateOf(r, it) }
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31)),
            dates,
        )
    }

    @Test
    fun leapDayYearlyFallsOnFeb28() {
        val r = rule(RecurFrequency.YEARLY, LocalDate.of(2024, 2, 29))
        assertEquals(LocalDate.of(2025, 2, 28), RecurringMath.dateOf(r, 1))
        assertEquals(LocalDate.of(2028, 2, 29), RecurringMath.dateOf(r, 4))
    }

    @Test
    fun weeklyAndDaily() {
        val start = LocalDate.of(2026, 10, 5)
        assertEquals(LocalDate.of(2026, 10, 19), RecurringMath.dateOf(rule(RecurFrequency.WEEKLY, start), 2))
        assertEquals(LocalDate.of(2026, 11, 4), RecurringMath.dateOf(rule(RecurFrequency.DAILY, start), 30))
    }

    @Test
    fun dueIncludesOnlyPastOccurrencesAtTheirTime() {
        val r = rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1), minute = 9 * 60)
        // 10 月 3 日 08:59：1、2 日到期，3 日 09:00 还没到
        val plan = RecurringMath.due(r, at(2026, 10, 3, 8, 59), zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), plan.occurrences.map { it.date })
        assertEquals(2, plan.nextIndex)
        assertEquals(at(2026, 10, 1, 9, 0), plan.occurrences[0].occurredAt)
        // 刚好 09:00 也算到期
        assertEquals(3, RecurringMath.due(r, at(2026, 10, 3, 9, 0), zone).nextIndex)
    }

    @Test
    fun endDateIsInclusiveAndPausedGeneratesNothing() {
        val r = rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 3))
        val plan = RecurringMath.due(r, at(2026, 12, 1), zone)
        assertEquals(3, plan.occurrences.size)
        assertNull(RecurringMath.nextDate(r.copy(nextIndex = plan.nextIndex)))
        assertTrue(RecurringMath.due(r.copy(paused = true), at(2026, 12, 1), zone).occurrences.isEmpty())
    }

    @Test
    fun catchUpIsCappedPerRun() {
        val r = rule(RecurFrequency.DAILY, LocalDate.of(2020, 1, 1))
        val plan = RecurringMath.due(r, at(2026, 10, 10), zone, limit = 50)
        assertEquals(50, plan.occurrences.size)
        assertTrue(plan.truncated)
        assertEquals(50, plan.nextIndex)
        // 下一次从第 50 期继续
        assertEquals(LocalDate.of(2020, 2, 20), RecurringMath.due(r.copy(nextIndex = 50), at(2026, 10, 10), zone, limit = 1).occurrences.single().date)
    }

    @Test
    fun generateNeverDuplicates() {
        val r = rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 8, 1))
        val now = at(2026, 10, 10)
        val first = RecurringMath.generate(LedgerData(accounts = accounts, recurring = listOf(r)), now, zone)
        assertEquals(3, first.transactions.size)
        assertEquals(setOf("r20260801-r-1", "r20260901-r-1", "r20261001-r-1"), first.transactions.map { it.id }.toSet())
        assertEquals(3, first.rules.single().nextIndex)
        // 生成推进进度时不改规则的更新时间
        assertEquals(r.updatedAt, first.rules.single().updatedAt)

        // 第二次（进度已推进）：什么也不生成
        val after = LedgerData(accounts = accounts, transactions = first.transactions, recurring = first.rules)
        assertTrue(RecurringMath.generate(after, now, zone).isEmpty)

        // 进度没推进但流水已存在（例如从另一台手机导入了同一规则生成的流水）：推进进度但不重复写流水
        val imported = LedgerData(accounts = accounts, transactions = first.transactions, recurring = listOf(r))
        val again = RecurringMath.generate(imported, now, zone)
        assertTrue(again.transactions.isEmpty())
        assertEquals(3, again.rules.single().nextIndex)

        // 生成的流水 ID 是合法的备份 ID，并且通过保存校验
        for (tx in first.transactions) {
            assertNull(LedgerMath.validate(tx, accounts))
            assertTrue(Regex("[A-Za-z0-9-]{1,64}").matches(tx.id))
        }
    }

    @Test
    fun blockedRulesDoNotAdvance() {
        val archived = cash.copy(archived = true)
        val r = rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1))
        assertNotNull(RecurringMath.blockedReason(r, listOf(archived, bank)))
        assertTrue(RecurringMath.generate(LedgerData(accounts = listOf(archived, bank), recurring = listOf(r)), at(2026, 10, 10), zone).isEmpty)
        // 账户删除
        assertNotNull(RecurringMath.blockedReason(r, listOf(bank)))
        // 转账到同一账户 / 转入账户已停用
        assertNotNull(RecurringMath.blockedReason(rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1), type = TxType.TRANSFER, to = cash.id), accounts))
        assertNotNull(RecurringMath.blockedReason(rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1), type = TxType.TRANSFER, to = bank.id), listOf(cash, bank.copy(archived = true))))
        assertNull(RecurringMath.blockedReason(rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1), type = TxType.TRANSFER, to = bank.id), accounts))
    }

    @Test
    fun transferRuleGeneratesTransfersWithoutCategory() {
        val r = rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 10, 1), type = TxType.TRANSFER, account = bank.id, to = cash.id).copy(categoryId = "cat-food")
        val tx = RecurringMath.generate(LedgerData(accounts = accounts, recurring = listOf(r)), at(2026, 10, 10), zone).transactions.single()
        assertEquals(TxType.TRANSFER, tx.type)
        assertEquals(cash.id, tx.toAccountId)
        assertNull(tx.categoryId)
        val balances = LedgerMath.balances(accounts, listOf(tx))
        assertEquals(-350_000L, balances[bank.id])
        assertEquals(350_000L, balances[cash.id])
    }

    @Test
    fun editingScheduleResumesAfterLastGenerated() {
        val r = rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 1, 15), next = 10) // 已生成到 10 月 15 日
        // 只改金额：进度不变
        assertEquals(10, RecurringMath.indexAfterEdit(r, r.copy(amount = 1)))
        // 改成每月 1 日，从 2026-01-01 起：10 月 15 日之后的第一个 1 日是 11 月 1 日，即第 10 期
        val moved = r.copy(startDate = LocalDate.of(2026, 1, 1))
        val index = RecurringMath.indexAfterEdit(r, moved)
        assertEquals(LocalDate.of(2026, 11, 1), RecurringMath.dateOf(moved, index))
        // 改成每天：从 10 月 16 日继续，不补记 1 月以来的每一天
        val daily = r.copy(frequency = RecurFrequency.DAILY)
        assertEquals(LocalDate.of(2026, 10, 16), RecurringMath.dateOf(daily, RecurringMath.indexAfterEdit(r, daily)))
        // 新规则从第 0 期开始；从没生成过的规则改日期也从第 0 期开始
        assertEquals(0, RecurringMath.indexAfterEdit(null, r))
        assertEquals(0, RecurringMath.indexAfterEdit(r.copy(nextIndex = 0), moved))
    }

    @Test
    fun validateRejectsBadRules() {
        assertNotNull(RecurringMath.validate(rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1)).copy(amount = 0), accounts))
        assertNotNull(RecurringMath.validate(rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 2), end = LocalDate.of(2026, 10, 1)), accounts))
        assertNotNull(RecurringMath.validate(rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1), type = TxType.TRANSFER, to = cash.id), accounts))
        assertNull(RecurringMath.validate(rule(RecurFrequency.DAILY, LocalDate.of(2026, 10, 1)), accounts))
    }

    @Test
    fun describeReadsNaturally() {
        assertEquals("每月 15 日 09:00", RecurringMath.describe(rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 10, 15))))
        assertEquals("每周一 08:30", RecurringMath.describe(rule(RecurFrequency.WEEKLY, LocalDate.of(2026, 10, 5), minute = 8 * 60 + 30)))
        assertTrue(RecurringMath.describe(rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 1, 31))).contains("月末"))
    }

    @Test
    fun payloadRoundTripsAndIsBoundToRecord() {
        val r = rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 1, 31), end = LocalDate.of(2027, 1, 31), next = 3)
            .copy(tags = listOf("固定", "家"), paused = true, createdAt = 5, updatedAt = 9)
        assertEquals(r, LedgerJson.readRecurringPayload(r.id, 9, LedgerJson.recurringPayload(r)))
        val cipher = FieldCipher(ByteArray(32) { it.toByte() })
        val sealed = cipher.encrypt(LedgerJson.recurringPayload(r), "ledger:recurring:${r.id}")
        assertFalse(sealed.contains("房租"))
        assertEquals(r, LedgerJson.readRecurringPayload(r.id, 9, cipher.decrypt(sealed, "ledger:recurring:${r.id}")))
    }

    @Test
    fun backupIncludesRecurringAndOldBackupsStillImport() {
        val r = rule(RecurFrequency.WEEKLY, LocalDate.of(2026, 10, 5), next = 1).copy(updatedAt = 7)
        val data = LedgerData(accounts = accounts, recurring = listOf(r))
        val restored = BackupJson.decodeAll(BackupJson.encode(emptyList(), 1L, data)).ledger
        assertEquals(listOf(r), restored.recurring)
        // 0.6.0（schema 2）的账本小节没有 recurring
        val v2 = JSONObject(String(BackupJson.encode(emptyList(), 1L, data.copy(recurring = emptyList())), Charsets.UTF_8))
        v2.put("schema", 2)
        v2.getJSONObject("ledger").remove("recurring")
        val old = BackupJson.decodeAll(v2.toString().toByteArray()).ledger
        assertTrue(old.recurring.isEmpty())
        assertEquals(2, old.accounts.size)
    }

    @Test
    fun mergeKeepsNewerRuleByUserEditTime() {
        val local = rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 1, 1), next = 9).copy(updatedAt = 5)
        val incomingOlder = local.copy(amount = 1, nextIndex = 2, updatedAt = 4)
        val incomingNewer = local.copy(amount = 2, nextIndex = 2, updatedAt = 6)
        assertTrue(LedgerMerge.plan(LedgerData(recurring = listOf(local)), LedgerData(recurring = listOf(incomingOlder))).toWrite.recurring.isEmpty())
        assertEquals(listOf(incomingNewer), LedgerMerge.plan(LedgerData(recurring = listOf(local)), LedgerData(recurring = listOf(incomingNewer))).toWrite.recurring)
    }

    @Test
    fun upcomingDateNeverShowsAPastStartBeforeFirstGeneration() {
        val today = LocalDate.of(2026, 10, 11)
        // 新建的每月规则，开始日期在上个月：还没生成过，“下次”应是今天以后的那一次，而不是 9 月 1 日
        val monthly = rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 9, 1))
        assertEquals(LocalDate.of(2026, 9, 1), RecurringMath.nextDate(monthly))
        assertEquals(LocalDate.of(2026, 11, 1), RecurringMath.upcomingDate(monthly, today))
        // 今天就是其中一次：显示今天
        assertEquals(today, RecurringMath.upcomingDate(rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 8, 11)), today))
        // 开始日期在将来：就是开始日期
        assertEquals(LocalDate.of(2026, 12, 1), RecurringMath.upcomingDate(rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 12, 1)), today))
        // 已经生成到 10 月：下次 11 月
        assertEquals(LocalDate.of(2026, 11, 1), RecurringMath.upcomingDate(monthly.copy(nextIndex = 2), today))
        // 每天的规则开始于很久以前
        assertEquals(today, RecurringMath.upcomingDate(rule(RecurFrequency.DAILY, LocalDate.of(2020, 1, 1)), today))
        // 结束日期已过：已结束
        assertNull(RecurringMath.upcomingDate(rule(RecurFrequency.WEEKLY, LocalDate.of(2026, 9, 1), end = LocalDate.of(2026, 10, 1)), today))
        // 月末规则：从 1 月 31 日开始，10 月落在 31 日
        assertEquals(LocalDate.of(2026, 10, 31), RecurringMath.upcomingDate(rule(RecurFrequency.MONTHLY, LocalDate.of(2026, 1, 31)), today))
    }
}
