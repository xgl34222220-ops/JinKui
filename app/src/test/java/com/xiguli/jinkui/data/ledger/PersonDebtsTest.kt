package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0-alpha04 欠款 / 借贷按人汇总：同一个人名下的几笔借款合成一行（共借多少、已还多少、还欠多少、最早的约定还款日），
 * 没还清的在前；按人收款 / 还款时本金先冲最早借的那笔。合成数据。
 */
class PersonDebtsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 12)
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private val bank = LedgerAccount("a-bank", "银行卡", AccountKind.BANK, openingBalance = 10_000_00, position = 0)
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, position = 1)
    private val zhao = LedgerAccount("a-zhao", "借出 · 小赵", AccountKind.RECEIVABLE, position = 2)
    private val wang = LedgerAccount("a-wang", "借入 · 老王", AccountKind.PAYABLE, position = 3)

    private val li1 = LedgerLoan("ln-li1", li.id, LoanDirection.LEND, today.minusDays(60))
    private val li2 = LedgerLoan("ln-li2", li.id, LoanDirection.LEND, today.minusDays(10), dueDate = today.plusDays(5))
    private val zhaoLoan = LedgerLoan("ln-zhao", zhao.id, LoanDirection.LEND, today.minusDays(30), dueDate = today.minusDays(1))
    private val wangLoan = LedgerLoan("ln-wang", wang.id, LoanDirection.BORROW, today.minusDays(40), dueDate = today.plusDays(20))

    private var seq = 0
    private fun t(amount: Long, day: LocalDate, from: String, to: String, debt: String) =
        LedgerTransaction("t${++seq}", TxType.TRANSFER, amount, at(day), from, to, debtId = debt)

    private val data by lazy {
        seq = 0
        LedgerData(
            accounts = listOf(bank, li, zhao, wang),
            categories = LedgerDefaults.categories(),
            transactions = listOf(
                t(800_00, li1.date, bank.id, li.id, li1.id),
                t(300_00, today.minusDays(20), li.id, bank.id, li1.id),
                t(500_00, li2.date, bank.id, li.id, li2.id),
                t(200_00, zhaoLoan.date, bank.id, zhao.id, zhaoLoan.id),
                t(200_00, today.minusDays(2), zhao.id, bank.id, zhaoLoan.id),
                t(3_000_00, wangLoan.date, wang.id, bank.id, wangLoan.id),
                t(1_000_00, today.minusDays(5), bank.id, wang.id, wangLoan.id),
            ),
            loans = listOf(li1, li2, zhaoLoan, wangLoan),
        )
    }
    private val loans by lazy { DebtMath.loans(data, zone) }

    @Test
    fun onePersonPerAccountWithTotals() {
        val lent = PersonDebtMath.people(loans, LoanDirection.LEND, today)
        assertEquals(listOf("小李", "小赵"), lent.map { it.person })
        val p = lent.first()
        assertEquals(2, p.loans.size)
        assertEquals(1_300_00L, p.total)
        assertEquals(300_00L, p.repaid)
        assertEquals(1_000_00L, p.remaining)
        // 第一笔没填还款日，最早的约定还款日就是第二笔的
        assertEquals(today.plusDays(5), p.due)
        assertFalse(p.overdue(today))
        assertEquals(300f / 1300f, p.progress, 0.001f)
        // 来往记录：两笔借出、一笔收款，新的在前
        assertEquals(3, p.entries.size)
        assertTrue(p.entries.zipWithNext().all { (a, b) -> a.occurredAt >= b.occurredAt })
        // 小赵已还清：排在后面，逾期不算
        val z = lent.last()
        assertTrue(z.settled)
        assertFalse(z.overdue(today))

        val borrowed = PersonDebtMath.people(loans, LoanDirection.BORROW, today)
        assertEquals(listOf("老王"), borrowed.map { it.person })
        assertEquals(2_000_00L, borrowed.single().remaining)
        assertEquals(wang.id, PersonDebtMath.person(loans, wang.id, today)?.account?.id)
        assertNull(PersonDebtMath.person(loans, bank.id, today))
    }

    @Test
    fun paymentGoesToOldestLoanFirst() {
        val p = PersonDebtMath.person(loans, li.id, today)!!
        // 700：先把第一笔剩的 500 冲完，再冲第二笔 200
        val parts = PersonDebtMath.split(p, 700_00)!!
        assertEquals(listOf(li1.id to 500_00L, li2.id to 200_00L), parts.map { it.first.loan.id to it.second })
        assertEquals(listOf(li1.id to 500_00L), PersonDebtMath.split(p, 500_00)!!.map { it.first.loan.id to it.second })
        // 正好还清 / 超过还欠的
        assertEquals(1_000_00L, PersonDebtMath.split(p, 1_000_00)!!.sumOf { it.second })
        assertNull(PersonDebtMath.split(p, 1_000_01))
        // 只付利息
        assertTrue(PersonDebtMath.split(p, 0)!!.isEmpty())

        // 按这个拆法写出的流水：两笔都不经过资金账户以外的地方，冲完后小李只欠 300
        val fund = bank
        val txs = parts.flatMap { (loan, amount) ->
            DebtMath.loanPaymentTxs(li, fund, amount, 0, at(today), 1L, "", loan.loan.id, loan.loan.id, "cat-other-income")
        }
        val after = PersonDebtMath.person(DebtMath.loans(data.copy(transactions = data.transactions + txs), zone), li.id, today)!!
        assertEquals(300_00L, after.remaining)
        assertEquals(1_000_00L, after.repaid)
        assertTrue(after.loans.first { it.loan.id == li1.id }.settled)
    }
}
