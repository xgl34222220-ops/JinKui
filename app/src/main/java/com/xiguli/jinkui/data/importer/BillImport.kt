package com.xiguli.jinkui.data.importer

import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerIds
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxType
import java.security.MessageDigest
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 预览里一行的状态。 */
enum class CandidateFlag {
    /** 新的一笔，默认勾选。 */
    NEW,
    /** 账本里已经有一笔同样金额、时间相差三分钟内的记录（可能是手动或自动记账记过的），默认不勾。 */
    SIMILAR,
    /** 退款收入，文件里找不到对应的原支出，默认不勾。 */
    REFUND,
}

/** 预览里的一行：要写入的流水 ID 固定由账单内容算出，同一笔重复导入会被认出来。 */
data class ImportCandidate(
    val id: String,
    val record: BillRecord,
    val categoryId: String?,
    val accountId: String,
    val checked: Boolean,
    val flag: CandidateFlag,
)

/** 导入预览。[newAccount] 不为空时，导入会顺带新建这个账户（例如账本里还没有“微信”账户）。 */
data class ImportPreview(
    val source: BillSource,
    val candidates: List<ImportCandidate>,
    val skipped: Map<SkipReason, Int>,
    /** 以前已经导入过的笔数（ID 已经在账本里），不再列出。 */
    val alreadyImported: Int,
    val newAccount: LedgerAccount?,
) {
    val selected: List<ImportCandidate> get() = candidates.filter { it.checked }
    val expenseTotal: Long get() = selected.filter { it.record.type == TxType.EXPENSE }.sumOf { it.record.amount }
    val incomeTotal: Long get() = selected.filter { it.record.type == TxType.INCOME }.sumOf { it.record.amount }
}

/** 导入完成后的结果。 */
data class ImportResult(
    val imported: Int,
    val expenseCount: Int,
    val expenseTotal: Long,
    val incomeCount: Int,
    val incomeTotal: Long,
    val unchecked: Int,
    val alreadyImported: Int,
    val skipped: Map<SkipReason, Int>,
    val createdAccount: String?,
)

/** 账单导入的预览与落账计算。纯函数，便于测试。 */
object BillImport {
    /** 去重键：有交易单号用单号；记账 App 导出用“分钟 + 金额 + 备注”；否则用“分钟 + 金额 + 交易对方”。 */
    fun dedupeKey(r: BillRecord): String {
        val number = r.txNo.trim()
        if (r.source == BillSource.APP) {
            return "app:${r.time.truncatedTo(ChronoUnit.MINUTES)}:${r.type.key}:${r.amount}:${r.note.trim()}"
        }
        return if (number.isNotEmpty() && number != "/") {
            "no:${r.source.name}:$number"
        } else {
            "k:${r.time.truncatedTo(ChronoUnit.MINUTES)}:${r.type.key}:${r.amount}:${r.counterparty.trim()}"
        }
    }

    /** 由去重键算出的流水 ID（"imp-" + 32 位十六进制），满足账本 ID 规则，与周期记账生成的 ID 不会混淆。 */
    fun transactionId(r: BillRecord): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(dedupeKey(r).toByteArray(Charsets.UTF_8))
        return "imp-" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    fun isImportedId(id: String): Boolean = id.startsWith("imp-")

    /** 这笔账默认记到哪个账户；[fallback] 是来源对应的账户（微信 / 支付宝），通用表格是用户选的账户。 */
    fun accountFor(r: BillRecord, accounts: List<LedgerAccount>, fallback: String): String {
        val method = r.method
        if (method.isNotBlank()) {
            val active = accounts.filter { !it.archived && it.name.length >= 2 }
            // 支付方式里写着账户名（“招商银行储蓄卡(1234)”→ 账户“招商银行”；“花呗”→ 账户“花呗”）
            active.firstOrNull { method.contains(it.name) }?.let { return it.id }
            if ("花呗" in method) active.firstOrNull { it.kind == AccountKind.CREDIT && "花呗" in it.name }?.let { return it.id }
            if (method == "支付宝") active.firstOrNull { it.kind == AccountKind.ALIPAY }?.let { return it.id }
            if (method == "微信") active.firstOrNull { it.kind == AccountKind.WECHAT }?.let { return it.id }
            if ("信用卡" in method) {
                val bank = method.substringBefore("信用卡").take(4)
                active.firstOrNull { it.kind == AccountKind.CREDIT && bank.isNotEmpty() && it.name.contains(bank) }?.let { return it.id }
            }
            if ("储蓄卡" in method || "借记卡" in method) {
                val bank = method.substringBefore("储蓄卡").substringBefore("借记卡").take(4)
                active.firstOrNull { it.kind == AccountKind.BANK && bank.isNotEmpty() && it.name.contains(bank) }?.let { return it.id }
            }
        }
        return fallback
    }

