package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.DebtChange
import com.xiguli.jinkui.data.ledger.DebtEdit
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.DebtRecord
import com.xiguli.jinkui.data.ledger.DebtRecordEdit
import com.xiguli.jinkui.data.ledger.DebtRecordType
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.PersonDebt
import com.xiguli.jinkui.data.ledger.PersonEdit
import com.xiguli.jinkui.ui.LocalContainer
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkDialog
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.theme.jkSurface
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Radius
import com.xiguli.jinkui.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

// 1.5.0-alpha03：借贷记录、对方都能直接改（参考一木记账、钱迹的“编辑借贷记录 / 联系人”，只借鉴交互）。
// 面板都是无状态的：数据与回调由外层给，界面测试直接渲染。

private fun cents(text: String): Long? = if (text.isBlank()) 0L else Money.parse(text.trim())

/** 一条记录在来往记录 / 面板里的叫法：“借出”“收款”“结清（不再收回）”“修改欠款”。 */
fun debtRecordLabel(record: DebtRecord): String = when {
    record.correction -> PersonEdit.CORRECTION_NOTE
    record.type == DebtRecordType.WRITE_OFF -> if (record.direction == LoanDirection.LEND) "坏账" else "免还"
    else -> record.type.label
}

/** 时间（几点几分）：点开系统时间选择。 */
@Composable
fun TimeField(label: String, time: Long, zone: ZoneId, onPick: (Long) -> Unit) {
    val colors = Jk.colors
    var open by remember { mutableStateOf(false) }
    val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .jkSurface(Jk.surfaces.control, Radius.field)
            .clickable(role = Role.Button, onClickLabel = "选择$label") { open = true }
            .padding(start = Space.x2, end = Space.x1_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.x1_5))
        Text(label, style = JkType.small, color = colors.textSecondary)
        Spacer(Modifier.width(Space.x1))
        Text("%02d:%02d".format(clock.hour, clock.minute), style = JkType.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = clock.hour, initialMinute = clock.minute, is24Hour = true)
        JkDialog(title = "选择时间", onDismiss = { open = false }, confirmText = "确定", onConfirm = {
            val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
            onPick(date.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli())
            open = false
        }) { TimePicker(state = state) }
    }
}

/**
 * 改一条借贷记录：类型（借出 / 借入 / 收款 / 还款，可以换）、对方（选已有的或写新名字）、金额、利息、日期与时间、
 * 经过哪个账户（可以不经过）、预计还款日（借出 / 借入时）、备注；下面“删除”“保存”。
 */
