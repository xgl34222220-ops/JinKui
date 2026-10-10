package com.xiguli.jinkui.ui.home

import com.xiguli.jinkui.ui.nav.transitionOrigin
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.jinkui.core.crypto.Totp
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.SortOrder
import com.xiguli.jinkui.data.model.totpConfig
import com.xiguli.jinkui.ui.components.BankBrand
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.CardFace
import com.xiguli.jinkui.ui.components.ExpiryNote
import com.xiguli.jinkui.ui.components.JkBareField
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkCopyButton
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.SearchEmptyIllustration
import com.xiguli.jinkui.ui.components.SwipeAction
import com.xiguli.jinkui.ui.components.SwipeActionsBox
import com.xiguli.jinkui.ui.components.cardTheme
import com.xiguli.jinkui.ui.components.icon
import com.xiguli.jinkui.ui.components.pressClickable
import com.xiguli.jinkui.ui.components.rememberCopyFeedback
import com.xiguli.jinkui.ui.components.rememberJkPress
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.delay

/** 分组容器里一行的形状：第一行上圆角、最后一行下圆角，中间是直角——整组看上去是一块。 */
internal fun groupShape(index: Int, count: Int): Shape {
    val r = 20.dp
    return when {
        count == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
        index == count - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RoundedCornerShape(0.dp)
    }
}

