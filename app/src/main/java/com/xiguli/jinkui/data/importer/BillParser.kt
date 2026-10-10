package com.xiguli.jinkui.data.importer

import com.xiguli.jinkui.data.ledger.Money
import com.xiguli.jinkui.data.ledger.TxType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 账单来源。 */
enum class BillSource(val label: String) {
    WECHAT("微信支付"),
    ALIPAY("支付宝"),
    CSV("表格"),
}

/** 没有导入的原因。 */
enum class SkipReason(val label: String) {
    NEUTRAL("不计收支（零钱提现、充值、还款等）"),
    CLOSED("交易关闭或失败"),
    REFUNDED("已全额退款"),
    REFUND_ROW("退款（已从原支出里扣除）"),
    INVALID("时间或金额无法识别"),
    DUPLICATE_IN_FILE("文件里重复的行"),
}

/** 账单里的一笔，已换算成“分”和本地时间。 */
data class BillRecord(
    val source: BillSource,
    val time: LocalDateTime,
    val type: TxType,
    val amount: Long,
    val counterparty: String = "",
    val item: String = "",
    /** 账单自带的类型 / 分类（“商户消费”“餐饮美食”）。 */
    val kind: String = "",
    /** 支付方式（“零钱”“招商银行储蓄卡(1234)”）。 */
    val method: String = "",
    val status: String = "",
    val txNo: String = "",
    val note: String = "",
    /** 退款收入，且文件里找不到被扣减的原支出。 */
    val refund: Boolean = false,
) {
    /** 记到流水备注里的一句话：交易对方 · 商品。 */
    val summary: String
        get() {
            val parts = listOf(counterparty, item).map { it.trim() }.filter { it.isNotEmpty() && it != "/" && it != "-" }.distinct()
            return parts.joinToString(" · ").take(120)
        }
}

class ParsedBill(val source: BillSource, val records: List<BillRecord>, val skipped: Map<SkipReason, Int>) {
    val skippedTotal: Int get() = skipped.values.sum()
}

/** 通用表格的列对应关系（列序号，从 0 开始）。 */
data class ColumnMapping(
    val headerRow: Int = 0,
    val time: Int = -1,
    val amount: Int = -1,
    val direction: Int = -1,
    val counterparty: Int = -1,
    val item: Int = -1,
    val txNo: Int = -1,
    val category: Int = -1,
    /** 没有“收/支”列时：true 表示负数是支出、正数是收入；false 表示全部当作支出。 */
    val negativeIsExpense: Boolean = true,
) {
    val complete: Boolean get() = time >= 0 && amount >= 0
}

/**
 * 解析微信支付、支付宝导出的账单与通用 CSV。纯函数，全部在本机完成。
 *
 * 微信：表头前有十几行说明，表头是「交易时间, 交易类型, 交易对方, 商品, 收/支, 金额(元), 支付方式, 当前状态, 交易单号, 商户单号, 备注」，
 *       .csv（UTF-8）与 .xlsx 两种。
 * 支付宝：GBK 编码的 .csv，表头前有二十来行说明；新版表头「交易时间, 交易分类, 交易对方, 对方账号, 商品说明, 收/支, 金额, 收/付款方式, 交易状态, 交易订单号, 商家订单号, 备注」，
 *       旧版「交易号, 商家订单号, 交易创建时间, 付款时间, …, 金额（元）, 收/支, 交易状态, 服务费（元）, 成功退款（元）, 备注, 资金状态」。
 */
object BillParser {
    private val TIME = listOf("交易时间", "付款时间", "交易创建时间", "时间", "日期")
    private val KIND = listOf("交易类型", "交易分类", "类型")
    private val PARTY = listOf("交易对方", "对方")
    private val ITEM = listOf("商品", "商品说明", "商品名称")
    private val DIRECTION = listOf("收/支", "收支")
    private val AMOUNT = listOf("金额")
    private val METHOD = listOf("支付方式", "收/付款方式")
    private val STATUS = listOf("当前状态", "交易状态")
    private val NUMBER = listOf("交易单号", "交易订单号", "交易号")
    private val NOTE = listOf("备注")
    private val REFUNDED = listOf("成功退款")

