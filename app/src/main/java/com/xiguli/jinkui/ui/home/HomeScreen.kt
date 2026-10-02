package com.xiguli.jinkui.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ViewAgenda
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardPalette
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.PasswordHealth
import com.xiguli.jinkui.data.model.deckOrder
import com.xiguli.jinkui.data.model.sortCards
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BrandMark
import com.xiguli.jinkui.ui.components.CardFace
import com.xiguli.jinkui.ui.components.CardTile
import com.xiguli.jinkui.ui.components.EmptyIllustration
import com.xiguli.jinkui.ui.components.ExpiryNote
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
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUi(
    val total: Int = 0,
    /** 筛选项：键 + 显示名。先是出现过的卡片类型，再是标签。 */
    val filters: List<Pair<String, String>> = emptyList(),
    /** 正在搜索或筛选时的结果；为 null 表示显示默认分区。 */
    val results: List<Card>? = null,
    val pinned: List<Card> = emptyList(),
    val recent: List<Card> = emptyList(),
    val all: List<Card> = emptyList(),
    /** 牌堆里的卡：筛选时是结果，否则是全部（置顶在前）。 */
    val deck: List<Card> = emptyList(),
    /** 安全体检发现的问题数，显示在“更多”里。 */
    val issues: Int = 0,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    /** 牌堆当前最上面那张的 ID。按 ID 记而不是按序号记，列表增删后位置不会错乱。 */
    var deckCardId by mutableStateOf<String?>(null)

    fun setQuery(text: String) {
        query.value = text
        deckCardId = null
    }

    fun setFilter(key: String?) {
        filter.value = key
        deckCardId = null
    }

    /** 当前筛选项的键："t:类型" 或 "g:标签"；null 为全部。 */
    val filter = MutableStateFlow<String?>(null)

    /** 列表滚动位置随页面的 ViewModel 保留，进详情再返回不会跳回顶部。 */
    val listState = LazyListState()

    val ui: StateFlow<HomeUi> = combine(container.repository.cards, query, filter, container.settings.flow) { cards, text, selected, settings ->
        val sorted = sortCards(cards, settings.sortOrder)
        val issues = PasswordHealth.analyze(cards, LocalDate.now()).issues.size
        val types = CardType.entries.filter { type -> cards.any { it.type == type } }
        val tags = cards.flatMap { it.tags }.distinct().sorted()
        val filters = (if (cards.any { it.pinned }) listOf("p:" to "置顶") else emptyList()) +
            (if (types.size > 1) types.map { "t:" + it.key to it.label } else emptyList()) +
            tags.map { "g:$it" to "#$it" }
        val active = selected?.takeIf { key -> filters.any { it.first == key } }
        if (text.isNotBlank() || active != null) {
            val results = sorted
                    .filter { card ->
                        val inFilter = when {
                            active == null -> true
                            active == "p:" -> card.pinned
                            active.startsWith("t:") -> card.type.key == active.substring(2)
                            else -> active.substring(2) in card.tags
                        }
                        inFilter && card.matches(text)
                    }
                    .sortedByDescending { it.pinned }
            HomeUi(total = cards.size, filters = filters, results = results, deck = results, issues = issues)
        } else {
            HomeUi(
                total = cards.size,
                filters = filters,
                pinned = sorted.filter { it.pinned },
                recent = cards.filter { !it.pinned && it.lastUsedAt > it.createdAt }
                    .sortedByDescending { it.lastUsedAt }
                    .take(3),
                all = sorted,
                deck = deckOrder(cards, settings.sortOrder),
                issues = issues,
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
    onHealth: () -> Unit,
    onOrganize: () -> Unit,
) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { HomeViewModel(container) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val deckIndex = ui.deck.indexOfFirst { it.id == viewModel.deckCardId }.coerceAtLeast(0)
    val colors = Jk.colors

    var quickCard by remember { mutableStateOf<Card?>(null) }
    var showTypes by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

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
                if (ui.total > 0) {
                    JkIconButton(
                        icon = if (settings.deckView) Icons.Outlined.ViewAgenda else Icons.Outlined.Layers,
                        description = if (settings.deckView) "切换为列表" else "切换为牌堆",
                        onClick = { container.settings.update { it.copy(deckView = !it.deckView) } },
                    )
                }
                JkIconButton(Icons.Outlined.Lock, "立即锁定", { container.vault.lock() })
                JkIconButton(Icons.Outlined.MoreHoriz, "更多", { showMore = true })
            }

            if (ui.total > 0) {
                JkTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    placeholder = "搜索名称、标签或备注",
                    leading = Icons.Outlined.Search,
                    modifier = Modifier.padding(horizontal = Space.x2_5),
                    trailing = {
                        if (query.isNotEmpty()) {
                            JkIconButton(Icons.Outlined.Close, "清除", { viewModel.setQuery("") })
                        } else {
                            Spacer(Modifier.width(Space.x1_5))
                        }
                    },
                )
            }

            if (ui.filters.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.x2_5),
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    modifier = Modifier.padding(top = Space.x1_5),
                ) {
                    item { JkChip("全部", filter == null, { viewModel.setFilter(null) }) }
                    items(ui.filters, key = { it.first }) { (key, label) ->
                        JkChip(label, filter == key, { viewModel.setFilter(if (filter == key) null else key) })
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
                    EmptyIllustration()
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
                    Spacer(Modifier.height(Space.x4))
                    // 首次使用的三步引导
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                        for ((step, text) in listOf(
                            "1" to "放进第一张卡片，11 种类型都有现成的字段",
                            "2" to "在设置里开启指纹或面容，解锁更顺手",
                            "3" to "导出一份加密备份，换手机也不怕",
                        )) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = step,
                                    style = JkType.caption,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceAlt)
                                        .padding(top = 2.dp),
                                )
                                Spacer(Modifier.width(Space.x1_5))
                                Text(text, style = JkType.small, color = colors.textSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.x6))
                }
            } else if (settings.deckView) {
                if (ui.deck.isEmpty()) {
                    JkEmpty("没有匹配的卡片", "试试别的关键词，或者换一个筛选项。", Modifier.weight(1f))
                } else {
                    val current = ui.deck[deckIndex]
                    CardDeck(
                        cards = ui.deck,
                        index = deckIndex,
                        onIndexChange = { viewModel.deckCardId = ui.deck[it].id },
                        onOpen = { onOpen(it.id) },
                        onLongClick = { quickCard = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = Space.x1_5),
                    )
                    DeckActions(
                        card = current,
                        onCopy = { viewModel.copy(current, it) },
                        onOpen = { onOpen(current.id) },
                        onCreate = { showTypes = true },
                    )
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
                            item(key = "none") { JkEmpty("没有匹配的卡片", "试试别的关键词，或者换一个筛选项。") }
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

        if (ui.total > 0 && !settings.deckView) {
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
    if (showMore) {
        JkSheet(onDismiss = { showMore = false }, title = "更多") {
            JkRow(
                title = "安全体检",
                subtitle = if (ui.issues == 0) "弱密码、重复密码与到期提醒" else "发现 ${ui.issues} 处可以改进",
                icon = Icons.Outlined.HealthAndSafety,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onHealth()
                },
            )
            JkRow(
                title = "批量整理",
                subtitle = "多选、排序与标签管理",
                icon = Icons.Outlined.DoneAll,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onOrganize()
                },
            )
            JkRow(
                title = "密码生成器",
                icon = Icons.Outlined.Key,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    showGenerator = true
                },
            )
            JkRow(
                title = "设置",
                subtitle = "解锁方式、自动锁定、加密备份",
                icon = Icons.Outlined.Settings,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    onSettings()
                },
            )
        }
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