/**
 * 首页的列表视图（1.0 重做，“搜索优先”）。
 *
 * 自上而下：大标题与三个图标 → 搜索框（最显眼）→ 快速筛选（全部 / 置顶 / 两步验证 / 类型 / 标签）
 * →（体检提示）→ 置顶的小卡面（横向）→（最近使用）→ 全部卡片的分组列表。
 * 参考：1Password / Bitwarden 的搜索在首页最上方；Apple 钱包 / Google 钱包的卡面只给置顶的几张，
 * 其余用紧凑的行；Aegis 的验证码列表（筛“两步验证”时行上直接显示验证码，点一下复制）。
 *
 * 行的手势：点 → 详情；长按 → 快速复制面板；右滑 → 置顶 / 取消置顶；左滑 → 复制账号（或主信息）；
 * 行尾按钮 → 复制密码 / 卡号。读屏用户在自定义操作里能找到同样的置顶与复制。
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
    onHealth: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onTogglePin: (Card) -> Unit = {},
    /** 标题栏右侧的“＋”：新建卡片。 */
    onCreate: () -> Unit = {},
    /** 筛“两步验证”时验证码每秒刷新；测试传 false 并给固定时钟。 */
    ticking: Boolean = true,
    clock: () -> Long = System::currentTimeMillis,
) {
    val copyFeedback = rememberCopyFeedback()
    val showCodes = filter == "o:"
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(showCodes, ticking) {
        while (showCodes && ticking) {
            now = clock()
            delay(1_000L - now % 1_000L)
        }
    }
    val rowCopy: (Card, CardField) -> Unit = { card, field ->
        copyFeedback.fire(card.id + field.id)
        onCopy(card, field)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ListHeader(total = ui.total, onToggleView = onToggleView, onCreate = onCreate, onMore = onMore)
        SearchBar(query = query, onQuery = onQuery)
        if (ui.filters.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.x2_5),
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
                modifier = Modifier.padding(top = Space.x05),
            ) {
                item(key = "listview-179", contentType = "listview-static") { JkChip("全部", filter == null, { onFilter(null) }) }
                items(ui.filters, key = { it.first }, contentType = { "listview-row" }) { (key, label) ->
                    JkChip(label, filter == key, { onFilter(if (filter == key) null else key) })
                }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = Space.x1, bottom = com.xiguli.jinkui.ui.nav.LocalTabBarInset.current + Space.x2),
            modifier = Modifier.weight(1f),
        ) {
            val results = ui.results
            if (results != null) {
                item(key = "h-results", contentType = "h-results") { GroupHeader(if (showCodes) "两步验证" else "找到", results.size) }
                if (results.isEmpty()) {
                    item(key = "none", contentType = "none") {
                        NoResults(onClear = {
                            onQuery("")
                            onFilter(null)
                        })
                    }
                }
                itemsIndexed(results, key = { _, card -> "r-" + card.id }, contentType = { _, _ -> "listview-row" }) { index, card ->
                    CardRow(
                        card = card,
                        shape = groupShape(index, results.size),
                        divider = index < results.lastIndex,
                        onClick = { onOpen(card) },
                        onLongClick = { onLongClick(card) },
                        onCopy = { rowCopy(card, it) },
                        onTogglePin = { onTogglePin(card) },
                        isCopied = { copyFeedback.isCopied(card.id + it) },
                        totpNow = if (showCodes) now else null,
                        modifier = Modifier.animateItem(),
                    )
                }
            } else {
                val wallet = ListLayout.wallet(ui.all)
                if (wallet.isNotEmpty()) {
                    item(key = "wallet", contentType = "wallet") { WalletStack(wallet, onOpen, onLongClick) }
                }
                if (ui.issues > 0) {
                    item(key = "health", contentType = "health") { HealthStrip(ui.issues, onHealth) }
                }
                if (ui.pinned.isNotEmpty()) {
                    item(key = "h-pinned", contentType = "h-pinned") { GroupHeader("置顶", ui.pinned.size) }
                    item(key = "pinned", contentType = "pinned") { PinnedFaces(ui.pinned, onOpen, onLongClick) }
                }
                val recent = ListLayout.recent(ui.all)
                if (recent.isNotEmpty()) {
                    item(key = "h-recent", contentType = "h-recent") { GroupHeader("最近使用", recent.size) }
                    item(key = "recent", contentType = "recent") { RecentChips(recent, onOpen, onLongClick) }
                }
                item(key = "h-all", contentType = "h-all") {
                    GroupHeader("全部", ui.all.size) {
                        SortButton(sortOrder, onSort)
                    }
                }
                // 排序切换时行平滑挪到新位置（animateItem），不是整表闪一下
                itemsIndexed(ui.all, key = { _, card -> "a-" + card.id }, contentType = { _, _ -> "listview-row" }) { index, card ->
                    CardRow(
                        card = card,
                        shape = groupShape(index, ui.all.size),
                        divider = index < ui.all.lastIndex,
                        onClick = { onOpen(card) },
                        onLongClick = { onLongClick(card) },
                        onCopy = { rowCopy(card, it) },
                        onTogglePin = { onTogglePin(card) },
                        isCopied = { copyFeedback.isCopied(card.id + it) },
                        totpNow = null,
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "hint", contentType = "hint") {
                    Text(
                        "右滑置顶，左滑复制账号，长按更多操作",
                        style = JkType.caption,
                        color = Jk.colors.textTertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.x3, vertical = Space.x2),
                    )
                }
            }
        }
    }
}

/** 顶栏：左边是分区大标题与一行概况，右边三个不带边框的图标。 */
@Composable
private fun ListHeader(total: Int, onToggleView: () -> Unit, onCreate: () -> Unit, onMore: () -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.x2_5, end = Space.x1, top = Space.x2, bottom = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("卡包", style = JkType.pageTitle, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
            Text("$total 张卡片 · 只在本机加密保存", style = JkType.caption, color = colors.textTertiary)
        }
        JkIconButton(Icons.Outlined.Layers, "切换为牌堆", onToggleView)
        NewCardButton(onCreate)
        JkIconButton(Icons.Outlined.MoreHoriz, "更多", onMore)
    }
}

