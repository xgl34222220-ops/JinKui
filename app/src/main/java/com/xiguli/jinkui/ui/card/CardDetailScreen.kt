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
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.HistoryEntry
import com.xiguli.jinkui.data.model.groupDigits
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.CardFace
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

    init {
        viewModelScope.launch { runCatching { repository.markUsed(cardId) } }
    }

    fun copy(field: CardField) {
        val seconds = container.clipboard.copy(field.label, field.value)
        container.messages.show("已复制「${field.label}」，$seconds 秒后清空剪贴板")
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
        viewModelScope.launch {
            history = runCatching { repository.history(cardId) }.getOrDefault(emptyList())
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { repository.clearHistory(cardId) }
            history = emptyList()
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
            Text(
                "这张卡片已不存在",
                style = JkType.body,
                color = colors.textTertiary,
                modifier = Modifier.padding(Space.x3),
            )
        } else {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2_5)
                .padding(top = Space.x05, bottom = Space.x4),
        ) {
            CardFace(
                title = current.title,
                type = current.type,
                colorIndex = current.color,
                hint = current.faceHint,
                pinned = current.pinned,
                modifier = Modifier.fillMaxWidth(),
            )
            if (current.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    verticalArrangement = Arrangement.spacedBy(Space.x1),
                    modifier = Modifier.padding(start = Space.x05, top = Space.x1_5),
                ) {
                    for (tag in current.tags) {
                        Text(
                            text = tag,
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

            Spacer(Modifier.height(Space.x2))
            val visibleFields = current.fields.filter { it.value.isNotBlank() }
            if (visibleFields.isEmpty()) {
                JkCard(Modifier.fillMaxWidth()) {
                    Text("还没有填写任何字段", style = JkType.small, color = colors.textTertiary)
                }
            } else {
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    visibleFields.forEachIndexed { index, field ->
                        if (index > 0) JkDivider(inset = Space.x2)
                        FieldRow(
                            field = field,
                            shown = !field.sensitive || field.id in revealed,
                            onToggle = {
                                if (field.id in revealed) revealed.remove(field.id) else revealed.add(field.id)
                            },
                            onCopy = { viewModel.copy(field) },
                        )
                    }
                }
            }

            if (current.note.isNotBlank()) {
                Spacer(Modifier.height(Space.x1_5))
                JkCard(Modifier.fillMaxWidth()) {
                    Text("备注", style = JkType.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(Space.x05))
                    Text(current.note, style = JkType.body, color = colors.textSecondary)
                }
            }

            if (current.attachments.isNotEmpty()) {
                Spacer(Modifier.height(Space.x1_5))
                JkCard(Modifier.fillMaxWidth()) {
                    Text("附件 ${current.attachments.size}", style = JkType.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(Space.x1))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.x1),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        for (attachment in current.attachments) {
                            SecureImage(
                                key = attachment.id,
                                maxEdge = 320,
                                modifier = Modifier
                                    .size(width = 128.dp, height = 88.dp)
                                    .clip(Radius.tile)
                                    .clickable { viewing = attachment },
                                load = { viewModel.attachmentBytes(attachment.id) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.x1_5))
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "历史密码",
                    subtitle = "敏感字段被修改前的旧值",
                    icon = Icons.Outlined.History,
                    onClick = {
                        viewModel.loadHistory()
                        showHistory = true
                    },
                )
                JkDivider(inset = Space.x6 + Space.x05)
                JkRow(
                    title = "复制一份",
                    subtitle = "以这张卡片为模板新建，附件不会带过去",
                    icon = Icons.Outlined.CopyAll,
                    onClick = viewModel::duplicate,
                    trailing = {},
                )
            }

            Spacer(Modifier.height(Space.x2))
            Text(
                text = "创建于 ${formatTime(current.createdAt)}　更新于 ${formatTime(current.updatedAt)}",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05),
            )
        }
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

/** 一行字段：点整行即复制；敏感字段默认打码，点眼睛才显示。 */
@Composable
private fun FieldRow(field: CardField, shown: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
    val colors = Jk.colors
    val monospace = field.sensitive || field.kind == FieldKind.NUMBER || field.kind == FieldKind.PASSWORD
    val display = if (field.kind == FieldKind.NUMBER) groupDigits(field.value) else field.value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "复制${field.label}", onClick = onCopy)
            .padding(start = Space.x2, end = Space.x05, top = Space.x1_5, bottom = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(field.label.ifBlank { "未命名" }, style = JkType.caption, color = colors.textTertiary)
            Spacer(Modifier.height(Space.x05))
            Text(
                text = if (shown) display else MASK,
                style = if (monospace) JkType.mono else JkType.body,
                color = if (shown) colors.textPrimary else colors.textSecondary,
            )
        }
        if (field.sensitive) {
            JkIconButton(
                icon = if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                description = if (shown) "隐藏" else "显示",
                onClick = onToggle,
            )
        }
        JkIconButton(Icons.Outlined.ContentCopy, "复制${field.label}", onCopy, tint = colors.textTertiary)
    }
}

@Composable
private fun HistorySheet(
    entries: List<HistoryEntry>?,
    onDismiss: () -> Unit,
    onCopy: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    val colors = Jk.colors
    val revealed = remember { mutableStateListOf<String>() }
    JkSheet(onDismiss = onDismiss, title = "历史密码") {
        when {
            entries == null -> Text("正在读取…", style = JkType.small, color = colors.textTertiary)
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
                JkButton("清空这张卡片的历史", onClear, style = ButtonStyle.Danger, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
