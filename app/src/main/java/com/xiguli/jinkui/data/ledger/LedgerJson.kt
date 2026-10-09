package com.xiguli.jinkui.data.ledger

import org.json.JSONArray
import org.json.JSONObject

/**
 * 账本数据的 JSON 形态，两处用到：
 *  - 数据库里每条记录的加密载荷（金额、备注、名称等先序列化成 JSON，再整体做字段级 AES-GCM）；
 *  - 加密备份文件里的 "ledger" 一节。
 *
 * 这里产出的 JSON 都是明文，只在内存里短暂存在，落盘前一定经过加密。
 */
object LedgerJson {
    private const val MAX_RECORDS = 500_000
    private val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")

    // ── 数据库载荷：只放需要加密的内容，结构列（ID、类型、时间、关联 ID）另存 ─────────────

    fun accountPayload(account: LedgerAccount): String = JSONObject()
        .put("name", account.name)
        .put("kind", account.kind.key)
        .put("opening", account.openingBalance)
        .put("cardId", account.cardId ?: JSONObject.NULL)
        .toString()

    class AccountPayload(val name: String, val kind: AccountKind, val opening: Long, val cardId: String?)

    fun readAccountPayload(text: String): AccountPayload {
        val json = JSONObject(text)
        return AccountPayload(
            name = json.optString("name", "账户"),
            kind = AccountKind.of(json.optString("kind")),
            opening = json.optLong("opening", 0L),
            cardId = json.optNullableString("cardId"),
        )
    }

    fun categoryPayload(category: LedgerCategory): String = JSONObject()
        .put("name", category.name)
        .put("icon", category.icon)
        .toString()

    class CategoryPayload(val name: String, val icon: String)

    fun readCategoryPayload(text: String): CategoryPayload {
        val json = JSONObject(text)
        return CategoryPayload(json.optString("name", "分类"), json.optString("icon", "more"))
    }

    fun transactionPayload(tx: LedgerTransaction): String = JSONObject()
        .put("amount", tx.amount)
        .put("note", tx.note)
        .put("tags", JSONArray(tx.tags))
        .toString()

    class TransactionPayload(val amount: Long, val note: String, val tags: List<String>)

    fun readTransactionPayload(text: String): TransactionPayload {
        val json = JSONObject(text)
        return TransactionPayload(json.getLong("amount"), json.optString("note", ""), json.optStringList("tags"))
    }

    fun budgetPayload(budget: LedgerBudget): String = JSONObject().put("amount", budget.amount).toString()

    fun readBudgetAmount(text: String): Long = JSONObject(text).getLong("amount")

    // ── 备份 ──────────────────────────────────────────────────────

    fun toBackup(data: LedgerData): JSONObject {
        val accounts = JSONArray()
        for (a in data.accounts) {
            accounts.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("kind", a.kind.key)
                    .put("opening", a.openingBalance)
                    .put("cardId", a.cardId ?: JSONObject.NULL)
                    .put("archived", a.archived)
                    .put("position", a.position)
                    .put("createdAt", a.createdAt)
                    .put("updatedAt", a.updatedAt),
            )
        }
        val categories = JSONArray()
        for (c in data.categories) {
            categories.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("type", c.type.key)
                    .put("parentId", c.parentId ?: JSONObject.NULL)
                    .put("icon", c.icon)
                    .put("position", c.position)
                    .put("archived", c.archived)
                    .put("updatedAt", c.updatedAt),
            )
        }
        val transactions = JSONArray()
        for (t in data.transactions) {
            transactions.put(
                JSONObject()
                    .put("id", t.id)
                    .put("type", t.type.key)
                    .put("amount", t.amount)
                    .put("occurredAt", t.occurredAt)
                    .put("accountId", t.accountId)
                    .put("toAccountId", t.toAccountId ?: JSONObject.NULL)
                    .put("categoryId", t.categoryId ?: JSONObject.NULL)
                    .put("note", t.note)
                    .put("tags", JSONArray(t.tags))
                    .put("createdAt", t.createdAt)
                    .put("updatedAt", t.updatedAt),
            )
        }
        val budgets = JSONArray()
        for (b in data.budgets) {
            budgets.put(
                JSONObject()
                    .put("id", b.id)
                    .put("categoryId", b.categoryId ?: JSONObject.NULL)
                    .put("amount", b.amount)
                    .put("updatedAt", b.updatedAt),
            )
        }
        return JSONObject()
            .put("accounts", accounts)
            .put("categories", categories)
            .put("transactions", transactions)
            .put("budgets", budgets)
    }

    /** 解析备份里的账本一节。ID 只接受 UUID 形态；金额必须是合法范围内的非负整数分。 */
    fun fromBackup(json: JSONObject): LedgerData {
        val accounts = json.optJSONArray("accounts").objects().map { a ->
            LedgerAccount(
                id = a.getString("id").also(::requireId),
                name = a.getString("name"),
                kind = AccountKind.of(a.optString("kind")),
                openingBalance = a.optLong("opening", 0L).also { require(kotlin.math.abs(it) <= Money.MAX_CENTS) { "金额越界" } },
                cardId = a.optNullableString("cardId")?.also(::requireId),
                archived = a.optBoolean("archived", false),
                position = a.optInt("position", 0),
                createdAt = a.optLong("createdAt", 0L),
                updatedAt = a.optLong("updatedAt", 0L),
            )
        }
        val categories = json.optJSONArray("categories").objects().map { c ->
            val type = TxType.of(c.optString("type"))
            require(type != TxType.TRANSFER) { "分类类型无效" }
            LedgerCategory(
                id = c.getString("id").also(::requireId),
                name = c.getString("name"),
                type = type,
                parentId = c.optNullableString("parentId")?.also(::requireId),
                icon = c.optString("icon", "more"),
                position = c.optInt("position", 0),
                archived = c.optBoolean("archived", false),
                updatedAt = c.optLong("updatedAt", 0L),
            )
        }
        val transactions = json.optJSONArray("transactions").objects().map { t ->
            LedgerTransaction(
                id = t.getString("id").also(::requireId),
                type = TxType.of(t.optString("type")),
                amount = t.getLong("amount").also { require(it in 0..Money.MAX_CENTS) { "金额越界" } },
                occurredAt = t.getLong("occurredAt"),
                accountId = t.getString("accountId").also(::requireId),
                toAccountId = t.optNullableString("toAccountId")?.also(::requireId),
                categoryId = t.optNullableString("categoryId")?.also(::requireId),
                note = t.optString("note", ""),
                tags = t.optStringList("tags"),
                createdAt = t.optLong("createdAt", 0L),
                updatedAt = t.optLong("updatedAt", 0L),
            )
        }
        val budgets = json.optJSONArray("budgets").objects().map { b ->
            LedgerBudget(
                id = b.getString("id").also(::requireId),
                categoryId = b.optNullableString("categoryId")?.also(::requireId),
                amount = b.getLong("amount").also { require(it in 0..Money.MAX_CENTS) { "金额越界" } },
                updatedAt = b.optLong("updatedAt", 0L),
            )
        }
        require(accounts.size + categories.size + transactions.size + budgets.size <= MAX_RECORDS) { "账本记录数量异常" }
        return LedgerData(accounts, categories, transactions, budgets)
    }

    private fun requireId(id: String) {
        require(ID_PATTERN.matches(id)) { "非法 ID" }
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        require(length() <= MAX_RECORDS) { "账本记录数量异常" }
        return (0 until length()).map { getJSONObject(it) }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.optStringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }
}
