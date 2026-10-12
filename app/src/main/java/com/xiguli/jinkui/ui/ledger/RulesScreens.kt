package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerRule
import com.xiguli.jinkui.data.ledger.LedgerTemplate
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.RuleMath
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.data.repo.LedgerInputException
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.BotanicalCard
import com.xiguli.jinkui.ui.components.BotanicalLayout
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkCard
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkDivider
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.JkTopBar
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkSize
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import com.xiguli.jinkui.ui.theme.jkSurface
import kotlinx.coroutines.launch

// ── 自动分类规则与记账模板（1.4.0-alpha04）──────────────────────────
// 规则：备注 / 商户里有某个关键词（可再限定收支与金额范围）就归到指定分类，可同时换账户、加标签；
// 手动记账给出建议、导入账单与自动记账的“待确认”直接套用。模板：常记的一笔，记一笔面板顶部一排，点一下就记。

@Immutable
data class RuleRowUi(val rule: LedgerRule, val title: String, val subtitle: String)

@Immutable
data class TemplateRowUi(val template: LedgerTemplate, val title: String, val subtitle: String, val amount: String)

@Immutable
data class RulesUi(val rules: List<RuleRowUi>, val templates: List<TemplateRowUi>)

fun buildRulesUi(data: LedgerData): RulesUi {
    val categories = data.categories.associateBy { it.id }
    val accounts = data.accounts.associateBy { it.id }
    val rules = data.rules.sortedBy { it.position }.map { r ->
        val target = listOfNotNull(
            r.categoryId?.let { categoryLabel(it, categories) },
            r.accountId?.let { accounts[it]?.name },
            r.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
        ).joinToString(" · ")
        RuleRowUi(r, r.keywords.joinToString(" / "), listOfNotNull(r.summary.substringAfter(" · ", "").takeIf { it.isNotEmpty() }, "→ $target").joinToString("　"))
    }
    val templates = data.templates.sortedBy { it.position }.map { t ->
        val where = when (t.type) {
            TxType.TRANSFER -> "${t.accountId?.let { accounts[it]?.name } ?: "转出账户"} → ${t.toAccountId?.let { accounts[it]?.name } ?: "转入账户"}"
            else -> listOfNotNull(t.categoryId?.let { categoryLabel(it, categories) }, t.accountId?.let { accounts[it]?.name }).joinToString(" · ").ifEmpty { "默认分类与账户" }
        }
        TemplateRowUi(
            t, t.name,
            listOf(t.type.label, where, t.note).filter { it.isNotBlank() }.joinToString("　"),
            t.amount?.let { Money.format(it) } ?: "每次输入",
        )
    }
    return RulesUi(rules, templates)
}

class RulesActions(
    val onBack: () -> Unit = {},
    val onAddRule: () -> Unit = {},
    val onEditRule: (LedgerRule) -> Unit = {},
    val onToggleRule: (LedgerRule, Boolean) -> Unit = { _, _ -> },
    val onMoveRule: (LedgerRule, Int) -> Unit = { _, _ -> },
    val onAddTemplate: () -> Unit = {},
    val onEditTemplate: (LedgerTemplate) -> Unit = {},
)

enum class RulesTab(val label: String) { RULES("分类规则"), TEMPLATES("记账模板") }

