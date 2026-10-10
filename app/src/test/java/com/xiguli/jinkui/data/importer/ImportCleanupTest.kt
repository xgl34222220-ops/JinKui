package com.xiguli.jinkui.data.importer

import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerMath
import com.xiguli.jinkui.data.ledger.LedgerMerge
import com.xiguli.jinkui.data.ledger.LedgerTombstone
import com.xiguli.jinkui.data.ledger.TxType
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.3：记账 App 导出的分类导入（余额校准 / 内部转账 / 借贷 / 花呗 / 手续费 / 普通收支）、账户推断、
 * 统计不含校准与转账、余额变化、删除标记。样例全部由 [XlsFixture.cleanupSample] 合成。
 */
class ImportCleanupTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val categories = LedgerDefaults.categories()
    private val cash = LedgerAccount("acc-cash", "现金", AccountKind.CASH)

    private fun parsed() = BillParser.parse(TableReader.read(XlsFixture.xls(XlsFixture.cleanupSample()), "一木.xls"), BillSource.APP)

    private fun preview(data: LedgerData = LedgerData(accounts = listOf(cash), categories = categories), preferred: String? = null) =
        BillImport.preview(parsed(), data, zone, preferredAccount = preferred, now = 1L)

    private fun ImportPreview.byNote(note: String) = candidates.first { it.record.note == note }
    private fun ImportPreview.accountName(id: String?) = (newAccounts + listOf(cash)).firstOrNull { it.id == id }?.name

    @Test
    fun classifiesEveryKindOfRow() {
        val p = preview()
        val counts = p.candidates.groupingBy { it.klass }.eachCount()
        assertEquals(3, counts[ImportClass.ADJUSTMENT])
        assertEquals(3, counts[ImportClass.INTERNAL])
        assertEquals(2, counts[ImportClass.LOAN])
        assertEquals(2, counts[ImportClass.CREDIT])
        assertEquals(2, counts[ImportClass.FEE])
        assertEquals(5, counts[ImportClass.NORMAL])

        // 余额校准：默认不勾；收入方向是“余额调整 → 账户”，支出方向反过来
        val adjIn = p.byNote("支付宝 账户余额补齐")
        assertFalse(adjIn.checked)
        assertEquals(TxType.TRANSFER, adjIn.txType)
        assertEquals("余额调整", p.accountName(adjIn.accountId))
        assertEquals("支付宝", p.accountName(adjIn.toAccountId))
        val adjOut = p.byNote("微信钱包 账户余额补齐".let { n -> p.candidates.first { it.record.note == n && it.record.type == TxType.EXPENSE }.record.note })
        assertEquals("微信", p.accountName(p.candidates.first { it.record.note == adjOut.record.note && it.record.type == TxType.EXPENSE }.accountId))

        // 内部转账
        val yuebao = p.byNote("余额宝-单次转入")
        assertEquals(TxType.TRANSFER, yuebao.txType)
        assertEquals("支付宝", p.accountName(yuebao.accountId))
        assertEquals("余额宝", p.accountName(yuebao.toAccountId))
        assertEquals("理财", p.accountName(p.byNote("基金定投").toAccountId))
        val withdraw = p.byNote("微信零钱提现")
        assertEquals("微信", p.accountName(withdraw.accountId))
        assertEquals("银行卡", p.accountName(withdraw.toAccountId))

        // 别人转来、进了余额宝的钱：仍是收入，记到余额宝
        val toYuebao = p.byNote("转账收款到余额宝")
        assertEquals(ImportClass.NORMAL, toYuebao.klass)
        assertEquals(TxType.INCOME, toYuebao.txType)
        assertEquals("余额宝", p.accountName(toYuebao.accountId))

        // 借贷：备注里保留对方
        val lent = p.byNote("微信转账给小李子")
        assertEquals("微信", p.accountName(lent.accountId))
        assertEquals("借出 · 小李子", p.accountName(lent.toAccountId))
        val borrowed = p.byNote("支付宝 转账收款 老王")
        assertEquals("借入 · 老王", p.accountName(borrowed.accountId))
        assertEquals("支付宝", p.accountName(borrowed.toAccountId))

        // 花呗：信用账户
        val hb = p.candidates.filter { it.klass == ImportClass.CREDIT }
        assertTrue(hb.all { p.accountName(it.accountId) == "花呗" && it.txType == TxType.EXPENSE })
        assertEquals(AccountKind.CREDIT, p.newAccounts.first { it.name == "花呗" }.kind)

        // 手续费是支出；普通收支按备注推断账户，认不出的记到默认账户
        assertTrue(p.candidates.filter { it.klass == ImportClass.FEE }.all { it.txType == TxType.EXPENSE && p.accountName(it.accountId) == "微信" })
        assertEquals("微信", p.accountName(p.byNote("微信 牛肉面").accountId))
        assertEquals("支付宝", p.accountName(p.byNote("支付宝 收钱码收款").accountId))
        assertEquals("现金", p.accountName(p.byNote("杂费").accountId))
        // 分类与原 App 一比一：缺的分类按“类别 / 二级分类”新建
        val cats = (categories + p.newCategories).associateBy { it.id }
        val gain = cats.getValue(p.candidates.first { it.record.note.isEmpty() && it.record.item == "理财盈利" }.categoryId!!)
        assertEquals("理财盈利" to null, gain.name to gain.parentId)
        assertEquals(TxType.INCOME, gain.type)
        val other = cats.getValue(p.byNote("杂费").categoryId!!)
        assertEquals("其他" to TxType.EXPENSE, other.name to other.type)
        val drink = cats.getValue(p.candidates.first { it.record.item == "饮料酒水" }.categoryId!!)
        assertEquals("食品餐饮", cats.getValue(drink.parentId!!).name)
        // 同一个名字只建一次；再导一次用同样的 ID
        assertEquals(p.newCategories.size, p.newCategories.map { it.type to (it.parentId to it.name) }.distinct().size)
        val again = BillImport.preview(parsed(), LedgerData(accounts = listOf(cash), categories = categories + p.newCategories), zone, now = 2L)
        assertTrue(again.newCategories.isEmpty())
        assertEquals(p.byNote("杂费").categoryId, again.byNote("杂费").categoryId)
        assertEquals(AccountKind.ADJUST, p.newAccounts.first { it.name == "余额调整" }.kind)
        assertTrue(p.newAccounts.first { it.name == "余额调整" }.archived)
    }

    @Test
    fun statsExcludeAdjustmentsAndTransfersAndBalancesStayHonest() {
        val base = LedgerData(accounts = listOf(cash), categories = categories)
        val p = preview(base).withGroup(ImportClass.ADJUSTMENT, true)
        val txs = BillImport.transactions(p, zone, 5L, "batch-1")
        assertTrue(txs.all { it.importBatch == "batch-1" })
        val accounts = base.accounts + p.usedNewAccounts
        val after = base.copy(accounts = accounts, transactions = txs)
        val stats = LedgerMath.summary(after.transactions)
        // 只有普通收支、花呗与手续费计入统计
        val counted = p.selected.filter { it.klass in setOf(ImportClass.NORMAL, ImportClass.CREDIT, ImportClass.FEE) }
        assertEquals(counted.filter { it.record.type == TxType.EXPENSE }.sumOf { it.record.amount }, stats.expense)
        assertEquals(counted.filter { it.record.type == TxType.INCOME }.sumOf { it.record.amount }, stats.income)
        // 校准与转账一笔都不进统计
        val adjustTotal = p.candidates.filter { it.klass == ImportClass.ADJUSTMENT }.sumOf { it.record.amount }
        assertTrue(adjustTotal > 0)
        assertTrue(stats.income < counted.sumOf { it.record.amount } + 1)

        // 余额：借出 600 是应收，借入 1000 是应付（负数），花呗欠 48.30
        val balances = LedgerMath.balances(accounts, txs)
        fun bal(name: String) = balances[accounts.first { it.name == name }.id]
        assertEquals(60_000L, bal("借出 · 小李子"))
        assertEquals(-100_000L, bal("借入 · 老王"))
        assertEquals(-4_830L, bal("花呗"))
        // 余额调整账户承担校准的反向金额，不计入净资产
        val adjust = accounts.first { it.kind == AccountKind.ADJUST }
        assertEquals(-(500_000L + 30_000L - 120_000L), balances[adjust.id])
        assertFalse(adjust.kind.countsInAssets)

        // 结果页的余额变化不列“余额调整”，新建的账户标出来
        val result = BillImport.result(p, txs, base, "batch-1")
        assertNull(result.balanceChanges.firstOrNull { it.name == "余额调整" })
        assertTrue(result.balanceChanges.first { it.name == "花呗" }.created)
        assertEquals(txs.count { it.type == TxType.TRANSFER }, result.transferCount)
        assertEquals("batch-1", result.batchId)
    }

    @Test
    fun adjustmentsOffByDefaultMeanNoPhantomSavings() {
        val base = LedgerData(accounts = listOf(cash), categories = categories)
        val p = preview(base)
        val txs = BillImport.transactions(p, zone, 5L)
        assertTrue(txs.none { it.note.contains("余额补齐") })
        assertFalse(p.usedNewAccounts.any { it.kind == AccountKind.ADJUST })
        val groups = p.groups.associateBy { it.klass }
        val adj = groups.getValue(ImportClass.ADJUSTMENT)
        assertEquals(3, adj.count)
        assertEquals(0, adj.checkedCount)
        assertEquals(500_000L + 30_000L - 120_000L, adj.net)
    }

    @Test
    fun defaultAccountComesFromPreviewChoiceAndExistingAccountsAreReused() {
        val bank = LedgerAccount("acc-bank", "招商银行", AccountKind.BANK)
        val ali = LedgerAccount("acc-ali", "支付宝", AccountKind.ALIPAY)
        val hb = LedgerAccount("acc-hb", "花呗", AccountKind.CREDIT)
        val p = preview(LedgerData(accounts = listOf(cash, bank, ali, hb), categories = categories), preferred = "acc-bank")
        assertEquals("acc-bank", p.defaultAccountId)
        assertEquals("acc-bank", p.byNote("杂费").accountId)
        assertEquals("acc-ali", p.byNote("支付宝 收钱码收款").accountId)
        assertTrue(p.candidates.filter { it.klass == ImportClass.CREDIT }.all { it.accountId == "acc-hb" })
        assertEquals("acc-bank", p.byNote("微信零钱提现").toAccountId)
        assertNull(p.newAccounts.firstOrNull { it.name == "支付宝" || it.name == "花呗" })
    }

    @Test
    fun counterpartyFromLoanNotes() {
        assertEquals("小李子", BillImport.counterparty("微信转账给小李子"))
        assertEquals("老王", BillImport.counterparty("支付宝 转账收款 老王"))
        assertEquals("小张", BillImport.counterparty("向小张借的"))
        assertEquals("阿明", BillImport.counterparty("借给阿明 周末还"))
        assertNull(BillImport.counterparty(""))
        assertNull(BillImport.counterparty("转账收款到余额宝"))
    }

    @Test
    fun deletedImportsAreNotResurrectedByOldBackupButCanBeReimported() {
        val base = LedgerData(accounts = listOf(cash), categories = categories)
        val p = preview(base)
        val txs = BillImport.transactions(p, zone, 5L)
        val gone = txs.first()
        val local = base.copy(
            accounts = base.accounts + p.usedNewAccounts,
            transactions = txs.drop(1),
            tombstones = listOf(LedgerTombstone(gone.id, LedgerTombstone.KIND_TX, 9L)),
        )
        // 旧备份里还有这一笔：合并时不加回来
        val plan = LedgerMerge.plan(local, base.copy(transactions = txs))
        assertTrue(plan.toWrite.transactions.none { it.id == gone.id })
        assertEquals(1, plan.suppressed)
        // 重新导入同一份账单：删掉的那一笔重新出现在预览里（由用户决定），其余认出是导入过的
        val again = BillImport.preview(parsed(), local, zone, now = 2L)
        assertEquals(listOf(gone.id), again.candidates.map { it.id }.filter { it == gone.id })
        assertEquals(txs.size - 1, again.alreadyImported)
        assertNotNull(again.candidates.firstOrNull { it.id == gone.id })
    }
}
