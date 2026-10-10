package com.xiguli.jinkui.data.backup

import com.xiguli.jinkui.core.crypto.BackupFormatException
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerJson
import com.xiguli.jinkui.data.model.Attachment
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardPalette
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.data.model.HistoryEntry
import java.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** 一张卡片在备份里的完整内容：卡片本身、附件原始数据、历史密码。 */
class BackupCard(
    val card: Card,
    val attachmentData: Map<String, ByteArray>,
    val history: List<HistoryEntry>,
)

/** 一份备份的全部内容。0.6.0 之前的备份没有账本，[ledger] 为空。 */
class BackupContent(
    val cards: List<BackupCard>,
    val ledger: LedgerData,
)

/**
 * 备份内容的 JSON 结构。
 *
 *  - schema 1（0.1.0 – 0.5.3）：只有 cards。
 *  - schema 2（0.6.0）：增加 ledger（账户、分类、流水、预算）。
 *  - schema 3（0.7.0）：ledger 里增加 recurring（周期记账规则）；卡片字段可以是 totp（两步验证密钥）。
 *  - schema 4（0.9.0 起）：ledger 里增加 tombstones（删除标记：删掉的周期生成流水与周期规则的 ID）。
 *    仍能导入 schema 1–3 的旧备份（没有删除标记，按空处理）；旧版金匮会拒绝 schema 4，
 *    而不是悄悄丢掉删除标记、把删掉的流水又加回来。
 *
 * 这段 JSON 只在内存里以明文存在，
 * 落盘前一定经过 [com.xiguli.jinkui.core.crypto.BackupCodec] 加密。
 */
object BackupJson {
    private const val APP = "jinkui"
    const val SCHEMA = 4
    private const val MAX_CARDS = 20_000

    fun encode(cards: List<BackupCard>, exportedAt: Long, ledger: LedgerData = LedgerData.EMPTY): ByteArray {
        val root = JSONObject()
        root.put("app", APP)
        root.put("schema", SCHEMA)
        root.put("exportedAt", exportedAt)
        val array = JSONArray()
        for (item in cards) array.put(cardToJson(item))
        root.put("cards", array)
        root.put("ledger", LedgerJson.toBackup(ledger))
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    /** 只取卡片部分。 */
    @Throws(BackupFormatException::class)
    fun decode(bytes: ByteArray): List<BackupCard> = decodeAll(bytes).cards

    @Throws(BackupFormatException::class)
    fun decodeAll(bytes: ByteArray): BackupContent {
        try {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            if (root.optString("app") != APP) throw BackupFormatException("备份内容不是金匮的数据")
            val schema = root.optInt("schema", -1)
            if (schema !in 1..SCHEMA) throw BackupFormatException("备份数据版本 $schema 暂不支持，请升级金匮")
            val array = root.getJSONArray("cards")
            if (array.length() > MAX_CARDS) throw BackupFormatException("备份中的卡片数量异常")
            val cards = (0 until array.length()).map { cardFromJson(array.getJSONObject(it)) }
            val ledger = root.optJSONObject("ledger")?.let(LedgerJson::fromBackup) ?: LedgerData.EMPTY
            return BackupContent(cards, ledger)
        } catch (e: JSONException) {
            throw BackupFormatException("备份内容结构不完整")
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("备份内容包含无效数据")
        }
    }

    private fun cardToJson(item: BackupCard): JSONObject {
        val card = item.card
        val json = JSONObject()
        json.put("id", card.id)
        json.put("type", card.type.key)
        json.put("title", card.title)
        json.put("tags", JSONArray(card.tags))
        json.put("color", card.color)
        json.put("note", card.note)
        json.put("pinned", card.pinned)
        json.put("createdAt", card.createdAt)
        json.put("updatedAt", card.updatedAt)
        json.put("lastUsedAt", card.lastUsedAt)
        val fields = JSONArray()
        for (field in card.fields) {
            fields.put(
                JSONObject()
                    .put("id", field.id)
                    .put("label", field.label)
                    .put("value", field.value)
                    .put("sensitive", field.sensitive)
                    .put("kind", field.kind.key),
            )
        }
        json.put("fields", fields)
        val attachments = JSONArray()
        for (attachment in card.attachments) {
            val data = item.attachmentData[attachment.id] ?: continue
            attachments.put(
                JSONObject()
                    .put("id", attachment.id)
                    .put("name", attachment.name)
                    .put("mime", attachment.mime)
                    .put("createdAt", attachment.createdAt)
                    .put("data", Base64.getEncoder().encodeToString(data)),
            )
        }
        json.put("attachments", attachments)
        val history = JSONArray()
        for (entry in item.history) {
            history.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("label", entry.label)
                    .put("value", entry.value)
                    .put("changedAt", entry.changedAt),
            )
        }
        json.put("history", history)
        return json
    }

    private fun cardFromJson(json: JSONObject): BackupCard {
        val id = json.getString("id").also(::requireId)
        val fieldsJson = json.getJSONArray("fields")
        val fields = (0 until fieldsJson.length()).map { index ->
            val field = fieldsJson.getJSONObject(index)
            val kind = FieldKind.of(field.optString("kind"))
            CardField(
                id = field.getString("id").also(::requireId),
                label = field.getString("label"),
                value = field.getString("value"),
                // 两步验证密钥无论备份里怎么标，导入后都是敏感字段
                sensitive = field.optBoolean("sensitive", false) || kind == FieldKind.TOTP,
                kind = kind,
            )
        }
        val data = HashMap<String, ByteArray>()
        val attachments = ArrayList<Attachment>()
        val attachmentsJson = json.optJSONArray("attachments") ?: JSONArray()
        for (index in 0 until attachmentsJson.length()) {
            val item = attachmentsJson.getJSONObject(index)
            val attachmentId = item.getString("id").also(::requireId)
            val bytes = Base64.getDecoder().decode(item.getString("data"))
            data[attachmentId] = bytes
            attachments += Attachment(
                id = attachmentId,
                name = item.optString("name", "附件"),
                mime = item.optString("mime", "image/jpeg"),
                size = bytes.size.toLong(),
                createdAt = item.optLong("createdAt", 0L),
            )
        }
        val historyJson = json.optJSONArray("history") ?: JSONArray()
        val history = (0 until historyJson.length()).map { index ->
            val item = historyJson.getJSONObject(index)
            HistoryEntry(
                id = item.getString("id").also(::requireId),
                label = item.getString("label"),
                value = item.getString("value"),
                changedAt = item.optLong("changedAt", 0L),
            )
        }
        val tagsJson = json.optJSONArray("tags") ?: JSONArray()
        val card = Card(
            id = id,
            type = CardType.of(json.optString("type")),
            title = json.getString("title"),
            tags = (0 until tagsJson.length()).map { tagsJson.getString(it) },
            color = json.optInt("color", CardPalette.AUTO),
            fields = fields,
            note = json.optString("note", ""),
            attachments = attachments,
            pinned = json.optBoolean("pinned", false),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            lastUsedAt = json.optLong("lastUsedAt", 0L),
        )
        return BackupCard(card, data, history)
    }

    /** ID 会被用作附件文件名，只接受 UUID 形态，杜绝备份文件借此做路径穿越。 */
    private fun requireId(id: String) {
        require(ID_PATTERN.matches(id)) { "非法 ID" }
    }

    private val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
}
