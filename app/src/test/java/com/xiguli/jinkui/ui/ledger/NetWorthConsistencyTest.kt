package com.xiguli.jinkui.ui.ledger

import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.CreditSettings
import com.xiguli.jinkui.data.ledger.DebtMath
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.5.0-alpha03：全 App 只有一个“净资产”——资产页、账户与余额页用同一个口径
 * （资金 + 别人欠我 − 信用卡欠款 − 我欠别人，内部“余额调整”不算）；账户详情与列表的余额一致。
 * 以及信用卡的白话状态句。合成数据。
 */
class NetWorthConsistencyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 12)
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val cash = LedgerAccount("a-cash", "现金", AccountKind.CASH, openingBalance = 1_000_00, position = 0)
    private val bank = LedgerAccount("a-bank", "招商银行", AccountKind.BANK, openingBalance = 41_200_00, position = 1)
    private val card = LedgerAccount("a-cc", "示例信用卡", AccountKind.CREDIT, position = 2, credit = CreditSettings(statementDay = 5, dueDay = 23, limit = 10_000_00))
    private val li = LedgerAccount("a-li", "借出 · 小李", AccountKind.RECEIVABLE, openingBalance = 800_00, position = 3)
    private val wang = LedgerAccount("a-wang", "借入 · 老王", AccountKind.PAYABLE, openingBalance = -2_000_00, position = 4)
    private val adjust = LedgerAccount("a-adj", "余额调整", AccountKind.ADJUST, archived = true, position = 5)
    private val data = LedgerData(
        accounts = listOf(cash, bank, card, li, wang, adjust),
        transactions = listOf(
            LedgerTransaction("t1", TxType.EXPENSE, 1_288_00, at(today.minusDays(10)), card.id),
            LedgerTransaction("t2", TxType.TRANSFER, 100_00, at(today.minusDays(1)), adjust.id, toAccountId = bank.id, note = "余额调整"),
        ),
    )

    @Test
    fun oneNetWorthEverywhere() {
        val expected = 1_000_00L + 41_300_00 - 1_288_00 + 800_00 - 2_000_00
        assertEquals(expected, LedgerMath.netWorth(data))
        assertEquals(expected, buildAssetsUi(data, today, zone).net)
        assertEquals(expected, buildAccountsUi(data).netWorth)
        // 资产 − 负债 = 净资产
        val assets = buildAssetsUi(data, today, zone)
        assertEquals(assets.net, assets.assets - assets.liabilities)
        // 招商银行：账户列表与账户详情同一个余额（含余额调整 +100）
        assertEquals(41_300_00L, buildAccountsUi(data).balances[bank.id])
        assertEquals(41_300_00L, buildAccountDetailUi(data, bank.id, zone, today)!!.balance)
    }

    @Test
    fun creditStatusLine() {
        val view = DebtMath.credit(card, data.transactions, emptyList(), today, zone)
        // 10月5日出账 1288，10月23日前还
        assertEquals("10月23日前还 ¥1,288.00", DebtMath.creditStatus(view, today))
        assertEquals("已逾期 1 天，还要还 ¥1,288.00", DebtMath.creditStatus(DebtMath.credit(card, data.transactions, emptyList(), LocalDate.of(2026, 10, 24), zone), LocalDate.of(2026, 10, 24)))
        val paid = data.transactions + LedgerTransaction("t3", TxType.TRANSFER, 1_288_00, at(today), bank.id, toAccountId = card.id)
        assertEquals("本期已还清", DebtMath.creditStatus(DebtMath.credit(card, paid, emptyList(), today, zone), today))
        val fresh = listOf(LedgerTransaction("t4", TxType.EXPENSE, 50_00, at(today), card.id))
        assertEquals("本期还没出账（11月5日出账）", DebtMath.creditStatus(DebtMath.credit(card, fresh, emptyList(), today, zone), today))
        assertEquals("欠 · 10月23日前还 ¥1,288.00", "欠 · " + buildAssetsUi(data, today, zone).creditStatus[card.id])
    }
}
