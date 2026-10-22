package com.xiguli.jinkui.data.ledger

import java.time.LocalDate
import java.time.ZoneId

/**
 * 1.5.0-alpha03：借贷的每一条记录都能直接改（参考一木记账、钱迹的“编辑借贷记录”，只借鉴交互）。
 *
 * 用户看到的一条记录（借出 / 借入 / 收款 / 还款 / 结清）背后是一到两笔普通流水：
 *  - 借出 / 借入：对方账户与资金账户（或“余额调整”）之间的一笔转账；约定的预计还款日、利息记在借贷记录上；
 *  - 收款 / 还款：一笔转账，另付利息时再加一笔收入 / 支出（同一时刻、同一次记下）；
 *  - 结清：对方账户上的一笔支出 / 收入。
 * 这里只做纯计算：给出要写入 / 删除的账户、借贷记录与流水（[DebtChange]），仓库在一个事务里写入。
 * 不改数据库与备份结构。
 */
enum class DebtRecordType(val label: String, val direction: LoanDirection, val principal: Boolean) {
    LEND("借出", LoanDirection.LEND, true),
    BORROW("借入", LoanDirection.BORROW, true),
    COLLECT("收款", LoanDirection.LEND, false),
    REPAY("还款", LoanDirection.BORROW, false),
    /** 坏账（收不回了）/ 免还（对方说不用还了）：只能改金额、日期与备注。 */
    WRITE_OFF("结清", LoanDirection.LEND, false);

    val payment: Boolean get() = this == COLLECT || this == REPAY

    companion object {
        fun of(direction: LoanDirection, principal: Boolean): DebtRecordType = when (direction) {
            LoanDirection.LEND -> if (principal) LEND else COLLECT
            LoanDirection.BORROW -> if (principal) BORROW else REPAY
        }
    }
}

/** 一条借贷记录。[main] 为 null 时是只付了利息的一次收款 / 还款。 */
data class DebtRecord(
    val type: DebtRecordType,
    val direction: LoanDirection,
    val person: LedgerAccount,
    val loan: LoanView,
    val main: LedgerTransaction?,
    val interestTx: LedgerTransaction?,
    /** 经过的资金账户；null = 不经过账户（只记欠款）。 */
    val fundId: String?,
) {
    val ids: List<String> get() = listOfNotNull(main?.id, interestTx?.id)
    val amount: Long get() = main?.amount ?: 0L
    /** 借出 / 借入时是约定利息；收款 / 还款时是这次另付的利息。 */
    val interest: Long get() = if (type.principal) loan.loan.interest else interestTx?.amount ?: 0L
    val at: Long get() = (main ?: interestTx)!!.occurredAt
    val note: String get() = main?.note ?: interestTx?.note.orEmpty()
    val due: LocalDate? get() = if (type.principal) loan.loan.dueDate else null
    /** 这是一笔“修改欠款”（直接改还欠多少时记的那一笔）。 */
    val correction: Boolean get() = main?.note == PersonEdit.CORRECTION_NOTE
    /** 一笔真正落库的借贷的本金（不是导入的）：改对方 / 方向时这笔借贷名下的全部记录一起跟着走。 */
    val ownsLoan: Boolean get() = type.principal && loan.stored && !loan.loan.legacy
}

/** 编辑后的内容（面板上填的）。 */
data class DebtRecordEdit(
    val type: DebtRecordType,
    /** 选的已有对方账户；null 时按 [personName] 找同名的或新建。 */
    val personAccountId: String?,
    val personName: String,
    val amount: Long,
    val interest: Long,
    val at: Long,
    val fundId: String?,
    val due: LocalDate?,
    val note: String,
)

