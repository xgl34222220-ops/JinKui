package com.xiguli.jinkui.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.model.Attachment
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.CircularProgressIndicator
import com.xiguli.jinkui.data.model.copyText
import com.xiguli.jinkui.data.model.totpConfig
import com.xiguli.jinkui.core.crypto.Totp
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.HistoryEntry
import com.xiguli.jinkui.data.model.groupDigits
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.CardFace
import com.xiguli.jinkui.ui.components.ExpiryNote
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.components.SecureImage
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.sp
import com.xiguli.jinkui.ui.components.BankBrand
import com.xiguli.jinkui.ui.components.JkCopyButton
import com.xiguli.jinkui.ui.components.JkEmpty
import com.xiguli.jinkui.ui.components.rememberCopyFeedback
import com.xiguli.jinkui.ui.theme.JkSize

const val MASK = "••••••••"

fun formatTime(millis: Long): String =
    if (millis <= 0L) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))

class CardDetailViewModel(private val container: AppContainer, private val cardId: String) : ViewModel() {
    private val repository = container.repository

    val card: StateFlow<Card?> = repository.cards
        .map { cards -> cards.firstOrNull { it.id == cardId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.find(cardId))

    var history by mutableStateOf<List<HistoryEntry>?>(null)
        private set

    /** 读取历史失败（例如刚好被锁定）。与“没有记录”区分开，免得误以为旧密码丢了。 */
    var historyFailed by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { runCatching { repository.markUsed(cardId) } }
    }

    /** 复制一个字段；成功返回 true（页面据此给触感与对勾反馈）。 */
    fun copy(field: CardField): Boolean {
        val text = field.copyText()
        if (text == null) {
            container.messages.show("「${field.label}」的密钥无效，请到编辑页修正")
            return false
        }
        val seconds = container.clipboard.copy(field.label, text)
        container.messages.show("已复制「${field.label}」，$seconds 秒后清空剪贴板")
        return true
    }

    fun copyText(label: String, value: String) {
        val seconds = container.clipboard.copy(label, value)
        container.messages.show("已复制，$seconds 秒后清空剪贴板")
    }

    fun togglePin() {
        val current = card.value ?: return
        viewModelScope.launch { runCatching { repository.setPinned(current.id, !current.pinned) } }
    }

    fun loadHistory() {
        history = null
        historyFailed = false
        viewModelScope.launch {
            runCatching { repository.history(cardId) }
                .onSuccess { history = it }
                .onFailure {
                    historyFailed = true
                    history = emptyList()
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { repository.clearHistory(cardId) }
                .onSuccess {
                    history = emptyList()
                    container.messages.show("已清空历史密码")
                }
                .onFailure { container.messages.show("清空失败：${it.message.orEmpty()}") }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.delete(cardId) }
                .onSuccess {
                    container.messages.show("已删除")
                    onDeleted()
                }
                .onFailure { container.messages.show("删除失败：${it.message.orEmpty()}") }
        }
    }

    /** 复制一份：字段原样带过去，附件与历史不复制。 */
    fun duplicate() {
        val current = card.value ?: return
        viewModelScope.launch {
            runCatching {
                repository.save(
                    current.copy(
                        id = CardTemplates.newId(),
                        title = current.title + " 副本",
                        fields = current.fields.map { it.copy(id = CardTemplates.newId()) },
                        attachments = emptyList(),
                        pinned = false,
                    ),
                )
            }
                .onSuccess { container.messages.show("已复制为「${it.title}」") }
                .onFailure { container.messages.show("复制失败：${it.message.orEmpty()}") }
        }
    }

    suspend fun attachmentBytes(id: String): ByteArray = repository.readAttachment(id)
}

