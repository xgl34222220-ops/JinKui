package com.xiguli.jinkui.data.repo

import com.xiguli.jinkui.data.ledger.*

/** 不覆盖保存后的新编辑或关联；纯计算方便对合成数据执行故障回归。 */
data class LedgerSaveReceipt(val saved: LedgerTransaction, val previous: LedgerTransaction?) {
    fun restore(current: LedgerData, now: Long): LedgerData {
        if (current.transactions.firstOrNull { it.id == saved.id } != saved) {
            throw LedgerInputException("记录已变更，请打开记录处理")
        }
        if (current.transactions.any { it.refundOf == saved.id || saved.id in it.reimburseOf }) {
            throw LedgerInputException("记录已有退款或报销关联，请打开记录处理")
        }
        if (previous == null) {
            val marker = LedgerTombstone(saved.id, LedgerTombstone.KIND_TX, now)
            return current.copy(
                transactions = current.transactions.filterNot { it.id == saved.id },
                tombstones = current.tombstones.filterNot { it.id == saved.id } + marker,
            )
        }
        if (previous.id != saved.id) throw LedgerInputException("撤销记录不匹配")
        LedgerMath.validate(previous, current.accounts)?.let { throw LedgerInputException(it) }
        // 撤销也是一次新写入，旧备份不能以较新的编辑时间重新覆盖撤销结果。
        val restored = previous.copy(updatedAt = maxOf(now, saved.updatedAt + 1))
        return current.copy(transactions = current.transactions.filterNot { it.id == saved.id } + restored)
    }
}
