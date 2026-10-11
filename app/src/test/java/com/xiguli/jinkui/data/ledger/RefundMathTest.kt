package com.xiguli.jinkui.data.ledger

import com.xiguli.jinkui.data.backup.BackupJson
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0-alpha04 退款与报销：统计口径（退款冲减原支出、跨月冲减原支出所在月、报销的支出与到账不计收支），
 * 余额照实计算，校验，以及数据库载荷与备份 schema 6 的往返、schema 5 备份照常导入。
 */
class RefundMathTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(m: Int, d: Int) = LocalDateTime.of(2026, m, d, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private val bank = LedgerAccount("a-bank", "银行卡", AccountKind.BANK, openingBalance = 1_000_00)
    private val shoes = LedgerTransaction("e-shoes", TxType.EXPENSE, 300_00, at(9, 28), bank.id, categoryId = "cat-shopping", note = "鞋")
    private val lunch = LedgerTransaction("e-lunch", TxType.EXPENSE, 50_00, at(10, 2), bank.id, categoryId = "cat-food")
    private val taxi = LedgerTransaction("e-taxi", TxType.EXPENSE, 80_00, at(10, 3), bank.id, categoryId = "cat-transport", reimburse = Reimburse.PENDING)
    private val hotel = LedgerTransaction("e-hotel", TxType.EXPENSE, 400_00, at(10, 4), bank.id, categoryId = "cat-fun", reimburse = Reimburse.DONE)
    private val salary = LedgerTransaction("i-salary", TxType.INCOME, 5_000_00, at(10, 10), bank.id, categoryId = "cat-salary")
    /** 10 月退了 9 月那双鞋的 120（部分退款，跨月）。 */
    private val partial = LedgerTransaction("i-refund", TxType.INCOME, 120_00, at(10, 5), bank.id, categoryId = "cat-refund", refundOf = shoes.id)
    /** 酒店报销到账。 */
    private val back = LedgerTransaction("i-back", TxType.INCOME, 400_00, at(10, 8), bank.id, categoryId = "cat-other-income", reimburseOf = listOf(hotel.id))

    private val all = listOf(shoes, lunch, taxi, hotel, salary, partial, back)

    @Test
    fun statsNetRefundsAndDropReimbursements() {
        val stats = RefundMath.forStats(all)
        // 9 月：鞋按退款后 180 计；10 月：只有午饭 50 是自己的支出，收入只有工资
        assertEquals(Summary(180_00, 0), LedgerMath.summary(LedgerMath.inMonth(stats, YearMonth.of(2026, 9), zone)))
        assertEquals(Summary(50_00, 5_000_00), LedgerMath.summary(LedgerMath.inMonth(stats, YearMonth.of(2026, 10), zone)))
        // 分类占比同样按净额
        val shares = LedgerMath.categoryBreakdown(stats, LedgerDefaults.categories(), TxType.EXPENSE)
        assertEquals(180_00L, shares.first { it.categoryId == "cat-shopping" }.amount)
        assertTrue(shares.none { it.categoryId == "cat-transport" || it.categoryId == "cat-fun" })
        // 余额照实：1000 − 300 − 50 − 80 − 400 + 5000 + 120 + 400
        assertEquals(5_690_00L, LedgerMath.balances(listOf(bank), all).getValue(bank.id))
    }

    @Test
    fun fullRefundRemovesExpenseAndOrphanRefundCountsAsIncome() {
        val full = partial.copy(amount = 300_00)
        assertTrue(RefundMath.forStats(listOf(shoes, full)).isEmpty())
        assertTrue(RefundMath.status(shoes, listOf(shoes, full)).full)
        // 原支出删掉了：退款当作普通收入
        assertEquals(listOf(full), RefundMath.forStats(listOf(full)))
        // 两笔退款合计
        val second = partial.copy(id = "i-refund-2", amount = 100_00)
        val status = RefundMath.status(shoes, listOf(shoes, partial, second))
        assertEquals(220_00L, status.refunded)
        assertEquals(80_00L, status.remaining)
        assertTrue(status.partial)
        assertEquals(80_00L, RefundMath.forStats(listOf(shoes, partial, second)).single().amount)
        assertEquals(mapOf(shoes.id to 220_00L), RefundMath.refundTotals(listOf(shoes, partial, second, lunch)))
    }

    @Test
    fun noRefundsReturnsSameList() {
        val plain = listOf(lunch, salary)
        assertSame(plain, RefundMath.forStats(plain))
    }

    @Test
    fun validation() {
        assertNull(RefundMath.validateRefund(shoes, 180_00, all))
        assertEquals("退款金额超过了可退的 ¥180.00", RefundMath.validateRefund(shoes, 180_01, all))
        assertEquals("退款金额要大于 0", RefundMath.validateRefund(shoes, 0, all))
        assertEquals("只有支出可以退款", RefundMath.validateRefund(salary, 10, all))
        assertEquals("这笔已经标记为报销，不能再退款", RefundMath.validateRefund(taxi, 10, all))
        assertEquals("这笔已经有退款，不能再报销", RefundMath.validateReimburse(listOf(shoes.id), all))
        assertEquals("只有支出可以报销", RefundMath.validateReimburse(listOf(salary.id), all))
        assertEquals("分期和借贷的流水不能报销", RefundMath.validateReimburse(listOf("d"), listOf(lunch.copy(id = "d", debtId = "ln-1"))))
        assertNull(RefundMath.validateReimburse(listOf(lunch.id), all))
    }

    @Test
    fun reimbursementOverview() {
        val o = RefundMath.reimbursements(all)
        assertEquals(listOf(taxi.id), o.pending.map { it.id })
        assertEquals(listOf(hotel.id), o.done.map { it.id })
        assertEquals(80_00L, o.pendingTotal)
        assertEquals(400_00L, o.doneTotal)
        assertEquals(listOf(back), RefundMath.reimbursementIncomes(listOf(hotel.id), all))
    }

    @Test
    fun budgetUsesNetSpending() {
        val budget = LedgerBudget(LedgerBudget.TOTAL_ID, null, 1_000_00)
        val october = LedgerMath.inMonth(RefundMath.forStats(all), YearMonth.of(2026, 10), zone)
        assertEquals(50_00L, LedgerMath.budgetProgress(listOf(budget), october, LedgerDefaults.categories()).single().spent)
    }

    @Test
    fun payloadAndBackupRoundTrip() {
        for (tx in listOf(partial, back, taxi, hotel, lunch)) {
            val p = LedgerJson.readTransactionPayload(LedgerJson.transactionPayload(tx))
            assertEquals(tx.refundOf, p.refundOf)
            assertEquals(tx.reimburse, p.reimburse)
            assertEquals(tx.reimburseOf, p.reimburseOf)
        }
        // 没有退款 / 报销的载荷与以前完全一样（不多写字段）
        val plain = org.json.JSONObject(LedgerJson.transactionPayload(lunch))
        assertEquals(setOf("amount", "note", "tags"), plain.keys().asSequence().toSet())
        assertEquals(5000L, plain.getLong("amount"))
        val data = LedgerData(accounts = listOf(bank), categories = LedgerDefaults.categories(), transactions = all)
        val back6 = BackupJson.decodeAll(BackupJson.encode(emptyList(), 1L, data)).ledger
        assertEquals(all.sortedBy { it.id }, back6.transactions.sortedBy { it.id })
        // schema 5（1.4.0-alpha03）的备份：没有这些字段，照常导入
        val old = """{"app":"jinkui","schema":5,"cards":[],"ledger":{
            "transactions":[{"id":"t-9","type":"expense","amount":100,"occurredAt":1,"accountId":"a-bank","toAccountId":null,"categoryId":null,"note":"","tags":[],"debtId":null,"createdAt":1,"updatedAt":1}]}}"""
        val tx = BackupJson.decodeAll(old.toByteArray()).ledger.transactions.single()
        assertNull(tx.refundOf)
        assertEquals(Reimburse.NONE, tx.reimburse)
        assertTrue(tx.reimburseOf.isEmpty())
    }
}
