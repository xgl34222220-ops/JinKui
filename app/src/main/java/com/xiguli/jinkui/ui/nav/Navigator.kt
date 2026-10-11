package com.xiguli.jinkui.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.jinkui.data.model.CardType
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data class Detail(val cardId: String) : Screen
    data class Edit(val cardId: String?, val type: CardType?) : Screen
    data object Settings : Screen
    data object Health : Screen
    data object Organize : Screen
    data object LedgerAccounts : Screen
    data object LedgerBudget : Screen
    data object LedgerCategories : Screen
    data object LedgerRecurring : Screen
    /** 资产总览：净资产、负债与信用账户，按现有账户算出。 */
    data object LedgerAssets : Screen
    /** 设置 → 外观 → 字体（1.2 起）。 */
    data object FontSettings : Screen
    /** 导入微信 / 支付宝 / CSV 账单（1.2 起）。 */
    data object BillImport : Screen
    /** 导入记录：每次导入、整批撤销、按时间段删除（1.3 起）。 */
    data object ImportHistory : Screen
    /** 自动记账设置（1.2 起）。 */
    data object AutoBookkeeping : Screen
    /** 自动记账的“待确认”（1.2 起）。 */
    data object PendingInbox : Screen
    /** 欠款 / 账单总览（1.4 起）：本月待还、即将到期、信用账户、借贷。 */
    data object Debts : Screen
    /** 一个信用账户的账单、分期与还款（1.4 起）。 */
    data class CreditStatement(val accountId: String) : Screen
    /** 借贷列表（1.4 起）。 */
    data object Loans : Screen
    /** 1.4.0-alpha04：借贷列表并直接打开“记一笔借出 / 借入”。 */
    data class LoansNew(val direction: com.xiguli.jinkui.data.ledger.LoanDirection) : Screen
    /** 1.4.0-alpha04：账户管理并直接打开“添加账户”（种类预选信用卡）。 */
    data object LedgerAddCredit : Screen
    /** 1.4.0-alpha04：报销（待报销、已报销）。 */
    data object Reimburse : Screen
}

/** 首页底部悬浮栏的五个分区（1.1 起）：账本是首页；卡包保留全部卡片功能。 */
enum class HomeTab(val label: String) {
    LEDGER("账本"),
    CALENDAR("日历"),
    CARDS("卡包"),
    STATS("统计"),
    ME("我的"),
}

/**
 * 导航栈中的一页。每页有自己的 ViewModelStore：
 * 出栈或锁定时立刻清掉，其中持有的明文（例如编辑中的草稿）不会留在内存里。
 */
class NavEntry(val screen: Screen) : ViewModelStoreOwner {
    val id: String = UUID.randomUUID().toString()
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

class Navigator {
    private val stack = mutableStateListOf(NavEntry(Screen.Home))

    val current: NavEntry get() = stack.last()

    /** 首页当前停在哪个分区；进详情再返回时保持不变。 */
    var homeTab by mutableStateOf(HomeTab.LEDGER)
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(NavEntry(screen))
    }

    /** 当前打开的页面（从栈底到栈顶）。 */
    val screens: List<Screen> get() = stack.map { it.screen }

    /** 回到首页（清掉上面所有页面）。 */
    fun popToHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex).viewModelStore.clear()
    }

    fun pop() {
        if (stack.size <= 1) return
        stack.removeAt(stack.lastIndex).viewModelStore.clear()
    }

    /** 整个导航离开组合（锁定）时调用。 */
    fun dispose() {
        stack.forEach { it.viewModelStore.clear() }
    }
}

/** 用给定的构造逻辑在当前页面作用域内取得 ViewModel。 */
@Composable
inline fun <reified VM : ViewModel> jkViewModel(key: String? = null, crossinline build: () -> VM): VM =
    viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
        },
    )
