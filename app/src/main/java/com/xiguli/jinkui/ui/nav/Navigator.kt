package com.xiguli.jinkui.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
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
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(NavEntry(screen))
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
inline fun <reified VM : ViewModel> jkViewModel(crossinline build: () -> VM): VM =
    viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
        },
    )