private const val DECK_VISIBLE = 3
private const val CARD_ASPECT = 1.586f
private val DECK_PEEK = 12.dp

/** 一张牌在牌堆第 [depth] 层时的姿态：每深一层缩小 5%、上移一个露边高度；超出可见层数后淡出。 */
private fun GraphicsLayerScope.deckPose(depth: Float, peekPx: Float) {
    val level = depth.coerceAtLeast(0f)
    val scale = 1f - 0.05f * level
    transformOrigin = TransformOrigin(0.5f, 0f)
    scaleX = scale
    scaleY = scale
    translationY = -peekPx * level
    alpha = (DECK_VISIBLE + 1f - level).coerceIn(0f, 1f)
}

/**
 * 牌堆：当前卡完整展示，后面的卡从上方露出边缘。
 *
 *  - 左滑：最上面的牌跟手移出并回到牌底，后面的牌向前补位；
 *  - 右滑：上一张从左侧滑回最上面，其余的牌后退一层；
 *  - 循环浏览，松手不到位则回弹。
 *
 * 位移、缩放、旋转都写在 graphicsLayer 里，拖动过程只重绘图层，不触发重组。
 */
@Composable
private fun CardDeck(
    cards: List<Card>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onOpen: (Card) -> Unit,
    onLongClick: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = cards.size
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var drag by remember { mutableFloatStateOf(0f) }
    var animating by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        val stackSpace = DECK_PEEK * DECK_VISIBLE
        val indicatorSpace = 52.dp
        val byWidth = maxWidth - Space.x2_5 * 2
        val byHeight = (maxHeight - stackSpace - indicatorSpace) * CARD_ASPECT
        // 屏幕极矮时也保证一个可用的最小宽度，避免算出负数。
        val cardWidth = maxOf(minOf(byWidth, byHeight, 440.dp), 160.dp)
        val widthPx = with(density) { cardWidth.toPx() }
        val peekPx = with(density) { DECK_PEEK.toPx() }
        val travel = widthPx * 1.2f

        // direction：1 下一张，-1 上一张，0 回弹。
        val settle: suspend (Int, Float) -> Unit = { direction, velocity ->
            animating = true
            try {
                if (direction == 0) {
                    animate(drag, 0f, velocity, spring(dampingRatio = 0.78f, stiffness = 380f)) { value, _ -> drag = value }
                } else {
                    animate(drag, -direction * travel, velocity, tween(durationMillis = 240)) { value, _ -> drag = value }
                    onIndexChange((index + direction).mod(count))
                    drag = 0f
                }
            } finally {
                animating = false
            }
        }
        val dragState = rememberDraggableState { delta -> if (!animating) drag += delta }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .padding(top = stackSpace)
                    .width(cardWidth)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        enabled = count > 1 && !animating,
                        onDragStopped = { velocity ->
                            val direction = when {
                                drag < -widthPx * 0.25f || velocity < -900f -> 1
                                drag > widthPx * 0.25f || velocity > 900f -> -1
                                else -> 0
                            }
                            settle(direction, velocity)
                        },
                    ),
            ) {
                val previousIndex = (index - 1).mod(count)
                // 由远及近绘制。最深的一张平时不可见，滑动时才淡入补位。
                for (depth in (DECK_VISIBLE + 1) downTo 1) {
                    if (depth < count) {
                        val listIndex = (index + depth).mod(count)
                        val card = cards[listIndex]
                        key(card.id) {
                            DeckCard(
                                card = card,
                                depth = { depth + (drag / travel).coerceIn(-1f, 1f) },
                                modifier = Modifier.graphicsLayer {
                                    val shift = (drag / travel).coerceIn(-1f, 1f)
                                    deckPose(depth + shift, peekPx)
                                    // 牌很少时，“上一张”同时也在牌堆后面：它滑回最上面的过程中，后面的那个分身淡出。
                                    if (listIndex == previousIndex && shift > 0f) alpha *= 1f - shift
                                },
                            )
                        }
                    }
                }

                val front = cards[index]
                key(front.id) {
                    DeckCard(
                        card = front,
                        depth = { (drag / travel).coerceIn(0f, 1f) },
                        onClick = { onOpen(front) },
                        onLongClick = { onLongClick(front) },
                        modifier = Modifier.graphicsLayer {
                            if (drag < 0f) {
                                // 像用手捻走一张牌：绕卡片下方的支点转一个小角度。
                                transformOrigin = TransformOrigin(0.5f, 1.25f)
                                translationX = drag
                                rotationZ = drag / widthPx * 7f
                            } else {
                                deckPose((drag / travel).coerceIn(0f, 1f), peekPx)
                            }
                        },
                    )
                }

                if (count > 1) {
                    val previous = cards[previousIndex]
                    key("incoming-" + previous.id) {
                        DeckCard(
                            card = previous,
                            depth = { 0f },
                            modifier = Modifier.graphicsLayer {
                                val progress = (drag / travel).coerceIn(0f, 1f)
                                alpha = if (drag > 0f) 1f else 0f
                                transformOrigin = TransformOrigin(0.5f, 1.25f)
                                translationX = -(1f - progress) * travel
                                rotationZ = -(1f - progress) * 7f
                            },
                        )
                    }
                }
            }

            DeckIndicator(
                index = index,
                count = count,
                enabled = count > 1 && !animating,
                onPrevious = { scope.launch { settle(-1, 0f) } },
                onNext = { scope.launch { settle(1, 0f) } },
            )
        }
    }
}

