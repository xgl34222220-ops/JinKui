package com.xiguli.jinkui.ui.ledger

import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerBudget
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 界面测试与模型测试共用的一份示例账本（2026 年 10 月）。 */
object LedgerSamples {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    val today: LocalDate = LocalDate.of(2026, 10, 10)

    val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH)
    val bank = LedgerAccount("a-bank", "招商银行", AccountKind.BANK, openingBalance = 1_000_000)
    val wechat = LedgerAccount("a-wechat", "微信", AccountKind.WECHAT)

    fun at(m: Int, d: Int, h: Int, min: Int = 0, y: Int = 2026): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private var seq = 0
    private fun tx(
        type: TxType,
        amount: Long,
        time: Long,
        category: String? = null,
        account: String = wechat.id,
        to: String? = null,
        note: String = "",
        tags: List<String> = emptyList(),
    ) = LedgerTransaction(
        id = "t${++seq}",
        type = type,
        amount = amount,
        occurredAt = time,
        accountId = account,
        toAccountId = to,
        categoryId = category,
        note = note,
        tags = tags,
        createdAt = time,
    )

    val data: LedgerData by lazy {
        seq = 0
        LedgerData(
            accounts = listOf(cash, bank, wechat),
            categories = LedgerDefaults.categories(),
            transactions = listOf(
                tx(TxType.EXPENSE, 3_250, at(10, 10, 12, 18), "cat-food-2", note = "牛肉面"),
                tx(TxType.EXPENSE, 1_800, at(10, 10, 8, 40), "cat-transport-1"),
                tx(TxType.INCOME, 1_850_000, at(10, 9, 10, 0), "cat-salary", account = bank.id, note = "九月工资"),
                tx(TxType.TRANSFER, 200_000, at(10, 9, 10, 30), account = bank.id, to = wechat.id),
                tx(TxType.EXPENSE, 380_000, at(10, 8, 9, 0), "cat-housing-1", account = bank.id, tags = listOf("房租")),
                tx(TxType.EXPENSE, 12_990, at(10, 6, 20, 15), "cat-shopping-3", note = "耳机"),
                tx(TxType.EXPENSE, 6_800, at(10, 3, 19, 0), "cat-fun-1", note = "电影"),
                tx(TxType.EXPENSE, 210_000, at(9, 20, 12, 0), "cat-shopping"),
                tx(TxType.EXPENSE, 95_000, at(8, 12, 12, 0), "cat-food"),
                tx(TxType.INCOME, 1_850_000, at(9, 9, 10, 0), "cat-salary", account = bank.id),
            ),
            budgets = listOf(LedgerBudget(id = "b-total", categoryId = null, amount = 600_000)),
        )
    }
}
