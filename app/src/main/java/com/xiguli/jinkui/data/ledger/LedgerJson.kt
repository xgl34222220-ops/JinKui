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
        .also { json -> account.credit?.let { json.put("credit", creditJson(it)) } }
        .toString()

    class AccountPayload(val name: String, val kind: AccountKind, val opening: Long, val cardId: String?, val credit: CreditSettings? = null)

    fun readAccountPayload(text: String): AccountPayload {
        val json = JSONObject(text)
        return AccountPayload(
            name = json.optString("name", "账户"),
            kind = AccountKind.of(json.optString("kind")),
            opening = json.optLong("opening", 0L),
            cardId = json.optNullableString("cardId"),
            // 设置损坏时当作没有设置，不让整个账户读不出来
            credit = json.optJSONObject("credit")?.let { runCatching { readCredit(it) }.getOrNull() },
        )
    }

    /** 信用账户的账单设置（1.4 起）：数据库载荷与备份共用。 */
    fun creditJson(credit: CreditSettings): JSONObject = JSONObject()
        .put("statementDay", credit.statementDay)
        .put("dueDay", credit.dueDay)
        .put("dueMode", credit.dueMode.key)
        .put("limit", credit.limit)

    fun readCredit(json: JSONObject): CreditSettings {
        val settings = CreditSettings(
            statementDay = json.getInt("statementDay"),
            dueDay = json.getInt("dueDay"),
            dueMode = DueMode.of(json.optString("dueMode")),
            limit = json.optLong("limit", 0L),
        )
        require(settings.problem() == null) { "账单设置无效" }
        return settings
    }

    /**
     * 分期与借贷（1.4 起）：除 ID 与更新时间外的一切都在这里，数据库载荷与备份共用同一形态。
     * 日期写成 ISO 字符串。
     */
    fun installmentJson(plan: LedgerInstallment): JSONObject = JSONObject()
        .put("accountId", plan.accountId)
        .put("title", plan.title)
        .put("total", plan.total)
        .put("periods", plan.periods)
        .put("fee", plan.feePerPeriod)
        .put("rateBp", plan.feeRateBp ?: JSONObject.NULL)
        .put("first", plan.firstDate.toString())
        .put("start", plan.startDate.toString())
        .put("payoff", plan.payoffDate?.toString() ?: JSONObject.NULL)
        .put("note", plan.note)
        .put("createdAt", plan.createdAt)

    fun readInstallment(id: String, updatedAt: Long, json: JSONObject): LedgerInstallment = parsingDates {
        val first = java.time.LocalDate.parse(json.getString("first"))
        val plan = LedgerInstallment(
            id = id,
            accountId = json.getString("accountId").also(::requireId),
            title = json.optString("title", "分期"),
            total = json.getLong("total"),
            periods = json.getInt("periods"),
            feePerPeriod = json.optLong("fee", 0L),
            feeRateBp = if (json.has("rateBp") && !json.isNull("rateBp")) json.getInt("rateBp") else null,
            firstDate = first,
            startDate = json.optNullableString("start")?.let(java.time.LocalDate::parse) ?: first,
            payoffDate = json.optNullableString("payoff")?.let(java.time.LocalDate::parse),
            note = json.optString("note", ""),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = updatedAt,
        )
        require(plan.problem() == null) { "分期无效" }
        plan
    }

    fun loanJson(loan: LedgerLoan): JSONObject = JSONObject()
        .put("accountId", loan.accountId)
        .put("direction", loan.direction.key)
        .put("date", loan.date.toString())
        .put("due", loan.dueDate?.toString() ?: JSONObject.NULL)
        .put("interest", loan.interest)
        .put("note", loan.note)
        .put("legacy", loan.legacy)
        .put("createdAt", loan.createdAt)

    fun readLoan(id: String, updatedAt: Long, json: JSONObject): LedgerLoan = parsingDates {
        val interest = json.optLong("interest", 0L)
        require(interest in 0..Money.MAX_CENTS) { "金额越界" }
        LedgerLoan(
            id = id,
            accountId = json.getString("accountId").also(::requireId),
            direction = LoanDirection.of(json.optString("direction")),
            date = java.time.LocalDate.parse(json.getString("date")),
            dueDate = json.optNullableString("due")?.let(java.time.LocalDate::parse),
            interest = interest,
            note = json.optString("note", ""),
            legacy = json.optBoolean("legacy", false),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = updatedAt,
        )
    }

    const val DEBT_INSTALLMENT = "installment"
    const val DEBT_LOAN = "loan"

    /** 数据库 ledger_debts 一行的载荷（kind 在明文列）。 */
    fun debtPayload(plan: LedgerInstallment): String = installmentJson(plan).toString()
    fun debtPayload(loan: LedgerLoan): String = loanJson(loan).toString()

    private inline fun <T> parsingDates(block: () -> T): T =
        try {
            block()
        } catch (e: java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("日期无效", e)
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
        .also { json -> tx.debtId?.let { json.put("debt", it) } }
        .toString()

    class TransactionPayload(val amount: Long, val note: String, val tags: List<String>, val debtId: String? = null)

    fun readTransactionPayload(text: String): TransactionPayload {
        val json = JSONObject(text)
        return TransactionPayload(
            json.getLong("amount"), json.optString("note", ""), json.optStringList("tags"),
            json.optNullableString("debt")?.takeIf { ID_PATTERN.matches(it) },
        )
    }

    /** 导入批次（1.3 起）：来源、文件名、笔数、自动新建的账户。 */
    fun importBatchPayload(batch: LedgerImportBatch): String = JSONObject()
        .put("source", batch.source)
        .put("file", batch.fileName)
        .put("count", batch.count)
        .put("accounts", JSONArray(batch.createdAccounts))
        .toString()

    fun readImportBatchPayload(id: String, createdAt: Long, text: String): LedgerImportBatch {
        val json = JSONObject(text)
        return LedgerImportBatch(
            id = id,
            createdAt = createdAt,
            source = json.optString("source", ""),
            fileName = json.optString("file", ""),
            count = json.optInt("count", 0),
            createdAccounts = json.optStringList("accounts"),
        )
    }

    fun budgetPayload(budget: LedgerBudget): String = JSONObject().put("amount", budget.amount).toString()

    fun readBudgetAmount(text: String): Long = JSONObject(text).getLong("amount")

    /**
     * 周期记账规则：除 ID 与更新时间外的一切都在这里，数据库载荷与备份共用同一形态。
     * 日期写成 ISO 字符串（"2026-10-15"），与时区无关。
     */
    fun recurringJson(rule: LedgerRecurring): JSONObject = JSONObject()
        .put("type", rule.type.key)
        .put("amount", rule.amount)
        .put("accountId", rule.accountId)
        .put("toAccountId", rule.toAccountId ?: JSONObject.NULL)
        .put("categoryId", rule.categoryId ?: JSONObject.NULL)
        .put("note", rule.note)
        .put("tags", JSONArray(rule.tags))
        .put("frequency", rule.frequency.key)
        .put("start", rule.startDate.toString())
        .put("minute", rule.minuteOfDay)
        .put("end", rule.endDate?.toString() ?: JSONObject.NULL)
        .put("next", rule.nextIndex)
        .put("paused", rule.paused)
        .put("createdAt", rule.createdAt)

    fun recurringPayload(rule: LedgerRecurring): String = recurringJson(rule).toString()

    /** 解析规则；[id] 与 [updatedAt] 来自数据库明文列或备份。内容不合法时抛 [IllegalArgumentException] / JSONException。 */
    fun readRecurring(id: String, updatedAt: Long, json: JSONObject): LedgerRecurring {
        val amount = json.getLong("amount")
        require(amount in 1..Money.MAX_CENTS) { "金额越界" }
        val minute = json.optInt("minute", 9 * 60)
        require(minute in 0..1439) { "时间无效" }
        val next = json.optInt("next", 0)
        require(next in 0..RecurringMath.MAX_INDEX) { "进度无效" }
        val start = java.time.LocalDate.parse(json.getString("start"))
        val end = json.optNullableString("end")?.let(java.time.LocalDate::parse)
        // DateTimeParseException 是 RuntimeException，统一转成 IllegalArgumentException
        return LedgerRecurring(
            id = id,
            type = TxType.of(json.optString("type")),
            amount = amount,
            accountId = json.getString("accountId").also(::requireId),
            toAccountId = json.optNullableString("toAccountId")?.also(::requireId),
            categoryId = json.optNullableString("categoryId")?.also(::requireId),
            note = json.optString("note", ""),
            tags = json.optStringList("tags"),
            frequency = RecurFrequency.of(json.optString("frequency")),
            startDate = start,
            minuteOfDay = minute,
            endDate = end,
            nextIndex = next,
            paused = json.optBoolean("paused", false),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = updatedAt,
        )
    }

    fun readRecurringPayload(id: String, updatedAt: Long, text: String): LedgerRecurring =
        try {
            readRecurring(id, updatedAt, JSONObject(text))
        } catch (e: java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("日期无效", e)
        }

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
                    .put("credit", a.credit?.let(::creditJson) ?: JSONObject.NULL)
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
                    .put("debtId", t.debtId ?: JSONObject.NULL)
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
        val recurring = JSONArray()
        for (r in data.recurring) {
            recurring.put(recurringJson(r).put("id", r.id).put("updatedAt", r.updatedAt))
        }
        val tombstones = JSONArray()
        for (t in data.tombstones) {
            tombstones.put(JSONObject().put("id", t.id).put("kind", t.kind).put("deletedAt", t.deletedAt))
        }
        // 1.4 起（备份 schema 5）：分期与借贷
        val installments = JSONArray()
        for (p in data.installments) installments.put(installmentJson(p).put("id", p.id).put("updatedAt", p.updatedAt))
        val loans = JSONArray()
        for (l in data.loans) loans.put(loanJson(l).put("id", l.id).put("updatedAt", l.updatedAt))
        return JSONObject()
            .put("installments", installments)
            .put("loans", loans)
            .put("tombstones", tombstones)
            .put("accounts", accounts)
            .put("categories", categories)
            .put("transactions", transactions)
            .put("budgets", budgets)
            .put("recurring", recurring)
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
                credit = a.optJSONObject("credit")?.let(::readCredit),
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
                debtId = t.optNullableString("debtId")?.also(::requireId),
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
        // 0.7.0 之前的备份没有 "recurring"，按空处理
        val recurring = json.optJSONArray("recurring").objects().map { r ->
            try {
                readRecurring(r.getString("id").also(::requireId), r.optLong("updatedAt", 0L), r)
            } catch (e: java.time.format.DateTimeParseException) {
                throw IllegalArgumentException("日期无效", e)
            }
        }
        // 0.9.0 之前的备份没有 "tombstones"，按空处理；不认识的种类忽略
        val tombstones = json.optJSONArray("tombstones").objects().mapNotNull { t ->
            val kind = t.getString("kind")
            if (kind !in LedgerTombstone.KINDS) return@mapNotNull null
            LedgerTombstone(t.getString("id").also(::requireId), kind, t.optLong("deletedAt", 0L))
        }
        // schema 5 之前的备份没有分期与借贷，按空处理
        val installments = json.optJSONArray("installments").objects().map { p ->
            readInstallment(p.getString("id").also(::requireId), p.optLong("updatedAt", 0L), p)
        }
        val loans = json.optJSONArray("loans").objects().map { l ->
            readLoan(l.getString("id").also(::requireId), l.optLong("updatedAt", 0L), l)
        }
        require(accounts.size + categories.size + transactions.size + budgets.size + recurring.size + tombstones.size + installments.size + loans.size <= MAX_RECORDS) { "账本记录数量异常" }
        return LedgerData(accounts, categories, transactions, budgets, recurring, tombstones = tombstones, installments = installments, loans = loans)
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