@Composable
fun CardDetailScreen(cardId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val container = LocalContainer.current
    val viewModel = jkViewModel { CardDetailViewModel(container, cardId) }
    val card by viewModel.card.collectAsStateWithLifecycle()
    val colors = Jk.colors

    val revealed = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }

    val current = card
    val copyFeedback = rememberCopyFeedback()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        JkTopBar(title = "", onBack = onBack) {
            if (current != null) {
                JkIconButton(
                    icon = Icons.Outlined.PushPin,
                    description = if (current.pinned) "取消置顶" else "置顶",
                    onClick = viewModel::togglePin,
                    tint = if (current.pinned) colors.accent else colors.textSecondary,
                )
                JkIconButton(Icons.Outlined.Edit, "编辑", onEdit)
                JkIconButton(Icons.Outlined.DeleteOutline, "删除", { confirmDelete = true })
            }
        }
        if (current == null) {
            JkEmpty(
                title = "这张卡片已不存在",
                message = "它可能刚被删除，或在别处合并了。",
                action = { JkButton("返回", onBack, style = ButtonStyle.Secondary, compact = true) },
            )
        } else {
            CardDetailContent(
                card = current,
                revealed = revealed,
                onToggleReveal = { id -> if (id in revealed) revealed.remove(id) else revealed.add(id) },
                onRevealAll = { ids, show ->
                    revealed.clear()
                    if (show) revealed.addAll(ids)
                },
                copiedKey = copyFeedback.copiedKey,
                onCopy = { field ->
                    if (viewModel.copy(field)) copyFeedback.fire(field.id)
                },
                onEdit = onEdit,
                onHistory = {
                    viewModel.loadHistory()
                    showHistory = true
                },
                onDuplicate = viewModel::duplicate,
                onViewAttachment = { viewing = it },
                loadAttachment = { viewModel.attachmentBytes(it) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (confirmDelete && current != null) {
        JkDialog(
            title = "删除「${current.title.ifBlank { "未命名" }}」？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
        ) {
            Text("卡片、附件照片和历史密码会一起删除，无法恢复。", style = JkType.body, color = colors.textSecondary)
        }
    }

    if (showHistory) {
        HistorySheet(
            entries = viewModel.history,
            failed = viewModel.historyFailed,
            onRetry = viewModel::loadHistory,
            onDismiss = { showHistory = false },
            onCopy = { viewModel.copyText(it.label, it.value) },
            onClear = viewModel::clearHistory,
        )
    }

    viewing?.let { attachment ->
        Dialog(
            onDismissRequest = { viewing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { viewing = null },
                contentAlignment = Alignment.Center,
            ) {
                SecureImage(
                    key = "full-" + attachment.id,
                    maxEdge = 2048,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    load = { viewModel.attachmentBytes(attachment.id) },
                )
            }
        }
    }
}

/**
 * 详情页主体（无状态）：卡面 → 标签与到期 → 两步验证 → 信息 → 备注 → 附件 → 更多 → 时间。
 * 不碰数据库与密钥，界面测试直接渲染它。[ticking] 为 false 时验证码不自动刷新（测试用）。
 */
@Composable
fun CardDetailContent(
    card: Card,
    revealed: List<String>,
    onToggleReveal: (String) -> Unit,
    onRevealAll: (List<String>, Boolean) -> Unit,
    copiedKey: String?,
    onCopy: (CardField) -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDuplicate: () -> Unit,
    onViewAttachment: (Attachment) -> Unit,
    loadAttachment: suspend (String) -> ByteArray,
    modifier: Modifier = Modifier,
    ticking: Boolean = true,
    clock: () -> Long = System::currentTimeMillis,
) {
    val colors = Jk.colors
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Space.x2_5)
            .padding(top = Space.x05, bottom = Space.x4),
    ) {
        val face = remember(card) { CardTemplates.face(card) }
        CardFace(
            title = card.title,
            type = card.type,
            colorIndex = card.color,
            hint = face.primary,
            secondaryLabel = face.secondaryLabel,
            secondary = face.secondary,
            pinned = card.pinned,
            brand = BankBrand.of(card),
            modifier = Modifier.fillMaxWidth(),
        )
        if (card.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
                verticalArrangement = Arrangement.spacedBy(Space.x1),
                modifier = Modifier.padding(start = Space.x05, top = Space.x1_5),
            ) {
                for (tag in card.tags) {
                    Text(
                        text = "#$tag",
                        style = JkType.caption,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clip(Radius.chip)
                            .background(colors.surfaceAlt)
                            .padding(horizontal = Space.x1, vertical = Space.x05),
                    )
                }
            }
        }
        ExpiryNote(card, Modifier.padding(start = Space.x05, top = Space.x1_5))

        // 按该类型模板的重要程度排序展示；保存的顺序不变。
        val visibleFields = remember(card) { CardTemplates.displayOrder(card).filter { it.value.isNotBlank() } }
        val totpFields = visibleFields.filter { it.kind == FieldKind.TOTP }
        val infoFields = visibleFields.filter { it.kind != FieldKind.TOTP }

        if (totpFields.isNotEmpty()) {
            DetailSection("两步验证")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                totpFields.forEachIndexed { index, field ->
                    if (index > 0) JkDivider(inset = Space.x2)
                    TotpRow(field, copied = copiedKey == field.id, onCopy = { onCopy(field) }, ticking = ticking, clock = clock)
                }
            }
        }

        // 两步验证字段只显示验证码，没有“显示密钥”这一说
        val sensitiveIds = infoFields.filter { it.sensitive }.map { it.id }
        DetailSection("信息") {
            if (sensitiveIds.isNotEmpty()) {
                val allShown = revealed.containsAll(sensitiveIds)
                JkButton(
                    text = if (allShown) "全部隐藏" else "全部显示",
                    icon = if (allShown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    style = ButtonStyle.Ghost,
                    compact = true,
                    onClick = { onRevealAll(sensitiveIds, !allShown) },
                )
            }
        }
        if (infoFields.isEmpty()) {
            JkCard(Modifier.fillMaxWidth()) {
                Text(if (totpFields.isEmpty()) "这张卡片还是空的" else "还没有其他信息", style = JkType.bodyStrong, color = colors.textPrimary)
                Spacer(Modifier.height(Space.x05))
                Text("填上卡号、账号、密码这些信息后，就能在这里一键复制。", style = JkType.small, color = colors.textTertiary)
                Spacer(Modifier.height(Space.x2))
                JkButton("去填写", onEdit, style = ButtonStyle.Secondary, compact = true, icon = Icons.Outlined.Edit)
            }
        } else {
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                infoFields.forEachIndexed { index, field ->
                    if (index > 0) JkDivider(inset = Space.x2)
                    FieldRow(
                        field = field,
                        emphasis = index == 0,
                        shown = !field.sensitive || field.id in revealed,
                        copied = copiedKey == field.id,
                        onToggle = { onToggleReveal(field.id) },
                        onCopy = { onCopy(field) },
                    )
                }
            }
        }

        if (card.note.isNotBlank()) {
            DetailSection("备注")
            JkCard(Modifier.fillMaxWidth()) {
                Text(card.note, style = JkType.body, color = colors.textSecondary)
            }
        }

        if (card.attachments.isNotEmpty()) {
            DetailSection("附件 ${card.attachments.size}")
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                card.attachments.forEachIndexed { i, attachment ->
                    SecureImage(
                        key = attachment.id,
                        maxEdge = 320,
                        modifier = Modifier
                            .size(width = 128.dp, height = 88.dp)
                            .clip(Radius.tile)
                            .border(1.dp, colors.border, Radius.tile)
                            .clickable(onClickLabel = "查看第 ${i + 1} 张照片") { onViewAttachment(attachment) },
                        load = { loadAttachment(attachment.id) },
                    )
                }
            }
        }

        DetailSection("更多")
        JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
            JkRow(
                title = "历史密码",
                subtitle = "敏感字段被修改前的旧值",
                icon = Icons.Outlined.History,
                onClick = onHistory,
            )
            JkDivider(inset = Space.x6 + Space.x05)
            JkRow(
                title = "复制一份",
                subtitle = "以这张卡片为模板新建，附件不会带过去",
                icon = Icons.Outlined.CopyAll,
                onClick = onDuplicate,
                trailing = {},
            )
        }

        Spacer(Modifier.height(Space.x2))
        Text(
            text = "创建于 ${formatTime(card.createdAt)}　更新于 ${formatTime(card.updatedAt)}",
            style = JkType.caption.copy(fontFeatureSettings = "tnum"),
            color = colors.textTertiary,
            modifier = Modifier.padding(start = Space.x05),
        )
    }
}