@Composable
fun LedgerRulesScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val data by container.ledger.data.collectAsStateWithLifecycle()
    val ui = remember(data) { buildRulesUi(data) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(RulesTab.RULES) }
    var editingRule by remember { mutableStateOf<LedgerRule?>(null) }
    var editingTemplate by remember { mutableStateOf<LedgerTemplate?>(null) }
    fun run(done: String?, after: () -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                after()
                done?.let(container.messages::show)
            } catch (e: LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }
    RulesContent(
        ui, tab, { tab = it },
        RulesActions(
            onBack = onBack,
            onAddRule = { editingRule = LedgerRule(LedgerIds.newId(), emptyList(), type = TxType.EXPENSE) },
            onEditRule = { editingRule = it },
            onToggleRule = { r, on -> run(null) { container.ledger.saveRule(r.copy(enabled = on)) } },
            onMoveRule = { r, delta ->
                val ids = data.rules.sortedBy { it.position }.map { it.id }.toMutableList()
                val i = ids.indexOf(r.id)
                val j = i + delta
                if (i >= 0 && j in ids.indices) {
                    ids.add(j, ids.removeAt(i))
                    run(null) { container.ledger.reorderRules(ids) }
                }
            },
            onAddTemplate = { editingTemplate = LedgerTemplate(LedgerIds.newId(), "") },
            onEditTemplate = { editingTemplate = it },
        ),
    )
    editingRule?.let { rule ->
        RuleEditSheet(
            rule = rule, data = data, isNew = data.rules.none { it.id == rule.id },
            onDismiss = { editingRule = null },
            onSave = { r -> run("已保存规则", after = { editingRule = null }) { container.ledger.saveRule(r) } },
            onDelete = {
                run(null, after = { editingRule = null }) {
                    container.ledger.deleteRule(rule.id)
                    container.messages.show("已删除规则", "撤销") { container.scope.launch { runCatching { container.ledger.restoreRule(rule) } } }
                }
            },
        )
    }
    editingTemplate?.let { t ->
        TemplateEditSheet(
            template = t, data = data, isNew = data.templates.none { it.id == t.id },
            onDismiss = { editingTemplate = null },
            onSave = { v -> run("已保存模板", after = { editingTemplate = null }) { container.ledger.saveTemplate(v) } },
            onDelete = {
                run(null, after = { editingTemplate = null }) {
                    container.ledger.deleteRule(t.id)
                    container.messages.show("已删除模板", "撤销") { container.scope.launch { runCatching { container.ledger.restoreTemplate(t) } } }
                }
            },
        )
    }
}

/** 规则与模板（无状态）：顶部说明卡 → 分段（分类规则 / 记账模板）→ 列表 → 添加。 */
@Composable
fun RulesContent(ui: RulesUi, tab: RulesTab, onTab: (RulesTab) -> Unit, actions: RulesActions) {
    val colors = Jk.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        JkTopBar("规则与模板", onBack = actions.onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Space.x2)
                .padding(bottom = Space.x4),
        ) {
            BotanicalCard(Modifier.fillMaxWidth(), layout = BotanicalLayout.SPRIG) {
                Column(Modifier.padding(Space.x2_5)) {
                    Text(
                        if (tab == RulesTab.RULES) "${ui.rules.size} 条分类规则" else "${ui.templates.size} 个记账模板",
                        style = JkType.title.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(Space.x05))
                    Text(
                        if (tab == RulesTab.RULES) {
                            "备注或商户里出现关键词，就归到指定分类。手动记账时给出建议，导入账单与自动记账直接套用；从上往下，第一条命中的生效。"
                        } else {
                            "常记的一笔放在“记一笔”面板顶部：带金额的点一下直接记下，不带金额的填好分类与账户，再输金额。"
                        },
                        style = JkType.small,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(Space.x2))
            JkSegmented(RulesTab.entries.map { it to it.label }, tab, onTab)
            Spacer(Modifier.height(Space.x1_5))
            when (tab) {
                RulesTab.RULES -> {
                    JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                        if (ui.rules.isEmpty()) {
                            EmptyHint("还没有规则。例如：关键词“美团 饿了么”→ 餐饮 · 外卖。")
                        }
                        ui.rules.forEachIndexed { i, row ->
                            if (i > 0) JkDivider(inset = Space.x2)
                            RuleRow(row, first = i == 0, last = i == ui.rules.lastIndex, actions)
                        }
                    }
                    Spacer(Modifier.height(Space.x1_5))
                    JkButton("添加规则", actions.onAddRule, icon = Icons.Outlined.AutoAwesome, modifier = Modifier.fillMaxWidth())
                }
                RulesTab.TEMPLATES -> {
                    JkCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = Space.x05)) {
                        if (ui.templates.isEmpty()) {
                            EmptyHint("还没有模板。例如：早餐 ¥8.00、地铁 ¥4.00、房租（每次输入）。")
                        }
                        ui.templates.forEachIndexed { i, row ->
                            if (i > 0) JkDivider(inset = Space.x2)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .clickable(role = Role.Button, onClickLabel = "编辑模板${row.title}") { actions.onEditTemplate(row.template) }
                                    .padding(horizontal = Space.x2, vertical = Space.x1),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.title, style = JkType.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(row.subtitle, style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(Space.x1))
                                Text(
                                    row.amount,
                                    style = if (row.template.amount != null) JkType.amountSmall else JkType.caption,
                                    color = if (row.template.amount != null) amountColor(row.template.type) else colors.textTertiary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.x1_5))
                    JkButton(
                        "添加模板", actions.onAddTemplate, icon = Icons.Outlined.Bolt,
                        enabled = ui.templates.size < RuleMath.MAX_TEMPLATES, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = JkType.small, color = Jk.colors.textTertiary, modifier = Modifier.padding(Space.x2))
}

@Composable
private fun RuleRow(row: RuleRowUi, first: Boolean, last: Boolean, actions: RulesActions) {
    val colors = Jk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "编辑规则") { actions.onEditRule(row.rule) }
            .padding(start = Space.x2, end = Space.x1, top = Space.x1, bottom = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = JkType.bodyStrong,
                color = if (row.rule.enabled) colors.textPrimary else colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(row.subtitle, style = JkType.caption, color = colors.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!first) JkIconButton(Icons.Outlined.KeyboardArrowUp, "上移", { actions.onMoveRule(row.rule, -1) })
        if (!last) JkIconButton(Icons.Outlined.KeyboardArrowDown, "下移", { actions.onMoveRule(row.rule, 1) })
        JkSwitch(row.rule.enabled, { actions.onToggleRule(row.rule, it) })
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = JkType.small,
        color = Jk.colors.textSecondary,
        modifier = Modifier.padding(start = Space.x05, top = Space.x05).semantics { heading() },
    )
}

/** 选分类的一行：点开分类选择。 */
@Composable
private fun CategoryField(label: String, data: LedgerData, categoryId: String?, onClick: () -> Unit) {
    val byId = remember(data.categories) { data.categories.associateBy { it.id } }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(Radius.control)
            .clickable(role = Role.Button, onClickLabel = "选择$label", onClick = onClick)
            .padding(horizontal = Space.x05),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = JkType.body, color = Jk.colors.textSecondary, modifier = Modifier.width(88.dp))
        Text(
            categoryId?.let { categoryLabel(it, byId) } ?: "不改分类",
            style = JkType.body,
            color = Jk.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = JkType.body, color = Jk.colors.textTertiary)
    }
}

