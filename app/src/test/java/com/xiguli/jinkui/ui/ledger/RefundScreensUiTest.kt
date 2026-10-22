package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.RefundMath
import com.xiguli.jinkui.data.ledger.Reimburse
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** 1.4.0-alpha04 退款与报销的界面：报销页（待报销多选、已报销撤回）、退款面板、标记已报销面板，浅色 / 深色截图。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class RefundScreensUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 11)
    private fun at(d: Int) = today.withDayOfMonth(d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private val bank = LedgerAccount("a-bank", "示例银行卡", AccountKind.BANK, openingBalance = 5_000_00, position = 0)
    private val wechat = LedgerAccount("a-wx", "微信", AccountKind.WECHAT, position = 1)
    private val taxi = LedgerTransaction("e-taxi", TxType.EXPENSE, 86_00, at(3), wechat.id, categoryId = "cat-transport-2", note = "去客户公司", reimburse = Reimburse.PENDING)
    private val hotel = LedgerTransaction("e-hotel", TxType.EXPENSE, 420_00, at(5), bank.id, categoryId = "cat-fun-3", note = "出差住宿", reimburse = Reimburse.PENDING)
    private val dinner = LedgerTransaction("e-dinner", TxType.EXPENSE, 260_00, at(1), bank.id, categoryId = "cat-social-3", note = "招待", reimburse = Reimburse.DONE)
    private val shoes = LedgerTransaction("e-shoes", TxType.EXPENSE, 399_00, at(6), bank.id, categoryId = "cat-shopping-2", note = "跑鞋")
    private val refund = LedgerTransaction("i-ref", TxType.INCOME, 100_00, at(8), bank.id, categoryId = "cat-refund", refundOf = shoes.id)

    private val data = LedgerData(
        accounts = listOf(bank, wechat),
        categories = LedgerDefaults.categories(),
        transactions = listOf(taxi, hotel, dinner, shoes, refund),
    )

    private fun clickText(text: String) = compose.onAllNodesWithText(text).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun reimburseScreenLightAndDark() {
        val ui = buildReimburseUi(data, zone)
        assertEquals(506_00L, ui.overview.pendingTotal)
        var marked: List<String> = emptyList()
        var reverted: String? = null
        val activity = h.show { ReimburseContent(ui, ReimburseActions(onMark = { txs -> marked = txs.map { it.id } }, onRevert = { reverted = it.id })) }
        compose.onNodeWithText("待报销(元)").assertExists()
        compose.onNodeWithText("506.00").assertExists()
        compose.onNodeWithText("2 笔待报销 · 已报销 1 笔，共 ¥260.00").assertExists()
        clickText("全选")
        compose.waitForIdle()
        h.snapshot(activity, "reimburse-light")
        clickText("标记已报销（2 笔 ¥506.00）")
        assertEquals(listOf(hotel.id, taxi.id), marked)
        clickText("撤回")
        assertEquals(dinner.id, reverted)
        h.close()
        val dark = h.show(dark = true) { ReimburseContent(ui, ReimburseActions(), initialSelection = setOf(taxi.id)) }
        compose.onNodeWithText("标记已报销（1 笔 ¥86.00）").assertExists()
        h.snapshot(dark, "reimburse-dark")
    }

    @Test
    fun refundSheetLightAndDark() {
        val status = RefundMath.status(shoes, data.transactions)
        var submitted: Pair<Long, String>? = null
        val activity = h.show {
            RefundSheet(status, "购物 · 服饰鞋包", refundAccounts(data), today, onDismiss = {}) { amount, account, _, _ -> submitted = amount to account }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("购物 · 服饰鞋包 · 原支出 ¥399.00，已退 ¥100.00").assertExists()
        compose.onNodeWithText("全额 ¥299.00").assertExists()
        h.snapshot(activity, "refund-sheet-light", withDialog = true)
        clickText("确定退款")
        assertEquals(299_00L to bank.id, submitted)
        h.close()
        val dark = h.show(dark = true) { RefundSheet(status, "购物 · 服饰鞋包", refundAccounts(data), today, onDismiss = {}) { _, _, _, _ -> } }
        compose.mainClock.advanceTimeBy(1_000)
        h.snapshot(dark, "refund-sheet-dark", withDialog = true)
    }

    @Test
    fun markReimbursedSheetLightAndDark() {
        var result: String? = "unset"
        val activity = h.show {
            MarkReimbursedSheet(listOf(taxi, hotel), fundAccounts(data), today, onDismiss = {}) { account, _ -> result = account }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("2 笔，合计 ¥506.00").assertExists()
        h.snapshot(activity, "reimburse-mark-sheet-light", withDialog = true)
        clickText("确定")
        assertEquals(bank.id, result)
        clickText("只标记（冲抵）")
        compose.waitForIdle()
        clickText("确定")
        assertEquals(null, result)
        h.close()
        val dark = h.show(dark = true) { MarkReimbursedSheet(listOf(taxi), fundAccounts(data), today, onDismiss = {}) { _, _ -> } }
        compose.mainClock.advanceTimeBy(1_000)
        h.snapshot(dark, "reimburse-mark-sheet-dark", withDialog = true)
    }
}
