package com.xiguli.jinkui.data.ledger

/**
 * “记一笔”的默认值。纯函数，便于单测。
 *
 * 参考钱迹的做法：默认账户是“这个分类上一次用的账户”——午饭总用微信、房租总走银行卡，
 * 选了分类账户就跟着对上，不用每次再点一下账户。这个分类从没记过时，退回到最近一笔用的账户。
 */
object EntryDefaults {
    /**
     * [categoryId] 所在一级分类（含其子分类）上一次用的、仍可用的账户；没有时取最近一笔的账户；
     * 再没有就取第一个可用账户。[active] 为未停用的账户。
     */
    fun accountFor(
        categoryId: String?,
        transactions: List<LedgerTransaction>,
        categories: List<LedgerCategory>,
        active: List<LedgerAccount>,
    ): String? {
        val usable = active.map { it.id }.toSet()
        val latest = transactions.filter { it.type != TxType.TRANSFER && it.accountId in usable }
        if (categoryId != null) {
            val byId = categories.associateBy { it.id }
            val top = topOf(categoryId, byId)
            latest
                .filter { tx -> tx.categoryId != null && topOf(tx.categoryId, byId) == top }
                .maxWithOrNull(compareBy<LedgerTransaction> { it.createdAt }.thenBy { it.occurredAt })
                ?.let { return it.accountId }
        }
        return transactions
            .filter { it.accountId in usable }
            .maxWithOrNull(compareBy<LedgerTransaction> { it.createdAt }.thenBy { it.occurredAt })
            ?.accountId
            ?: active.firstOrNull()?.id
    }

    /**
     * 账本首页“快速记一笔”的常用分类：最近 [days] 天里用得最多的一级支出分类在前，
     * 不够 [count] 个时按分类自身的顺序补齐。隐藏的分类不出现。
     */
    fun frequentCategories(
        transactions: List<LedgerTransaction>,
        categories: List<LedgerCategory>,
        nowMillis: Long,
        count: Int = 6,
        days: Int = 60,
        type: TxType = TxType.EXPENSE,
    ): List<LedgerCategory> {
        val byId = categories.associateBy { it.id }
        val tops = categories.filter { it.type == type && it.parentId == null && !it.archived }.sortedBy { it.position }
        val since = nowMillis - days * 86_400_000L
        val usage = transactions
            .filter { it.type == type && it.occurredAt >= since && it.categoryId != null }
            .groupingBy { topOf(it.categoryId!!, byId) }
            .eachCount()
        val ranked = tops
            .filter { (usage[it.id] ?: 0) > 0 }
            .sortedWith(compareByDescending<LedgerCategory> { usage[it.id] ?: 0 }.thenBy { it.position })
        return (ranked + tops.filter { it !in ranked }).take(count)
    }

    private fun topOf(id: String, byId: Map<String, LedgerCategory>): String =
        byId[id]?.parentId ?: id
}
