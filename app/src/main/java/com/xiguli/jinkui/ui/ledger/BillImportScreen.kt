package com.xiguli.jinkui.ui.ledger

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TableChart
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.jinkui.AppContainer
import com.xiguli.jinkui.data.importer.BillFormatException
import com.xiguli.jinkui.data.importer.BillImport
import com.xiguli.jinkui.data.importer.BillParser
import com.xiguli.jinkui.data.importer.BillSource
import com.xiguli.jinkui.data.importer.CandidateFlag
import com.xiguli.jinkui.data.importer.ColumnMapping
import com.xiguli.jinkui.data.importer.ImportCandidate
import com.xiguli.jinkui.data.importer.ImportPreview
import com.xiguli.jinkui.data.importer.ImportResult
import com.xiguli.jinkui.data.importer.ParsedBill
import com.xiguli.jinkui.data.importer.TableReader
import com.xiguli.jinkui.data.importer.TextTable
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkBadge
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkCheck
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSectionLabel
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.nav.jkViewModel
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导入账单的几个步骤。 */
sealed interface ImportStage {
    data object Intro : ImportStage
    data object Reading : ImportStage
    /** 通用表格：确认各列对应什么。 */
    data class Mapping(val table: TextTable, val mapping: ColumnMapping, val fileName: String) : ImportStage
    data class Preview(val preview: ImportPreview) : ImportStage
    data class Saving(val preview: ImportPreview) : ImportStage
    data class Done(val result: ImportResult) : ImportStage
}

class BillImportViewModel(private val container: AppContainer) : ViewModel() {
    var stage by mutableStateOf<ImportStage>(ImportStage.Intro)
        private set
    var error by mutableStateOf<String?>(null)
    private var parsedCsv: ParsedBill? = null
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun open(context: Context, uri: Uri) {
        error = null
        stage = ImportStage.Reading
        viewModelScope.launch {
            try {
                val (table, name) = withContext(Dispatchers.IO) {
                    val name = displayName(context, uri)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            if (out.size() > TableReader.MAX_BYTES) throw BillFormatException("文件太大了（上限 30 MB）")
                        }
                        out.toByteArray()
                    } ?: throw BillFormatException("无法读取这个文件")
                    TableReader.read(bytes, name) to name
                }
                val source = BillParser.detect(table)
                stage = if (source != null) {
                    val parsed = withContext(Dispatchers.Default) { BillParser.parse(table, source) }
                    ImportStage.Preview(withContext(Dispatchers.Default) { BillImport.preview(parsed, container.ledger.data.value, zone) })
                } else {
                    if (table.rows.size < 2) throw BillFormatException("这个文件里没有可以导入的表格")
                    ImportStage.Mapping(table, BillParser.guessMapping(table), name)
                }
            } catch (e: BillFormatException) {
                error = e.message
                stage = ImportStage.Intro
            } catch (e: Exception) {
                error = "无法读取这个文件"
                stage = ImportStage.Intro
            }
        }
    }

    fun updateMapping(mapping: ColumnMapping) {
        val current = stage as? ImportStage.Mapping ?: return
        stage = current.copy(mapping = mapping)
    }

    fun confirmMapping() {
        val current = stage as? ImportStage.Mapping ?: return
        try {
            val parsed = BillParser.parseGeneric(current.table, current.mapping)
            if (parsed.records.isEmpty()) {
                error = "按这样的列对应，一笔也没有识别出来，请检查“时间”和“金额”两列"
                return
            }
            parsedCsv = parsed
            stage = ImportStage.Preview(BillImport.preview(parsed, container.ledger.data.value, zone))
        } catch (e: BillFormatException) {
            error = e.message
        }
    }

    private fun edit(transform: (ImportPreview) -> ImportPreview) {
        val current = stage as? ImportStage.Preview ?: return
        stage = ImportStage.Preview(transform(current.preview))
    }

    fun toggle(id: String) = edit { p -> p.copy(candidates = p.candidates.map { if (it.id == id) it.copy(checked = !it.checked) else it }) }

    fun setAll(checked: Boolean) = edit { p -> p.copy(candidates = p.candidates.map { it.copy(checked = checked) }) }

    fun setCategory(id: String, categoryId: String?) =
        edit { p -> p.copy(candidates = p.candidates.map { if (it.id == id) it.copy(categoryId = categoryId) else it }) }

    /** 通用表格：换一个默认账户（重新算一遍预览，保留勾选与改过的分类）。 */
    fun setAccount(accountId: String) {
        val current = stage as? ImportStage.Preview ?: return
        val parsed = parsedCsv ?: return
        val fresh = BillImport.preview(parsed, container.ledger.data.value, zone, preferredAccount = accountId)
        val old = current.preview.candidates.associateBy { it.id }
        stage = ImportStage.Preview(
            fresh.copy(candidates = fresh.candidates.map { c -> old[c.id]?.let { o -> c.copy(checked = o.checked, categoryId = o.categoryId) } ?: c }),
        )
    }

    fun commit() {
        val current = stage as? ImportStage.Preview ?: return
        val preview = current.preview
        if (preview.selected.isEmpty()) return
        stage = ImportStage.Saving(preview)
        viewModelScope.launch {
            try {
                val txs = BillImport.transactions(preview, zone, System.currentTimeMillis())
                val saved = container.ledger.importTransactions(txs, preview.newAccount)
                stage = ImportStage.Done(BillImport.result(preview, saved))
            } catch (e: LedgerInputException) {
                error = e.message
                stage = current
            } catch (e: Exception) {
                error = "导入失败：${e.message ?: "未知错误"}"
                stage = current
            }
        }
    }

    fun restart() {
        parsedCsv = null
        error = null
        stage = ImportStage.Intro
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
}

