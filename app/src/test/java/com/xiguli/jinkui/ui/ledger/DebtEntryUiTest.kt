package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerLoan
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 1.4.0-alpha04：借钱给别人 / 欠别人钱要一眼找得到——记一笔面板里的“借贷”（借出 / 借入 / 收款 / 还款），
 * 账本首页的快捷入口，以及欠款页第一次打开时的空状态（记一笔借出 / 记一笔借入 / 添加信用卡）。浅色 / 深色截图，合成数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class DebtEntryUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.now(zone)
    private val noon = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private val bank = LedgerAccount("a-bank", "示例银行卡", AccountKind.BANK, openingBalance = 5_000_00, position = 0)
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, position = 1)
    private val liLoan = LedgerLoan("ln-li", li.id, LoanDirection.LEND, today.minusDays(20))

    private val data = LedgerData(
        accounts = listOf(bank, li),
        categories = LedgerDefaults.categories(),
        transactions = listOf(
            LedgerTransaction(
                "t-lend", TxType.TRANSFER, 800_00, today.minusDays(20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                bank.id, li.id, debtId = liLoan.id,
            ),
        ),
        loans = listOf(liLoan),
    )

    private fun clickText(text: String) = compose.onAllNodesWithText(text).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    /** 数字键盘上的键（金额显示里也有同样的数字，所以只找能点的那个）。 */
    private fun key(d: Char) = compose.onAllNodes(hasText(d.toString()).and(hasClickAction())).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    private fun save() {
        compose.onNodeWithContentDescription("保存").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun entrySheetRecordsLendAndCollect() {
        val requests = mutableListOf<LoanEntryRequest>()
        val messages = mutableListOf<String>()
        val activity = h.show {
            EntrySheet(
                data = data, existing = null, onDismiss = {}, onSave = { _, _, _ -> }, onDelete = {},
                onMessage = { messages += it }, initialTime = noon,
                onLoan = { requests += it }, initialLoan = LoanAction.LEND,
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        // 第四个分段是“借贷”；借出 / 借入 / 收款 / 还款四个类型都看得见
        for (label in listOf("借贷", "借出", "借入", "收款", "还款")) compose.onAllNodesWithText(label).onFirst().assertExists()
        compose.onNodeWithText("借钱给别人，别人欠我").assertExists()
        compose.onNodeWithText("预计还款日（可选）").assertExists()
        // 借出：没填对方时提示
        "300".forEach(::key)
        save()
        assertEquals("请填写对方", messages.last())
        // 已有的对方“小李”可以直接点；也可以填一个新名字
        compose.onNode(hasSetTextAction().and(hasText("对方（例如：小李）", substring = true))).performTextInput("小陈")
        compose.waitForIdle()
        h.snapshot(activity, "entry-loan-lend-light", withDialog = true)
        save()
        val lend = requests.single()
        assertEquals(LoanAction.LEND, lend.action)
        assertEquals(LoanDirection.LEND, lend.action.direction)
        assertEquals("小陈", lend.person)
        assertNull(lend.accountId)
        assertEquals(300_00L, lend.amount)
        assertEquals(bank.id, lend.fundId)

        // 收款：选没结清的借出，金额自动填成还剩的 800
        clickText("收款")
        compose.waitForIdle()
        compose.onNodeWithText("别人还我钱").assertExists()
        compose.onNodeWithText("小李 · 剩", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        save()
        val collect = requests.last()
        assertEquals(LoanAction.COLLECT, collect.action)
        assertEquals(liLoan.id, collect.loanId)
        assertEquals(li.id, collect.accountId)
        assertEquals(800_00L, collect.amount)
        h.close()

        // 深色：还款（没有借入时给出说明）
        val dark = h.show(dark = true) {
            EntrySheet(
                data = data, existing = null, onDismiss = {}, onSave = { _, _, _ -> }, onDelete = {},
                onMessage = {}, initialTime = noon, onLoan = {}, initialLoan = LoanAction.REPAY,
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("没有未结清的借入。先记一笔“借入”。").assertExists()
        h.snapshot(dark, "entry-loan-repay-dark", withDialog = true)
    }

    /**
     * 用户反馈“借款只能在现金，我早就花完了，都是欠账”：资金账户可以不选——默认“不经过账户（只记欠款）”，
     * 可选的账户是全部在用的资金账户（现金、银行卡、微信、支付宝、花呗 / 信用卡）。
     */
    @Test
    fun entrySheetLoanWithoutFundAccountLightAndDark() {
        val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, position = 2)
        val wx = LedgerAccount("a-wx", "微信", AccountKind.WECHAT, position = 3)
        val ali = LedgerAccount("a-ali", "支付宝", AccountKind.ALIPAY, position = 4)
        val huabei = LedgerAccount("a-hb", "花呗", AccountKind.CREDIT, position = 5)
        // 没有借贷流水：没有“上次的选择”，默认不经过账户
        val fresh = LedgerData(accounts = listOf(cash, bank, wx, ali, huabei), categories = LedgerDefaults.categories())
        val requests = mutableListOf<LoanEntryRequest>()
        val activity = h.show {
            EntrySheet(
                data = fresh, existing = null, onDismiss = {}, onSave = { _, _, _ -> }, onDelete = {},
                onMessage = {}, initialTime = noon, onLoan = { requests += it }, initialLoan = LoanAction.BORROW,
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(NO_FUND_LABEL).assertExists().assertIsSelected()
        for (name in listOf("现金", "示例银行卡", "微信", "支付宝", "花呗")) compose.onAllNodesWithText(name).onFirst().assertExists()
        compose.onNodeWithText(noFundHint(LoanAction.BORROW)).assertExists()
        compose.onNode(hasSetTextAction().and(hasText("对方（例如：小李）", substring = true))).performTextInput("老张")
        "3000".forEach(::key)
        compose.waitForIdle()
        h.snapshot(activity, "entry-loan-nofund-light", withDialog = true)
        save()
        val none = requests.single()
        assertEquals(LoanAction.BORROW, none.action)
        assertNull(none.fundId)
        assertEquals(3_000_00L, none.amount)
        // 也可以选花呗等任何资金账户
        clickText("花呗")
        compose.waitForIdle()
        save()
        assertEquals(huabei.id, requests.last().fundId)
        h.close()

        val dark = h.show(dark = true) {
            EntrySheet(
                data = fresh, existing = null, onDismiss = {}, onSave = { _, _, _ -> }, onDelete = {},
                onMessage = {}, initialTime = noon, onLoan = {}, initialLoan = LoanAction.LEND,
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText(NO_FUND_LABEL).assertIsSelected()
        h.snapshot(dark, "entry-loan-nofund-dark", withDialog = true)
    }

    @Test
    fun loanTargetsAndValidation() {
        assertEquals(listOf("小李"), LoanEntryMath.targets(data, LoanAction.LEND, today, zone).map { it.person })
        val collect = LoanEntryMath.targets(data, LoanAction.COLLECT, today, zone).single()
        assertEquals(800_00L, collect.remaining)
        assertTrue(LoanEntryMath.targets(data, LoanAction.REPAY, today, zone).isEmpty())
        val base = LoanEntryRequest(LoanAction.COLLECT, "小李", li.id, liLoan.id, 900_00, bank.id, today, null, 0, "")
        assertEquals("超过了还剩的 ¥800.00", LoanEntryMath.validate(base, collect))
        assertNull(LoanEntryMath.validate(base.copy(amount = 500_00), collect))
        assertEquals("请选择要收款的借出", LoanEntryMath.validate(base.copy(loanId = null), null))
        assertEquals(
            "预计还款日不能早于借款日期",
            LoanEntryMath.validate(base.copy(action = LoanAction.LEND, loanId = null, due = today.minusDays(1)), null),
        )
    }

    @Test
    fun debtEmptyStateLightAndDark() {
        val empty = DebtMath.overview(LedgerData(accounts = listOf(bank), categories = LedgerDefaults.categories()), today, zone)
        val opened = mutableListOf<String>()
        val activity = h.show {
            DebtOverviewContent(empty, DebtOverviewActions(onNewLoan = { opened += it.key }, onAddCredit = { opened += "credit" }))
        }
        compose.onNodeWithText("还没有欠款与借贷").assertExists()
        h.snapshot(activity, "debts-empty-light")
        clickText("记一笔借出")
        clickText("记一笔借入")
        clickText("添加信用卡")
        assertEquals(listOf("lend", "borrow", "credit"), opened)
        h.close()
        val dark = h.show(dark = true) { DebtOverviewContent(empty, DebtOverviewActions()) }
        compose.onNodeWithText("还没有欠款与借贷").assertExists()
        h.snapshot(dark, "debts-empty-dark")
    }

    @Test
    fun ledgerHomeShowsDebtShortcuts() {
        var loan = 0
        var debts = 0
        val activity = h.show { DebtShortcuts(onLoan = { loan++ }, onDebts = { debts++ }) }
        clickText("借出 / 借入")
        clickText("欠款 / 账单")
        assertEquals(1, loan)
        assertEquals(1, debts)
        h.snapshot(activity, "ledger-debt-shortcuts-light")
    }
}