@Composable
fun DebtRecordSheet(
    record: DebtRecord,
    /** 全部对方账户（两个方向都有，按类型筛）。 */
    people: List<LedgerAccount>,
    funds: List<LedgerAccount>,
    balances: Map<String, Long>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (DebtRecordEdit) -> Unit,
    onDelete: () -> Unit,
    /** 删这条时会一起删掉的其他记录条数（借出 / 借入名下的收款 / 还款）。 */
    relatedCount: Int = 0,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = Jk.colors
    val writeOff = record.type == DebtRecordType.WRITE_OFF
    var type by remember { mutableStateOf(record.type) }
    var personId by remember { mutableStateOf<String?>(record.person.id) }
    var person by remember { mutableStateOf(DebtMath.personOf(record.person)) }
    var amount by remember { mutableStateOf(record.amount.takeIf { it > 0 }?.let(Money::plain) ?: "") }
    var interest by remember { mutableStateOf(record.interest.takeIf { it > 0 }?.let(Money::plain) ?: "") }
    var time by remember { mutableLongStateOf(record.at) }
    var fund by remember { mutableStateOf(record.fundId) }
    var due by remember { mutableStateOf(record.due) }
    var note by remember { mutableStateOf(record.note) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 同随手记：已经收（还）过钱的借出 / 借入不能换人、换类型（防止账对不上），同一个人两个名字用“合并”
    val locked = record.ownsLoan && relatedCount > 0
    val direction = if (writeOff) record.direction else type.direction
    val lend = direction == LoanDirection.LEND
    val known = people.filter { LoanDirection.of(it.kind) == direction && (!it.archived || it.id == personId) }
    // 当前用的账户停用了也要列出来（否则看不到选的是哪个）
    val fundList = funds
    val date = LedgerMath.dateOf(time, zone)
    JkSheet(onDismiss = onDismiss, title = "编辑${debtRecordLabel(record)}") {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
            if (writeOff) {
                Text(
                    if (lend) "收不回了：这部分记成一笔“其他支出”（坏账）" else "对方说不用还了：这部分记成一笔“其他收入”（免还）",
                    style = JkType.small,
                    color = colors.textSecondary,
                )
            } else if (locked) {
                Text(
                    "${record.type.label} · ${DebtMath.personOf(record.person)}",
                    style = JkType.bodyStrong,
                    color = colors.textPrimary,
                )
                Text(
                    "已经${if (lend) "收" else "还"}过钱（$relatedCount 条），类型和对方不能改；同一个人记成了两个名字，可以在人页右上角“编辑 → 合并到另一个人”。",
                    style = JkType.caption,
                    color = colors.textTertiary,
                )
            } else {
                JkSegmented(
                    listOf(DebtRecordType.LEND to "借出", DebtRecordType.BORROW to "借入", DebtRecordType.COLLECT to "收款", DebtRecordType.REPAY to "还款"),
                    type,
                    {
                        if (it.direction != type.direction) {
                            // 换了一边：同名的人在另一边另有账户，按名字重新找
                            personId = null
                        }
                        type = it
                        error = null
                    },
                )
                Text(
                    when (type) {
                        DebtRecordType.LEND -> "借出 = 我把钱借给别人（别人欠我）"
                        DebtRecordType.BORROW -> "借入 = 我向别人借钱（我欠别人）"
                        DebtRecordType.COLLECT -> "收款 = 别人还我钱"
                        else -> "还款 = 我还别人钱"
                    },
                    style = JkType.caption,
                    color = colors.textTertiary,
                )
            }
            if (!locked) Text("对方", style = JkType.small.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
            if (!locked && known.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                    for (a in known) {
                        val name = DebtMath.personOf(a)
                        JkChip(name, personId == a.id || (personId == null && person.trim() == name), {
                            personId = a.id
                            person = name
                            error = null
                        })
                    }
                }
            }
            if (!locked) JkTextField(value = person, onValueChange = { person = it.take(20); personId = null; error = null }, label = "对方名字（选上面的或写新名字）", placeholder = "例如：小李")
            JkTextField(
                value = amount,
                onValueChange = { amount = it.take(14); error = null },
                label = when {
                    writeOff -> if (lend) "收不回的金额" else "不用还的金额"
                    type.principal -> if (lend) "借出多少" else "借入多少"
                    else -> if (lend) "收回多少" else "还了多少"
                },
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal,
            )
            if (!writeOff) {
                JkTextField(
                    value = interest,
                    onValueChange = { interest = it.take(14); error = null },
                    label = if (type.principal) "约定利息（可选）" else "另付利息（可选）",
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Decimal,
                )
            }
            DateField("日期", date, today, { picked ->
                val clock = Instant.ofEpochMilli(time).atZone(zone).toLocalTime()
                time = picked.atTime(clock).atZone(zone).toInstant().toEpochMilli()
            })
            TimeField("时间", time, zone) { time = it }
            if (!writeOff) {
                Text(
                    when {
                        type == DebtRecordType.LEND || type == DebtRecordType.REPAY -> "钱从哪个账户出"
                        else -> "钱进哪个账户"
                    },
                    style = JkType.small.copy(fontWeight = FontWeight.Medium),
                    color = colors.textSecondary,
                )
                LoanFundChips(fundList, fund, balances) { fund = it }
                if (fund == null) {
                    Text(
                        if (type.principal) noFundHint(LoanAction.LEND) else noFundHint(LoanAction.COLLECT),
                        style = JkType.caption,
                        color = colors.textTertiary,
                    )
                }
            }
            if (type.principal) DateField("预计还款日（可选）", due, today, { due = it }, onClear = { due = null })
            JkTextField(value = note, onValueChange = { note = it.take(200) }, label = "备注（可选）", singleLine = false)
            if (!record.loan.stored) Text("这条是导入的记录，改了以后照样按对方账户上的流水算。", style = JkType.caption, color = colors.textTertiary)
            error?.let { Text(it, style = JkType.small, color = colors.danger) }
        }
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            JkButton("删除", { confirmDelete = true }, style = ButtonStyle.Danger, modifier = Modifier.weight(1f))
            JkButton("保存", {
                val a = cents(amount)
                val i = if (writeOff) 0L else cents(interest)
                error = when {
                    a == null || i == null -> "金额格式不对，最多两位小数"
                    person.isBlank() && personId == null -> "请填写对方"
                    (type.principal || writeOff) && a <= 0 -> "请输入大于 0 的金额"
                    a <= 0 && i <= 0 -> "请输入大于 0 的金额"
                    type.principal && due != null && due!! < date -> "预计还款日不能早于借款日期"
                    else -> null
                }
                if (error == null) {
                    onSave(DebtRecordEdit(type, personId, person.trim(), a!!, i!!, time, fund, if (type.principal) due else null, note.trim()))
                }
            }, modifier = Modifier.weight(2f))
        }
    }
    if (confirmDelete) {
        JkDialog(
            title = "删除这条${debtRecordLabel(record)}？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        ) {
            Text(
                buildString {
                    append("${Money.format(record.amount.takeIf { it > 0 } ?: record.interest)}，删除后欠款与账户余额随之恢复。")
                    if (relatedCount > 0) append("这笔${record.type.label}下面的 $relatedCount 条收款 / 还款会一起删除。")
                    append("删除后几秒内可以点“撤销”恢复。")
                },
                style = JkType.body,
            )
        }
    }
}