    /**
     * 来源对应的默认账户：微信账单记到第一个“微信”账户，支付宝记到第一个“支付宝”账户；
     * 还没有时返回一个待新建的账户。通用表格用 [preferred]（用户在预览里选的），否则第一个可用账户。
     */
    fun defaultAccount(source: BillSource, accounts: List<LedgerAccount>, preferred: String? = null, now: Long = 0L): Pair<String, LedgerAccount?> {
        val active = accounts.filter { !it.archived }
        preferred?.let { id -> active.firstOrNull { it.id == id }?.let { return it.id to null } }
        val kind = when (source) {
            BillSource.WECHAT -> AccountKind.WECHAT
            BillSource.ALIPAY -> AccountKind.ALIPAY
            BillSource.CSV, BillSource.APP -> null
        }
        if (kind != null) {
            active.firstOrNull { it.kind == kind }?.let { return it.id to null }
            val created = LedgerAccount(
                id = LedgerIds.newId(),
                name = if (kind == AccountKind.WECHAT) "微信" else "支付宝",
                kind = kind,
                createdAt = now,
            )
            return created.id to created
        }
        val first = active.firstOrNull() ?: run {
            val created = LedgerAccount(LedgerIds.newId(), "现金", AccountKind.CASH, createdAt = now)
            return created.id to created
        }
        return first.id to null
    }

    fun preview(parsed: ParsedBill, data: LedgerData, zone: ZoneId, preferredAccount: String? = null, now: Long = System.currentTimeMillis()): ImportPreview {
        val existingIds = data.transactions.mapTo(HashSet()) { it.id }
        val (fallback, created) = defaultAccount(parsed.source, data.accounts, preferredAccount, now)
        val accounts = data.accounts + listOfNotNull(created)
        // 按金额建索引，查“三分钟内同金额”的已有记录
        val byAmount = data.transactions.filter { it.type != TxType.TRANSFER }.groupBy { it.amount to it.type }
        var already = 0
        val candidates = ArrayList<ImportCandidate>()
        for (r in parsed.records) {
            val id = transactionId(r)
            if (id in existingIds) {
                already++
                continue
            }
            val at = r.time.atZone(zone).toInstant().toEpochMilli()
            val similar = byAmount[r.amount to r.type]?.any { kotlin.math.abs(it.occurredAt - at) <= SIMILAR_WINDOW_MS } == true
            val flag = when {
                r.refund -> CandidateFlag.REFUND
                similar -> CandidateFlag.SIMILAR
                else -> CandidateFlag.NEW
            }
            candidates += ImportCandidate(
                id = id,
                record = r,
                categoryId = if (r.source == BillSource.APP) {
                    BillCategorizer.appCategory(r.type, r.kind, r.item, r.note, data.categories, r.time.toLocalTime())
                } else {
                    BillCategorizer.categorize(r.type, listOf(r.counterparty, r.item, r.note), data.categories, r.time.toLocalTime(), r.kind)
                },
                accountId = accountFor(r, accounts, fallback),
                checked = flag == CandidateFlag.NEW,
                flag = flag,
            )
        }
        val usesCreated = created != null && candidates.any { it.accountId == created.id }
        return ImportPreview(parsed.source, candidates, parsed.skipped, already, created.takeIf { usesCreated })
    }

    /** 勾选的行 → 流水。备注是“交易对方 · 商品”。 */
    fun transactions(preview: ImportPreview, zone: ZoneId, now: Long): List<LedgerTransaction> =
        preview.selected.map { c ->
            val r = c.record
            LedgerTransaction(
                id = c.id,
                type = r.type,
                amount = r.amount,
                occurredAt = r.time.atZone(zone).toInstant().toEpochMilli(),
                accountId = c.accountId,
                categoryId = c.categoryId,
                note = r.summary,
                createdAt = now,
                updatedAt = now,
            )
        }

    fun result(preview: ImportPreview, imported: List<LedgerTransaction>): ImportResult = ImportResult(
        imported = imported.size,
        expenseCount = imported.count { it.type == TxType.EXPENSE },
        expenseTotal = imported.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
        incomeCount = imported.count { it.type == TxType.INCOME },
        incomeTotal = imported.filter { it.type == TxType.INCOME }.sumOf { it.amount },
        unchecked = preview.candidates.size - preview.selected.size,
        alreadyImported = preview.alreadyImported,
        skipped = preview.skipped,
        createdAccount = preview.newAccount?.takeIf { a -> imported.any { it.accountId == a.id } }?.name,
    )

    const val SIMILAR_WINDOW_MS = 3 * 60 * 1000L
}
