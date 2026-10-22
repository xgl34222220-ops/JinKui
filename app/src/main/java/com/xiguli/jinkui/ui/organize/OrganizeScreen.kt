package com.xiguli.jinkui.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.SortOrder
import com.xiguli.jinkui.data.model.sortCards
import com.xiguli.jinkui.data.model.tagCounts
import com.xiguli.jinkui.data.model.withTagRenamed
import com.xiguli.jinkui.data.model.withoutTag
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.CardTile
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCheck
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkEmpty
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OrganizeViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository

    val cards: StateFlow<List<Card>> = combine(repository.cards, container.settings.flow) { cards, settings ->
        sortCards(cards, settings.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repository.cards.value)

    val selected = mutableStateListOf<String>()
    var busy by mutableStateOf(false)
        private set

    fun toggle(id: String) {
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    fun selectAll() {
        val all = cards.value.map { it.id }
        val everything = selected.size == all.size
        selected.clear()
        if (!everything) selected.addAll(all)
    }

    fun deleteSelected() = batch("已将 ${selected.size} 张卡片移到回收站，30 天内可以还原") { targets ->
        targets.forEach { repository.delete(it.id) }
    }

    fun pinSelected(pinned: Boolean) = batch(if (pinned) "已置顶 ${selected.size} 张" else "已取消置顶") { targets ->
        targets.forEach { repository.setPinned(it.id, pinned) }
    }

    fun tagSelected(tag: String) = batch("已给 ${selected.size} 张卡片加上「$tag」") { targets ->
        targets.forEach { if (tag !in it.tags) repository.save(it.copy(tags = it.tags + tag)) }
    }

    fun renameTag(old: String, new: String) = all("已改名为「${new.trim()}」") { cards ->
        cards.filter { old in it.tags }.forEach { repository.save(it.withTagRenamed(old, new)) }
    }

    fun deleteTag(tag: String) = all("已删除标签「$tag」，卡片本身不受影响") { cards ->
        cards.filter { tag in it.tags }.forEach { repository.save(it.withoutTag(tag)) }
    }

    private fun batch(done: String, block: suspend (List<Card>) -> Unit) {
        val ids = selected.toSet()
        all(done) { cards -> block(cards.filter { it.id in ids }) }
    }

    private fun all(done: String, block: suspend (List<Card>) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block(cards.value)
                container.messages.show(done)
            } catch (e: Exception) {
                container.messages.show("操作失败：${e.message.orEmpty()}")
            } finally {
                selected.clear()
                busy = false
            }
        }
    }
}

