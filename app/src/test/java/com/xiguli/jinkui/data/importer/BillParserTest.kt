package com.xiguli.jinkui.data.importer

import android.app.Application
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** 账单解析、归类、去重与预览。.xlsx 要用 Android 的 XmlPullParser，所以跑在 Robolectric 上。样例都是合成的。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class BillParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val categories = LedgerDefaults.categories()
    private val cash = LedgerAccount("acc-cash", "现金", AccountKind.CASH)
    private val cmb = LedgerAccount("acc-cmb", "招商银行", AccountKind.BANK)
    private val wechat = LedgerAccount("acc-wx", "微信", AccountKind.WECHAT)

    private fun wechatBill(bytes: ByteArray, name: String): ParsedBill {
        val table = TableReader.read(bytes, name)
        assertEquals(BillSource.WECHAT, BillParser.detect(table))
        return BillParser.parse(table, BillSource.WECHAT)
    }

    private fun checkWechat(bill: ParsedBill) {
        assertEquals(6, bill.records.size)
        val byParty = bill.records.associateBy { it.counterparty }
        val noodle = byParty.getValue("示例面馆")
        assertEquals(3_250L, noodle.amount)
        assertEquals(TxType.EXPENSE, noodle.type)
        assertEquals(LocalDateTime.of(2026, 9, 30, 12, 18, 5), noodle.time)
        assertEquals("4200000001202609300000000001", noodle.txNo) // 去掉了导出时附带的制表符
        assertEquals("示例面馆 · 牛肉面", noodle.summary)
        assertEquals(50_000L, byParty.getValue("朋友甲").amount)
        assertEquals(TxType.INCOME, byParty.getValue("朋友甲").type)
        // 部分退款从原支出里扣掉：30 - 10 = 20
        assertEquals(2_000L, byParty.getValue("示例便利店").amount)
        // 全额退款的支出与它的退款行都不导入；失败、提现、充值不导入
        assertFalse(byParty.containsKey("示例商城"))
        assertFalse(byParty.containsKey("示例超市"))
        assertEquals(2, bill.skipped[SkipReason.NEUTRAL])
        assertEquals(1, bill.skipped[SkipReason.REFUNDED])
        assertEquals(1, bill.skipped[SkipReason.REFUND_ROW])
        assertEquals(1, bill.skipped[SkipReason.CLOSED])
        // 按时间倒序
        assertEquals(bill.records.sortedByDescending { it.time }, bill.records)
    }

    @Test
    fun parsesWechatCsv() = checkWechat(wechatBill(BillSamples.wechatCsv(), "微信支付账单(20260901-20260930).csv"))

    @Test
    fun parsesWechatXlsxWithSharedInlineAndNumericCells() {
        val bill = wechatBill(BillSamples.wechatXlsx(), "微信支付账单.xlsx")
        checkWechat(bill)
        // .xlsx 和 .csv 解析出同一组记录（去重键一致）
        val csv = wechatBill(BillSamples.wechatCsv(), "a.csv")
        assertEquals(csv.records.map(BillImport::dedupeKey).toSet(), bill.records.map(BillImport::dedupeKey).toSet())
        assertEquals("生椰拿铁", bill.records.first { it.counterparty.startsWith("示例咖啡") }.item)
    }

    @Test
    fun parsesAlipayGbkCsv() {
        val table = TableReader.read(BillSamples.alipayCsv(), "alipay_record_20261001.csv")
        assertEquals(BillSource.ALIPAY, BillParser.detect(table))
        val bill = BillParser.parse(table, BillSource.ALIPAY)
        assertEquals(5, bill.records.size)
        val takeout = bill.records.first { it.counterparty == "示例外卖平台" }
        assertEquals(4_500L, takeout.amount)
        assertEquals("花呗", takeout.method)
        assertEquals("餐饮美食", takeout.kind)
        assertEquals(TxType.INCOME, bill.records.first { it.counterparty == "示例商家" }.type)
        assertEquals(1, bill.skipped[SkipReason.NEUTRAL])
        assertEquals(1, bill.skipped[SkipReason.CLOSED])
    }

    @Test
    fun parsesLegacyAlipayWithRefundColumn() {
        val table = TableReader.read(BillSamples.alipayLegacyCsv(), "alipay.csv")
        assertEquals(BillSource.ALIPAY, BillParser.detect(table))
        val bill = BillParser.parse(table, BillSource.ALIPAY)
        assertEquals(2, bill.records.size)
        // 成功退款 15 元从 60 元里扣掉
        assertEquals(4_500L, bill.records.first { it.counterparty == "示例书店" }.amount)
        // 付款时间为空时用交易创建时间
        assertEquals(LocalDateTime.of(2026, 9, 16, 10, 0), bill.records.first { it.counterparty == "示例药房" }.time)
        assertEquals("2026091500000001", bill.records.first { it.counterparty == "示例书店" }.txNo)
    }

    @Test
    fun genericCsvUsesGuessedMappingAndSigns() {
        val table = TableReader.read(BillSamples.genericCsv(), "我的账.csv")
        assertNull(BillParser.detect(table))
        val mapping = BillParser.guessMapping(table)
        assertEquals(0, mapping.time)
        assertEquals(1, mapping.amount)
        assertEquals(-1, mapping.direction)
        assertEquals(2, mapping.counterparty)
        assertEquals(3, mapping.item)
        assertTrue(mapping.negativeIsExpense)
        val bill = BillParser.parseGeneric(table, mapping)
        assertEquals(3, bill.records.size)
        assertEquals(1, bill.skipped[SkipReason.DUPLICATE_IN_FILE])
        assertEquals(1, bill.skipped[SkipReason.INVALID])
        val lunch = bill.records.first { it.counterparty == "示例食堂" }
        assertEquals(TxType.EXPENSE, lunch.type)
        assertEquals(2_850L, lunch.amount)
        assertEquals(LocalTime.NOON, bill.records.first { it.counterparty == "示例公交" }.time.toLocalTime()) // 只有日期时记在中午
        assertEquals(TxType.INCOME, bill.records.first { it.counterparty == "示例公司" }.type)
        // 关掉“负数是支出”后全部当作支出
        assertTrue(BillParser.parseGeneric(table, mapping.copy(negativeIsExpense = false)).records.all { it.type == TxType.EXPENSE })
        assertThrows(BillFormatException::class.java) { BillParser.parseGeneric(table, mapping.copy(amount = -1)) }
    }

    @Test
    fun readerHandlesQuotesEncodingsAndBadFiles() {
        val csv = TableReader.readCsv("a,\"b,c\",\"d\"\"e\"\r\n\"多\n行\",2,3\n\n")
        assertEquals(listOf(listOf("a", "b,c", "d\"e"), listOf("多\n行", "2", "3")), csv.rows)
        assertEquals("微信", TableReader.decode("微信".toByteArray(charset("GBK"))))
        assertEquals("微信", TableReader.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "微信".toByteArray(Charsets.UTF_16LE)))
        assertEquals(2, TableReader.columnIndex("C12"))
        assertEquals(27, TableReader.columnIndex("AB3"))
        assertThrows(BillFormatException::class.java) { TableReader.read("PK\u0003\u0004garbage".toByteArray(), "x.xlsx") }
        assertThrows(BillFormatException::class.java) { BillParser.parse(TableReader.readCsv("x,y\n1,2"), BillSource.WECHAT) }
    }

    @Test
    fun amountsAndTimes() {
        assertEquals(1_250L, BillParser.parseAmount("¥12.50"))
        assertEquals(-123_450L, BillParser.parseAmount("-1,234.5"))
        assertEquals(1_250L, BillParser.parseAmount("12.499999999")) // .xlsx 浮点
        assertEquals(800L, BillParser.parseAmount("8元"))
        assertEquals(-500L, BillParser.parseAmount("(5.00)"))
        assertNull(BillParser.parseAmount("abc"))
        assertNull(BillParser.parseAmount(""))
        assertEquals(LocalDateTime.of(2026, 10, 9, 8, 5, 0), BillParser.parseTime("2026/10/9 8:05"))
        assertEquals(LocalDateTime.of(2026, 10, 9, 12, 0), BillParser.parseTime("2026年10月9日"))
        // Excel 日期序列数：46304.5 = 2026-10-09 12:00
        assertEquals(LocalDateTime.of(2026, 10, 9, 12, 0), BillParser.parseTime("46304.5"))
        assertNull(BillParser.parseTime("2026-13-40"))
        assertNull(BillParser.parseTime("12"))
    }

    @Test
    fun categorizerUsesKeywordsTimeAndFallbacks() {
        fun cat(type: TxType, text: String, time: LocalTime? = null, kind: String = "", cats: List<LedgerCategory> = categories) =
            BillCategorizer.categorize(type, listOf(text), cats, time, kind)
        assertEquals("cat-food-4", cat(TxType.EXPENSE, "示例咖啡 生椰拿铁 瑞幸"))
        assertEquals("cat-transport-2", cat(TxType.EXPENSE, "滴滴出行"))
        assertEquals("cat-food-2", cat(TxType.EXPENSE, "示例面馆", LocalTime.of(12, 18)))
        assertEquals("cat-food-1", cat(TxType.EXPENSE, "示例面馆", LocalTime.of(7, 30)))
        assertEquals("cat-food-3", cat(TxType.EXPENSE, "美团外卖", LocalTime.of(19, 0)))
        assertEquals("cat-shopping-1", cat(TxType.EXPENSE, "无名小铺", kind = "日用百货"))
        assertEquals("cat-other-expense", cat(TxType.EXPENSE, "完全认不出的商户"))
        assertEquals("cat-salary", cat(TxType.INCOME, "代发工资"))
        assertEquals("cat-red-packet", cat(TxType.INCOME, "朋友乙", kind = "微信红包"))
        assertEquals("cat-other-income", cat(TxType.INCOME, "朋友甲"))
        // 子分类被隐藏时退回上级
        val hidden = categories.map { if (it.id == "cat-food-4") it.copy(archived = true) else it }
        assertEquals("cat-food", cat(TxType.EXPENSE, "瑞幸", cats = hidden))
        assertNull(cat(TxType.EXPENSE, "瑞幸", cats = emptyList()))
    }

    @Test
    fun previewAssignsAccountsCategoriesAndNewAccount() {
        val bill = wechatBill(BillSamples.wechatCsv(), "a.csv")
        val data = LedgerData(accounts = listOf(cash, cmb), categories = categories)
        val preview = BillImport.preview(bill, data, zone, now = 1L)
        // 账本里没有微信账户：新建一个
        assertNotNull(preview.newAccount)
        val created = preview.newAccount!!
        assertEquals(AccountKind.WECHAT, created.kind)
        assertEquals("微信", created.name)
        val byParty = preview.candidates.associateBy { it.record.counterparty }
        // 用银行卡付的记到“招商银行”，用零钱付的记到新建的微信账户
        assertEquals(cmb.id, byParty.getValue("示例咖啡（科技园店）").accountId)
        assertEquals(created.id, byParty.getValue("示例面馆").accountId)
        assertEquals("cat-food-4", byParty.getValue("示例咖啡（科技园店）").categoryId)
        assertEquals("cat-transport-2", byParty.getValue("示例出行").categoryId)
        assertTrue(preview.candidates.all { it.checked && it.flag == CandidateFlag.NEW })
        assertEquals(3_250L + 1_580 + 2_300 + 2_000, preview.expenseTotal)
        assertEquals(52_000L, preview.incomeTotal)
        // ID 固定且符合账本 ID 规则
        assertTrue(preview.candidates.all { it.id.matches(Regex("imp-[0-9a-f]{32}")) })
        assertEquals(preview.candidates.map { it.id }, BillImport.preview(bill, data, zone, now = 2L).candidates.map { it.id })
    }

    @Test
    fun previewSkipsAlreadyImportedAndFlagsSimilarAndRefunds() {
        val bill = wechatBill(BillSamples.wechatCsv(), "a.csv")
        val first = BillImport.preview(bill, LedgerData(accounts = listOf(cash, wechat), categories = categories), zone, now = 1L)
        assertNull(first.newAccount) // 已经有微信账户
        val imported = BillImport.transactions(first, zone, 5L)
        assertEquals(6, imported.size)
        assertEquals("示例面馆 · 牛肉面", imported.first { it.amount == 3_250L }.note)
        // 再导入同一份：全部认出来跳过
        val again = BillImport.preview(bill, LedgerData(accounts = listOf(cash, wechat), categories = categories, transactions = imported), zone)
        assertEquals(0, again.candidates.size)
        assertEquals(6, again.alreadyImported)
        // 手动记过一笔同金额、两分钟内的：标“可能已记过”，默认不勾
        val noodleAt = LocalDateTime.of(2026, 9, 30, 12, 20).atZone(zone).toInstant().toEpochMilli()
        val manual = LedgerTransaction("m-1", TxType.EXPENSE, 3_250L, noodleAt, cash.id, categoryId = "cat-food-2")
        val withManual = BillImport.preview(bill, LedgerData(accounts = listOf(cash, wechat), categories = categories, transactions = listOf(manual)), zone)
        val noodle = withManual.candidates.first { it.record.amount == 3_250L }
        assertEquals(CandidateFlag.SIMILAR, noodle.flag)
        assertFalse(noodle.checked)
        // 找不到原支出的退款：标“退款”，默认不勾
        val refundOnly = ParsedBill(
            BillSource.WECHAT,
            listOf(BillRecord(BillSource.WECHAT, LocalDateTime.of(2026, 9, 1, 9, 0), TxType.INCOME, 990, "示例店", refund = true, txNo = "R1")),
            emptyMap(),
        )
        val r = BillImport.preview(refundOnly, LedgerData(accounts = listOf(wechat), categories = categories), zone).candidates.single()
        assertEquals(CandidateFlag.REFUND, r.flag)
        assertFalse(r.checked)
        assertEquals("cat-refund", BillCategorizer.categorize(TxType.INCOME, listOf("示例店 退款"), categories))
    }

    @Test
    fun dedupeKeyFallsBackToTimeAmountAndCounterparty() {
        val a = BillRecord(BillSource.CSV, LocalDateTime.of(2026, 9, 1, 9, 0, 10), TxType.EXPENSE, 100, "店")
        val b = a.copy(time = LocalDateTime.of(2026, 9, 1, 9, 0, 50), item = "不同说明")
        assertEquals(BillImport.dedupeKey(a), BillImport.dedupeKey(b))
        assertFalse(BillImport.dedupeKey(a) == BillImport.dedupeKey(a.copy(counterparty = "别的店")))
        assertFalse(BillImport.dedupeKey(a) == BillImport.dedupeKey(a.copy(txNo = "N1")))
        assertEquals(BillImport.transactionId(a.copy(txNo = "N1")), BillImport.transactionId(a.copy(txNo = "N1", amount = 999)))
        assertTrue(BillImport.isImportedId(BillImport.transactionId(a)))
    }

    @Test
    fun resultSummarizesImported() {
        val bill = wechatBill(BillSamples.wechatCsv(), "a.csv")
        val preview = BillImport.preview(bill, LedgerData(accounts = listOf(cash), categories = categories), zone, now = 1L)
        val trimmed = preview.copy(candidates = preview.candidates.mapIndexed { i, c -> if (i == 0) c.copy(checked = false) else c })
        val txs = BillImport.transactions(trimmed, zone, 1L)
        val result = BillImport.result(trimmed, txs)
        assertEquals(5, result.imported)
        assertEquals(1, result.unchecked)
        assertEquals(txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }, result.expenseTotal)
        assertEquals("微信", result.createdAccount)
        assertEquals(2, result.skipped[SkipReason.NEUTRAL])
    }
}
