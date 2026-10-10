package com.xiguli.jinkui.ui.ledger

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerRecurring
import com.xiguli.jinkui.data.ledger.RecurFrequency
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.MainLooperDrainRule
import com.xiguli.jinkui.ui.ScreenHarness
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.generator.GeneratorSheet
import com.xiguli.jinkui.ui.health.HealthScreen
import com.xiguli.jinkui.ui.organize.OrganizeScreen
import com.xiguli.jinkui.ui.settings.PinSetupDialog
import com.xiguli.jinkui.ui.settings.QuickUnlockIntro
import com.xiguli.jinkui.ui.setup.SetupScreen
import com.xiguli.jinkui.ui.theme.JkType
import java.time.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 之前没有截图的页面、面板与对话框：首次设置、快捷解锁引导、PIN 设置、密码生成器、安全体检、批量整理 / 标签、
 * 分类管理与编辑面板、周期记账编辑面板、预算对话框、删除确认对话框。
 * 需要 AppContainer 的页面用锁定状态的空容器渲染（不碰 Keystore 与数据库），所以体检与整理是空数据的样子。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w392dp-h860dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
class MoreScreensUiTest {
    @get:Rule(order = 0)
    val drain = MainLooperDrainRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val h = ScreenHarness(compose)

    @After
    fun close() = h.close()

    @Test
    fun onboardingLightAndDark() {
        val activity = h.show(withContainer = true) { SetupScreen() }
        compose.onNodeWithText("设置主密码").assertExists()
        compose.onNodeWithText("我已了解：忘记主密码，数据将无法恢复").assertExists()
        h.snapshot(activity, "onboarding-light")
        h.close()
        val dark = h.show(dark = true, withContainer = true) { SetupScreen() }
        h.snapshot(dark, "onboarding-dark")
    }

    @Test
    fun quickUnlockIntro() {
        val activity = h.show(withContainer = true) { QuickUnlockIntro(onDone = {}) }
        compose.onNodeWithText("保险库已创建").assertExists()
        h.snapshot(activity, "quick-unlock-intro-light")
    }

    /** PIN 输入框自动聚焦、光标一直闪：单独一个测试，用暂停的时钟渲染。 */
    @Test
    fun pinSetupDialog() {
        val pin = h.show(withContainer = true, pausedClock = true) { PinSetupDialog(onDismiss = {}, onSaved = {}) }
        h.snapshot(pin, "pin-setup-dialog-light", withDialog = true)
    }

    @Test
    fun generatorSheet() {
        val activity = h.show(withContainer = true) { GeneratorSheet(onDismiss = {}) }
        compose.onNodeWithText("密码生成器").assertExists()
        compose.onNodeWithText("长度").assertExists()
        h.snapshot(activity, "generator-sheet-light", withDialog = true)
    }

    @Test
    fun healthAndOrganizeEmpty() {
        val activity = h.show(withContainer = true) { HealthScreen(onBack = {}, onOpen = {}, onSettings = {}) }
        compose.onNodeWithText("安全体检").assertExists()
        h.snapshot(activity, "health-light")
        h.close()
        val organize = h.show(withContainer = true) { OrganizeScreen(onBack = {}) }
        h.snapshot(organize, "organize-tags-light")
    }

    @Test
    fun categoriesListLightDarkAndEditSheet() {
        val categories = LedgerDefaults.categories().map { if (it.id == "cat-family-2") it.copy(archived = true) else it }
        val activity = h.show { LedgerCategoriesContent(categories, TxType.EXPENSE, onType = {}, onBack = {}, onEdit = {}) }
        compose.onNodeWithText("分类管理").assertExists()
        compose.onNodeWithText("餐饮").assertExists()
        h.snapshot(activity, "categories-light")
        h.close()
        val dark = h.show(dark = true) { LedgerCategoriesContent(categories, TxType.INCOME, onType = {}, onBack = {}, onEdit = {}) }
        compose.onNodeWithText("工资").assertExists()
        h.snapshot(dark, "categories-income-dark")
        h.close()
        val sheet = h.show { CategoryEditSheet(categories.first { it.id == "cat-food-2" }, isNew = false, parentName = "餐饮", onDismiss = {}, onSave = {}) }
        h.snapshot(sheet, "category-edit-sheet-light", withDialog = true)
    }

    @Test
    fun recurringEditSheet() {
        val rule = LedgerRecurring(
            id = "r-1", type = TxType.EXPENSE, amount = 380_000, accountId = LedgerSamples.bank.id,
            categoryId = "cat-housing-1", note = "房租", frequency = RecurFrequency.MONTHLY, startDate = LocalDate.of(2026, 9, 8),
        )
        val activity = h.show(withContainer = true) {
            RecurringEditSheet(rule, LedgerSamples.data, isNew = false, onDismiss = {}, onSave = {}, onDelete = {})
        }
        h.snapshot(activity, "recurring-edit-sheet-light", withDialog = true)
    }

    /** 预算输入框自动聚焦：单独一个测试，用暂停的时钟渲染。 */
    @Test
    fun budgetDialog() {
        val budget = h.show(pausedClock = true) { BudgetDialog("本月总预算", 500_000, onDismiss = {}, onSave = {}) }
        h.snapshot(budget, "budget-dialog-light", withDialog = true)
    }

    @Test
    fun confirmDialogLightAndDark() {
        val activity = h.show {
            JkDialog(title = "删除这笔记录？", onDismiss = {}, confirmText = "删除", danger = true, onConfirm = {}) {
                Text("¥32.50，删除后账户余额会随之调整，无法撤销。", style = JkType.body)
            }
        }
        h.snapshot(activity, "dialog-confirm-light", withDialog = true)
        h.close()
        val dark = h.show(dark = true) {
            JkDialog(title = "删除这笔记录？", onDismiss = {}, confirmText = "删除", danger = true, onConfirm = {}) {
                Text("¥32.50，删除后账户余额会随之调整，无法撤销。", style = JkType.body)
            }
        }
        h.snapshot(dark, "dialog-confirm-dark", withDialog = true)
    }
}
