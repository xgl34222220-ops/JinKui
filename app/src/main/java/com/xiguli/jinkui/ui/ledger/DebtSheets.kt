package com.xiguli.jinkui.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xiguli.jinkui.data.ledger.CreditSettings
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.DueMode
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.LedgerInstallment
import com.xiguli.jinkui.data.ledger.LedgerLoan
import com.xiguli.jinkui.data.ledger.LoanDirection
import com.xiguli.jinkui.data.ledger.LoanView
import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.ui.components.ButtonStyle
import com.xiguli.jinkui.ui.components.JkButton
import com.xiguli.jinkui.ui.components.JkChip
import com.xiguli.jinkui.ui.components.JkSegmented
import com.xiguli.jinkui.ui.components.JkSheet
import com.xiguli.jinkui.ui.components.JkSwitch
import com.xiguli.jinkui.ui.components.JkTextField
import com.xiguli.jinkui.ui.theme.Jk
import com.xiguli.jinkui.ui.theme.JkType
import com.xiguli.jinkui.ui.theme.Space
import java.time.LocalDate

// 欠款 / 账单（1.4）的底部面板。都是无状态的：数据与提交回调由外层给，界面测试可以直接渲染。

private fun parseCents(text: String): Long? = if (text.isBlank()) null else Money.parse(text)

@Composable
private fun SheetBody(content: @Composable () -> Unit) {
    Column(
        Modifier
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.x1_5),
    ) { content() }
}

@Composable
private fun Hint(text: String, danger: Boolean = false) {
    Text(text, style = JkType.small, color = if (danger) Jk.colors.expense else Jk.colors.textTertiary)
}

// ── 账单设置 ──────────────────────────────────────────────────────

/** 信用账户的额度、账单日、还款日。预览下一期的出账日、还款日与最长免息天数。 */
@Composable
fun CreditSettingsSheet(
    account: LedgerAccount,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CreditSettings?) -> Unit,
) {
    val initial = account.credit
    var limit by remember { mutableStateOf(initial?.limit?.takeIf { it > 0 }?.let(Money::plain) ?: "") }
    var statement by remember { mutableStateOf(initial?.statementDay?.toString() ?: "") }
    var mode by remember { mutableStateOf(initial?.dueMode ?: DueMode.DAY_OF_MONTH) }
    var due by remember { mutableStateOf(initial?.dueDay?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val draft = run {
        val s = statement.toIntOrNull() ?: return@run null
        val d = due.toIntOrNull() ?: return@run null
        CreditSettings(s, d, mode, parseCents(limit) ?: 0L).takeIf { it.problem() == null }
    }
    JkSheet(onDismiss = onDismiss, title = "${account.name} · 账单设置") {
        SheetBody {
            JkTextField(value = limit, onValueChange = { limit = it.take(14); error = null }, label = "额度（可选）", placeholder = "例如 20000", keyboardType = KeyboardType.Decimal)
            JkTextField(value = statement, onValueChange = { statement = it.filter(Char::isDigit).take(2); error = null }, label = "账单日（每月几号，1–31）", placeholder = "例如 1", keyboardType = KeyboardType.Number)
            JkSegmented(listOf(DueMode.DAY_OF_MONTH to "还款日：每月固定日", DueMode.AFTER_STATEMENT to "出账后 N 天"), mode, { mode = it; error = null })
            JkTextField(
                value = due,
                onValueChange = { due = it.filter(Char::isDigit).take(2); error = null },
                label = if (mode == DueMode.DAY_OF_MONTH) "还款日（每月几号，1–31）" else "出账后第几天还款（1–60）",
                placeholder = if (mode == DueMode.DAY_OF_MONTH) "例如 10" else "例如 20",
                keyboardType = KeyboardType.Number,
                error = error,
            )
            if (draft != null) {
                val next = DebtMath.statementOnOrAfter(today, draft)
                val dueDate = DebtMath.dueDate(next, draft)
                val bestStart = DebtMath.statementOnOrBefore(today, draft).plusDays(1)
                val maxFree = java.time.temporal.ChronoUnit.DAYS.between(bestStart, DebtMath.dueDate(DebtMath.statementOnOrAfter(bestStart, draft), draft))
                Hint("下一期 ${debtDate(next, today)} 出账，${debtDate(dueDate, today)} 前还款；账单日次日消费最长免息 $maxFree 天。月份没有这一天时按月末算。")
            } else {
                Hint("账单日当天的消费计入当期账单。还款日小于或等于账单日时，在次月还款。")
            }
        }
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
            if (initial != null) {
                JkButton("清除设置", { onSave(null) }, style = ButtonStyle.Danger, modifier = Modifier.weight(1f))
            }
            JkButton("保存", {
                val s = statement.toIntOrNull()
                val d = due.toIntOrNull()
                val l = if (limit.isBlank()) 0L else Money.parse(limit)
                val settings = if (s != null && d != null && l != null) CreditSettings(s, d, mode, l) else null
                val problem = when {
                    s == null -> "请填写账单日"
                    d == null -> "请填写还款日"
                    l == null -> "额度格式不对，最多两位小数"
                    else -> settings?.problem()
                }
                if (problem != null) error = problem else onSave(settings)
            }, modifier = Modifier.weight(1f))
        }
    }
}

// ── 还款 ──────────────────────────────────────────────────────────

/** 信用账户还款：本期剩余 / 全部欠款 / 自定义金额（部分还款），从资金账户转出。 */
@Composable
fun RepaySheet(
    account: LedgerAccount,
    statementRemaining: Long,
    totalDebt: Long,
    funds: List<LedgerAccount>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (fromId: String, amount: Long, date: LocalDate) -> Unit,
) {
    var amount by remember { mutableStateOf(Money.plain((if (statementRemaining > 0) statementRemaining else totalDebt).coerceAtLeast(0L)).takeIf { it != "0" } ?: "") }
    var from by remember { mutableStateOf(funds.firstOrNull()?.id) }
    var date by remember { mutableStateOf(today) }
    var error by remember { mutableStateOf<String?>(null) }
    JkSheet(onDismiss = onDismiss, title = "还款 · ${account.name}") {
        SheetBody {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                if (statementRemaining > 0) item(key = "st") { JkChip("本期剩余 ${Money.format(statementRemaining)}", parseCents(amount) == statementRemaining, { amount = Money.plain(statementRemaining) }) }
                if (totalDebt > 0) item(key = "all") { JkChip("全部欠款 ${Money.format(totalDebt)}", parseCents(amount) == totalDebt, { amount = Money.plain(totalDebt) }) }
            }
            JkTextField(value = amount, onValueChange = { amount = it.take(14); error = null }, label = "还款金额（可以只还一部分）", placeholder = "0.00", keyboardType = KeyboardType.Decimal, error = error)
            Text("从哪个账户还", style = JkType.small, color = Jk.colors.textSecondary)
            AccountChips(funds, from) { from = it }
            DateField("还款日期", date, today, { date = it })
            Hint("还款记成一笔从资金账户到“${account.name}”的转账，不计入支出。")
        }
        Spacer(Modifier.height(Space.x2))
        JkButton("确认还款", {
            val cents = parseCents(amount)
            val source = from
            when {
                cents == null || cents <= 0 -> error = "请输入大于 0 的金额"
                source == null -> error = "请选择还款账户"
                else -> onSubmit(source, cents, date)
            }
        }, modifier = Modifier.fillMaxWidth())
    }
}

