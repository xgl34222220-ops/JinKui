package com.xiguli.jinkui.ui.card

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.model.Attachment
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardPalette
import com.xiguli.jinkui.data.model.CardTemplates
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import androidx.compose.material.icons.outlined.Timer
import com.xiguli.jinkui.core.crypto.Totp
import com.xiguli.jinkui.data.model.PendingAttachment
import com.xiguli.jinkui.data.repo.ImageImporter
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.CardFace
import com.xiguli.jinkui.ui.components.JkBareField
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.components.SecureImage
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.cardTheme
import com.xiguli.jinkui.ui.components.icon
import com.xiguli.jinkui.ui.generator.GeneratorSheet
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import kotlinx.coroutines.launch

/** 编辑中的字段。值保存在 Compose 状态里，随页面的 ViewModel 一起在离开或锁定时销毁。 */
class DraftField(val id: String, label: String, value: String, sensitive: Boolean, val kind: FieldKind) {
    var label by mutableStateOf(label)
    var value by mutableStateOf(value)
    var sensitive by mutableStateOf(sensitive)
}

class CardEditViewModel(
    private val container: AppContainer,
    cardId: String?,
    newType: CardType?,
) : ViewModel() {
    private val original: Card? = cardId?.let { container.repository.find(it) }
    private val id: String = original?.id ?: CardTemplates.newId()

    val isNew: Boolean = original == null
    val type: CardType = original?.type ?: newType ?: CardType.CUSTOM

    var title by mutableStateOf(original?.title.orEmpty())
    var color by mutableIntStateOf(original?.color ?: CardPalette.AUTO)
    var note by mutableStateOf(original?.note.orEmpty())
    val tags = mutableStateListOf<String>().apply { addAll(original?.tags.orEmpty()) }
    val fields = mutableStateListOf<DraftField>().apply {
        val source = original?.fields ?: CardTemplates.fieldsFor(type)
        addAll(source.map { DraftField(it.id, it.label, it.value, it.sensitive, it.kind) })
    }
    val existingAttachments = mutableStateListOf<Attachment>().apply { addAll(original?.attachments.orEmpty()) }
    val pendingAttachments = mutableStateListOf<PendingAttachment>()
    private val removedAttachmentIds = ArrayList<String>()

    var saving by mutableStateOf(false)
        private set
    var titleError by mutableStateOf(false)

    /** 打开编辑页那一刻的内容，用来判断“有没有改过”。必须在上面的状态都初始化之后再取。 */
    private val baseline: Card = build()

    /** 已有标签中还没选上的，作为输入建议。 */
    val suggestions: List<String>
        get() = container.repository.cards.value.flatMap { it.tags }.distinct().sorted().filterNot { it in tags }.take(8)

    fun addTag(raw: String) {
        raw.split(',', '，', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            if (it !in tags) tags.add(it)
        }
    }

    fun addField(kind: FieldKind) {
        val label = if (kind == FieldKind.TOTP) "两步验证码" else ""
        fields.add(DraftField(CardTemplates.newId(), label, "", sensitive = kind == FieldKind.PASSWORD || kind == FieldKind.TOTP, kind = kind))
    }

    /** 这张卡片比它所属类型的模板少了几个字段（按字段名比对）。 */
    val missingTemplateCount: Int
        get() = CardTemplates.missingFields(type, fields.map { CardField(it.id, it.label, it.value, it.sensitive, it.kind) }).size

    /** 把模板里缺的字段补到末尾；已有字段和内容不动。 */
    fun addMissingTemplateFields() {
        val missing = CardTemplates.missingFields(type, fields.map { CardField(it.id, it.label, it.value, it.sensitive, it.kind) })
        missing.forEach { fields.add(DraftField(it.id, it.label, "", it.sensitive, it.kind)) }
    }

    fun removeExisting(attachment: Attachment) {
        existingAttachments.remove(attachment)
        removedAttachmentIds.add(attachment.id)
    }

    suspend fun attachmentBytes(id: String): ByteArray = container.repository.readAttachment(id)

    fun hasChanges(): Boolean =
        pendingAttachments.isNotEmpty() || removedAttachmentIds.isNotEmpty() || build() != baseline

    private fun build(): Card = Card(
        id = id,
        type = type,
        title = title.trim(),
        tags = tags.toList(),
        color = color,
        fields = fields
            .filter { it.label.isNotBlank() || it.value.isNotBlank() }
            // 两步验证密钥永远是敏感字段
            .map { CardField(it.id, it.label.trim().ifEmpty { "未命名" }, it.value, it.sensitive || it.kind == FieldKind.TOTP, it.kind) },
        note = note,
        attachments = existingAttachments.toList(),
        pinned = original?.pinned ?: false,
        createdAt = original?.createdAt ?: 0L,
        updatedAt = original?.updatedAt ?: 0L,
        lastUsedAt = original?.lastUsedAt ?: 0L,
    )

    fun save(onSaved: () -> Unit) {
        if (saving) return
        if (title.isBlank()) {
            titleError = true
            return
        }
        // 两步验证：先校验，再统一存成规范的 otpauth URI（粘贴的纯 Base32 密钥也会转成 URI）
        for (field in fields) {
            if (field.kind != FieldKind.TOTP || field.value.isBlank()) continue
            val config = try {
                Totp.parse(field.value)
            } catch (e: IllegalArgumentException) {
                container.messages.show("「${field.label.ifBlank { "两步验证码" }}」：${e.message ?: "密钥无效"}")
                return
            }
            field.value = config.toUri()
        }
        saving = true
        viewModelScope.launch {
            try {
                container.repository.save(build(), pendingAttachments.toList(), removedAttachmentIds.toList())
                container.messages.show(if (isNew) "已保存到金匮" else "已更新")
                onSaved()
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message.orEmpty()}")
            } finally {
                saving = false
            }
        }
    }
}