/** 账户：一行标签，第一个是“不指定”。 */
@Composable
private fun OptionalAccountChips(accounts: List<LedgerAccount>, selected: String?, noneLabel: String, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
        item(key = "none", contentType = "none") { JkChip(noneLabel, selected == null, { onSelect(null) }) }
        items(accounts, key = { it.id }, contentType = { "account" }) { a -> JkChip(a.name, a.id == selected, { onSelect(a.id) }) }
    }
}

@Composable
fun RuleEditSheet(
    rule: LedgerRule,
    data: LedgerData,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (LedgerRule) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Jk.colors
    val accounts = remember(data.accounts) { data.accounts.filter { !it.archived && it.kind.countsInAssets }.sortedBy { it.position } }
    var keywords by remember { mutableStateOf(rule.keywords.joinToString(" ")) }
    var type by remember { mutableStateOf(rule.type) }
    var min by remember { mutableStateOf(rule.minAmount?.let(Money::plain) ?: "") }
    var max by remember { mutableStateOf(rule.maxAmount?.let(Money::plain) ?: "") }
    var categoryId by remember { mutableStateOf(rule.categoryId) }
    var accountId by remember { mutableStateOf(rule.accountId) }
    var tags by remember { mutableStateOf(rule.tags.joinToString(" ")) }
    var enabled by remember { mutableStateOf(rule.enabled) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val categoryType = categoryId?.let { id -> data.categories.firstOrNull { it.id == id }?.type }

    JkSheet(onDismiss = onDismiss, title = if (isNew) "添加分类规则" else "编辑分类规则") {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkTextField(
                value = keywords, onValueChange = { keywords = it.take(200); error = null },
                label = "关键词", placeholder = "例如：美团 饿了么（空格或逗号分隔）",
            )
            SheetLabel("对哪类生效")
            JkSegmented(
                listOf<Pair<TxType?, String>>(TxType.EXPENSE to "支出", TxType.INCOME to "收入", null to "都可以"),
                type,
                { picked ->
                    type = picked
                    if (picked != null && categoryType != null && categoryType != picked) categoryId = null
                    error = null
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkTextField(min, { min = it.take(14); error = null }, Modifier.weight(1f), label = "最低金额（可选）", placeholder = "不限", keyboardType = KeyboardType.Decimal)
                JkTextField(max, { max = it.take(14); error = null }, Modifier.weight(1f), label = "最高金额（可选）", placeholder = "不限", keyboardType = KeyboardType.Decimal)
            }
            SheetLabel("命中后")
            CategoryField("归到分类", data, categoryId) { pickCategory = true }
            Text("换成账户（可选）", style = JkType.small, color = colors.textSecondary, modifier = Modifier.padding(start = Space.x05))
            OptionalAccountChips(accounts, accountId, "不换账户") { accountId = it; error = null }
            JkTextField(tags, { tags = it.take(120); error = null }, label = "加标签（可选）", placeholder = "例如：外卖")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Space.x05)) {
                Column(Modifier.weight(1f)) {
                    Text("启用", style = JkType.body, color = colors.textPrimary)
                    Text("停用的规则保留，但不再生效", style = JkType.caption, color = colors.textTertiary)
                }
                JkSwitch(enabled, { enabled = it })
            }
            error?.let { Text(it, style = JkType.small, color = colors.danger, modifier = Modifier.padding(start = Space.x05)) }
        }
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) JkIconButton(Icons.Outlined.DeleteOutline, "删除这条规则", { confirmDelete = true }, tint = colors.danger)
            JkButton("保存", {
                val minCents = if (min.isBlank()) null else Money.parse(min)
                val maxCents = if (max.isBlank()) null else Money.parse(max)
                if ((min.isNotBlank() && minCents == null) || (max.isNotBlank() && maxCents == null)) {
                    error = "金额格式不对"
                    return@JkButton
                }
                val draft = rule.copy(
                    keywords = RuleMath.parseKeywords(keywords),
                    type = type,
                    minAmount = minCents,
                    maxAmount = maxCents,
                    categoryId = categoryId,
                    accountId = accountId,
                    tags = LedgerMath.parseTags(tags),
                    enabled = enabled,
                )
                error = draft.problem()
                if (error == null) onSave(draft)
            }, modifier = Modifier.weight(1f))
        }
    }
    if (pickCategory) {
        CategoryPickerSheet(
            categories = data.categories,
            title = "归到分类",
            type = type,
            onDismiss = { pickCategory = false },
            onPick = { id ->
                if (id != null) {
                    categoryId = id
                    // “都可以”时按所选分类定下类型，避免收入规则指向支出分类
                    if (type == null) type = data.categories.firstOrNull { it.id == id }?.type
                    error = null
                }
                pickCategory = false
            },
        )
    }
    if (confirmDelete) {
        JkDialog(
            title = "删除这条规则？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        ) {
            Text("已经记下的流水不受影响。", style = JkType.body)
        }
    }
}

