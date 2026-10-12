package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.xiguli.jinkui.data.ledger.Money
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
 * 1.4 欠款 / 借贷的界面：总览（1.4.0-alpha04 起按人：别人欠我 / 我欠别人 / 净额，两栏一人一行；信用卡 / 花呗单独一段）、某一个人、选账户带余额、信用账户账单、借贷列表、
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

    /**
     * 1.4.0-alpha04 重做：首屏三个大数（别人欠我 / 我欠别人 / 净额）、记一笔借出 / 借入两个大按钮，
     * “别人欠我 / 我欠别人”两栏一人一行（还欠多少、已还 x / 共 y、到期），信用卡 / 花呗欠款单独一段。首屏不出现术语。
     */
    @Test
    fun overviewLightAndDark() {
        assertEquals(900_00L + 2_000_00L + overview.items.filter { it.title == "示例信用卡" && it.counts }.sumOf { it.amount }, overview.monthDue)
        var opened: String? = null
        var person: String? = null
        val newLoans = mutableListOf<LoanDirection>()
        val actions = DebtOverviewActions(onCredit = { opened = it }, onPerson = { person = it }, onNewLoan = { newLoans += it })
        val activity = h.show { DebtOverviewContent(overview, actions) }
        for (label in listOf("别人欠我", "我欠别人", "净额")) compose.onNodeWithText(label).assertExists()
        compose.onNodeWithText(Money.format(overview.receivable, symbol = false)).assertExists()
        compose.onNodeWithText(Money.format(overview.payable, symbol = false)).assertExists()
        compose.onNodeWithText("-" + Money.format(overview.payable - overview.receivable, symbol = false)).assertExists()
        compose.onNodeWithText("借出 = 我把钱借给别人；借入 = 我向别人借钱", substring = true).assertExists()
        // 首屏不出现“本金 / 应收 / 应付 / 冲抵”这类词
        for (jargon in listOf("本金", "应收", "应付", "冲抵", "欠款合计")) {
            assertTrue(jargon, compose.onAllNodesWithText(jargon, substring = true).fetchSemanticsNodes().isEmpty())
        }
        compose.onAllNodesWithText("记一笔借出").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("记一笔借入").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(LoanDirection.LEND, LoanDirection.BORROW), newLoans)
        // 别人欠我：小李一行，还欠 800，已收回 0 / 共 800，已逾期
        compose.onNodeWithText("别人欠我 · 1 人").assertExists()
        compose.onNodeWithText("已收回 ${Money.format(0)} / 共 ${Money.format(800_00)}").performScrollTo().assertExists()
        compose.onAllNodesWithText(Money.format(800_00)).onFirst().assertExists()
        h.snapshot(activity, "debts-overview-light")
        compose.onAllNodesWithText("小李").onFirst().performScrollTo()
        h.snapshot(activity, "debts-tab-lend-light")
        compose.onAllNodesWithText("小李").onFirst().performClick()
        assertEquals(li.id, person)
        // 我欠别人：老王一行，还欠 2000，已还 1000 / 共 3000
        compose.onNodeWithText("我欠别人 · 1 人").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("已还 ${Money.format(1_000_00)} / 共 ${Money.format(3_000_00)}").performScrollTo().assertExists()
        compose.onAllNodesWithText("老王").onFirst().performScrollTo()
        h.snapshot(activity, "debts-tab-borrow-light")
        compose.onAllNodesWithText("老王").onFirst().performClick()
        assertEquals(wang.id, person)
        // 信用卡 / 花呗单独一段
        compose.onNodeWithText("一共欠 ${Money.format(overview.creditDebt)}", substring = true).performScrollTo().assertExists()
        compose.onAllNodesWithText("已逾期 1 天").onFirst().assertExists()
        compose.onAllNodesWithText("花呗").onFirst().assertExists()
        compose.onAllNodesWithText("示例信用卡").onFirst().performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(card.id, opened)
        h.close()
        val dark = h.show(dark = true) { DebtOverviewContent(overview, DebtOverviewActions()) }
        h.snapshot(dark, "debts-overview-dark")
        compose.onAllNodesWithText("小李").onFirst().performScrollTo()
        h.snapshot(dark, "debts-tab-lend-dark")
        h.close()
        val darkBorrow = h.show(dark = true) { DebtOverviewContent(overview, DebtOverviewActions(), initialTab = LoanDirection.BORROW) }
        compose.onAllNodesWithText("老王").onFirst().performScrollTo()
        h.snapshot(darkBorrow, "debts-tab-borrow-dark")
    }

    /** 某一个人：还欠多少（大字）、已还 x / 共 y、约定还款日 → 还款 / 再借入 / 结清 → 来往记录。 */
    @Test
    fun personDetailLightAndDark() {
        val wangDebt = com.xiguli.jinkui.data.ledger.PersonDebtMath.person(overview.loans, wang.id, today)!!
        val hits = mutableListOf<String>()
        val actions = PersonActions(onPay = { hits += "pay" }, onMore = { hits += "more" }, onSettle = { hits += "settle" }, onLoan = { hits += "loan" })
        val activity = h.show { LoanPersonContent(wangDebt, today, actions, zone) }
        compose.onNodeWithText("我还欠老王").assertExists()
        compose.onNodeWithText(Money.format(2_000_00)).assertExists()
        compose.onNodeWithText("已还 ${Money.format(1_000_00)} / 共 ${Money.format(3_000_00)}").assertExists()
        compose.onAllNodesWithText("约定", substring = true).onFirst().assertExists()
        compose.onNodeWithText("还款 = 我还老王钱", substring = true).assertExists()
        compose.onNodeWithText("来往记录").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("借入 ${Money.format(3_000_00)}").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("还款 ${Money.format(1_000_00)}").performScrollTo().performClick()
        compose.onAllNodesWithText("还款").onFirst().performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("再借入").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("结清").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("loan", "pay", "more", "settle"), hits)
        h.snapshot(activity, "debt-person-borrow-light")
        h.close()
        val liDebt = com.xiguli.jinkui.data.ledger.PersonDebtMath.person(overview.loans, li.id, today)!!
        val lend = h.show { LoanPersonContent(liDebt, today, PersonActions(), zone) }
        compose.onNodeWithText("小李还欠我").assertExists()
        compose.onNodeWithText("收款").assertExists()
        compose.onNodeWithText("再借出").assertExists()
        h.snapshot(lend, "debt-person-lend-light")
        h.close()
        val dark = h.show(dark = true) { LoanPersonContent(wangDebt, today, PersonActions(), zone) }
        h.snapshot(dark, "debt-person-borrow-dark")
    }

    /** 按人还款：默认还欠的全部，选账户时带余额，可以不经过账户。 */
    @Test
    fun personPaySheetLightAndDark() {
        val wangDebt = com.xiguli.jinkui.data.ledger.PersonDebtMath.person(overview.loans, wang.id, today)!!
        val balances = com.xiguli.jinkui.data.ledger.LedgerMath.balances(data.accounts, data.transactions)
        val funds = DebtMath.loanFunds(data.accounts)
        var paid: Triple<Long, Long, String?>? = null
        val activity = h.show {
            PersonPaySheet(wangDebt, funds, balances, null, today, {}) { p, i, f, _ -> paid = Triple(p, i, f) }
        }
        compose.onNodeWithText("我还老王钱").assertExists()
        compose.onNodeWithText("全部 ${Money.format(2_000_00)}").assertExists()
        compose.onNodeWithText(NO_FUND_LABEL).assertExists()
        compose.onNodeWithText("示例银行卡 ${Money.format(balances.getValue(bank.id))}").assertExists()
        compose.onNodeWithText("花呗 欠 ${Money.format(-balances.getValue(huabei.id))}").assertExists()
        h.snapshot(activity, "debt-person-pay-light", withDialog = true)
        compose.onNodeWithText("确认还款").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(Triple(2_000_00L, 0L, null as String?), paid)
        h.close()
        val dark = h.show(dark = true) { PersonPaySheet(wangDebt, funds, balances, bank.id, today, {}) { _, _, _, _ -> } }
        h.snapshot(dark, "debt-person-pay-dark", withDialog = true)
    }

    /** 选账户的面板：每个账户右边写余额，信用卡 / 花呗写欠多少，下面写可用额度。 */
    @Test
    fun accountPickerShowsBalancesLightAndDark() {
        val balances = com.xiguli.jinkui.data.ledger.LedgerMath.balances(data.accounts, data.transactions)
        val accounts = listOf(bank, huabei, card)
        val picked = mutableListOf<String>()
        val activity = h.show { AccountPickerSheet("选择账户", accounts, balances, onDismiss = {}) { picked += it.id } }
        compose.onNodeWithText(Money.format(balances.getValue(bank.id))).assertExists()
        val hbOwed = -balances.getValue(huabei.id)
        compose.onNodeWithText("欠 ${Money.format(hbOwed)}").assertExists()
        compose.onNodeWithText("信用卡 · 可用额度 ${Money.format(5_000_00 - hbOwed)}", substring = true).assertExists()
        h.snapshot(activity, "account-picker-balances-light", withDialog = true)
        compose.onNodeWithText("花呗").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(huabei.id), picked)
        h.close()
        val dark = h.show(dark = true) { AccountPickerSheet("转出账户", accounts, balances, onDismiss = {}) {} }
        h.snapshot(dark, "account-picker-balances-dark", withDialog = true)
    }

    /** 1.4.0-alpha02：导入一木账单（合成样例）后，总览里能看到欠款、默认账单日的花呗、没设账单日的信用卡与借贷。 */
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
        // 老王是借入：直接打开“我欠别人”那一栏
        val activity = h.show { DebtOverviewContent(o, DebtOverviewActions(), initialTab = LoanDirection.BORROW) }
        compose.onNodeWithText("我欠别人").assertExists()
        compose.onNodeWithText(Money.format(o.payable, symbol = false)).assertExists()
        compose.onNodeWithText("一共欠 ${Money.format(o.creditDebt)}", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("设置账单日 ›").performScrollTo().assertExists()
        compose.onNodeWithText("默认 · 每月 1 号出账、10 号还款，可在账单设置里修改").performScrollTo().assertExists()
        compose.onAllNodesWithText("老王").onFirst().performScrollTo().assertExists()
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
        compose.onAllNodesWithText("老王").onFirst().assertExists()
        compose.onAllNodesWithText("小李").onFirst().assertExists()
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
    /** 回归（587b7ae）：首页经过 EntryHost.actions() 之后，“借钱 / 欠款”快捷入口与到期提示条都要打开欠款页。 */
    @Test
    fun debtEntriesWorkThroughEntryHostActions() {
        val reminders = debtReminders(data, today, zone)
        var opened = 0
        val ui = buildLedgerUi(data, YearMonth.of(2026, 10), TxFilter(), zone, today)
        val actions = EntryHostState().actions(LedgerHomeActions(onDebts = { opened++ }))
        h.show { LedgerHome(ui, TxFilter(), LazyListState(), actions, debtReminders = reminders, today = today) }
        compose.onNodeWithText("${reminders.size} 笔欠款 / 账单待处理，其中 ${reminders.count { it.overdue }} 笔已逾期").assertExists().performClick()
        assertEquals(1, opened)
        compose.onAllNodesWithText("借钱 / 欠款").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(2, opened)
    }

    /** EntryHost.actions() 要把页面传进来的每一个导航回调原样转交（只有记账面板、菜单与多选由它自己接管）。 */
    @Test
    fun entryHostActionsForwardEveryBaseCallback() {
        val hits = mutableListOf<String>()
        val base = LedgerHomeActions(
            onMonth = { hits += "month" }, onQuery = { hits += "query" }, onPickAccount = { hits += "account" },
            onPickCategory = { hits += "category" }, onClearFilters = { hits += "clear" }, onStats = { hits += "stats" },
            onBudget = { hits += "budget" }, onLock = { hits += "lock" }, onMore = { hits += "more" },
            onAssets = { hits += "assets" }, onPending = { hits += "pending" }, onDebts = { hits += "debts" },
        )
        val a = EntryHostState().actions(base)
        a.onMonth(YearMonth.of(2026, 10)); a.onQuery("x"); a.onPickAccount(); a.onPickCategory(); a.onClearFilters()
        a.onStats(); a.onBudget(); a.onLock(); a.onMore(); a.onAssets(); a.onPending(); a.onDebts()
        assertEquals(listOf("month", "query", "account", "category", "clear", "stats", "budget", "lock", "more", "assets", "pending", "debts"), hits)
    }
}
