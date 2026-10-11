package com.xiguli.jinkui.data.importer

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 测试用的最小 .xls（BIFF8 + OLE2 复合文档）生成器。只为测试 [XlsReader] 而写，数据全部是合成的。
 *
 * 一格的取值决定写成哪种记录：
 *  - [String] → 默认 LABELSST（进共享字符串表，表大于 8224 字节时用 CONTINUE 拆开，字符可能被拆在两段之间）；
 *  - [Label] → LABEL（字符串直接写在格里）；
 *  - [Num] → NUMBER；[Rk] → RK；同一行连续几个 [Rk] 合并成 MULRK；
 *  - [Formula] → FORMULA（缓存的数字结果）；[FormulaText] → FORMULA + STRING（缓存的字符串结果）。
 */
object XlsFixture {
    data class Label(val text: String)
    data class Num(val value: Double)
    /** RK 的原始 32 位值（测试里手工编码：整数、÷100、截断的 double）。 */
    data class Rk(val raw: Int)
    data class Formula(val value: Double)
    data class FormulaText(val text: String)

    /** RK 编码：30 位整数。 */
    fun rkInt(n: Int): Rk = Rk((n shl 2) or 0x02)
    /** RK 编码：整数 ÷ 100（例如 1234 → 12.34）。 */
    fun rkCents(n: Int): Rk = Rk((n shl 2) or 0x03)
    /** RK 编码：double 的高 30 位（只适合低位全是 0 的数，如 0.5、2.25）。 */
    fun rkDouble(d: Double): Rk = Rk((java.lang.Double.doubleToLongBits(d) ushr 32).toInt() and 0xFFFFFFFC.toInt())

    private class Out {
        val bytes = ByteArrayOutputStream()
        fun record(type: Int, data: ByteArray) {
            require(data.size <= 8224)
            u16(type); u16(data.size); bytes.write(data)
        }
        fun u16(v: Int) { bytes.write(v and 0xFF); bytes.write((v ushr 8) and 0xFF) }
        val size: Int get() = bytes.size()
    }

