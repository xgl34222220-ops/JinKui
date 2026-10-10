package com.xiguli.jinkui.data.ledger

import com.xiguli.jinkui.core.auto.CapturedPayment
import com.xiguli.jinkui.core.auto.PaySource
import com.xiguli.jinkui.data.importer.BillCategorizer
import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject

/**
 * “待确认”里的一笔：自动记账从通知里识别出来、还没被用户确认的收支。
 * 只有来源、方向、金额、商户与时间，整条加密存在数据库的 `ledger_pending` 表里（AAD 绑定 ID）。
 * 不进加密备份：它只是还没确认的草稿，确认后变成普通流水，随流水一起备份。
 */
data class PendingEntry(
    val id: String,
    val source: PaySource,
    val type: TxType,
    val amount: Long,
    val merchant: String,
    val occurredAt: Long,
    val createdAt: Long,
)

object PendingMath {
    /** 同一来源、同一方向、同金额、相差 2 分钟内，视为同一笔（通知重复推送、两个 App 都提醒）。 */
    const val SAME_WINDOW_MS = 2 * 60 * 1000L

    fun payload(entry: PendingEntry): String = JSONObject()
        .put("source", entry.source.key)
        .put("type", entry.type.key)
        .put("amount", entry.amount)
        .put("merchant", entry.merchant)
        .put("at", entry.occurredAt)
        .toString()

    fun read(id: String, createdAt: Long, text: String): PendingEntry {
        val json = JSONObject(text)
        val amount = json.getLong("amount")
        require(amount in 1..Money.MAX_CENTS) { "金额无效" }
        return PendingEntry(
            id = id,
            source = PaySource.of(json.optString("source")),
            type = if (json.optString("type") == TxType.INCOME.key) TxType.INCOME else TxType.EXPENSE,
            amount = amount,
            merchant = json.optString("merchant", "").take(40),
            occurredAt = json.getLong("at"),
            createdAt = createdAt,
        )
    }

    /** 新到的几笔里，去掉与已有待确认、已有流水或彼此重复的。 */
    fun fresh(incoming: List<CapturedPayment>, pending: List<PendingEntry>, transactions: List<LedgerTransaction>): List<CapturedPayment> {
        val out = ArrayList<CapturedPayment>()
        for (p in incoming.sortedBy { it.postedAt }) {
            val dupPending = pending.any { it.source == p.source && it.type == p.type && it.amount == p.amount && kotlin.math.abs(it.occurredAt - p.postedAt) <= SAME_WINDOW_MS }
            val dupOut = out.any { it.type == p.type && it.amount == p.amount && kotlin.math.abs(it.postedAt - p.postedAt) <= SAME_WINDOW_MS }
            // 已经有一笔同金额、两分钟内的流水（手动记过了，或导入过）
            val dupTx = transactions.any { it.type == p.type && it.amount == p.amount && kotlin.math.abs(it.occurredAt - p.postedAt) <= SAME_WINDOW_MS }
            if (!dupPending && !dupOut && !dupTx) out += p
        }
        return out
    }

    /** 记到哪个账户：来源对应种类的第一个可用账户（微信 / 支付宝 / 银行卡），没有时用第一个可用账户。 */
    fun accountFor(source: PaySource, accounts: List<LedgerAccount>): String? {
        val active = accounts.filter { !it.archived }
        return (active.firstOrNull { it.kind == source.accountKind } ?: active.firstOrNull())?.id
    }

    /** 由一笔待确认生成建议的流水（确认时直接保存，或作为“编辑后确认”的初始内容）。 */
    fun suggestion(entry: PendingEntry, data: LedgerData, zone: ZoneId = ZoneId.systemDefault()): LedgerTransaction? {
        val account = accountFor(entry.source, data.accounts) ?: return null
        val time = Instant.ofEpochMilli(entry.occurredAt).atZone(zone).toLocalTime()
        return LedgerTransaction(
            id = LedgerIds.newId(),
            type = entry.type,
            amount = entry.amount,
            occurredAt = entry.occurredAt,
            accountId = account,
            categoryId = BillCategorizer.categorize(entry.type, listOf(entry.merchant), data.categories, time),
            note = entry.merchant,
        )
    }
}
