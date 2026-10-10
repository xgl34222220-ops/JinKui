package com.xiguli.jinkui.data.importer

import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/** 读不出表格时抛出，[message] 直接给用户看。 */
class BillFormatException(message: String) : Exception(message)

/** 一张二维文本表：每行若干格，已去掉首尾空白与制表符。 */
class TextTable(val rows: List<List<String>>) {
    fun cell(row: Int, col: Int): String = rows.getOrNull(row)?.getOrNull(col) ?: ""
}

/**
 * 把账单文件读成 [TextTable]：.xlsx 自己解压读 XML（java.util.zip + XmlPullParser，不引入依赖），
 * .xls（Excel 97-2003）用 [XlsReader] 读，文本按 CSV 读（格式只看文件内容，不看扩展名），自动识别 UTF-8 / UTF-16 / GBK（GB18030）编码。全部在本机内存里完成。
 */
object TableReader {
    const val MAX_BYTES = 30 * 1024 * 1024
    const val MAX_ROWS = 100_000
    private const val MAX_ENTRY_BYTES = 80L * 1024 * 1024

    /** 按文件内容（而不是扩展名）认出的格式。 */
    enum class Kind { XLSX, XLS, TEXT, UNSUPPORTED }

    /**
     * 看文件开头的“魔数”判断格式：PK → .xlsx（zip），D0 CF 11 E0 → .xls（OLE2 复合文档），
     * 能按文本解码且不含二进制控制字符 → CSV / 文本；其余（PDF、图片、压缩包等）不支持。
     * 扩展名经常不可信（例如 .xls 其实是 CSV，或 .csv 其实是 .xlsx），所以只看内容。
     */
    fun sniff(bytes: ByteArray): Kind {
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2].toInt() in 1..8) return Kind.XLSX
        if (XlsReader.isCompoundFile(bytes)) return Kind.XLS
        if (bytes.isEmpty() || knownBinary(bytes)) return Kind.UNSUPPORTED
        val head = bytes.copyOf(minOf(bytes.size, 4096))
        val bom16 = head.size >= 2 && ((head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) || (head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()))
        if (bom16) return Kind.TEXT
        // 文本文件里不该出现 NUL 与大量控制字符（制表、换行、回车除外）
        val control = head.count { val b = it.toInt() and 0xFF; b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D && b != 0x0C }
        return if (control == 0 || control * 100 < head.size) Kind.TEXT else Kind.UNSUPPORTED
    }

    /** PDF、图片、压缩包：开头是可打印字符也不能当文本读。 */
    private fun knownBinary(bytes: ByteArray): Boolean {
        fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { (bytes[it].toInt() and 0xFF) == b[it] }
        return starts(0x25, 0x50, 0x44, 0x46) || starts(0x89, 0x50, 0x4E, 0x47) || starts(0xFF, 0xD8, 0xFF) ||
            starts(0x47, 0x49, 0x46, 0x38) || starts(0x52, 0x61, 0x72, 0x21) || starts(0x37, 0x7A, 0xBC, 0xAF) || starts(0x1F, 0x8B)
    }

    /** 不支持的文件给出具体的说明（文件名只用来猜用户想导入的是什么）。 */
    fun unsupportedMessage(bytes: ByteArray, fileName: String): String {
        val name = fileName.lowercase()
        fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { (bytes[it].toInt() and 0xFF) == b[it] }
        return when {
            bytes.isEmpty() -> "这个文件是空的"
            starts(0x25, 0x50, 0x44, 0x46) -> "暂不支持 PDF 账单。请在微信 / 支付宝或记账 App 里导出 Excel（.xlsx / .xls）或 CSV 文件"
            starts(0x89, 0x50, 0x4E, 0x47) || starts(0xFF, 0xD8, 0xFF) || starts(0x47, 0x49, 0x46, 0x38) -> "这是一张图片，不是账单文件。请导出 Excel（.xlsx / .xls）或 CSV 文件"
            starts(0x52, 0x61, 0x72, 0x21) || starts(0x37, 0x7A, 0xBC, 0xAF) || starts(0x1F, 0x8B) || starts(0x50, 0x4B) -> "这是一个压缩包，请先解压，再选择里面的账单文件"
            name.endsWith(".numbers") || name.endsWith(".et") -> "暂不支持这种表格格式，请另存为 .xlsx 或 .csv 后再导入"
            else -> "无法识别这个文件的格式。支持 Excel（.xlsx / .xls）与 CSV 文本文件"
        }
    }

    fun read(bytes: ByteArray, fileName: String = ""): TextTable {
        if (bytes.size > MAX_BYTES) throw BillFormatException("文件太大了（上限 30 MB）")
        return when (sniff(bytes)) {
            Kind.XLSX -> readXlsx(bytes)
            Kind.XLS -> XlsReader.read(bytes)
            Kind.TEXT -> readCsv(decode(bytes))
            Kind.UNSUPPORTED -> throw BillFormatException(unsupportedMessage(bytes, fileName))
        }
    }

    // ── 编码 ──────────────────────────────────────────────────────

    /** UTF-8（含 BOM）→ UTF-16（有 BOM）→ 严格 UTF-8 → GB18030（GBK 的超集，支付宝账单用它）。 */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return strict(bytes, Charsets.UTF_8) ?: String(bytes, Charset.forName("GB18030"))
    }

    private fun strict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    // ── CSV ───────────────────────────────────────────────────────

    /** RFC 4180：引号包起来的格里可以有逗号、换行，"" 表示一个引号。 */
    fun readCsv(text: String): TextTable {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("\uFEFF")
        fun endCell() {
            row += clean(cell.toString())
            cell.setLength(0)
        }
        fun endRow() {
            endCell()
            if (row.any { it.isNotEmpty() }) rows += row
            if (rows.size > MAX_ROWS) throw BillFormatException("行数太多了（上限 ${MAX_ROWS} 行）")
            row = ArrayList()
        }
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (cell.isBlank()) { cell.setLength(0); quoted = true } else cell.append(c)
                    ',' -> endCell()
                    '\r' -> {
                        endRow()
                        if (i + 1 < s.length && s[i + 1] == '\n') i++
                    }
                    '\n' -> endRow()
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return TextTable(rows)
    }

    private fun clean(raw: String): String = raw.trim { it.isWhitespace() || it == '\t' || it == '\u00A0' || it == '\uFEFF' }

    // ── XLSX ──────────────────────────────────────────────────────

    /** 只读第一张工作表：共享字符串、内联字符串、数字三种格。 */
    fun readXlsx(bytes: ByteArray): TextTable {
        val entries = HashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name == "xl/sharedStrings.xml" || name == "xl/workbook.xml" || name == "xl/_rels/workbook.xml.rels" ||
                        (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                    ) {
                        entries[name] = readLimited(zip)
                    }
                }
            }
        } catch (e: BillFormatException) {
            throw e
        } catch (e: Exception) {
            throw BillFormatException("无法打开这个 .xlsx 文件，请确认已经解压出账单本身")
        }
        val sheetName = firstSheet(entries) ?: throw BillFormatException("这个 .xlsx 里没有工作表")
        val shared = entries["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        return try {
            sheet(entries.getValue(sheetName), shared)
        } catch (e: BillFormatException) {
            throw e
        } catch (e: Exception) {
            throw BillFormatException("这个 .xlsx 文件的内容无法识别")
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw BillFormatException("文件内容过大，无法读取")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** 工作簿里的第一张表（按 workbook.xml 的顺序），找不到时退回文件名最小的那张。 */
    private fun firstSheet(entries: Map<String, ByteArray>): String? {
        val sheets = entries.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
        if (sheets.isEmpty()) return null
        runCatching {
            var rid: String? = null
            parse(entries.getValue("xl/workbook.xml")) { p ->
                if (rid == null && p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                    rid = (0 until p.attributeCount).firstOrNull { p.getAttributeName(it) == "id" || p.getAttributeName(it) == "r:id" }
                        ?.let { p.getAttributeValue(it) }
                }
            }
            var target: String? = null
            parse(entries.getValue("xl/_rels/workbook.xml.rels")) { p ->
                if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship" && p.getAttributeValue(null, "Id") == rid) {
                    target = p.getAttributeValue(null, "Target")
                }
            }
            val path = target?.removePrefix("/")?.let { if (it.startsWith("xl/")) it else "xl/$it" }
            if (path != null && path in sheets) return path
        }
        return sheets.minWithOrNull(compareBy<String> { it.length }.thenBy { it })
    }

    private fun parse(xml: ByteArray, onEvent: (XmlPullParser) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        while (parser.next() != XmlPullParser.END_DOCUMENT) onEvent(parser)
    }

    private fun local(name: String?): String = name?.substringAfter(':') ?: ""

    private fun sharedStrings(xml: ByteArray): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inSi = false
        var inT = false
        var inPhonetic = false
        parse(xml) { p ->
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "si" -> { inSi = true; current.setLength(0) }
                    "t" -> inT = true
                    "rPh" -> inPhonetic = true
                }
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "si" -> { inSi = false; result += current.toString() }
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                }
                XmlPullParser.TEXT -> if (inSi && inT && !inPhonetic) current.append(p.text)
            }
        }
        return result
    }

    private fun sheet(xml: ByteArray, shared: List<String>): TextTable {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        var col = 0
        var type: String? = null
        val value = StringBuilder()
        var inValue = false
        var inCell = false
        parse(xml) { p ->
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "row" -> { row = ArrayList(); col = 0 }
                    "c" -> {
                        inCell = true
                        type = p.getAttributeValue(null, "t")
                        val ref = p.getAttributeValue(null, "r")
                        col = if (ref != null) columnIndex(ref) else row.size
                        value.setLength(0)
                    }
                    "v", "t" -> if (inCell) inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        inCell = false
                        val raw = value.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            else -> raw
                        }
                        while (row.size < col) row += ""
                        if (col < 512) {
                            if (row.size == col) row += clean(text) else row[col] = clean(text)
                        }
                    }
                    "row" -> {
                        if (row.any { it.isNotEmpty() }) rows += row
                        if (rows.size > MAX_ROWS) throw BillFormatException("行数太多了（上限 ${MAX_ROWS} 行）")
                    }
                }
            }
        }
        return TextTable(rows)
    }

    /** "C12" → 2。 */
    fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1) else break
        }
        return (n - 1).coerceAtLeast(0)
    }
}