/** 编辑这个人：改名，或合并到另一个人（全部记录挪过去）。 */
@Composable
fun PersonEditSheet(
    person: PersonDebt,
    /** 同一边的其他人（可以合并到的）。 */
    others: List<LedgerAccount>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onMerge: (intoId: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colors = Jk.colors
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(person.person) }
    var error by remember { mutableStateOf<String?>(null) }
    var mergeInto by remember { mutableStateOf<LedgerAccount?>(null) }
    JkSheet(onDismiss = onDismiss, title = "编辑 ${person.person}") {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.x1_5)) {
            JkTextField(value = name, onValueChange = { name = it.take(20); error = null }, label = "名字", placeholder = "例如：小李", error = error)
            JkButton("保存名字", {
                if (name.isBlank()) error = "请填写名字" else onRename(name.trim())
            }, enabled = name.trim() != person.person, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.x05))
            Text("合并到另一个人", style = JkType.bodyStrong, color = colors.textPrimary)
            Text(
                "同一个人记成了两个名字时用：${person.person}的全部记录都挪到选的那个人名下，${person.person}这个名字不再出现。",
                style = JkType.caption,
                color = colors.textTertiary,
            )
            if (others.isEmpty()) {
                Text(if (person.direction == LoanDirection.LEND) "没有别的欠你钱的人可以合并。" else "没有别的你欠钱的人可以合并。", style = JkType.small, color = colors.textTertiary)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                    for (a in others) JkChip(DebtMath.personOf(a), false, { mergeInto = a })
                }
            }
            if (onDelete != null) {
                Spacer(Modifier.height(Space.x05))
                JkButton("删除${person.person}和全部记录", { confirmDelete = true }, style = ButtonStyle.Danger, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        JkDialog(
            title = "删除${person.person}？",
            onDismiss = { confirmDelete = false },
            confirmText = "删除",
            onConfirm = { confirmDelete = false; onDelete() },
        ) {
            Text("${person.person}的 ${person.entries.size} 条记录都会删除，涉及的账户余额会改回去。删除后几秒内可以点“撤销”。", style = JkType.body)
        }
    }
    mergeInto?.let { into ->
        val target = DebtMath.personOf(into)
        JkDialog(
            title = "把${person.person}合并到$target？",
            onDismiss = { mergeInto = null },
            confirmText = "合并",
            onConfirm = {
                mergeInto = null
                onMerge(into.id)
            },
        ) {
            Text("${person.person}的 ${person.entries.size} 条记录都会挪到${target}名下，两人欠的钱加在一起。合并后几秒内可以点“撤销”。", style = JkType.body)
        }
    }
}