    /** 认出微信或支付宝账单；都不是（通用表格）时返回 null。 */
    fun detect(table: TextTable): BillSource? {
        val header = headerRow(table) ?: return null
        val preamble = table.rows.take(header).joinToString(" ") { it.joinToString(" ") }
        val names = table.rows[header].map(::norm)
        return when {
            "微信" in preamble -> BillSource.WECHAT
            "支付宝" in preamble || "alipay" in preamble.lowercase() -> BillSource.ALIPAY
            names.any { it == "商品" } && names.any { it == "当前状态" } -> BillSource.WECHAT
            names.any { it.startsWith("交易分类") || it.startsWith("商品说明") || it.startsWith("商品名称") } -> BillSource.ALIPAY
            else -> null
        }
    }

    /** 表头所在行：同时有时间列、金额列与“收/支”列的第一行。 */
    fun headerRow(table: TextTable): Int? {
        val limit = minOf(table.rows.size, 60)
        for (i in 0 until limit) {
            val names = table.rows[i].map(::norm)
            val hasTime = names.any { n -> TIME.take(3).any { n.startsWith(it) } }
            val hasAmount = names.any { it.startsWith("金额") }
            val hasDirection = names.any { it == "收/支" }
            if (hasTime && hasAmount && hasDirection) return i
        }
        return null
    }

    /** 解析微信 / 支付宝账单。 */
    fun parse(table: TextTable, source: BillSource): ParsedBill {
        val header = headerRow(table) ?: throw BillFormatException("没有找到账单表头，请确认是微信或支付宝导出的原始账单")
        val names = table.rows[header].map(::norm)
        fun col(aliases: List<String>): List<Int> = aliases.mapNotNull { a ->
            names.indexOfFirst { it == a }.takeIf { it >= 0 } ?: names.indexOfFirst { it.startsWith(a) }.takeIf { it >= 0 }
        }.distinct()
        val timeCols = col(TIME)
        val amountCol = col(AMOUNT).firstOrNull() ?: throw BillFormatException("账单里没有金额列")
        val dirCol = col(DIRECTION).firstOrNull() ?: throw BillFormatException("账单里没有“收/支”列")
        val kindCol = col(KIND).firstOrNull()
        val partyCol = col(PARTY).firstOrNull()
        val itemCol = col(ITEM).firstOrNull()
        val methodCol = col(METHOD).firstOrNull()
        val statusCol = col(STATUS).firstOrNull()
        val numberCol = col(NUMBER).firstOrNull()
        val noteCol = col(NOTE).firstOrNull()
        val refundedCol = col(REFUNDED).firstOrNull()

        val skipped = LinkedHashMap<SkipReason, Int>()
        fun skip(reason: SkipReason) { skipped[reason] = (skipped[reason] ?: 0) + 1 }
        val kept = ArrayList<BillRecord>()
        val refundRows = ArrayList<BillRecord>()
        val refundedParties = HashSet<String>()

        for (r in header + 1 until table.rows.size) {
            val row = table.rows[r]
            fun at(c: Int?): String = if (c == null) "" else row.getOrNull(c)?.trim().orEmpty()
            val first = row.firstOrNull().orEmpty()
            // 支付宝账单末尾有一段“----”分隔与说明文字
            if (first.startsWith("-") || first.startsWith("共") && row.count { it.isNotEmpty() } <= 2) continue
            val time = timeCols.asSequence().map { at(it) }.firstOrNull { it.isNotEmpty() }?.let(::parseTime)
            var amount = parseAmount(at(amountCol))?.let { kotlin.math.abs(it) }
            if (time == null || amount == null) {
                skip(SkipReason.INVALID)
                continue
            }
            val direction = at(dirCol)
            val status = at(statusCol)
            val kind = at(kindCol)
            val party = at(partyCol)
            val type = when {
                direction.startsWith("支出") -> TxType.EXPENSE
                direction.startsWith("收入") -> TxType.INCOME
                else -> null
            }
            if (type == null) {
                skip(SkipReason.NEUTRAL)
                continue
            }
            if (CLOSED_WORDS.any { it in status }) {
                skip(SkipReason.CLOSED)
                continue
            }
            if (type == TxType.EXPENSE && ("全额退款" in status)) {
                refundedParties += party
                skip(SkipReason.REFUNDED)
                continue
            }
            // 部分退款：微信“已退款(￥5.00)”；支付宝旧版“成功退款（元）”列
            val partial = PARTIAL_REFUND.find(status)?.groupValues?.get(1)?.let(::parseAmount)
                ?: refundedCol?.let { parseAmount(at(it)) }?.takeIf { it > 0 }
            if (type == TxType.EXPENSE && partial != null && partial > 0) {
                refundedParties += party
                amount -= partial
                if (amount <= 0) {
                    skip(SkipReason.REFUNDED)
                    continue
                }
            }
            if (amount <= 0 || amount > Money.MAX_CENTS) {
                skip(SkipReason.INVALID)
                continue
            }
            val record = BillRecord(
                source = source,
                time = time,
                type = type,
                amount = amount,
                counterparty = party.takeUnless { it == "/" }.orEmpty(),
                item = at(itemCol).takeUnless { it == "/" }.orEmpty(),
                kind = kind,
                method = at(methodCol).takeUnless { it == "/" }.orEmpty(),
                status = status,
                txNo = at(numberCol).trim().trimStart('\''),
                note = at(noteCol).takeUnless { it == "/" }.orEmpty(),
            )
            val isRefund = type == TxType.INCOME && ("退款" in kind || "退款" in status)
            if (isRefund) refundRows += record else kept += record
        }
        // 退款收入：原支出已经在上面扣减 / 跳过的，退款行不再记，免得算两次；找不到原支出的留给用户决定
        for (refund in refundRows) {
            if (refund.counterparty in refundedParties) skip(SkipReason.REFUND_ROW) else kept += refund.copy(refund = true)
        }
        return ParsedBill(source, dedupeInFile(kept, skipped), skipped)
    }

