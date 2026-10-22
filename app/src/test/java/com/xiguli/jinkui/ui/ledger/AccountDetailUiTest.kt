package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.CreditSettings
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 1.5.0-alpha02 账户详情：余额、本月流入 / 流出、这个账户自己的流水（按天分组，转账写方向，余额调整也在里面），
 * 四个按钮；信用卡另有当前欠款、可用额度、账单日 / 还款日与“还款”。浅色 / 深色截图，合成数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class AccountDetailUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val adjust = LedgerAccount("a-adj", "余额调整", AccountKind.ADJUST, archived = true, position = 9)
    private val card = LedgerAccount(
        "a-cc", "示例信用卡", AccountKind.CREDIT, position = 4,
        credit = CreditSettings(statementDay = 5, dueDay = 23, limit = 10_000_00),
    )

    private val data by lazy {
        val base = LedgerSamples.data
        base.copy(
            accounts = base.accounts + adjust + card,
            transactions = base.transactions +
                LedgerTransaction("t-adj", TxType.TRANSFER, 100_00, LedgerSamples.at(10, 10, 9, 0), adjust.id, toAccountId = "a-bank", note = "余额调整") +
                LedgerTransaction("t-cc1", TxType.EXPENSE, 1_200_00, LedgerSamples.at(10, 7, 19, 30), card.id, categoryId = "cat-shopping-2", note = "外套") +
                LedgerTransaction("t-cc2", TxType.EXPENSE, 88_00, LedgerSamples.at(10, 10, 13, 0), card.id, categoryId = "cat-food-2"),
        )
    }

    private fun click(text: String) = compose.onAllNodesWithText(text).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun bankDetailModel() {
        val ui = buildAccountDetailUi(data, "a-bank", LedgerSamples.zone, LedgerSamples.today)!!
        // 期初 10000 + 两个月工资 37000 − 转出 2000 − 房租 3800 + 余额调整 100
        assertEquals(4_130_000L, ui.balance)
        assertEquals(1_850_000L + 100_00L, ui.monthIn)
        assertEquals(200_000L + 380_000L, ui.monthOut)
        assertNull(ui.credit)
        assertEquals(5, ui.count)
        val today = ui.days.first()
        assertEquals("今天", today.label)
        assertEquals("余额调整", today.rows.single().title)
        assertEquals("+¥100.00", today.rows.single().amountText)
        val oct9 = ui.days.first { it.date.dayOfMonth == 9 && it.date.monthValue == 10 }
        assertEquals(1_850_000L, oct9.inflow)
        assertEquals(200_000L, oct9.outflow)
        val transfer = oct9.rows.first { it.tx.type == TxType.TRANSFER }
        assertEquals("转出", transfer.title)
        assertEquals("到 微信", transfer.where)
        assertEquals("-¥2,000.00", transfer.amountText)
        // 微信那边是“从 招商银行 转入”
        val wechat = buildAccountDetailUi(data, "a-wechat", LedgerSamples.zone, LedgerSamples.today)!!
        val incoming = wechat.days.flatMap { it.rows }.first { it.tx.type == TxType.TRANSFER }
        assertEquals("转入", incoming.title)
        assertEquals("从 招商银行", incoming.where)
        assertEquals("+¥2,000.00", incoming.amountText)
        assertNull(buildAccountDetailUi(data, "nope", LedgerSamples.zone, LedgerSamples.today))
    }

    @Test
    fun bankDetailLightAndDark() {
        val ui = buildAccountDetailUi(data, "a-bank", LedgerSamples.zone, LedgerSamples.today)!!
        val clicked = mutableListOf<String>()
        val actions = AccountDetailActions(
            onRecord = { clicked += "record" },
            onTransfer = { clicked += "transfer" },
            onAdjust = { clicked += "adjust" },
            onEdit = { clicked += "edit" },
            onOpen = { clicked += "open:" + it.id },
        )
        val light = h.show { AccountDetailContent(ui, actions) }
        compose.onNodeWithText("余额(元) · 银行卡").assertExists()
        compose.onNodeWithText("41,300.00").assertExists()
        compose.onNodeWithText("本月流入").assertExists()
        compose.onNodeWithText("本月流出").assertExists()
        compose.onNodeWithText("这个账户的流水").assertExists()
        compose.onNodeWithText("共 5 笔").assertExists()
        h.snapshot(light, "account-detail-light")
        click("记一笔")
        click("转账")
        click("修改余额")
        click("编辑")
        compose.onAllNodesWithText("余额调整").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf("record", "transfer", "adjust", "edit", "open:t-adj"), clicked)
        h.close()
        val dark = h.show(dark = true) { AccountDetailContent(ui, actions) }
        h.snapshot(dark, "account-detail-dark")
    }

    @Test
    fun creditDetailLightAndDark() {
        val ui = buildAccountDetailUi(data, card.id, LedgerSamples.zone, LedgerSamples.today)!!
        assertNotNull(ui.credit)
        val credit = ui.credit!!
        assertEquals(1_288_00L, credit.debt)
        assertEquals(10_000_00L, credit.limit)
        assertEquals(10_000_00L - 1_288_00L, credit.available)
        assertEquals("每月 5 号", credit.statementDay)
        assertEquals("每月 23 号", credit.dueDay)
        var repay = 0
        var statements = 0
        val light = h.show { AccountDetailContent(ui, AccountDetailActions(onRepay = { repay++ }, onStatements = { statements++ })) }
        compose.onNodeWithText("当前欠款(元) · 信用卡").assertExists()
        compose.onAllNodesWithText("1,288.00").assertCountEquals(2) // 当前欠款与本月流出
        compose.onNodeWithText("可用额度").assertExists()
        compose.onNodeWithText("8,712.00").assertExists()
        compose.onNodeWithText("账单日").assertExists()
        compose.onNodeWithText("每月 23 号").assertExists()
        h.snapshot(light, "account-detail-credit-light")
        click("还款")
        click("账单与分期")
        compose.waitForIdle()
        assertEquals(1, repay)
        assertEquals(1, statements)
        h.close()
        val dark = h.show(dark = true) { AccountDetailContent(ui, AccountDetailActions()) }
        h.snapshot(dark, "account-detail-credit-dark")
    }

    @Test
    fun hiddenAmountsAndEmptyAccount() {
        val ui = buildAccountDetailUi(data, "a-cash", LedgerSamples.zone, LedgerSamples.today)!!
        assertTrue(ui.days.isEmpty())
        h.show { AccountDetailContent(ui, AccountDetailActions()) }
        compose.onNodeWithText("这个账户还没有流水").assertExists()
        click("隐藏")
        compose.waitForIdle()
        compose.onAllNodesWithText("****").onFirst().assertExists()
    }

    @Test
    fun entrySheetPrefillsAccountFromDetail() {
        h.show(pausedClock = true) {
            EntrySheet(
                data = data, existing = null, onDismiss = {}, onSave = { _, _, _ -> }, onDelete = {},
                onMessage = {}, initialTime = LedgerSamples.at(10, 10, 12, 0), initialAccount = card.id,
            )
        }
        compose.onAllNodesWithText("示例信用卡", substring = true).onFirst().assertExists()
    }
}
