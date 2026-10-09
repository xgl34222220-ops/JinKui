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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.SortOrder
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.CardFace
import com.xiguli.jinkui.ui.components.SearchEmptyIllustration
import com.xiguli.jinkui.ui.components.BankBrand
import com.xiguli.jinkui.ui.components.JkCopyButton
import com.xiguli.jinkui.ui.components.pressClickable
import com.xiguli.jinkui.ui.components.pressScale
import com.xiguli.jinkui.ui.components.rememberCopyFeedback
import com.xiguli.jinkui.ui.components.rememberJkPress
import com.xiguli.jinkui.ui.theme.JkSize
import androidx.compose.foundation.layout.heightIn
import com.xiguli.jinkui.ui.components.ExpiryNote
import com.xiguli.jinkui.ui.components.JkBareField
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.cardTheme
import com.xiguli.jinkui.ui.components.icon
import com.xiguli.jinkui.ui.theme.DarkColors
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.LightColors
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space

private val RowShape = RoundedCornerShape(20.dp)

/**
 * 首页的列表视图。
 *
 * 自上而下：品牌与操作 → 搜索 → 筛选 →（体检提示）→ 置顶卡面 →（最近使用）→ 全部。
 * 每一行是一个描边圆角条，左边是这张卡片自己卡面的缩略图，与牌堆里的卡面是同一套语言。
 */
@Composable
internal fun ListHome(
    ui: HomeUi,
    query: String,
    filter: String?,
    listState: LazyListState,
    sortOrder: SortOrder,
    onQuery: (String) -> Unit,
    onFilter: (String?) -> Unit,
    onOpen: (Card) -> Unit,
    onLongClick: (Card) -> Unit,
    onCopy: (Card, CardField) -> Unit,
    onToggleView: () -> Unit,
    onLock: () -> Unit,
    onMore: () -> Unit,
    onCreate: () -> Unit,
    onHealth: () -> Unit,
    onSort: (SortOrder) -> Unit,
) {
    val colors = Jk.colors
    val copyFeedback = rememberCopyFeedback()
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            ListHeader(total = ui.total, onToggleView = onToggleView, onLock = onLock, onMore = onMore)
            SearchBar(query = query, onQuery = onQuery)
            if (ui.filters.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.x2_5),
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    // JkChip 布局高 48dp（触控区），视觉仍是 32dp：上边距相应减 8dp，筛选条看上去位置不变
                    modifier = Modifier.padding(top = Space.x05),
                ) {
                    item { JkChip("全部", filter == null, { onFilter(null) }) }
                    items(ui.filters, key = { it.first }) { (key, label) ->
                        JkChip(label, filter == key, { onFilter(if (filter == key) null else key) })
                    }
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = Space.x1_5, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(Space.x1),
                modifier = Modifier.weight(1f),
            ) {
                val results = ui.results
                if (results != null) {
                    item(key = "h-results") { GroupHeader("找到", results.size) }
                    if (results.isEmpty()) {
                        item(key = "none") {
                            NoResults(onClear = {
                                onQuery("")
                                onFilter(null)
                            })
                        }
                    }
                    items(results, key = { "r-" + it.id }) { card ->
                        CardRow(
                            card, { onOpen(card) }, { onLongClick(card) },
                            { copyFeedback.fire(card.id); onCopy(card, it) },
                            copied = copyFeedback.isCopied(card.id),
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    if (ui.issues > 0) {
                        item(key = "health") { HealthStrip(ui.issues, onHealth) }
                    }
                    if (ui.pinned.isNotEmpty()) {
                        item(key = "h-pinned") { GroupHeader("置顶", ui.pinned.size) }
                        item(key = "pinned") { PinnedFaces(ui.pinned, onOpen, onLongClick) }
                    }
                    val recent = ListLayout.recent(ui.all)
                    if (recent.isNotEmpty()) {
                        item(key = "h-recent") { GroupHeader("最近使用", recent.size) }
                        item(key = "recent") { RecentTiles(recent, onOpen, onLongClick) }
                    }
                    item(key = "h-all") {
                        GroupHeader("全部", ui.all.size) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = JkSize.touch)
                                    .clip(Radius.chip)
                                    .clickable(onClickLabel = "切换排序，当前按${sortOrder.label}") { onSort(ListLayout.nextSort(sortOrder)) }
                                    .padding(horizontal = Space.x1),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(Space.x05))
                                Text("按${sortOrder.label}", style = JkType.small, color = colors.textSecondary)
                            }
                        }
                    }
                    // 排序切换时行平滑挪到新位置（animateItem），不是整表闪一下
                    items(ui.all, key = { "a-" + it.id }) { card ->
                        CardRow(
                            card, { onOpen(card) }, { onLongClick(card) },
                            { copyFeedback.fire(card.id); onCopy(card, it) },
                            copied = copyFeedback.isCopied(card.id),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        CreateButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(Space.x2_5),
        )
    }
}