    /** 通用表格：按用户确认过的列对应关系解析。 */
    fun parseGeneric(table: TextTable, mapping: ColumnMapping): ParsedBill {
        if (!mapping.complete) throw BillFormatException("请先选好“时间”和“金额”两列")
        val skipped = LinkedHashMap<SkipReason, Int>()
        fun skip(reason: SkipReason) { skipped[reason] = (skipped[reason] ?: 0) + 1 }
        val kept = ArrayList<BillRecord>()
        for (r in mapping.headerRow + 1 until table.rows.size) {
            val row = table.rows[r]
            fun at(c: Int): String = if (c < 0) "" else row.getOrNull(c)?.trim().orEmpty()
            val time = parseTime(at(mapping.time))
            val signed = parseAmount(at(mapping.amount))
            if (time == null || signed == null || signed == 0L) {
                skip(SkipReason.INVALID)
                continue
            }
            val type: TxType? = if (mapping.direction >= 0) {
                directionOf(at(mapping.direction)) ?: if (at(mapping.direction).isEmpty()) signedType(signed, mapping) else null
            } else {
                signedType(signed, mapping)
            }
            if (type == null) {
                skip(SkipReason.NEUTRAL)
                continue
            }
            val amount = kotlin.math.abs(signed)
            if (amount > Money.MAX_CENTS) {
                skip(SkipReason.INVALID)
                continue
            }
            kept += BillRecord(
                source = BillSource.CSV,
                time = time,
                type = type,
                amount = amount,
                counterparty = at(mapping.counterparty),
                item = at(mapping.item),
                kind = at(mapping.category),
                txNo = at(mapping.txNo),
            )
        }
        return ParsedBill(BillSource.CSV, dedupeInFile(kept, skipped), skipped)
    }

    private fun signedType(signed: Long, mapping: ColumnMapping): TxType =
        if (!mapping.negativeIsExpense) TxType.EXPENSE else if (signed < 0) TxType.EXPENSE else TxType.INCOME

    /** “支出 / 支 / 出 / 付款”与“收入 / 收 / 入 / 收款”。 */
    fun directionOf(text: String): TxType? {
        val t = text.trim()
        return when {
            t.isEmpty() -> null
            t.contains("不计") -> null
            t.contains("支") || t.contains("出") || t.contains("付") || t.equals("expense", true) || t == "-" -> TxType.EXPENSE
            t.contains("收") || t.contains("入") || t.equals("income", true) || t == "+" -> TxType.INCOME
            else -> null
        }
    }

