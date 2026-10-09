package com.xiguli.jinkui.ui.card

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.core.qr.OtpMigration
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.SortOrder
import com.xiguli.jinkui.data.repo.ThemeMode
import com.xiguli.jinkui.ui.home.ListHome
import com.xiguli.jinkui.ui.home.buildHomeUi
import com.xiguli.jinkui.ui.ledger.EntrySheet
import com.xiguli.jinkui.ui.ledger.LedgerSamples
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JinKuiTheme
import com.xiguli.jinkui.ui.theme.Space
import java.io.File
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * 卡包页面的界面测试（Robolectric）：列表首页、无结果、详情（浅 / 深 / 显示敏感字段 / 1.3 倍字号）、
 * 新建表单（含校验提示）、两步验证码批量导入预览、记一笔面板。
 * 只渲染无状态的组件（ListHome / CardDetailContent / CardEditForm / OtpBatchContent / EntrySheet），
 * 不碰数据库与密钥。x86_64（CI）上把页面画成 PNG 存到 app/build/outputs/ui-screenshots/。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class CardScreensUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun show(dark: Boolean = false, content: @Composable () -> Unit): ComponentActivity {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            JinKuiTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Jk.colors.bg),
                ) { content() }
            }
        }
        ShadowLooper.idleMainLooper()
        compose.waitForIdle()
        return activity
    }

    /** 尽力而为的截图；有对话框（底部面板）时把它叠画在页面上。 */
    private fun snapshot(activity: ComponentActivity, name: String, withDialog: Boolean = false) {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        runCatching {
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            if (withDialog) {
                ShadowDialog.getLatestDialog()?.window?.decorView?.let { dialogView ->
                    canvas.drawARGB(0x66, 0, 0, 0)
                    dialogView.draw(canvas)
                }
            }
            val dir = File("build/outputs/ui-screenshots").apply { mkdirs() }
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("截图 $name 失败：$it") }
    }

    private fun listHome(query: String = "", copied: MutableList<String> = mutableListOf()): @Composable () -> Unit = {
        val ui = buildHomeUi(CardSamples.all, query, null, SortOrder.USED, 2)
        ListHome(
            ui = ui, query = query, filter = null, listState = LazyListState(), sortOrder = SortOrder.USED,
            onQuery = {}, onFilter = {}, onOpen = {}, onLongClick = {}, onCopy = { _, f -> copied += f.label },
            onToggleView = {}, onLock = {}, onMore = {}, onCreate = {}, onHealth = {}, onSort = {},
        )
    }

    @Test
    fun cardListShowsPinnedRowsMaskedNumbersAndCopies() {
        val copied = mutableListOf<String>()
        val activity = show(content = listHome(copied = copied))
        compose.onAllNodesWithText("置顶").assertCountEquals(2) // 分组标题 + 筛选项
        compose.onAllNodesWithText("工商银行信用卡").onFirst().assertExists()
        // 打码的卡号只露末四位
        compose.onNodeWithText("•••• 8841").assertExists()
        // 复制按钮 48dp 触控区
        val copy = compose.onAllNodesWithContentDescription("复制卡号").onFirst()
        copy.assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp)
        copy.performClick()
        assertEquals(listOf("卡号"), copied)
        snapshot(activity, "card-list-light")
    }

    @Test
    fun cardListDark() {
        val activity = show(dark = true, content = listHome())
        compose.onNodeWithText("GitHub").assertExists()
        snapshot(activity, "card-list-dark")
    }

    @Test
    fun cardListNoResults() {
        val activity = show(content = listHome(query = "不存在的关键词"))
        compose.onNodeWithText("没有匹配的卡片").assertExists()
        compose.onNodeWithText("清除搜索与筛选").assertExists()
        snapshot(activity, "card-list-no-results")
    }

    private fun detail(
        card: com.xiguli.jinkui.data.model.Card,
        revealed: MutableList<String> = mutableStateListOf(),
        copied: MutableList<String> = mutableListOf(),
    ): @Composable () -> Unit = {
        Column(Modifier.padding(top = Space.x2)) {
            CardDetailContent(
                card = card,
                revealed = revealed,
                onToggleReveal = { if (it in revealed) revealed.remove(it) else revealed.add(it) },
                onRevealAll = { ids, show -> revealed.clear(); if (show) revealed.addAll(ids) },
                copiedKey = copied.lastOrNull(),
                onCopy = { copied += it.id },
                onEdit = {}, onHistory = {}, onDuplicate = {}, onViewAttachment = {},
                loadAttachment = { ByteArray(0) },
                ticking = false,
                clock = { CardSamples.now },
            )
        }
    }

    @Test
    fun cardDetailGroupsSectionsAndHidesSensitive() {
        val revealed = mutableStateListOf<String>()
        val activity = show(content = detail(CardSamples.cmb, revealed))
        compose.onNodeWithText("信息").assertExists()
        compose.onNodeWithText("备注").assertExists()
        compose.onNodeWithText("更多").assertExists()
        compose.onNodeWithContentDescription("卡号，已隐藏").assertExists()
        snapshot(activity, "card-detail-light")
        compose.onNodeWithText("全部显示").performClick()
        compose.waitForIdle()
        assertTrue("f1" in revealed && "f5" in revealed)
        // 卡号四位一组显示
        compose.onNodeWithText("6225 8888 1234 5972").assertExists()
        snapshot(activity, "card-detail-revealed")
    }

    @Test
    fun cardDetailTotpDark() {
        val copied = mutableListOf<String>()
        val activity = show(dark = true, content = detail(CardSamples.github, copied = copied))
        compose.onNodeWithText("两步验证").assertExists()
        compose.onNodeWithContentDescription("复制验证码").assertTouchHeightIsEqualTo(48.dp).performClick()
        assertEquals(listOf("h4"), copied)
        snapshot(activity, "card-detail-totp-dark")
    }

    @Test
    fun cardDetailAtLargeFontScale() {
        RuntimeEnvironment.setFontScale(1.3f)
        try {
            val activity = show(content = detail(CardSamples.idCard))
            compose.onNodeWithText("公民身份号码").assertIsDisplayed()
            // 行高随字号增长，不被固定高度截断
            compose.onNodeWithContentDescription("复制公民身份号码").assertHeightIsAtLeast(48.dp)
            snapshot(activity, "card-detail-font130")
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test
    fun addFormShowsGroupsAndValidation() {
        val draft = CardDraft(null, CardType.BANK)
        draft.fields.first { it.label == "卡号" }.value = "6225 88AB"
        draft.titleError = "请填写名称，例如“招商银行储蓄卡”"
        val activity = show {
            CardEditForm(
                draft = draft, isNew = true, suggestions = listOf("工资卡", "常用"), scanningId = null,
                onAddField = {}, onGenerate = {}, onScanQr = {}, onAddPhoto = {}, loadAttachment = { ByteArray(0) },
            )
        }
        compose.onNodeWithText("卡面").assertExists()
        compose.onNodeWithText("字段").assertExists()
        compose.onNodeWithText("请填写名称，例如“招商银行储蓄卡”").assertExists()
        compose.onNodeWithText("这里通常只有数字（可以带空格或短横线）").assertExists()
        snapshot(activity, "card-add-form")
    }

    @Test
    fun addFormBankBrandPreviewDark() {
        val draft = CardDraft(null, CardType.BANK)
        draft.title = "招商银行储蓄卡"
        draft.fields.first { it.label == "开户行" }.value = "招商银行杭州分行"
        assertTrue(draft.brand != null)
        val activity = show(dark = true) {
            CardEditForm(
                draft = draft, isNew = true, suggestions = emptyList(), scanningId = null,
                onAddField = {}, onGenerate = {}, onScanQr = {}, onAddPhoto = {}, loadAttachment = { ByteArray(0) },
            )
        }
        compose.onNodeWithText("默认卡面会按名称或开户行带上银行的主色").assertExists()
        snapshot(activity, "card-add-form-dark")
    }

    @Test
    fun otpBatchPreviewListsAccountsWithoutSecrets() {
        fun entry(issuer: String, name: String, type: Int = 2): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            fun v(x: Int) { var y = x; while (y >= 0x80) { out.write((y and 0x7F) or 0x80); y = y ushr 7 }; out.write(y) }
            fun bytes(f: Int, b: ByteArray) { v((f shl 3) or 2); v(b.size); out.write(b) }
            bytes(1, CardSamples.totpSecret); bytes(2, name.toByteArray()); bytes(3, issuer.toByteArray())
            v(6 shl 3); v(type)
            return out.toByteArray()
        }
        val payload = java.io.ByteArrayOutputStream()
        for (e in listOf(entry("Google", "lin@example.com"), entry("GitHub", "lin"), entry("Bank", "lin", type = 1))) {
            payload.write(0x0A); payload.write(e.size); payload.write(e)
        }
        val uri = "otpauth-migration://offline?data=" + URLEncoder.encode(Base64.getEncoder().encodeToString(payload.toByteArray()), "UTF-8")
        val batch = OtpMigration.parse(uri)
        val selected = mutableStateListOf(true, false, false)
        val activity = show {
            Column(Modifier.padding(Space.x2_5)) {
                OtpBatchContent(batch, duplicate = listOf(false, true, false), selected = selected, onToggle = { selected[it] = !selected[it] })
            }
        }
        compose.onNodeWithText("识别到 3 个账号，其中 1 个无法导入。").assertExists()
        compose.onNodeWithText("金匮里已有同一个密钥　lin").assertExists()
        snapshot(activity, "otp-batch-preview")
    }

    @Test
    fun entrySheetRendersKeypadAndCategories() {
        val messages = mutableListOf<String>()
        val activity = show {
            EntrySheet(
                data = LedgerSamples.data,
                existing = null,
                onDismiss = {},
                onSave = { _, _, _ -> },
                onDelete = {},
                onMessage = { messages += it },
                initialTime = LedgerSamples.at(10, 10, 12, 0),
            )
        }
        compose.onNodeWithText("完成").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        // 底部面板有进入动画，直接触发语义上的点击，不依赖手势坐标
        compose.onNodeWithContentDescription("保存").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf("请输入大于 0 的金额"), messages)
        snapshot(activity, "entry-sheet", withDialog = true)
    }
}