@Composable
fun TemplateEditSheet(
    template: LedgerTemplate,
    data: LedgerData,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (LedgerTemplate) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Jk.colors
    val active = remember(data.accounts) { data.accounts.filter { !it.archived }.sortedBy { it.position } }
    var name by remember { mutableStateOf(template.name) }
    var type by remember { mutableStateOf(template.type) }
    var amount by remember { mutableStateOf(template.amount?.let(Money::plain) ?: "") }
    var categoryId by remember { mutableStateOf(template.categoryId) }
    var accountId by remember { mutableStateOf(template.accountId) }
    var toAccountId by remember { mutableStateOf(template.toAccountId) }
    var note by remember { mutableStateOf(template.note) }
    var tags by remember { mutableStateOf(template.tags.joinToString(" ")) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    JkSheet(onDismiss = onDismiss, title = if (isNew) "添加记账模板" else "编辑记账模板") {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkTextField(name, { name = it.take(12); error = null }, label = "名称", placeholder = "例如：早餐")
            JkSegmented(
                TxType.entries.map { it to it.label },
                type,
                { picked ->
                    type = picked
                    if (categoryId?.let { id -> data.categories.firstOrNull { it.id == id }?.type } != picked) categoryId = null
                    error = null
                },
            )
            JkTextField(
                amount, { amount = it.take(14); error = null }, label = "金额（可选）",
                placeholder = "不填则每次输入", keyboardType = KeyboardType.Decimal,
            )
            if (type == TxType.TRANSFER) {
                SheetLabel("转出账户")
                OptionalAccountChips(active, accountId, "未选") { accountId = it; error = null }
                SheetLabel("转入账户")
                OptionalAccountChips(active.filter { it.id != accountId }, toAccountId, "未选") { toAccountId = it; error = null }
            } else {
                CategoryField("分类", data, categoryId) { pickCategory = true }
                SheetLabel("账户")
                OptionalAccountChips(active, accountId, "默认账户") { accountId = it; error = null }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                JkTextField(note, { note = it.take(500) }, Modifier.weight(3f), placeholder = "备注（可选）")
                JkTextField(tags, { tags = it.take(120) }, Modifier.weight(2f), placeholder = "标签")
            }
            error?.let { Text(it, style = JkType.small, color = colors.danger, modifier = Modifier.padding(start = Space.x05)) }
        }
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) JkIconButton(Icons.Outlined.DeleteOutline, "删除这个模板", { confirmDelete = true }, tint = colors.danger)
            JkButton("保存", {
                val cents = if (amount.isBlank()) null else Money.parse(amount)
                if (amount.isNotBlank() && (cents == null || cents <= 0)) {
                    error = "请输入大于 0 的金额，或留空"
                    return@JkButton
                }
                val draft = template.copy(
                    name = name.trim(),
                    type = type,
                    amount = cents,
                    categoryId = categoryId.takeIf { type != TxType.TRANSFER },
                    accountId = accountId,
                    toAccountId = toAccountId.takeIf { type == TxType.TRANSFER },
                    note = note.trim(),
                    tags = LedgerMath.parseTags(tags),
                )
                error = draft.problem()
                if (error == null) onSave(draft)
            }, modifier = Modifier.weight(1f))
        }
    }
    if (pickCategory) {
        CategoryPickerSheet(
            categories = data.categories,
            title = "选择分类",
            type = type,
            onDismiss = { pickCategory = false },
            onPick = { id ->
                if (id != null) categoryId = id
                pickCategory = false
            },
        )
    }
    if (confirmDelete) {
        JkDialog(
            title = "删除这个模板？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        ) {
            Text("用这个模板记下的流水保留。", style = JkType.body)
        }
    }
}

