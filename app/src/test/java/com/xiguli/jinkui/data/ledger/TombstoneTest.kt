package com.xiguli.jinkui.data.ledger

import com.xiguli.jinkui.core.crypto.BackupFormatException
import com.xiguli.jinkui.data.backup.BackupJson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 删除标记：用户删掉的周期生成流水 / 周期规则，不会被补记或导入旧备份“复活”。
 */
class TombstoneTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val cash = LedgerAccount("acc-cash", "现金", AccountKind.CASH, updatedAt = 1)
    private val rule = LedgerRecurring(
        id = "r-1", type = TxType.EXPENSE, amount = 350_000, accountId = cash.id, categoryId = "cat-housing-1",
        note = "房租", frequency = RecurFrequency.DAILY, startDate = LocalDate.of(2026, 10, 1), updatedAt = 10,
    )

    private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 12, 0).atZone(zone).toInstant().toEpochMilli()

    /** 10 月 1 日到 3 日各生成一笔，返回生成后的账本。 */
    private fun generated(): LedgerData {
        val base = LedgerData(accounts = listOf(cash), recurring = listOf(rule))
        val batch = RecurringMath.generate(base, at(2026, 10, 3), zone)
        assertEquals(3, batch.transactions.size)
        return base.copy(transactions = batch.transactions, recurring = batch.rules)
    }

    @Test
    fun generatedIdsAreRecognised() {
        assertTrue(RecurringMath.isGeneratedId(RecurringMath.transactionId("r-1", LocalDate.of(2026, 10, 2))))
        assertTrue(RecurringMath.isGeneratedId("r20261002-4f7d0c1e-1111-2222-3333-444455556666"))
        assertFalse(RecurringMath.isGeneratedId("4f7d0c1e-1111-2222-3333-444455556666"))
        assertFalse(RecurringMath.isGeneratedId("t-1"))
        assertFalse(RecurringMath.isGeneratedId("r2026-x"))
    }

    @Test
    fun oldBackupDoesNotResurrectDeletedGeneratedTransaction() {
        val backup = generated() // 备份时三笔都在
        val deletedId = RecurringMath.transactionId("r-1", LocalDate.of(2026, 10, 2))
        val local = backup.copy(
            transactions = backup.transactions.filterNot { it.id == deletedId },
            tombstones = listOf(LedgerTombstone(deletedId, LedgerTombstone.KIND_TX, 99)),
        )
        val plan = LedgerMerge.plan(local, backup)
        assertTrue(plan.toWrite.transactions.none { it.id == deletedId })
        assertEquals(1, plan.suppressed)
        assertEquals(0, plan.added)
        // 没有删除标记时（0.8.0 及更早的行为）会被加回来：确认标记确实在起作用
        val withoutTombstone = LedgerMerge.plan(local.copy(tombstones = emptyList()), backup)
        assertEquals(listOf(deletedId), withoutTombstone.toWrite.transactions.map { it.id })
    }

    @Test
    fun recurringDoesNotRegenerateDeletedDay() {
        val data = generated()
        val deletedId = RecurringMath.transactionId("r-1", LocalDate.of(2026, 10, 2))
        // 规则进度被旧备份退回到 0（例如备份里的规则更新时间更新）：已删的那天不能再生成
        val rewound = data.copy(
            transactions = data.transactions.filterNot { it.id == deletedId },
            recurring = listOf(rule.copy(nextIndex = 0)),
            tombstones = listOf(LedgerTombstone(deletedId, LedgerTombstone.KIND_TX, 99)),
        )
        val batch = RecurringMath.generate(rewound, at(2026, 10, 4), zone)
        assertEquals(listOf(RecurringMath.transactionId("r-1", LocalDate.of(2026, 10, 4))), batch.transactions.map { it.id })
    }

    @Test
    fun deletedRuleFromOldBackupIsNotRestoredNorGenerating() {
        val backup = generated()
        val local = LedgerData(
            accounts = listOf(cash),
            transactions = backup.transactions,
            tombstones = listOf(LedgerTombstone(rule.id, LedgerTombstone.KIND_RECURRING, 99)),
        )
        val plan = LedgerMerge.plan(local, backup)
        assertTrue(plan.toWrite.recurring.isEmpty())
        assertEquals(1, plan.suppressed)
        // 就算规则还在内存里（例如别处带回来的），有删除标记也不生成
        val stray = local.copy(recurring = listOf(rule))
        assertTrue(RecurringMath.generate(stray, at(2026, 10, 9), zone).isEmpty)
    }

    @Test
    fun incomingTombstonesAreRecordedButNeverDeleteLocalRecords() {
        val local = generated()
        val keptId = local.transactions.first().id
        val goneElsewhere = "r20260901-r-1"
        val incoming = LedgerData(
            tombstones = listOf(
                LedgerTombstone(keptId, LedgerTombstone.KIND_TX, 50),
                LedgerTombstone(goneElsewhere, LedgerTombstone.KIND_TX, 40),
                LedgerTombstone(goneElsewhere, LedgerTombstone.KIND_TX, 60),
            ),
        )
        val plan = LedgerMerge.plan(local, incoming)
        // 本机仍有的那笔不受影响，也不为它记标记；本机没有的记下来（同 ID 取最新）
        assertEquals(listOf(LedgerTombstone(goneElsewhere, LedgerTombstone.KIND_TX, 60)), plan.toWrite.tombstones)
        assertTrue(plan.toWrite.transactions.isEmpty())
        // 备份里同时带着那笔流水和它的标记（理论上不会出现）：按标记处理，不加回
        val both = LedgerMerge.plan(LedgerData(), incoming.copy(transactions = local.transactions.take(1)))
        assertTrue(both.toWrite.transactions.isEmpty())
    }

    @Test
    fun tombstonesRoundTripThroughBackupAndOldBackupsHaveNone() {
        val data = generated().copy(
            tombstones = listOf(
                LedgerTombstone("r20261002-r-1", LedgerTombstone.KIND_TX, 99),
                LedgerTombstone("r-9", LedgerTombstone.KIND_RECURRING, 100),
            ),
        )
        val bytes = BackupJson.encode(emptyList(), 1L, data)
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        assertEquals(5, root.getInt("schema"))
        assertEquals(data, BackupJson.decodeAll(bytes).ledger)

        // schema 3（0.7.0 / 0.8.0）的备份：没有 tombstones，照常导入
        val old = """{"app":"jinkui","schema":3,"cards":[],"ledger":{"accounts":[],"transactions":[]}}"""
        assertTrue(BackupJson.decodeAll(old.toByteArray()).ledger.tombstones.isEmpty())

        // 不认识的种类忽略；非法 ID 拒绝整份备份
        val unknownKind = """{"app":"jinkui","schema":4,"cards":[],"ledger":{"tombstones":[{"id":"x-1","kind":"future","deletedAt":1}]}}"""
        assertTrue(BackupJson.decodeAll(unknownKind.toByteArray()).ledger.tombstones.isEmpty())
        val badId = """{"app":"jinkui","schema":4,"cards":[],"ledger":{"tombstones":[{"id":"../x","kind":"tx","deletedAt":1}]}}"""
        assertThrows(BackupFormatException::class.java) { BackupJson.decodeAll(badId.toByteArray()) }
        // 更新版本（schema 6）的备份明确拒绝
        val future = """{"app":"jinkui","schema":6,"cards":[]}"""
        assertThrows(BackupFormatException::class.java) { BackupJson.decodeAll(future.toByteArray()) }
    }
}