/** 批量整理：多选后置顶、加标签、删除；调整排序；管理标签。 */
@Composable
fun OrganizeScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { OrganizeViewModel(container) }
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val colors = Jk.colors
    val selected = viewModel.selected
    val allCards by container.repository.cards.collectAsStateWithLifecycle()
    val tags = tagCounts(allCards)

    var confirmDelete by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var editTag by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar(title = if (selected.isEmpty()) "整理" else "已选 ${selected.size} 张", onBack = onBack) {
            if (cards.isNotEmpty()) {
                JkButton(
                    text = if (selected.size == cards.size) "取消全选" else "全选",
                    style = ButtonStyle.Ghost,
                    compact = true,
                    onClick = viewModel::selectAll,
                )
            }
            JkIconButton(Icons.Outlined.LocalOffer, "标签管理", { showTags = true })
        }

        if (cards.isEmpty()) {
            JkEmpty("还没有可整理的卡片", "放进几张卡片后，可以在这里多选、排序和管理标签。", Modifier.weight(1f))
        } else {
            Text(
                "排序方式（首页同步生效）",
                style = JkType.small,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x2_5 + Space.x05, bottom = Space.x1),
            )
            JkSegmented(
                options = SortOrder.entries.map { it to it.label },
                selected = settings.sortOrder,
                onSelect = { order -> container.settings.update { it.copy(sortOrder = order) } },
                modifier = Modifier.padding(horizontal = Space.x2_5),
            )
            LazyColumn(
                contentPadding = PaddingValues(top = Space.x1, bottom = Space.x3),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(cards, key = { _, card -> card.id }, contentType = { _, _ -> "organizescreen-row" }) { index, card ->
                    SelectableRow(
                        card = card,
                        checked = card.id in selected,
                        divider = index < cards.lastIndex,
                        onToggle = { viewModel.toggle(card.id) },
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            val picked = cards.filter { it.id in selected }
            val allPinned = picked.isNotEmpty() && picked.all { it.pinned }
            JkDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = Space.x2_5, vertical = Space.x1_5),
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
            ) {
                JkButton(
                    text = if (allPinned) "取消置顶" else "置顶",
                    style = ButtonStyle.Secondary,
                    enabled = !viewModel.busy,
                    onClick = { viewModel.pinSelected(!allPinned) },
                    modifier = Modifier.weight(1f),
                )
                JkButton(
                    text = "加标签",
                    style = ButtonStyle.Secondary,
                    enabled = !viewModel.busy,
                    onClick = { addTag = true },
                    modifier = Modifier.weight(1f),
                )
                JkButton(
                    text = "删除",
                    style = ButtonStyle.Danger,
                    enabled = !viewModel.busy,
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmDelete) {
        JkDialog(
            title = "删除 ${selected.size} 张卡片？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteSelected()
            },
        ) {
            Text(com.xiguli.jinkui.ui.card.DELETE_EXPLAIN, style = JkType.body, color = colors.textSecondary)
        }
    }

    if (addTag) {
        var text by remember { mutableStateOf("") }
        JkDialog(
            title = "给 ${selected.size} 张卡片加标签",
            onDismiss = { addTag = false },
            confirmText = "加上",
            confirmEnabled = text.isNotBlank(),
            onConfirm = {
                addTag = false
                viewModel.tagSelected(text.trim())
            },
        ) {
            JkTextField(value = text, onValueChange = { text = it }, placeholder = "标签名", imeAction = ImeAction.Done)
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    verticalArrangement = Arrangement.spacedBy(Space.x1),
                ) {
                    for ((name, _) in tags.take(12)) {
                        JkChip(name, selected = text == name, onClick = { text = name })
                    }
                }
            }
        }
    }

    if (showTags) {
        JkSheet(onDismiss = { showTags = false }, title = "标签管理") {
            if (tags.isEmpty()) {
                Text("还没有标签。在编辑卡片时添加，或在这里多选后批量加上。", style = JkType.small, color = colors.textTertiary)
            }
            for ((name, count) in tags) {
                JkRow(
                    title = "#$name",
                    value = "$count 张",
                    modifier = Modifier.clip(Radius.control),
                    onClick = {
                        showTags = false
                        editTag = name
                    },
                )
            }
        }
    }

    editTag?.let { tag ->
        var text by remember(tag) { mutableStateOf(tag) }
        JkDialog(
            title = "标签「$tag」",
            onDismiss = { editTag = null },
            confirmText = "改名",
            confirmEnabled = text.isNotBlank() && text.trim() != tag,
            onConfirm = {
                editTag = null
                viewModel.renameTag(tag, text)
            },
        ) {
            JkTextField(value = text, onValueChange = { text = it }, label = "新名称", imeAction = ImeAction.Done)
            Text("改成已有的标签名时，两个标签会合并。", style = JkType.caption, color = colors.textTertiary)
            JkButton(
                text = "删除这个标签",
                style = ButtonStyle.Danger,
                compact = true,
                onClick = {
                    editTag = null
                    viewModel.deleteTag(tag)
                },
            )
        }
    }
}

@Composable
private fun SelectableRow(card: Card, checked: Boolean, divider: Boolean, onToggle: () -> Unit) {
    val colors = Jk.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = Space.x2_5, vertical = Space.x1_5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JkCheck(checked)
            Spacer(Modifier.width(Space.x1_5))
            CardTile(card.type, card.color)
            Spacer(Modifier.width(Space.x1_5))
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.title.ifBlank { "未命名" },
                    style = JkType.bodyStrong,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (card.tags.isEmpty()) card.type.label else card.type.label + "　" + card.tags.joinToString(" ") { "#$it" },
                    style = JkType.small,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.pinned) {
                Icon(Icons.Outlined.PushPin, contentDescription = "已置顶", tint = colors.accent, modifier = Modifier.size(14.dp))
            }
        }
        if (divider) JkDivider(inset = Space.x2_5 + 22.dp + Space.x1_5 + 40.dp + Space.x1_5)
    }
}