    /** 猜通用表格的表头行与各列。 */
    fun guessMapping(table: TextTable): ColumnMapping {
        val header = (0 until minOf(table.rows.size, 30)).firstOrNull { i -> table.rows[i].count { it.isNotEmpty() } >= 2 } ?: 0
        val names = table.rows.getOrNull(header).orEmpty().map(::norm)
        fun find(vararg keys: String): Int = names.indexOfFirst { n -> keys.any { n.contains(it) } }
        val negatives = table.rows.drop(header + 1).take(200).any { row ->
            val c = find("金额", "amount", "价格", "收支金额")
            c >= 0 && (parseAmount(row.getOrNull(c).orEmpty()) ?: 0L) < 0L
        }
        return ColumnMapping(
            headerRow = header,
            time = find("时间", "日期", "date", "time"),
            amount = find("金额", "amount", "价格"),
            direction = find("收/支", "收支", "类型", "方向", "type"),
            counterparty = find("对方", "商户", "商家", "收款方", "payee", "merchant"),
            item = find("商品", "说明", "备注", "摘要", "描述", "note", "memo"),
            txNo = find("单号", "流水号", "交易号", "编号", "id"),
            category = find("分类", "category"),
            negativeIsExpense = negatives,
        ).let { m -> if (m.direction == m.category) m.copy(category = -1) else m }
    }

    private fun dedupeInFile(records: List<BillRecord>, skipped: MutableMap<SkipReason, Int>): List<BillRecord> {
        val seen = HashSet<String>()
        val out = ArrayList<BillRecord>()
        for (r in records) {
            if (seen.add(BillImport.dedupeKey(r))) out += r else skipped[SkipReason.DUPLICATE_IN_FILE] = (skipped[SkipReason.DUPLICATE_IN_FILE] ?: 0) + 1
        }
        return out.sortedByDescending { it.time }
    }

    private val CLOSED_WORDS = listOf("关闭", "失败", "撤销", "未支付", "等待付款", "已取消")
    private val PARTIAL_REFUND = Regex("已退款[（(]?[¥￥]?\\s*([0-9.,]+)")

    /** 去掉空白、括号里的单位，统一全角括号。 */
    fun norm(name: String): String = name.trim().replace(" ", "").replace("\u3000", "")

    /** “¥12.50”“-1,234.5”“12.5元”“12.499999”（.xlsx 里的浮点）→ 分。超出两位小数时四舍五入。 */
    fun parseAmount(text: String): Long? {
        var s = text.trim().replace(",", "").replace("，", "").replace("¥", "").replace("￥", "")
            .replace("元", "").replace("RMB", "", ignoreCase = true).replace("CNY", "", ignoreCase = true).trim()
        if (s.isEmpty()) return null
        if (s.startsWith("(") && s.endsWith(")")) s = "-" + s.substring(1, s.length - 1)
        s = s.replace("−", "-")
        return try {
            val cents = BigDecimal(s).setScale(2, RoundingMode.HALF_UP).movePointRight(2)
            if (cents.abs() > BigDecimal(Money.MAX_CENTS)) null else cents.longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    private val DATE_TIME = Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?(?:[ T]+(\\d{1,2})[:：](\\d{1,2})(?:[:：](\\d{1,2}))?)?")

    /** “2026-10-09 12:30:45”“2026/10/9 12:30”“2026年10月9日 12:30”“2026-10-09”，以及 .xlsx 的日期序列数。 */
    fun parseTime(text: String): LocalDateTime? {
        val s = text.trim()
        if (s.isEmpty()) return null
        DATE_TIME.find(s)?.let { m ->
            val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            val h = m.groupValues[4].toIntOrNull()
            val mi = m.groupValues[5].toIntOrNull() ?: 0
            val se = m.groupValues[6].toIntOrNull() ?: 0
            return runCatching {
                LocalDateTime.of(LocalDate.of(y, mo, d), if (h == null) LocalTime.NOON else LocalTime.of(h, mi, se))
            }.getOrNull()
        }
        // Excel 日期序列数：1899-12-30 起的天数，小数部分是一天中的时刻
        val serial = s.toDoubleOrNull() ?: return null
        if (serial < 20_000 || serial > 80_000) return null
        val days = kotlin.math.floor(serial).toLong()
        val seconds = Math.round((serial - days) * 86_400).coerceIn(0, 86_399)
        return LocalDate.of(1899, 12, 30).plusDays(days).atStartOfDay().plusSeconds(seconds)
    }
}