// ── 分期 ──────────────────────────────────────────────────────────

/** 新增分期：总额、期数、每期手续费或费率、首期日期；可以同时记一笔分期消费。 */
@Composable
fun InstallmentSheet(
    account: LedgerAccount,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (plan: LedgerInstallment, recordPurchase: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var periods by remember { mutableStateOf("12") }
    var byRate by remember { mutableStateOf(false) }
    var fee by remember { mutableStateOf("") }
    var first by remember { mutableStateOf(today) }
    var record by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val totalCents = parseCents(total)
    val count = periods.toIntOrNull()
    // 费率按“每期 x%”填，存成万分之一
    val rateBp = if (byRate) fee.toBigDecimalOrNull()?.movePointRight(2)?.toInt() else null
    val feeCents = when {
        byRate -> if (totalCents != null && rateBp != null) DebtMath.feeFromRate(totalCents, rateBp) else if (fee.isBlank()) 0L else null
        else -> if (fee.isBlank()) 0L else parseCents(fee)
    }
    JkSheet(onDismiss = onDismiss, title = "新增分期 · ${account.name}") {
        SheetBody {
            JkTextField(value = title, onValueChange = { title = it.take(40); error = null }, label = "分期名称", placeholder = "例如：手机、账单分期")
            JkTextField(value = total, onValueChange = { total = it.take(14); error = null }, label = "分期总额（本金）", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                items(listOf(3, 6, 9, 12, 24), key = { it }) { n -> JkChip("$n 期", periods == n.toString(), { periods = n.toString() }) }
            }
            JkTextField(value = periods, onValueChange = { periods = it.filter(Char::isDigit).take(2); error = null }, label = "期数（2–60）", keyboardType = KeyboardType.Number)
            JkSegmented(listOf(false to "每期手续费（元）", true to "每期费率（%）"), byRate, { byRate = it; fee = "" })
            JkTextField(
                value = fee,
                onValueChange = { fee = it.take(10); error = null },
                label = if (byRate) "每期费率，例如 0.6" else "每期手续费，没有就留空",
                placeholder = if (byRate) "0.6" else "0.00",
                keyboardType = KeyboardType.Decimal,
            )
            DateField("首期入账日", first, today, { first = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("同时记一笔分期消费", style = JkType.body, color = Jk.colors.textPrimary)
                    Text("已经记过这笔消费（或是账单分期）就关掉", style = JkType.caption, color = Jk.colors.textTertiary)
                }
                JkSwitch(record, { record = it })
            }
            if (totalCents != null && totalCents > 0 && count != null && count in 2..60 && feeCents != null) {
                val per = totalCents / count
                val firstPer = per + totalCents % count
                Hint(
                    "每期本金 ${Money.format(firstPer)}${if (firstPer != per) "（之后每期 ${Money.format(per)}）" else ""}" +
                        " + 手续费 ${Money.format(feeCents)}；手续费共 ${Money.format(feeCents * count)}。" +
                        "第 1 期计入 ${debtDate(first, today)} 所在的那一期账单，之后每月一期。",
                )
            }
            error?.let { Hint(it, danger = true) }
        }
        Spacer(Modifier.height(Space.x2))
        JkButton("创建分期", {
            val plan = LedgerInstallment(
                id = LedgerIds.newId(),
                accountId = account.id,
                title = title.trim(),
                total = totalCents ?: 0L,
                periods = count ?: 0,
                feePerPeriod = feeCents ?: -1L,
                feeRateBp = rateBp,
                firstDate = first,
                startDate = minOf(today, first),
            )
            val problem = when {
                totalCents == null -> "请填写分期总额"
                feeCents == null -> "手续费格式不对"
                else -> plan.problem()
            }
            if (problem != null) error = problem else onSubmit(plan, record)
        }, modifier = Modifier.fillMaxWidth())
    }
}

// ── 借贷 ──────────────────────────────────────────────────────────

/** 记一笔借贷：方向、对方（已有的或新名字）、金额、资金账户、日期，可选预计还款日、利息与备注。 */
@Composable
fun NewLoanSheet(
    people: List<LedgerAccount>,
    funds: List<LedgerAccount>,
    today: LocalDate,
    onDismiss: () -> Unit,
    initialDirection: LoanDirection = LoanDirection.LEND,
    onSubmit: (direction: LoanDirection, person: String, accountId: String?, amount: Long, fundId: String, date: LocalDate, due: LocalDate?, interest: Long, note: String) -> Unit,
) {
    var direction by remember { mutableStateOf(initialDirection) }
    var person by remember { mutableStateOf("") }
    var personId by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var fund by remember { mutableStateOf(funds.firstOrNull()?.id) }
    var date by remember { mutableStateOf(today) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var interest by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val known = people.filter { LoanDirection.of(it.kind) == direction }
    JkSheet(onDismiss = onDismiss, title = "记一笔借贷") {
        SheetBody {
            JkSegmented(listOf(LoanDirection.LEND to "借出（别人欠我）", LoanDirection.BORROW to "借入（我欠别人）"), direction, {
                direction = it
                personId = null
            })
            if (known.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                    items(known, key = { it.id }) { a ->
                        val name = DebtMath.personOf(a)
                        JkChip(name, personId == a.id, {
                            personId = a.id
                            person = name
                        })
                    }
                }
            }
            JkTextField(value = person, onValueChange = { person = it.take(20); personId = null; error = null }, label = "对方", placeholder = "例如：小李")
            JkTextField(value = amount, onValueChange = { amount = it.take(14); error = null }, label = "金额", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            Text(if (direction == LoanDirection.LEND) "钱从哪个账户借出" else "钱借到哪个账户", style = JkType.small, color = Jk.colors.textSecondary)
            AccountChips(funds, fund) { fund = it }
            DateField("借款日期", date, today, { date = it })
            DateField("预计还款日（可选）", due, today, { due = it }, onClear = { due = null })
            JkTextField(value = interest, onValueChange = { interest = it.take(14); error = null }, label = "约定利息（可选）", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            JkTextField(value = note, onValueChange = { note = it.take(200) }, label = "备注（可选）", singleLine = false)
            error?.let { Hint(it, danger = true) }
        }
        Spacer(Modifier.height(Space.x2))
        JkButton("保存", {
            val cents = parseCents(amount)
            val rate = if (interest.isBlank()) 0L else parseCents(interest)
            val source = fund
            error = when {
                person.isBlank() && personId == null -> "请填写对方"
                cents == null || cents <= 0 -> "请输入大于 0 的金额"
                rate == null -> "利息格式不对"
                source == null -> "请选择资金账户"
                due != null && due!! < date -> "预计还款日不能早于借款日期"
                else -> null
            }
            if (error == null) onSubmit(direction, person.trim(), personId, cents!!, source!!, date, due, rate!!, note.trim())
        }, modifier = Modifier.fillMaxWidth())
    }
}

/** 收款（借出）/ 还款（借入）：本金可以只还一部分，利息另填。 */
@Composable
fun LoanPaySheet(
    loan: LoanView,
    funds: List<LedgerAccount>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (principal: Long, interest: Long, fundId: String, date: LocalDate) -> Unit,
) {
    val lend = loan.direction == LoanDirection.LEND
    var amount by remember { mutableStateOf(loan.remaining.takeIf { it > 0 }?.let(Money::plain) ?: "") }
    var interest by remember { mutableStateOf("") }
    var fund by remember { mutableStateOf(funds.firstOrNull()?.id) }
    var date by remember { mutableStateOf(today) }
    var error by remember { mutableStateOf<String?>(null) }
    JkSheet(onDismiss = onDismiss, title = (if (lend) "收款 · " else "还款 · ") + loan.person) {
        SheetBody {
            Hint("待${if (lend) "收" else "还"}本金 ${Money.format(loan.remaining)}" + if (loan.interestRemaining > 0) "，约定利息还差 ${Money.format(loan.interestRemaining)}" else "")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                if (loan.remaining > 0) item(key = "all") { JkChip("全部 ${Money.format(loan.remaining)}", parseCents(amount) == loan.remaining, { amount = Money.plain(loan.remaining) }) }
                if (loan.remaining >= 2) item(key = "half") { JkChip("一半", parseCents(amount) == loan.remaining / 2, { amount = Money.plain(loan.remaining / 2) }) }
            }
            JkTextField(value = amount, onValueChange = { amount = it.take(14); error = null }, label = if (lend) "收回本金（可以只收一部分）" else "归还本金（可以只还一部分）", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            JkTextField(value = interest, onValueChange = { interest = it.take(14); error = null }, label = "利息（可选）", placeholder = loan.interestRemaining.takeIf { it > 0 }?.let(Money::plain) ?: "0.00", keyboardType = KeyboardType.Decimal)
            Text(if (lend) "收到哪个账户" else "从哪个账户还", style = JkType.small, color = Jk.colors.textSecondary)
            AccountChips(funds, fund) { fund = it }
            DateField(if (lend) "收款日期" else "还款日期", date, today, { date = it })
            error?.let { Hint(it, danger = true) }
        }
        Spacer(Modifier.height(Space.x2))
        JkButton(if (lend) "确认收款" else "确认还款", {
            val p = if (amount.isBlank()) 0L else parseCents(amount)
            val i = if (interest.isBlank()) 0L else parseCents(interest)
            val source = fund
            error = when {
                p == null || i == null -> "金额格式不对，最多两位小数"
                p <= 0 && i <= 0 -> "请输入大于 0 的金额"
                source == null -> "请选择资金账户"
                else -> null
            }
            if (error == null) onSubmit(p!!, i!!, source!!, date)
        }, modifier = Modifier.fillMaxWidth())
    }
}

/** 改借贷的约定：预计还款日、利息、备注。 */
@Composable
fun LoanEditSheet(loan: LoanView, today: LocalDate, onDismiss: () -> Unit, onSave: (LedgerLoan) -> Unit) {
    var due by remember { mutableStateOf(loan.loan.dueDate) }
    var interest by remember { mutableStateOf(loan.loan.interest.takeIf { it > 0 }?.let(Money::plain) ?: "") }
    var note by remember { mutableStateOf(loan.loan.note) }
    var error by remember { mutableStateOf<String?>(null) }
    JkSheet(onDismiss = onDismiss, title = "约定 · ${loan.person}") {
        SheetBody {
            DateField("预计还款日", due, today, { due = it }, onClear = { due = null })
            JkTextField(value = interest, onValueChange = { interest = it.take(14); error = null }, label = "约定利息（可选）", placeholder = "0.00", keyboardType = KeyboardType.Decimal, error = error)
            JkTextField(value = note, onValueChange = { note = it.take(200) }, label = "备注", singleLine = false)
            if (!loan.stored) Hint("这是导入的借贷（按对方账户上的流水算出），保存后记住这些约定。")
        }
        Spacer(Modifier.height(Space.x2))
        JkButton("保存", {
            val cents = if (interest.isBlank()) 0L else parseCents(interest)
            if (cents == null) error = "利息格式不对" else onSave(loan.loan.copy(dueDate = due, interest = cents, note = note.trim()))
        }, modifier = Modifier.fillMaxWidth())
    }
}
