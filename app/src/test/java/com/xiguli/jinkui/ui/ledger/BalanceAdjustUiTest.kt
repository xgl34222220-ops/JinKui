package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.BalanceAdjustMode
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 1.5.0-alpha01 修改余额：现金 10000 改成 0 → 记一笔“余额调整 -¥10,000.00”，默认不计入收支；
 * 账户列表长按打开修改余额；流水里显示“余额调整”。浅色 / 深色截图，合成数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class BalanceAdjustUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, openingBalance = 10_000_00, position = 0)
    private val card = LedgerAccount("a-cc", "示例信用卡", AccountKind.CREDIT, openingBalance = -1_200_00, position = 1)
    private val adjust = LedgerAccount("a-adj", "余额调整", AccountKind.ADJUST, archived = true, position = 2)

    private fun click(text: String) = compose.onAllNodesWithText(text).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun cashToZeroSheetLightAndDark() {
        var saved: Pair<Long, BalanceAdjustMode>? = null
        val light = h.show(pausedClock = true) {
            BalanceAdjustSheet(cash, 10_000_00L, emptyList(), {}, { t, m -> saved = t to m })
        }
        compose.onNodeWithText("现金 · 现在余额 ¥10,000.00").assertExists()
        compose.onNodeWithText("改成多少（元）").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("将记一笔余额调整 -¥10,000.00").assertExists()
        compose.onNodeWithText("只把余额改对，本月收入、支出与统计都不变（推荐）").assertExists()
        h.snapshot(light, "balance-adjust-sheet-light", withDialog = true)
        click("保存")
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0L to BalanceAdjustMode.NOT_COUNTED, saved)
        h.close()

        val recent = listOf(RecentAdjust("t-adj", -10_000_00L, "2026年10月12日 · 不计入收支"))
        var undone: String? = null
        val dark = h.show(dark = true, pausedClock = true) {
            BalanceAdjustSheet(cash, 0L, recent, {}, { _, _ -> }, onUndo = { undone = it })
        }
        compose.onNodeWithText("余额调整 -¥10,000.00").assertExists()
        h.snapshot(dark, "balance-adjust-sheet-dark", withDialog = true)
        click("撤销")
        compose.mainClock.advanceTimeByFrame()
        assertEquals("t-adj", undone)
    }

    @Test
    fun creditCardEditsCurrentDebtAndCanCountAsExpense() {
        var saved: Pair<Long, BalanceAdjustMode>? = null
        h.show(pausedClock = true) { BalanceAdjustSheet(card, -1_200_00L, emptyList(), {}, { t, m -> saved = t to m }) }
        compose.onNodeWithText("示例信用卡 · 现在欠 ¥1,200.00").assertExists()
        compose.onNodeWithText("现在欠多少（元）").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("800")
        click("记为收入 / 支出")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("将记一笔余额调整 +¥400.00").assertExists()
        click("保存")
        compose.mainClock.advanceTimeByFrame()
        assertEquals(-800_00L to BalanceAdjustMode.AS_INCOME_EXPENSE, saved)
    }

    @Test
    fun accountsLongPressOpensAdjustAndFlowShowsAdjustRow() {
        val tx = LedgerTransaction("t-adj", TxType.TRANSFER, 10_000_00, 1L, cash.id, toAccountId = adjust.id, note = "余额调整")
        val data = LedgerData(accounts = listOf(cash, card, adjust), transactions = listOf(tx))
        val ui = buildAccountsUi(data)
        // 内部“余额调整”账户不出现在列表里；现金余额是 0，最近一次调整可以撤销
        assertEquals(listOf(cash.id, card.id), ui.accounts.map { it.id })
        assertEquals(0L, ui.balances[cash.id])
        assertEquals(-10_000_00L, ui.recentAdjusts[cash.id]!!.single().delta)
        var adjusted: String? = null
        h.show { LedgerAccountsContent(ui, emptyList(), {}, {}, {}, onAdjust = { adjusted = it.id }) }
        compose.onNodeWithText("现金").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
        assertEquals(cash.id, adjusted)
        val row = txRow(tx, data.accounts.associateBy { it.id }, emptyMap(), ZoneId.of("Asia/Shanghai"))
        assertEquals("余额调整", row.title)
        assertEquals("-¥10,000.00", row.amountText)
    }
}