    private fun le(size: Int, block: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(block).array()

    private fun isAscii(s: String) = s.all { it.code < 0x80 }

    /** XLUnicodeString（2 字节长度）：ASCII 用压缩格式，其余 UTF-16LE。 */
    private fun xlString(s: String): ByteArray {
        val wide = !isAscii(s)
        val out = ByteArrayOutputStream()
        out.write(s.length and 0xFF); out.write(s.length ushr 8); out.write(if (wide) 1 else 0)
        for (ch in s) {
            out.write(ch.code and 0xFF)
            if (wide) out.write(ch.code ushr 8)
        }
        return out.toByteArray()
    }

    /** SST 拆成若干段：第一段是 SST，其后是 CONTINUE；字符跨段时新段先写标志位。 */
    private fun sstRecords(strings: List<String>, totalRefs: Int): List<ByteArray> {
        val max = 8224
        val records = ArrayList<ByteArray>()
        var cur = ByteArrayOutputStream()
        fun flush() { records += cur.toByteArray(); cur = ByteArrayOutputStream() }
        fun room() = max - cur.size()
        cur.write(le(8) { putInt(totalRefs); putInt(strings.size) })
        for (s in strings) {
            val wide = !isAscii(s)
            if (room() < 4 + (if (wide) 2 else 1)) flush()
            cur.write(s.length and 0xFF); cur.write(s.length ushr 8); cur.write(if (wide) 1 else 0)
            var i = 0
            while (i < s.length) {
                val per = if (wide) 2 else 1
                if (room() < per) {
                    flush()
                    cur.write(if (wide) 1 else 0) // 字符跨段：新段开头重新给出标志位
                }
                val ch = s[i].code
                cur.write(ch and 0xFF)
                if (wide) cur.write(ch ushr 8)
                i++
            }
        }
        flush()
        return records
    }

    /** 生成一个只有一张表（名为 [sheetName]）的工作簿流。 */
    fun workbook(rows: List<List<Any?>>, sheetName: String = "账单", biffVersion: Int = 0x0600): ByteArray {
        val strings = LinkedHashMap<String, Int>()
        var refs = 0
        for (row in rows) for (cell in row) if (cell is String && cell.isNotEmpty()) { refs++; strings.getOrPut(cell) { strings.size } }

        val out = Out()
        out.record(0x0809, le(16) { putShort(biffVersion.toShort()); putShort(0x0005); putShort(0x1234); putShort(0x07CC); putInt(0); putInt(0x0600) })
        val name = xlString(sheetName).let { it.copyOfRange(0, 0) + byteArrayOf(sheetName.length.toByte()) + it.copyOfRange(2, it.size) }
        val boundsheetAt = out.size
        out.record(0x0085, le(4) { putInt(0) } + byteArrayOf(0, 0) + name)
        val sst = sstRecords(strings.keys.toList(), refs)
        sst.forEachIndexed { i, data -> out.record(if (i == 0) 0x00FC else 0x003C, data) }
        out.record(0x000A, ByteArray(0))

        val sheetAt = out.size
        out.record(0x0809, le(16) { putShort(biffVersion.toShort()); putShort(0x0010); putShort(0x1234); putShort(0x07CC); putInt(0); putInt(0x0600) })
        rows.forEachIndexed { r, row ->
            var c = 0
            while (c < row.size) {
                when (val cell = row[c]) {
                    null -> Unit
                    is String -> if (cell.isNotEmpty()) out.record(0x00FD, le(10) { putShort(r.toShort()); putShort(c.toShort()); putShort(15); putInt(strings.getValue(cell)) })
                    is Label -> out.record(0x0204, le(6) { putShort(r.toShort()); putShort(c.toShort()); putShort(15) } + xlString(cell.text))
                    is Num -> out.record(0x0203, le(14) { putShort(r.toShort()); putShort(c.toShort()); putShort(15); putDouble(cell.value) })
                    is Rk -> {
                        // 连续的 RK 合并成 MULRK
                        var end = c
                        while (end + 1 < row.size && row[end + 1] is Rk) end++
                        if (end == c) {
                            out.record(0x027E, le(10) { putShort(r.toShort()); putShort(c.toShort()); putShort(15); putInt(cell.raw) })
                        } else {
                            val n = end - c + 1
                            out.record(0x00BD, le(6 + n * 6) {
                                putShort(r.toShort()); putShort(c.toShort())
                                for (k in c..end) { putShort(15); putInt((row[k] as Rk).raw) }
                                putShort(end.toShort())
                            })
                            c = end
                        }
                    }
                    is Formula -> out.record(0x0006, le(20) { putShort(r.toShort()); putShort(c.toShort()); putShort(15); putDouble(cell.value); putShort(0); putInt(0) })
                    is FormulaText -> {
                        out.record(0x0006, le(20) {
                            putShort(r.toShort()); putShort(c.toShort()); putShort(15)
                            put(0); put(0); put(0); put(0); put(0); put(0); putShort(0xFFFF.toShort())
                            putShort(0); putInt(0)
                        })
                        out.record(0x0207, xlString(cell.text))
                    }
                    else -> error("不支持的格：$cell")
                }
                c++
            }
        }
        out.record(0x000A, ByteArray(0))
        val stream = out.bytes.toByteArray()
        // 回填 BOUNDSHEET 里工作表 BOF 的位置
        ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN).putInt(boundsheetAt + 4, sheetAt)
        return stream
    }

