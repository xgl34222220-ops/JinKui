package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.xiguli.jinkui.data.importer.BillImport
import com.xiguli.jinkui.data.importer.BillParser
import com.xiguli.jinkui.data.importer.BillSource
import com.xiguli.jinkui.data.importer.ImportClass
import com.xiguli.jinkui.data.importer.TableReader
import com.xiguli.jinkui.data.importer.XlsFixture
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerImportBatch
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxFilter
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 1.3 导入清理：导入的流水能用左滑、读屏操作、详情页删除；长按进入多选、整天 / 整月选择后删除；
 * 导入预览按类分组（余额校准默认不导入）；结果页显示余额变化与“撤销本次导入”；导入记录页。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class ImportCleanupUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val october = YearMonth.of(2026, 10)

    /** 示例账本里的两笔换成“导入的”（imp- 开头的 ID、带批次）。 */
    private val imported: LedgerData by lazy {
        val base = LedgerSamples.data
        base.copy(
            transactions = base.transactions.mapIndexed { i, t ->
                if (i < 2) t.copy(id = "imp-" + "%032x".format(i + 1), importBatch = "batch-1") else t
            },
            importBatches = listOf(LedgerImportBatch("batch-1", LedgerSamples.at(10, 10, 21, 0), "记账 App", "一木账单.xls", 2)),
        )
    }

    private fun ui(data: LedgerData) = buildLedgerUi(data, october, TxFilter(), LedgerSamples.zone, LedgerSamples.today)

    private fun rowNode() = compose.onNodeWithText("餐饮 · 午餐")

    @Test
    fun importedRowsDeleteBySwipeAndAccessibilityAction() {
        var deleted: LedgerTransaction? = null
        h.show { LedgerHome(ui(imported), TxFilter(), LazyListState(), LedgerHomeActions(onDelete = { deleted = it })) }
        rowNode().performScrollTo().performTouchInput { swipeLeft(startX = right - 4f, endX = left + 4f) }
        compose.waitForIdle()
        assertTrue(deleted?.id.orEmpty().startsWith("imp-"))
        deleted = null
        val node = rowNode().fetchSemanticsNode()
        var target = node
        while (target.config.getOrNull(SemanticsActions.CustomActions) == null) target = target.parent!!
        compose.runOnIdle { target.config[SemanticsActions.CustomActions].first { it.label == "删除" }.action() }
        assertTrue(deleted?.id.orEmpty().startsWith("imp-"))
    }

    @Test
    fun longPressEntersSelectionAndBulkDeletes() {
        val host = EntryHostState()
        var bulk: List<LedgerTransaction> = emptyList()
        val activity = h.show {
            val actions = host.actions()
            LedgerHome(
                ui(imported),
                TxFilter(),
                LazyListState(),
                LedgerHomeActions(
                    selection = actions.selection,
                    onSelectStart = actions.onSelectStart,
                    onSelectToggle = actions.onSelectToggle,
                    onSelectMany = actions.onSelectMany,
                    onSelectDone = actions.onSelectDone,
                    onDeleteSelected = { bulk = it },
                ),
            )
        }
        rowNode().performScrollTo().performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(1, host.selection?.size)
        compose.onNodeWithText("已选 1 笔").assertExists()
        // 本日全选：10 月 10 日的两笔
        val dayCount = ui(imported).days.first().rows.size
        compose.onAllNodesWithContentDescription("本日全选").onFirst().performClick()
        compose.waitForIdle()
        assertEquals(dayCount, host.selection?.size)
        h.snapshot(activity, "ledger-multiselect-light")
        compose.onNodeWithText("删除 $dayCount 笔").performClick()
        assertEquals(dayCount, bulk.size)
        assertTrue(bulk.count { it.id.startsWith("imp-") } >= 1)
        // 本月全选
        compose.onNodeWithText("本月全选", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(ui(imported).days.sumOf { it.rows.size }, host.selection?.size)
        compose.onNodeWithText("完成").performClick()
        compose.waitForIdle()
        assertEquals(null, host.selection)
    }

    @Test
    fun entrySheetDeletesImportedRow() {
        val tx = imported.transactions.first { it.id.startsWith("imp-") }
        var deleted: LedgerTransaction? = null
        h.show {
            EntrySheet(
                data = imported,
                existing = tx,
                onDismiss = {},
                onSave = { _, _, _ -> },
                onDelete = { deleted = it },
                onMessage = {},
            )
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("删除这笔").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("删除").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(tx.id, deleted?.id)
    }

    private fun appPreview() = BillImport.preview(
        BillParser.parse(TableReader.read(XlsFixture.xls(XlsFixture.cleanupSample()), "一木.xls"), BillSource.APP),
        LedgerSamples.data.copy(transactions = emptyList()),
        LedgerSamples.zone,
        now = 1L,
        fileName = "一木.xls",
    )

    @Test
    fun previewGroupsByClassAndAdjustmentIsOffByDefault() {
        var group: Pair<ImportClass, Boolean>? = null
        val preview = appPreview()
        val activity = h.show { BillPreviewContent(preview, LedgerSamples.data, false, BillPreviewActions(onGroup = { k, on -> group = k to on })) }
        compose.onNodeWithText("按类别导入").assertExists()
        compose.onAllNodesWithText("余额校准").onFirst().assertExists()
        compose.onAllNodesWithText("内部转账").onFirst().assertExists()
        compose.onAllNodesWithText("借贷往来").onFirst().assertExists()
        h.snapshot(activity, "import-groups-light")
        // 点“余额校准”这一组的整行开关（角色是 Switch 的那一行），不是第一个同名文字；
        // 分组卡在 LazyColumn 里可能在首屏以外，用语义点击不依赖它是否露在屏幕上
        val adjustmentRow = androidx.compose.ui.test.hasText("余额校准") and
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.Role,
                androidx.compose.ui.semantics.Role.Switch,
            )
        compose.onNode(adjustmentRow).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(ImportClass.ADJUSTMENT to true, group)
    }

    @Test
    fun doneShowsBalanceChangesAndUndo() {
        val preview = appPreview()
        val before = LedgerSamples.data.copy(transactions = emptyList())
        val txs = BillImport.transactions(preview, LedgerSamples.zone, 1L, "batch-x")
        var undone = false
        val activity = h.show {
            BillImportDone(BillImport.result(preview, txs, before, "batch-x"), onFinish = {}, onAgain = {}, onUndo = { undone = true })
        }
        compose.onNodeWithText("账户余额变化").assertExists()
        h.snapshot(activity, "import-done-balances-light")
        // “撤销本次导入”在底部固定按钮区里，不在滚动区内
        compose.onNodeWithText("撤销本次导入").performClick()
        assertTrue(undone)
    }

    @Test
    fun importHistoryListsBatchesAndUndo() {
        var undo: ImportBatchUi? = null
        var range = false
        val items = buildImportHistory(imported, LedgerSamples.zone)
        val activity = h.show { ImportHistoryContent(items, ImportHistoryActions(onUndo = { undo = it }, onDeleteRange = { range = true })) }
        compose.onNodeWithText("一木账单.xls").assertExists()
        compose.onNodeWithText("现有 2 笔", substring = true).assertExists()
        h.snapshot(activity, "import-history-light")
        compose.onNodeWithText("撤销导入").performClick()
        assertEquals("batch-1", undo?.batch?.id)
        compose.onNodeWithText("选择日期范围").performScrollTo().performClick()
        assertTrue(range)
        assertEquals(TxType.EXPENSE, imported.transactions.first { it.importBatch == "batch-1" }.type)
    }
}
