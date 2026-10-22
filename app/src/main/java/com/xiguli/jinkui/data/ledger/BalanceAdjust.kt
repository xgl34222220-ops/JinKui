package com.xiguli.jinkui.data.ledger

/**
 * 1.5.0-alpha01：手动“修改余额”（参考一木记账、钱迹的“修改余额 / 余额校正”，只借鉴交互）。
 *
 * 用户直接填账户现在应有的余额（信用卡、花呗填负数 = 当前欠款），金匮记一笔差额：
 * - [BalanceAdjustMode.NOT_COUNTED]（默认）：差额记成内部“余额调整”账户与这个账户之间的转账——
 *   不计入收支统计；“余额调整”本身不计入资产、负债与净资产（与账单导入的“余额补齐”、不经过账户的借贷同一种）；
 * - [BalanceAdjustMode.AS_INCOME_EXPENSE]：多出来的记一笔收入，少了的记一笔支出（分类用“其他收入 / 其他支出”）。
 *
 * 不改数据库与备份结构：只是普通的流水（以及需要时新建的停用“余额调整”账户）。删掉这笔流水就撤销了这次修改。
 */
enum class BalanceAdjustMode { NOT_COUNTED, AS_INCOME_EXPENSE }

data class BalanceAdjustPlan(
    val transaction: LedgerTransaction,
    /** 还没有“余额调整”账户时新建的那个，需要一并写入；已有时为 null。 */
    val newAdjustAccount: LedgerAccount?,
)

object BalanceAdjust {
    const val NOTE = "余额调整"

    /** 可以修改余额的账户：资金账户与信用账户（不含借贷对象与内部“余额调整”）。 */
    fun canAdjust(account: LedgerAccount): Boolean =
        account.kind != AccountKind.ADJUST && LoanDirection.of(account.kind) == null

    /**
     * 把 [accountId] 的余额从 [current] 改成 [target] 需要记的那一笔；两者相等时返回 null（什么都不用记）。
     * 找不到账户或账户不能修改余额时抛 [IllegalArgumentException]。
     */
    fun plan(
        data: LedgerData,
        accountId: String,
        current: Long,
        target: Long,
        mode: BalanceAdjustMode,
        at: Long,
        now: Long,
        newId: () -> String = LedgerIds::newId,
    ): BalanceAdjustPlan? {
        val account = data.accounts.firstOrNull { it.id == accountId } ?: throw IllegalArgumentException("找不到这个账户")
        require(canAdjust(account)) { "这个账户不能直接修改余额" }
        require(target in -Money.MAX_CENTS..Money.MAX_CENTS) { "金额太大了" }
        val diff = target - current
        if (diff == 0L) return null
        val amount = kotlin.math.abs(diff)
        require(amount <= Money.MAX_CENTS) { "差额太大了" }
        return when (mode) {
            BalanceAdjustMode.NOT_COUNTED -> {
                val (adjust, created) = DebtMath.loanFundAccount(data.accounts, null, now, newId)
                val tx = LedgerTransaction(
                    id = newId(), type = TxType.TRANSFER, amount = amount, occurredAt = at,
                    accountId = if (diff > 0) adjust.id else account.id,
                    toAccountId = if (diff > 0) account.id else adjust.id,
                    note = NOTE, createdAt = now, updatedAt = now,
                )
                BalanceAdjustPlan(tx, created)
            }
            BalanceAdjustMode.AS_INCOME_EXPENSE -> {
                val type = if (diff > 0) TxType.INCOME else TxType.EXPENSE
                val tx = LedgerTransaction(
                    id = newId(), type = type, amount = amount, occurredAt = at, accountId = account.id,
                    categoryId = otherCategory(data.categories, type)?.id, note = NOTE, createdAt = now, updatedAt = now,
                )
                BalanceAdjustPlan(tx, null)
            }
        }
    }

    /** “其他收入 / 其他支出”（名字以“其他”开头的一级分类），没有时用这一类的第一个一级分类。 */
    fun otherCategory(categories: List<LedgerCategory>, type: TxType): LedgerCategory? {
        val tops = categories.filter { it.type == type && it.parentId == null && !it.archived }.sortedBy { it.position }
        return tops.firstOrNull { it.name.startsWith("其他") || it.name.startsWith("其它") } ?: tops.firstOrNull()
    }

    /**
     * 这笔流水是不是一笔“不计入收支”的余额调整：真实账户与内部“余额调整”账户之间、不属于任何借贷的转账。
     * 返回 (真实账户, 对它余额的影响)；不是时返回 null。不经过账户的借贷（对方是借贷对象）不算。
     */
    fun adjustmentOf(tx: LedgerTransaction, accountsById: Map<String, LedgerAccount>): Pair<LedgerAccount, Long>? {
        if (tx.type != TxType.TRANSFER || tx.debtId != null) return null
        val from = accountsById[tx.accountId] ?: return null
        val to = tx.toAccountId?.let { accountsById[it] } ?: return null
        return when {
            from.kind == AccountKind.ADJUST && canAdjust(to) -> to to tx.amount
            to.kind == AccountKind.ADJUST && canAdjust(from) -> from to -tx.amount
            else -> null
        }
    }
}
