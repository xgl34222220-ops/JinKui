package com.xiguli.jinkui.data.importer

import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.CreditSettings
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.DueKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4.0-alpha02：导入一木记账的 .xls 后，“欠款 / 账单”里要看得到真正的欠款——花呗按默认账单日出账单、
 * 没设账单日的信用卡照样显示欠款、每个借贷对象的余额与部分还款、欠款合计。样例由 [XlsFixture.debtSample] 合成。
 */
class DebtImportTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 5)

    /** 像用户一样：空账本、默认勾选、导入。 */
    private fun imported(base: LedgerData = LedgerData(categories = LedgerDefaults.categories())): Pair<ImportPreview, LedgerData> {
        val table = TableReader.read(XlsFixture.xls(XlsFixture.debtSample()), "一木账单.xls")
        val parsed = BillParser.parse(table, BillParser.detect(table)!!)
        val preview = BillImport.preview(parsed, base, zone, now = 1L)
        val txs = BillImport.transactions(preview, zone, 2L, "batch")
        val accounts = base.accounts + preview.usedNewAccounts.mapIndexed { i, a -> a.copy(position = base.accounts.size + i) }
        return preview to base.copy(accounts = accounts, categories = base.categories + preview.usedNewCategories, transactions = base.transactions + txs)
    }

    @Test
    fun previewAndResultCountDebts() {
        val (preview, data) = imported()
        assertEquals(10, preview.selected.size)
        assertEquals(2, preview.creditAccountCount)
        assertEquals(2, preview.loanAccountCount)
        val result = BillImport.result(preview, data.transactions)
        assertEquals(2, result.creditAccounts)
        assertEquals(2, result.loanAccounts)
        assertNotNull(com.xiguli.jinkui.ui.ledger.debtLine(preview.creditAccountCount, preview.loanAccountCount))
        assertNull(com.xiguli.jinkui.ui.ledger.debtLine(0, 0))
    }

    @Test
    fun creditAccountsShowRealDebtAndDefaultHuabeiStatements() {
        val (_, data) = imported()
        val o = DebtMath.overview(data, today, zone)
        assertEquals(2, o.credits.size)
        val hb = o.credits.first { it.account.name == "花呗" }
        // 花呗没设账单日：按一木的默认 1 号出账、10 号还款，标为默认
        assertTrue(hb.defaulted)
        assertEquals(CreditSettings(1, 10), hb.settings)
        assertEquals(50_00L, hb.debt)
        // 9 月 1 日账单 200（8 月两笔），9 月 8 日还清；10 月 1 日账单 50 待还
        val sep = hb.statements.first { it.statementDate == LocalDate.of(2026, 9, 1) }
        assertEquals(200_00L, sep.owed)
        assertTrue(sep.cleared)
        val oct = hb.latest!!
        assertEquals(LocalDate.of(2026, 10, 1), oct.statementDate)
        assertEquals(LocalDate.of(2026, 10, 10), oct.dueDate)
        assertEquals(50_00L, oct.remaining)
        assertFalse(oct.cleared)

        // 没设账单日的信用卡：不消失，显示欠款，没有账单
        val card = o.credits.first { it.account.name == "示例银行信用卡" }
        assertEquals(AccountKind.CREDIT, card.account.kind)
        assertNull(card.settings)
        assertFalse(card.defaulted)
        assertEquals(300_00L, card.debt)
        assertTrue(card.statements.isEmpty())
        assertEquals(1, o.unsetCredits)

        assertEquals(350_00L, o.creditDebt)
        assertEquals(50_00L, o.monthDue)
        assertEquals(listOf(DueKind.CREDIT), o.items.filter { it.counts }.map { it.kind })
    }

    @Test
    fun loansShowCounterpartiesWithRepaymentsAndTotals() {
        val (_, data) = imported()
        val o = DebtMath.overview(data, today, zone)
        assertEquals(2, o.loans.size)
        val li = o.loans.first { it.person == "小李子" }
        assertEquals(LoanDirection.LEND, li.direction)
        assertEquals(600_00L, li.principal)
        assertEquals(600_00L, li.repaid)
        assertTrue(li.settled)
        val wang = o.loans.first { it.person == "老王" }
        assertEquals(LoanDirection.BORROW, wang.direction)
        assertEquals(1_000_00L, wang.principal)
        assertEquals(400_00L, wang.repaid)
        assertEquals(600_00L, wang.remaining)
        assertFalse(wang.settled)
        // 没有预计还款日：列在借贷里，不进即将到期
        assertTrue(o.items.none { it.kind == DueKind.LOAN })
        assertEquals(0L, o.receivable)
        assertEquals(600_00L, o.payable)
        // 欠款合计 = 信用账户欠款 + 借入待还，本月没到期也显示
        assertEquals(950_00L, o.totalDebt)
    }

    @Test
    fun normalRowsAndLoanTransfersStayOutOfIncomeAndExpense() {
        val (preview, data) = imported()
        val normal = preview.candidates.single { it.record.note.contains("牛肉面") }
        assertEquals(ImportClass.NORMAL, normal.klass)
        assertEquals(TxType.EXPENSE, normal.txType)
        assertNotNull(normal.categoryId)
        // 借贷本金与收款 / 还款、花呗还款都是转账，不计收支
        val loans = preview.candidates.filter { it.klass == ImportClass.LOAN }
        assertEquals(4, loans.size)
        assertTrue(loans.all { it.txType == TxType.TRANSFER })
        assertEquals(TxType.TRANSFER, preview.candidates.single { it.record.note == "花呗还款" }.txType)
        // 消费仍是信用账户上的支出
        assertEquals(4, data.transactions.count { t -> t.type == TxType.EXPENSE && data.accounts.first { it.id == t.accountId }.kind == AccountKind.CREDIT })
    }

    @Test
    fun existingDataWithoutSettingsGetsDefaultsAndUserSettingsWin() {
        val (_, data) = imported()
        // 已经导入过的老用户：账户上没有账单设置，不用重新导入就按默认出账单
        val o = DebtMath.overview(data, today, zone)
        assertTrue(o.credits.first { it.account.name == "花呗" }.statements.isNotEmpty())
        // 用户自己设了账单日：用他的，不再标默认
        val custom = data.copy(accounts = data.accounts.map { if (it.name == "花呗") it.copy(credit = CreditSettings(5, 25)) else it })
        val hb = DebtMath.overview(custom, today, zone).credits.first { it.account.name == "花呗" }
        assertFalse(hb.defaulted)
        assertEquals(CreditSettings(5, 25), hb.settings)
    }

    @Test
    fun repaymentFindsExistingCounterpartyAccount() {
        val li = LedgerAccount("acc-li", "借出 · 小李子", AccountKind.RECEIVABLE)
        val cash = LedgerAccount("acc-cash", "现金", AccountKind.CASH)
        val base = LedgerData(accounts = listOf(cash, li), categories = LedgerDefaults.categories())
        val (preview, _) = imported(base)
        val repay = preview.candidates.single { it.record.note == "小李子还钱" }
        assertEquals(li.id, repay.accountId)
    }
}