/** 牌堆里的一张牌。[depth] 越大压得越暗；只有最上面那张可以点。 */
@Composable
private fun DeckCard(
    card: Card,
    depth: () -> Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth()) {
        CardFace(
            title = card.title,
            type = card.type,
            colorIndex = card.color,
            hint = CardTemplates.face(card).primary,
            secondaryLabel = CardTemplates.face(card).secondaryLabel,
            secondary = CardTemplates.face(card).secondary,
            pinned = card.pinned,
            aspect = CARD_ASPECT,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .clip(Radius.card)
                            .combinedClickable(onClickLabel = "查看详情", onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier
                    },
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = (depth() * 0.1f).coerceIn(0f, 0.45f) }
                .clip(Radius.card)
                .background(Color.Black),
        )
    }
}

/** 页指示：不超过 9 张用圆点，更多时显示“第几张 / 共几张”；两侧的箭头可以代替滑动。 */
@Composable
private fun DeckIndicator(index: Int, count: Int, enabled: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier.padding(top = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count > 1) {
            JkIconButton(Icons.Outlined.ChevronLeft, "上一张", onPrevious, tint = colors.textTertiary, enabled = enabled)
        }
        if (count in 2..9) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.x1 - 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Space.x1),
            ) {
                for (position in 0 until count) {
                    val active = position == index
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (active) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.textPrimary else colors.border),
                    )
                }
            }
        } else {
            Text(
                text = "${index + 1} / $count",
                style = JkType.small,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Space.x1_5),
            )
        }
        if (count > 1) {
            JkIconButton(Icons.Outlined.ChevronRight, "下一张", onNext, tint = colors.textTertiary, enabled = enabled)
        }
    }
}

