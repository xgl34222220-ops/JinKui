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

    const val WALLET_COUNT = 3

    /** 钱包（列表最上面叠成一摞的卡面）：银行卡与 U 卡，置顶的在前，其次最近用过的，最多三张。 */
    fun wallet(cards: List<Card>): List<Card> = cards
        .filter { it.type == com.xiguli.jinkui.data.model.CardType.BANK || it.type == com.xiguli.jinkui.data.model.CardType.UKEY }
        .sortedWith(compareByDescending<Card> { it.pinned }.thenByDescending { it.lastUsedAt })
        .take(WALLET_COUNT)

    /** 点分组标题上的排序按钮时，轮换到下一种排序。 */
    fun nextSort(order: SortOrder): SortOrder = SortOrder.entries[(order.ordinal + 1) % SortOrder.entries.size]

    /** 行尾复制按钮复制哪个字段：优先敏感字段（密码、卡号），没有就取最要紧的明文字段。 */
    fun copyTarget(card: Card): CardField? = card.quickCopyFields.lastOrNull()

    /**
     * 行上向左滑复制的字段：有两个快捷字段时取明文的那个（账号、卡号持卡人等），
     * 行尾按钮复制敏感的那个（密码、卡号）——一行上两种复制各有一个大手势 / 一个按钮。
     */
    fun swipeCopyTarget(card: Card): CardField? = card.quickCopyFields.firstOrNull()

    /** 这张卡片的两步验证字段（筛选“两步验证”时在行上直接显示实时验证码）。 */
    fun totpField(card: Card): CardField? =
        card.fields.firstOrNull { it.kind == com.xiguli.jinkui.data.model.FieldKind.TOTP && it.value.isNotBlank() }

    /** 主信息是不是打了码的编号（用等宽字体显示）。 */
    fun isMasked(text: String): Boolean = text.startsWith("••••")
}
