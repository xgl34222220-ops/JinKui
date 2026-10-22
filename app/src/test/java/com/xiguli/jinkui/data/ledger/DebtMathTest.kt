package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.4 欠款 / 账单的计算：账单日与还款日（月末、2 月没有 31 号、次月还款、出账后 N 天）、
 * 应还 / 已还 / 剩余与部分还款、本期未出账单、分期跨期入账与提前结清、借贷结清。全部是合成数据。
 */
class DebtMathTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun at(y: Int, m: Int, day: Int, h: Int = 12) = d(y, m, day).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private var seq = 0
    private fun tx(type: TxType, amount: Long, time: Long, from: String, to: String? = null, debt: String? = null) =
        LedgerTransaction("t${++seq}", type, amount, time, from, to, debtId = debt)

    private val bank = LedgerAccount("a-bank", "银行卡", AccountKind.BANK, openingBalance = 10_000_00)

    // ── 账单日与还款日 ────────────────────────────────────────────

    @Test
    fun statementDay31FallsOnLastDayOfFebruary() {
        val s = CreditSettings(statementDay = 31, dueDay = 20)
        assertEquals(d(2026, 2, 28), DebtMath.statementDate(YearMonth.of(2026, 2), s))
        assertEquals(d(2028, 2, 29), DebtMath.statementDate(YearMonth.of(2028, 2), s))
        assertEquals(d(2026, 4, 30), DebtMath.statementDate(YearMonth.of(2026, 4), s))
        // 3 月中旬往前最近的账单日是 2 月 28 日；3 月 1 日的消费计入 3 月 31 日的账单
        assertEquals(d(2026, 2, 28), DebtMath.statementOnOrBefore(d(2026, 3, 15), s))
        assertEquals(d(2026, 3, 31), DebtMath.statementOnOrAfter(d(2026, 3, 1), s))
        // 账单日当天的消费计入当期
        assertEquals(d(2026, 2, 28), DebtMath.statementOnOrAfter(d(2026, 2, 28), s))
        assertEquals(d(2026, 3, 31), DebtMath.nextStatement(d(2026, 2, 28), s))
        // 还款日 20 号小于账单日：次月 20 号
        assertEquals(d(2026, 3, 20), DebtMath.dueDate(d(2026, 2, 28), s))
    }

    @Test
    fun monthEndStatementDays() {
        val s = CreditSettings(statementDay = 30, dueDay = 30)
        assertEquals(d(2026, 1, 30), DebtMath.statementOnOrBefore(d(2026, 2, 27), s))
        assertEquals(d(2026, 2, 28), DebtMath.statementOnOrAfter(d(2026, 2, 1), s))
        assertEquals(d(2026, 3, 30), DebtMath.statementOnOrAfter(d(2026, 3, 1), s))
        // 还款日等于账单日：次月同一天，2 月没有 30 号取 28 号
        assertEquals(d(2026, 2, 28), DebtMath.dueDate(d(2026, 1, 30), s))
        assertEquals(d(2026, 3, 30), DebtMath.dueDate(d(2026, 2, 28), s))
    }

    @Test
    fun dueDateInSameOrNextMonthOrDaysAfter() {
        val sameMonth = CreditSettings(statementDay = 1, dueDay = 10)
        assertEquals(d(2026, 10, 10), DebtMath.dueDate(d(2026, 10, 1), sameMonth))
        val nextMonth = CreditSettings(statementDay = 25, dueDay = 15)
        assertEquals(d(2026, 11, 15), DebtMath.dueDate(d(2026, 10, 25), nextMonth))
        assertEquals(d(2027, 1, 15), DebtMath.dueDate(d(2026, 12, 25), nextMonth))
        val after = CreditSettings(statementDay = 31, dueDay = 20, dueMode = DueMode.AFTER_STATEMENT)
        assertEquals(d(2026, 3, 20), DebtMath.dueDate(d(2026, 2, 28), after))
        assertEquals(d(2027, 1, 20), DebtMath.dueDate(d(2026, 12, 31), after))
    }

    @Test
    fun settingsValidation() {
        assertNull(CreditSettings(31, 31).problem())
        assertTrue(CreditSettings(0, 10).problem() != null)
        assertTrue(CreditSettings(10, 32).problem() != null)
        assertTrue(CreditSettings(10, 61, DueMode.AFTER_STATEMENT).problem() != null)
        assertNull(CreditSettings(10, 60, DueMode.AFTER_STATEMENT).problem())
    }

    // ── 账单：应还、已还、剩余 ────────────────────────────────────

    private val huabei = LedgerAccount(
        "a-hb", "花呗", AccountKind.CREDIT,
        credit = CreditSettings(statementDay = 1, dueDay = 10, limit = 5_000_00),
    )

    private fun huabeiTxs() = listOf(
        tx(TxType.EXPENSE, 1_000_00, at(2026, 9, 5), huabei.id),
        tx(TxType.EXPENSE, 500_00, at(2026, 9, 20), huabei.id),
        // 10 月 1 日出账 1500；10 月 3 日新消费 200（下一期）；10 月 5 日还 600（部分还款）
        tx(TxType.EXPENSE, 200_00, at(2026, 10, 3), huabei.id),
        tx(TxType.TRANSFER, 600_00, at(2026, 10, 5), bank.id, huabei.id),
    )

    @Test
    fun partialRepaymentLeavesRemainingAndOpenCycle() {
        val txs = huabeiTxs()
        val view = DebtMath.credit(huabei, txs, emptyList(), d(2026, 10, 8), zone)
        val latest = view.latest!!
        assertEquals(d(2026, 10, 1), latest.statementDate)
        assertEquals(d(2026, 9, 2), latest.cycleStart)
        assertEquals(d(2026, 10, 10), latest.dueDate)
        assertEquals(1_500_00L, latest.owed)
        assertEquals(1_500_00L, latest.charges)
        assertEquals(600_00L, latest.paid)
        assertEquals(900_00L, latest.remaining)
        assertFalse(latest.overdue(d(2026, 10, 8)))
        assertTrue(latest.overdue(d(2026, 10, 11)))
        // 本期未出：10 月 2 日到 11 月 1 日，新消费 200
        val open = view.open!!
        assertEquals(d(2026, 10, 2), open.start)
        assertEquals(d(2026, 11, 1), open.statementDate)
        assertEquals(d(2026, 11, 10), open.dueDate)
        assertEquals(200_00L, open.amount)
        // 额度：已用 = 当前欠款 1100，可用 3900
        assertEquals(1_100_00L, view.used)
        assertEquals(3_900_00L, view.available)
        // 第一笔消费在 9 月 5 日：账单从 10 月 1 日那一期算起
        assertEquals(1, view.statements.size)
    }

    @Test
    fun secondPaymentClearsStatementAndCarryOverRollsIntoNext() {
        val txs = huabeiTxs() + tx(TxType.TRANSFER, 900_00, at(2026, 10, 9), bank.id, huabei.id)
        val cleared = DebtMath.credit(huabei, txs, emptyList(), d(2026, 10, 12), zone).latest!!
        assertTrue(cleared.cleared)
        assertEquals(1_500_00L, cleared.paid)
        // 不还第二笔：11 月 1 日出账时，上期剩下的 900 并进新账单（900 + 200 = 1100）
        val later = DebtMath.credit(huabei, huabeiTxs(), emptyList(), d(2026, 11, 2), zone)
        assertEquals(d(2026, 11, 1), later.latest!!.statementDate)
        assertEquals(1_100_00L, later.latest!!.owed)
        assertEquals(200_00L, later.latest!!.charges)
        assertFalse(later.statements[1].cleared)
    }

    @Test
    fun refundsAndOverpaymentNeverMakeNegativeStatements() {
        val txs = listOf(
            tx(TxType.EXPENSE, 100_00, at(2026, 9, 5), huabei.id),
            tx(TxType.TRANSFER, 300_00, at(2026, 9, 6), bank.id, huabei.id),
        )
        val view = DebtMath.credit(huabei, txs, emptyList(), d(2026, 10, 5), zone)
        assertEquals(0L, view.latest!!.owed)
        assertTrue(view.latest!!.cleared)
        assertEquals(200_00L, view.balance)
        assertEquals(0L, view.used)
    }

    @Test
    fun interestFreePeriod() {
        val s = CreditSettings(statementDay = 5, dueDay = 25)
        val card = LedgerAccount("a-cc", "招行信用卡", AccountKind.CREDIT, credit = s)
        // 10 月 6 日（账单日次日）消费：11 月 5 日出账，11 月 25 日还，免息 50 天
        val view = DebtMath.credit(card, emptyList(), emptyList(), d(2026, 10, 6), zone)
        assertEquals(d(2026, 11, 25), view.freeUntil)
        assertEquals(50, view.freeDays)
        assertEquals(50, view.maxFreeDays)
        // 账单日当天消费：当期出账，当月 25 日还
        val onStatement = DebtMath.credit(card, emptyList(), emptyList(), d(2026, 10, 5), zone)
        assertEquals(d(2026, 10, 25), onStatement.freeUntil)
        assertEquals(20, onStatement.freeDays)
    }

    @Test
    fun withoutSettingsOnlyBalanceIsKnown() {
        // 不是花呗的信用账户没设账单日：只知道欠款（1.4.0-alpha02 起花呗有默认账单日，见下一个测试）
        val plain = huabei.copy(name = "示例白条", credit = null)
        val view = DebtMath.credit(plain, huabeiTxs(), emptyList(), d(2026, 10, 8), zone)
        assertNull(view.open)
        assertNull(view.settings)
        assertTrue(view.statements.isEmpty())
        assertEquals(1_100_00L, view.used)
        assertEquals(1_100_00L, view.debt)
        assertNull(view.available)
    }

    @Test
    fun huabeiWithoutSettingsUsesDefaultStatementAndDueDay() {
        val plain = huabei.copy(credit = null)
        val view = DebtMath.credit(plain, huabeiTxs(), emptyList(), d(2026, 10, 8), zone)
        assertTrue(view.defaulted)
        assertEquals(CreditSettings(1, 10), view.settings)
        assertEquals(d(2026, 10, 1), view.latest!!.statementDate)
        assertEquals(d(2026, 10, 10), view.latest!!.dueDate)
        assertEquals(1_100_00L, view.debt)
    }

    // ── 分期 ──────────────────────────────────────────────────────

    private val card = LedgerAccount("a-cc", "招行信用卡", AccountKind.CREDIT, credit = CreditSettings(statementDay = 10, dueDay = 28))

    private val plan = LedgerInstallment(
        id = "inst-1", accountId = card.id, title = "手机", total = 1_200_00, periods = 3, feePerPeriod = 10_00,
        firstDate = d(2026, 10, 3), startDate = d(2026, 10, 3),
    )

    /** 分期时记上的消费与手续费，各带分期 ID。 */
    private fun planTxs(fee: Long = 30_00) = listOf(
        tx(TxType.EXPENSE, 1_200_00, at(2026, 10, 3), card.id, debt = plan.id),
        tx(TxType.EXPENSE, fee, at(2026, 10, 3), card.id, debt = plan.id),
    )

    @Test
    fun installmentSplitsPrincipalWithRemainderInFirstPeriod() {
        val odd = plan.copy(total = 1_000_00, periods = 3)
        assertEquals(333_34L, odd.principalOf(0))
        assertEquals(333_33L, odd.principalOf(1))
        assertEquals(333_33L, odd.principalOf(2))
        assertEquals(1_000_00L, (0 until 3).sumOf { odd.principalOf(it) })
        assertEquals(d(2026, 11, 3), odd.scheduledDate(1))
        // 首期 1 月 31 日：2 月取月末
        assertEquals(d(2026, 2, 28), odd.copy(firstDate = d(2026, 1, 31), startDate = d(2026, 1, 31)).scheduledDate(1))
        assertEquals(60L, DebtMath.feeFromRate(10_000, 60))
        assertEquals(7_20L, DebtMath.feeFromRate(1_200_00, 60))
    }

    @Test
    fun installmentPeriodsRollIntoTheirStatements() {
        val repayOct = tx(TxType.TRANSFER, 410_00, at(2026, 10, 20), bank.id, card.id)
        val txs = planTxs() + repayOct
        val view = DebtMath.credit(card, txs, listOf(plan), d(2026, 10, 25), zone)
        // 10 月 10 日账单：只有第 1 期（400 + 10），后两期还没入账
        val oct = view.latest!!
        assertEquals(d(2026, 10, 10), oct.statementDate)
        assertEquals(410_00L, oct.owed)
        assertEquals(410_00L, oct.installmentPart)
        assertEquals(410_00L, oct.charges)
        assertTrue(oct.cleared)
        val inst = view.installments.single()
        assertEquals(1, inst.paidCount)
        assertEquals(3, inst.total)
        assertEquals(d(2026, 11, 10), inst.next!!.statementDate)
        assertEquals(d(2026, 11, 28), inst.next!!.dueDate)
        assertEquals(820_00L, inst.remaining)
        // 本期未出：第 2 期 410
        assertEquals(410_00L, view.open!!.amount)
        assertEquals(410_00L, view.open!!.installmentPart)
        // 已用额度含还没入账的分期
        assertEquals(820_00L, view.used)
        // 11 月 10 日出账：第 2 期 410；还上后 2/3
        val nov = DebtMath.credit(card, txs + tx(TxType.TRANSFER, 410_00, at(2026, 11, 20), bank.id, card.id), listOf(plan), d(2026, 11, 21), zone)
        assertEquals(410_00L, nov.latest!!.owed)
        assertTrue(nov.latest!!.cleared)
        assertEquals(2, nov.installments.single().paidCount)
    }

    @Test
    fun installmentPlusRegularSpendingInSameCycle() {
        val txs = planTxs() + tx(TxType.EXPENSE, 88_00, at(2026, 10, 8), card.id)
        val oct = DebtMath.credit(card, txs, listOf(plan), d(2026, 10, 11), zone).latest!!
        assertEquals(498_00L, oct.owed)
        assertEquals(498_00L, oct.charges)
        assertEquals(410_00L, oct.installmentPart)
    }

    @Test
    fun earlyPayoffBillsRemainingPrincipalAndWaivesLaterFees() {
        val paid = DebtMath.payoff(plan, d(2026, 11, 15))
        assertEquals(d(2026, 11, 15), paid.payoffDate)
        // 12 月 3 日那一期并到 11 月 15 日、免手续费：实际收 20
        assertEquals(20_00L, paid.chargedFees)
        assertTrue(paid.waived(2))
        assertEquals(d(2026, 11, 15), paid.billingDate(2))
        // 手续费那一笔改成 20（仓库在结清时做）
        val txs = planTxs(fee = 20_00) +
            tx(TxType.TRANSFER, 410_00, at(2026, 10, 20), bank.id, card.id) +
            tx(TxType.TRANSFER, 410_00, at(2026, 11, 20), bank.id, card.id)
        val dec = DebtMath.credit(card, txs, listOf(paid), d(2026, 12, 11), zone)
        // 11 月 10 日账单只有第 2 期；12 月 10 日账单是结清并入的第 3 期本金 400
        assertEquals(410_00L, dec.statements[1].owed)
        assertEquals(d(2026, 12, 10), dec.latest!!.statementDate)
        assertEquals(400_00L, dec.latest!!.owed)
        assertEquals(400_00L, dec.latest!!.installmentPart)
        val view = dec.installments.single()
        assertTrue(view.earlyPaid)
        assertEquals(2, view.paidCount)
        assertEquals(0L, view.periods[2].fee)
        // 还上 12 月账单后全部还清
        val done = DebtMath.credit(card, txs + tx(TxType.TRANSFER, 400_00, at(2026, 12, 20), bank.id, card.id), listOf(paid), d(2026, 12, 21), zone)
        assertTrue(done.installments.single().settled)
        assertEquals(0L, done.used)
    }

    @Test
    fun payoffBeforeFirstPeriodBillsEverythingAtOnce() {
        val early = DebtMath.payoff(plan.copy(firstDate = d(2026, 11, 1)), d(2026, 10, 5))
        assertEquals(0L, early.chargedFees)
        val txs = listOf(tx(TxType.EXPENSE, 1_200_00, at(2026, 10, 3), card.id, debt = plan.id))
        val oct = DebtMath.credit(card, txs, listOf(early), d(2026, 10, 11), zone).latest!!
        assertEquals(1_200_00L, oct.owed)
    }

    @Test
    fun installmentValidation() {
        assertNull(plan.problem())
        assertTrue(plan.copy(periods = 1).problem() != null)
        assertTrue(plan.copy(total = 0).problem() != null)
        assertTrue(plan.copy(startDate = d(2026, 10, 4)).problem() != null)
    }

    // ── 借贷 ──────────────────────────────────────────────────────

    private val lentLi = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE)
    private val borrowedWang = LedgerAccount("a-wang", "借入 · 老王", AccountKind.PAYABLE)

    @Test
    fun importedLoanRowsBecomeLegacyLoanPerCounterparty() {
        // 1.3 导入的一木“借出”：资金账户 → 借出 · 小李；之后收回一部分。没有任何借贷记录。
        val txs = listOf(
            tx(TxType.TRANSFER, 500_00, at(2026, 9, 1), bank.id, lentLi.id),
            tx(TxType.TRANSFER, 200_00, at(2026, 9, 20), lentLi.id, bank.id),
        )
        val data = LedgerData(accounts = listOf(bank, lentLi), transactions = txs)
        val loan = DebtMath.loans(data, zone).single()
        assertEquals(DebtMath.legacyLoanId(lentLi.id), loan.loan.id)
        assertFalse(loan.stored)
        assertTrue(loan.loan.legacy)
        assertEquals("小李", loan.person)
        assertEquals(LoanDirection.LEND, loan.direction)
        assertEquals(500_00L, loan.principal)
        assertEquals(200_00L, loan.repaid)
        assertEquals(300_00L, loan.remaining)
        assertFalse(loan.settled)
        assertEquals(d(2026, 9, 1), loan.loan.date)
        // 设了预计还款日之后落库成记录，同一批流水仍归它
        val stored = data.copy(loans = listOf(loan.loan.copy(dueDate = d(2026, 10, 1))))
        val again = DebtMath.loans(stored, zone).single()
        assertTrue(again.stored)
        assertEquals(300_00L, again.remaining)
        assertTrue(again.overdue(d(2026, 10, 2)))
    }

    @Test
    fun openingBalanceCountsAsPrincipal() {
        val data = LedgerData(accounts = listOf(borrowedWang.copy(openingBalance = -800_00)))
        val loan = DebtMath.loans(data, zone).single()
        assertEquals(LoanDirection.BORROW, loan.direction)
        assertEquals(800_00L, loan.principal)
        assertEquals(800_00L, loan.remaining)
    }

    @Test
    fun partialRepaymentsSettleLoanAndInterestTracksSeparately() {
        val loan = LedgerLoan("ln-1", lentLi.id, LoanDirection.LEND, d(2026, 8, 1), dueDate = d(2026, 10, 31), interest = 50_00)
        val txs = listOf(
            tx(TxType.TRANSFER, 1_000_00, at(2026, 8, 1), bank.id, lentLi.id, debt = loan.id),
            tx(TxType.TRANSFER, 400_00, at(2026, 9, 1), lentLi.id, bank.id, debt = loan.id),
        )
        val data = LedgerData(accounts = listOf(bank, lentLi), transactions = txs, loans = listOf(loan))
        val open = DebtMath.loans(data, zone).single()
        assertEquals(600_00L, open.remaining)
        assertEquals(50_00L, open.interestRemaining)
        assertFalse(open.settled)
        assertEquals(LoanEntryKind.PRINCIPAL, DebtMath.entryKind(open, txs[0]))
        assertEquals(LoanEntryKind.REPAY, DebtMath.entryKind(open, txs[1]))
        val rest = txs +
            tx(TxType.TRANSFER, 600_00, at(2026, 10, 1), lentLi.id, bank.id, debt = loan.id) +
            tx(TxType.INCOME, 50_00, at(2026, 10, 1), bank.id, debt = loan.id)
        val done = DebtMath.loans(data.copy(transactions = rest), zone).single()
        assertTrue(done.settled)
        assertEquals(0L, done.remaining)
        assertEquals(50_00L, done.interestPaid)
        assertEquals(0L, done.interestRemaining)
        assertEquals(LoanEntryKind.INTEREST, DebtMath.entryKind(done, rest.last()))
        // 已结清的借贷排在后面，也不再出现在待收里
        val overview = DebtMath.overview(data.copy(transactions = rest), d(2026, 10, 10), zone)
        assertTrue(overview.items.none { it.kind == DueKind.LOAN })
        assertEquals(0L, overview.receivable)
    }

    @Test
    fun writeOffSettlesTheRemainder() {
        val loan = LedgerLoan("ln-2", borrowedWang.id, LoanDirection.BORROW, d(2026, 9, 1))
        val txs = listOf(
            tx(TxType.TRANSFER, 800_00, at(2026, 9, 1), borrowedWang.id, bank.id, debt = loan.id),
            tx(TxType.TRANSFER, 300_00, at(2026, 9, 15), bank.id, borrowedWang.id, debt = loan.id),
        )
        val data = LedgerData(accounts = listOf(bank, borrowedWang), transactions = txs, loans = listOf(loan))
        assertEquals(500_00L, DebtMath.loans(data, zone).single().remaining)
        // 对方免还：应付账户上一笔收入，余额归零
        val forgiven = txs + tx(TxType.INCOME, 500_00, at(2026, 10, 1), borrowedWang.id, debt = loan.id)
        val view = DebtMath.loans(data.copy(transactions = forgiven), zone).single()
        assertTrue(view.settled)
        assertEquals(LoanEntryKind.WRITE_OFF, DebtMath.entryKind(view, forgiven.last()))
        assertEquals(0L, LedgerMath.balances(data.accounts, forgiven)[borrowedWang.id])
    }

    @Test
    fun linkedAndImportedRowsOnSameAccountStaySeparate() {
        val loan = LedgerLoan("ln-3", lentLi.id, LoanDirection.LEND, d(2026, 10, 1))
        val txs = listOf(
            tx(TxType.TRANSFER, 500_00, at(2026, 9, 1), bank.id, lentLi.id),
            tx(TxType.TRANSFER, 100_00, at(2026, 10, 1), bank.id, lentLi.id, debt = loan.id),
        )
        val loans = DebtMath.loans(LedgerData(accounts = listOf(bank, lentLi), transactions = txs, loans = listOf(loan)), zone)
        assertEquals(2, loans.size)
        assertEquals(100_00L, loans.first { it.loan.id == loan.id }.principal)
        assertEquals(500_00L, loans.first { it.loan.legacy }.principal)
    }

    @Test
    fun savedImportedLoanWithNothingOnItIsHidden() {
        // 用户在“导入的借贷”上改过约定（存下了 legacy 记录），后来新记了一笔借入：不该再多出一张 ¥0.00 的“借入（导入）”
        val legacy = LedgerLoan(DebtMath.legacyLoanId(borrowedWang.id), borrowedWang.id, LoanDirection.BORROW, d(2026, 10, 12), legacy = true)
        val loan = LedgerLoan("ln-new", borrowedWang.id, LoanDirection.BORROW, d(2026, 10, 13))
        val txs = listOf(tx(TxType.TRANSFER, 10_600_00, at(2026, 10, 13), borrowedWang.id, bank.id, debt = loan.id))
        val loans = DebtMath.loans(LedgerData(accounts = listOf(bank, borrowedWang), transactions = txs, loans = listOf(legacy, loan)), zone)
        assertEquals(listOf("ln-new"), loans.map { it.loan.id })
        assertEquals(10_600_00L, loans.single().remaining)
    }

    @Test
    fun importedOpeningOnlyRepaidIsNotACard() {
        // 对方账户只有一个期初“已还”、没有任何流水，又新记了一笔借入：只列新的那笔
        val wang = borrowedWang.copy(openingBalance = 500_00)
        val loan = LedgerLoan("ln-new", wang.id, LoanDirection.BORROW, d(2026, 10, 13))
        val txs = listOf(tx(TxType.TRANSFER, 10_600_00, at(2026, 10, 13), wang.id, bank.id, debt = loan.id))
        val loans = DebtMath.loans(LedgerData(accounts = listOf(bank, wang), transactions = txs, loans = listOf(loan)), zone)
        assertEquals(listOf("ln-new"), loans.map { it.loan.id })
    }

    // ── 总览 ──────────────────────────────────────────────────────

    @Test
    fun overviewSumsThisMonthDueAndOverdue() {
        val wangLoan = LedgerLoan("ln-4", borrowedWang.id, LoanDirection.BORROW, d(2026, 9, 1), dueDate = d(2026, 10, 12))
        val liLoan = LedgerLoan("ln-5", lentLi.id, LoanDirection.LEND, d(2026, 9, 1), dueDate = d(2026, 10, 20))
        val txs = huabeiTxs() + listOf(
            tx(TxType.TRANSFER, 800_00, at(2026, 9, 1), borrowedWang.id, bank.id, debt = wangLoan.id),
            tx(TxType.TRANSFER, 300_00, at(2026, 9, 1), bank.id, lentLi.id, debt = liLoan.id),
        )
        val data = LedgerData(
            accounts = listOf(bank, huabei, lentLi, borrowedWang),
            transactions = txs,
            loans = listOf(wangLoan, liLoan),
        )
        val today = d(2026, 10, 11)
        val o = DebtMath.overview(data, today, zone)
        // 花呗 10 月账单剩 900（10 号到期，已逾期）+ 老王 800（12 号到期）
        assertEquals(1_700_00L, o.monthDue)
        assertEquals(900_00L, o.overdue)
        assertEquals(300_00L, o.monthReceivable)
        assertEquals(300_00L, o.receivable)
        assertEquals(800_00L, o.payable)
        // 按日期排序：花呗（10 日）→ 老王（12 日）→ 小李（20 日）→ 花呗未出账单（11 月 10 日）
        assertEquals(listOf(DueKind.CREDIT, DueKind.LOAN, DueKind.LOAN, DueKind.CREDIT_OPEN), o.items.map { it.kind })
        assertTrue(o.items.first().overdue)
        // 提醒：逾期的花呗、1 天后到期的老王；小李 9 天后到期不提醒，未出账单不提醒
        assertEquals(listOf("花呗", "老王"), o.reminders.map { it.title })
        assertEquals(setOf("老王", "小李"), o.people.map { it.person }.toSet())
        assertEquals(800_00L, o.people.first { it.person == "老王" }.payable)
    }

    @Test
    fun emptyLedgerHasNoDebts() {
        val o = DebtMath.overview(LedgerData(accounts = listOf(bank)), d(2026, 10, 11), zone)
        assertEquals(0L, o.monthDue)
        assertTrue(o.items.isEmpty())
        assertTrue(o.reminders.isEmpty())
    }
}
