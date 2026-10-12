package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.xiguli.jinkui.data.ledger.EntryDefaults
import com.xiguli.jinkui.ui.components.JkBareField
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import com.xiguli.jinkui.ui.theme.JkSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.AmountInput
import com.xiguli.jinkui.data.ledger.KeypadKey
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkIconButton
import com.xiguli.jinkui.ui.components.JkRow
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.components.LocalUserActivity
import com.xiguli.jinkui.ui.components.SecureDialog
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 记一笔 / 改一笔（1.0 重做，参考钱迹 / iCost 的一屏记账）：
 * 最上面是类型切换与大号金额，中间是分类宫格（默认选中第一个），下面一行账户 / 日期 / 时间与备注，
 * 最底下是数字键盘。打开面板 → 输金额 → 点“完成”，不用离开这一屏。
 * 账户默认跟着分类走：选了分类，就用这个分类上一次用的账户（[EntryDefaults.accountFor]）；手动改过账户后不再自动换。
 * 金额用面板自带的数字键盘输入（不经过系统输入法），支持简单加减。
 * [onSave] 的第三个参数在保存成功、需要继续记下一笔时调用，用来清空金额与备注。
 */
@Composable
fun EntrySheet(
    data: LedgerData,
    existing: LedgerTransaction?,
    onDismiss: () -> Unit,
    onSave: (LedgerTransaction, Boolean, () -> Unit) -> Unit,
    onDelete: (LedgerTransaction) -> Unit,
    /** 校验提示；默认走全局消息条。界面测试传入自己的实现，就不需要 AppContainer。 */
    onMessage: ((String) -> Unit)? = null,
    /** 新记一笔时的初始时间（测试用固定值）。 */
    initialTime: Long? = null,
    /** 新记一笔时预选的分类（首页“快速记一笔”）。 */
    initialCategory: String? = null,
    /** “再记一笔”：照这一笔预填类型、金额、分类、账户、备注与标签；ID 与时间是新的。 */
    template: LedgerTransaction? = null,
    /**
     * 1.4.0-alpha04：借贷（借出 / 借入 / 收款 / 还款）。为 null 时不显示“借贷”（例如修改已有流水）。
     */
    onLoan: ((LoanEntryRequest) -> Unit)? = null,
    /** 打开时直接进入借贷的某一类（首页“借出 / 借入”快捷入口）。 */
    initialLoan: LoanAction? = null,
    /** 1.5.0-alpha02：从某个账户的详情页记一笔 / 转账时预选的账户（转账时是转出账户）。 */
    initialAccount: String? = null,
    /** 1.5.0-alpha02：打开时的类型（账户详情页的“转账”）。 */
    initialType: TxType? = null,
) {
    val message: (String) -> Unit = onMessage ?: LocalContainer.current.messages::show
    val touch = LocalUserActivity.current
    val colors = Jk.colors
    val zone = remember { ZoneId.systemDefault() }
    val active = remember(data.accounts) { data.accounts.filter { !it.archived } }
    // 1.4.0-alpha04：选账户时显示各账户当前余额（信用卡 / 花呗显示欠款与可用额度）
    val balances = remember(data.accounts, data.transactions) { LedgerMath.balances(data.accounts, data.transactions) }
    val seed = existing ?: template
    val presetType = remember(initialCategory, data.categories) {
        initialCategory?.let { id -> data.categories.firstOrNull { it.id == id }?.type }
    }
    // 新记一笔时默认用这个分类上一次用的账户；没有就用最近一笔的账户
    fun defaultAccount(category: String?): String? =
        EntryDefaults.accountFor(category, data.transactions, data.categories, active)

    fun firstTop(t: TxType): String? =
        data.categories.filter { it.type == t && it.parentId == null && !it.archived }.minByOrNull { it.position }?.id

    var id by remember { mutableStateOf(existing?.id ?: LedgerIds.newId()) }
    var type by remember { mutableStateOf(seed?.type ?: initialType ?: presetType ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(seed?.amount?.let(Money::plain) ?: "") }
    var categoryId by remember { mutableStateOf(seed?.categoryId ?: initialCategory) }
    val presetAccount = initialAccount?.takeIf { id -> seed == null && active.any { it.id == id } }
    var accountId by remember {
        mutableStateOf(presetAccount ?: seed?.accountId?.takeIf { id -> existing != null || active.any { it.id == id } } ?: defaultAccount(seed?.categoryId ?: initialCategory ?: firstTop(seed?.type ?: presetType ?: TxType.EXPENSE)))
    }
    /** 用户手动选过账户后，换分类不再自动改账户。改一笔时也不自动改。从账户详情页进来时也不改。 */
    var accountTouched by remember { mutableStateOf(existing != null || template != null || presetAccount != null) }
    // 转账默认转入另一个账户，避免一打开就是“同一账户转给自己”
    var toAccountId by remember { mutableStateOf(seed?.toAccountId ?: active.firstOrNull { it.id != accountId }?.id) }
    var time by remember { mutableLongStateOf(existing?.occurredAt ?: initialTime ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(seed?.note ?: "") }
    var tags by remember { mutableStateOf(seed?.tags?.joinToString(" ") ?: "") }

    // 借贷（1.4.0-alpha04）：不为 null 时面板处于“借贷”模式
    val loanEnabled = onLoan != null && existing == null
    var loanAction by remember { mutableStateOf(initialLoan?.takeIf { loanEnabled }) }
    // 借贷经过的资金账户：全部在用的资金账户（含信用卡 / 花呗）；null = 不经过账户（只记欠款）。默认是上一次的选择
    val funds = remember(data.accounts) { com.xiguli.jinkui.data.ledger.DebtMath.loanFunds(data.accounts) }
    var loanFund by remember { mutableStateOf(com.xiguli.jinkui.data.ledger.DebtMath.lastLoanFund(data)) }
    val today = remember { LocalDate.now(zone) }
    var loanPerson by remember { mutableStateOf("") }
    var loanTarget by remember { mutableStateOf<LoanTarget?>(null) }
    var loanDue by remember { mutableStateOf<LocalDate?>(null) }
    var loanInterest by remember { mutableStateOf("") }
    val loanTargets = remember(data, loanAction) { loanAction?.let { LoanEntryMath.targets(data, it, today, zone) }.orEmpty() }
    fun enterLoan(action: LoanAction) {
        loanAction = action
        loanTarget = null
    }

    fun pickCategory(newId: String) {
        categoryId = newId
        if (!accountTouched && type != TxType.TRANSFER) defaultAccount(newId)?.let { accountId = it }
    }

    // 1.4.0-alpha04：记账模板。带金额的点一下直接记下；不带金额的填好分类 / 账户 / 备注，再输金额
    val showTemplates = existing == null && loanAction == null && data.templates.isNotEmpty()
    fun useTemplate(t: com.xiguli.jinkui.data.ledger.LedgerTemplate) {
        touch()
        if (t.amount != null) {
            val tx = t.toTransaction(id, time, data.accounts, accountId?.takeIf { a -> active.any { it.id == a } } ?: active.firstOrNull()?.id)
            when {
                tx == null -> message("请先在“账户与余额”里添加一个账户")
                tx.type == TxType.TRANSFER && (tx.toAccountId == null || tx.toAccountId == tx.accountId) -> message("模板里的转入账户不可用，请在“规则与模板”里改一下")
                else -> onSave(if (tx.type != TxType.TRANSFER && tx.categoryId == null) tx.copy(categoryId = firstTop(tx.type)) else tx, false) {}
            }
            return
        }
        type = t.type
        if (t.type != TxType.TRANSFER) t.categoryId?.let { categoryId = it }
        t.accountId?.takeIf { a -> active.any { it.id == a } }?.let {
            accountId = it
            accountTouched = true
        }
        if (t.type == TxType.TRANSFER) t.toAccountId?.takeIf { a -> active.any { it.id == a } }?.let { toAccountId = it }
        if (t.note.isNotBlank()) note = t.note
        if (t.tags.isNotEmpty()) tags = t.tags.joinToString(" ")
        message("已按模板“${t.name}”填好，输入金额即可")
    }

    // 1.4.0-alpha04：自动分类规则——按备注给出建议（有金额时也看金额范围），点一下套用
    val ruleHit = if (loanAction == null && type != TxType.TRANSFER && note.isNotBlank() && data.rules.isNotEmpty()) {
        com.xiguli.jinkui.data.ledger.RuleMath.match(
            data.rules, type, listOf(note), Money.evaluate(amount)?.takeIf { it > 0 }, data.categories, active,
        )
    } else {
        null
    }
    fun applyRule(hit: com.xiguli.jinkui.data.ledger.RuleHit) {
        hit.categoryId?.let { categoryId = it }
        hit.accountId?.let {
            accountId = it
            accountTouched = true
        }
        if (hit.tags.isNotEmpty()) tags = (LedgerMath.parseTags(tags) + hit.tags).distinct().take(12).joinToString(" ")
    }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val byId = remember(data.categories) { data.categories.associateBy { it.id } }
    val accountsById = remember(data.accounts) { data.accounts.associateBy { it.id } }
    val tops = remember(data.categories, type) {
        data.categories.filter { it.type == type && it.parentId == null && !it.archived }.sortedBy { it.position }
    }
    // 当前分类不属于这个类型时（例如刚从支出切到收入），默认选第一个
    val selectedCategory = categoryId?.let(byId::get)?.takeIf { it.type == type }
    val selectedTop = selectedCategory?.let { it.parentId?.let(byId::get) ?: it }
    val children = remember(data.categories, selectedTop?.id) {
        selectedTop?.let { top -> data.categories.filter { it.parentId == top.id && !it.archived }.sortedBy { it.position } }.orEmpty()
    }

    fun press(key: KeypadKey) {
        touch()
        amount = AmountInput.press(amount, key)
    }

    fun submitLoan(action: LoanAction) {
        val cents = if (amount.isBlank()) 0L else Money.evaluate(amount)
        val rate = if (loanInterest.isBlank()) 0L else Money.parse(loanInterest)
        // 选的账户已经停用时退回“不经过账户”
        val fund = loanFund?.takeIf { id -> funds.any { it.id == id } }
        if (cents == null || rate == null) {
            message(if (cents == null) "金额算式不完整" else "利息格式不对")
            return
        }
        val target = loanTarget
        val request = LoanEntryRequest(
            action = action,
            person = (target?.person ?: loanPerson).trim(),
            accountId = target?.accountId,
            loanId = target?.loanId,
            amount = cents,
            fundId = fund,
            date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate(),
            due = loanDue.takeIf { action.principal },
            interest = rate,
            note = note.trim(),
        )
        LoanEntryMath.validate(request, target)?.let {
            message(it)
            return
        }
        onLoan?.invoke(request)
    }

    fun submit(keepOpen: Boolean) {
        loanAction?.let {
            submitLoan(it)
            return
        }
        val cents = Money.evaluate(amount)
        if (cents == null || cents <= 0L) {
            message("请输入大于 0 的金额")
            return
        }
        val from = accountId
        if (from == null) {
            message("请先在“账户与余额”里添加一个账户")
            return
        }
        val tx = LedgerTransaction(
            id = id,
            type = type,
            amount = cents,
            occurredAt = time,
            accountId = from,
            toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
            categoryId = if (type == TxType.TRANSFER) null else (selectedCategory?.id ?: tops.firstOrNull()?.id),
            note = note,
            tags = LedgerMath.parseTags(tags),
            createdAt = existing?.createdAt ?: 0L,
        )
        onSave(tx, keepOpen && existing == null) {
            id = LedgerIds.newId()
            amount = ""
            note = ""
            tags = ""
        }
    }

    JkSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 1.4.0-alpha04：第四个分段“借贷”——借出 / 借入 / 收款 / 还款都在这里记
            JkSegmented(
                options = TxType.entries.map { it.key to it.label } + if (loanEnabled) listOf(LOAN_KEY to "借贷") else emptyList(),
                selected = if (loanAction != null) LOAN_KEY else type.key,
                onSelect = { key ->
                    if (key == LOAN_KEY) {
                        if (loanAction == null) enterLoan(LoanAction.LEND)
                    } else {
                        loanAction = null
                        type = TxType.of(key)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            if (existing != null) {
                JkIconButton(Icons.Outlined.DeleteOutline, "删除这笔", { confirmDelete = true }, tint = colors.danger)
            }
        }

        if (showTemplates) {
            Spacer(Modifier.height(Space.x1))
            TemplateChips(data.templates, onPick = ::useTemplate)
        }

        // 1.5.0-alpha01：类型下面一句白话，说清这一类是什么（转账、借贷不算收支）
        Text(
            entryTypeHint(loanAction != null, type),
            style = JkType.caption,
            color = colors.textTertiary,
            modifier = Modifier.padding(start = Space.x05, top = Space.x05),
        )
        Column(
            Modifier
                .padding(top = Space.x1)
                .heightIn(max = 236.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val loan = loanAction
            if (loan != null) {
                Spacer(Modifier.height(Space.x05))
                LoanActionChips(loan) { enterLoan(it) }
                Spacer(Modifier.height(Space.x1))
                Text(
                    if (loan.tone == TxType.EXPENSE) "钱从哪个账户出" else "钱进哪个账户",
                    style = JkType.small,
                    color = colors.textSecondary,
                )
                LoanFundChips(funds, loanFund?.takeIf { id -> funds.any { it.id == id } }, balances) { loanFund = it }
                if (loanFund?.takeIf { id -> funds.any { it.id == id } } == null) {
                    Text(noFundHint(loan), style = JkType.caption, color = colors.textTertiary)
                }
                Spacer(Modifier.height(Space.x1))
                if (loanTargets.isNotEmpty()) {
                    LoanTargetChips(loanTargets, loanTarget) { t ->
                        loanTarget = t
                        // 收款 / 还款：选一笔就把金额填成还剩的，可以再改成只还一部分
                        if (t != null && !loan.principal) amount = Money.plain(t.remaining)
                    }
                    Spacer(Modifier.height(Space.x1))
                } else if (!loan.principal) {
                    Text(
                        if (loan == LoanAction.COLLECT) "没有未结清的借出。先记一笔“借出”。" else "没有未结清的借入。先记一笔“借入”。",
                        style = JkType.small,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(Space.x1))
                }
                if (loan.principal && loanTarget == null) {
                    CompactField(loanPerson, { loanPerson = it.take(20) }, "对方（例如：小李）", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Space.x1))
                }
                if (loan.principal) {
                    DateField("预计还款日（可选）", loanDue, today, { loanDue = it }, onClear = { loanDue = null })
                    Spacer(Modifier.height(Space.x1))
                }
                CompactField(loanInterest, { loanInterest = it.take(14) }, if (loan.principal) "约定利息（可选）" else "另付利息（可选）", Modifier.fillMaxWidth())
            } else if (type == TxType.TRANSFER) {
                Spacer(Modifier.height(Space.x05))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountChip("转出", accountId?.let(accountsById::get), { pickFrom = true }, Modifier.weight(1f), balances)
                    Text("→", style = JkType.heading, color = colors.textTertiary, modifier = Modifier.padding(horizontal = Space.x1))
                    AccountChip("转入", toAccountId?.let(accountsById::get), { pickTo = true }, Modifier.weight(1f), balances)
                }
            } else {
                // 分类宫格：每行 5 个，圆形图标底 + 名称，选中的填上类型色
                for (rowItems in tops.chunked(5)) {
                    Row(Modifier.fillMaxWidth()) {
                        for (top in rowItems) {
                            val chosen = selectedTop?.id == top.id || (selectedTop == null && top.id == tops.firstOrNull()?.id)
                            CategoryTile(top, chosen, onClick = { pickCategory(top.id) }, modifier = Modifier.weight(1f))
                        }
                        repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (children.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                        items(children, key = { it.id }, contentType = { "entrysheet-row" }) { child ->
                            JkChip(
                                text = child.name,
                                selected = selectedCategory?.id == child.id,
                                onClick = { pickCategory(if (selectedCategory?.id == child.id) child.parentId ?: child.id else child.id) },
                            )
                        }
                    }
                }
            }
        }

        // 信息条：账户、日期、时间；下面是备注与标签
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x1),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            if (type != TxType.TRANSFER && loanAction == null) {
                InfoChip(
                    icon = accountId?.let(accountsById::get)?.kind?.let(::accountIcon) ?: accountIcon(AccountKind.OTHER),
                    text = accountId?.let(accountsById::get)?.name ?: "选账户",
                    onClick = { pickFrom = true },
                    description = "账户",
                )
            }
            val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
            val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
            InfoChip(Icons.Outlined.CalendarMonth, shortDate(date), { showDate = true }, description = "日期")
            InfoChip(Icons.Outlined.Schedule, "%02d:%02d".format(clock.hour, clock.minute), { showTime = true }, description = "时间")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            CompactField(note, { note = it.take(500) }, "添加备注", Modifier.weight(3f))
            CompactField(tags, { tags = it.take(120) }, "#标签", Modifier.weight(2f))
        }
        ruleHit?.let { hit ->
            val catNow = selectedCategory?.id ?: tops.firstOrNull()?.id
            val pendingCategory = hit.categoryId?.takeIf { it != catNow }
            val pendingAccount = hit.accountId?.takeIf { it != accountId }
            val pendingTags = hit.tags.filterNot { it in LedgerMath.parseTags(tags) }
            if (pendingCategory != null || pendingAccount != null || pendingTags.isNotEmpty()) {
                RuleSuggestion(
                    text = listOfNotNull(
                        pendingCategory?.let { categoryLabel(it, byId) },
                        pendingAccount?.let(accountsById::get)?.name,
                        pendingTags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
                    ).joinToString(" · "),
                    keyword = hit.rule.keywords.firstOrNull { note.contains(it, ignoreCase = true) } ?: hit.rule.keywords.first(),
                    onApply = { applyRule(hit) },
                )
            }
        }

        // 1.5.0-alpha01：金额条挪到键盘正上方（参考钱迹、一木记账：先选分类，再输金额）。金额：整块最显眼。左边是这笔的类型与分类，右边等宽数字的大号金额（颜色随类型）
        Spacer(Modifier.height(Space.x1))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radius.control)
                .background(txSoft(loanAction?.tone ?: type).copy(alpha = if (colors.isDark) 0.6f else 0.5f))
                .animateContentSize(tween(200))
                .padding(horizontal = Space.x2, vertical = Space.x1_5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(loanAction?.label ?: type.label, style = JkType.overline, color = txColor(loanAction?.tone ?: type))
                Text(
                    text = loanAction?.let { a -> (loanTarget?.person ?: loanPerson.takeIf { a.principal } ?: "").trim().ifEmpty { if (a.principal) "填写对方" else "选择一笔借贷" } }
                        ?: if (type == TxType.TRANSFER) "账户之间" else (selectedCategory?.let { categoryLabel(it.id, byId) } ?: tops.firstOrNull()?.name ?: "未分类"),
                    style = JkType.small,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥", style = JkType.title, color = colors.textTertiary, modifier = Modifier.padding(end = Space.x05, bottom = 4.dp))
                    Text(
                        text = amount.ifEmpty { "0" },
                        style = JkType.amountHero,
                        color = if (amount.isEmpty()) colors.textTertiary else amountColor(loanAction?.tone ?: type),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.semantics { contentDescription = "金额 ${amount.ifEmpty { "0" }}，用下方键盘输入" },
                    )
                }
                if (AmountInput.hasOperator(amount)) {
                    val value = Money.evaluate(amount)
                    Text(
                        text = if (value != null) "= ${Money.format(value)}" else "算式不完整",
                        style = JkType.amountSmall,
                        color = if (value != null && value > 0) colors.textSecondary else colors.danger,
                    )
                }
            }
        }

        // 数字键盘
        Spacer(Modifier.height(Space.x1))
        Keypad(
            onKey = ::press,
            onAgain = if (existing == null && loanAction == null) ({ submit(keepOpen = true) }) else null,
            onDone = { submit(keepOpen = false) },
        )
    }

    if (pickFrom || pickTo) {
        val picking = if (pickTo) "转入账户" else if (type == TxType.TRANSFER) "转出账户" else "选择账户"
        AccountPickerSheet(
            title = picking,
            // 选转入账户时不列出转出账户本身
            accounts = if (pickTo) active.filter { it.id != accountId } else active,
            balances = balances,
            onDismiss = {
                pickFrom = false
                pickTo = false
            },
            onPick = { account ->
                if (pickTo) {
                    toAccountId = account.id
                } else {
                    // 新的转出账户正好是转入账户时，两者对调，而不是留下一个注定保存失败的组合
                    if (type == TxType.TRANSFER && account.id == toAccountId) toAccountId = accountId
                    accountId = account.id
                    accountTouched = true
                }
                pickFrom = false
                pickTo = false
            },
        )
    }

    if (showDate) {
        val initial = Instant.ofEpochMilli(time).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // 只能选到今天：将来的开销请用“周期记账”。DatePicker 的毫秒是 UTC 零点。
        val todayUtc = LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc || utcTimeMillis == initial
                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now(zone).year || year == LocalDate.ofEpochDay(initial / 86_400_000L).year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            properties = SecureDialog,
            confirmButton = {
                JkButton("确定", {
                    val picked = state.selectedDateMillis
                    if (picked != null) {
                        val date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                        val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
                        time = date.atTime(clock).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }, compact = true, modifier = Modifier.padding(end = Space.x1, bottom = Space.x1))
            },
            dismissButton = {
                JkButton("取消", { showDate = false }, style = ButtonStyle.Ghost, compact = true, modifier = Modifier.padding(bottom = Space.x1))
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }

    if (showTime) {
        val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
        val state = rememberTimePickerState(initialHour = clock.hour, initialMinute = clock.minute, is24Hour = true)
        JkDialog(
            title = "选择时间",
            onDismiss = { showTime = false },
            confirmText = "确定",
            onConfirm = {
                val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                time = date.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli()
                showTime = false
            },
        ) {
            TimePicker(state = state)
        }
    }

    if (confirmDelete && existing != null) {
        JkDialog(
            title = "删除这笔记录？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                onDelete(existing)
            },
        ) {
            Text("删除后账户余额会随之调整，无法撤销。", style = JkType.body)
        }
    }
}

