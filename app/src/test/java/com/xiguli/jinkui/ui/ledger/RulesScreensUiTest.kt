package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
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
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerRule
import com.xiguli.jinkui.data.ledger.LedgerTemplate
import com.xiguli.jinkui.data.ledger.LedgerTransaction
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
 * 1.4.0-alpha04 规则与模板：管理页（分类规则 / 记账模板两段）、规则与模板编辑面板的校验，
 * 记一笔面板里的模板（带金额一点就记、不带金额只填好）与按备注给出的规则建议。浅色 / 深色截图，合成数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class RulesScreensUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val noon = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, position = 0)
    private val wechat = LedgerAccount("a-wx", "微信", AccountKind.WECHAT, position = 1)
    private val takeout = LedgerRule("r-takeout", listOf("美团", "饿了么"), TxType.EXPENSE, categoryId = "cat-food-3", tags = listOf("外卖"), position = 0)
    private val subway = LedgerRule("r-subway", listOf("地铁"), maxAmount = 10_00, categoryId = "cat-transport-1", accountId = wechat.id, position = 1, enabled = false)
    private val breakfast = LedgerTemplate("tp-1", "早饭", amount = 8_00, categoryId = "cat-food-1", accountId = cash.id, note = "包子豆浆", position = 0)
    private val rent = LedgerTemplate("tp-2", "交房租", categoryId = "cat-housing-1", accountId = wechat.id, note = "十月房租", position = 1)
    private val topUp = LedgerTemplate("tp-3", "充饭卡", TxType.TRANSFER, 100_00, cash.id, wechat.id, position = 2)

    private val data = LedgerData(
        accounts = listOf(cash, wechat),
        categories = LedgerDefaults.categories(),
        rules = listOf(takeout, subway),
        templates = listOf(breakfast, rent, topUp),
    )

    private fun clickText(text: String) = compose.onAllNodesWithText(text).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    private fun key(d: Char) = compose.onAllNodes(hasText(d.toString()).and(hasClickAction())).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun rulesScreenLightAndDark() {
        val ui = buildRulesUi(data)
        assertEquals("→ 餐饮 · 晚餐 · #外卖", ui.rules[0].subtitle.substringAfter("支出　"))
        assertEquals("每次输入", ui.templates[1].amount)
        var toggled: Pair<String, Boolean>? = null
        var moved: Pair<String, Int>? = null
        var edited: String? = null
        val actions = RulesActions(
            onToggleRule = { r, on -> toggled = r.id to on },
            onMoveRule = { r, d -> moved = r.id to d },
            onEditRule = { edited = it.id },
        )
        val activity = h.show { RulesContent(ui, RulesTab.RULES, {}, actions) }
        compose.onNodeWithText("2 条分类规则").assertExists()
        compose.onNodeWithText("美团 / 饿了么").assertExists()
        compose.onNodeWithText("地铁").assertExists()
        h.snapshot(activity, "rules-light")
        compose.onNodeWithContentDescription("下移").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(takeout.id to 1, moved)
        compose.onNodeWithContentDescription("上移").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(subway.id to -1, moved)
        clickText("美团 / 饿了么")
        assertEquals(takeout.id, edited)
        h.close()
        val dark = h.show(dark = true) { RulesContent(ui, RulesTab.TEMPLATES, {}, actions) }
        compose.onNodeWithText("3 个记账模板").assertExists()
        compose.onNodeWithText("交房租").assertExists()
        compose.onNodeWithText("每次输入").assertExists()
        h.snapshot(dark, "rules-templates-dark")
        assertNull(toggled)
    }

    @Test
    fun ruleEditSheetValidatesLightAndDark() {
        var saved: LedgerRule? = null
        val activity = h.show {
            RuleEditSheet(LedgerRule("r-new", emptyList(), TxType.EXPENSE), data, isNew = true, onDismiss = {}, onSave = { saved = it }, onDelete = {})
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("添加分类规则").assertExists()
        clickText("保存")
        compose.waitForIdle()
        compose.onNodeWithText("请至少填一个关键词").assertExists()
        compose.onNode(hasSetTextAction().and(hasText("空格或逗号分隔", substring = true))).performTextInput("星巴克 瑞幸")
        compose.waitForIdle()
        clickText("保存")
        compose.waitForIdle()
        compose.onNodeWithText("请至少选择分类、账户或标签中的一项").assertExists()
        clickText("微信")
        compose.waitForIdle()
        h.snapshot(activity, "rule-edit-light", withDialog = true)
        clickText("保存")
        compose.waitForIdle()
        assertEquals(listOf("星巴克", "瑞幸"), saved?.keywords)
        assertEquals(wechat.id, saved?.accountId)
        assertEquals(TxType.EXPENSE, saved?.type)
        h.close()
        val dark = h.show(dark = true) { RuleEditSheet(takeout, data, isNew = false, onDismiss = {}, onSave = {}, onDelete = {}) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("编辑分类规则").assertExists()
        compose.onNodeWithText("餐饮 · 晚餐").assertExists()
        h.snapshot(dark, "rule-edit-dark", withDialog = true)
    }

    @Test
    fun templateEditSheetLightAndDark() {
        var saved: LedgerTemplate? = null
        val activity = h.show {
            TemplateEditSheet(LedgerTemplate("tp-new", ""), data, isNew = true, onDismiss = {}, onSave = { saved = it }, onDelete = {})
        }
        compose.mainClock.advanceTimeBy(1_000)
        clickText("保存")
        compose.waitForIdle()
        compose.onNodeWithText("请填写模板名称").assertExists()
        compose.onNode(hasSetTextAction().and(hasText("例如：早餐", substring = true))).performTextInput("地铁")
        compose.onNode(hasSetTextAction().and(hasText("不填则每次输入", substring = true))).performTextInput("4")
        compose.waitForIdle()
        h.snapshot(activity, "template-edit-light", withDialog = true)
        clickText("保存")
        compose.waitForIdle()
        assertEquals("地铁", saved?.name)
        assertEquals(4_00L, saved?.amount)
        h.close()
        val dark = h.show(dark = true) { TemplateEditSheet(topUp, data, isNew = false, onDismiss = {}, onSave = {}, onDelete = {}) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("转入账户").assertExists()
        h.snapshot(dark, "template-edit-dark", withDialog = true)
    }

    @Test
    fun entrySheetTemplatesAndRuleSuggestion() {
        val saved = mutableListOf<Pair<LedgerTransaction, Boolean>>()
        val messages = mutableListOf<String>()
        val activity = h.show {
            EntrySheet(
                data = data, existing = null, onDismiss = {}, onSave = { tx, keep, _ -> saved += tx to keep }, onDelete = {},
                onMessage = { messages += it }, initialTime = noon,
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        // 模板一排：带金额的显示金额，不带的显示“…”
        compose.onNodeWithText("早饭").assertExists()
        compose.onNodeWithText("¥8.00").assertExists()
        h.snapshot(activity, "entry-templates-light", withDialog = true)
        // 带金额：点一下直接记下（不继续记）
        clickText("早饭")
        compose.waitForIdle()
        val (tx, keep) = saved.single()
        assertEquals(8_00L, tx.amount)
        assertEquals("cat-food-1", tx.categoryId)
        assertEquals(cash.id, tx.accountId)
        assertEquals("包子豆浆", tx.note)
        assertEquals(false, keep)
        // 不带金额：只填好，提示输入金额
        clickText("交房租")
        compose.waitForIdle()
        assertEquals(1, saved.size)
        assertEquals("已按模板“交房租”填好，输入金额即可", messages.last())
        compose.onNodeWithText("十月房租").assertExists()
        h.close()

        // 规则建议：备注里有“美团”，给出“餐饮 · 晚餐 · #外卖”，点“套用”后保存
        saved.clear()
        val dark = h.show(dark = true) {
            EntrySheet(
                data = data, existing = null, onDismiss = {}, onSave = { t, k, _ -> saved += t to k }, onDelete = {},
                onMessage = {}, initialTime = noon,
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(hasSetTextAction().and(hasText("添加备注", substring = true))).performTextInput("美团 黄焖鸡")
        compose.waitForIdle()
        compose.onNodeWithText("按规则「美团」：餐饮 · 晚餐 · #外卖").assertExists()
        h.snapshot(dark, "entry-rule-suggestion-dark", withDialog = true)
        clickText("套用")
        compose.waitForIdle()
        "32".forEach(::key)
        compose.onNodeWithContentDescription("保存").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val ruled = saved.single().first
        assertEquals("cat-food-3", ruled.categoryId)
        assertTrue("外卖" in ruled.tags)
        assertEquals(32_00L, ruled.amount)
    }
}