/** 小节标题：与设置页同一种眉题，右侧可放一个操作；没有操作时也保持 48dp 高，节奏一致。 */
@Composable
private fun DetailSection(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.x1_5)
            .heightIn(min = JkSize.touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = JkType.overline,
            color = Jk.colors.textTertiary,
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.x05)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

/**
 * 两步验证码：每秒刷新，验证码换新时从下往上滚入；右侧圆环平滑倒计时，最后 5 秒转为警示色。
 * 点整行或复制按钮复制验证码（经 ClipboardGuard，按设置自动清空）。密钥本身从不显示在这里。
 */
@Composable
private fun TotpRow(field: CardField, copied: Boolean, onCopy: () -> Unit, ticking: Boolean, clock: () -> Long) {
    val colors = Jk.colors
    val config = remember(field.value) { field.totpConfig() }
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(config, ticking) {
        while (ticking && config != null) {
            now = clock()
            delay(1_000L - now % 1_000L)
        }
    }
    val code = remember(config, now / 1_000L) { config?.let { Totp.code(it, now) } }
    val label = field.label.ifBlank { "两步验证码" }
    val urgent = code != null && code.secondsLeft <= 5
    val tint by animateColorAsState(if (urgent) colors.danger else colors.accent, tween(240), label = "totpTint")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = code != null, onClickLabel = "复制验证码", onClick = onCopy)
            .semantics(mergeDescendants = true) {
                if (code != null) contentDescription = "$label ${code.code.toList().joinToString(" ")}，还剩 ${code.secondsLeft} 秒"
            }
            .heightIn(min = 72.dp)
            .padding(start = Space.x2, end = Space.x05, top = Space.x1_5, bottom = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val subtitle = config?.let { c -> listOf(c.issuer, c.account).filter { it.isNotBlank() }.joinToString(" · ") }.orEmpty()
            Text(if (subtitle.isEmpty()) label else "$label · $subtitle", style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.x05))
            if (code == null) {
                Text("密钥无效，请到编辑页修正", style = JkType.body, color = colors.danger)
            } else {
                AnimatedContent(
                    targetState = code.code,
                    transitionSpec = {
                        (slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(140)))
                    },
                    label = "totpCode",
                ) { value ->
                    Text(
                        groupOtp(value),
                        style = JkType.digitsLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
                        color = if (urgent) colors.danger else colors.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (code != null) {
            TotpRing(secondsLeft = code.secondsLeft, period = code.period, tint = tint, animate = ticking)
        }
        JkCopyButton(copied, "复制验证码", onCopy, enabled = code != null)
    }
}

