package com.xiguli.jinkui.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.groupDigits
import com.xiguli.jinkui.ui.card.MASK
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.CardFace
import com.xiguli.jinkui.ui.components.ExpiryNote
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val DECK_ASPECT = 1.586f

/** 当前卡的宽度：左边留页边距，右边留出牌堆展开的空间。 */
private fun deckCardWidth(available: Dp): Dp =
    maxOf(minOf(available - Space.x2_5 - DeckLayout.FAN_DP.dp - Space.x1_5, 340.dp), 200.dp)

/**
 * 牌堆首页。
 *
 * 上半部分是牌堆：当前卡靠左完整展示，后面的牌向右依次缩小、露出边缘并逐层融进底色；
 * 左右拖动连续翻牌，点后面露出的牌或左缘的窄边可以直接切过去。
 * 下半部分是当前卡的要紧信息，样式与列表行一致，点一行即复制。
 */
@Composable
internal fun DeckHome(
    cards: List<Card>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onOpen: (Card) -> Unit,
    onLongClick: (Card) -> Unit,
    onCopy: (Card, CardField) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) {
        EmptyDeck(onClearFilters, modifier)
        return
    }
    val count = cards.size
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 连续的“看到第几张”。拖动与动画只改它，各张牌在 graphicsLayer 里按它算姿态，不触发重组。
    var position by remember { mutableFloatStateOf(index.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val raw = remember { FloatArray(1) }
    val settleJob = remember { arrayOfNulls<Job>(1) }
    val settled by remember(count) { derivedStateOf { position.roundToInt().coerceIn(0, count - 1) } }

    val goTo: (Int) -> Unit = { target ->
        settleJob[0]?.cancel()
        settleJob[0] = scope.launch {
            animate(position, target.toFloat(), animationSpec = spring(dampingRatio = 0.84f, stiffness = 300f)) { value, _ -> position = value }
            onIndexChange(target)
        }
    }

    // 外部改变了当前卡（搜索、筛选、删除）时跟过去。
    LaunchedEffect(index, count) {
        if (!dragging && position.roundToInt() != index) {
            animate(position, index.toFloat(), animationSpec = tween(durationMillis = 220)) { value, _ -> position = value }
        }
    }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.x2),
        ) {
            val cardWidth = deckCardWidth(maxWidth)
            val cardHeight = cardWidth / DECK_ASPECT
            val widthPx = with(density) { cardWidth.toPx() }
            val unitPx = with(density) { 1.dp.toPx() }
            val stepPx = widthPx + DeckLayout.GAP_DP * unitPx

            val dragState = rememberDraggableState { delta ->
                raw[0] -= delta / stepPx
                position = DeckLayout.rubberBand(raw[0], count)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        enabled = count > 1,
                        onDragStarted = {
                            settleJob[0]?.cancel()
                            raw[0] = position
                            dragging = true
                        },
                        onDragStopped = { velocity ->
                            val velocityCards = -velocity / stepPx
                            val target = DeckLayout.settleTarget(position, velocityCards, count)
                            dragging = false
                            settleJob[0] = scope.launch {
                                animate(
                                    initialValue = position,
                                    targetValue = target.toFloat(),
                                    initialVelocity = velocityCards,
                                    animationSpec = spring(dampingRatio = 0.84f, stiffness = 300f),
                                ) { value, _ -> position = value }
                                onIndexChange(target)
                            }
                        },
                    ),
            ) {
                // 只组合当前卡附近的几张；序号大的先画，序号小的盖在上面，层次固定不会跳变。
                val first = maxOf(0, settled - 2)
                val last = minOf(count - 1, settled + DeckLayout.VISIBLE_BEHIND + 1)
                for (i in last downTo first) {
                    val card = cards[i]
                    key(card.id) {
                        DeckCard(
                            card = card,
                            haze = { DeckLayout.haze(i - position) },
                            clickLabel = if (i == settled) "查看详情" else "切到这一张",
                            onClick = { if (i == settled) onOpen(card) else goTo(i) },
                            onLongClick = if (i == settled) ({ onLongClick(card) }) else null,
                            modifier = Modifier
                                .padding(start = Space.x2_5)
                                .width(cardWidth)
                                .graphicsLayer {
                                    val d = i - position
                                    val scale = DeckLayout.scale(d)
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = DeckLayout.translationX(d, widthPx, unitPx)
                                    alpha = DeckLayout.alpha(d)
                                },
                        )
                    }
                }
            }
        }

        if (count > 1) {
            DeckProgress(position = { position }, settled = settled, count = count)
        }
        DeckInfo(
            card = cards[settled],
            onOpen = { onOpen(cards[settled]) },
            onCopy = { onCopy(cards[settled], it) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** 牌堆里的一张牌。后面的牌盖一层页面底色做“雾化”，离得越远越淡。 */
@Composable
private fun DeckCard(
    card: Card,
    haze: () -> Float,
    clickLabel: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val face = CardTemplates.face(card)
    Box(modifier) {
        CardFace(
            title = card.title,
            type = card.type,
            colorIndex = card.color,
            hint = face.primary,
            secondaryLabel = face.secondaryLabel,
            secondary = face.secondary,
            pinned = card.pinned,
            aspect = DECK_ASPECT,
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radius.card)
                .combinedClickable(onClickLabel = clickLabel, onClick = onClick, onLongClick = onLongClick),
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = haze() }
                .clip(Radius.card)
                .background(Jk.colors.bg),
        )
    }
}