@Composable
fun BillImportScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val viewModel = jkViewModel { BillImportViewModel(container) }
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.open(context, uri)
    }
    var pickCategoryFor by remember { mutableStateOf<ImportCandidate?>(null) }
    var pickAccount by remember { mutableStateOf(false) }
    val pick = {
        container.lock.holdForExternalActivity()
        picker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/octet-stream"))
    }

    viewModel.error?.let { message ->
        LaunchedMessage(message) { viewModel.error = null }
    }

    when (val stage = viewModel.stage) {
        ImportStage.Intro, ImportStage.Reading -> BillImportIntro(reading = stage == ImportStage.Reading, onBack = onBack, onPick = pick)
        is ImportStage.Mapping -> BillMappingContent(
            stage = stage,
            onBack = { viewModel.restart() },
            onChange = viewModel::updateMapping,
            onConfirm = viewModel::confirmMapping,
        )
        is ImportStage.Preview -> BillPreviewContent(
            preview = stage.preview,
            data = data,
            saving = false,
            actions = BillPreviewActions(
                onBack = { viewModel.restart() },
                onToggle = viewModel::toggle,
                onAll = viewModel::setAll,
                onCategory = { pickCategoryFor = it },
                onAccount = if (stage.preview.source == BillSource.CSV) ({ pickAccount = true }) else null,
                onCommit = viewModel::commit,
            ),
        )
        is ImportStage.Saving -> BillPreviewContent(stage.preview, data, saving = true, actions = BillPreviewActions())
        is ImportStage.Done -> BillImportDone(stage.result, onFinish = onBack, onAgain = { viewModel.restart() })
    }

    pickCategoryFor?.let { candidate ->
        CategoryPickerSheet(
            categories = data.categories,
            title = "选择分类",
            type = candidate.record.type,
            onDismiss = { pickCategoryFor = null },
            onPick = {
                viewModel.setCategory(candidate.id, it)
                pickCategoryFor = null
            },
        )
    }
    if (pickAccount) {
        JkSheet(onDismiss = { pickAccount = false }, title = "记到哪个账户") {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                for (account in data.accounts.filter { !it.archived }) {
                    JkRow(
                        title = account.name,
                        subtitle = account.kind.label,
                        icon = accountIcon(account.kind),
                        modifier = Modifier.clip(Radius.control),
                        trailing = {},
                        onClick = {
                            viewModel.setAccount(account.id)
                            pickAccount = false
                        },
                    )
                }
            }
        }
    }
}

/** 把错误交给底部消息条显示一次。 */
@Composable
private fun LaunchedMessage(message: String, onShown: () -> Unit) {
    val container = LocalContainer.current
    androidx.compose.runtime.LaunchedEffect(message) {
        container.messages.show(message)
        onShown()
    }
}

// ── 第一步：说明与选文件 ─────────────────────────────────────────