/** 验证码每 3 位（8 位时每 4 位）一组。 */
internal fun groupOtp(code: String): String =
    if (code.length == 8) code.chunked(4).joinToString(" ") else code.chunked(3).joinToString(" ")

/** 倒计时圆环：圆头弧线平滑走完一秒，换周期时直接回满，不倒着转一圈。 */
@Composable
private fun TotpRing(secondsLeft: Int, period: Int, tint: Color, animate: Boolean) {
    val colors = Jk.colors
    val target = (secondsLeft - 1).coerceAtLeast(0) / period.toFloat()
    val start = secondsLeft / period.toFloat()
    val sweep = remember { Animatable(start) }
    LaunchedEffect(secondsLeft, period, animate) {
        if (!animate) {
            sweep.snapTo(start)
            return@LaunchedEffect
        }
        // 每秒从“当前剩余”匀速走到“下一秒的剩余”
        sweep.snapTo(start)
        sweep.animateTo(target, tween(durationMillis = 1_000, easing = LinearEasing))
    }
    val track = colors.surfaceAlt
    Box(Modifier.size(JkSize.touch), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(30.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(tint, -90f, 360f * sweep.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$secondsLeft", style = JkType.amountCaption, color = colors.textSecondary)
    }
}

/** 一行字段：点整行即复制；敏感字段默认打码，点眼睛才显示（明文淡入、轻轻上移）。 */
@Composable
private fun FieldRow(
    field: CardField,
    shown: Boolean,
    copied: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    emphasis: Boolean = false,
) {
    val colors = Jk.colors
    val numeric = field.kind == FieldKind.NUMBER || field.kind == FieldKind.PHONE || field.kind == FieldKind.DATE
    val display = if (field.kind == FieldKind.NUMBER) groupDigits(field.value) else field.value
    val base = when {
        field.kind == FieldKind.PASSWORD || (field.sensitive && !numeric) -> JkType.mono
        numeric -> JkType.digits
        else -> JkType.body
    }
    // 第一行是这张卡片最要紧的信息，字号放大一级。
    val style = if (emphasis) base.copy(fontSize = JkType.title.fontSize, lineHeight = JkType.title.lineHeight) else base
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "复制${field.label}", onClick = onCopy)
            .heightIn(min = 64.dp)
            .padding(start = Space.x2, end = Space.x05, top = Space.x1_5, bottom = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(field.label.ifBlank { "未命名" }, style = JkType.caption, color = colors.textTertiary)
                if (field.sensitive) {
                    Spacer(Modifier.width(Space.x05))
                    Icon(Icons.Outlined.Lock, contentDescription = "敏感", tint = colors.textTertiary, modifier = Modifier.size(11.dp))
                }
            }
            Spacer(Modifier.height(Space.x05))
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 }) togetherWith fadeOut(tween(120))
                },
                label = "reveal",
            ) { visible ->
                Text(
                    text = if (visible) display else MASK,
                    style = if (visible) style else style.copy(letterSpacing = 2.sp),
                    color = if (visible) colors.textPrimary else colors.textSecondary,
                    modifier = if (visible) Modifier else Modifier.semantics { contentDescription = "${field.label}，已隐藏" },
                )
            }
        }
        if (field.sensitive) {
            JkIconButton(
                icon = if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                description = if (shown) "隐藏${field.label}" else "显示${field.label}",
                onClick = onToggle,
            )
        }
        JkCopyButton(copied, "复制${field.label}", onCopy)
    }
}

