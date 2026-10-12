package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0-alpha04 借贷“不经过账户（只记欠款）”：以前的借款、早就花掉的钱，只记谁欠谁、欠多少——
 * 不产生资金账户的流水，资金账户余额一分不变，欠款总额照常；收款 / 还款同样可以不经过账户。
 * 也确认可选的资金账户是全部在用的资金账户（含信用卡 / 花呗），以及“记住上次的选择”。合成数据。
 */
class LoanNoFundTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 12)
    private val at = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, openingBalance = 50_00, position = 0)
    private val bank = LedgerAccount("a-bank", "银行卡", AccountKind.BANK, openingBalance = 2_000_00, position = 1)
    private val wechat = LedgerAccount("a-wx", "微信", AccountKind.WECHAT, position = 2)
    private val alipay = LedgerAccount("a-ali", "支付宝", AccountKind.ALIPAY, position = 3)
    private val huabei = LedgerAccount("a-hb", "花呗", AccountKind.CREDIT, position = 4)
    private val oldCard = LedgerAccount("a-old", "旧卡", AccountKind.BANK, archived = true, position = 5)
    private val zhang = LedgerAccount("a-zhang", "借入 · 老张", AccountKind.PAYABLE, position = 6)
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, position = 7)
    private val base = listOf(cash, bank, wechat, alipay, huabei, oldCard, zhang, li)
    private var seq = 0
    private fun id() = "id-${++seq}"

    @Test
    fun loanFundsListEveryActiveFundAccountIncludingCredit() {
        assertEquals(listOf(cash, bank, wechat, alipay, huabei), DebtMath.loanFunds(base))
        // 借贷对象、停用账户、“余额调整”都不在里面
        val adjust = LedgerAccount("a-adj", "余额调整", AccountKind.ADJUST, archived = true)
        assertTrue(DebtMath.loanFunds(base + adjust).none { it.kind == AccountKind.ADJUST || LoanDirection.of(it.kind) != null || it.archived })
    }

    @Test
    fun noFundBorrowAndLendCreateNoFundTransactionAndKeepBalances() {
        val before = LedgerMath.balances(base, emptyList())
        // 不经过账户：新建停用的“余额调整”账户
        val (fund, created) = DebtMath.loanFundAccount(base, null, 1L, ::id)
        assertNotNull(created)
        assertSame(fund, created)
        assertEquals(AccountKind.ADJUST, fund.kind)
        assertTrue(fund.archived)
        val accounts = base + fund
        // 第二次不再新建
        val (again, none) = DebtMath.loanFundAccount(accounts, null, 2L, ::id)
        assertEquals(fund.id, again.id)
        assertNull(none)

        val borrow = LedgerLoan("ln-zhang", zhang.id, LoanDirection.BORROW, today.minusDays(200))
        val lend = LedgerLoan("ln-li", li.id, LoanDirection.LEND, today.minusDays(90))
        val txs = listOf(
            DebtMath.loanPrincipalTx(id(), borrow, zhang, fund, 3_000_00, at, 1L),
            DebtMath.loanPrincipalTx(id(), lend, li, fund, 800_00, at, 1L),
        )
        val fundIds = DebtMath.loanFunds(accounts).mapTo(HashSet()) { it.id }
        // 没有任何一笔流水碰到资金账户
        assertTrue(txs.none { it.accountId in fundIds || it.toAccountId in fundIds })
        txs.forEach { assertNull(LedgerMath.validate(it, accounts)) }
        val after = LedgerMath.balances(accounts, txs)
        for (a in DebtMath.loanFunds(accounts)) assertEquals(a.name, before[a.id], after[a.id])
        // 欠款总额照常：我欠老张 3000、小李欠我 800
        assertEquals(-3_000_00L, after[zhang.id])
        assertEquals(800_00L, after[li.id])
        val data = LedgerData(accounts = accounts, categories = LedgerDefaults.categories(), transactions = txs, loans = listOf(borrow, lend))
        val overview = DebtMath.overview(data, today, zone)
        assertEquals(3_000_00L, overview.payable)
        assertEquals(800_00L, overview.receivable)
        // 净资产只多了欠款这一项（“余额调整”不计入）
        val counted = accounts.filter { it.kind.countsInAssets }.sumOf { after[it.id] ?: 0L }
        assertEquals(accounts.filter { it.kind.countsInAssets }.sumOf { before[it.id] ?: 0L } - 3_000_00 + 800_00, counted)
        // 上一次“不经过账户”，下一次默认还是不经过账户
        assertNull(DebtMath.lastLoanFund(data))

        // 还款也可以不经过账户：只减少欠款；另付的利息计入支出，但不改资金账户
        val pay = DebtMath.loanPaymentTxs(zhang, fund, 1_000_00, 50_00, at + 1, 2L, "", borrow.id, borrow.id, "cat-other-expense", ::id)
        assertEquals(2, pay.size)
        assertTrue(pay.none { it.accountId in fundIds || it.toAccountId in fundIds })
        val all = txs + pay
        val afterPay = LedgerMath.balances(accounts, all)
        for (a in DebtMath.loanFunds(accounts)) assertEquals(a.name, before[a.id], afterPay[a.id])
        assertEquals(-2_000_00L, afterPay[zhang.id])
        val view = DebtMath.loans(data.copy(transactions = all), zone).first { it.loan.id == borrow.id }
        assertEquals(3_000_00L, view.principal)
        assertEquals(1_000_00L, view.repaid)
        assertEquals(2_000_00L, view.remaining)
        assertEquals(50_00L, view.interestPaid)
    }

    @Test
    fun explicitFundStillMovesMoneyAndIsRemembered() {
        val (fund, created) = DebtMath.loanFundAccount(base, huabei.id, 1L, ::id)
        assertSame(huabei, fund)
        assertNull(created)
        // 不能把借贷对象当资金账户；找不到的账户报错
        assertThrows(IllegalArgumentException::class.java) { DebtMath.loanFundAccount(base, li.id, 1L, ::id) }
        assertThrows(IllegalArgumentException::class.java) { DebtMath.loanFundAccount(base, "nope", 1L, ::id) }
        val borrow = LedgerLoan("ln-zhang", zhang.id, LoanDirection.BORROW, today)
        val tx = DebtMath.loanPrincipalTx(id(), borrow, zhang, bank, 500_00, at, 5L)
        val after = LedgerMath.balances(base, listOf(tx))
        assertEquals(2_500_00L, after[bank.id])
        // 用花呗还：信用账户欠款增加，借入减少
        val pay = DebtMath.loanPaymentTxs(zhang, huabei, 200_00, 0, at + 1, 6L, "", borrow.id, borrow.id, null, ::id).single()
        val afterPay = LedgerMath.balances(base, listOf(tx, pay))
        assertEquals(-200_00L, afterPay[huabei.id])
        assertEquals(-300_00L, afterPay[zhang.id])
        // 记住上次的选择：最近一笔是花呗
        val data = LedgerData(accounts = base, transactions = listOf(tx, pay), loans = listOf(borrow))
        assertEquals(huabei.id, DebtMath.lastLoanFund(data))
        // 上次的账户停用后退回“不经过账户”
        assertNull(DebtMath.lastLoanFund(data.copy(accounts = base.map { if (it.id == huabei.id) it.copy(archived = true) else it })))
        assertNull(DebtMath.lastLoanFund(LedgerData(accounts = base)))
    }
}