/** 进度：一根细轨上的滑块随拖动连续移动，旁边是“第几张 / 共几张”。 */
@Composable
private fun DeckProgress(position: () -> Float, settled: Int, count: Int) {
    val colors = Jk.colors
    val trackWidth = 72.dp
    val thumbWidth = maxOf(trackWidth / count, 10.dp)
    val travelPx = with(LocalDensity.current) { (trackWidth - thumbWidth).toPx() }
    Row(
        modifier = Modifier.padding(start = Space.x2_5, top = Space.x2_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.border),
        ) {
            Box(
                Modifier
                    .width(thumbWidth)
                    .height(3.dp)
                    .graphicsLayer { translationX = (position() / (count - 1)).coerceIn(0f, 1f) * travelPx }
                    .clip(CircleShape)
                    .background(colors.textPrimary),
            )
        }
        Spacer(Modifier.width(Space.x1_5))
        Text("${settled + 1} / $count", style = JkType.caption, color = colors.textTertiary)
    }
}

/** 当前卡的要紧信息：标题、类型与标签、前几个字段。字段行与列表行同一套样式，点一行即复制。 */
@Composable
private fun DeckInfo(card: Card, onOpen: () -> Unit, onCopy: (CardField) -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    // 换卡时信息区轻轻淡入，避免文字生硬跳变。
    var shown by remember(card.id) { mutableStateOf(false) }
    LaunchedEffect(card.id) { shown = true }
    val fade by animateFloatAsState(targetValue = if (shown) 1f else 0f, animationSpec = tween(durationMillis = 180), label = "deckInfo")

    val fields = CardTemplates.displayOrder(card).filter { it.value.isNotBlank() }.take(4)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = fade }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x2_5)
            .padding(top = Space.x2_5, bottom = 112.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.title.ifBlank { "未命名" },
                    style = JkType.title,
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
                ExpiryNote(card, Modifier.padding(top = Space.x05))
            }
            Spacer(Modifier.width(Space.x1_5))
            JkButton("查看详情", onOpen, style = ButtonStyle.Secondary, compact = true)
        }

        Spacer(Modifier.height(Space.x1_5))
        if (fields.isEmpty()) {
            JkDivider()
            Text(
                "这张卡片还没有内容。点「查看详情」进去填写。",
                style = JkType.small,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = Space.x2),
            )
        }
        for (field in fields) {
            JkDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "复制${field.label}") { onCopy(field) }
                    .padding(vertical = Space.x1_5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(field.label.ifBlank { "未命名" }, style = JkType.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            field.sensitive -> MASK
                            field.kind == FieldKind.NUMBER -> groupDigits(field.value)
                            else -> field.value
                        },
                        style = if (field.sensitive || field.kind == FieldKind.NUMBER) JkType.mono else JkType.body,
                        color = if (field.sensitive) colors.textSecondary else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .padding(start = Space.x1_5)
                        .size(18.dp),
                )
            }
        }
    }
}

/** 搜索或筛选没有结果：一叠空白卡位，姿态与真牌堆一致。 */
@Composable
private fun EmptyDeck(onClearFilters: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.x2),
        ) {
            val cardWidth = deckCardWidth(maxWidth)
            val widthPx = with(density) { cardWidth.toPx() }
            val unitPx = with(density) { 1.dp.toPx() }
            for (depth in 2 downTo 0) {
                Box(
                    modifier = Modifier
                        .padding(start = Space.x2_5)
                        .width(cardWidth)
                        .aspectRatio(DECK_ASPECT)
                        .graphicsLayer {
                            val d = depth.toFloat()
                            val scale = DeckLayout.scale(d)
                            scaleX = scale
                            scaleY = scale
                            translationX = DeckLayout.translationX(d, widthPx, unitPx)
                            alpha = 1f - DeckLayout.haze(d)
                        }
                        .clip(Radius.card)
                        .background(colors.surface)
                        .border(1.dp, colors.border, Radius.card),
                    contentAlignment = Alignment.Center,
                ) {
                    if (depth == 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Space.x2_5)) {
                            Text("没有匹配的卡片", style = JkType.heading, color = colors.textPrimary)
                            Spacer(Modifier.height(Space.x05))
                            Text(
                                "换个关键词，或者换一个筛选项",
                                style = JkType.small,
                                color = colors.textTertiary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        JkButton(
            text = "清除搜索与筛选",
            onClick = onClearFilters,
            style = ButtonStyle.Secondary,
            compact = true,
            modifier = Modifier.padding(start = Space.x2_5, top = Space.x2_5),
        )
    }
}