@Composable
fun BillImportIntro(reading: Boolean, onBack: () -> Unit, onPick: () -> Unit) {
    val colors = Jk.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(title = "导入账单", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2)
                .padding(top = Space.x05, bottom = Space.x5),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.card)
                    .background(colors.hero)
                    .padding(Space.x2_5),
            ) {
                Text("把微信、支付宝的账单一次导进来", style = JkType.section, color = colors.textPrimary)
                Spacer(Modifier.height(Space.x1))
                Text(
                    "支持微信支付账单（.xlsx / .csv）、支付宝账单（.csv），以及任意 CSV 表格。导入前可以逐笔预览、改分类、去掉不想要的。",
                    style = JkType.small,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(Space.x2))
                JkButton(
                    text = if (reading) "正在读取…" else "选择账单文件",
                    onClick = onPick,
                    icon = Icons.Outlined.FileOpen,
                    loading = reading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            JkSectionLabel("微信怎么导出")
            HowTo(
                listOf(
                    "微信 → 我 → 服务 → 钱包 → 账单 → 右上角“···” → 下载账单 → 用于个人对账",
                    "选好时间范围，账单会发到你的邮箱（压缩包，解压密码在微信支付的通知里）",
                    "解压后在这里选那个 .xlsx 或 .csv 文件",
                ),
            )
            JkSectionLabel("支付宝怎么导出")
            HowTo(
                listOf(
                    "支付宝 → 我的 → 账单 → 右上角“···” → 开具交易流水证明 → 用于个人对账",
                    "选好时间范围发到邮箱，解压后得到 .csv 文件（GBK 编码，金匮会自动识别）",
                ),
            )
            JkSectionLabel("会怎么处理")
            HowTo(
                listOf(
                    "按关键字自动归类（例如瑞幸 → 饮品、滴滴 → 打车），微信账单记到“微信”账户、支付宝记到“支付宝”账户，没有时会新建",
                    "同一笔重复导入会按交易单号认出来跳过；没有单号时按“时间 + 金额 + 交易对方”判断",
                    "零钱提现、充值、还款等“不计收支”，交易关闭、已全额退款的不会导入；部分退款会从原支出里扣掉",
                ),
            )
            Row(Modifier.padding(start = Space.x05, top = Space.x2), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Space.x05))
                Text("账单只在这台手机上解析，不上传；原文件不会被保存。", style = JkType.caption, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun HowTo(steps: List<String>) {
    val colors = Jk.colors
    JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.x2, vertical = Space.x1_5)) {
        steps.forEachIndexed { index, step ->
            Row(Modifier.padding(vertical = Space.x05)) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = JkType.amountCaption, color = colors.accent)
                }
                Spacer(Modifier.width(Space.x1_5))
                Text(step, style = JkType.small, color = colors.textSecondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── 通用表格：列对应 ─────────────────────────────────────────────

private enum class MapField(val label: String, val required: Boolean) {
    TIME("时间", true), AMOUNT("金额", true), DIRECTION("收 / 支", false), PARTY("交易对方", false),
    ITEM("说明", false), NUMBER("单号", false), CATEGORY("分类", false),
}

private fun ColumnMapping.get(field: MapField): Int = when (field) {
    MapField.TIME -> time
    MapField.AMOUNT -> amount
    MapField.DIRECTION -> direction
    MapField.PARTY -> counterparty
    MapField.ITEM -> item
    MapField.NUMBER -> txNo
    MapField.CATEGORY -> category
}

private fun ColumnMapping.with(field: MapField, col: Int): ColumnMapping = when (field) {
    MapField.TIME -> copy(time = col)
    MapField.AMOUNT -> copy(amount = col)
    MapField.DIRECTION -> copy(direction = col)
    MapField.PARTY -> copy(counterparty = col)
    MapField.ITEM -> copy(item = col)
    MapField.NUMBER -> copy(txNo = col)
    MapField.CATEGORY -> copy(category = col)
}

@Composable
fun BillMappingContent(
    stage: ImportStage.Mapping,
    onBack: () -> Unit,
    onChange: (ColumnMapping) -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = Jk.colors
    val table = stage.table
    val mapping = stage.mapping
    val header = table.rows.getOrNull(mapping.headerRow).orEmpty()
    val sample = table.rows.getOrNull(mapping.headerRow + 1).orEmpty()
    var picking by remember { mutableStateOf<MapField?>(null) }
    var pickingHeader by remember { mutableStateOf(false) }
    fun columnName(i: Int): String = if (i < 0) "不使用" else header.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: "第 ${i + 1} 列"

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(title = "对应表格的列", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x2)
                .padding(top = Space.x05, bottom = Space.x2),
        ) {
            Text(
                "「${stage.fileName.ifEmpty { "表格" }}」不是微信或支付宝的原始账单。告诉金匮每一列是什么，下一步可以先预览再导入。",
                style = JkType.small,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Space.x05, vertical = Space.x1),
            )
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                JkRow(
                    title = "表头所在行",
                    subtitle = header.filter { it.isNotEmpty() }.take(4).joinToString(" · ").ifEmpty { "（空行）" },
                    value = "第 ${mapping.headerRow + 1} 行",
                    icon = Icons.Outlined.TableChart,
                    onClick = { pickingHeader = true },
                )
            }
            JkSectionLabel("各列")
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                MapField.entries.forEachIndexed { index, field ->
                    if (index > 0) JkDivider(inset = Space.x2)
                    val col = mapping.get(field)
                    JkRow(
                        title = field.label + if (field.required) "（必选）" else "",
                        subtitle = if (col >= 0) "例：" + sample.getOrNull(col).orEmpty().ifEmpty { "（空）" } else null,
                        value = columnName(col),
                        onClick = { picking = field },
                    )
                }
            }
            if (mapping.direction < 0) {
                Spacer(Modifier.height(Space.x1_5))
                JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                    JkRow(
                        title = "负数是支出、正数是收入",
                        subtitle = if (mapping.negativeIsExpense) "没有“收 / 支”列时按金额的正负判断" else "关闭时每一笔都当作支出",
                        trailing = { JkSwitch(mapping.negativeIsExpense, { onChange(mapping.copy(negativeIsExpense = it)) }) },
                    )
                }
            }
        }
        Box(Modifier.navigationBarsPadding().padding(Space.x2)) {
            JkButton(
                text = "下一步：预览",
                onClick = onConfirm,
                enabled = mapping.complete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    picking?.let { field ->
        JkSheet(onDismiss = { picking = null }, title = "「${field.label}」是哪一列") {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (!field.required) {
                    JkRow(title = "不使用", modifier = Modifier.clip(Radius.control), trailing = {}, onClick = {
                        onChange(mapping.with(field, -1))
                        picking = null
                    })
                }
                val width = table.rows.drop(mapping.headerRow).take(20).maxOfOrNull { it.size } ?: header.size
                for (i in 0 until width) {
                    JkRow(
                        title = columnName(i),
                        subtitle = "例：" + sample.getOrNull(i).orEmpty().ifEmpty { "（空）" },
                        modifier = Modifier.clip(Radius.control),
                        trailing = { if (mapping.get(field) == i) Icon(Icons.Outlined.Check, contentDescription = "已选", tint = colors.accent) },
                        onClick = {
                            onChange(mapping.with(field, i))
                            picking = null
                        },
                    )
                }
            }
        }
    }
    if (pickingHeader) {
        JkSheet(onDismiss = { pickingHeader = false }, title = "哪一行是表头") {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                for (i in 0 until minOf(table.rows.size, 30)) {
                    JkRow(
                        title = "第 ${i + 1} 行",
                        subtitle = table.rows[i].filter { it.isNotEmpty() }.take(5).joinToString(" · "),
                        modifier = Modifier.clip(Radius.control),
                        trailing = { if (mapping.headerRow == i) Icon(Icons.Outlined.Check, contentDescription = "已选", tint = colors.accent) },
                        onClick = {
                            val guessed = BillParser.guessMapping(TextTable(table.rows.drop(i)))
                            onChange(guessed.copy(headerRow = i))
                            pickingHeader = false
                        },
                    )
                }
            }
        }
    }
}