@Composable
fun CardEditScreen(cardId: String?, newType: CardType?, onClose: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = jkViewModel { CardEditViewModel(container, cardId, newType) }
    val colors = Jk.colors

    var confirmDiscard by remember { mutableStateOf(false) }
    var generatorFor by remember { mutableStateOf<DraftField?>(null) }
    var tagInput by remember { mutableStateOf("") }
    var pickKind by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { ImageImporter.load(context, uri) }
                    .onSuccess { viewModel.pendingAttachments.add(it) }
                    .onFailure { container.messages.show(it.message ?: "无法读取这张图片") }
            }
        }
    }

    val requestClose: () -> Unit = {
        if (viewModel.hasChanges()) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(onBack = requestClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        JkTopBar(
            title = if (viewModel.isNew) "新建${viewModel.type.label}" else "编辑卡片",
            onBack = requestClose,
        ) {
            JkButton(
                text = "保存",
                compact = true,
                loading = viewModel.saving,
                onClick = { viewModel.save(onClose) },
                modifier = Modifier.padding(end = Space.x1),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2_5)
                .padding(top = Space.x05, bottom = Space.x5),
        ) {
            // 实时预览：名称和颜色一改，卡面跟着变。
            CardFace(
                title = viewModel.title.ifBlank { viewModel.type.label },
                type = viewModel.type,
                colorIndex = viewModel.color,
                hint = "预览",
                aspect = 2.4f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.x2_5),
            )
            JkTextField(
                value = viewModel.title,
                onValueChange = {
                    viewModel.title = it
                    viewModel.titleError = false
                },
                label = "名称",
                placeholder = "例如：招商银行储蓄卡",
                imeAction = ImeAction.Next,
                error = if (viewModel.titleError) "请填写名称" else null,
            )

            JkSectionLabel("卡面")
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.x1_5),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                // 第一个是该类型自己的默认卡面，其后是可选的纯色。
                for (index in listOf(CardPalette.AUTO) + CardPalette.colors.indices) {
                    val selected = index == viewModel.color
                    val theme = cardTheme(viewModel.type, index)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (selected) colors.textPrimary else colors.bg, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(theme.start, theme.end)))
                            .clickable { viewModel.color = index },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == CardPalette.AUTO) {
                            Icon(viewModel.type.icon, contentDescription = "默认卡面", tint = theme.accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            JkSectionLabel("标签")
            if (viewModel.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    verticalArrangement = Arrangement.spacedBy(Space.x1),
                    modifier = Modifier.padding(bottom = Space.x1),
                ) {
                    for (tag in viewModel.tags.toList()) {
                        JkChip("$tag  ×", selected = true, onClick = { viewModel.tags.remove(tag) })
                    }
                }
            }
            JkTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                placeholder = "输入标签后按回车，可用来分组",
                imeAction = ImeAction.Done,
                onImeAction = {
                    viewModel.addTag(tagInput)
                    tagInput = ""
                },
            )
            val suggestions = viewModel.suggestions
            if (suggestions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.x1),
                    verticalArrangement = Arrangement.spacedBy(Space.x1),
                    modifier = Modifier.padding(top = Space.x1),
                ) {
                    for (tag in suggestions) {
                        JkChip(tag, selected = false, onClick = { viewModel.addTag(tag) })
                    }
                }
            }

            JkSectionLabel("字段")
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                for (field in viewModel.fields.toList()) {
                    FieldEditor(
                        field = field,
                        hint = CardTemplates.hintFor(viewModel.type, field.label),
                        onRemove = { viewModel.fields.remove(field) },
                        onGenerate = { generatorFor = field },
                    )
                }
            }
            Spacer(Modifier.height(Space.x1))
            JkButton(
                text = "添加字段",
                icon = Icons.Outlined.Add,
                style = ButtonStyle.Secondary,
                onClick = { pickKind = true },
                modifier = Modifier.fillMaxWidth(),
            )
            val missing = viewModel.missingTemplateCount
            if (!viewModel.isNew && missing > 0) {
                Spacer(Modifier.height(Space.x05))
                JkButton(
                    text = "补上「${viewModel.type.label}」模板里缺的 $missing 个字段",
                    style = ButtonStyle.Ghost,
                    compact = true,
                    onClick = viewModel::addMissingTemplateFields,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            JkSectionLabel("备注")
            JkTextField(
                value = viewModel.note,
                onValueChange = { viewModel.note = it },
                placeholder = "可以写点提醒自己的话，支持搜索",
                singleLine = false,
                minLines = 3,
            )

            JkSectionLabel("附件")
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                for (attachment in viewModel.existingAttachments.toList()) {
                    AttachmentThumb(
                        key = attachment.id,
                        onRemove = { viewModel.removeExisting(attachment) },
                        load = { viewModel.attachmentBytes(attachment.id) },
                    )
                }
                for (pending in viewModel.pendingAttachments.toList()) {
                    AttachmentThumb(
                        key = pending.id,
                        onRemove = { viewModel.pendingAttachments.remove(pending) },
                        load = { pending.bytes },
                    )
                }
                Column(
                    modifier = Modifier
                        .size(width = 128.dp, height = 88.dp)
                        .clip(Radius.tile)
                        .border(1.dp, colors.border, Radius.tile)
                        .clickable {
                            // 去系统选图期间暂不因切后台而锁定，否则回来时草稿已被清掉。
                            container.lock.holdForExternalActivity()
                            picker.launch("image/*")
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = colors.textSecondary)
                    Spacer(Modifier.height(Space.x05))
                    Text("添加照片", style = JkType.caption, color = colors.textSecondary)
                }
            }
            Text(
                "照片会去掉拍摄信息后加密保存在本机，不会出现在系统相册里。",
                style = JkType.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.x05, top = Space.x1),
            )
        }
    }

    if (confirmDiscard) {
        JkDialog(
            title = "放弃修改？",
            onDismiss = { confirmDiscard = false },
            confirmText = "放弃",
            dismissText = "继续编辑",
            danger = true,
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
        ) {
            Text("尚未保存的内容会丢失。", style = JkType.body, color = colors.textSecondary)
        }
    }

    if (pickKind) {
        JkSheet(onDismiss = { pickKind = false }, title = "添加字段") {
            val kinds = listOf(
                Triple(FieldKind.TEXT, "文本", Icons.Outlined.Notes),
                Triple(FieldKind.PASSWORD, "密码（默认打码，可一键生成）", Icons.Outlined.Password),
                Triple(FieldKind.NUMBER, "数字（卡号、编号）", Icons.Outlined.Numbers),
                Triple(FieldKind.PHONE, "电话", Icons.Outlined.Phone),
                Triple(FieldKind.DATE, "日期", Icons.Outlined.CalendarMonth),
                Triple(FieldKind.URL, "网址", Icons.Outlined.Link),
                Triple(FieldKind.TOTP, "两步验证码（粘贴 otpauth:// 链接或密钥）", Icons.Outlined.Timer),
            ).let { list ->
                // 登录密码卡片最常用两步验证，放到最前面
                if (viewModel.type == CardType.LOGIN) list.sortedByDescending { it.first == FieldKind.TOTP } else list
            }
            for ((kind, label, icon) in kinds) {
                JkRow(
                    title = label,
                    icon = icon,
                    trailing = {},
                    modifier = Modifier.clip(Radius.control),
                    onClick = {
                        viewModel.addField(kind)
                        pickKind = false
                    },
                )
            }
        }
    }

    generatorFor?.let { target ->
        GeneratorSheet(
            onDismiss = { generatorFor = null },
            onUse = { target.value = it },
        )
    }
}

