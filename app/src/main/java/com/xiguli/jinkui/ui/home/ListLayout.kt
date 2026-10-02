package com.xiguli.jinkui.ui.home

import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.SortOrder

/** 列表视图里与界面框架无关的判断，纯函数，便于单测。 */
object ListLayout {
    /** 卡片少于这个数时不显示“最近使用”——那时它和“全部”几乎是同一批卡，重复而已。 */
    const val RECENT_MIN_TOTAL = 6
    const val RECENT_COUNT = 4

    /** “最近使用”：用过的、没置顶的卡片里最近的几张。置顶的已经在最上面，不再重复。 */
    fun recent(cards: List<Card>): List<Card> {
        if (cards.size < RECENT_MIN_TOTAL) return emptyList()
        return cards
            .filter { !it.pinned && it.lastUsedAt > it.createdAt }
            .sortedByDescending { it.lastUsedAt }
            .take(RECENT_COUNT)
    }

    /** 点分组标题上的排序按钮时，轮换到下一种排序。 */
    fun nextSort(order: SortOrder): SortOrder = SortOrder.entries[(order.ordinal + 1) % SortOrder.entries.size]

    /** 行尾复制按钮复制哪个字段：优先敏感字段（密码、卡号），没有就取最要紧的明文字段。 */
    fun copyTarget(card: Card): CardField? = card.quickCopyFields.lastOrNull()

    /** 主信息是不是打了码的编号（用等宽字体显示）。 */
    fun isMasked(text: String): Boolean = text.startsWith("••••")
}
