package com.xiguli.jinkui.ui.card

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.xiguli.jinkui.data.model.TrashMath
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
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
 * 1.4.0-alpha04 卡包回收站：列表只显示标题、类型与剩余天数（不显示字段），还原、彻底删除与清空都要再确认，
 * 卡片详情的删除确认改为“移到回收站”。浅色 / 深色截图，合成数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class TrashScreenUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    private val now = CardSamples.now
    private val trash = listOf(
        CardSamples.cmb.copy(deletedAt = now - 2 * TrashMath.DAY_MS),
        CardSamples.github.copy(deletedAt = now - 29 * TrashMath.DAY_MS - 1),
    )

    private fun clickText(text: String) = compose.onAllNodesWithText(text).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun trashListRestoreAndPurgeLightAndDark() {
        val rows = buildTrashUi(trash, now)
        assertEquals(listOf("c-cmb", "c-gh"), rows.map { it.card.id })
        assertEquals("银行卡 · 28 天后彻底删除", rows[0].subtitle)
        assertEquals("登录密码 · 1 天后彻底删除", rows[1].subtitle)
        val restored = mutableListOf<String>()
        val purged = mutableListOf<String>()
        var purgedAll = 0
        val activity = h.show {
            CardTrashContent(rows, TrashActions(onRestore = { restored += it.id }, onPurge = { purged += it.id }, onPurgeAll = { purgedAll++ }))
        }
        compose.onNodeWithText("2 张卡片").assertExists()
        compose.onNodeWithText("招商银行储蓄卡").assertExists()
        // 不显示任何字段内容
        compose.onAllNodesWithText("6225888812345972", substring = true).assertCountEquals(0)
        h.snapshot(activity, "card-trash-light")
        clickText("还原")
        assertEquals(listOf("c-cmb"), restored)
        // 彻底删除先确认
        compose.onNodeWithContentDescription("彻底删除GitHub").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("彻底删除「GitHub」？").assertExists()
        assertTrue(purged.isEmpty())
        h.snapshot(activity, "card-trash-confirm-light", withDialog = true)
        clickText("彻底删除")
        compose.waitForIdle()
        assertEquals(listOf("c-gh"), purged)
        clickText("清空回收站")
        compose.waitForIdle()
        compose.onNodeWithText("这 2 张卡片连同附件照片和历史密码会一起删除，无法恢复。").assertExists()
        clickText("全部彻底删除")
        compose.waitForIdle()
        assertEquals(1, purgedAll)
        h.close()
        val dark = h.show(dark = true) { CardTrashContent(rows, TrashActions()) }
        h.snapshot(dark, "card-trash-dark")
        h.close()
        val empty = h.show(dark = true) { CardTrashContent(emptyList(), TrashActions()) }
        compose.onNodeWithText("回收站是空的").assertExists()
        compose.onAllNodesWithText("清空回收站").assertCountEquals(0)
        h.snapshot(empty, "card-trash-empty-dark")
    }

    @Test
    fun deleteDialogMovesToTrashLightAndDark() {
        var confirmed = 0
        val activity = h.show { DeleteCardDialog("GitHub", onDismiss = {}, onConfirm = { confirmed++ }) }
        compose.onNodeWithText("删除「GitHub」？").assertExists()
        compose.onNodeWithText(DELETE_EXPLAIN).assertExists()
        h.snapshot(activity, "card-delete-dialog-light", withDialog = true)
        clickText("移到回收站")
        assertEquals(1, confirmed)
        h.close()
        val dark = h.show(dark = true) { DeleteCardDialog("", onDismiss = {}, onConfirm = {}) }
        compose.onNodeWithText("删除「未命名」？").assertExists()
        h.snapshot(dark, "card-delete-dialog-dark", withDialog = true)
    }
}