private const val LOAN_KEY = "loan"

/** 1.5.0-alpha01：记一笔面板类型下面的白话说明。 */
internal fun entryTypeHint(loan: Boolean, type: TxType): String = when {
    loan -> "借钱给别人、向别人借钱，以及收回、还上；不算收支"
    type == TxType.EXPENSE -> "花出去的钱"
    type == TxType.INCOME -> "收到的钱，例如工资、红包、退款"
    else -> "自己的账户之间挪钱，例如银行卡转到微信；不算收支"
}

private fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> if (date.year == today.year) "${date.monthValue}月${date.dayOfMonth}日" else "${date.year}/${date.monthValue}/${date.dayOfMonth}"
}

@Composable
private fun CategoryTile(category: LedgerCategory, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    val tone = txColor(category.type)
    val bg by animateColorAsState(if (selected) tone else colors.surfaceAlt, tween(160), label = "tileBg")
    Column(
        modifier = modifier
            .clip(Radius.tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                categoryIcon(category.icon),
                contentDescription = null,
                tint = if (selected) (if (colors.isDark) colors.bg else Color.White) else colors.textSecondary,
                modifier = Modifier.size(JkSize.icon),
            )
        }
        Spacer(Modifier.height(Space.x05))
        Text(
            text = category.name,
            style = if (selected) JkType.caption.copy(fontWeight = FontWeight.Medium) else JkType.caption,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一行的小输入框：浅灰胶囊，不画描边。 */
@Composable
private fun CompactField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = Jk.colors
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(Radius.pill)
            .background(colors.surfaceAlt)
            .padding(horizontal = Space.x2),
        contentAlignment = Alignment.CenterStart,
    ) {
        JkBareField(value = value, onValueChange = onChange, placeholder = placeholder, textStyle = JkType.small, imeAction = ImeAction.Done)
    }
}

