package com.xiguli.jinkui.data.ledger

/**
 * 报销状态（1.4.0-alpha04 起）：只对支出有意义。[key] 写入加密载荷与备份，上线后不要改动。
 *  - [PENDING] 待报销：替单位 / 别人垫付的钱，记账时就从“支出”里拿掉，列在“报销”页的待报销里；
 *  - [DONE] 已报销：报销到账时可以记一笔关联的收入（同样不算“收入”），也可以只标记（冲抵，钱没有单独入账）。
 */
enum class Reimburse(val key: String, val label: String) {
    NONE("", "不报销"),
    PENDING("pending", "待报销"),
    DONE("done", "已报销");

    companion object {
        fun of(key: String?): Reimburse = entries.firstOrNull { it.key == key && it != NONE } ?: NONE
    }
}

/** 一笔支出的退款情况。 */
data class RefundStatus(
    val expense: LedgerTransaction,
    /** 已经关联的退款（收入），时间先后排列。 */
    val refunds: List<LedgerTransaction>,
) {
    val refunded: Long get() = refunds.sumOf { it.amount }
    /** 还能退多少（不会小于 0）。 */
    val remaining: Long get() = (expense.amount - refunded).coerceAtLeast(0L)
    /** 退款后的实际支出。 */
    val net: Long get() = remaining
    val full: Boolean get() = refunds.isNotEmpty() && refunded >= expense.amount
    val partial: Boolean get() = refunds.isNotEmpty() && !full
}

/** “报销”页需要的一切。 */
data class ReimburseOverview(
    val pending: List<LedgerTransaction>,
    val done: List<LedgerTransaction>,
) {
    val pendingTotal: Long get() = pending.sumOf { it.amount }
    val doneTotal: Long get() = done.sumOf { it.amount }
}

/**
 * 退款与报销的计算（1.4.0-alpha04）。纯函数，便于测试。
 *
 * 统计口径（与一木记账、钱迹一致）：
 *  - 退款：一笔“退款”是记在账户上的收入（[LedgerTransaction.refundOf] 指向原支出），账户余额照常增加；
 *    统计里原支出按“退款后的实际支出”计算（全额退款就不再算支出），退款本身不算收入。
 *    原支出已经不在（被删掉）时，退款当作普通收入。
 *  - 报销：标了待报销 / 已报销的支出不算支出（是替别人垫的钱）；报销到账记的那笔收入
 *    （[LedgerTransaction.reimburseOf] 指向这几笔支出）也不算收入。账户余额都照实计算。
 */
object RefundMath {
    /** 一笔支出名下的退款。 */
    fun status(expense: LedgerTransaction, transactions: List<LedgerTransaction>): RefundStatus =
        RefundStatus(expense, transactions.filter { it.type == TxType.INCOME && it.refundOf == expense.id }.sortedBy { it.occurredAt })

    /** 每笔支出已经退了多少（只含有退款的支出）。 */
    fun refundTotals(transactions: List<LedgerTransaction>): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (tx in transactions) {
            val target = tx.refundOf ?: continue
            if (tx.type == TxType.INCOME) out[target] = (out[target] ?: 0L) + tx.amount
        }
        return out
    }

    /** 退款金额的校验；通过时返回 null。 */
    fun validateRefund(expense: LedgerTransaction?, amount: Long, transactions: List<LedgerTransaction>): String? = when {
        expense == null || expense.type != TxType.EXPENSE -> "只有支出可以退款"
        expense.reimburse != Reimburse.NONE -> "这笔已经标记为报销，不能再退款"
        amount <= 0L -> "退款金额要大于 0"
        amount > status(expense, transactions).remaining -> "退款金额超过了可退的 ${Money.format(status(expense, transactions).remaining)}"
        else -> null
    }

    /** 报销标记的校验（[ids] 里的每一笔都要是没有退款的支出）。 */
    fun validateReimburse(ids: Collection<String>, transactions: List<LedgerTransaction>): String? {
        val byId = transactions.associateBy { it.id }
        val refundedIds = transactions.mapNotNullTo(HashSet()) { it.refundOf.takeIf { _ -> it.type == TxType.INCOME } }
        for (id in ids) {
            val tx = byId[id] ?: return "找不到这笔记录"
            if (tx.type != TxType.EXPENSE) return "只有支出可以报销"
            if (tx.id in refundedIds) return "这笔已经有退款，不能再报销"
            if (tx.debtId != null) return "分期和借贷的流水不能报销"
        }
        return null
    }

    /**
     * 统计用的流水：退款冲减原支出、报销的支出与报销到账的收入去掉。账户余额不用它，用原始流水。
     * 在全部流水上算，再按月份筛选：跨月的退款冲减的是原支出所在的月份。
     */
    fun forStats(transactions: List<LedgerTransaction>): List<LedgerTransaction> {
        val byId = HashMap<String, LedgerTransaction>(transactions.size * 2)
        for (tx in transactions) byId[tx.id] = tx
        var refunded: HashMap<String, Long>? = null
        var any = false
        for (tx in transactions) {
            if (tx.reimburse != Reimburse.NONE || tx.reimburseOf.isNotEmpty()) any = true
            val target = tx.refundOf ?: continue
            if (tx.type != TxType.INCOME || byId[target]?.type != TxType.EXPENSE) continue
            any = true
            val map = refunded ?: HashMap<String, Long>().also { refunded = it }
            map[target] = (map[target] ?: 0L) + tx.amount
        }
        // 绝大多数账本没有退款与报销：原样返回，不复制
        if (!any) return transactions
        return transactions.mapNotNull { tx ->
            when (tx.type) {
                TxType.INCOME -> when {
                    tx.refundOf != null && byId[tx.refundOf]?.type == TxType.EXPENSE -> null
                    tx.reimburseOf.any { byId[it]?.type == TxType.EXPENSE } -> null
                    else -> tx
                }
                TxType.EXPENSE -> when {
                    tx.reimburse != Reimburse.NONE -> null
                    else -> {
                        val back = refunded?.get(tx.id) ?: 0L
                        when {
                            back <= 0L -> tx
                            back >= tx.amount -> null
                            else -> tx.copy(amount = tx.amount - back)
                        }
                    }
                }
                TxType.TRANSFER -> tx
            }
        }
    }

    /** 报销页：待报销在前（新的在前），已报销按时间倒序。 */
    fun reimbursements(transactions: List<LedgerTransaction>): ReimburseOverview {
        val expenses = transactions.filter { it.type == TxType.EXPENSE && it.reimburse != Reimburse.NONE }
        return ReimburseOverview(
            pending = expenses.filter { it.reimburse == Reimburse.PENDING }.sortedByDescending { it.occurredAt },
            done = expenses.filter { it.reimburse == Reimburse.DONE }.sortedByDescending { it.occurredAt },
        )
    }

    /** 关联到这几笔支出的报销到账收入。 */
    fun reimbursementIncomes(expenseIds: Collection<String>, transactions: List<LedgerTransaction>): List<LedgerTransaction> {
        val ids = expenseIds.toHashSet()
        return transactions.filter { it.type == TxType.INCOME && it.reimburseOf.any { id -> id in ids } }
    }
}
