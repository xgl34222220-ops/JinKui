package com.xiguli.jinkui.data.importer

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 测试用的合成账单。版式照微信 / 支付宝导出的账单写，内容全部是编造的（虚构的商户、单号与金额），
 * 不含任何真实用户数据。
 */
object BillSamples {
    private val wechatHeader = listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式", "当前状态", "交易单号", "商户单号", "备注")

    private val wechatPreamble = listOf(
        listOf("微信支付账单明细"),
        listOf("微信昵称：[示例用户]"),
        listOf("起始时间：[2026-09-01 00:00:00] 终止时间：[2026-09-30 23:59:59]"),
        listOf("导出类型：[全部]"),
        listOf("导出时间：[2026-10-01 10:00:00]"),
        listOf(""),
        listOf("共10笔记录"),
        listOf("收入：2笔 520.00元"),
        listOf("支出：6笔 151.30元"),
        listOf("中性交易：2笔 300.00元"),
        listOf("注："),
        listOf("1. 充值/提现/理财通购买/零钱通存取/信用卡还款等交易，将计入中性交易"),
        listOf(""),
        listOf("----------------------微信支付账单明细列表--------------------"),
    )

    val wechatRows = listOf(
        listOf("2026-09-30 12:18:05", "商户消费", "示例面馆", "牛肉面", "支出", "¥32.50", "零钱", "支付成功", "4200000001202609300000000001\t", "M0001\t", "/"),
        listOf("2026-09-29 08:40:11", "商户消费", "示例咖啡（科技园店）", "生椰拿铁", "支出", "¥15.80", "招商银行储蓄卡(1234)", "支付成功", "4200000001202609290000000002\t", "M0002\t", "/"),
        listOf("2026-09-28 19:02:44", "转账", "朋友甲", "/", "收入", "¥500.00", "/", "已存入零钱", "1000050001202609280000000003\t", "/", "/"),
        listOf("2026-09-27 22:15:00", "零钱提现", "招商银行(1234)", "/", "/", "¥200.00", "零钱", "提现已到账", "1000050001202609270000000004\t", "/", "/"),
        listOf("2026-09-26 18:30:00", "商户消费", "示例出行", "快车 行程", "支出", "¥23.00", "零钱", "支付成功", "4200000001202609260000000005\t", "M0005\t", "/"),
        listOf("2026-09-25 14:00:00", "商户消费", "示例商城", "数据线", "支出", "¥40.00", "零钱", "已全额退款", "4200000001202609250000000006\t", "M0006\t", "/"),
        listOf("2026-09-25 16:00:00", "示例商城-退款", "示例商城", "数据线", "收入", "¥40.00", "零钱", "已退款", "4200000001202609250000000007\t", "M0006\t", "/"),
        listOf("2026-09-24 11:11:11", "商户消费", "示例便利店", "饮料", "支出", "¥30.00", "零钱", "已退款(￥10.00)", "4200000001202609240000000008\t", "M0008\t", "/"),
        listOf("2026-09-23 09:00:00", "微信红包", "朋友乙", "/", "收入", "¥20.00", "/", "已存入零钱", "1000039501202609230000000009\t", "/", "/"),
        listOf("2026-09-22 10:00:00", "零钱充值", "招商银行(1234)", "/", "/", "¥100.00", "招商银行(1234)", "充值完成", "1000050001202609220000000010\t", "/", "/"),
        listOf("2026-09-21 10:00:00", "商户消费", "示例超市", "日用品", "支出", "¥18.00", "零钱", "支付失败", "4200000001202609210000000011\t", "M0011\t", "/"),
    )

    private fun csvLine(cells: List<String>): String = cells.joinToString(",") { c ->
        if (c.contains(',') || c.contains('"') || c.contains('\n')) "\"" + c.replace("\"", "\"\"") + "\"" else c
    }

    /** 微信 .csv：UTF-8 带 BOM。 */
    fun wechatCsv(): ByteArray {
        val lines = (wechatPreamble + listOf(wechatHeader) + wechatRows).joinToString("\n") { csvLine(it) } + "\n"
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + lines.toByteArray(Charsets.UTF_8)
    }

