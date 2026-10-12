package com.xiguli.jinkui.ui.card

import com.xiguli.jinkui.ui.theme.jkSurface

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
import com.xiguli.jinkui.data.repo.QrImageReader
import com.xiguli.jinkui.core.qr.OtpQrResult
import com.xiguli.jinkui.core.qr.OtpQr
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.xiguli.jinkui.core.qr.MigrationBatch
import com.xiguli.jinkui.data.model.FieldCheck
import com.xiguli.jinkui.ui.components.BankBrand
import com.xiguli.jinkui.ui.components.confirmHaptic
import com.xiguli.jinkui.ui.theme.JkSize

/** 编辑中的字段。值保存在 Compose 状态里，随页面的 ViewModel 一起在离开或锁定时销毁。 */
class DraftField(val id: String, label: String, value: String, sensitive: Boolean, val kind: FieldKind) {
    var label by mutableStateOf(label)
    var value by mutableStateOf(value)
    var sensitive by mutableStateOf(sensitive)
}

/**
 * 一张卡片的编辑草稿：只有 Compose 状态，不碰仓库与密钥。
 * ViewModel 持有它并负责保存；界面测试可以直接构造一份草稿渲染表单。
 */
class CardDraft(private val original: Card?, val type: CardType) {
    val id: String = original?.id ?: CardTemplates.newId()

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
    val removedAttachmentIds = ArrayList<String>()
    var titleError by mutableStateOf<String?>(null)

    fun addTag(raw: String) {
        raw.split(',', '，', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            if (it !in tags) tags.add(it)
        }
    }

    fun addField(kind: FieldKind) {
        val label = if (kind == FieldKind.TOTP) "两步验证码" else ""
        fields.add(DraftField(CardTemplates.newId(), label, "", sensitive = kind == FieldKind.PASSWORD || kind == FieldKind.TOTP, kind = kind))
    }

    private fun currentFields() = fields.map { CardField(it.id, it.label, it.value, it.sensitive, it.kind) }

    /** 这张卡片比它所属类型的模板少了几个字段（按字段名比对）。 */
    val missingTemplateCount: Int
        get() = CardTemplates.missingFields(type, currentFields()).size

    /** 把模板里缺的字段补到末尾；已有字段和内容不动。 */
    fun addMissingTemplateFields() {
        CardTemplates.missingFields(type, currentFields()).forEach { fields.add(DraftField(it.id, it.label, "", it.sensitive, it.kind)) }
    }

    fun removeExisting(attachment: Attachment) {
        existingAttachments.remove(attachment)
        removedAttachmentIds.add(attachment.id)
    }

    /** 预览卡面用的银行品牌色（名称或“开户行”里认出的银行）。 */
    val brand: androidx.compose.ui.graphics.Color?
        get() = BankBrand.of(type, color, title, fields.firstOrNull { it.label.trim() == "开户行" }?.value.orEmpty())

    fun build(): Card = Card(
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
}

class CardEditViewModel(
    private val container: AppContainer,
    cardId: String?,
    newType: CardType?,
) : ViewModel() {
    private val original: Card? = cardId?.let { container.repository.find(it) }

    val isNew: Boolean = original == null
    val type: CardType = original?.type ?: newType ?: CardType.CUSTOM
    val draft = CardDraft(original, type)

    var saving by mutableStateOf(false)
        private set

    /** 打开编辑页那一刻的内容，用来判断“有没有改过”。 */
    private val baseline: Card = draft.build()

    /** 已有标签中还没选上的，作为输入建议。 */
    val suggestions: List<String>
        get() = container.repository.cards.value.flatMap { it.tags }.distinct().sorted().filterNot { it in draft.tags }.take(8)

    suspend fun attachmentBytes(id: String): ByteArray = container.repository.readAttachment(id)

    fun hasChanges(): Boolean =
        draft.pendingAttachments.isNotEmpty() || draft.removedAttachmentIds.isNotEmpty() || draft.build() != baseline

    /** 保存；校验没过时返回 false（错误已标在表单上或以消息提示）。 */
    fun save(onSaved: () -> Unit): Boolean {
        if (saving) return false
        FieldCheck.titleIssue(draft.title)?.let {
            draft.titleError = it
            return false
        }
        // 两步验证：先校验，再统一存成规范的 otpauth URI（粘贴的纯 Base32 密钥也会转成 URI）
        for (field in draft.fields) {
            if (field.kind != FieldKind.TOTP || field.value.isBlank()) continue
            val config = try {
                Totp.parse(field.value)
            } catch (e: IllegalArgumentException) {
                container.messages.show("「${field.label.ifBlank { "两步验证码" }}」：${e.message ?: "密钥无效"}")
                return false
            }
            field.value = config.toUri()
        }
        saving = true
        viewModelScope.launch {
            try {
                container.repository.save(draft.build(), draft.pendingAttachments.toList(), draft.removedAttachmentIds.toList())
                container.messages.show(if (isNew) "已保存到金匮" else "已更新")
                onSaved()
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message.orEmpty()}")
            } finally {
                saving = false
            }
        }
        return true
    }
}

