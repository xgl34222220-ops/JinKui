package com.xiguli.jinkui.data.ledger

import java.time.LocalDate

/**
 * 按人汇总的借贷（1.4.0-alpha04 重做“欠款 / 借贷”页，参考钱迹 / 一木记账的“借贷”：一人一行、只看还欠多少）。
 * 每个对方账户（“借出 · 小李” / “借入 · 老王”）是一个人；他名下的每一笔借款（[LoanView]）合在一起算。
 */
data class PersonDebt(
    val account: LedgerAccount,
    val person: String,
    val direction: LoanDirection,
    /** 一共借出 / 借入了多少。 */
    val total: Long,
    /** 已经收回 / 还了多少。 */
    val repaid: Long,
    /** 还欠多少（不会小于 0）。 */
    val remaining: Long,
    /** 最早的预计还款日（只看没还清的那几笔）。 */
    val due: LocalDate?,
    val interestPaid: Long,
    val loans: List<LoanView>,
) {
    val settled: Boolean get() = remaining <= 0L && loans.all { it.settled }
    fun overdue(today: LocalDate): Boolean = !settled && due != null && today > due
    /** 还款进度 0..1。 */
    val progress: Float get() = if (total <= 0L) 0f else (repaid.toFloat() / total).coerceIn(0f, 1f)
    /** 没还清的几笔，最早借的在前（收款 / 还款先冲最早的那笔）。 */
    val open: List<LoanView> get() = loans.filter { !it.settled }.sortedWith(compareBy<LoanView> { it.loan.date }.thenBy { it.loan.createdAt })
    /** 全部记录（借出 / 借入、收款 / 还款、利息、免除），新的在前。 */
    val entries: List<LedgerTransaction> get() = loans.flatMap { it.entries }.distinctBy { it.id }.sortedByDescending { it.occurredAt }
}

object PersonDebtMath {
    /** 全部的人：[direction] 这一边，没还清的在前（还欠多的在前），还清的在后。 */
    fun people(loans: List<LoanView>, direction: LoanDirection, today: LocalDate): List<PersonDebt> =
        loans.filter { it.direction == direction }
            .groupBy { it.account.id }
            .map { (_, list) ->
                val account = list.first().account
                val open = list.filter { !it.settled }
                PersonDebt(
                    account = account,
                    person = list.first().person,
                    direction = direction,
                    total = list.sumOf { it.principal },
                    repaid = list.sumOf { it.repaid.coerceAtMost(it.principal) },
                    remaining = list.sumOf { it.remaining },
                    due = open.mapNotNull { it.loan.dueDate }.minOrNull(),
                    interestPaid = list.sumOf { it.interestPaid },
                    loans = list,
                )
            }
            .sortedWith(compareBy<PersonDebt> { it.settled }.thenByDescending { it.overdue(today) }.thenByDescending { it.remaining }.thenBy { it.person })

    /** 某一个人（对方账户）。 */
    fun person(loans: List<LoanView>, accountId: String, today: LocalDate): PersonDebt? {
        val direction = loans.firstOrNull { it.account.id == accountId }?.direction ?: return null
        return people(loans, direction, today).firstOrNull { it.account.id == accountId }
    }

    /**
     * 收款 / 还款按人记：本金先冲最早借的那笔，冲完再冲下一笔。超过还欠的总数时返回 null。
     * 只有利息（本金为 0）时返回空列表。
     */
    fun split(person: PersonDebt, amount: Long): List<Pair<LoanView, Long>>? {
        if (amount <= 0L) return emptyList()
        if (amount > person.remaining) return null
        var left = amount
        val out = ArrayList<Pair<LoanView, Long>>()
        for (loan in person.open) {
            if (left <= 0L) break
            val part = minOf(left, loan.remaining)
            if (part == 0L) continue
            out += loan to part
            left -= part
        }
        return if (left > 0L) null else out
    }
}
