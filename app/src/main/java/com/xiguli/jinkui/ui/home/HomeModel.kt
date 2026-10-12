package com.xiguli.jinkui.ui.home

import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.SortOrder
import com.xiguli.jinkui.data.model.deckOrder
import com.xiguli.jinkui.data.model.sortCards

data class HomeUi(
    val total: Int = 0,
    /** 筛选项：键 + 显示名。先是出现过的卡片类型，再是标签。 */
    val filters: List<Pair<String, String>> = emptyList(),
    /** 正在搜索或筛选时的结果；为 null 表示显示默认分区。 */
    val results: List<Card>? = null,
    val pinned: List<Card> = emptyList(),
    val recent: List<Card> = emptyList(),
    val all: List<Card> = emptyList(),
    /** 牌堆里的卡：筛选时是结果，否则是全部（置顶在前）。 */
    val deck: List<Card> = emptyList(),
    /** 安全体检发现的问题数，显示在“更多”里。 */
    val issues: Int = 0,
)

/**
 * 首页要展示的全部内容。纯函数，不碰界面与线程，便于在后台计算和单元测试。
 * [filter] 的键："p:" 置顶，"o:" 两步验证，"t:类型" 或 "g:标签"；null 为全部。
 */
fun buildHomeUi(
    cards: List<Card>,
    query: String,
    filter: String?,
    sortOrder: SortOrder,
    issues: Int,
): HomeUi {
    val sorted = sortCards(cards, sortOrder)
    val types = CardType.entries.filter { type -> cards.any { it.type == type } }
    val tags = cards.flatMap { it.tags }.distinct().sorted()
    // 1.5.0-alpha01：先按类型筛（参考 Bitwarden 的类型、Apple 密码的分类），再是两步验证、置顶与标签
    val filters = (if (types.size > 1) types.map { "t:" + it.key to it.label } else emptyList()) +
        // 有两步验证码的卡片时多一个“两步验证”快速筛选：结果行上直接显示实时验证码（参考 Aegis 的验证码列表）
        (if (cards.any { ListLayout.totpField(it) != null }) listOf("o:" to "两步验证") else emptyList()) +
        (if (cards.any { it.pinned }) listOf("p:" to "置顶") else emptyList()) +
        tags.map { "g:$it" to "#$it" }
    val active = filter?.takeIf { key -> filters.any { it.first == key } }
    if (query.isNotBlank() || active != null) {
        val results = sorted
            .filter { card ->
                val inFilter = when {
                    active == null -> true
                    active == "p:" -> card.pinned
                    active == "o:" -> ListLayout.totpField(card) != null
                    active.startsWith("t:") -> card.type.key == active.substring(2)
                    else -> active.substring(2) in card.tags
                }
                inFilter && card.matches(query)
            }
            .sortedByDescending { it.pinned }
        return HomeUi(total = cards.size, filters = filters, results = results, deck = results, issues = issues)
    }
    return HomeUi(
        total = cards.size,
        filters = filters,
        pinned = sorted.filter { it.pinned },
        recent = cards.filter { !it.pinned && it.lastUsedAt > it.createdAt }
            .sortedByDescending { it.lastUsedAt }
            .take(3),
        all = sorted,
        deck = deckOrder(cards, sortOrder),
        issues = issues,
    )
}