@Composable
fun CardEditScreen(
    cardId: String?,
    newType: CardType?,
    onClose: () -> Unit,
    /** 1.5.0-alpha02：新建时“更换类型”——换成另一种类型重新开始填。 */
    onRetype: (CardType) -> Unit = {},
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val viewModel = jkViewModel { CardEditViewModel(container, cardId, newType) }
    val draft = viewModel.draft
    val colors = Jk.colors

    var confirmDiscard by remember { mutableStateOf(false) }
    var generatorFor by remember { mutableStateOf<DraftField?>(null) }
    var pickKind by remember { mutableStateOf(false) }
    var batch by remember { mutableStateOf<MigrationBatch?>(null) }
    var pickType by remember { mutableStateOf(false) }
    var retypeTo by remember { mutableStateOf<CardType?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching { ImageImporter.load(context, uri) }
                    .onSuccess { draft.pendingAttachments.add(it) }
                    .onFailure { container.messages.show(it.message ?: "无法读取这张图片") }
            }
        }
    }

    // 两步验证码：从相册 / 文件里选一张二维码截图，离线识别后填入（系统选择器授权单张图片，不需要任何权限）
    var qrTarget by remember { mutableStateOf<DraftField?>(null) }
    var scanningId by remember { mutableStateOf<String?>(null) }
    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val field = qrTarget ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scanningId = field.id
        scope.launch {
            val read = runCatching { QrImageReader.read(context, uri) }
            scanningId = null
            val error = read.exceptionOrNull()
            if (error != null) {
                container.messages.show(error.message ?: "无法读取这张图片")
                return@launch
            }
            when (val result = OtpQr.interpret(read.getOrNull())) {
                is OtpQrResult.Ok -> {
                    field.value = result.uri
                    view.confirmHaptic()
                    container.messages.show("已从二维码填入两步验证码")
                }
                is OtpQrResult.Batch -> {
                    val usable = result.batch.entries.filter { it.usable }
                    if (result.batch.entries.size == 1 && usable.size == 1) {
                        // 只有一个账号：直接填进当前字段
                        field.value = usable.single().config!!.toUri()
                        view.confirmHaptic()
                        container.messages.show("已从导出二维码填入两步验证码")
                    } else {
                        batch = result.batch
                    }
                }
                is OtpQrResult.Rejected -> container.messages.show(result.message)
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
                onClick = { if (viewModel.save(onClose)) view.confirmHaptic() },
                modifier = Modifier.padding(end = Space.x1),
            )
        }
        CardEditForm(
            draft = draft,
            isNew = viewModel.isNew,
            suggestions = viewModel.suggestions,
            scanningId = scanningId,
            onAddField = { pickKind = true },
            onGenerate = { generatorFor = it },
            onScanQr = { field ->
                if (scanningId == null) {
                    qrTarget = field
                    // 去系统选图期间暂不因切后台而锁定，否则回来时草稿已被清掉
                    container.lock.holdForExternalActivity()
                    qrPicker.launch("image/*")
                }
            },
            onAddPhoto = {
                container.lock.holdForExternalActivity()
                picker.launch("image/*")
            },
            loadAttachment = { viewModel.attachmentBytes(it) },
            modifier = Modifier.weight(1f),
            onChangeType = if (viewModel.isNew) ({ pickType = true }) else null,
        )
    }

    if (pickType) {
        com.xiguli.jinkui.ui.home.TypePickerSheet(onDismiss = { pickType = false }, title = "换成哪种类型") { type ->
            pickType = false
            if (type != viewModel.type) {
                if (viewModel.hasChanges()) retypeTo = type else onRetype(type)
            }
        }
    }
    retypeTo?.let { type ->
        JkDialog(
            title = "换成「${type.label}」？",
            onDismiss = { retypeTo = null },
            confirmText = "换类型",
            dismissText = "继续填",
            danger = true,
            onConfirm = {
                retypeTo = null
                onRetype(type)
            },
        ) {
            Text("换类型会按新类型重新准备栏目，已经填的内容不会保留。", style = JkType.body, color = colors.textSecondary)
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
        JkSheet(onDismiss = { pickKind = false }, title = "添加自定义字段") {
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
                        draft.addField(kind)
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
    batch?.let { pending ->
        OtpBatchSheet(pending, onDismiss = { batch = null })
    }
}

/**
 * 编辑表单本体。1.5.0-alpha02 重排（参考 Bitwarden / Proton Pass 的新建条目：先选类型，再按组填）：
 * 类型（新建时可更换）→ 名称 → 主要信息（模板里的栏目）→ 两步验证 → 自定义字段 → 备注 → 标签 → 卡面颜色 → 附件。
 * 密码栏目下面直接有“生成强密码”和强度提示。只读写 [draft] 的 Compose 状态，不碰仓库，界面测试可以直接渲染。
 */
@Composable
fun CardEditForm(
    draft: CardDraft,
    isNew: Boolean,
    suggestions: List<String>,
    scanningId: String?,
    onAddField: () -> Unit,
    onGenerate: (DraftField) -> Unit,
    onScanQr: (DraftField) -> Unit,
    onAddPhoto: () -> Unit,
    loadAttachment: suspend (String) -> ByteArray,
    modifier: Modifier = Modifier,
    /** 新建时“更换”类型；null 时不显示（编辑已有卡片不能换类型）。 */
    onChangeType: (() -> Unit)? = null,
) {
    val colors = Jk.colors
    var tagInput by remember { mutableStateOf("") }
    val templateLabels = remember(draft.type) { CardTemplates.fieldsFor(draft.type).map { it.label }.toSet() }
    val all = draft.fields.toList()
    val main = all.filter { it.kind != FieldKind.TOTP && it.label.trim() in templateLabels }
    val totp = all.filter { it.kind == FieldKind.TOTP }
    val custom = all.filter { it.kind != FieldKind.TOTP && it.label.trim() !in templateLabels }
    val lastField = all.lastOrNull()
    @Composable
    fun editor(field: DraftField) = FieldEditor(
        field = field,
        hint = CardTemplates.hintFor(draft.type, field.label),
        last = field == lastField,
        scanning = scanningId == field.id,
        onRemove = { draft.fields.remove(field) },
        onGenerate = { onGenerate(field) },
        onScanQr = { onScanQr(field) },
    )
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Space.x2_5)
            .padding(top = Space.x05, bottom = Space.x5),
    ) {
        // 类型：一枚与卡面同色的图标块 + 类型名；新建时可以更换
        val theme = cardTheme(draft.type, draft.color, draft.brand)
        Row(
            Modifier
                .fillMaxWidth()
                .jkSurface(Jk.surfaces.card, Radius.field)
                .padding(start = Space.x1_5, end = Space.x05, top = Space.x1_5, bottom = Space.x1_5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(Radius.tile)
                    .background(Brush.linearGradient(listOf(theme.start, theme.end))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(draft.type.icon, contentDescription = null, tint = theme.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(Space.x1_5))
            Column(Modifier.weight(1f)) {
                Text("类型", style = JkType.caption, color = colors.textTertiary)
                Text(draft.type.label, style = JkType.bodyStrong, color = colors.textPrimary)
            }
            if (onChangeType != null) {
                JkButton("更换", onChangeType, style = ButtonStyle.Ghost, compact = true)
            }
        }
        Spacer(Modifier.height(Space.x1_5))
        JkTextField(
            value = draft.title,
            onValueChange = {
                draft.title = it
                draft.titleError = null
            },
            label = "名称",
            placeholder = when (draft.type) {
                CardType.LOGIN -> "例如：GitHub、微信"
                CardType.WIFI -> "例如：家里 Wi-Fi"
                else -> "例如：招商银行储蓄卡"
            },
            imeAction = ImeAction.Next,
            error = draft.titleError,
        )

        if (main.isNotEmpty()) {
            FormSection("主要信息", hint = "带锁的栏目保存后默认打码，点锁可以切换")
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) { for (field in main) editor(field) }
        }

        if (totp.isNotEmpty() || draft.type == CardType.LOGIN) {
            FormSection("两步验证", hint = "填了以后，详情页会显示每 30 秒变一次的验证码")
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) { for (field in totp) editor(field) }
            if (totp.isEmpty()) {
                JkButton(
                    text = "添加两步验证码",
                    icon = Icons.Outlined.Timer,
                    style = ButtonStyle.Secondary,
                    onClick = { draft.addField(FieldKind.TOTP) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        FormSection("自定义字段", hint = "模板里没有的信息，比如安全问题、备用邮箱、客服电话")
        if (custom.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) { for (field in custom) editor(field) }
            Spacer(Modifier.height(Space.x1))
        }
        JkButton(
            text = "添加自定义字段",
            icon = Icons.Outlined.Add,
            style = ButtonStyle.Secondary,
            onClick = onAddField,
            modifier = Modifier.fillMaxWidth(),
        )
        val missing = draft.missingTemplateCount
        if (!isNew && missing > 0) {
            Spacer(Modifier.height(Space.x05))
            JkButton(
                text = "补上「${draft.type.label}」模板里缺的 $missing 个字段",
                style = ButtonStyle.Ghost,
                compact = true,
                onClick = draft::addMissingTemplateFields,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormSection("备注")
        JkTextField(
            value = draft.note,
            onValueChange = { draft.note = it },
            placeholder = "可以写点提醒自己的话，支持搜索",
            singleLine = false,
            minLines = 3,
        )

        FormSection("标签", hint = "用来分组与筛选，回车或逗号分隔")
        if (draft.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
                modifier = Modifier.padding(bottom = Space.x05),
            ) {
                for (tag in draft.tags.toList()) {
                    JkChip("$tag  ×", selected = true, onClick = { draft.tags.remove(tag) })
                }
            }
        }
        JkTextField(
            value = tagInput,
            onValueChange = { tagInput = it },
            placeholder = "输入标签后按回车",
            imeAction = ImeAction.Done,
            onImeAction = {
                draft.addTag(tagInput)
                tagInput = ""
            },
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x1),
                modifier = Modifier.padding(top = Space.x05),
            ) {
                for (tag in suggestions) {
                    JkChip(tag, selected = false, onClick = { draft.addTag(tag) })
                }
            }
        }

        FormSection("卡面颜色", hint = if (draft.type == CardType.BANK) "默认卡面会按名称或开户行带上银行的主色" else null)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x05),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            // 第一个是该类型自己的默认卡面，其后是可选的纯色。
            for (index in listOf(CardPalette.AUTO) + CardPalette.colors.indices) {
                val selected = index == draft.color
                val swatch = cardTheme(draft.type, index, if (index == CardPalette.AUTO) BankBrand.of(draft.type, index, draft.title, "") else null)
                Box(
                    modifier = Modifier
                        .size(JkSize.touch)
                        .clip(CircleShape)
                        .clickable(onClickLabel = if (index == CardPalette.AUTO) "使用默认卡面" else "使用第 ${index + 1} 种颜色") { draft.color = index }
                        .semantics {
                            contentDescription = if (index == CardPalette.AUTO) "默认卡面" else "颜色 ${index + 1}"
                            this.selected = selected
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (selected) colors.textPrimary else colors.bg, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(swatch.start, swatch.end))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == CardPalette.AUTO) {
                            Icon(draft.type.icon, contentDescription = null, tint = swatch.accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        FormSection("附件", hint = "照片会去掉拍摄信息后加密保存在本机，不会出现在系统相册里")
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            for (attachment in draft.existingAttachments.toList()) {
                AttachmentThumb(
                    key = attachment.id,
                    onRemove = { draft.removeExisting(attachment) },
                    load = { loadAttachment(attachment.id) },
                )
            }
            for (pending in draft.pendingAttachments.toList()) {
                AttachmentThumb(
                    key = pending.id,
                    onRemove = { draft.pendingAttachments.remove(pending) },
                    load = { pending.bytes },
                )
            }
            Column(
                modifier = Modifier
                    .size(width = 128.dp, height = 88.dp)
                    .clip(Radius.tile)
                    .border(1.dp, colors.border, Radius.tile)
                    .clickable(onClickLabel = "添加照片", onClick = onAddPhoto),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = colors.textSecondary)
                Spacer(Modifier.height(Space.x05))
                Text("添加照片", style = JkType.caption, color = colors.textSecondary)
            }
        }
    }
}

/** 密码强度的白话：弱 / 一般 / 强（按估算的熵）。 */
internal fun strengthLabel(password: String): String? {
    if (password.isBlank()) return null
    val bits = com.xiguli.jinkui.core.crypto.PasswordGenerator.estimateBits(password)
    return when {
        bits < 40 -> "弱：容易被猜到，建议生成一个"
        bits < 70 -> "一般"
        else -> "强"
    }
}

/** 表单小节：眉题 + 可选的一行说明。 */
@Composable
private fun FormSection(title: String, hint: String? = null) {
    Column(Modifier.padding(start = Space.x05, top = Space.x3, bottom = Space.x1)) {
        Text(title, style = JkType.overline, color = Jk.colors.textTertiary, modifier = Modifier.semantics { heading() })
        if (hint != null) {
            Text(hint, style = JkType.caption, color = Jk.colors.textTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * 一个字段块：上行是字段名，下行是内容，中间一根分隔线。
 * 右上角的小锁切换“敏感”——锁上后内容默认打码。内容下方是即时提示（格式看起来不对时）。
 */
@Composable
private fun FieldEditor(
    field: DraftField,
    hint: String,
    last: Boolean,
    scanning: Boolean,
    onRemove: () -> Unit,
    onGenerate: () -> Unit,
    onScanQr: () -> Unit,
) {
    val colors = Jk.colors
    var revealed by remember { mutableStateOf(false) }
    val totpError = if (field.kind == FieldKind.TOTP) remember(field.value) { Totp.errorOf(field.value) } else null
    val softIssue = remember(field.kind, field.value) { FieldCheck.issue(field.kind, field.value) }
    // 1.3：字段块是一块玻璃卡片；只有格式有问题时才加一圈提示色描边
    val outline = when {
        totpError != null -> colors.danger
        softIssue != null -> colors.expense
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .jkSurface(Jk.surfaces.card, Radius.field)
            .then(if (outline != null) Modifier.border(1.dp, outline, Radius.field) else Modifier),
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
                imeAction = ImeAction.Next,
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
            JkIconButton(Icons.Outlined.Close, "删除字段${field.label}", onRemove, tint = colors.textTertiary)
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
                textStyle = when {
                    field.kind == FieldKind.NUMBER || field.kind == FieldKind.PHONE -> JkType.digits
                    field.sensitive -> JkType.mono
                    else -> JkType.body
                },
                hidden = field.sensitive && !revealed,
                keyboardType = when (field.kind) {
                    FieldKind.NUMBER -> if (field.sensitive) KeyboardType.NumberPassword else KeyboardType.Number
                    FieldKind.URL -> KeyboardType.Uri
                    FieldKind.PHONE -> KeyboardType.Phone
                    FieldKind.PASSWORD, FieldKind.TOTP -> KeyboardType.Password
                    else -> if (field.sensitive) KeyboardType.Password else KeyboardType.Text
                },
                // 最后一个字段按“完成”收起键盘，其余“下一项”
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = JkSize.touch)
                    .padding(vertical = Space.x1_5),
            )
            if (field.kind == FieldKind.TOTP) {
                JkIconButton(
                    Icons.Outlined.QrCodeScanner,
                    if (scanning) "正在识别二维码" else "从相册图片识别二维码",
                    onScanQr,
                    tint = if (scanning) colors.textTertiary else colors.textSecondary,
                    enabled = !scanning,
                )
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
        // 1.5.0-alpha02：密码栏目下面直接生成（参考 Bitwarden / Proton Pass 的内联生成器），不用另开一页
        if (field.kind == FieldKind.PASSWORD) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.x1, end = Space.x1, bottom = Space.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JkButton(
                    text = "生成强密码",
                    icon = Icons.Outlined.AutoFixHigh,
                    style = ButtonStyle.Ghost,
                    compact = true,
                    onClick = {
                        field.value = com.xiguli.jinkui.core.crypto.PasswordGenerator.generate(com.xiguli.jinkui.core.crypto.GeneratorOptions())
                        revealed = true
                    },
                )
                JkButton(text = "更多选项", style = ButtonStyle.Ghost, compact = true, onClick = onGenerate)
                Spacer(Modifier.weight(1f))
                strengthLabel(field.value)?.let { label ->
                    Text(
                        "强度：$label",
                        style = JkType.caption,
                        color = if (label.startsWith("弱")) colors.expense else colors.textTertiary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = Space.x1).weight(1f, fill = false),
                    )
                }
            }
        }
        when {
            field.kind == FieldKind.TOTP -> Text(
                text = totpError ?: "支持 SHA1 / SHA256 / SHA512、6 或 8 位、30 或 60 秒；也可以点二维码图标，从相册截图识别（含身份验证器的导出码）。密钥只存在本机，加密保存。",
                style = JkType.caption,
                color = if (totpError != null) colors.danger else colors.textTertiary,
                modifier = Modifier.padding(start = Space.x2, end = Space.x2, bottom = Space.x1_5),
            )
            softIssue != null -> Text(
                text = softIssue,
                style = JkType.caption,
                color = colors.expense,
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
        // 视觉 24dp 的小圆钮，触控区补到 48dp
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(JkSize.touch)
                .clip(CircleShape)
                .clickable(onClickLabel = "移除照片", onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Jk.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "移除照片", tint = Jk.colors.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
