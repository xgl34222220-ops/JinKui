package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.CreditSettings
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerInstallment
import com.xiguli.jinkui.data.ledger.LedgerLoan
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.TxFilter
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.RobolectricTestRunner

/**
 * 1.4 欠款 / 账单的界面：总览（本月待还、逾期标红、即将到期、信用账户、借贷）、信用账户账单、借贷列表、
 * 还款面板、分期面板（浅色 / 深色截图），以及账本首页的到期提示条。全部是合成数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class DebtScreensUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 11)
    private fun at(m: Int, d: Int, h: Int = 12) = LocalDateTime.of(2026, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private val bank = LedgerAccount("a-bank", "示例银行卡", AccountKind.BANK, openingBalance = 20_000_00, position = 0)
    private val huabei = LedgerAccount("a-hb", "花呗", AccountKind.CREDIT, position = 1, credit = CreditSettings(1, 10, limit = 5_000_00))
    private val card = LedgerAccount("a-cc", "示例信用卡", AccountKind.CREDIT, position = 2, credit = CreditSettings(5, 25, limit = 30_000_00))
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, position = 3)
    private val wang = LedgerAccount("a-wang", "借入 · 老王", AccountKind.PAYABLE, position = 4)
    private val zhao = LedgerAccount("a-zhao", "借出 · 小赵", AccountKind.RECEIVABLE, position = 5)

    private val plan = LedgerInstallment(
        "in-1", card.id, "笔记本电脑", 6_000_00, 6, feePerPeriod = 36_00, feeRateBp = 60,
        firstDate = LocalDate.of(2026, 9, 1), startDate = LocalDate.of(2026, 9, 1),
    )
    private val wangLoan = LedgerLoan("ln-wang", wang.id, LoanDirection.BORROW, LocalDate.of(2026, 8, 1), dueDate = LocalDate.of(2026, 10, 13))
    private val liLoan = LedgerLoan("ln-li", li.id, LoanDirection.LEND, LocalDate.of(2026, 9, 1), dueDate = LocalDate.of(2026, 10, 8), interest = 20_00, note = "周转")

    private var seq = 0
    private fun tx(type: TxType, amount: Long, time: Long, from: String, to: String? = null, debt: String? = null, note: String = "") =
        LedgerTransaction("t${++seq}", type, amount, time, from, to, categoryId = if (type == TxType.TRANSFER) null else "cat-shopping", note = note, debtId = debt)

    private val data: LedgerData by lazy {
        seq = 0
        LedgerData(
            accounts = listOf(bank, huabei, card, li, wang, zhao),
            categories = LedgerDefaults.categories(),
            transactions = listOf(
                // 花呗：9 月消费 1500，10 月 1 日出账，10 月 5 日还了 600，剩 900 已逾期（10 日到期）
                tx(TxType.EXPENSE, 1_000_00, at(9, 5), huabei.id, note = "超市"),
                tx(TxType.EXPENSE, 500_00, at(9, 20), huabei.id, note = "外卖"),
                tx(TxType.EXPENSE, 200_00, at(10, 3), huabei.id, note = "打车"),
                tx(TxType.TRANSFER, 600_00, at(10, 5), bank.id, huabei.id),
                // 信用卡：9 月 1 日分期 6 期，每期 1000 + 36
                tx(TxType.EXPENSE, 6_000_00, at(9, 1), card.id, debt = plan.id, note = "分期消费 · 笔记本电脑"),
                tx(TxType.EXPENSE, 216_00, at(9, 1), card.id, debt = plan.id, note = "分期手续费 · 笔记本电脑（6 期）"),
                tx(TxType.EXPENSE, 320_00, at(9, 18), card.id, note = "加油"),
                tx(TxType.TRANSFER, 1_036_00, at(9, 20), bank.id, card.id),
                tx(TxType.EXPENSE, 88_00, at(10, 2), card.id, note = "电影"),
                // 借贷：借入老王 3000 还了 1000；借出小李 800（导入前的一木借出）；借出小赵 500 已收回
                tx(TxType.TRANSFER, 3_000_00, at(8, 1), wang.id, bank.id, debt = wangLoan.id),
                tx(TxType.TRANSFER, 1_000_00, at(9, 1), bank.id, wang.id, debt = wangLoan.id),
                tx(TxType.TRANSFER, 800_00, at(9, 1), bank.id, li.id, debt = liLoan.id),
                tx(TxType.TRANSFER, 500_00, at(7, 1), bank.id, zhao.id),
                tx(TxType.TRANSFER, 500_00, at(8, 15), zhao.id, bank.id),
            ),
            installments = listOf(plan),
            loans = listOf(wangLoan, liLoan),
        )
    }

    private val overview by lazy { DebtMath.overview(data, today, zone) }

    @Test
    fun overviewLightAndDark() {
        assertEquals(900_00L + 2_000_00L + overview.items.filter { it.title == "示例信用卡" && it.counts }.sumOf { it.amount }, overview.monthDue)
        var opened: String? = null
        val activity = h.show { DebtOverviewContent(overview, DebtOverviewActions(onCredit = { opened = it })) }
        compose.onNodeWithText("欠款合计(元)").assertExists()
        compose.onNodeWithText("本月待还").assertExists()
        compose.onAllNodesWithText("已逾期 1 天").onFirst().assertExists()
        compose.onAllNodesWithText("花呗").onFirst().assertExists()
        h.snapshot(activity, "debts-overview-light")
        compose.onNodeWithText("应收合计").performScrollTo().assertExists()
        compose.onAllNodesWithText("示例信用卡").onLast().performScrollTo().performClick()
        assertEquals(card.id, opened)
        h.close()
        val dark = h.show(dark = true) { DebtOverviewContent(overview, DebtOverviewActions()) }
        h.snapshot(dark, "debts-overview-dark")
    }

    /** 1.4.0-alpha02：导入一木账单（合成样例）后，总览里能看到欠款合计、默认账单日的花呗、没设账单日的信用卡与借贷。 */
    @Test
    fun importedDebtsShowInOverview() {
        val table = com.xiguli.jinkui.data.importer.TableReader.read(
            com.xiguli.jinkui.data.importer.XlsFixture.xls(com.xiguli.jinkui.data.importer.XlsFixture.debtSample()), "一木账单.xls",
        )
        val parsed = com.xiguli.jinkui.data.importer.BillParser.parse(table, com.xiguli.jinkui.data.importer.BillSource.APP)
        val base = LedgerData(categories = LedgerDefaults.categories())
        val preview = com.xiguli.jinkui.data.importer.BillImport.preview(parsed, base, zone, now = 1L)
        val imported = base.copy(
            accounts = preview.usedNewAccounts.mapIndexed { i, a -> a.copy(position = i) },
            categories = base.categories + preview.usedNewCategories,
            transactions = com.xiguli.jinkui.data.importer.BillImport.transactions(preview, zone, 2L, "batch"),
        )
        val o = DebtMath.overview(imported, LocalDate.of(2026, 10, 5), zone)
        assertEquals(950_00L, o.totalDebt)
        val activity = h.show { DebtOverviewContent(o, DebtOverviewActions()) }
        compose.onNodeWithText("欠款合计(元)").assertExists()
        compose.onNodeWithText("950.00").assertExists()
        compose.onNodeWithText("设置账单日 ›").performScrollTo().assertExists()
        compose.onNodeWithText("默认 · 每月 1 号出账、10 号还款，可在账单设置里修改").performScrollTo().assertExists()
        compose.onNodeWithText("老王").performScrollTo().assertExists()
        h.snapshot(activity, "debts-overview-imported-light")
        h.close()
        val hb = o.credits.first { it.account.name == "花呗" }
        val detail = h.show { CreditStatementContent(hb, LocalDate.of(2026, 10, 5), CreditActions()) }
        compose.onNodeWithText("默认账单日：每月 1 号出账、10 号还款").assertExists()
        compose.onNodeWithText("2026年9月账单").performScrollTo().assertExists()
        h.snapshot(detail, "credit-statement-default-light")
    }

    @Test
    fun creditStatementLightAndDark() {
        val view = overview.credits.first { it.account.id == card.id }
        // 10 月 5 日账单：第 2 期 1036 + 9 月 18 日加油 320 + 10 月 2 日电影 88
        assertEquals(1_444_00L, view.latest!!.owed)
        assertEquals(1_036_00L, view.latest!!.installmentPart)
        var repay = 0
        val activity = h.show { CreditStatementContent(view, today, CreditActions(onRepay = { repay++ })) }
        compose.onNodeWithText("2026年10月账单").performScrollTo().assertExists()
        compose.onNodeWithText("已还 1/6 期").performScrollTo().assertExists()
        h.snapshot(activity, "credit-statement-light")
        compose.onNodeWithText("还款").performScrollTo().performClick()
        assertEquals(1, repay)
        h.close()
        val hb = overview.credits.first { it.account.id == huabei.id }
        val dark = h.show(dark = true) { CreditStatementContent(hb, today, CreditActions()) }
        compose.onNodeWithText("10月10日到期 · 已逾期 1 天").assertExists()
        h.snapshot(dark, "credit-statement-dark")
    }

    @Test
    fun loanListLightAndDark() {
        var tab: Boolean? = null
        val activity = h.show { LoanListContent(overview, false, LoanActions(onTab = { tab = it })) }
        compose.onNodeWithText("老王").assertExists()
        compose.onNodeWithText("小李").assertExists()
        compose.onNodeWithText("已逾期 3 天").performScrollTo().assertExists()
        h.snapshot(activity, "loans-light")
        compose.onNodeWithText("已结清 1").performClick()
        assertEquals(true, tab)
        h.close()
        val dark = h.show(dark = true) { LoanListContent(overview, true, LoanActions()) }
        compose.onNodeWithText("小赵").assertExists()
        h.snapshot(dark, "loans-settled-dark")
        h.close()
        val loan = overview.loans.first { it.loan.id == liLoan.id }
        val detail = h.show { LoanDetailSheet(loan, today, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("收款").assertExists()
        h.snapshot(detail, "loan-detail-sheet-light", withDialog = true)
    }

    @Test
    fun repaySheetLightAndDark() {
        val view = overview.credits.first { it.account.id == huabei.id }
        var submitted: Pair<String, Long>? = null
        val activity = h.show {
            RepaySheet(huabei, view.latest!!.remaining, view.used, fundAccounts(data), today, {}) { from, amount, _ -> submitted = from to amount }
        }
        compose.onNodeWithText("本期剩余 ¥900.00").assertExists()
        h.snapshot(activity, "repay-sheet-light", withDialog = true)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("全部欠款 ¥1,100.00").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("确认还款").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(bank.id to 1_100_00L, submitted)
        h.close()
        val dark = h.show(dark = true) { RepaySheet(huabei, view.latest!!.remaining, view.used, fundAccounts(data), today, {}) { _, _, _ -> } }
        h.snapshot(dark, "repay-sheet-dark", withDialog = true)
    }

    @Test
    fun installmentSheetLightAndDark() {
        var plan: LedgerInstallment? = null
        val activity = h.show { InstallmentSheet(card, today, {}) { p, record -> if (record) plan = p } }
        compose.onNodeWithText("新增分期 · 示例信用卡").assertExists()
        h.snapshot(activity, "installment-sheet-light", withDialog = true)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("创建分期").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("请填写分期总额").assertExists()
        // 没填名称与总额：不提交
        assertEquals(null, plan)
        h.close()
        val dark = h.show(dark = true) { InstallmentSheet(card, today, {}) { _, _ -> } }
        h.snapshot(dark, "installment-sheet-dark", withDialog = true)
    }

    @Test
    fun ledgerHomeShowsDueBanner() {
        val reminders = debtReminders(data, today, zone)
        assertTrue(reminders.any { it.overdue })
        var opened = 0
        val ui = buildLedgerUi(data, YearMonth.of(2026, 10), TxFilter(), zone, today)
        val activity = h.show {
            LedgerHome(ui, TxFilter(), LazyListState(), LedgerHomeActions(onDebts = { opened++ }), debtReminders = reminders, today = today)
        }
        compose.onNodeWithText("${reminders.size} 笔欠款 / 账单待处理，其中 ${reminders.count { it.overdue }} 笔已逾期").assertExists().performClick()
        assertEquals(1, opened)
        h.snapshot(activity, "ledger-debt-banner-light")
    }
}