/** 顶栏：左边是宋体字标与一行概况，右边三个操作收在一个描边胶囊里。 */
@Composable
private fun ListHeader(total: Int, onToggleView: () -> Unit, onLock: () -> Unit, onMore: () -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x2_5, end = Space.x2, top = Space.x2, bottom = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("金匮", style = JkType.brand, color = colors.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
                Spacer(Modifier.width(Space.x1 - 2.dp))
                Text("$total 张卡片，加密保存在本机", style = JkType.caption, color = colors.textTertiary)
            }
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surface)
                .border(1.dp, colors.border, CircleShape)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JkIconButton(Icons.Outlined.Layers, "切换为牌堆", onToggleView)
            JkIconButton(Icons.Outlined.Lock, "立即锁定", onLock)
            JkIconButton(Icons.Outlined.MoreHoriz, "更多", onMore)
        }
    }
}

/** 搜索：无描边的浅底条，比输入框更轻，不和下面的卡片条抢层次。 */
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .padding(horizontal = Space.x2_5)
            .fillMaxWidth()
            .height(48.dp)
            .clip(Radius.control)
            .background(colors.surfaceAlt)
            .padding(start = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.x1_5))
        JkBareField(
            value = query,
            onValueChange = onQuery,
            placeholder = "搜索名称、标签、账号或备注",
            imeAction = ImeAction.Done,
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            JkIconButton(Icons.Outlined.Close, "清除", { onQuery("") })
        } else {
            Spacer(Modifier.width(Space.x2))
        }
    }
}

/** 分组标题：名称 + 数量，右侧可放一个操作。 */
@Composable
private fun GroupHeader(title: String, count: Int, trailing: (@Composable () -> Unit)? = null) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x2_5 + Space.x05, end = Space.x2, top = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = JkType.small, color = colors.textSecondary)
        Spacer(Modifier.width(Space.x1))
        Text(
            text = count.toString(),
            style = JkType.caption,
            color = colors.textTertiary,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surfaceAlt)
                .padding(horizontal = Space.x1, vertical = 1.dp),
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing() else Spacer(Modifier.height(26.dp))
    }
}

/** 卡面缩略图：与完整卡面同一套配色和点缀色，左上角类型图标，左下角两道“卡号”细线。 */
@Composable
private fun CardThumb(type: CardType, colorIndex: Int, modifier: Modifier = Modifier, brand: Color? = null) {
    val theme = cardTheme(type, colorIndex, brand)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(58.dp)
            .height(40.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(theme.start, theme.end)))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    ) {
        Icon(
            type.icon,
            contentDescription = null,
            tint = theme.accent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 7.dp, top = 6.dp)
                .size(14.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 7.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                Modifier
                    .width(22.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.42f)),
            )
            Box(
                Modifier
                    .width(13.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.24f)),
            )
        }
    }
}

/**
 * 一行卡片：缩略卡面 + 名称（置顶、到期提醒）+ 主信息与次要信息 + 一键复制。
 * 点整行进详情，长按出快速复制。
 */
@Composable
private fun CardRow(
    card: Card,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopy: (CardField) -> Unit,
    copied: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    val face = CardTemplates.face(card)
    val target = ListLayout.copyTarget(card)
    val brand = BankBrand.of(card)
    val press = rememberJkPress()
    Row(
        modifier = modifier
            .padding(horizontal = Space.x2_5)
            .fillMaxWidth()
            .pressScale(press)
            .clip(RowShape)
            .background(colors.surface)
            .border(1.dp, colors.border, RowShape)
            .pressClickable(press, onClickLabel = "查看详情", onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "快速复制")
            .heightIn(min = 64.dp)
            .padding(start = Space.x1_5, top = Space.x1_5, bottom = Space.x1_5, end = Space.x05),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardThumb(card.type, card.color, brand = brand)
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.title.ifBlank { "未命名" },
                    style = JkType.bodyStrong,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (card.pinned) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "已置顶",
                        tint = colors.accent,
                        modifier = Modifier
                            .padding(start = Space.x1 - 2.dp)
                            .size(13.dp),
                    )
                }
                ExpiryNote(card, Modifier.padding(start = Space.x1))
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = face.primary,
                    // 打码的末四位、卡号用等宽数字，上下行对齐
                    style = if (ListLayout.isMasked(face.primary)) JkType.digitsSmall else JkType.small,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (face.secondary.isNotBlank()) {
                    Text(
                        text = "　${face.secondaryLabel} ${face.secondary}",
                        style = JkType.digitsSmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (target != null) {
            JkCopyButton(copied, "复制${target.label}", { onCopy(target) })
        } else {
            Spacer(Modifier.width(Space.x1_5))
        }
    }
}