/**
 * 一个字段块：上行是字段名，下行是内容，中间一根分隔线。
 * 右上角的小锁切换“敏感”——锁上后内容默认打码。
 */
@Composable
private fun FieldEditor(field: DraftField, hint: String, onRemove: () -> Unit, onGenerate: () -> Unit) {
    val colors = Jk.colors
    var revealed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.field)
            .background(colors.surface)
            .border(1.dp, colors.border, Radius.field),
    ) {
        Row(
            modifier = Modifier.padding(start = Space.x2, end = Space.x05),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JkBareField(
                value = field.label,
                onValueChange = { field.label = it },
                placeholder = "字段名",
                textStyle = JkType.small,
                textColor = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (field.kind != FieldKind.TOTP) {
                JkIconButton(
                    icon = if (field.sensitive) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    description = if (field.sensitive) "敏感字段，点按改为普通" else "普通字段，点按改为敏感",
                    onClick = { field.sensitive = !field.sensitive },
                    tint = if (field.sensitive) colors.accent else colors.textTertiary,
                )
            }
            JkIconButton(Icons.Outlined.Close, "删除字段", onRemove, tint = colors.textTertiary)
        }
        JkDivider()
        Row(
            modifier = Modifier.padding(start = Space.x2, end = Space.x05),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JkBareField(
                value = field.value,
                onValueChange = { field.value = it },
                placeholder = when {
                    field.kind == FieldKind.TOTP -> "粘贴 otpauth://totp/… 链接或 Base32 密钥"
                    else -> hint.ifBlank { if (field.sensitive) "内容（保存后默认打码）" else "内容" }
                },
                textStyle = if (field.sensitive || field.kind == FieldKind.NUMBER) JkType.mono else JkType.body,
                hidden = field.sensitive && !revealed,
                keyboardType = when (field.kind) {
                    FieldKind.NUMBER -> if (field.sensitive) KeyboardType.NumberPassword else KeyboardType.Number
                    FieldKind.URL -> KeyboardType.Uri
                    FieldKind.PHONE -> KeyboardType.Phone
                    FieldKind.PASSWORD, FieldKind.TOTP -> KeyboardType.Password
                    else -> if (field.sensitive) KeyboardType.Password else KeyboardType.Text
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Space.x1_5),
            )
            if (field.kind == FieldKind.PASSWORD) {
                JkIconButton(Icons.Outlined.AutoFixHigh, "生成密码", onGenerate)
            }
            if (field.sensitive) {
                JkIconButton(
                    icon = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    description = if (revealed) "隐藏" else "显示",
                    onClick = { revealed = !revealed },
                )
            } else {
                Spacer(Modifier.width(Space.x1_5))
            }
        }
        if (field.kind == FieldKind.TOTP) {
            val error = remember(field.value) { Totp.errorOf(field.value) }
            Text(
                text = error ?: "支持 SHA1 / SHA256 / SHA512、6 或 8 位、30 或 60 秒。密钥只存在本机，加密保存。",
                style = JkType.caption,
                color = if (error != null) colors.danger else colors.textTertiary,
                modifier = Modifier.padding(start = Space.x2, end = Space.x2, bottom = Space.x1_5),
            )
        }
    }
}

@Composable
private fun AttachmentThumb(key: String, onRemove: () -> Unit, load: suspend () -> ByteArray) {
    Box(Modifier.size(width = 128.dp, height = 88.dp)) {
        SecureImage(
            key = key,
            maxEdge = 320,
            modifier = Modifier
                .fillMaxSize()
                .clip(Radius.tile),
            load = load,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.x05)
                .size(24.dp)
                .clip(CircleShape)
                .background(Jk.colors.primary)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "移除照片", tint = Jk.colors.onPrimary, modifier = Modifier.size(14.dp))
        }
    }
}