/** 一次改动：要写入（新建或覆盖）的与要删掉的。 */
data class DebtChange(
    val accounts: List<LedgerAccount> = emptyList(),
    val loans: List<LedgerLoan> = emptyList(),
    val deleteLoans: List<String> = emptyList(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val deleteTransactions: List<String> = emptyList(),
    /** 要删掉的账户（合并后没有流水的那个人）。 */
    val deleteAccounts: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = accounts.isEmpty() && loans.isEmpty() && deleteLoans.isEmpty() && transactions.isEmpty() && deleteTransactions.isEmpty() && deleteAccounts.isEmpty()

    /** 把这次改动应用到 [data] 上（仓库发布内存数据、测试都用它）。删掉的流水 / 借贷同时记删除标记，写回的去掉标记。 */
    fun applyTo(data: LedgerData, now: Long): LedgerData {
        val txIds = transactions.mapTo(HashSet()) { it.id }
        val goneTx = deleteTransactions.toHashSet() - txIds
        val loanIds = loans.mapTo(HashSet()) { it.id }
        val goneLoans = deleteLoans.toHashSet() - loanIds
        val accountIds = accounts.mapTo(HashSet()) { it.id }
        val goneAccounts = deleteAccounts.toHashSet() - accountIds
        val revived = txIds + loanIds
        return data.copy(
            accounts = (data.accounts.filterNot { it.id in accountIds || it.id in goneAccounts } + accounts).sortedBy { it.position },
            loans = data.loans.filterNot { it.id in loanIds || it.id in goneLoans } + loans,
            transactions = data.transactions.filterNot { it.id in txIds || it.id in goneTx } + transactions,
            tombstones = data.tombstones.filterNot { it.id in revived || it.id in goneTx || it.id in goneLoans } +
                tombstonesFor(now),
        )
    }

    fun tombstonesFor(now: Long): List<LedgerTombstone> {
        val txIds = transactions.mapTo(HashSet()) { it.id }
        val loanIds = loans.mapTo(HashSet()) { it.id }
        return deleteTransactions.filter { it !in txIds }.distinct().map { LedgerTombstone(it, LedgerTombstone.KIND_TX, now) } +
            deleteLoans.filter { it !in loanIds }.distinct().map { LedgerTombstone(it, LedgerTombstone.KIND_DEBT, now) }
    }

    /** 撤销这次改动要做的改动（在应用之前的 [before] 上算）。新建的账户保留（没有流水的人不会显示）。 */
    fun inverse(before: LedgerData): DebtChange {
        val txById = before.transactions.associateBy { it.id }
        val loanById = before.loans.associateBy { it.id }
        val accountById = before.accounts.associateBy { it.id }
        val restoreTx = (transactions.map { it.id } + deleteTransactions).distinct().mapNotNull { txById[it] }
        val dropTx = transactions.map { it.id }.filter { it !in txById }
        val restoreLoans = (loans.map { it.id } + deleteLoans).distinct().mapNotNull { loanById[it] }
        val dropLoans = loans.map { it.id }.filter { it !in loanById }
        val restoreAccounts = (accounts.map { it.id } + deleteAccounts).distinct().mapNotNull { accountById[it] }
        return DebtChange(
            accounts = restoreAccounts,
            loans = restoreLoans,
            deleteLoans = dropLoans,
            transactions = restoreTx,
            deleteTransactions = dropTx,
        )
    }
}

object DebtEdit {
    const val INTEREST_PREFIX = "利息 · "

    /** 某一笔流水所在的借贷记录；不是借贷的流水（普通收支、信用卡还款、分期……）返回 null。 */
    fun recordOf(data: LedgerData, txId: String, zone: ZoneId = ZoneId.systemDefault()): DebtRecord? {
        val tx = data.transactions.firstOrNull { it.id == txId } ?: return null
        val people = data.accounts.filter { LoanDirection.of(it.kind) != null }.associateBy { it.id }
        val touchesPerson = tx.accountId in people || tx.toAccountId in people
        val loanIds = data.loans.mapTo(HashSet()) { it.id }
        val legacyIds = people.keys.associateBy { DebtMath.legacyLoanId(it) }
        if (!touchesPerson && !(tx.type != TxType.TRANSFER && (tx.debtId in loanIds || tx.debtId in legacyIds))) return null
        val loans = DebtMath.loans(data, zone)
        val loan = loans.firstOrNull { l -> l.entries.any { it.id == tx.id } } ?: return null
        val person = loan.account
        val accounts = data.accounts.associateBy { it.id }
        if (touchesPerson) {
            val kind = DebtMath.entryKind(loan, tx)
            val type = when (kind) {
                LoanEntryKind.PRINCIPAL -> DebtRecordType.of(loan.direction, true)
                LoanEntryKind.REPAY -> DebtRecordType.of(loan.direction, false)
                LoanEntryKind.WRITE_OFF -> DebtRecordType.WRITE_OFF
                LoanEntryKind.INTEREST -> return null
            }
            val partner = if (type.payment) interestPartner(loan, tx) else null
            return DebtRecord(type, loan.direction, person, loan, tx, partner, fundOf(tx, person.id, accounts))
        }
        // 利息：找同一次记下的收款 / 还款
        val main = loan.entries.firstOrNull { it.type == TxType.TRANSFER && it.occurredAt == tx.occurredAt && it.createdAt == tx.createdAt && DebtMath.entryKind(loan, it) == LoanEntryKind.REPAY }
        if (main != null) return DebtRecord(DebtRecordType.of(loan.direction, false), loan.direction, person, loan, main, tx, fundOf(main, person.id, accounts))
        val fund = accounts[tx.accountId]?.takeIf { it.kind != AccountKind.ADJUST }?.id
        return DebtRecord(DebtRecordType.of(loan.direction, false), loan.direction, person, loan, null, tx, fund)
    }

    private fun interestPartner(loan: LoanView, main: LedgerTransaction): LedgerTransaction? =
        loan.entries.firstOrNull { it.type != TxType.TRANSFER && it.occurredAt == main.occurredAt && it.createdAt == main.createdAt && it.id != main.id && it.accountId != loan.account.id }

    private fun fundOf(tx: LedgerTransaction, personId: String, accounts: Map<String, LedgerAccount>): String? {
        if (tx.type != TxType.TRANSFER) return null
        val other = if (tx.accountId == personId) tx.toAccountId else tx.accountId
        return accounts[other]?.takeIf { it.kind != AccountKind.ADJUST }?.id
    }

    private fun interestCategory(data: LedgerData, lend: Boolean): String? {
        val ids = if (lend) listOf("cat-invest", "cat-other-income") else listOf("cat-other-expense")
        return ids.firstOrNull { id -> data.categories.any { it.id == id } }
    }

    private fun writeOffCategory(data: LedgerData, lend: Boolean): String? {
        val id = if (lend) "cat-other-expense" else "cat-other-income"
        return id.takeIf { data.categories.any { it.id == id } }
    }

    /** 对方：选的账户（方向对得上时），或同方向同名的账户，或新建一个（第二个值是新建 / 恢复启用的账户）。 */
    fun personAccount(data: LedgerData, direction: LoanDirection, accountId: String?, name: String, now: Long, newId: () -> String): Pair<LedgerAccount, LedgerAccount?> {
        accountId?.let { id -> data.accounts.firstOrNull { it.id == id && it.kind == direction.accountKind } }?.let { found ->
            return found to found.takeIf { it.archived }?.copy(archived = false, updatedAt = now)
        }
        val clean = name.trim().take(20)
        require(clean.isNotEmpty()) { "请填写对方" }
        val prefix = if (direction == LoanDirection.LEND) "借出 · " else "借入 · "
        data.accounts.firstOrNull { it.kind == direction.accountKind && (it.name == prefix + clean || DebtMath.personOf(it) == clean) }?.let { found ->
            val revived = found.takeIf { it.archived }?.copy(archived = false, updatedAt = now)
            return (revived ?: found) to revived
        }
        val created = LedgerAccount(newId(), prefix + clean, direction.accountKind, position = (data.accounts.maxOfOrNull { it.position } ?: -1) + 1, createdAt = now, updatedAt = now)
        return created to created
    }

    /** 把 [tx] 从对方 [from] 挪到 [to]；[flip] 为 true 时方向反过来（借出改借入）。 */
    private fun moveTx(tx: LedgerTransaction, from: String, to: String, flip: Boolean, data: LedgerData, now: Long): LedgerTransaction {
        var t = tx.copy(
            accountId = if (tx.accountId == from) to else tx.accountId,
            toAccountId = if (tx.toAccountId == from) to else tx.toAccountId,
            updatedAt = now,
        )
        if (flip) {
            t = when (t.type) {
                TxType.TRANSFER -> t.copy(accountId = t.toAccountId!!, toAccountId = t.accountId)
                TxType.INCOME -> t.copy(type = TxType.EXPENSE, categoryId = if (t.accountId == to) writeOffCategory(data, true) else interestCategory(data, false))
                TxType.EXPENSE -> t.copy(type = TxType.INCOME, categoryId = if (t.accountId == to) writeOffCategory(data, false) else interestCategory(data, true))
            }
        }
        return t
    }

    /**
     * 改一条记录。校验没通过时抛 [IllegalArgumentException]（消息直接给用户看）。
     * [now] 也用作新流水的记录时间；[newId] 生成新 ID（测试传固定的）。
     */
    fun plan(data: LedgerData, record: DebtRecord, edit: DebtRecordEdit, now: Long, zone: ZoneId = ZoneId.systemDefault(), newId: () -> String = LedgerIds::newId): DebtChange {
        val type = edit.type
        require((type == DebtRecordType.WRITE_OFF) == (record.type == DebtRecordType.WRITE_OFF)) { "结清的记录不能改成别的类型" }
        require(edit.amount >= 0 && edit.interest >= 0) { "金额不能是负数" }
        require(edit.amount <= Money.MAX_CENTS && edit.interest <= Money.MAX_CENTS) { "金额太大了" }
        when {
            type.principal -> require(edit.amount > 0) { "请输入大于 0 的金额" }
            type.payment -> require(edit.amount > 0 || edit.interest > 0) { "请输入大于 0 的金额" }
            else -> require(edit.amount > 0) { "请输入大于 0 的金额" }
        }
        val date = LedgerMath.dateOf(edit.at, zone)
        if (type.principal && edit.due != null) require(edit.due >= date) { "预计还款日不能早于借款日期" }
        require(edit.note.length <= 500) { "备注太长了" }
        val direction = if (type == DebtRecordType.WRITE_OFF) record.direction else type.direction
        val lend = direction == LoanDirection.LEND

        val (target, personWrite) = personAccount(data, direction, edit.personAccountId, edit.personName.ifBlank { DebtMath.personOf(record.person) }, now, newId)
        val accountsNow = data.accounts.filterNot { it.id == target.id } + target
        val name = DebtMath.personOf(target)
        val newAccounts = listOfNotNull(personWrite).toMutableList()

        // 结清：只改金额、日期、备注（以及换人）
        if (type == DebtRecordType.WRITE_OFF) {
            val main = record.main!!
            val samePerson = target.id == record.person.id
            val tx = main.copy(
                accountId = target.id, amount = edit.amount, occurredAt = edit.at,
                note = edit.note.trim().ifEmpty { (if (lend) "坏账 · " else "免还 · ") + name },
                debtId = if (samePerson) main.debtId else null, updatedAt = now,
            )
            return DebtChange(accounts = newAccounts, transactions = listOf(tx))
        }

        val (fund, newAdjust) = DebtMath.loanFundAccount(accountsNow + newAccounts.filterNot { a -> accountsNow.any { it.id == a.id } }, edit.fundId, now, newId)
        newAdjust?.let { newAccounts += it }
        val accountsAll = (accountsNow + newAccounts).distinctBy { it.id }
        val reuse = ArrayDeque(record.ids)
        fun nextId(): String = reuse.removeFirstOrNull() ?: newId()
        val note = edit.note.trim()
        val txs = ArrayList<LedgerTransaction>()
        val loansOut = ArrayList<LedgerLoan>()
        val deleteLoans = ArrayList<String>()
        val deleteTxs = ArrayList<String>()

        if (type.principal) {
            val flip = direction != record.direction
            val moved = target.id != record.person.id
            if (record.ownsLoan) {
                // 一笔落库的借贷：改的就是这笔借贷，名下的收款 / 还款跟着走。
                // 已经收（还）过钱的不能换人、换方向（同随手记：防止账对不上；同一个人两个名字请用合并）
                val paid = record.loan.entries.any { it.id !in record.ids }
                require(!paid || (!moved && !flip)) { "已经${if (record.direction == LoanDirection.LEND) "收" else "还"}过钱，不能换人；同一个人记成了两个名字，可以在人页“编辑 → 合并”" }
                require(edit.amount >= record.loan.repaid || edit.amount >= record.amount) { "已${if (lend) "收回" else "还了"} ${Money.format(record.loan.repaid)}，金额不能少于它" }
                val old = record.loan.loan
                val loan = old.copy(accountId = target.id, direction = direction, date = date, dueDate = edit.due, interest = edit.interest, note = note.take(500), updatedAt = now)
                loansOut += loan
                val principalTx = DebtMath.loanPrincipalTx(nextId(), loan, target, fund, edit.amount, edit.at, now)
                    .copy(createdAt = record.main?.createdAt ?: now, note = note.ifEmpty { (if (lend) "借出 · " else "借入 · ") + name })
                txs += principalTx
                if (moved || flip) {
                    for (t in record.loan.entries) {
                        if (t.id in record.ids) continue
                        txs += moveTx(t, record.person.id, target.id, flip, data, now)
                    }
                }
            } else {
                // 收款 / 还款改成借出 / 借入、或导入的借出：成为一笔新的借贷（导入的仍挂在对方账户上）
                if (record.type.principal && !moved && !flip) {
                    val main = record.main!!
                    val tx = DebtMath.loanPrincipalTx(main.id, record.loan.loan, target, fund, edit.amount, edit.at, now)
                        .copy(createdAt = main.createdAt, debtId = main.debtId, note = note.ifEmpty { (if (lend) "借出 · " else "借入 · ") + name })
                    reuse.remove(main.id)
                    txs += tx
                    // 导入的借贷：预计还款日 / 利息记在这个对方账户的“导入的借贷”上
                    val old = record.loan.loan
                    if (old.dueDate != edit.due || old.interest != edit.interest) {
                        loansOut += old.copy(dueDate = edit.due, interest = edit.interest, legacy = true, createdAt = old.createdAt.takeIf { it > 0 } ?: now, updatedAt = now)
                    }
                } else {
                    val loan = LedgerLoan(newId(), target.id, direction, date, edit.due, edit.interest, note.take(500), createdAt = now, updatedAt = now)
                    loansOut += loan
                    txs += DebtMath.loanPrincipalTx(nextId(), loan, target, fund, edit.amount, edit.at, now)
                        .copy(note = note.ifEmpty { (if (lend) "借出 · " else "借入 · ") + name })
                }
            }
        } else {
            // 收款 / 还款：先把这条记录拿掉，再按人重新分到没还清的几笔上（与按人收款同一个算法）
            var base = data.copy(transactions = data.transactions.filterNot { it.id in record.ids }, accounts = accountsAll)
            if (record.ownsLoan) {
                val others = record.loan.entries.filterNot { it.id in record.ids }
                require(others.isEmpty()) { "这笔${record.type.label}下面还有 ${others.size} 条收款 / 还款记录，请先改或删掉那几条" }
                deleteLoans += record.loan.loan.id
                base = base.copy(loans = base.loans.filterNot { it.id == record.loan.loan.id })
            }
            val today = LedgerMath.dateOf(now, zone)
            val person = PersonDebtMath.person(DebtMath.loans(base, zone), target.id, today)
            val unchanged = record.type == type && target.id == record.person.id && edit.amount == record.amount
            // 多收 / 多还的部分记成利息（同钱迹“还款超出部分自动算利息”）；原样没改的旧记录保持不动
            val owed = person?.remaining ?: 0L
            val principal = if (unchanged) edit.amount else minOf(edit.amount, owed)
            val paidInterest = edit.interest + (edit.amount - principal)
            require(principal > 0 || paidInterest > 0) { "请输入大于 0 的金额" }
            val parts: List<Pair<LoanView?, Long>> = when {
                principal == 0L -> listOf((person?.open?.firstOrNull() ?: person?.loans?.maxByOrNull { it.loan.date } ?: record.loan.takeIf { !record.ownsLoan && target.id == record.person.id }) to 0L)
                person != null -> PersonDebtMath.split(person, principal) ?: listOf(record.loan to principal)
                else -> listOf(record.loan to principal)
            }
            val storedIds = data.loans.filter { !it.legacy }.mapTo(HashSet()) { it.id } - deleteLoans.toSet()
            val category = record.interestTx?.categoryId?.takeIf { record.direction == direction } ?: interestCategory(data, lend)
            parts.forEachIndexed { i, (loan, amount) ->
                val loanId = loan?.loan?.id ?: DebtMath.legacyLoanId(target.id)
                val link = loanId.takeIf { it in storedIds }
                txs += DebtMath.loanPaymentTxs(target, fund, amount, if (i == 0) paidInterest else 0L, edit.at, now, note, link, loanId, category, ::nextId)
            }
            // 保留原来的记录时间（列表按它排序“上次用的账户”），利息与本金仍是同一时刻、同一次
            val created = record.main?.createdAt ?: record.interestTx?.createdAt ?: now
            for (i in txs.indices) txs[i] = txs[i].copy(createdAt = created)
        }
        val written = txs.mapTo(HashSet()) { it.id }
        deleteTxs += record.ids.filter { it !in written }
        txs.forEach { tx -> LedgerMath.validate(tx, accountsAll)?.let { throw IllegalArgumentException(it) } }
        return DebtChange(
            accounts = newAccounts.distinctBy { it.id },
            loans = loansOut,
            deleteLoans = deleteLoans,
            transactions = txs,
            deleteTransactions = deleteTxs,
        )
    }

    /**
     * 删一条记录。借出 / 借入本金所在的借贷是落库的那种时，连同这笔借贷和它名下的收款 / 还款、利息一起删
     * （[relatedCount] 告诉用户会多删几条）；否则只删这一条（以及同一次记下的利息）。
     */
    fun deletePlan(record: DebtRecord): DebtChange =
        if (record.ownsLoan) {
            DebtChange(deleteLoans = listOf(record.loan.loan.id), deleteTransactions = (record.ids + record.loan.entries.map { it.id }).distinct())
        } else {
            DebtChange(deleteTransactions = record.ids)
        }

    /** 删这条记录时会一起删掉的其他记录条数。 */
    /** 收款 / 还款 [amount] 里超过还欠 [owed] 的部分（会记成利息）。 */
    fun overpay(amount: Long, owed: Long): Long = (amount - owed).coerceAtLeast(0L)

    fun relatedCount(record: DebtRecord): Int =
        if (record.ownsLoan) record.loan.entries.count { it.id !in record.ids } else 0

    /** 删一整笔借款（卡片上的“删除这笔”）：它的全部流水 + 存下来的借贷记录（导入的借贷存过约定时也删）。 */
    fun deleteLoanPlan(data: LedgerData, loan: LoanView, now: Long = System.currentTimeMillis()): DebtChange {
        // 导入的借贷还算上了这个人账户的期初数：删它时期初数一起清零，否则删完还在
        val legacy = loan.loan.id == DebtMath.legacyLoanId(loan.account.id)
        val account = data.accounts.firstOrNull { it.id == loan.account.id }
        return DebtChange(
            accounts = listOfNotNull(account?.takeIf { legacy && it.openingBalance != 0L }?.copy(openingBalance = 0L, updatedAt = now)),
            deleteLoans = listOfNotNull(loan.loan.id.takeIf { id -> data.loans.any { it.id == id } }),
            deleteTransactions = loan.entries.map { it.id }.distinct(),
        )
    }
}

/**
 * 1.5.0-alpha03：按人改——改名、合并到另一个人、直接改“还欠多少”（参考一木记账的“修改欠款”、
 * 钱迹的“合并联系人”，只借鉴交互）。
 */
object PersonEdit {
    /** 直接改还欠多少时记的那一笔的备注（流水与来往记录里都显示“修改欠款”）。 */
    const val CORRECTION_NOTE = "修改欠款"

    private val defaultPrefixes = listOf("借出 · ", "借入 · ", "收款 · ", "还款 · ", "利息 · ", "免除 · ", "对方免还 · ", "坏账 · ", "免还 · ")

    /** 把这个人改名为 [name]。同方向已有同名的人时抛 [IllegalArgumentException]（请用合并）。默认备注里的旧名字一起换掉。 */
    fun rename(data: LedgerData, accountId: String, name: String, now: Long): DebtChange {
        val account = data.accounts.firstOrNull { it.id == accountId && LoanDirection.of(it.kind) != null } ?: throw IllegalArgumentException("找不到这个人")
        val clean = name.trim().take(20)
        require(clean.isNotEmpty()) { "请填写名字" }
        val old = DebtMath.personOf(account)
        if (clean == old) return DebtChange()
        val direction = LoanDirection.of(account.kind)!!
        val clash = data.accounts.firstOrNull { it.id != accountId && it.kind == account.kind && DebtMath.personOf(it) == clean }
        require(clash == null) { "已经有一个叫“$clean”的人了，可以用“合并到另一个人”" }
        val prefix = if (direction == LoanDirection.LEND) "借出 · " else "借入 · "
        val renamed = account.copy(name = prefix + clean, updatedAt = now)
        val touched = data.transactions.filter { tx ->
            (tx.accountId == accountId || tx.toAccountId == accountId || tx.debtId == DebtMath.legacyLoanId(accountId) ||
                data.loans.any { it.accountId == accountId && it.id == tx.debtId }) && defaultPrefixes.any { tx.note == it + old }
        }.map { tx -> tx.copy(note = tx.note.removeSuffix(old) + clean, updatedAt = now) }
        return DebtChange(accounts = listOf(renamed), transactions = touched)
    }

    /**
     * 把 [fromId] 这个人合并到 [intoId]（同一方向）：全部记录、借贷、期初欠款都挪过去，原来那个人删掉
     * （有周期记账在用时改为停用）。
     */
    /**
     * 删除这个人：和他有关的全部流水（借出 / 借入 / 收款 / 还款 / 利息 / 坏账 / 修改欠款）、全部借贷记录、这个人本身。
     * 有周期记账还用着这个人时只停用不删。可以撤销。
     */
    fun deletePerson(data: LedgerData, accountId: String, now: Long): DebtChange {
        val account = data.accounts.firstOrNull { it.id == accountId && LoanDirection.of(it.kind) != null } ?: throw IllegalArgumentException("找不到这个人")
        val loanIds = data.loans.filter { it.accountId == accountId }.mapTo(HashSet()) { it.id } + DebtMath.legacyLoanId(accountId)
        val txs = data.transactions.filter { it.accountId == accountId || it.toAccountId == accountId || it.debtId in loanIds }.map { it.id }
        val usedByRecurring = data.recurring.any { it.accountId == accountId || it.toAccountId == accountId }
        return DebtChange(
            accounts = listOfNotNull(account.copy(archived = true, openingBalance = 0L, updatedAt = now).takeIf { usedByRecurring }),
            deleteLoans = data.loans.filter { it.accountId == accountId }.map { it.id },
            deleteTransactions = txs,
            deleteAccounts = if (usedByRecurring) emptyList() else listOf(accountId),
        )
    }

    fun merge(data: LedgerData, fromId: String, intoId: String, now: Long): DebtChange {
        require(fromId != intoId) { "请选择另一个人" }
        val from = data.accounts.firstOrNull { it.id == fromId } ?: throw IllegalArgumentException("找不到这个人")
        val into = data.accounts.firstOrNull { it.id == intoId } ?: throw IllegalArgumentException("找不到要合并到的人")
        require(LoanDirection.of(from.kind) != null && from.kind == into.kind) { "只能合并到同一边（都是别人欠我，或都是我欠别人）的人" }
        val oldLegacy = DebtMath.legacyLoanId(fromId)
        val newLegacy = DebtMath.legacyLoanId(intoId)
        val txs = data.transactions.filter { it.accountId == fromId || it.toAccountId == fromId || it.debtId == oldLegacy }.map { tx ->
            tx.copy(
                accountId = if (tx.accountId == fromId) intoId else tx.accountId,
                toAccountId = if (tx.toAccountId == fromId) intoId else tx.toAccountId,
                debtId = if (tx.debtId == oldLegacy) newLegacy else tx.debtId,
                updatedAt = now,
            )
        }
        val loans = ArrayList<LedgerLoan>()
        val deleteLoans = ArrayList<String>()
        for (loan in data.loans.filter { it.accountId == fromId }) {
            if (loan.id == oldLegacy) {
                deleteLoans += loan.id
                // “导入的借贷”的约定：要合并到的人还没有时搬过去
                if (data.loans.none { it.id == newLegacy }) loans += loan.copy(id = newLegacy, accountId = intoId, updatedAt = now)
            } else {
                loans += loan.copy(accountId = intoId, updatedAt = now)
            }
        }
        val merged = into.copy(openingBalance = into.openingBalance + from.openingBalance, archived = false, updatedAt = now)
        val usedByRecurring = data.recurring.any { it.accountId == fromId || it.toAccountId == fromId }
        return DebtChange(
            accounts = listOfNotNull(merged, from.copy(archived = true, openingBalance = 0L, updatedAt = now).takeIf { usedByRecurring }),
            loans = loans,
            deleteLoans = deleteLoans,
            transactions = txs,
            deleteAccounts = if (usedByRecurring) emptyList() else listOf(fromId),
        )
    }

    /**
     * 修改欠款：把这个人“还欠多少”直接改成 [target]（不小于 0）。差额记成与内部“余额调整”之间的转账，
     * 备注“修改欠款”：不计入收支统计、不改任何资金账户的余额（与账户的“修改余额”同一种）。
     * 欠得更多：记在最近一笔没还清的借贷上（都还清了时新记一笔借贷）；欠得更少：按收款 / 还款的顺序先冲最早的那笔。
     * 和现在一样时返回空的改动。
     */
    fun setOwed(data: LedgerData, accountId: String, target: Long, at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), newId: () -> String = LedgerIds::newId): DebtChange {
        require(target in 0..Money.MAX_CENTS) { "金额不对" }
        val account = data.accounts.firstOrNull { it.id == accountId } ?: throw IllegalArgumentException("找不到这个人")
        val direction = LoanDirection.of(account.kind) ?: throw IllegalArgumentException("找不到这个人")
        val today = LedgerMath.dateOf(now, zone)
        val person = PersonDebtMath.person(DebtMath.loans(data, zone), accountId, today)
        val current = person?.remaining ?: 0L
        val diff = target - current
        if (diff == 0L) return DebtChange()
        val (adjust, newAdjust) = DebtMath.loanFundAccount(data.accounts, null, now, newId)
        val lend = direction == LoanDirection.LEND
        val storedIds = data.loans.filter { !it.legacy }.mapTo(HashSet()) { it.id }
        val loans = ArrayList<LedgerLoan>()
        val txs = ArrayList<LedgerTransaction>()
        if (diff > 0) {
            val open = person?.open?.lastOrNull()
            val loanId = open?.loan?.id ?: run {
                val loan = LedgerLoan(newId(), accountId, direction, LedgerMath.dateOf(at, zone), note = CORRECTION_NOTE, createdAt = now, updatedAt = now)
                loans += loan
                loan.id
            }
            txs += LedgerTransaction(
                newId(), TxType.TRANSFER, diff, at,
                accountId = if (lend) adjust.id else accountId, toAccountId = if (lend) accountId else adjust.id,
                note = CORRECTION_NOTE, createdAt = now, updatedAt = now,
                debtId = loanId.takeIf { it in storedIds || loans.any { l -> l.id == it } },
            )
        } else {
            val parts = PersonDebtMath.split(person!!, -diff) ?: throw IllegalArgumentException("金额不对")
            for ((loan, amount) in parts) {
                txs += LedgerTransaction(
                    newId(), TxType.TRANSFER, amount, at,
                    accountId = if (lend) accountId else adjust.id, toAccountId = if (lend) adjust.id else accountId,
                    note = CORRECTION_NOTE, createdAt = now, updatedAt = now,
                    debtId = loan.loan.id.takeIf { it in storedIds },
                )
            }
        }
        return DebtChange(accounts = listOfNotNull(newAdjust), loans = loans, transactions = txs)
    }
}