@Composable
private fun AccountChip(label: String, account: LedgerAccount?, onClick: () -> Unit, modifier: Modifier = Modifier, balances: Map<String, Long> = emptyMap()) {
    val colors = Jk.colors
    Row(
        modifier = modifier
            .clip(Radius.control)
            .border(1.dp, colors.border, Radius.control)
            .clickable(role = Role.Button, onClickLabel = "选择$label" + "账户", onClick = onClick)
            .padding(horizontal = Space.x1_5, vertical = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (account != null) {
            Icon(accountIcon(account.kind), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.x1))
        }
        Column {
            Text(label, style = JkType.caption, color = colors.textTertiary)
            Text(account?.name ?: "选择账户", style = JkType.body, color = if (account != null) colors.textPrimary else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 1.4.0-alpha04：转出 / 转入下面写上这个账户现在有多少（信用卡写欠多少）
            account?.let { a -> balances[a.id]?.let { b -> Text(accountBalanceText(a, b), style = JkType.caption, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, onClick: () -> Unit, description: String? = null) {
    val colors = Jk.colors
    Row(
        modifier = Modifier
            // 视觉高度 32dp，触控区补足到 48dp
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(Radius.pill)
            .background(colors.surfaceAlt)
            .clickable(role = Role.Button, onClickLabel = description?.let { "选择$it" }, onClick = onClick)
            .padding(horizontal = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.x05))
        Text(text, style = JkType.small, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
fun AccountPickerSheet(
    title: String,
    accounts: List<LedgerAccount>,
    /** 1.4.0-alpha04：各账户当前余额；给了就在每一行右边写余额（信用卡 / 花呗写欠多少，下面写可用额度）。 */
    balances: Map<String, Long> = emptyMap(),
    onDismiss: () -> Unit,
    onPick: (LedgerAccount) -> Unit,
) {
    JkSheet(onDismiss = onDismiss, title = title) {
        if (accounts.isEmpty()) {
            Text("还没有可用的账户，先到“账户与余额”里添加一个。", style = JkType.small, color = Jk.colors.textTertiary)
        }
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            for (account in accounts) {
                val balance = balances[account.id]
                JkRow(
                    title = account.name,
                    subtitle = pickerSubtitle(account, balance),
                    icon = accountIcon(account.kind),
                    modifier = Modifier.clip(Radius.control),
                    trailing = balance?.let { b -> { PickerBalance(account, b) } } ?: {},
                    onClick = { onPick(account) },
                )
            }
        }
    }
}

/** 选账户一行的副标题：种类；信用卡 / 花呗有额度时加上“可用 ¥x”。 */
internal fun pickerSubtitle(account: LedgerAccount, balance: Long?): String {
    val limit = account.credit?.limit ?: 0L
    if (account.kind != AccountKind.CREDIT || balance == null || limit <= 0L) return account.kind.label
    val owed = (-balance).coerceAtLeast(0L)
    return "${account.kind.label} · 可用额度 ${Money.format((limit - owed).coerceAtLeast(0L))}"
}

/** 选账户一行右边的余额：普通账户写余额，信用卡 / 花呗写“欠 ¥x”或“没有欠款”。 */
@Composable
private fun PickerBalance(account: LedgerAccount, balance: Long) {
    val colors = Jk.colors
    if (account.kind == AccountKind.CREDIT) {
        val owed = (-balance).coerceAtLeast(0L)
        Text(
            if (owed > 0) "欠 ${Money.format(owed)}" else "没有欠款",
            style = JkType.amountSmall,
            color = if (owed > 0) colors.expense else colors.textTertiary,
            maxLines = 1,
        )
    } else {
        Text(Money.format(balance), style = JkType.amountSmall, color = if (balance < 0) colors.expense else colors.textPrimary, maxLines = 1)
    }
}

/** 4×4 数字键盘：最后一列是退格、加、减与“完成”。每个键按下有轻微的缩放、变色与触感反馈。 */
@Composable
internal fun Keypad(
    onKey: (KeypadKey) -> Unit,
    onAgain: (() -> Unit)?,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (d in "123") DigitKey(d.toString(), { onKey(KeypadKey.Digit(d)) })
            IconKey(Icons.AutoMirrored.Outlined.Backspace, "退格", { onKey(KeypadKey.Backspace) }, onLongClick = { onKey(KeypadKey.Clear) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (d in "456") DigitKey(d.toString(), { onKey(KeypadKey.Digit(d)) })
            DigitKey("+", { onKey(KeypadKey.Plus) }, subtle = true, description = "加")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            for (d in "789") DigitKey(d.toString(), { onKey(KeypadKey.Digit(d)) })
            DigitKey("−", { onKey(KeypadKey.Minus) }, subtle = true, description = "减")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            DigitKey(".", { onKey(KeypadKey.Dot) }, description = "小数点")
            DigitKey("0", { onKey(KeypadKey.Digit('0')) })
            if (onAgain != null) {
                DigitKey("再记", onAgain, subtle = true, small = true, description = "保存并再记一笔")
            }
            // 没有“再记”（修改已有流水）时，“完成”占两格，不在键盘上留一个空洞
            PressableKey(
                onClick = onDone,
                weight = if (onAgain != null) 1f else 2f,
                background = Jk.colors.primary,
                pressedBackground = Jk.colors.primary.copy(alpha = 0.86f),
                border = null,
                haptic = confirmHaptic(),
                modifier = Modifier.semantics { contentDescription = "保存"; role = Role.Button },
            ) {
                Text("完成", style = JkType.bodyStrong, color = Jk.colors.onPrimary)
            }
        }
    }
}

/** API 30 起才有 CONFIRM；更早的系统退回到长按的触感。 */
private fun confirmHaptic(): Int =
    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS

/** 键盘上的一个键：按下时缩到 96%、底色加深一档，并发出键盘触感（跟随系统触感设置）。 */
@Composable
private fun RowScope.PressableKey(
    onClick: () -> Unit,
    background: Color,
    pressedBackground: Color,
    border: Color?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    haptic: Int = HapticFeedbackConstants.KEYBOARD_TAP,
    weight: Float = 1f,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "keyScale")
    val fill by animateColorAsState(if (pressed) pressedBackground else background, tween(90), label = "keyFill")
    Box(
        modifier = Modifier
            .weight(weight)
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(Radius.control)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, Radius.control) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = onLongClick?.let { long ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        long()
                    }
                },
                onClick = {
                    view.performHapticFeedback(haptic)
                    onClick()
                },
            )
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun RowScope.DigitKey(
    text: String,
    onClick: () -> Unit,
    subtle: Boolean = false,
    small: Boolean = false,
    description: String? = null,
) {
    val colors = Jk.colors
    PressableKey(
        onClick = onClick,
        background = if (subtle) colors.accentSoft else colors.surfaceAlt,
        pressedBackground = colors.border,
        border = null,
        modifier = if (description != null) Modifier.clearAndSetSemantics { contentDescription = description; role = Role.Button } else Modifier,
    ) {
        Text(text, style = if (small) JkType.bodyStrong else JkType.title.copy(fontFeatureSettings = "tnum"), color = colors.textPrimary)
    }
}

@Composable
private fun RowScope.IconKey(icon: ImageVector, description: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = Jk.colors
    PressableKey(
        onClick = onClick,
        onLongClick = onLongClick,
        background = colors.accentSoft,
        pressedBackground = colors.border,
        border = null,
    ) {
        Icon(icon, contentDescription = description, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
    }
}
