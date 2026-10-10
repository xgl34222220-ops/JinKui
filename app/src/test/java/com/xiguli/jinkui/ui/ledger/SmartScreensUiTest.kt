package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xiguli.jinkui.core.auto.PaySource
import com.xiguli.jinkui.data.importer.BillImport
import com.xiguli.jinkui.data.importer.BillParser
import com.xiguli.jinkui.data.importer.BillSamples
import com.xiguli.jinkui.data.importer.BillSource
import com.xiguli.jinkui.data.importer.TableReader
import com.xiguli.jinkui.data.ledger.PendingEntry
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.ledger.TxFilter
import com.xiguli.jinkui.data.repo.AppSettings
import com.xiguli.jinkui.data.repo.FontMode
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import com.xiguli.jinkui.ui.me.MeActions
import com.xiguli.jinkui.ui.me.MeContent
import com.xiguli.jinkui.ui.me.buildMeUi
import com.xiguli.jinkui.ui.settings.AutoBookkeepingActions
import com.xiguli.jinkui.ui.settings.AutoBookkeepingContent
import com.xiguli.jinkui.ui.settings.FontSettingsActions
import com.xiguli.jinkui.ui.settings.FontSettingsContent
import com.xiguli.jinkui.ui.settings.SettingsActions
import com.xiguli.jinkui.ui.settings.SettingsContent
import com.xiguli.jinkui.ui.theme.FontConfig
import com.xiguli.jinkui.ui.theme.JkFonts
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** 1.2 新增的页面：字体、导入账单（说明 / 列对应 / 预览 / 完成）、待确认、自动记账，以及首页与设置里的新入口。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class SmartScreensUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private fun wechatPreview() = BillImport.preview(
        BillParser.parse(TableReader.read(BillSamples.wechatCsv(), "a.csv"), BillSource.WECHAT),
        LedgerSamples.data.copy(transactions = emptyList()),
        LedgerSamples.zone,
        now = 1L,
    )

    // ── 字体 ──────────────────────────────────────────────────────

    @Test
    fun fontSettingsLightRoundedDarkAndFontScale() {
        val picked = mutableListOf<FontMode>()
        val activity = h.show {
            FontSettingsContent(FontMode.SYSTEM, customName = null, importing = false, actions = FontSettingsActions(onSelect = { picked += it }))
        }
        compose.onNodeWithText("跟随系统字体").assertExists()
        compose.onNodeWithText("默认").assertExists()
        compose.onNodeWithText("还没有导入", substring = true).assertExists()
        h.snapshot(activity, "font-settings-light")
        compose.onNodeWithText("圆润数字").performClick()
        assertEquals(listOf(FontMode.ROUNDED), picked)
        h.close()

        JkFonts.config = FontConfig.Rounded
        val rounded = h.show { FontSettingsContent(FontMode.ROUNDED, customName = "示例字体", importing = false, actions = FontSettingsActions()) }
        compose.onNodeWithText("「示例字体」用于全部文字").assertExists()
        compose.onNodeWithText("移除").assertExists()
        h.snapshot(rounded, "font-settings-rounded")
        h.close()

        val dark = h.show(dark = true) { FontSettingsContent(FontMode.SYSTEM, null, false, FontSettingsActions()) }
        h.snapshot(dark, "font-settings-dark")
        h.close()

        RuntimeEnvironment.setFontScale(1.3f)
        val big = h.show { FontSettingsContent(FontMode.SYSTEM, null, false, FontSettingsActions()) }
        h.snapshot(big, "font-settings-font130")
    }

    // ── 导入账单 ──────────────────────────────────────────────────

    @Test
    fun importIntroLightAndDark() {
        var picks = 0
        val activity = h.show { BillImportIntro(reading = false, onBack = {}, onPick = { picks++ }) }
        compose.onNodeWithText("选择账单文件").performClick()
        assertEquals(1, picks)
        compose.onNodeWithText("微信怎么导出").assertExists()
        h.snapshot(activity, "import-intro-light")
        h.close()
        val dark = h.show(dark = true) { BillImportIntro(reading = false, onBack = {}, onPick = {}) }
        h.snapshot(dark, "import-intro-dark")
    }

    @Test
    fun importPreviewListsRowsAndToggles() {
        val preview = wechatPreview()
        val toggled = mutableListOf<String>()
        var committed = 0
        val activity = h.show {
            BillPreviewContent(
                preview, LedgerSamples.data, saving = false,
                BillPreviewActions(onToggle = { toggled += it }, onCommit = { committed++ }),
            )
        }
        compose.onNodeWithText("微信支付账单").assertExists()
        compose.onNodeWithText("导入 6 笔").assertExists()
        compose.onNodeWithText("示例面馆 · 牛肉面").assertExists()
        compose.onNodeWithText("不计收支", substring = true).assertExists()
        // 先点再截图（列表里的点按放在截图之前，截图会推进测试时钟）
        compose.onNodeWithText("示例面馆 · 牛肉面").performClick()
        assertEquals(1, toggled.size)
        compose.onNodeWithText("导入 6 笔").performClick()
        assertEquals(1, committed)
        h.snapshot(activity, "import-preview-light")
    }

    @Test
    fun importPreviewDarkFontScaleAndNothingSelected() {
        val preview = wechatPreview()
        val dark = h.show(dark = true) { BillPreviewContent(preview, LedgerSamples.data, false, BillPreviewActions()) }
        h.snapshot(dark, "import-preview-dark")
        h.close()
        RuntimeEnvironment.setFontScale(1.3f)
        val big = h.show { BillPreviewContent(preview, LedgerSamples.data, false, BillPreviewActions()) }
        h.snapshot(big, "import-preview-font130")
        h.close()
        val none = preview.copy(candidates = preview.candidates.map { it.copy(checked = false) })
        h.show { BillPreviewContent(none, LedgerSamples.data, false, BillPreviewActions()) }
        compose.onNodeWithText("请至少勾选一笔").assertIsNotEnabled()
    }

    @Test
    fun importMappingForGenericCsv() {
        val table = TableReader.read(BillSamples.genericCsv(), "我的账.csv")
        var confirmed = 0
        val activity = h.show {
            BillMappingContent(ImportStage.Mapping(table, BillParser.guessMapping(table), "我的账.csv"), onBack = {}, onChange = {}, onConfirm = { confirmed++ })
        }
        compose.onNodeWithText("时间（必选）").assertExists()
        compose.onNodeWithText("负数是支出、正数是收入").assertExists()
        h.snapshot(activity, "import-mapping-light")
        compose.onNodeWithText("下一步：预览").assertIsEnabled().performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun importDoneSummary() {
        val preview = wechatPreview()
        val txs = BillImport.transactions(preview, LedgerSamples.zone, 1L)
        val activity = h.show { BillImportDone(BillImport.result(preview, txs), onFinish = {}, onAgain = {}) }
        compose.onNodeWithText("已导入 6 笔").assertExists()
        compose.onNodeWithText("新建账户").assertDoesNotExist() // 示例账本里已有微信账户
        h.snapshot(activity, "import-done-light")
    }

    // ── 待确认 ────────────────────────────────────────────────────

    private val pending = listOf(
        PendingEntry("p1", PaySource.WECHAT, TxType.EXPENSE, 3_250, "示例面馆", LedgerSamples.at(10, 10, 12, 18), 0),
        PendingEntry("p2", PaySource.ALIPAY, TxType.EXPENSE, 1_580, "示例咖啡", LedgerSamples.at(10, 10, 8, 40), 0),
        PendingEntry("p3", PaySource.BANK, TxType.INCOME, 800_000, "", LedgerSamples.at(10, 9, 9, 0), 0),
    )

    @Test
    fun pendingInboxLightAndActions() {
        val rows = buildPendingUi(pending, LedgerSamples.data, LedgerSamples.zone)
        val confirmed = mutableListOf<String>()
        val ignored = mutableListOf<String>()
        val activity = h.show {
            PendingInboxContent(rows, enabled = true, PendingInboxActions(onConfirm = { confirmed += it.entry.id }, onIgnore = { ignored += it.entry.id }))
        }
        compose.onNodeWithText("示例面馆").assertExists()
        compose.onNodeWithText("银行收款").assertExists() // 没有商户时显示来源
        compose.onNodeWithText("全部记账").assertExists()
        compose.onNodeWithText("餐饮 · 午餐", substring = true).assertExists()
        compose.onAllNodesWithText("记账").onFirst().performClick()
        compose.onAllNodesWithText("忽略").onFirst().performClick()
        assertEquals(listOf("p1"), confirmed)
        assertEquals(listOf("p1"), ignored)
        h.snapshot(activity, "pending-inbox-light")
    }

    @Test
    fun pendingInboxDarkAndEmpty() {
        val rows = buildPendingUi(pending, LedgerSamples.data, LedgerSamples.zone)
        val dark = h.show(dark = true) { PendingInboxContent(rows, true, PendingInboxActions()) }
        h.snapshot(dark, "pending-inbox-dark")
        h.close()
        var settings = 0
        val empty = h.show { PendingInboxContent(emptyList(), enabled = false, PendingInboxActions(onSettings = { settings++ })) }
        compose.onNodeWithText("没有待确认的记录").assertExists()
        compose.onNodeWithText("去打开自动记账").performClick()
        assertEquals(1, settings)
        h.snapshot(empty, "pending-inbox-empty")
    }

    // ── 自动记账设置 ──────────────────────────────────────────────

    @Test
    fun autoBookkeepingSettingsLightAndDark() {
        val toggles = mutableListOf<Boolean>()
        val activity = h.show {
            AutoBookkeepingContent(enabled = false, autoSave = false, accessGranted = false, pendingCount = 0, actions = AutoBookkeepingActions(onToggle = { toggles += it }))
        }
        compose.onNodeWithText("未授予").assertExists()
        compose.onNodeWithText("隐私与权限").performScrollTo()
        compose.onNodeWithText("没有联网权限", substring = true).assertExists()
        h.snapshot(activity, "auto-bookkeeping-light")
        h.close()
        val dark = h.show(dark = true) { AutoBookkeepingContent(true, false, true, 3, AutoBookkeepingActions()) }
        compose.onNodeWithText("3 笔").assertExists()
        compose.onNodeWithText("已授予").assertExists()
        h.snapshot(dark, "auto-bookkeeping-dark")
    }

    // ── 入口 ──────────────────────────────────────────────────────

    @Test
    fun ledgerHomeShowsPendingBanner() {
        var opened = 0
        val ui = buildLedgerUi(LedgerSamples.data, YearMonth.of(2026, 10), TxFilter(), LedgerSamples.zone, LedgerSamples.today)
        val activity = h.show { LedgerHome(ui, TxFilter(), LazyListState(), LedgerHomeActions(onPending = { opened++ }), pendingCount = 3) }
        compose.onNodeWithText("自动记账有 3 笔待确认").performClick()
        assertEquals(1, opened)
        h.snapshot(activity, "ledger-home-pending-light")
    }

    @Test
    fun ledgerSearchResults() {
        val filter = TxFilter(query = "面")
        val ui = buildLedgerUi(LedgerSamples.data, YearMonth.of(2026, 10), filter, LedgerSamples.zone, LedgerSamples.today)
        val activity = h.show { LedgerHome(ui, filter, LazyListState(), LedgerHomeActions()) }
        compose.onNodeWithText("清除筛选").assertExists()
        h.snapshot(activity, "ledger-search-light")
    }

    @Test
    fun settingsShowsFontAndBillEntries() {
        var fonts = 0
        var imports = 0
        val activity = h.show {
            SettingsContent(
                settings = AppSettings(fontMode = FontMode.ROUNDED),
                biometricAvailable = true, biometricEnabled = false, pinEnabled = true, scanningOtp = false, version = "1.2.0-beta",
                actions = SettingsActions(onFonts = { fonts++ }, onBillImport = { imports++ }),
            )
        }
        compose.onNodeWithText("字体").performScrollTo()
        compose.onNodeWithText("圆润数字").assertExists()
        compose.onNodeWithText("账单与自动记账").performScrollTo()
        h.snapshot(activity, "settings-smart-light")
        compose.onNodeWithText("字体").performClick()
        compose.onNodeWithText("导入账单").performScrollTo().performClick()
        assertEquals(1, fonts)
        assertEquals(1, imports)
    }

    @Test
    fun meShowsAssistantRows() {
        var imports = 0
        val ui = buildMeUi(LedgerSamples.data, cards = 5, today = LedgerSamples.today, zone = LedgerSamples.zone, version = "1.2.0-beta")
        val activity = h.show { MeContent(ui, MeActions(onImport = { imports++ })) }
        compose.onNodeWithText("记账助手").performScrollTo()
        h.snapshot(activity, "me-assistant-light")
        compose.onNodeWithText("导入账单").performClick()
        assertEquals(1, imports)
    }
}