// ── 记一笔面板里的模板与规则建议 ─────────────────────────────────

/** 记一笔面板顶部的一排模板：带金额的显示金额（点一下直接记下），不带的显示“…”。 */
@Composable
internal fun TemplateChips(templates: List<LedgerTemplate>, onPick: (LedgerTemplate) -> Unit) {
    val colors = Jk.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        item(key = "label", contentType = "label") {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
        items(templates, key = { it.id }, contentType = { "template" }) { t ->
            Row(
                Modifier
                    .heightIn(min = JkSize.touch)
                    .clip(Radius.pill)
                    .clickable(role = Role.Button, onClickLabel = if (t.amount != null) "直接记一笔${t.name}" else "按模板${t.name}填写") { onPick(t) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .jkSurface(Jk.surfaces.chip, Radius.pill)
                        .padding(horizontal = Space.x1_5, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, style = JkType.small.copy(fontWeight = FontWeight.Medium), color = colors.textPrimary, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Text(t.amount?.let { Money.format(it) } ?: "…", style = JkType.caption, color = if (t.amount != null) amountColor(t.type) else colors.textTertiary, maxLines = 1)
                }
            }
        }
    }
}

/** 规则建议：“按规则「美团」：餐饮 · 外卖　套用”。 */
@Composable
internal fun RuleSuggestion(text: String, keyword: String, onApply: () -> Unit) {
    val colors = Jk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.x1)
            .jkSurface(Jk.surfaces.chip, Radius.control)
            .padding(start = Space.x1_5, end = Space.x05),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.x1))
        Text(
            "按规则「$keyword」：$text",
            style = JkType.small,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        JkButton("套用", onApply, style = ButtonStyle.Ghost, compact = true)
    }
}
