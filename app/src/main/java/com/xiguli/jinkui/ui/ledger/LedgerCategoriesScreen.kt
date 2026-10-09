package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.launch

class LedgerCategoriesViewModel(private val container: AppContainer) : ViewModel() {
    fun save(category: LedgerCategory, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                container.ledger.saveCategory(category)
                onDone()
            } catch (e: LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }
}

@Composable
fun LedgerCategoriesScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { LedgerCategoriesViewModel(container) }
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val colors = Jk.colors
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var editing by remember { mutableStateOf<LedgerCategory?>(null) }

    val tops = data.categories.filter { it.type == type && it.parentId == null }.sortedWith(compareBy({ it.archived }, { it.position }))

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar("分类管理", onBack = onBack) {
            JkIconButton(Icons.Outlined.Add, "添加一级分类", {
                editing = LedgerCategory(LedgerIds.newId(), "", type, null, "more")
            })
        }
        JkSegmented(
            options = listOf(TxType.EXPENSE to "支出分类", TxType.INCOME to "收入分类"),
            selected = type,
            onSelect = { type = it },
            modifier = Modifier.padding(horizontal = Space.x2_5),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.x2_5, end = Space.x2_5, top = Space.x2, bottom = Space.x6),
            verticalArrangement = Arrangement.spacedBy(Space.x1_5),
        ) {
            items(tops.size, key = { tops[it].id }) { index ->
                val top = tops[index]
                val children = data.categories.filter { it.parentId == top.id }.sortedWith(compareBy({ it.archived }, { it.position }))
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    JkRow(
                        title = top.name + if (top.archived) "（已隐藏）" else "",
                        icon = categoryIcon(top.icon),
                        onClick = { editing = top },
                    )
                    for (child in children) {
                        JkDivider(inset = Space.x6 + Space.x05)
                        JkRow(
                            title = child.name + if (child.archived) "（已隐藏）" else "",
                            modifier = Modifier.padding(start = Space.x4),
                            onClick = { editing = child },
                        )
                    }
                    JkDivider(inset = Space.x6 + Space.x05)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = LedgerCategory(LedgerIds.newId(), "", top.type, top.id, top.icon) }
                            .padding(start = Space.x6 + Space.x05, top = Space.x1_5, bottom = Space.x1_5),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Text("添加子分类", style = JkType.small, color = colors.accent, modifier = Modifier.padding(start = Space.x05))
                    }
                }
            }
            item {
                Text(
                    "分类不会被删除，只能隐藏：隐藏后记账时不再出现，已有流水和统计不受影响。",
                    style = JkType.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = Space.x05),
                )
            }
        }
    }

    editing?.let { category ->
        CategoryEditSheet(
            category = category,
            isNew = data.categories.none { it.id == category.id },
            parentName = category.parentId?.let { id -> data.categories.firstOrNull { it.id == id }?.name },
            onDismiss = { editing = null },
            onSave = { viewModel.save(it) { editing = null } },
        )
    }
}

@Composable
private fun CategoryEditSheet(
    category: LedgerCategory,
    isNew: Boolean,
    parentName: String?,
    onDismiss: () -> Unit,
    onSave: (LedgerCategory) -> Unit,
) {
    val colors = Jk.colors
    var name by remember { mutableStateOf(category.name) }
    var icon by remember { mutableStateOf(category.icon) }
    var archived by remember { mutableStateOf(category.archived) }
    val title = when {
        isNew && parentName != null -> "在「$parentName」下添加"
        isNew -> "添加${category.type.label}分类"
        else -> "编辑分类"
    }
    JkSheet(onDismiss = onDismiss, title = title) {
        Column(
            Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            JkTextField(value = name, onValueChange = { name = it.take(20) }, label = "名称", placeholder = "例如：咖啡")
            Spacer(Modifier.height(Space.x2))
            Text("图标", style = JkType.small, color = colors.textSecondary, modifier = Modifier.padding(start = Space.x05, bottom = Space.x1))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
                verticalArrangement = Arrangement.spacedBy(Space.x1),
            ) {
                for ((key, vector) in CategoryIcons) {
                    val selected = key == icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(Radius.tile)
                            .background(if (selected) colors.accentSoft else colors.surface)
                            .border(1.dp, if (selected) colors.accent else colors.border, Radius.tile)
                            .clickable { icon = key },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(vector, contentDescription = null, tint = if (selected) colors.accent else colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (!isNew) {
                Spacer(Modifier.height(Space.x2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("隐藏", style = JkType.body, color = colors.textPrimary)
                        Text("记账时不再出现，已有流水照常统计", style = JkType.caption, color = colors.textTertiary)
                    }
                    JkSwitch(archived, { archived = it })
                }
            }
        }
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkButton("取消", onDismiss, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f))
            JkButton(
                "保存",
                { onSave(category.copy(name = name, icon = icon, archived = archived)) },
                modifier = Modifier.weight(1f),
                enabled = name.isNotBlank(),
            )
        }
    }
}