/** 牌堆下方的操作：一键复制当前卡最常用的两个字段，进详情，以及新建。 */
@Composable
private fun DeckActions(card: Card, onCopy: (CardField) -> Unit, onOpen: () -> Unit, onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.x2_5)
            .padding(top = Space.x1, bottom = Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        val quick = card.quickCopyFields
        if (quick.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                for (field in quick) {
                    JkButton(
                        text = "复制${field.label}",
                        onClick = { onCopy(field) },
                        style = ButtonStyle.Secondary,
                        icon = Icons.Outlined.ContentCopy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkButton("查看详情", onOpen, modifier = Modifier.weight(1f))
            JkButton("新建", onCreate, style = ButtonStyle.Secondary, icon = Icons.Outlined.Add)
        }
    }
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
            hint = CardTemplates.face(card).primary,
            secondaryLabel = CardTemplates.face(card).secondaryLabel,
            secondary = CardTemplates.face(card).secondary,
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
                    hint = CardTemplates.face(card).primary,
                    secondaryLabel = CardTemplates.face(card).secondaryLabel,
                    secondary = CardTemplates.face(card).secondary,
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
                    text = CardTemplates.face(card).primary,
                    style = JkType.small,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ExpiryNote(card, Modifier.padding(start = Space.x1_5))
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

/** 新建卡片：直接从 11 种卡面里挑一种。 */
@Composable
private fun TypePickerSheet(onDismiss: () -> Unit, onPick: (CardType) -> Unit) {
    JkSheet(onDismiss = onDismiss, title = "新建卡片") {
        Column(
            modifier = Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.x1_5),
        ) {
            for (pair in CardType.entries.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x1_5)) {
                    for (type in pair) {
                        CardFace(
                            title = type.label,
                            type = type,
                            colorIndex = CardPalette.AUTO,
                            hint = "",
                            aspect = 1.9f,
                            mini = true,
                            modifier = Modifier
                                .weight(1f)
                                .clip(Radius.field)
                                .clickable { onPick(type) },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
