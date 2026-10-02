package com.xiguli.jinkui.data.model

/** 卡片类型预设。[key] 会写入数据库与备份文件，上线后不要改动。 */
enum class CardType(val key: String, val label: String) {
    LOGIN("login", "登录密码"),
    BANK("bank", "银行卡"),
    UKEY("ukey", "U 卡 / 硬件密钥"),
    ID_CARD("id_card", "身份证"),
    PASSPORT("passport", "护照"),
    SOCIAL("social", "社保卡"),
    DRIVER("driver", "驾驶证"),
    MEMBER("member", "会员卡"),
    ACCESS("access", "门禁卡"),
    WIFI("wifi", "Wi-Fi"),
    CUSTOM("custom", "自定义卡片");

    companion object {
        fun of(key: String?): CardType = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

/** 字段的输入与展示方式。 */
enum class FieldKind(val key: String) {
    TEXT("text"),
    PASSWORD("password"),
    NUMBER("number"),
    DATE("date"),
    URL("url"),
    PHONE("phone");

    companion object {
        fun of(key: String?): FieldKind = entries.firstOrNull { it.key == key } ?: TEXT
    }
}

data class CardField(
    val id: String,
    val label: String,
    val value: String,
    /** 敏感字段默认打码，点眼睛才显示。 */
    val sensitive: Boolean,
    val kind: FieldKind = FieldKind.TEXT,
)

data class Attachment(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val createdAt: Long,
)

data class Card(
    val id: String,
    val type: CardType,
    val title: String,
    val tags: List<String> = emptyList(),
    /** [CardPalette] 中的序号；[CardPalette.AUTO] 表示跟随卡片类型的默认卡面。 */
    val color: Int = CardPalette.AUTO,
    val fields: List<CardField> = emptyList(),
    val note: String = "",
    val attachments: List<Attachment> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastUsedAt: Long = 0L,
) {
    /** 列表副标题：第一个非敏感且有内容的字段。 */
    val subtitle: String
        get() = fields.firstOrNull { !it.sensitive && it.value.isNotBlank() }?.value ?: type.label

    /**
     * 卡面与列表上的一行提示。只有“数字类”的敏感字段（卡号等）会露出末四位，
     * 密码类字段永远不出现在这里。
     */
    val faceHint: String
        get() {
            val number = fields.firstOrNull { it.sensitive && it.kind == FieldKind.NUMBER && it.value.count(Char::isDigit) >= 8 }
            if (number != null) return "•••• " + number.value.filter(Char::isDigit).takeLast(4)
            return subtitle
        }

    /**
     * 最值得“一键复制”的字段：一个明文字段（账号、卡主等，不取网址）加一个敏感字段（密码、卡号）。
     * 都没有时退而取第一个有内容的字段。
     */
    val quickCopyFields: List<CardField>
        get() {
            val filled = fields.filter { it.value.isNotBlank() }
            val plain = filled.firstOrNull { !it.sensitive && it.kind != FieldKind.URL }
            val secret = filled.firstOrNull { it.sensitive }
            return listOfNotNull(plain, secret).ifEmpty { filled.take(1) }
        }

    fun matches(query: String): Boolean {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val haystack = buildString {
            append(title).append('\n')
            append(type.label).append('\n')
            tags.forEach { append(it).append('\n') }
            append(note)
        }.lowercase()
        return tokens.all { haystack.contains(it) }
    }
}

/** 历史密码：敏感字段被改动时保存的旧值。 */
data class HistoryEntry(
    val id: String,
    val label: String,
    val value: String,
    val changedAt: Long,
)

/** 尚未保存的新附件（明文仅存在于内存，保存时加密落盘）。 */
class PendingAttachment(
    val id: String,
    val name: String,
    val mime: String,
    val bytes: ByteArray,
)

/** 卡片色板的 ARGB 值；界面层再转成 Color。 */
object CardPalette {
    /** 不指定颜色：使用该类型自己的卡面配色。 */
    const val AUTO = -1

    val colors: List<Long> = listOf(
        0xFF3A3632, // 墨
        0xFF8F6420, // 赤金
        0xFF2F6B57, // 松绿
        0xFF3B5B8C, // 靛蓝
        0xFFA8483A, // 朱砂
        0xFF6B4E8C, // 黛紫
        0xFF2E7A86, // 石青
        0xFF6E6A62, // 岩灰
    )
    val names: List<String> = listOf("墨", "赤金", "松绿", "靛蓝", "朱砂", "黛紫", "石青", "岩灰")

    fun argb(index: Int): Long = colors[index.mod(colors.size)]
}

/** 长数字（卡号、手机号）按四位一组显示，方便核对；只影响显示，不改动保存的值。 */
fun groupDigits(value: String): String {
    val compact = value.filterNot { it == ' ' }
    if (compact.length < 12 || !compact.all(Char::isDigit)) return value
    return compact.chunked(4).joinToString(" ")
}

/** 牌堆里的顺序：置顶在前，其余按名称。 */
fun deckOrder(cards: List<Card>): List<Card> =
    cards.sortedWith(compareByDescending<Card> { it.pinned }.thenBy { it.title.lowercase() })