    /** 把工作簿流装进 OLE2 复合文档（版本 3，512 字节扇区）。小于 4096 字节时放进迷你流。 */
    fun compound(workbook: ByteArray, streamName: String = "Workbook"): ByteArray {
        val sector = 512
        val mini = workbook.size < 4096
        fun sectorsFor(n: Int, size: Int) = (n + size - 1) / size
        val dataSectors = if (mini) sectorsFor(sectorsFor(workbook.size, 64) * 64, sector) else sectorsFor(workbook.size, sector)
        val miniFatSectors = if (mini) 1 else 0
        var fatSectors = 1
        while ((fatSectors + 1 + miniFatSectors + dataSectors) > fatSectors * 128) fatSectors++
        val total = fatSectors + 1 + miniFatSectors + dataSectors
        val fat = IntArray(fatSectors * 128) { -1 }
        for (i in 0 until fatSectors) fat[i] = -3
        val dirSector = fatSectors
        fat[dirSector] = -2
        val miniFatSector = if (mini) dirSector + 1 else -2
        if (mini) fat[miniFatSector] = -2
        val dataStart = dirSector + 1 + miniFatSectors
        for (i in 0 until dataSectors) fat[dataStart + i] = if (i == dataSectors - 1) -2 else dataStart + i + 1

        val file = ByteBuffer.allocate((total + 1) * sector).order(ByteOrder.LITTLE_ENDIAN)
        // 文件头
        file.put(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()))
        file.put(ByteArray(16))
        file.putShort(0x003E); file.putShort(0x0003); file.putShort(0xFFFE.toShort()); file.putShort(9); file.putShort(6)
        file.put(ByteArray(6)); file.putInt(0); file.putInt(fatSectors); file.putInt(dirSector); file.putInt(0); file.putInt(4096)
        file.putInt(miniFatSector); file.putInt(miniFatSectors); file.putInt(-2); file.putInt(0)
        for (i in 0 until 109) file.putInt(if (i < fatSectors) i else -1)
        fun at(s: Int) = (s + 1) * sector
        for (i in fat.indices) file.putInt(at(0) + i * 4, fat[i])
        // 目录：根目录项 + 工作簿流
        fun dirEntry(index: Int, name: String, type: Int, child: Int, start: Int, size: Int) {
            val off = at(dirSector) + index * 128
            name.forEachIndexed { k, ch -> file.putShort(off + k * 2, ch.code.toShort()) }
            file.putShort(off + 64, ((name.length + 1) * 2).toShort())
            file.put(off + 66, type.toByte()); file.put(off + 67, 1)
            file.putInt(off + 68, -1); file.putInt(off + 72, -1); file.putInt(off + 76, child)
            file.putInt(off + 116, start); file.putInt(off + 120, size)
        }
        val miniStreamSize = if (mini) sectorsFor(workbook.size, 64) * 64 else 0
        dirEntry(0, "Root Entry", 5, 1, if (mini) dataStart else -2, miniStreamSize)
        dirEntry(1, streamName, 2, -1, if (mini) 0 else dataStart, workbook.size)
        for (i in 2 until 4) { val off = at(dirSector) + i * 128; file.putInt(off + 68, -1); file.putInt(off + 72, -1); file.putInt(off + 76, -1) }
        if (mini) {
            val n = sectorsFor(workbook.size, 64)
            for (i in 0 until 128) file.putInt(at(miniFatSector) + i * 4, if (i < n - 1) i + 1 else if (i == n - 1) -2 else -1)
        }
        System.arraycopy(workbook, 0, file.array(), at(dataStart), workbook.size)
        return file.array()
    }

    fun xls(rows: List<List<Any?>>): ByteArray = compound(workbook(rows))

    // ── 合成的“记账 App”导出 ─────────────────────────────────────

    val APP_HEADER = listOf(
        "日期", "收支类型", "金额", "类别", "二级分类", "账户", "账本", "退款", "优惠", "备注", "标签",
        "报销账户", "报销金额", "报销明细", "多币种", "地址", "创建用户", "其他", "附件1", "附件2", "附件3", "附件4", "附件5",
    )

    /** 一行：只填常用的几列，其余留空（和真实导出一样，空格不写记录）。 */
    fun appRow(
        time: String,
        type: String,
        amount: String,
        main: String,
        sub: String = "",
        account: String = "",
        refund: Any? = null,
        discount: Any? = null,
        note: String = "",
    ): List<Any?> = listOf(time, type, amount, main, sub, account.ifEmpty { null }, Label("日常账本"), refund, discount, note.ifEmpty { null })

    private val expenseKinds = listOf(
        "食品餐饮" to "早午晚餐", "出行交通" to "打车", "购物消费" to "手机数码", "送礼人情" to "红包", "送礼人情" to "礼金人情",
        "居家生活" to "话费", "健康医疗" to "药品", "休闲娱乐" to "虚拟充值", "文化教育" to "书籍", "其他" to "借出", "其他" to "理财支出",
    )
    private val incomeKinds = listOf("收入" to "工资", "收入" to "理财盈利", "收入" to "红包", "收入" to "兼职外快", "收入" to "礼金人情")
    private val notes = listOf("支付宝 账户余额补齐", "微信钱包 账户余额补齐", "午饭", "", "打车回家", "", "群红包", "话费充值 50 元")

    /**
     * 合成样例：[count] 行（不含表头），确定性的伪随机，结构与真实导出一致——
     * 日期是文本、金额是文本且支出为负数、账户多为空偶尔“花呗”、退款与优惠偶尔有值。
     */
    fun appSample(count: Int = 240): List<List<Any?>> {
        var seed = 20261011L
        fun next(n: Int): Int { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) % n).toInt() }
        val rows = ArrayList<List<Any?>>()
        rows += APP_HEADER
        for (i in 0 until count) {
            val expense = i % 5 != 0 // 每 5 行 1 行收入
            val day = 1 + (i % 28)
            val time = "2026-09-%02d %02d:%02d".format(day, 7 + i % 15, (i * 7) % 60)
            val cents = 100 + next(60_000)
            val amount = (if (expense) "-" else "") + "%d.%d".format(cents / 100, (cents % 100) / 10)
            val (main, sub) = if (expense) expenseKinds[i % expenseKinds.size] else incomeKinds[i % incomeKinds.size]
            val note = notes[i % notes.size] + if (i % 3 == 0) "" else " #$i"
            rows += appRow(
                time = time,
                type = if (expense) "支出" else "收入",
                amount = amount,
                main = main,
                sub = sub,
                account = if (expense && i % 11 == 0) "花呗" else "",
                discount = if (i % 13 == 0) "2.0" else null,
                note = note,
            )
        }
        return rows
    }

    /**
     * 1.3 导入清理的合成样例：每一类至少两笔（全部是编出来的人名与金额，不含任何真实账单）。
     *  - 余额校准：支付宝 / 微信钱包 “账户余额补齐”，收入与支出各有；
     *  - 内部转账：余额宝-单次转入（记成收入）、理财支出、零钱提现；
     *  - 收进余额宝的转账收款（真实收入，记到余额宝）；
     *  - 借贷：送礼人情/借出（转账给某人）、收入/借入；
     *  - 花呗消费、转账手续费；
     *  - 普通收支：其他/空二级、收入/其他、理财盈利、餐饮。
     */
    fun cleanupSample(): List<List<Any?>> = listOf(
        APP_HEADER,
        appRow("2026-09-01 09:00", "收入", "5000.0", "收入", "其他", note = "支付宝 账户余额补齐"),
        appRow("2026-09-01 09:05", "支出", "-1200.0", "其他", "", note = "微信钱包 账户余额补齐"),
        appRow("2026-09-01 09:10", "收入", "300.0", "收入", "其他", note = "微信钱包 账户余额补齐"),
        appRow("2026-09-02 10:00", "收入", "2000.0", "收入", "理财盈利", note = "余额宝-单次转入"),
        appRow("2026-09-02 10:30", "支出", "-500.0", "其他", "理财支出", note = "基金定投"),
        appRow("2026-09-02 11:00", "支出", "-200.0", "其他", "", note = "微信零钱提现"),
        appRow("2026-09-03 12:00", "收入", "888.0", "收入", "兼职外快", note = "转账收款到余额宝"),
        appRow("2026-09-04 13:00", "支出", "-600.0", "送礼人情", "借出", note = "微信转账给小李子"),
        appRow("2026-09-04 14:00", "收入", "1000.0", "收入", "借入", note = "支付宝 转账收款 老王"),
        appRow("2026-09-05 08:00", "支出", "-35.5", "出行交通", "打车", account = "花呗"),
        appRow("2026-09-05 08:30", "支出", "-12.8", "食品餐饮", "饮料酒水", account = "花呗"),
        appRow("2026-09-05 09:00", "支出", "-0.5", "其他", "", note = "微信钱包 转账手续费"),
        appRow("2026-09-05 09:10", "支出", "-0.2", "其他", "", note = "微信钱包 转账手续费"),
        appRow("2026-09-06 12:00", "支出", "-18.0", "食品餐饮", "午餐", note = "微信 牛肉面"),
        appRow("2026-09-06 15:00", "支出", "-66.0", "其他", "", note = "杂费"),
        appRow("2026-09-07 10:00", "收入", "120.0", "收入", "其他", note = "支付宝 收钱码收款"),
        appRow("2026-09-07 11:00", "收入", "3.21", "收入", "理财盈利"),
    )

    /**
     * 1.4.0-alpha02 欠款识别的合成样例（一木记账风格，人名与金额全部是编的）：
     *  - 花呗：8 月消费 120 + 80、9 月 8 日“花呗还款”200、9 月消费 50；
     *  - 借出“小李子”600，9 月 20 日收款 600（已结清）；
     *  - 借入“老王”1000，9 月 25 日还款 400（部分还款，剩 600）；
     *  - 一张没设账单日的信用卡“示例银行信用卡”消费 300；
     *  - 一笔普通支出（分类不受影响）。
     */
    fun debtSample(): List<List<Any?>> = listOf(
        APP_HEADER,
        appRow("2026-08-05 12:00", "支出", "-120.0", "购物消费", "日用百货", account = "花呗", note = "超市"),
        appRow("2026-08-20 18:30", "支出", "-80.0", "食品餐饮", "晚餐", account = "花呗", note = "外卖"),
        appRow("2026-09-03 08:10", "支出", "-50.0", "出行交通", "打车", account = "花呗"),
        appRow("2026-09-08 09:00", "支出", "-200.0", "其他", "", account = "支付宝", note = "花呗还款"),
        appRow("2026-09-04 13:00", "支出", "-600.0", "送礼人情", "借出", note = "微信转账给小李子"),
        appRow("2026-09-20 10:00", "收入", "600.0", "收入", "收款", note = "小李子还钱"),
        appRow("2026-09-04 14:00", "收入", "1000.0", "收入", "借入", note = "支付宝 转账收款 老王"),
        appRow("2026-09-25 19:00", "支出", "-400.0", "其他", "还款", note = "还给老王"),
        appRow("2026-09-10 20:00", "支出", "-300.0", "购物消费", "手机数码", account = "示例银行信用卡", note = "耳机"),
        appRow("2026-09-12 12:00", "支出", "-18.0", "食品餐饮", "午餐", note = "微信 牛肉面"),
    )
}