// ── 预览 ──────────────────────────────────────────────────────────

class BillPreviewActions(
    val onBack: () -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onAll: (Boolean) -> Unit = {},
    val onCategory: (ImportCandidate) -> Unit = {},
    /** 只有通用表格可以换默认账户。 */
    val onAccount: (() -> Unit)? = null,
    val onCommit: () -> Unit = {},
)

private val ROW_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val RANGE_DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd")

@Composable
fun BillPreviewContent(preview: ImportPreview, data: LedgerData, saving: Boolean, actions: BillPreviewActions) {
    val colors = Jk.colors
    val categories = remember(data.categories) { data.categories.associateBy { it.id } }
    val accounts: Map<String, LedgerAccount> = remember(data.accounts, preview.newAccount) {
        (data.accounts + listOfNotNull(preview.newAccount)).associateBy { it.id }
    }
    val selected = preview.selected
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(title = "导入预览", onBack = actions.onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Space.x2, end = Space.x2, bottom = Space.x2),
        ) {
            item(key = "summary") {
                PreviewSummary(preview, onAccount = actions.onAccount, accounts = accounts)
            }
            item(key = "tools") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.x1_5, bottom = Space.x05),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "共 ${preview.candidates.size} 笔 · 已选 ${selected.size} 笔",
                        style = JkType.small.copy(fontFamily = JkType.date.fontFamily),
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f).padding(start = Space.x05),
                    )
                    JkChip("全选", selected = selected.size == preview.candidates.size && selected.isNotEmpty(), onClick = { actions.onAll(true) })
                    Spacer(Modifier.width(Space.x05))
                    JkChip("全不选", selected = selected.isEmpty(), onClick = { actions.onAll(false) })
                }
            }
            if (preview.candidates.isEmpty()) {
                item(key = "empty") {
                    com.xiguli.jinkui.ui.components.JkEmpty(
                        title = "没有新的记录",
                        message = if (preview.alreadyImported > 0) "这份账单里的 ${preview.alreadyImported} 笔以前都导入过了。" else "这份账单里没有可以导入的收支。",
                        illustration = { LedgerIllustration(LedgerArt.NO_MATCH) },
                    )
                }
            }
            items(preview.candidates, key = { it.id }, contentType = { "row" }) { c ->
                val shape = when {
                    preview.candidates.size == 1 -> Radius.card
                    c === preview.candidates.first() -> androidx.compose.foundation.shape.RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    c === preview.candidates.last() -> androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                    else -> androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                }
                Column(Modifier.clip(shape).background(colors.surface)) {
                    CandidateRow(c, categories, accounts, actions)
                    if (c !== preview.candidates.last()) JkDivider(inset = 92.dp)
                }
            }
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = Space.x2, vertical = Space.x1_5)) {
            JkButton(
                text = if (selected.isEmpty()) "请至少勾选一笔" else "导入 ${selected.size} 笔",
                onClick = actions.onCommit,
                enabled = selected.isNotEmpty(),
                loading = saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PreviewSummary(preview: ImportPreview, onAccount: (() -> Unit)?, accounts: Map<String, LedgerAccount>) {
    val colors = Jk.colors
    val times = preview.candidates.map { it.record.time }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(colors.hero)
            .padding(Space.x2_5),
    ) {
        Text(
            when (preview.source) {
                BillSource.WECHAT -> "微信支付账单"
                BillSource.ALIPAY -> "支付宝账单"
                BillSource.CSV -> "表格账单"
            },
            style = JkType.section,
            color = colors.textPrimary,
        )
        if (times.isNotEmpty()) {
            Text(
                "${times.min().format(RANGE_DAY)} – ${times.max().format(RANGE_DAY)}",
                style = JkType.date,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(Space.x1_5))
        Row {
            SummaryFigure("将导入支出", preview.expenseTotal, colors.expense, Modifier.weight(1f))
            SummaryFigure("将导入收入", preview.incomeTotal, colors.income, Modifier.weight(1f))
        }
        val notes = buildList {
            if (preview.newAccount != null) add("将新建账户「${preview.newAccount.name}」")
            if (preview.alreadyImported > 0) add("以前导入过 ${preview.alreadyImported} 笔，已跳过")
            for ((reason, n) in preview.skipped) add("${reason.label} ${n} 笔，不导入")
            val similar = preview.candidates.count { it.flag == CandidateFlag.SIMILAR }
            if (similar > 0) add("$similar 笔可能已经记过（同金额、时间相近），默认不勾")
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(Space.x1_5))
            for (line in notes) Text("· $line", style = JkType.caption, color = colors.textSecondary)
        }
        if (onAccount != null) {
            val first = preview.candidates.firstOrNull()?.accountId?.let { accounts[it]?.name } ?: "选择账户"
            Spacer(Modifier.height(Space.x1_5))
            JkButton("记到：$first", onAccount, style = ButtonStyle.Secondary, compact = true)
        }
    }
}

@Composable
private fun SummaryFigure(label: String, cents: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = JkType.caption, color = Jk.colors.textTertiary)
        Text(Money.format(cents), style = JkType.amount, color = color)
    }
}

