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
 * 其余按 CSV 读，自动识别 UTF-8 / UTF-16 / GBK（GB18030）编码。全部在本机内存里完成。
 */
object TableReader {
    const val MAX_BYTES = 30 * 1024 * 1024
    const val MAX_ROWS = 100_000
    private const val MAX_ENTRY_BYTES = 80L * 1024 * 1024

    fun read(bytes: ByteArray, fileName: String = ""): TextTable {
        if (bytes.size > MAX_BYTES) throw BillFormatException("文件太大了（上限 30 MB）")
        val zip = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        return if (zip || fileName.lowercase().endsWith(".xlsx")) readXlsx(bytes) else readCsv(decode(bytes))
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