/**
 * 修改欠款（与账户的“修改余额”同一种）：直接填现在还欠多少。差额记一笔“修改欠款”，不计入收支、不改任何账户的余额。
 */
@Composable
fun PersonOwedSheet(
    person: PersonDebt,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (target: Long, date: LocalDate) -> Unit,
) {
    val colors = Jk.colors
    val lend = person.direction == LoanDirection.LEND
    var text by remember { mutableStateOf(Money.plain(person.remaining)) }
    var date by remember { mutableStateOf(today) }
    var error by remember { mutableStateOf<String?>(null) }
    val target = Money.parse(text.trim().ifEmpty { "0" })
    val delta = target?.let { it - person.remaining }
    JkSheet(onDismiss = onDismiss, title = "修改欠款") {
        Text(
            (if (lend) "${person.person}现在还欠我 " else "我现在还欠${person.person} ") + Money.format(person.remaining),
            style = JkType.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(Space.x1_5))
        JkTextField(
            value = text,
            onValueChange = { text = it.take(16); error = null },
            label = if (lend) "改成他还欠我多少（元）" else "改成我还欠他多少（元）",
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            error = error,
        )
        Spacer(Modifier.height(Space.x1))
        Text(
            when {
                delta == null || target < 0 -> "金额格式不对，最多两位小数"
                delta == 0L -> "和现在一样，不用改"
                else -> "将记一笔修改欠款 ${if (delta > 0) "+" else "-"}${Money.format(kotlin.math.abs(delta))}"
            },
            style = JkType.small,
            color = if (delta == null) colors.danger else colors.textSecondary,
        )
        Text(
            "只把欠款改对：不计入收入、支出与统计，也不改任何账户的余额。想记成真的收到 / 还了钱，请用“收款 / 还款”。",
            style = JkType.caption,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = Space.x05),
        )
        Spacer(Modifier.height(Space.x1_5))
        DateField("日期", date, today, { date = it })
        Spacer(Modifier.height(Space.x2))
        JkButton("保存", {
            if (target == null || target < 0) error = "金额格式不对，最多两位小数" else onSave(target, date)
        }, enabled = delta != 0L, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * 有状态的外壳：打开 [txId] 所在的借贷记录面板，保存 / 删除走仓库，完成后底部提示可“撤销”。
 * 不是借贷记录（例如已经被删掉）时直接关闭。
 */
@Composable
fun DebtRecordHost(txId: String, data: LedgerData, onClose: () -> Unit) {
    val container = LocalContainer.current
    val zone = remember { ZoneId.systemDefault() }
    val record = remember(data, txId) { DebtEdit.recordOf(data, txId, zone) }
    if (record == null) {
        androidx.compose.runtime.LaunchedEffect(txId) { onClose() }
        return
    }
    val scope = rememberCoroutineScope()
    val balances = remember(data) { LedgerMath.balances(data.accounts, data.transactions) }
    fun run(done: String, block: suspend () -> DebtChange) {
        scope.launch {
            try {
                val undo = block()
                onClose()
                if (undo.isEmpty) container.messages.show(done)
                else container.messages.show(done, "撤销") { container.scope.launch { runCatching { container.ledger.undoDebtChange(undo) } } }
            } catch (e: com.xiguli.jinkui.data.repo.LedgerInputException) {
                container.messages.show(e.message ?: "保存失败")
            } catch (e: Exception) {
                container.messages.show("保存失败：${e.message ?: "未知错误"}")
            }
        }
    }
    DebtRecordSheet(
        record = record,
        people = data.accounts.filter { LoanDirection.of(it.kind) != null },
        // 当前用的账户停用了也列出来（否则看不到选的是哪个）
        funds = DebtMath.loanFunds(data.accounts).let { list ->
            list + listOfNotNull(record.fundId?.takeIf { id -> list.none { it.id == id } }?.let { id -> data.accounts.firstOrNull { it.id == id } })
        },
        balances = balances,
        today = LocalDate.now(zone),
        onDismiss = onClose,
        onSave = { edit -> run("已保存") { container.ledger.editDebtRecord(txId, edit, zone) } },
        onDelete = { run("已删除") { container.ledger.deleteDebtRecord(txId, zone) } },
        relatedCount = DebtEdit.relatedCount(record),
        zone = zone,
    )
}