@Composable
private fun CandidateRow(
    c: ImportCandidate,
    categories: Map<String, LedgerCategory>,
    accounts: Map<String, LedgerAccount>,
    actions: BillPreviewActions,
) {
    val colors = Jk.colors
    val r = c.record
    val category = c.categoryId?.let(categories::get)
    val label = categoryLabel(c.categoryId, categories)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Checkbox, onClick = { actions.onToggle(c.id) })
            .semantics { stateDescription = if (c.checked) "已勾选" else "未勾选" }
            .padding(start = Space.x1_5, end = Space.x2, top = Space.x1, bottom = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(JkSize.touch), contentAlignment = Alignment.Center) { JkCheck(c.checked) }
        Box(
            Modifier
                .size(JkSize.touch)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = { actions.onCategory(c) })
                .semantics { contentDescription = "分类：$label，点按修改" },
            contentAlignment = Alignment.Center,
        ) {
            CategoryChip(categoryIcon(category?.icon), r.type)
        }
        Spacer(Modifier.width(Space.x1))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = JkType.bodyStrong,
                    color = if (c.checked) colors.textPrimary else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                when (c.flag) {
                    CandidateFlag.SIMILAR -> { Spacer(Modifier.width(Space.x05)); JkBadge("可能已记过") }
                    CandidateFlag.REFUND -> { Spacer(Modifier.width(Space.x05)); JkBadge("退款") }
                    CandidateFlag.NEW -> Unit
                }
            }
            Text(
                r.summary.ifEmpty { r.kind.ifEmpty { "（无说明）" } },
                style = JkType.small,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                r.time.format(ROW_TIME) + " · " + (accounts[c.accountId]?.name ?: "账户"),
                style = JkType.date.copy(fontSize = JkType.caption.fontSize),
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space.x1))
        Text(
            (if (r.type == TxType.INCOME) "+" else "-") + Money.format(r.amount, symbol = false),
            style = JkType.amount,
            color = if (!c.checked) colors.textTertiary else if (r.type == TxType.INCOME) colors.income else colors.textPrimary,
        )
    }
}