@Composable
private fun HistorySheet(
    entries: List<HistoryEntry>?,
    failed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    val colors = Jk.colors
    val revealed = remember { mutableStateListOf<String>() }
    var confirmClear by remember { mutableStateOf(false) }
    JkSheet(onDismiss = onDismiss, title = "历史密码") {
        when {
            entries == null -> Text("正在读取…", style = JkType.small, color = colors.textTertiary)
            failed -> {
                Text("历史记录读取失败。", style = JkType.small, color = colors.danger)
                Spacer(Modifier.height(Space.x1_5))
                JkButton("重试", onRetry, style = ButtonStyle.Secondary, compact = true)
            }
            entries.isEmpty() -> Text(
                "还没有记录。敏感字段被修改时，旧值会保存在这里。可以在设置里关闭这项记录。",
                style = JkType.small,
                color = colors.textTertiary,
            )
            else -> {
                Column(
                    modifier = Modifier
                        .height(320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (entry in entries) {
                        val shown = entry.id in revealed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.x1),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${entry.label}　${formatTime(entry.changedAt)}",
                                    style = JkType.caption,
                                    color = colors.textTertiary,
                                )
                                Text(if (shown) entry.value else MASK, style = JkType.mono, color = colors.textPrimary)
                            }
                            JkIconButton(
                                icon = if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                description = if (shown) "隐藏" else "显示",
                                onClick = { if (shown) revealed.remove(entry.id) else revealed.add(entry.id) },
                            )
                            JkIconButton(Icons.Outlined.ContentCopy, "复制", { onCopy(entry) })
                        }
                        JkDivider()
                    }
                }
                Spacer(Modifier.height(Space.x2))
                JkButton("清空这张卡片的历史", { confirmClear = true }, style = ButtonStyle.Danger, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirmClear) {
        JkDialog(
            title = "清空历史密码？",
            onDismiss = { confirmClear = false },
            confirmText = "清空",
            danger = true,
            onConfirm = {
                confirmClear = false
                onClear()
            },
        ) {
            Text("这张卡片保存的 ${entries?.size ?: 0} 条旧值会被永久删除，无法恢复。", style = JkType.body, color = colors.textSecondary)
        }
    }
}
