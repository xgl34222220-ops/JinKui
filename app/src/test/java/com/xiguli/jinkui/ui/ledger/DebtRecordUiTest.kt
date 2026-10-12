package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.DebtEdit
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.DebtRecordEdit
import com.xiguli.jinkui.data.ledger.DebtRecordType
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerLoan
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.PersonDebtMath
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 1.5.0-alpha03：借贷记录编辑面板（类型、对方、金额、利息、日期时间、账户、预计还款日、备注、删除），
 * 人详情里的“编辑”（改名 / 合并）与“修改欠款”。浅色 / 深色截图，合成数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class DebtRecordUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 12)
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, openingBalance = 1_000_00, position = 0)
    private val wechat = LedgerAccount("a-wx", "微信", AccountKind.WECHAT, openingBalance = 300_00, position = 1)
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, position = 2)
    private val zhou = LedgerAccount("a-zhou", "借出 · 小周", AccountKind.RECEIVABLE, position = 3)
    private val zhang = LedgerAccount("a-zhang", "借入 · 老张", AccountKind.PAYABLE, position = 4)
    private val loan = LedgerLoan("ln-1", li.id, LoanDirection.LEND, today.minusDays(30), dueDate = today.plusDays(20), createdAt = 1L)
    private val tLend = LedgerTransaction("t-lend", TxType.TRANSFER, 500_00, at(today.minusDays(30)), cash.id, toAccountId = li.id, note = "借出 · 小李", createdAt = 10L, debtId = loan.id)
    private val tCollect = LedgerTransaction("t-col", TxType.TRANSFER, 200_00, at(today.minusDays(5)), li.id, toAccountId = wechat.id, note = "收款 · 小李", createdAt = 20L, debtId = loan.id)
    private val tInterest = LedgerTransaction("t-int", TxType.INCOME, 10_00, at(today.minusDays(5)), wechat.id, note = "利息 · 小李", createdAt = 20L, debtId = loan.id)
    private val data = LedgerData(
        accounts = listOf(cash, wechat, li, zhou, zhang),
        categories = LedgerDefaults.categories(),
        transactions = listOf(tLend, tCollect, tInterest, LedgerTransaction("t-zhou", TxType.TRANSFER, 80_00, at(today.minusDays(9)), cash.id, toAccountId = zhou.id, createdAt = 30L)),
        loans = listOf(loan),
    )
    private val people = data.accounts.filter { LoanDirection.of(it.kind) != null }
    private val balances = LedgerMath.balances(data.accounts, data.transactions)

    private fun frames() {
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun click(text: String) {
        val node = compose.onAllNodesWithText(text).onFirst()
        // 对话框里的按钮不在可滚动的容器里，滚不了就直接点
        runCatching { node.performScrollTo() }
        node.performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun editLendRecordLightAndDark() {
        val record = DebtEdit.recordOf(data, tLend.id, zone)!!
        var saved: DebtRecordEdit? = null
        val light = h.show(pausedClock = true) {
            DebtRecordSheet(record, people, DebtMath.loanFunds(data.accounts), balances, today, {}, { saved = it }, {}, relatedCount = 0, zone = zone)
        }
        compose.onNodeWithText("编辑借出").assertExists()
        // 预填：类型、对方、金额、账户、预计还款日、备注
        compose.onAllNodesWithText("小李").onFirst().assertExists()
        compose.onNode(hasSetTextAction() and hasText("500")).assertExists()
        compose.onAllNodesWithText("现金 ¥", substring = true).onFirst().assertExists()
        compose.onNodeWithText("预计还款日（可选）").performScrollTo().assertExists()
        h.snapshot(light, "debt-record-edit-light", withDialog = true)
        // 改金额、换人、换账户（不经过账户）、改类型为借入
        compose.onNode(hasSetTextAction() and hasText("500")).performTextReplacement("800")
        click("小周")
        click(NO_FUND_LABEL)
        frames()
        click("保存")
        frames()
        val edit = assertNotNull(saved).let { saved!! }
        assertEquals(DebtRecordType.LEND, edit.type)
        assertEquals(800_00L, edit.amount)
        assertEquals(zhou.id, edit.personAccountId)
        assertEquals(null, edit.fundId)
        assertEquals(today.plusDays(20), edit.due)
        h.close()

        val dark = h.show(dark = true, pausedClock = true) {
            DebtRecordSheet(record, people, DebtMath.loanFunds(data.accounts), balances, today, {}, {}, {}, relatedCount = 2, zone = zone)
        }
        // 已经收过钱：类型和对方锁定，提示用合并（同随手记）
        compose.onNodeWithText("借出 · 小李").assertExists()
        compose.onNodeWithText("类型和对方不能改", substring = true).assertExists()
        h.snapshot(dark, "debt-record-edit-dark", withDialog = true)
    }

    @Test
    fun editCollectRecordChangesTypeAndShowsInterestAndDeleteConfirm() {
        val record = DebtEdit.recordOf(data, tInterest.id, zone)!!
        var saved: DebtRecordEdit? = null
        var deleted = false
        val activity = h.show(pausedClock = true) {
            DebtRecordSheet(record, people, DebtMath.loanFunds(data.accounts), balances, today, {}, { saved = it }, { deleted = true }, zone = zone)
        }
        compose.onNodeWithText("编辑收款").assertExists()
        compose.onNode(hasSetTextAction() and hasText("200")).assertExists()
        compose.onNodeWithText("另付利息（可选）").assertExists()
        compose.onNode(hasSetTextAction() and hasText("10")).assertExists()
        h.snapshot(activity, "debt-record-collect-light", withDialog = true)
        // 改成借入：对方按名字到另一边去找
        click("借入")
        frames()
        compose.onNodeWithText("约定利息（可选）").assertExists()
        click("保存")
        frames()
        assertEquals(DebtRecordType.BORROW, saved!!.type)
        assertEquals(null, saved!!.personAccountId)
        assertEquals("小李", saved!!.personName)
        click("删除")
        frames()
        compose.onNodeWithText("删除这条收款？").assertExists()
        compose.onNodeWithText("可以点“撤销”恢复", substring = true).assertExists()
        compose.onAllNodesWithText("删除").onFirst().assertExists()
        compose.onAllNodesWithText("删除")[2].performSemanticsAction(SemanticsActions.OnClick)
        frames()
        assertTrue(deleted)
    }

    @Test
    fun personEditAndOwedSheetsLightAndDark() {
        val person = PersonDebtMath.person(DebtMath.loans(data, zone), li.id, today)!!
        var renamed: String? = null
        var merged: String? = null
        val light = h.show(pausedClock = true) {
            PersonEditSheet(person, listOf(zhou), {}, { renamed = it }, { merged = it })
        }
        compose.onNodeWithText("编辑 小李").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("李雷")
        frames()
        h.snapshot(light, "person-edit-light", withDialog = true)
        click("保存名字")
        frames()
        assertEquals("李雷", renamed)
        click("小周")
        frames()
        compose.onNodeWithText("把小李合并到小周？").assertExists()
        click("合并")
        frames()
        assertEquals(zhou.id, merged)
        h.close()

        var owed: Long? = null
        val owedLight = h.show(pausedClock = true) { PersonOwedSheet(person, today, {}) { t, _ -> owed = t } }
        compose.onNodeWithText("小李现在还欠我 ${Money.format(300_00)}").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("100")
        frames()
        compose.onNodeWithText("将记一笔修改欠款 -${Money.format(200_00)}").assertExists()
        compose.onNodeWithText("不计入收入、支出与统计", substring = true).assertExists()
        h.snapshot(owedLight, "person-owed-light", withDialog = true)
        click("保存")
        frames()
        assertEquals(100_00L, owed)
        h.close()

        val darkEdit = h.show(dark = true, pausedClock = true) { PersonEditSheet(person, listOf(zhou), {}, {}, {}) }
        h.snapshot(darkEdit, "person-edit-dark", withDialog = true)
        h.close()
        val darkOwed = h.show(dark = true, pausedClock = true) { PersonOwedSheet(person, today, {}) { _, _ -> } }
        h.snapshot(darkOwed, "person-owed-dark", withDialog = true)
    }

    @Test
    fun personPageHasEditAndOwedAndRowsOpenEditor() {
        val person = PersonDebtMath.person(DebtMath.loans(data, zone), li.id, today)!!
        val hits = mutableListOf<String>()
        val activity = h.show {
            LoanPersonContent(person, today, PersonActions(onEntry = { hits += it.id }, onEditPerson = { hits += "edit" }, onOwed = { hits += "owed" }), zone)
        }
        compose.onNodeWithContentDescription("编辑小李").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("修改欠款").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("收款 ${Money.format(200_00)}").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("利息 ${Money.format(10_00)}").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("edit", "owed", tCollect.id, tInterest.id), hits)
        h.snapshot(activity, "debt-person-edit-entry-light")
    }
}