// ── 完成 ──────────────────────────────────────────────────────────

@Composable
fun BillImportDone(result: ImportResult, onFinish: () -> Unit, onAgain: () -> Unit) {
    val colors = Jk.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar(title = "导入完成", onBack = onFinish)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x2)
                .padding(top = Space.x2, bottom = Space.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(Space.x2))
            Text("已导入 ${result.imported} 笔", style = JkType.title, color = colors.textPrimary)
            Spacer(Modifier.height(Space.x2_5))
            JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.x2, vertical = Space.x1_5)) {
                val lines = buildList {
                    add("支出 ${result.expenseCount} 笔" to Money.format(result.expenseTotal))
                    add("收入 ${result.incomeCount} 笔" to Money.format(result.incomeTotal))
                    result.createdAccount?.let { add("新建账户" to it) }
                    if (result.unchecked > 0) add("没有勾选" to "${result.unchecked} 笔")
                    if (result.alreadyImported > 0) add("以前导入过" to "${result.alreadyImported} 笔")
                    for ((reason, n) in result.skipped) add(reason.label to "$n 笔")
                }
                lines.forEachIndexed { i, (name, value) ->
                    if (i > 0) JkDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = JkType.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
                        Text(value, style = JkType.amountSmall, color = colors.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(Space.x1_5))
            Text("导入的流水和手动记的一样，可以随时修改或删除。", style = JkType.caption, color = colors.textTertiary)
        }
        Column(Modifier.navigationBarsPadding().padding(Space.x2), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkButton("完成", onFinish, modifier = Modifier.fillMaxWidth())
            JkButton("再导入一份", onAgain, style = ButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
        }
    }
}