    /** 微信 .xlsx：共享字符串 + 一部分内联字符串与数字格（金额写成数字，时间写成文字），和真账单一样从 A1 开始。 */
    fun wechatXlsx(): ByteArray {
        val rows = wechatPreamble + listOf(wechatHeader) + wechatRows.map { r -> r.toMutableList().also { it[5] = it[5].removePrefix("¥") } }
        val shared = ArrayList<String>()
        val index = HashMap<String, Int>()
        val sheet = StringBuilder()
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, cells ->
            sheet.append("<row r=\"${r + 1}\">")
            cells.forEachIndexed { c, value ->
                if (value.isEmpty()) return@forEachIndexed
                val ref = ('A' + c).toString() + (r + 1)
                val numeric = r > wechatPreamble.size && c == 5
                when {
                    numeric -> sheet.append("<c r=\"$ref\"><v>${value}</v></c>")
                    c == 3 && r > wechatPreamble.size -> sheet.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${esc(value)}</t></is></c>")
                    else -> {
                        val i = index.getOrPut(value) { shared += value; shared.size - 1 }
                        sheet.append("<c r=\"$ref\" t=\"s\"><v>$i</v></c>")
                    }
                }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        val sst = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${shared.size}" uniqueCount="${shared.size}">""")
        // 第一项用富文本（多段 <r><t>），检查拼接
        shared.forEachIndexed { i, s ->
            if (i == 0 && s.length > 2) {
                sst.append("<si><r><t>${esc(s.substring(0, 2))}</t></r><r><rPr><b/></rPr><t>${esc(s.substring(2))}</t></r></si>")
            } else {
                sst.append("<si><t xml:space=\"preserve\">${esc(s)}</t></si>")
            }
        }
        sst.append("</sst>")
        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", "<Types/>")
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", rels)
            put("xl/sharedStrings.xml", sst.toString())
            put("xl/worksheets/sheet1.xml", sheet.toString())
        }
        return out.toByteArray()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** 支付宝新版 .csv：GBK 编码，前面二十来行说明。 */
    fun alipayCsv(): ByteArray {
        val preamble = listOf(
            "------------------------------------------------------------------------------------",
            "导出信息：",
            "姓名：示例用户",
            "支付宝账户：demo@example.com",
            "起始时间：[2026-09-01 00:00:00]    终止时间：[2026-09-30 23:59:59]",
            "导出交易类型：[全部]",
            "导出时间：[2026-10-01 10:00:00]",
            "共6笔记录",
            "收入：1笔 3.20元",
            "支出：4笔 166.00元",
            "不计收支：1笔 1000.00元",
            "",
            "特别提示：",
            "1.本回单内容可表明支付宝受理了此笔业务，不代表实际到账",
            "------------------------支付宝（中国）网络技术有限公司  电子客户回单------------------------",
        )
        val header = "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,"
        val rows = listOf(
            "2026-09-30 20:01:02,餐饮美食,示例外卖平台,dem***@example.com,外卖订单,支出,45.00,花呗,交易成功,2026093022001100000000000001\t,T0001\t,,",
            "2026-09-29 07:55:00,交通出行,示例地铁,/,乘车码,支出,4.00,余额宝,交易成功,2026092922001100000000000002\t,T0002\t,,",
            "2026-09-28 13:00:00,日用百货,示例超市,/,购物,支出,97.00,招商银行信用卡(5678),交易成功,2026092822001100000000000003\t,T0003\t,,",
            "2026-09-27 09:00:00,投资理财,余额宝,/,余额宝-收益发放,不计收支,1000.00,余额宝,交易成功,2026092722001100000000000004\t,,,",
            "2026-09-26 12:00:00,其他,示例商家,/,收钱码收款,收入,3.20,余额,交易成功,2026092622001100000000000005\t,,,",
            "2026-09-25 18:00:00,服饰装扮,示例服饰店,/,衬衫,支出,20.00,花呗,交易关闭,2026092522001100000000000006\t,,,",
            "2026-09-24 18:00:00,充值缴费,示例运营商,/,话费充值,支出,20.00,余额宝,交易成功,2026092422001100000000000007\t,,,",
        )
        val footer = listOf("", "------------------------------------------------------------------------------------")
        val text = (preamble + header + rows + footer).joinToString("\r\n") + "\r\n"
        return text.toByteArray(Charset.forName("GBK"))
    }

    /** 支付宝旧版 .csv（交易号、成功退款列）。 */
    fun alipayLegacyCsv(): ByteArray {
        val text = listOf(
            "支付宝交易记录明细查询",
            "账号:[demo@example.com]",
            "起始日期:[2026-09-01 00:00:00]    终止日期:[2026-09-30 23:59:59]",
            "---------------------------------交易记录明细列表------------------------------------",
            "交易号                  ,商家订单号               ,交易创建时间              ,付款时间                ,最近修改时间              ,交易来源地     ,类型              ,交易对方            ,商品名称                ,金额（元）   ,收/支     ,交易状态    ,服务费（元）   ,成功退款（元）  ,备注                  ,资金状态     ,",
            "2026091500000001        ,O1                     ,2026-09-15 10:00:00     ,2026-09-15 10:00:05     ,2026-09-15 10:00:05     ,其他（包括阿里巴巴和外部商家）,即时到账交易          ,示例书店           ,图书                ,60.00       ,支出      ,交易成功    ,0.00         ,15.00         ,                    ,已支出      ,",
            "2026091600000002        ,                     ,2026-09-16 10:00:00     ,                        ,2026-09-16 10:00:05     ,其他          ,即时到账交易          ,示例药房           ,药品                ,12.00       ,支出      ,交易成功    ,0.00         ,0.00          ,                    ,已支出      ,",
            "------------------------------------------------------------------------------------",
            "共2笔记录",
        ).joinToString("\r\n")
        return text.toByteArray(Charset.forName("GBK"))
    }

    /** 自己整理的表格：没有收/支列，负数是支出；没有单号。 */
    fun genericCsv(): ByteArray = listOf(
        "日期,金额,商户,备注",
        "2026/9/3 12:00,-28.5,示例食堂,午饭",
        "2026/9/4,-6,示例公交,上班",
        "2026/9/5 09:30,8000,示例公司,工资",
        "2026/9/5 09:30,8000,示例公司,工资",
        "坏行,abc,,",
    ).joinToString("\n").toByteArray(Charsets.UTF_8)
}
