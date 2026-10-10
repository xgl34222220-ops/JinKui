package com.xiguli.jinkui.data.importer

import com.xiguli.jinkui.data.importer.XlsFixture.Formula
import com.xiguli.jinkui.data.importer.XlsFixture.FormulaText
import com.xiguli.jinkui.data.importer.XlsFixture.Label
import com.xiguli.jinkui.data.importer.XlsFixture.Num
import com.xiguli.jinkui.data.importer.XlsFixture.appRow
import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 旧版 Excel（.xls）读取与“记账 App”导出的识别、解析、归类、账户与去重。样例全部由 [XlsFixture] 合成。 */
class XlsImportTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val categories = LedgerDefaults.categories()
    private val cash = LedgerAccount("acc-cash", "现金", AccountKind.CASH)
    private val alipay = LedgerAccount("acc-ali", "支付宝", AccountKind.ALIPAY)
    private val wechat = LedgerAccount("acc-wx", "微信", AccountKind.WECHAT)
    private val huabei = LedgerAccount("acc-hb", "花呗", AccountKind.CREDIT)

    @Test
    fun readsSyntheticAppExportAndDetectsIt() {
        val rows = XlsFixture.appSample(240)
        val bytes = XlsFixture.xls(rows)
        assertEquals(TableReader.Kind.XLS, TableReader.sniff(bytes))
        val table = TableReader.read(bytes, "账单.xls")
        assertEquals(241, table.rows.size)
        assertEquals(XlsFixture.APP_HEADER, table.rows[0])
        assertEquals(BillSource.APP, BillParser.detect(table))

        val parsed = BillParser.parse(table, BillSource.APP)
        val expenses = rows.drop(1).count { it[1] == "支出" }
        val incomes = rows.drop(1).count { it[1] == "收入" }
        assertEquals(expenses, parsed.records.count { it.type == TxType.EXPENSE })
        assertEquals(incomes, parsed.records.count { it.type == TxType.INCOME })
        assertEquals(0, parsed.skippedTotal)
        // 金额取绝对值，方向来自“收支类型”
        val first = rows[1]
        val record = parsed.records.first { it.time == LocalDateTime.of(2026, 9, 1, 7, 0) }
        assertEquals(BillParser.parseAmount(first[2] as String)!!.let { kotlin.math.abs(it) }, record.amount)
        assertTrue(parsed.records.all { it.amount > 0 && it.source == BillSource.APP })
    }

    @Test
    fun parsesAmountsRefundsAccountsAndNotes() {
        val rows = listOf(
            XlsFixture.APP_HEADER,
            appRow("2026-09-11 22:59", "支出", "-600.0", "送礼人情", "礼金人情", note = "微信钱包 账户余额补齐"),
            appRow("2026-09-12 08:10", "支出", "-35.5", "出行交通", "打车", account = "花呗"),
            appRow("2026-09-12 12:00", "支出", "-100.0", "购物消费", "手机数码", refund = Num(40.0), discount = "5.0", note = "耳机"),
            appRow("2026-09-13 09:00", "支出", "0.0", "购物消费", "", refund = "20.0", note = "全额退了"),
            appRow("2026-09-15 10:00", "收入", "8800.0", "收入", "工资", note = "支付宝 账户余额补齐"),
            appRow("2026-09-16 10:00", "收入", "12.34", "收入", "理财盈利"),
            appRow("2026-09-17 10:00", "收入", "66.0", "收入", "红包", note = "群红包"),
            appRow("2026-09-18 10:00", "转账", "-10.0", "其他", ""),
            appRow("not a date", "支出", "-1.0", "其他", ""),
        )
        val parsed = BillParser.parse(TableReader.read(XlsFixture.xls(rows), "x.xls"), BillSource.APP)
        assertEquals(6, parsed.records.size)
        assertEquals(1, parsed.skipped[SkipReason.REFUNDED])
        assertEquals(1, parsed.skipped[SkipReason.NEUTRAL])
        assertEquals(1, parsed.skipped[SkipReason.INVALID])
        val gift = parsed.records.first { it.time.dayOfMonth == 11 }
        assertEquals(60_000L, gift.amount)
        assertEquals(TxType.EXPENSE, gift.type)
        assertEquals("微信", gift.method)
        assertEquals("微信钱包 账户余额补齐", gift.summary)
        assertEquals("花呗", parsed.records.first { it.time.dayOfMonth == 12 && it.time.hour == 8 }.method)
        // “金额”已是退款后的实付，不再二次扣减；“优惠”不影响金额；金额为 0 且有退款的是全额退款
        assertEquals(10_000L, parsed.records.first { it.time.dayOfMonth == 12 && it.time.hour == 12 }.amount)
        val salary = parsed.records.first { it.time.dayOfMonth == 15 }
        assertEquals(TxType.INCOME, salary.type)
        assertEquals(880_000L, salary.amount)
        assertEquals("支付宝", salary.method)

        // 有支付宝 / 微信 / 花呗账户时各归各的；没有时落到默认账户（1.3：“账户余额补齐”是余额校准，默认不导入）
        val data = LedgerData(accounts = listOf(cash, alipay, wechat, huabei), categories = categories)
        val preview = BillImport.preview(parsed, data, zone)
        fun of(day: Int, hour: Int? = null) = preview.candidates.first { it.record.time.dayOfMonth == day && (hour == null || it.record.time.hour == hour) }
        assertEquals(ImportClass.ADJUSTMENT, of(11).klass)
        assertEquals("acc-wx", of(11).accountId) // 支出方向的校准：微信 → 余额调整
        assertEquals(false, of(11).checked)
        assertEquals("acc-hb", of(12, 8).accountId)
        assertEquals(ImportClass.CREDIT, of(12, 8).klass)
        assertEquals("acc-cash", of(12, 12).accountId)
        assertEquals("acc-ali", of(15).toAccountId) // 收入方向的校准：余额调整 → 支付宝
        assertEquals(ImportClass.ADJUSTMENT, of(15).klass)
        assertTrue(preview.usedNewAccounts.isEmpty())
        val bare = BillImport.preview(parsed, LedgerData(accounts = listOf(cash), categories = categories), zone)
        assertTrue(bare.candidates.filter { it.klass == ImportClass.NORMAL }.all { it.accountId == "acc-cash" })

        // 1.3：分类与原 App 一比一（类别 / 二级分类），账本里没有的就新建
        val cats = (categories + preview.newCategories).associateBy { it.id }
        fun path(c: ImportCandidate) = c.categoryId?.let { id -> cats.getValue(id).let { cat -> listOfNotNull(cat.parentId?.let { cats.getValue(it).name }, cat.name).joinToString("/") } }
        assertEquals("出行交通/打车", path(of(12, 8)))
        assertEquals("购物消费/手机数码", path(of(12, 12)))
        assertEquals("理财盈利", path(of(16)))
        assertEquals("红包", path(of(17)))
        assertEquals(TxType.INCOME, cats.getValue(of(16).categoryId!!).type)

        // 流水备注就是“备注”
        val txs = BillImport.transactions(preview, zone, 0L)
        assertEquals("耳机", txs.first { it.id == of(12, 12).id }.note)
    }

    @Test
    fun dedupesOnTimeAmountAndNote() {
        val rows = listOf(
            XlsFixture.APP_HEADER,
            appRow("2026-09-11 12:00", "支出", "-18.0", "食品餐饮", "午餐", note = "牛肉面"),
            appRow("2026-09-11 12:00", "支出", "-18.0", "食品餐饮", "午餐", note = "牛肉面"),
            appRow("2026-09-11 12:00", "支出", "-18.0", "食品餐饮", "午餐", note = "另一碗"),
            appRow("2026-09-11 12:01", "支出", "-18.0", "食品餐饮", "午餐", note = "牛肉面"),
        )
        val parsed = BillParser.parse(TableReader.read(XlsFixture.xls(rows), "x.xls"), BillSource.APP)
        assertEquals(3, parsed.records.size)
        assertEquals(1, parsed.skipped[SkipReason.DUPLICATE_IN_FILE])
        // 同一份文件再导一次：全部认出是导入过的
        val data = LedgerData(accounts = listOf(cash), categories = categories)
        val first = BillImport.preview(parsed, data, zone)
        val imported = data.copy(transactions = BillImport.transactions(first, zone, 0L))
        val again = BillImport.preview(parsed, imported, zone)
        assertEquals(0, again.candidates.size)
        assertEquals(3, again.alreadyImported)
        val cats = (categories + first.newCategories).associateBy { it.id }
        val lunch = cats.getValue(first.candidates.first().categoryId!!)
        assertEquals("午餐", lunch.name)
        assertEquals("食品餐饮", cats.getValue(lunch.parentId!!).name)
    }

    @Test
    fun readsEveryCellKindAndSplitStrings() {
        // 很长的中文字符串：共享字符串表超过 8224 字节，字符被拆到 CONTINUE 里
        val long = buildString { repeat(5000) { append("账单里很长的备注文字".get(it % 10)) } }
        val mixed = "ASCII-only text that is compressed " + "x".repeat(9000)
        val rows = listOf(
            listOf("名称", "数值"),
            listOf("整数 RK", XlsFixture.rkInt(-42)),
            listOf("分 RK", XlsFixture.rkCents(1234)),
            listOf("小数 RK", XlsFixture.rkDouble(2.25)),
            listOf("NUMBER", Num(-600.0)),
            listOf("NUMBER 小数", Num(0.1)),
            listOf(Label("LABEL 中文"), Label("plain label")),
            listOf("FORMULA", Formula(3.5)),
            listOf("FORMULA 文本", FormulaText("公式结果")),
            listOf("MULRK", XlsFixture.rkInt(1), XlsFixture.rkCents(250), XlsFixture.rkInt(3)),
            listOf("长文本", long),
            listOf("长 ASCII", mixed),
            listOf("末行", "完"),
        )
        val table = TableReader.read(XlsFixture.xls(rows), "cells.xls")
        fun value(name: String) = table.rows.first { it[0] == name }
        assertEquals("-42", value("整数 RK")[1])
        assertEquals("12.34", value("分 RK")[1])
        assertEquals("2.25", value("小数 RK")[1])
        assertEquals("-600", value("NUMBER")[1])
        assertEquals("0.1", value("NUMBER 小数")[1])
        assertEquals("plain label", value("LABEL 中文")[1])
        assertEquals("3.5", value("FORMULA")[1])
        assertEquals("公式结果", value("FORMULA 文本")[1])
        assertEquals(listOf("MULRK", "1", "2.5", "3"), value("MULRK"))
        assertEquals(long, value("长文本")[1])
        assertEquals(mixed, value("长 ASCII")[1])
        assertEquals("完", value("末行")[1])
    }

    @Test
    fun readsSmallWorkbookFromMiniStream() {
        val rows = listOf(XlsFixture.APP_HEADER, appRow("2026-09-01 08:00", "支出", "-3.0", "食品餐饮", "早餐", note = "豆浆"))
        val workbook = XlsFixture.workbook(rows)
        assertTrue(workbook.size < 4096)
        val table = TableReader.read(XlsFixture.compound(workbook), "small.xls")
        assertEquals(2, table.rows.size)
        assertEquals("豆浆", table.rows[1][9])
        // 更老的流名 “Book” 也认
        val book = TableReader.read(XlsFixture.compound(workbook, "Book"), "small.xls")
        assertEquals("2026-09-01 08:00", book.rows[1][0])
    }

    @Test
    fun sniffsContentInsteadOfExtension() {
        // 扩展名是 .xls，内容其实是 CSV
        val csv = "日期,收支类型,金额,类别,二级分类,账户,账本,退款,优惠,备注\n2026-09-01 08:00,支出,-3.0,食品餐饮,早餐,,日常账本,,,豆浆\n".toByteArray()
        assertEquals(TableReader.Kind.TEXT, TableReader.sniff(csv))
        val table = TableReader.read(csv, "账单.xls")
        assertEquals(BillSource.APP, BillParser.detect(table))
        assertEquals(1, BillParser.parse(table, BillSource.APP).records.size)

        // 扩展名是 .csv，内容其实是 .xls
        val xls = XlsFixture.xls(XlsFixture.appSample(5))
        assertEquals(6, TableReader.read(xls, "bill.csv").rows.size)

        // 不支持的文件给出具体说明，而不是静默失败
        val pdf = "%PDF-1.7\n%\u00e2\u00e3\n1 0 obj".toByteArray(Charsets.ISO_8859_1)
        val pdfError = assertThrows(BillFormatException::class.java) { TableReader.read(pdf, "账单.xls") }
        assertTrue(pdfError.message!!.contains("PDF"))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
        assertTrue(assertThrows(BillFormatException::class.java) { TableReader.read(png, "a.xls") }.message!!.contains("图片"))
        val junk = ByteArray(2048) { (it % 7).toByte() }
        assertEquals(TableReader.Kind.UNSUPPORTED, TableReader.sniff(junk))
        assertTrue(assertThrows(BillFormatException::class.java) { TableReader.read(junk, "a.xls") }.message!!.contains("无法识别"))
        assertTrue(assertThrows(BillFormatException::class.java) { TableReader.read(ByteArray(0), "a.csv") }.message!!.contains("空"))

        // 复合文档里没有工作簿（例如 Word 文件）
        val noWorkbook = XlsFixture.compound(XlsFixture.workbook(listOf(listOf("a"))), "WordDocument")
        assertTrue(assertThrows(BillFormatException::class.java) { TableReader.read(noWorkbook, "a.xls") }.message!!.contains("工作簿"))
        // Excel 95（BIFF5）
        val biff5 = XlsFixture.compound(XlsFixture.workbook(listOf(listOf("a")), biffVersion = 0x0500))
        assertTrue(assertThrows(BillFormatException::class.java) { TableReader.read(biff5, "a.xls") }.message!!.contains("Excel 95"))
    }

    @Test
    fun wechatAndAlipayDetectionUnchanged() {
        assertEquals(BillSource.WECHAT, BillParser.detect(TableReader.read(BillSamples.wechatCsv(), "a.csv")))
        assertEquals(BillSource.ALIPAY, BillParser.detect(TableReader.read(BillSamples.alipayCsv(), "a.csv")))
        assertNull(BillParser.detect(TableReader.read(BillSamples.genericCsv(), "a.csv")))
    }
}
