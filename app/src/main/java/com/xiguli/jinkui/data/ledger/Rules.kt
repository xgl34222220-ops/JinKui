package com.xiguli.jinkui.data.ledger

import org.json.JSONArray
import org.json.JSONObject

/**
 * 自动分类规则（1.4.0-alpha04 起）：备注 / 商户里出现某个关键词（可选再限定收支类型与金额范围），
 * 就归到指定的分类，可选同时改账户、加标签。按 [position] 从上到下，第一条命中的生效。
 * 用在三处：手动记账时按备注给出分类、导入账单（微信 / 支付宝 / CSV）、自动记账的“待确认”。
 */
data class LedgerRule(
    val id: String,
    /** 关键词，任一出现在备注 / 商户里即命中（不区分大小写）。 */
    val keywords: List<String>,
    /** 只对这一类生效；null 表示支出与收入都可以（规则从不作用于转账）。 */
    val type: TxType? = null,
    /** 金额范围（分，含两端）；null 表示不限。 */
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val tags: List<String> = emptyList(),
    val enabled: Boolean = true,
    val position: Int = 0,
    val updatedAt: Long = 0L,
) {
    /** 规则自身的问题；没有时返回 null。 */
    fun problem(): String? = when {
        keywords.isEmpty() -> "请至少填一个关键词"
        keywords.any { it.length > 30 } -> "关键词太长了"
        type == TxType.TRANSFER -> "规则只对支出或收入生效"
        minAmount != null && minAmount < 0 -> "金额不能是负数"
        maxAmount != null && maxAmount > Money.MAX_CENTS -> "金额太大了"
        minAmount != null && maxAmount != null && minAmount > maxAmount -> "最低金额不能大于最高金额"
        categoryId == null && accountId == null && tags.isEmpty() -> "请至少选择分类、账户或标签中的一项"
        else -> null
    }

    /** 列表里的一句话：“美团 / 饿了么 · 支出 · ¥0.00–¥50.00”。 */
    val summary: String
        get() = buildList {
            add(keywords.joinToString(" / "))
            type?.let { add(it.label) }
            if (minAmount != null || maxAmount != null) {
                add(
                    when {
                        minAmount != null && maxAmount != null -> "${Money.format(minAmount)}–${Money.format(maxAmount)}"
                        minAmount != null -> "≥ ${Money.format(minAmount)}"
                        else -> "≤ ${Money.format(maxAmount!!)}"
                    },
                )
            }
        }.joinToString(" · ")
}

/**
 * 记账模板（1.4.0-alpha04 起）：常记的一笔（早餐、地铁、房租…）。在“记一笔”面板顶部一排，
 * 带金额的模板点一下直接记账，不带金额的点一下填好分类 / 账户 / 备注，再输金额。
 */
data class LedgerTemplate(
    val id: String,
    val name: String,
    val type: TxType = TxType.EXPENSE,
    /** null 表示每次输入金额。 */
    val amount: Long? = null,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val position: Int = 0,
    val updatedAt: Long = 0L,
) {
    fun problem(): String? = when {
        name.isBlank() -> "请填写模板名称"
        name.length > 12 -> "模板名称最多 12 个字"
        amount != null && (amount <= 0 || amount > Money.MAX_CENTS) -> "金额无效"
        type == TxType.TRANSFER && (accountId == null || toAccountId == null) -> "转账模板要选转出和转入账户"
        type == TxType.TRANSFER && accountId == toAccountId -> "转出和转入不能是同一个账户"
        note.length > 500 -> "备注太长了"
        else -> null
    }

    /** 由模板生成一笔流水（[amountOverride] 用于不带金额的模板）；账户无效时用 [fallbackAccount]。 */
    fun toTransaction(id: String, at: Long, accounts: List<LedgerAccount>, fallbackAccount: String?, amountOverride: Long? = null): LedgerTransaction? {
        val usable = accounts.filter { !it.archived }.mapTo(HashSet()) { it.id }
        val from = accountId?.takeIf { it in usable } ?: fallbackAccount ?: return null
        val cents = amountOverride ?: amount ?: return null
        return LedgerTransaction(
            id = id,
            type = type,
            amount = cents,
            occurredAt = at,
            accountId = from,
            toAccountId = if (type == TxType.TRANSFER) toAccountId?.takeIf { it in usable } else null,
            categoryId = if (type == TxType.TRANSFER) null else categoryId,
            note = note,
            tags = tags,
        )
    }
}

/** 规则命中的结果。 */
data class RuleHit(val rule: LedgerRule, val categoryId: String?, val accountId: String?, val tags: List<String>)

object RuleMath {
    const val MAX_RULES = 500
    const val MAX_TEMPLATES = 40