/** 置顶：整张卡面。一张时占满一行，多张时横向翻看。 */
@Composable
private fun PinnedFaces(cards: List<Card>, onOpen: (Card) -> Unit, onLongClick: (Card) -> Unit) {
    if (cards.size == 1) {
        PinnedFace(
            card = cards.first(),
            onOpen = onOpen,
            onLongClick = onLongClick,
            modifier = Modifier
                .padding(horizontal = Space.x2_5)
                .fillMaxWidth(),
        )
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.x2_5),
            horizontalArrangement = Arrangement.spacedBy(Space.x1_5),
        ) {
            items(cards, key = { it.id }) { card ->
                PinnedFace(card = card, onOpen = onOpen, onLongClick = onLongClick, modifier = Modifier.width(272.dp))
            }
        }
    }
}

@Composable
private fun PinnedFace(card: Card, onOpen: (Card) -> Unit, onLongClick: (Card) -> Unit, modifier: Modifier = Modifier) {
    val face = CardTemplates.face(card)
    val press = rememberJkPress(0.985f)
    CardFace(
        brand = BankBrand.of(card),
        title = card.title,
        type = card.type,
        colorIndex = card.color,
        hint = face.primary,
        secondaryLabel = face.secondaryLabel,
        secondary = face.secondary,
        pinned = true,
        modifier = modifier
            .pressScale(press)
            .clip(Radius.card)
            .pressClickable(press, onClickLabel = "查看详情", onClick = { onOpen(card) }, onLongClick = { onLongClick(card) }, onLongClickLabel = "快速复制"),
    )
}

/** 最近使用：横向的小磁贴，形态与下面的列表不同，不显得重复。 */
@Composable
private fun RecentTiles(cards: List<Card>, onOpen: (Card) -> Unit, onLongClick: (Card) -> Unit) {
    val colors = Jk.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.x2_5),
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        items(cards, key = { it.id }) { card ->
            Column(
                modifier = Modifier
                    .width(136.dp)
                    .clip(RowShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, RowShape)
                    .combinedClickable(onClickLabel = "查看详情", onClick = { onOpen(card) }, onLongClick = { onLongClick(card) })
                    .padding(Space.x1_5),
            ) {
                CardThumb(card.type, card.color, brand = BankBrand.of(card))
                Spacer(Modifier.height(Space.x1_5))
                Text(
                    text = card.title.ifBlank { "未命名" },
                    style = JkType.bodyStrong,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = CardTemplates.face(card).primary,
                    style = JkType.caption.copy(fontFeatureSettings = "tnum"),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 体检发现问题时的一条提示，点了直达安全体检。 */
@Composable
private fun HealthStrip(issues: Int, onClick: () -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .padding(horizontal = Space.x2_5)
            .fillMaxWidth()
            .clip(Radius.control)
            .background(colors.accentSoft)
            .clickable(onClickLabel = "打开安全体检", onClick = onClick)
            .heightIn(min = JkSize.touch)
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.HealthAndSafety, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.x1_5))
        Text("安全体检发现 $issues 处可以改进", style = JkType.small, color = colors.accent, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun NoResults(onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x4, vertical = Space.x5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchEmptyIllustration()
        Spacer(Modifier.height(Space.x2_5))
        Text("没有匹配的卡片", style = JkType.heading, color = Jk.colors.textPrimary)
        Spacer(Modifier.height(Space.x05))
        Text("换个关键词，或者换一个筛选项", style = JkType.small, color = Jk.colors.textTertiary)
        Spacer(Modifier.height(Space.x2_5))
        JkButton("清除搜索与筛选", onClear, style = ButtonStyle.Secondary, compact = true)
    }
}

/** 新建按钮：与卡片同一种圆角的墨色方块，加号用赤金点一下。 */
@Composable
private fun CreateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val shape = RoundedCornerShape(18.dp)
    // 赤金在墨色底上要用亮的一档，在浅色底上用深的一档。
    val gold = if (colors.isDark) LightColors.accent else DarkColors.accent
    val press = rememberJkPress(0.95f)
    Row(
        modifier = modifier
            .pressScale(press)
            .height(52.dp)
            .clip(shape)
            .background(colors.primary)
            .border(1.dp, colors.onPrimary.copy(alpha = 0.14f), shape)
            .pressClickable(press, onClickLabel = "新建卡片", onClick = onClick)
            .padding(start = Space.x2, end = Space.x2_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.x1))
        Text("新建", style = JkType.bodyStrong, color = colors.onPrimary)
    }
}
