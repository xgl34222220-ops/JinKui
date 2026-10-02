package com.xiguli.jinkui.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BrandMark
import com.xiguli.jinkui.ui.components.CardFace
import com.xiguli.jinkui.ui.components.CardTile
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkEmpty
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.icon
import com.xiguli.jinkui.ui.generator.GeneratorSheet
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUi(
    val total: Int = 0,
    val tags: List<String> = emptyList(),
    /** 正在搜索或按标签筛选时的结果；为 null 表示显示默认分区。 */
    val results: List<Card>? = null,
    val pinned: List<Card> = emptyList(),
    val recent: List<Card> = emptyList(),
    val all: List<Card> = emptyList(),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val tag = MutableStateFlow<String?>(null)

    /** 列表滚动位置随页面的 ViewModel 保留，进详情再返回不会跳回顶部。 */
    val listState = LazyListState()

    val ui: StateFlow<HomeUi> = combine(container.repository.cards, query, tag) { cards, text, selectedTag ->
        val sorted = cards.sortedBy { it.title.lowercase() }
        val tags = cards.flatMap { it.tags }.distinct().sorted()
        val activeTag = selectedTag?.takeIf { it in tags }
        if (text.isNotBlank() || activeTag != null) {
            HomeUi(
                total = cards.size,
                tags = tags,
                results = sorted
                    .filter { (activeTag == null || activeTag in it.tags) && it.matches(text) }
                    .sortedByDescending { it.pinned },
            )
        } else {
            HomeUi(
                total = cards.size,
                tags = tags,
                pinned = sorted.filter { it.pinned },
                recent = cards.filter { !it.pinned && it.lastUsedAt > it.createdAt }
                    .sortedByDescending { it.lastUsedAt }
                    .take(3),
                all = sorted,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUi())

    fun togglePin(card: Card) {
        viewModelScope.launch {
            runCatching { container.repository.setPinned(card.id, !card.pinned) }
        }
    }

    fun copy(card: Card, field: CardField) {
        val seconds = container.clipboard.copy(field.label, field.value)
        container.messages.show("已复制「${field.label}」，$seconds 秒后清空剪贴板")
        viewModelScope.launch {
            runCatching { container.repository.markUsed(card.id) }
        }
    }
}

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onCreate: (CardType) -> Unit,
    onSettings: () -> Unit,
) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { HomeViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tag by viewModel.tag.collectAsStateWithLifecycle()
    val colors = Jk.colors

    var quickCard by remember { mutableStateOf<Card?>(null) }
    var showTypes by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.x2_5, end = Space.x1, top = Space.x1_5, bottom = Space.x1_5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("金匮", style = JkType.brand, color = colors.textPrimary)
                    Text(
                        text = if (ui.total == 0) "只存在这台手机里的安全卡包" else "${ui.total} 张卡片，加密保存在本机",
                        style = JkType.caption,
                        color = colors.textTertiary,
                    )
                }
                JkIconButton(Icons.Outlined.Key, "密码生成器", { showGenerator = true })
                JkIconButton(Icons.Outlined.Lock, "立即锁定", { container.vault.lock() })
                JkIconButton(Icons.Outlined.Settings, "设置", onSettings)
            }

            if (ui.total > 0) {
                JkTextField(
                    value = query,
                    onValueChange = { viewModel.query.value = it },
                    placeholder = "搜索名称、标签或备注",
                    leading = Icons.Outlined.Search,
                    modifier = Modifier.padding(horizontal = Space.x2_5),
                    trailing = {
                        if (query.isNotEmpty()) {
                            JkIconButton(Icons.Outlined.Close, "清除", { viewModel.query.value = "" })
                        } else {
                            Spacer(Modifier.width(Space.x1_5))
                        }
                    },
                )
            }

            if (ui.tags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.x2_5),
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    modifier = Modifier.padding(top = Space.x1_5),
                ) {
                    item { JkChip("全部", tag == null, { viewModel.tag.value = null }) }
                    items(ui.tags, key = { it }) { name ->
                        JkChip(name, tag == name, { viewModel.tag.value = if (tag == name) null else name })
                    }
                }
            }

            if (ui.total == 0) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Space.x5),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BrandMark(size = 72.dp)
                    Spacer(Modifier.height(Space.x3))
                    Text("匣子还是空的", style = JkType.title, color = colors.textPrimary)
                    Spacer(Modifier.height(Space.x1))
                    Text(
                        "密码、银行卡、证件……放进来的每一样东西，都只有你能打开。",
                        style = JkType.small,
                        color = colors.textTertiary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.x3))
                    JkButton("放进第一张卡片", { showTypes = true }, icon = Icons.Outlined.Add)
                    Spacer(Modifier.height(Space.x6))
                }
            } else {
                LazyColumn(
                    state = viewModel.listState,
                    contentPadding = PaddingValues(top = Space.x05, bottom = 112.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    val results = ui.results
                    if (results != null) {
                        item(key = "h-results") { SectionHeader("找到 ${results.size} 张") }
                        if (results.isEmpty()) {
                            item(key = "none") { JkEmpty("没有匹配的卡片", "试试别的关键词，或者换一个标签。") }
                        }
                        itemsIndexed(results, key = { _, card -> "r-" + card.id }) { index, card ->
                            CardListItem(card, index < results.lastIndex, { onOpen(card.id) }, { quickCard = card })
                        }
                    } else {
                        if (ui.pinned.isNotEmpty()) {
                            item(key = "h-pinned") { SectionHeader("置顶") }
                            item(key = "pinned") {
                                PinnedCards(ui.pinned, onOpen = { onOpen(it.id) }, onLongClick = { quickCard = it })
                            }
                        }
                        if (ui.recent.isNotEmpty()) {
                            item(key = "h-recent") { SectionHeader("最近使用") }
                            itemsIndexed(ui.recent, key = { _, card -> "u-" + card.id }) { index, card ->
                                CardListItem(card, index < ui.recent.lastIndex, { onOpen(card.id) }, { quickCard = card })
                            }
                        }
                        item(key = "h-all") { SectionHeader("全部") }
                        itemsIndexed(ui.all, key = { _, card -> "a-" + card.id }) { index, card ->
                            CardListItem(card, index < ui.all.lastIndex, { onOpen(card.id) }, { quickCard = card })
                        }
                    }
                }
            }
        }

        if (ui.total > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(Space.x2_5)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable { showTypes = true }
                    .padding(start = Space.x2, end = Space.x2_5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.x1))
                Text("新建", style = JkType.bodyStrong, color = colors.onPrimary)
            }
        }
    }

    quickCard?.let { card ->
        QuickActionsSheet(
            card = card,
            onDismiss = { quickCard = null },
            onCopy = { field ->
                viewModel.copy(card, field)
                quickCard = null
            },
            onTogglePin = {
                viewModel.togglePin(card)
                quickCard = null
            },
        )
    }
    if (showTypes) {
        TypePickerSheet(
            onDismiss = { showTypes = false },
            onPick = { type ->
                showTypes = false
                onCreate(type)
            },
        )
    }
    if (showGenerator) {
        GeneratorSheet(onDismiss = { showGenerator = false })
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = JkType.small,
        color = Jk.colors.textTertiary,
        modifier = Modifier.padding(start = Space.x2_5, top = Space.x2_5, bottom = Space.x1),
    )
}