    /** 关键词输入：“美团 饿了么，外卖” → ["美团","饿了么","外卖"]。 */
    fun parseKeywords(text: String): List<String> =
        text.split(Regex("[\\s,，、;；/|]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20)

    /**
     * 第一条命中的规则。[texts] 是备注、商户、商品等；[amount] 为 null 时（手动记账还没输金额）
     * 设了金额范围的规则不参与。命中的分类必须存在、类型一致且没有隐藏；账户必须存在且在用，否则那一项忽略。
     */
    fun match(
        rules: List<LedgerRule>,
        type: TxType,
        texts: List<String>,
        amount: Long?,
        categories: List<LedgerCategory>,
        accounts: List<LedgerAccount>,
    ): RuleHit? {
        if (type == TxType.TRANSFER || rules.isEmpty()) return null
        val haystack = texts.filter { it.isNotBlank() }.joinToString("\n").lowercase()
        if (haystack.isEmpty()) return null
        val categoryOk = categories.filter { it.type == type && !it.archived }.mapTo(HashSet()) { it.id }
        val accountOk = accounts.filter { !it.archived && it.kind.countsInAssets }.mapTo(HashSet()) { it.id }
        for (rule in rules.sortedBy { it.position }) {
            if (!rule.enabled || rule.problem() != null) continue
            if (rule.type != null && rule.type != type) continue
            if (rule.minAmount != null || rule.maxAmount != null) {
                if (amount == null) continue
                if (rule.minAmount != null && amount < rule.minAmount) continue
                if (rule.maxAmount != null && amount > rule.maxAmount) continue
            }
            if (rule.keywords.none { haystack.contains(it.lowercase()) }) continue
            val category = rule.categoryId?.takeIf { it in categoryOk }
            val account = rule.accountId?.takeIf { it in accountOk }
            if (category == null && account == null && rule.tags.isEmpty()) continue
            return RuleHit(rule, category, account, rule.tags)
        }
        return null
    }

    /** 把命中的规则套到一笔流水上：分类、账户替换，标签追加（去重，最多 12 个）。 */
    fun apply(tx: LedgerTransaction, hit: RuleHit, changeAccount: Boolean = true): LedgerTransaction = tx.copy(
        categoryId = hit.categoryId ?: tx.categoryId,
        accountId = if (changeAccount) hit.accountId ?: tx.accountId else tx.accountId,
        tags = (tx.tags + hit.tags).distinct().take(12),
    )

    // ── 加密载荷 / 备份（两处同一形态）────────────────────────────

    const val KIND_RULE = "rule"
    const val KIND_TEMPLATE = "template"

    fun ruleJson(rule: LedgerRule): JSONObject = JSONObject()
        .put("keywords", JSONArray(rule.keywords))
        .put("type", rule.type?.key ?: JSONObject.NULL)
        .put("min", rule.minAmount ?: JSONObject.NULL)
        .put("max", rule.maxAmount ?: JSONObject.NULL)
        .put("categoryId", rule.categoryId ?: JSONObject.NULL)
        .put("accountId", rule.accountId ?: JSONObject.NULL)
        .put("tags", JSONArray(rule.tags))
        .put("enabled", rule.enabled)
        .put("position", rule.position)

    fun readRule(id: String, updatedAt: Long, json: JSONObject): LedgerRule {
        val rule = LedgerRule(
            id = id,
            keywords = json.strings("keywords"),
            type = json.nullableString("type")?.let { TxType.of(it) },
            minAmount = json.nullableLong("min"),
            maxAmount = json.nullableLong("max"),
            categoryId = json.nullableString("categoryId")?.also(::requireId),
            accountId = json.nullableString("accountId")?.also(::requireId),
            tags = json.strings("tags"),
            enabled = json.optBoolean("enabled", true),
            position = json.optInt("position", 0),
            updatedAt = updatedAt,
        )
        require(rule.problem() == null) { "规则无效" }
        return rule
    }

    fun templateJson(t: LedgerTemplate): JSONObject = JSONObject()
        .put("name", t.name)
        .put("type", t.type.key)
        .put("amount", t.amount ?: JSONObject.NULL)
        .put("accountId", t.accountId ?: JSONObject.NULL)
        .put("toAccountId", t.toAccountId ?: JSONObject.NULL)
        .put("categoryId", t.categoryId ?: JSONObject.NULL)
        .put("note", t.note)
        .put("tags", JSONArray(t.tags))
        .put("position", t.position)

    fun readTemplate(id: String, updatedAt: Long, json: JSONObject): LedgerTemplate {
        val t = LedgerTemplate(
            id = id,
            name = json.optString("name", "").take(12),
            type = TxType.of(json.optString("type")),
            amount = json.nullableLong("amount"),
            accountId = json.nullableString("accountId")?.also(::requireId),
            toAccountId = json.nullableString("toAccountId")?.also(::requireId),
            categoryId = json.nullableString("categoryId")?.also(::requireId),
            note = json.optString("note", ""),
            tags = json.strings("tags"),
            position = json.optInt("position", 0),
            updatedAt = updatedAt,
        )
        require(t.problem() == null) { "模板无效" }
        return t
    }

    private val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")

    private fun requireId(id: String) = require(ID_PATTERN.matches(id)) { "非法 ID" }

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.nullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)

    private fun JSONObject.strings(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        require(array.length() <= 50) { "内容过多" }
        return (0 until array.length()).map { array.getString(it) }.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