/** 标题栏里的“新建卡片”：薄荷实心小圆钮（48dp 触控区）。卡包分区不再有悬浮按钮。 */
@Composable
internal fun NewCardButton(onClick: () -> Unit) {
    val colors = Jk.colors
    Box(
        Modifier
            .size(JkSize.touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "新建卡片", onClick = onClick)
            .semantics { contentDescription = "新建卡片" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

/** 搜索：页面上最显眼的控件——52dp 高的白色胶囊。 */
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .padding(horizontal = Space.x2_5)
            .fillMaxWidth()
            .height(52.dp)
            .clip(Radius.pill)
            .background(colors.surface)
            .then(if (colors.isDark) Modifier else Modifier.border(1.dp, colors.border, Radius.pill))
            .padding(start = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.x1_5))
        JkBareField(
            value = query,
            onValueChange = onQuery,
            placeholder = "搜索卡片、账号、标签或备注",
            imeAction = ImeAction.Search,
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            JkIconButton(Icons.Outlined.Close, "清除", { onQuery("") })
        } else {
            Spacer(Modifier.width(Space.x2))
        }
    }
}

@Composable
private fun SortButton(sortOrder: SortOrder, onSort: (SortOrder) -> Unit) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .heightIn(min = JkSize.touch)
            .clip(Radius.pill)
            .clickable(onClickLabel = "切换排序，当前按${sortOrder.label}") { onSort(ListLayout.nextSort(sortOrder)) }
            .padding(horizontal = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SwapVert, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.x05))
        Text("按${sortOrder.label}", style = JkType.small, color = colors.textSecondary)
    }
}

/** 分组标题：名称 + 数量，右侧可放一个操作。 */
@Composable
private fun GroupHeader(title: String, count: Int, trailing: (@Composable () -> Unit)? = null) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = JkSize.touch)
            .padding(start = Space.x3, end = Space.x1_5, top = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$title · $count",
            style = JkType.overline,
            color = colors.textTertiary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** 卡面缩略图：与完整卡面同一套配色，左上角类型图标，左下角两道“卡号”细线。 */
@Composable
internal fun CardThumb(type: CardType, colorIndex: Int, modifier: Modifier = Modifier, brand: Color? = null) {
    val theme = cardTheme(type, colorIndex, brand)
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .width(52.dp)
            .height(36.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(theme.start, theme.end))),
    ) {
        Icon(
            type.icon,
            contentDescription = null,
            tint = theme.accent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 5.dp)
                .size(13.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 6.dp)
                .width(20.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.42f)),
        )
    }
}

/**
 * 一行卡片（分组列表中的一行）：缩略卡面 + 名称（置顶、到期提醒）+ 主信息与次要信息 + 行尾复制。
 * [totpNow] 不为 null 时（筛选“两步验证”），行尾换成此刻的验证码与剩余秒数，点一下复制验证码。
 */
@Composable
private fun CardRow(
    card: Card,
    shape: Shape,
    divider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopy: (CardField) -> Unit,
    onTogglePin: () -> Unit,
    isCopied: (String) -> Boolean,
    totpNow: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = Jk.colors
    val face = CardTemplates.face(card)
    val target = ListLayout.copyTarget(card)
    val swipeTarget = ListLayout.swipeCopyTarget(card)
    val totp = if (totpNow != null) ListLayout.totpField(card) else null
    val brand = BankBrand.of(card)
    val press = rememberJkPress(0.985f)
    val actions = buildList {
        add(CustomAccessibilityAction(if (card.pinned) "取消置顶" else "置顶") { onTogglePin(); true })
        if (swipeTarget != null) add(CustomAccessibilityAction("复制${swipeTarget.label}") { onCopy(swipeTarget); true })
    }
    // 不用 clip：圆角只画在底色上（clip 会让触摸命中检测依赖圆角轮廓）
    Box(modifier.padding(horizontal = Space.x2_5).transitionOrigin()) {
        SwipeActionsBox(
            start = SwipeAction(if (card.pinned) "取消置顶" else "置顶", Icons.Outlined.PushPin, colors.accentSoft, colors.accent, onTogglePin),
            end = swipeTarget?.let { field -> SwipeAction("复制${field.label}", Icons.Outlined.ContentCopy, colors.primary, colors.onPrimary) { onCopy(field) } },
            shape = shape,
        ) {
            Column(Modifier.background(colors.surface, shape)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressClickable(press, onClickLabel = "查看详情", onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "更多操作")
                        .semantics { customActions = actions }
                        .heightIn(min = 68.dp)
                        .padding(start = Space.x2, top = Space.x1_5, bottom = Space.x1_5, end = Space.x05),
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
                                        .padding(start = Space.x05)
                                        .size(13.dp),
                                )
                            }
                            ExpiryNote(card, Modifier.padding(start = Space.x1))
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = face.primary,
                                style = if (ListLayout.isMasked(face.primary)) JkType.digitsSmall else JkType.small,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (face.secondary.isNotBlank() && totp == null) {
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
                    when {
                        totp != null && totpNow != null -> InlineTotp(totp, totpNow, isCopied(totp.id)) { onCopy(totp) }
                        target != null -> JkCopyButton(isCopied(target.id), "复制${target.label}", { onCopy(target) })
                        else -> Spacer(Modifier.width(Space.x1_5))
                    }
                }
                if (divider) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = Space.x2 + 52.dp + Space.x1_5)
                            .height(1.dp)
                            .background(colors.border),
                    )
                }
            }
        }
    }
}

