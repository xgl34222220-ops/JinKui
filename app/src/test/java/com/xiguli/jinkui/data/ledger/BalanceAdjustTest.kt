package com.xiguli.jinkui.data.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.5.0-alpha01 修改余额：现金 10000 直接改成 0，余额正好是 0；默认不计入收支（月度收入、支出不变），
 * “余额调整”内部账户不计入净资产；也可以把差额记成收入 / 支出；信用卡按“现在欠多少”改；删掉那一笔即撤销。合成数据。
 */
class BalanceAdjustTest {
    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, openingBalance = 10_000_00, position = 0)
    private val bank = LedgerAccount("a-bank", "银行卡", AccountKind.BANK, openingBalance = 500_00, position = 1)
    private val card = LedgerAccount("a-cc", "招行信用卡", AccountKind.CREDIT, openingBalance = -1_200_00, position = 2)
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, position = 3)
    private val otherOut = LedgerCategory("c-out", "其他支出", TxType.EXPENSE, position = 9)
    private val food = LedgerCategory("c-food", "餐饮", TxType.EXPENSE, position = 0)
    private val otherIn = LedgerCategory("c-in", "其他收入", TxType.INCOME, position = 5)
    private val lunch = LedgerTransaction("t-1", TxType.EXPENSE, 32_50, 1_000L, cash.id, categoryId = food.id)
    private val data = LedgerData(accounts = listOf(cash, bank, card, li), categories = listOf(food, otherOut, otherIn), transactions = listOf(lunch))
    private var seq = 0
    private fun id() = "id-${++seq}"

    private fun apply(d: LedgerData, plan: BalanceAdjustPlan) =
        d.copy(accounts = d.accounts + listOfNotNull(plan.newAdjustAccount), transactions = d.transactions + plan.transaction)

    private fun netWorth(d: LedgerData): Long {
        val b = LedgerMath.balances(d.accounts, d.transactions)
        return d.accounts.filter { it.kind.countsInAssets }.sumOf { b[it.id] ?: 0L }
    }

    @Test
    fun cashTenThousandToZeroNotCounted() {
        val current = LedgerMath.balances(data.accounts, data.transactions)[cash.id]!!
        assertEquals(9_967_50L, current)
        val plan = BalanceAdjust.plan(data, cash.id, current, 0L, BalanceAdjustMode.NOT_COUNTED, 2_000L, 2_000L, ::id)!!
        val after = apply(data, plan)
        assertEquals(0L, LedgerMath.balances(after.accounts, after.transactions)[cash.id])
        // 不计入收支：月度收入 / 支出与改之前一样
        assertEquals(LedgerMath.summary(data.transactions), LedgerMath.summary(after.transactions))
        assertEquals(TxType.TRANSFER, plan.transaction.type)
        assertEquals(9_967_50L, plan.transaction.amount)
        assertEquals(BalanceAdjust.NOTE, plan.transaction.note)
        // 第一次修改余额时新建停用的“余额调整”账户；它不计入净资产
        assertNotNull(plan.newAdjustAccount)
        val adjust = plan.newAdjustAccount!!
        assertEquals(AccountKind.ADJUST, adjust.kind)
        assertTrue(adjust.archived)
        assertEquals(netWorth(data) - 9_967_50L, netWorth(after))
        assertNull(LedgerMath.validate(plan.transaction, after.accounts))
        // 流水里显示成“余额调整 -¥9,967.50”：真实账户是现金，影响是负的
        assertEquals(cash to -9_967_50L, BalanceAdjust.adjustmentOf(plan.transaction, after.accounts.associateBy { it.id }))
        // 删掉这一笔就撤销
        val undone = after.copy(transactions = after.transactions.filterNot { it.id == plan.transaction.id })
        assertEquals(current, LedgerMath.balances(undone.accounts, undone.transactions)[cash.id])
    }

    @Test
    fun secondAdjustReusesAdjustAccountAndRaisesBalance() {
        val first = BalanceAdjust.plan(data, cash.id, 9_967_50L, 0L, BalanceAdjustMode.NOT_COUNTED, 2_000L, 2_000L, ::id)!!
        val d1 = apply(data, first)
        val second = BalanceAdjust.plan(d1, bank.id, 500_00L, 800_00L, BalanceAdjustMode.NOT_COUNTED, 3_000L, 3_000L, ::id)!!
        assertNull(second.newAdjustAccount)
        val d2 = apply(d1, second)
        assertEquals(800_00L, LedgerMath.balances(d2.accounts, d2.transactions)[bank.id])
        assertEquals(first.newAdjustAccount!!.id, second.transaction.accountId)
        assertEquals(bank to 300_00L, BalanceAdjust.adjustmentOf(second.transaction, d2.accounts.associateBy { it.id }))
    }

    @Test
    fun asIncomeOrExpenseUsesOtherCategories() {
        val down = BalanceAdjust.plan(data, cash.id, 9_967_50L, 9_000_00L, BalanceAdjustMode.AS_INCOME_EXPENSE, 2_000L, 2_000L, ::id)!!
        assertEquals(TxType.EXPENSE, down.transaction.type)
        assertEquals(967_50L, down.transaction.amount)
        assertEquals(otherOut.id, down.transaction.categoryId)
        assertNull(down.newAdjustAccount)
        val after = apply(data, down)
        assertEquals(9_000_00L, LedgerMath.balances(after.accounts, after.transactions)[cash.id])
        assertEquals(LedgerMath.summary(data.transactions).expense + 967_50L, LedgerMath.summary(after.transactions).expense)
        val up = BalanceAdjust.plan(data, bank.id, 500_00L, 650_00L, BalanceAdjustMode.AS_INCOME_EXPENSE, 2_000L, 2_000L, ::id)!!
        assertEquals(TxType.INCOME, up.transaction.type)
        assertEquals(otherIn.id, up.transaction.categoryId)
    }

    @Test
    fun creditCardSetsCurrentDebt() {
        // 欠 1200 → 实际欠 800：余额从 -1200 改成 -800
        val plan = BalanceAdjust.plan(data, card.id, -1_200_00L, -800_00L, BalanceAdjustMode.NOT_COUNTED, 2_000L, 2_000L, ::id)!!
        val after = apply(data, plan)
        assertEquals(-800_00L, LedgerMath.balances(after.accounts, after.transactions)[card.id])
        assertEquals(LedgerMath.summary(data.transactions), LedgerMath.summary(after.transactions))
    }

    @Test
    fun sameBalanceNothingAndLoanAccountsRefused() {
        assertNull(BalanceAdjust.plan(data, bank.id, 500_00L, 500_00L, BalanceAdjustMode.NOT_COUNTED, 2_000L, 2_000L, ::id))
        assertThrows(IllegalArgumentException::class.java) {
            BalanceAdjust.plan(data, li.id, 0L, 100_00L, BalanceAdjustMode.NOT_COUNTED, 2_000L, 2_000L, ::id)
        }
        // 不经过账户的借贷（对方是借贷对象）不算余额调整
        val adjust = LedgerAccount("a-adj", "余额调整", AccountKind.ADJUST, archived = true)
        val loanTx = LedgerTransaction("t-loan", TxType.TRANSFER, 100_00, 1L, adjust.id, toAccountId = li.id)
        assertNull(BalanceAdjust.adjustmentOf(loanTx, (data.accounts + adjust).associateBy { it.id }))
    }
}
