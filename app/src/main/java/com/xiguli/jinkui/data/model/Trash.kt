package com.xiguli.jinkui.data.model

/**
 * 卡包回收站（1.4.0-alpha04 起）。删掉的卡片先放进回收站：仍然在加密数据库里（字段、附件、历史密码原样保留），
 * 不出现在卡包、搜索、体检与备份里；可以还原，也可以彻底删除；放进回收站满 [RETENTION_DAYS] 天后，
 * 下次解锁时自动彻底删除。纯函数，便于测试。
 */
object TrashMath {
    const val RETENTION_DAYS = 30
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val RETENTION_MS = RETENTION_DAYS * DAY_MS

    /** 放进回收站满 30 天、该彻底删除了（[deletedAt] 为 null 表示不在回收站）。 */
    fun isExpired(deletedAt: Long?, now: Long): Boolean = deletedAt != null && now - deletedAt >= RETENTION_MS

    /** 到期该彻底删除的卡片（放进回收站满 30 天）。 */
    fun expired(cards: List<Card>, now: Long): List<Card> = cards.filter { isExpired(it.deletedAt, now) }

    /** 还剩几天自动删除（至少 0）。 */
    fun daysLeft(card: Card, now: Long): Int {
        val at = card.deletedAt ?: return RETENTION_DAYS
        val left = RETENTION_MS - (now - at)
        return if (left <= 0) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    /** 回收站列表：最近删的在前。 */
    fun sorted(cards: List<Card>): List<Card> = cards.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
}