/** 行尾的实时验证码：等宽大号数字 + 剩余秒数，最后 5 秒转警示色；整块 48dp 以上，点一下复制。 */
@Composable
private fun InlineTotp(field: CardField, now: Long, copied: Boolean, onCopy: () -> Unit) {
    val colors = Jk.colors
    val config = remember(field.value) { field.totpConfig() }
    val code = remember(config, now / 1_000L) { config?.let { Totp.code(it, now) } }
    if (code == null) {
        Text("密钥无效", style = JkType.small, color = colors.danger, modifier = Modifier.padding(end = Space.x1_5))
        return
    }
    val urgent = code.secondsLeft <= 5
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .heightIn(min = JkSize.touch)
            .clip(Radius.control)
            .clickable(onClickLabel = "复制验证码", onClick = onCopy)
            .semantics(mergeDescendants = true) {
                contentDescription = "验证码 ${code.code.toList().joinToString(" ")}，还剩 ${code.secondsLeft} 秒，点按复制"
            }
            .padding(horizontal = Space.x1_5, vertical = Space.x05),
    ) {
        Text(
            if (copied) "已复制" else groupCode(code.code),
            style = JkType.digitsLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = if (urgent) colors.danger else colors.accent,
            maxLines = 1,
        )
        Text("${code.secondsLeft} 秒", style = JkType.amountCaption, color = if (urgent) colors.danger else colors.textTertiary)
    }
}

private fun groupCode(code: String): String =
    if (code.length == 8) code.chunked(4).joinToString(" ") else code.chunked(3).joinToString(" ")

/**
 * 钱包（1.3）：银行卡叠成一摞，最前面一张完整显示，后面两张只露出上沿、依次变窄。
 * 点最前面一张进详情、长按快速复制；点后面露出的边把那张换到最前（弹簧动画）。读屏把每张当成一个按钮。
 */
