package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.5.0-alpha03：借贷记录直接改（金额、类型、账户、对方）、按人改名 / 合并 / 修改欠款。
 * 仓库写入的就是 [DebtEdit] / [PersonEdit] 给出的 [DebtChange]（一个事务），这里在内存数据上验证结果：
 * 余额、别人欠我 / 我欠别人合计、收支统计（修改欠款不计入），以及撤销能回到改之前。合成数据。
 */
class DebtEditTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 12)
    private fun at(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at(today, 20)

    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, openingBalance = 1_000_00, position = 0)
    private val bank = LedgerAccount("a-bank", "银行卡", AccountKind.BANK, openingBalance = 5_000_00, position = 1)
    private val card = LedgerAccount("a-cc", "示例信用卡", AccountKind.CREDIT, position = 2, credit = CreditSettings(1, 10))
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, position = 3)
    private val zhou = LedgerAccount("a-zhou", "借出 · 小周", AccountKind.RECEIVABLE, position = 4)
    private val zhang = LedgerAccount("a-zhang", "借入 · 老张", AccountKind.PAYABLE, position = 5)
    private val imported = LedgerAccount("a-imp", "借出 · 阿明", AccountKind.RECEIVABLE, position = 6)

    private val lend = LedgerLoan("ln-1", li.id, LoanDirection.LEND, today.minusDays(30), dueDate = today.plusDays(30), createdAt = 1L)
    private val zhouLoan = LedgerLoan("ln-2", zhou.id, LoanDirection.LEND, today.minusDays(20), createdAt = 2L)
    private val borrow = LedgerLoan("ln-3", zhang.id, LoanDirection.BORROW, today.minusDays(10), createdAt = 3L)

    private val tLend = LedgerTransaction("t-lend", TxType.TRANSFER, 500_00, at(today.minusDays(30)), cash.id, toAccountId = li.id, note = "借出 · 小李", createdAt = 10L, debtId = lend.id)
    private val tCollect = LedgerTransaction("t-col", TxType.TRANSFER, 200_00, at(today.minusDays(5)), li.id, toAccountId = cash.id, note = "收款 · 小李", createdAt = 20L, debtId = lend.id)
    private val tInterest = LedgerTransaction("t-int", TxType.INCOME, 10_00, at(today.minusDays(5)), cash.id, categoryId = "cat-other-income", note = "利息 · 小李", createdAt = 20L, debtId = lend.id)
    private val tZhou = LedgerTransaction("t-zhou", TxType.TRANSFER, 100_00, at(today.minusDays(20)), bank.id, toAccountId = zhou.id, note = "借出 · 小周", createdAt = 30L, debtId = zhouLoan.id)
    private val tBorrow = LedgerTransaction("t-bor", TxType.TRANSFER, 1_000_00, at(today.minusDays(10)), zhang.id, toAccountId = bank.id, note = "借入 · 老张", createdAt = 40L, debtId = borrow.id)
    // 导入的（没有借贷 ID，按对方账户算）
    private val tImported = LedgerTransaction("t-imp", TxType.TRANSFER, 300_00, at(today.minusDays(60)), cash.id, toAccountId = imported.id, note = "借给阿明", createdAt = 50L)
    // 信用卡还款与普通支出：不是借贷记录
    private val tRepayCard = LedgerTransaction("t-cc", TxType.TRANSFER, 50_00, at(today.minusDays(2)), bank.id, toAccountId = card.id, note = "还款 · 示例信用卡", createdAt = 60L)
    private val tFood = LedgerTransaction("t-food", TxType.EXPENSE, 30_00, at(today.minusDays(1)), cash.id, categoryId = "cat-food", createdAt = 70L)

    private val data = LedgerData(
        accounts = listOf(cash, bank, card, li, zhou, zhang, imported),
        categories = LedgerDefaults.categories(),
        transactions = listOf(tLend, tCollect, tInterest, tZhou, tBorrow, tImported, tRepayCard, tFood),
        loans = listOf(lend, zhouLoan, borrow),
    )
    private var seq = 0
    private val ids = { "n-${++seq}" }

    private fun overview(d: LedgerData) = DebtMath.overview(d, today, zone)
    private fun balances(d: LedgerData) = LedgerMath.balances(d.accounts, d.transactions)
    private fun person(d: LedgerData, id: String) = PersonDebtMath.person(DebtMath.loans(d, zone), id, today)
    private fun record(d: LedgerData, txId: String) = DebtEdit.recordOf(d, txId, zone)!!
    private fun edit(r: DebtRecord, type: DebtRecordType = r.type, personId: String? = r.person.id, name: String = DebtMath.personOf(r.person), amount: Long = r.amount, interest: Long = r.interest, at: Long = r.at, fund: String? = r.fundId, due: LocalDate? = r.due, note: String = r.note) =
        DebtRecordEdit(type, personId, name, amount, interest, at, fund, due, note)
    private fun apply(d: LedgerData, c: DebtChange) = c.applyTo(d, now)
    private fun sameTxs(a: LedgerData, b: LedgerData) = assertEquals(a.transactions.sortedBy { it.id }, b.transactions.sortedBy { it.id })

    @Test
    fun recordsAreFoundForEveryLoanFlowAndNotForOtherFlows() {
        val r = record(data, tCollect.id)
        assertEquals(DebtRecordType.COLLECT, r.type)
        assertEquals(tInterest.id, r.interestTx?.id)
        assertEquals(cash.id, r.fundId)
        // 点利息那一行也是同一条收款
        assertEquals(tCollect.id, record(data, tInterest.id).main?.id)
        assertEquals(DebtRecordType.LEND, record(data, tLend.id).type)
        assertTrue(record(data, tLend.id).ownsLoan)
        assertEquals(DebtRecordType.BORROW, record(data, tBorrow.id).type)
        assertFalse(record(data, tImported.id).loan.stored)
        // 信用卡还款、普通支出照旧用记一笔面板
        assertNull(DebtEdit.recordOf(data, tRepayCard.id, zone))
        assertNull(DebtEdit.recordOf(data, tFood.id, zone))
    }

    @Test
    fun editPrincipalAmountUpdatesBalancesAndTotalsAndUndoRestores() {
        assertEquals(300_00L + 100_00 + 300_00, overview(data).receivable)
        val r = record(data, tLend.id)
        val change = DebtEdit.plan(data, r, edit(r, amount = 800_00), now, zone, ids)
        val after = apply(data, change)
        assertEquals(600_00L, person(after, li.id)!!.remaining)
        assertEquals(600_00L + 100_00 + 300_00, overview(after).receivable)
        // 现金：1000 − 800 + 200 + 10（利息）− 300（阿明）− 30（吃饭）
        assertEquals(1_000_00L - 800_00 + 200_00 + 10_00 - 300_00 - 30_00, balances(after)[cash.id])
        // 同一笔流水被改，不是删了重记
        assertEquals(800_00L, after.transactions.single { it.id == tLend.id }.amount)
        assertEquals(LedgerMath.summary(data.transactions), LedgerMath.summary(after.transactions))
        sameTxs(data, apply(after, change.inverse(data)))
    }

    @Test
    fun editAccountMovesMoneyBetweenAccountsAndNoFundKeepsBalances() {
        val r = record(data, tLend.id)
        val toBank = apply(data, DebtEdit.plan(data, r, edit(r, fund = bank.id), now, zone, ids))
        assertEquals(balances(data)[cash.id]!! + 500_00, balances(toBank)[cash.id])
        assertEquals(balances(data)[bank.id]!! - 500_00, balances(toBank)[bank.id])
        assertEquals(overview(data).receivable, overview(toBank).receivable)
        // 不经过账户：现金加回来，欠款不变，新建停用的“余额调整”
        val noFund = DebtEdit.plan(data, r, edit(r, fund = null), now, zone, ids)
        assertTrue(noFund.accounts.any { it.kind == AccountKind.ADJUST && it.archived })
        val after = apply(data, noFund)
        assertEquals(balances(data)[cash.id]!! + 500_00, balances(after)[cash.id])
        assertEquals(balances(data)[bank.id], balances(after)[bank.id])
        assertEquals(overview(data).receivable, overview(after).receivable)
        assertNull(record(after, tLend.id).fundId)
    }

    @Test
    fun editPaymentAmountTypeAndInterest() {
        val r = record(data, tCollect.id)
        // 收款 200 → 250，利息 10 → 0：利息那笔删掉
        val c1 = DebtEdit.plan(data, r, edit(r, amount = 250_00, interest = 0), now, zone, ids)
        assertEquals(listOf(tInterest.id), c1.deleteTransactions)
        val a1 = apply(data, c1)
        assertEquals(250_00L, person(a1, li.id)!!.repaid)
        assertEquals(250_00L, person(a1, li.id)!!.remaining)
        assertEquals(0L, LedgerMath.summary(a1.transactions).income)
        // 超过还欠的（还欠 500）：多出来的 100 记成利息，同钱迹
        val over = apply(data, DebtEdit.plan(data, r, edit(r, amount = 600_00), now, zone, ids))
        assertEquals(0L, person(over, li.id)!!.remaining)
        assertEquals(110_00L, person(over, li.id)!!.interestPaid)
        assertEquals(110_00L, LedgerMath.summary(over.transactions).income)
        assertEquals(balances(data)[cash.id]!! + 300_00 + 100_00, balances(over)[cash.id])
        // 收款改成借出：小李欠我 500 + 200 = 700（成了一笔新的借出）
        val c2 = DebtEdit.plan(data, r, edit(r, type = DebtRecordType.LEND, interest = 0), now, zone, ids)
        val a2 = apply(data, c2)
        assertEquals(700_00L, person(a2, li.id)!!.remaining)
        assertEquals(2, a2.loans.count { it.accountId == li.id })
        assertEquals(DebtRecordType.LEND, record(a2, tCollect.id).type)
        assertEquals(1_000_00L - 500_00 - 200_00 - 300_00 - 30_00, balances(a2)[cash.id])
    }

    @Test
    fun editPersonOfALoanWithoutCollectionsAndLockWhenCollected() {
        // 小李那笔已经收过钱：不能换人（同随手记），提示用合并
        val r = record(data, tLend.id)
        assertThrows(IllegalArgumentException::class.java) { DebtEdit.plan(data, r, edit(r, personId = zhou.id, name = "小周"), now, zone, ids) }
        assertThrows(IllegalArgumentException::class.java) { DebtEdit.plan(data, r, edit(r, type = DebtRecordType.BORROW), now, zone, ids) }
        // 本金不能少于已收的 200
        assertThrows(IllegalArgumentException::class.java) { DebtEdit.plan(data, r, edit(r, amount = 150_00), now, zone, ids) }
        // 小周那笔还没收过：换成小李
        val z = record(data, tZhou.id)
        val toLi = apply(data, DebtEdit.plan(data, z, edit(z, personId = li.id, name = "小李"), now, zone, ids))
        assertNull(person(toLi, zhou.id))
        assertEquals(300_00L + 100_00, person(toLi, li.id)!!.remaining)
        assertEquals(overview(data).receivable, overview(toLi).receivable)
        assertEquals(balances(data)[bank.id], balances(toLi)[bank.id])
        // 写一个新名字：新建“借出 · 小王”
        val change = DebtEdit.plan(data, z, edit(z, personId = null, name = "小王"), now, zone, ids)
        val wang = change.accounts.single { it.name == "借出 · 小王" }
        val toWang = apply(data, change)
        assertEquals(100_00L, person(toWang, wang.id)!!.remaining)
        assertEquals(AccountKind.RECEIVABLE, wang.kind)
    }

    @Test
    fun flipBorrowToLend() {
        val r = record(data, tBorrow.id)
        val change = DebtEdit.plan(data, r, edit(r, type = DebtRecordType.LEND, personId = zhang.id), now, zone, ids)
        val after = apply(data, change)
        val lendZhang = change.accounts.single { it.kind == AccountKind.RECEIVABLE }
        assertEquals("借出 · 老张", lendZhang.name)
        assertEquals(1_000_00L, person(after, lendZhang.id)!!.remaining)
        assertEquals(0L, overview(after).payable)
        assertEquals(300_00L + 100_00 + 300_00 + 1_000_00, overview(after).receivable)
        // 银行：原来借入 +1000，现在借出 −1000
        assertEquals(balances(data)[bank.id]!! - 2_000_00, balances(after)[bank.id])
        assertEquals(DebtRecordType.LEND, record(after, tBorrow.id).type)
    }

    @Test
    fun principalWithRepaymentsCannotTurnIntoPaymentButDeleteTakesAllAndUndoes() {
        val r = record(data, tLend.id)
        assertThrows(IllegalArgumentException::class.java) {
            DebtEdit.plan(data, r, edit(r, type = DebtRecordType.COLLECT), now, zone, ids)
        }
        assertEquals(2, DebtEdit.relatedCount(r))
        val del = DebtEdit.deletePlan(r)
        val after = apply(data, del)
        assertNull(person(after, li.id))
        assertTrue(after.loans.none { it.id == lend.id })
        assertTrue(after.tombstones.any { it.id == lend.id && it.kind == LedgerTombstone.KIND_DEBT })
        assertEquals(1_000_00L - 300_00 - 30_00, balances(after)[cash.id])
        val back = apply(after, del.inverse(data))
        sameTxs(data, back)
        assertEquals(data.loans.sortedBy { it.id }, back.loans.sortedBy { it.id })
        assertTrue(back.tombstones.none { it.id == lend.id || it.id == tLend.id })
    }

    @Test
    fun importedRecordIsEditableAndRemembersDue() {
        val r = record(data, tImported.id)
        val due = today.plusDays(10)
        val change = DebtEdit.plan(data, r, edit(r, amount = 350_00, due = due), now, zone, ids)
        val after = apply(data, change)
        assertEquals(350_00L, person(after, imported.id)!!.remaining)
        assertEquals(due, person(after, imported.id)!!.due)
        assertTrue(after.loans.single { it.accountId == imported.id }.legacy)
        assertEquals(350_00L, after.transactions.single { it.id == tImported.id }.amount)
        assertNull(after.transactions.single { it.id == tImported.id }.debtId)
    }

    @Test
    fun renameAndMergePeople() {
        val renamed = apply(data, PersonEdit.rename(data, li.id, "李雷", now))
        assertEquals("借出 · 李雷", renamed.accounts.single { it.id == li.id }.name)
        // 默认备注里的名字一起换
        assertEquals("收款 · 李雷", renamed.transactions.single { it.id == tCollect.id }.note)
        assertThrows(IllegalArgumentException::class.java) { PersonEdit.rename(data, li.id, "小周", now) }

        val merge = PersonEdit.merge(data, li.id, zhou.id, now)
        val merged = apply(data, merge)
        assertTrue(merged.accounts.none { it.id == li.id })
        assertEquals(400_00L, person(merged, zhou.id)!!.remaining)
        assertEquals(overview(data).receivable, overview(merged).receivable)
        assertEquals(balances(data)[cash.id], balances(merged)[cash.id])
        assertEquals(LedgerMath.summary(data.transactions), LedgerMath.summary(merged.transactions))
        // 不同一边的不能合并
        assertThrows(IllegalArgumentException::class.java) { PersonEdit.merge(data, li.id, zhang.id, now) }
        val back = apply(merged, merge.inverse(data))
        sameTxs(data, back)
        assertEquals(data.accounts.sortedBy { it.id }, back.accounts.sortedBy { it.id })
    }

    @Test
    fun mergeMovesImportedOpeningBalanceAndLegacyInterest() {
        val ming2 = LedgerAccount("a-ming2", "借出 · 明", AccountKind.RECEIVABLE, openingBalance = 50_00, position = 9)
        val legacyInterest = LedgerTransaction("t-li", TxType.INCOME, 5_00, at(today.minusDays(3)), cash.id, note = "利息 · 明", createdAt = 80L, debtId = DebtMath.legacyLoanId(ming2.id))
        val d = data.copy(accounts = data.accounts + ming2, transactions = data.transactions + legacyInterest)
        assertEquals(50_00L, person(d, ming2.id)!!.remaining)
        val merged = apply(d, PersonEdit.merge(d, ming2.id, imported.id, now))
        assertEquals(350_00L, person(merged, imported.id)!!.remaining)
        assertEquals(5_00L, person(merged, imported.id)!!.interestPaid)
    }

    @Test
    fun setOwedRecordsANotCountedCorrection() {
        val before = person(data, li.id)!!.remaining
        assertEquals(300_00L, before)
        val down = PersonEdit.setOwed(data, li.id, 100_00, at(today), now, zone, ids)
        val a1 = apply(data, down)
        assertEquals(100_00L, person(a1, li.id)!!.remaining)
        assertEquals(100_00L + 100_00 + 300_00, overview(a1).receivable)
        // 不计入收支，不改任何资金账户
        assertEquals(LedgerMath.summary(data.transactions), LedgerMath.summary(a1.transactions))
        for (a in listOf(cash, bank, card)) assertEquals(balances(data)[a.id], balances(a1)[a.id])
        val tx = down.transactions.single()
        assertEquals(PersonEdit.CORRECTION_NOTE, tx.note)
        assertTrue(record(a1, tx.id).correction)
        // 改多：欠 900
        val up = PersonEdit.setOwed(a1, li.id, 900_00, at(today), now, zone, ids)
        val a2 = apply(a1, up)
        assertEquals(900_00L, person(a2, li.id)!!.remaining)
        assertEquals(LedgerMath.summary(data.transactions), LedgerMath.summary(a2.transactions))
        // 我欠老张 1000 → 0：我欠别人合计为 0
        val a3 = apply(a2, PersonEdit.setOwed(a2, zhang.id, 0, at(today), now, zone, ids))
        assertEquals(0L, overview(a3).payable)
        assertEquals(balances(data)[bank.id], balances(a3)[bank.id])
        // 一样的不记
        assertTrue(PersonEdit.setOwed(a3, zhang.id, 0, at(today), now, zone, ids).isEmpty)
        // 已经还清的人再改多：新记一笔借贷
        val a4 = apply(a3, PersonEdit.setOwed(a3, zhang.id, 200_00, at(today), now, zone, ids))
        assertEquals(200_00L, overview(a4).payable)
    }

    @Test
    fun writeOffKeepsItsTypeAndEditsAmount() {
        val off = LedgerTransaction("t-off", TxType.EXPENSE, 300_00, at(today.minusDays(1)), li.id, categoryId = "cat-other-expense", note = "免除 · 小李", createdAt = 90L, debtId = lend.id)
        val d = data.copy(transactions = data.transactions + off)
        assertEquals(0L, person(d, li.id)!!.remaining)
        val r = record(d, off.id)
        assertEquals(DebtRecordType.WRITE_OFF, r.type)
        val after = apply(d, DebtEdit.plan(d, r, edit(r, amount = 100_00), now, zone, ids))
        assertEquals(200_00L, person(after, li.id)!!.remaining)
        assertThrows(IllegalArgumentException::class.java) { DebtEdit.plan(d, r, edit(r, type = DebtRecordType.COLLECT), now, zone, ids) }
    }

    @Test
    fun interestOnlyPaymentAndDatesAreEditable() {
        val only = LedgerTransaction("t-only", TxType.INCOME, 20_00, at(today.minusDays(2)), cash.id, note = "利息 · 小李", createdAt = 95L, debtId = lend.id)
        val d = data.copy(transactions = data.transactions + only)
        val r = record(d, only.id)
        assertNull(r.main)
        assertEquals(DebtRecordType.COLLECT, r.type)
        val newAt = at(today.minusDays(4), 9)
        val after = apply(d, DebtEdit.plan(d, r, edit(r, amount = 50_00, interest = 20_00, at = newAt), now, zone, ids))
        assertEquals(250_00L, person(after, li.id)!!.remaining)
        val moved = after.transactions.filter { it.occurredAt == newAt }
        assertEquals(2, moved.size)
        assertNotNull(record(after, moved.first().id).interestTx)
    }

    @Test
    fun deletePersonRemovesEverythingAndUndoRestores() {
        val c = PersonEdit.deletePerson(data, li.id, now)
        val after = apply(data, c)
        assertTrue(after.accounts.none { it.id == li.id })
        assertTrue(after.transactions.none { it.id in setOf(tLend.id, tCollect.id, tInterest.id) })
        assertTrue(after.loans.none { it.accountId == li.id })
        // 现金余额回到没借过的样子
        assertEquals(balances(data)[cash.id]!! + 500_00 - 200_00 - 10_00, balances(after)[cash.id])
        val back = apply(after, c.inverse(data))
        sameTxs(data, back)
        assertTrue(back.accounts.any { it.id == li.id })
    }

    @Test
    fun deleteWholeLoanIncludingImported() {
        val view = DebtMath.loans(data, zone).first { it.account.id == imported.id }
        val after = apply(data, DebtEdit.deleteLoanPlan(data, view))
        assertTrue(after.transactions.none { it.id == tImported.id })
        assertTrue(DebtMath.loans(after, zone).none { it.account.id == imported.id })
    }
}