/** 置顶区：把最要紧的几张做成真正的“卡面”，横向翻看。 */
@Composable
private fun PinnedCards(cards: List<Card>, onOpen: (Card) -> Unit, onLongClick: (Card) -> Unit) {
    if (cards.size == 1) {
        val card = cards.first()
        CardFace(
            title = card.title,
            type = card.type,
            colorIndex = card.color,
            hint = card.faceHint,
            pinned = true,
            modifier = Modifier
                .padding(horizontal = Space.x2_5)
                .fillMaxWidth()
                .clip(Radius.card)
                .combinedClickable(onClick = { onOpen(card) }, onLongClick = { onLongClick(card) }),
        )
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.x2_5),
            horizontalArrangement = Arrangement.spacedBy(Space.x1_5),
        ) {
            items(cards, key = { it.id }) { card ->
                CardFace(
                    title = card.title,
                    type = card.type,
                    colorIndex = card.color,
                    hint = card.faceHint,
                    pinned = true,
                    modifier = Modifier
                        .width(288.dp)
                        .clip(Radius.card)
                        .combinedClickable(onClick = { onOpen(card) }, onLongClick = { onLongClick(card) }),
                )
            }
        }
    }
}

/** 列表行：不套卡片外框，直接落在底色上，行与行之间只有一根极淡的分隔线。 */
@Composable
private fun CardListItem(card: Card, divider: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = Jk.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = Space.x2_5, vertical = Space.x1_5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    text = card.faceHint,
                    style = JkType.small,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val firstTag = card.tags.firstOrNull()
            if (firstTag != null) {
                Text(
                    text = firstTag,
                    style = JkType.caption,
                    color = colors.textTertiary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Space.x1_5),
                )
            }
            if (card.pinned) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = "已置顶",
                    tint = colors.accent,
                    modifier = Modifier
                        .padding(start = Space.x1)
                        .size(14.dp),
                )
            }
        }
        if (divider) JkDivider(inset = Space.x2_5 + 40.dp + Space.x1_5)
    }
}

/** 长按卡片：直接复制账号、密码等字段，不必进详情页。 */
@Composable
private fun QuickActionsSheet(
    card: Card,
    onDismiss: () -> Unit,
    onCopy: (CardField) -> Unit,
    onTogglePin: () -> Unit,
) {
    val copyable = card.fields.filter { it.value.isNotBlank() }
    JkSheet(onDismiss = onDismiss, title = card.title.ifBlank { "未命名" }) {
        if (copyable.isEmpty()) {
            Text("这张卡片还没有可复制的内容", style = JkType.small, color = Jk.colors.textTertiary)
        }
        Column(Modifier.padding(horizontal = 0.dp)) {
            for (field in copyable.take(6)) {
                JkRow(
                    title = "复制${field.label}",
                    subtitle = if (field.sensitive) "••••••••" else field.value.take(32),
                    icon = Icons.Outlined.ContentCopy,
                    onClick = { onCopy(field) },
                    trailing = {},
                    modifier = Modifier.clip(Radius.control),
                )
            }
            JkDivider(Modifier.padding(vertical = Space.x1))
            JkRow(
                title = if (card.pinned) "取消置顶" else "置顶",
                icon = Icons.Outlined.PushPin,
                onClick = onTogglePin,
                trailing = {},
                modifier = Modifier.clip(Radius.control),
            )
        }
    }
}

@Composable
private fun TypePickerSheet(onDismiss: () -> Unit, onPick: (CardType) -> Unit) {
    val colors = Jk.colors
    JkSheet(onDismiss = onDismiss, title = "新建卡片") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (pair in CardType.entries.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                    for (type in pair) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(Radius.control)
                                .border(1.dp, colors.border, Radius.control)
                                .clickable { onPick(type) }
                                .padding(horizontal = Space.x1_5),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(type.icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(Space.x1_5))
                            Text(type.label, style = JkType.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