@Composable
private fun WalletStack(cards: List<Card>, onOpen: (Card) -> Unit, onLongClick: (Card) -> Unit) {
    var front by rememberSaveable { mutableIntStateOf(0) }
    val order = remember(cards, front) { cards.indices.map { (front + it) % cards.size } }
    val peek = 16.dp
    val view = androidx.compose.ui.platform.LocalView.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.x2_5, end = Space.x2_5, top = Space.x1, bottom = Space.x1)
            .padding(top = peek * (cards.size - 1)),
    ) {
        // 从最后面一张画起
        for (depth in order.indices.reversed()) {
            val index = order[depth]
            val card = cards[index]
            key(card.id) {
                val offset by androidx.compose.animation.core.animateDpAsState(-(peek * depth), androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 420f), label = "walletY")
                val inset by androidx.compose.animation.core.animateDpAsState(10.dp * depth, androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 420f), label = "walletX")
                val face = CardTemplates.face(card)
                val press = rememberJkPress(0.97f)
                CardFace(
                    brand = BankBrand.of(card),
                    title = card.title,
                    type = card.type,
                    colorIndex = card.color,
                    hint = face.primary,
                    secondaryLabel = face.secondaryLabel,
                    secondary = face.secondary,
                    pinned = card.pinned,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = inset)
                        .offset(y = offset)
                        .zIndex((cards.size - depth).toFloat())
                        .then(if (depth == 0) Modifier.transitionOrigin() else Modifier)
                        .clip(Radius.card)
                        .then(
                            if (depth == 0) {
                                Modifier.pressClickable(press, onClickLabel = "查看详情", onClick = { onOpen(card) }, onLongClick = { onLongClick(card) }, onLongClickLabel = "更多操作")
                            } else {
                                Modifier.clickable(role = Role.Button, onClickLabel = "把${card.title}换到最前") {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    front = index
                                }
                            },
                        ),
                )
            }
        }
    }
}

/** 置顶：小一号的整张卡面，横向排开（像钱包里最上面的几张卡）。 */
@Composable
private fun PinnedFaces(cards: List<Card>, onOpen: (Card) -> Unit, onLongClick: (Card) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.x2_5),
        horizontalArrangement = Arrangement.spacedBy(Space.x1_5),
    ) {
        items(cards, key = { it.id }, contentType = { "listview-row" }) { card ->
            val face = CardTemplates.face(card)
            val press = rememberJkPress(0.97f)
            CardFace(
                brand = BankBrand.of(card),
                title = card.title,
                type = card.type,
                colorIndex = card.color,
                hint = face.primary,
                secondaryLabel = face.secondaryLabel,
                secondary = face.secondary,
                pinned = true,
                // 完整卡面（名称、打码卡号、有效期）而不是只写类型名的小卡面：一眼认得出是哪张卡
                modifier = Modifier
                    .width(if (cards.size == 1) 260.dp else 216.dp)
                    .transitionOrigin()
                    .clip(Radius.card)
                    .pressClickable(press, onClickLabel = "查看详情", onClick = { onOpen(card) }, onLongClick = { onLongClick(card) }, onLongClickLabel = "更多操作"),
            )
        }
    }
}

/** 最近使用：横向的小胶囊（缩略卡面 + 名称），比整行轻，不和下面的列表重复。 */
@Composable
private fun RecentChips(cards: List<Card>, onOpen: (Card) -> Unit, onLongClick: (Card) -> Unit) {
    val colors = Jk.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.x2_5),
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        items(cards, key = { it.id }, contentType = { "listview-row" }) { card ->
            Row(
                modifier = Modifier
                    .clip(Radius.pill)
                    .background(colors.surface)
                    .combinedClickable(onClickLabel = "查看详情", onClick = { onOpen(card) }, onLongClick = { onLongClick(card) })
                    .heightIn(min = JkSize.touch)
                    .padding(start = Space.x1, end = Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardThumb(card.type, card.color, brand = BankBrand.of(card), modifier = Modifier.size(width = 34.dp, height = 24.dp))
                Spacer(Modifier.width(Space.x1))
                Text(
                    text = card.title.ifBlank { "未命名" },
                    style = JkType.small,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
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
            .padding(start = Space.x2_5, end = Space.x2_5, top = Space.x1)
            .fillMaxWidth()
            .clip(Radius.card)
            .background(colors.surface)
            .clickable(onClickLabel = "打开安全体检", onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.x2, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.HealthAndSafety, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Space.x1_5))
        Column(Modifier.weight(1f)) {
            Text("安全体检发现 $issues 处可以改进", style = JkType.bodyStrong, color = colors.textPrimary)
            Text("弱密码、重复密码与到期提醒", style = JkType.caption, color = colors.textTertiary)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
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
