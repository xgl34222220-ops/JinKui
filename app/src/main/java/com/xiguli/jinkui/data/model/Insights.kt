package com.xiguli.jinkui.data.model

import com.xiguli.jinkui.core.crypto.PasswordGenerator
import java.time.LocalDate

/** 列表与牌堆的排序方式。[key] 会写入偏好设置。 */
enum class SortOrder(val key: String, val label: String) {
    TITLE("title", "名称"),
    UPDATED("updated", "最近修改"),
    USED("used", "最近使用"),
    TYPE("type", "类型");

    companion object {
        fun of(key: String?): SortOrder = entries.firstOrNull { it.key == key } ?: TITLE
    }
}

fun sortCards(cards: List<Card>, order: SortOrder): List<Card> = when (order) {
    SortOrder.TITLE -> cards.sortedBy { it.title.lowercase() }
    SortOrder.UPDATED -> cards.sortedWith(compareByDescending<Card> { it.updatedAt }.thenBy { it.title.lowercase() })
    SortOrder.USED -> cards.sortedWith(compareByDescending<Card> { it.lastUsedAt }.thenBy { it.title.lowercase() })
    SortOrder.TYPE -> cards.sortedWith(compareBy<Card> { it.type.ordinal }.thenBy { it.title.lowercase() })
}

enum class HealthKind { WEAK, REUSED, EXPIRED, EXPIRING }

/** 体检发现的一个问题。[detail] 只描述问题，永远不包含密码本身。 */
class HealthIssue(val kind: HealthKind, val card: Card, val detail: String)

class HealthReport(val issues: List<HealthIssue>, val checkedPasswords: Int) {
    val isClean: Boolean get() = issues.isEmpty()

    fun of(kind: HealthKind): List<HealthIssue> = issues.filter { it.kind == kind }
}

/**
 * 安全体检：弱密码、重复使用的密码、已过期或即将到期的卡片。
 * 全部在本机内存里完成，不联网比对任何泄露库。
 */
object PasswordHealth {
    const val MIN_LENGTH = 8
    private const val WEAK_BITS = 45.0

    fun isWeak(password: String): Boolean =
        password.length < MIN_LENGTH || PasswordGenerator.estimateBits(password) < WEAK_BITS

    fun analyze(cards: List<Card>, today: LocalDate): HealthReport {
        // 只检查“密码”类型的字段；取款密码、安全码这类固定位数的数字不算弱密码。
        val passwords = cards.flatMap { card ->
            card.fields.filter { it.kind == FieldKind.PASSWORD && it.value.isNotBlank() }.map { card to it }
        }
        val issues = ArrayList<HealthIssue>()

        for ((card, field) in passwords) {
            if (isWeak(field.value)) {
                val reason = if (field.value.length < MIN_LENGTH) "不足 $MIN_LENGTH 位" else "过于简单"
                issues += HealthIssue(HealthKind.WEAK, card, "「${field.label}」$reason")
            }
        }

        for (group in passwords.groupBy { it.second.value }.values) {
            val distinctCards = group.distinctBy { it.first.id }
            if (distinctCards.size < 2) continue
            for ((card, field) in distinctCards) {
                issues += HealthIssue(HealthKind.REUSED, card, "「${field.label}」与另外 ${distinctCards.size - 1} 张卡片相同")
            }
        }

        for (card in cards) {
            val days = Expiry.daysLeft(card, today) ?: continue
            when {
                days < 0 -> issues += HealthIssue(HealthKind.EXPIRED, card, "已过期 ${-days} 天")
                days == 0L -> issues += HealthIssue(HealthKind.EXPIRING, card, "今天到期")
                days <= Expiry.WARN_DAYS -> issues += HealthIssue(HealthKind.EXPIRING, card, "$days 天后到期")
            }
        }
        return HealthReport(issues, passwords.size)
    }
}

/** 所有标签及其卡片数，按名称排序。 */
fun tagCounts(cards: List<Card>): List<Pair<String, Int>> =
    cards.flatMap { it.tags }.groupingBy { it }.eachCount().toList().sortedBy { it.first }

/** 重命名标签；新名字已存在时自动合并，不会出现重复标签。 */
fun Card.withTagRenamed(old: String, new: String): Card {
    val target = new.trim()
    if (old !in tags || target.isEmpty()) return this
    return copy(tags = tags.map { if (it == old) target else it }.distinct())
}

fun Card.withoutTag(tag: String): Card = if (tag in tags) copy(tags = tags - tag) else this
