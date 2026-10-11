package com.xiguli.jinkui.data.importer

import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.RuleMath
import com.xiguli.jinkui.data.ledger.TxType

/**
 * 导入账单时套用自动分类规则（1.4.0-alpha04）。只是在解析与预览之后“挂”一步，解析与预览本身的逻辑不变：
 *  - 预览：普通收支（不是转账、内部往来、借贷、余额调整）里命中规则的，分类与账户换成规则的；
 *    记账 App 的导出（一木等）保持与原 App 一比一，不套规则；
 *  - 写入：命中规则的流水追加规则里的标签（预览里用户可能改过分类，所以这一步只加标签）。
 */
object ImportRules {
    fun applyToPreview(preview: ImportPreview, data: LedgerData): ImportPreview {
        if (data.rules.isEmpty() || preview.source == BillSource.APP) return preview
        val accounts = data.accounts + preview.newAccounts
        var changed = false
        val candidates = preview.candidates.map { c ->
            if (c.txType == TxType.TRANSFER || c.klass != ImportClass.NORMAL) return@map c
            val r = c.record
            val hit = RuleMath.match(data.rules, c.txType, listOf(r.counterparty, r.item, r.note), r.amount, data.categories, accounts)
                ?: return@map c
            changed = true
            c.copy(categoryId = hit.categoryId ?: c.categoryId, accountId = hit.accountId ?: c.accountId)
        }
        return if (changed) preview.copy(candidates = candidates) else preview
    }

    fun tag(transactions: List<LedgerTransaction>, preview: ImportPreview, data: LedgerData): List<LedgerTransaction> {
        if (data.rules.none { it.tags.isNotEmpty() } || preview.source == BillSource.APP) return transactions
        val byId = preview.candidates.associateBy { it.id }
        return transactions.map { tx ->
            val c = byId[tx.id] ?: return@map tx
            if (tx.type == TxType.TRANSFER || c.klass != ImportClass.NORMAL) return@map tx
            val r = c.record
            val hit = RuleMath.match(data.rules, tx.type, listOf(r.counterparty, r.item, r.note), r.amount, data.categories, data.accounts + preview.newAccounts)
            if (hit == null || hit.tags.isEmpty()) tx else tx.copy(tags = (tx.tags + hit.tags).distinct().take(12))
        }
    }
}
