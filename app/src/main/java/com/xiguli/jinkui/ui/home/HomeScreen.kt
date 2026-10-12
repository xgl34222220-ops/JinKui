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
import androidx.compose.runtime.LaunchedEffect
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
import com.xiguli.jinkui.data.model.copyText
import com.xiguli.jinkui.data.model.CardPalette
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.PasswordHealth
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
import com.xiguli.jinkui.ui.components.pressClickable
import com.xiguli.jinkui.ui.components.pressScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** 体检只依赖卡片本身：卡片不变时，敲搜索词不会重新跑一遍弱密码 / 重复密码分析。 */
    private val issues = container.repository.cards
        .map { cards -> PasswordHealth.analyze(cards, LocalDate.now()).issues.size }
        .distinctUntilChanged()

    /**
     * 首屏同步算出初值，避免先闪一下“匣子还是空的”；之后的搜索、筛选、排序都在后台线程计算，
     * 卡片多时打字也不卡主线程。
     */
    val ui: StateFlow<HomeUi> = combine(
        container.repository.cards,
        query,
        filter,
        container.settings.flow,
        issues,
    ) { cards, text, selected, settings, issueCount ->
        buildHomeUi(cards, text, selected, settings.sortOrder, issueCount)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialUi())

    private fun initialUi(): HomeUi {
        val cards = container.repository.cards.value
        return buildHomeUi(
            cards = cards,
            query = query.value,
            filter = filter.value,
            sortOrder = container.settings.current.sortOrder,
            issues = PasswordHealth.analyze(cards, LocalDate.now()).issues.size,
        )
    }

    fun togglePin(card: Card) {
        viewModelScope.launch {
            runCatching { container.repository.setPinned(card.id, !card.pinned) }
        }
    }

    fun copy(card: Card, field: CardField) {
        // 两步验证字段复制此刻的验证码，而不是密钥
        val text = field.copyText()
        if (text == null) {
            container.messages.show("「${field.label}」的密钥无效，请到编辑页修正")
            return
        }
        val seconds = container.clipboard.copy(field.label, text)
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
    /** 1.5.0-alpha01：卡包列表底部的回收站入口。 */
    onTrash: () -> Unit = {},
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

    // 列表视图有自己的一整套版面；牌堆与空首页继续用下面原来的布局。
    if (!settings.deckView && ui.total > 0) {
        ListHome(
            ui = ui,
            query = query,
            filter = filter,
            listState = viewModel.listState,
            sortOrder = settings.sortOrder,
            onQuery = viewModel::setQuery,
            onFilter = viewModel::setFilter,
            onOpen = { onOpen(it.id) },
            onLongClick = { quickCard = it },
            onCopy = { card, field -> viewModel.copy(card, field) },
            onToggleView = { container.settings.update { it.copy(deckView = true) } },
            onLock = { container.vault.lock() },
            onCreate = { showTypes = true },
            onTrash = onTrash,
            onMore = { showMore = true },
            onHealth = onHealth,
            onSort = { order -> container.settings.update { it.copy(sortOrder = order) } },
            onTogglePin = viewModel::togglePin,
        )
    } else {
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
                        Text("卡包", style = JkType.pageTitle, color = colors.textPrimary)
                        Text(
                            text = if (ui.total == 0) "只存在这台手机里的安全卡包" else "${ui.total} 张卡片 · 只在本机加密保存",
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
                    if (ui.total > 0) NewCardButton { showTypes = true }
                    JkIconButton(Icons.Outlined.MoreHoriz, "更多", { showMore = true })
                }

                if (ui.total > 0) {
                    JkTextField(
                        value = query,
                        onValueChange = { viewModel.setQuery(it) },
                        placeholder = "搜索名称、标签、账号或备注",
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
                        // JkChip 布局高 48dp（触控区），视觉仍是 32dp：上边距相应减 8dp，筛选条看上去位置不变
                        modifier = Modifier.padding(top = Space.x05),
                    ) {
                        item(key = "homescreen-297", contentType = "homescreen-static") { JkChip("全部", filter == null, { viewModel.setFilter(null) }) }
                        items(ui.filters, key = { it.first }, contentType = { "homescreen-row" }) { (key, label) ->
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
                    DeckHome(
                        cards = ui.deck,
                        index = deckIndex,
                        onIndexChange = { viewModel.deckCardId = ui.deck[it].id },
                        onOpen = { onOpen(it.id) },
                        onLongClick = { quickCard = it },
                        onCopy = { card, field -> viewModel.copy(card, field) },
                        onClearFilters = {
                            viewModel.setQuery("")
                            viewModel.setFilter(null)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
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
            JkRow(
                title = "立即锁定",
                subtitle = "清掉内存里的明文，回到解锁页",
                icon = Icons.Outlined.Lock,
                modifier = Modifier.clip(Radius.control),
                onClick = {
                    showMore = false
                    container.vault.lock()
                },
            )
        }
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
                                .clickable(onClickLabel = "新建${type.label}") { onPick(type) },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
