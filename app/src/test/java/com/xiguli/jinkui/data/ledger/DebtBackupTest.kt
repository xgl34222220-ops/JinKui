package com.xiguli.jinkui.data.ledger

import com.xiguli.jinkui.core.crypto.BackupFormatException
import com.xiguli.jinkui.data.backup.BackupJson
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.4 备份 schema 5：账单设置、分期、借贷与流水归属进备份；schema 4 的旧备份照常导入；合并时尊重删除标记。合成数据。 */
class DebtBackupTest {
    private val card = LedgerAccount(
        "a-cc", "招行信用卡", AccountKind.CREDIT, updatedAt = 5,
        credit = CreditSettings(statementDay = 31, dueDay = 20, dueMode = DueMode.AFTER_STATEMENT, limit = 20_000_00),
    )
    private val bank = LedgerAccount("a-bank", "银行卡", AccountKind.BANK, updatedAt = 5)
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, updatedAt = 5)
    private val plan = LedgerInstallment(
        "in-1", card.id, "手机", 6_000_00, 12, feePerPeriod = 36_00, feeRateBp = 60,
        firstDate = LocalDate.of(2026, 10, 3), startDate = LocalDate.of(2026, 10, 1),
        payoffDate = LocalDate.of(2027, 1, 5), note = "分期备注", createdAt = 1, updatedAt = 7,
    )
    private val loan = LedgerLoan(
        "ln-1", li.id, LoanDirection.LEND, LocalDate.of(2026, 9, 1), dueDate = LocalDate.of(2026, 12, 31),
        interest = 30_00, note = "周转", createdAt = 1, updatedAt = 8,
    )
    private val data = LedgerData(
        accounts = listOf(card, bank, li),
        transactions = listOf(
            LedgerTransaction("t-1", TxType.EXPENSE, 6_000_00, 1, card.id, debtId = plan.id, updatedAt = 3),
            LedgerTransaction("t-2", TxType.TRANSFER, 500_00, 2, bank.id, li.id, debtId = loan.id, updatedAt = 3),
        ),
        installments = listOf(plan),
        loans = listOf(loan),
        tombstones = listOf(LedgerTombstone("ln-gone", LedgerTombstone.KIND_DEBT, 9)),
    )

    @Test
    fun schemaFiveRoundTripsDebts() {
        val bytes = BackupJson.encode(emptyList(), 1L, data)
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        assertEquals(6, root.getInt("schema"))
        assertEquals(1, root.getJSONObject("ledger").getJSONArray("installments").length())
        val back = BackupJson.decodeAll(bytes).ledger
        assertEquals(data, back)
        assertEquals(card.credit, back.accounts.first { it.id == card.id }.credit)
        assertEquals(plan.id, back.transactions.first { it.id == "t-1" }.debtId)
    }

    @Test
    fun databasePayloadsRoundTrip() {
        val account = LedgerJson.readAccountPayload(LedgerJson.accountPayload(card))
        assertEquals(card.credit, account.credit)
        assertNull(LedgerJson.readAccountPayload(LedgerJson.accountPayload(bank)).credit)
        val tx = LedgerJson.readTransactionPayload(LedgerJson.transactionPayload(data.transactions[1]))
        assertEquals(loan.id, tx.debtId)
        assertEquals(plan, LedgerJson.readInstallment(plan.id, plan.updatedAt, JSONObject(LedgerJson.debtPayload(plan))))
        assertEquals(loan, LedgerJson.readLoan(loan.id, loan.updatedAt, JSONObject(LedgerJson.debtPayload(loan))))
        // 1.3 写入的账户载荷没有 credit：读出来为空；损坏的 credit 当作没有设置
        assertNull(LedgerJson.readAccountPayload("""{"name":"花呗","kind":"credit","opening":0}""").credit)
        assertNull(LedgerJson.readAccountPayload("""{"name":"花呗","kind":"credit","credit":{"statementDay":40,"dueDay":1}}""").credit)
    }

    @Test
    fun schemaFourBackupsStillImport() {
        // 1.3 导出的备份：schema 4，没有分期、借贷与账单设置
        val old = """{"app":"jinkui","schema":4,"cards":[],"ledger":{
            "accounts":[{"id":"a-hb","name":"花呗","kind":"credit","opening":0,"cardId":null,"archived":false,"position":0,"createdAt":1,"updatedAt":1}],
            "transactions":[{"id":"t-9","type":"expense","amount":100,"occurredAt":1,"accountId":"a-hb","toAccountId":null,"categoryId":null,"note":"","tags":[],"createdAt":1,"updatedAt":1}],
            "tombstones":[]}}"""
        val ledger = BackupJson.decodeAll(old.toByteArray()).ledger
        assertNull(ledger.accounts.single().credit)
        assertNull(ledger.transactions.single().debtId)
        assertTrue(ledger.installments.isEmpty())
        assertTrue(ledger.loans.isEmpty())
        // 更新版本的备份明确拒绝
        assertThrows(BackupFormatException::class.java) { BackupJson.decodeAll("""{"app":"jinkui","schema":${BackupJson.SCHEMA + 1},"cards":[]}""".toByteArray()) }
    }

    @Test
    fun rejectsTamperedDebts() {
        fun withLedger(json: String) = """{"app":"jinkui","schema":5,"cards":[],"ledger":$json}""".toByteArray()
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"installments":[{"id":"../x","accountId":"a","title":"t","total":100,"periods":3,"first":"2026-10-01"}]}"""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"installments":[{"id":"i","accountId":"a","title":"t","total":100,"periods":0,"first":"2026-10-01"}]}"""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"loans":[{"id":"l","accountId":"a","direction":"lend","date":"2026-13-01"}]}"""))
        }
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decodeAll(withLedger("""{"accounts":[{"id":"a","name":"x","kind":"credit","credit":{"statementDay":0,"dueDay":1}}]}"""))
        }
    }

    @Test
    fun mergeHonorsDebtTombstonesAndNewerVersions() {
        val local = LedgerData(accounts = listOf(card, li), tombstones = listOf(LedgerTombstone(plan.id, LedgerTombstone.KIND_DEBT, 9)))
        val plan2 = plan.copy(updatedAt = 99)
        val merged = LedgerMerge.plan(local, data.copy(installments = listOf(plan2)))
        // 本机删掉的分期不会从备份加回来；借贷是新的，加进来
        assertTrue(merged.toWrite.installments.isEmpty())
        assertEquals(listOf(loan), merged.toWrite.loans)
        val newer = LedgerMerge.plan(data, data.copy(loans = listOf(loan.copy(dueDate = null, updatedAt = 50))))
        assertEquals(null, newer.toWrite.loans.single().dueDate)
        val older = LedgerMerge.plan(data, data.copy(loans = listOf(loan.copy(updatedAt = 1))))
        assertTrue(older.toWrite.loans.isEmpty())
    }
}
