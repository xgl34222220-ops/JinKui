package com.xiguli.jinkui.data.importer

import java.math.BigDecimal

/**
 * 旧版 Excel（.xls，BIFF8）读取器，不依赖任何库，全部在本机内存里完成。
 *
 * 文件结构：外层是 OLE2 复合文档（CFB：512 字节文件头 + 按 FAT 串起来的扇区 + 目录项 + 迷你流），
 * 里面名为 “Workbook”（或更老的 “Book”）的流就是 BIFF8 记录序列。这里只读第一张工作表：
 *  - 全局区：BOUNDSHEET（各表的位置与名字）、SST（共享字符串表，可能被 CONTINUE 拆成多段，
 *    拆分处的下一段开头会重新给出“是否双字节”的标志位）；
 *  - 工作表：LABELSST、LABEL、NUMBER、RK、MULRK、FORMULA（取缓存的结果，字符串结果在随后的 STRING 记录里）、BOOLERR。
 * 字符串有压缩（每字 1 字节，Latin-1）与 UTF-16LE 两种，富文本 / 拼音附加信息直接跳过。
 */
object XlsReader {
    /** OLE2 复合文档的文件头签名 D0 CF 11 E0 A1 B1 1A E1。 */
    private val MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    fun isCompoundFile(bytes: ByteArray): Boolean = bytes.size >= 512 && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun read(bytes: ByteArray): TextTable {
        if (!isCompoundFile(bytes)) throw BillFormatException("这不是 Excel 97-2003（.xls）文件")
        val stream = try {
            Cfb(bytes).stream("Workbook") ?: Cfb(bytes).stream("Book")
        } catch (e: BillFormatException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: throw BillFormatException("无法打开这个 .xls 文件：里面没有工作簿（可能是 Word 等其他 Office 文件，或文件已损坏）")
        return try {
            Biff(stream).firstSheet()
        } catch (e: BillFormatException) {
            throw e
        } catch (e: Exception) {
            throw BillFormatException("这个 .xls 文件的内容无法识别（只支持 Excel 97 及以后的格式）")
        }
    }

    // ── 复合文档（CFB） ───────────────────────────────────────────

    private class Cfb(private val b: ByteArray) {
        private val sectorSize = 1 shl u16(b, 0x1E).coerceIn(7, 16)
        private val miniSectorSize = 1 shl u16(b, 0x20).coerceIn(2, 16)
        private val miniCutoff = u32(b, 0x38).toLong()
        private val fat: IntArray
        private val miniFat: IntArray
        private val entries: List<Entry>

        private class Entry(val name: String, val type: Int, val start: Int, val size: Long)

        init {
            // DIFAT：文件头里 109 项，不够时串在 DIFAT 扇区里（每个扇区最后一项指向下一个）
            val fatSectors = ArrayList<Int>()
            val numFat = u32(b, 0x2C)
            for (i in 0 until 109) {
                val s = u32(b, 0x4C + i * 4)
                if (s >= 0 && fatSectors.size < numFat) fatSectors += s
            }
            var difat = u32(b, 0x44)
            var guard = 0
            val perSector = sectorSize / 4
            while (difat >= 0 && fatSectors.size < numFat && guard++ < 10_000) {
                val off = offset(difat)
                for (i in 0 until perSector - 1) {
                    val s = u32(b, off + i * 4)
                    if (s >= 0 && fatSectors.size < numFat) fatSectors += s
                }
                difat = u32(b, off + (perSector - 1) * 4)
            }
            fat = IntArray(fatSectors.size * perSector)
            fatSectors.forEachIndexed { i, s ->
                val off = offset(s)
                for (j in 0 until perSector) fat[i * perSector + j] = if (off + j * 4 + 4 <= b.size) u32(b, off + j * 4) else END
            }
            val miniFatBytes = chain(u32(b, 0x3C), Long.MAX_VALUE)
            miniFat = IntArray(miniFatBytes.size / 4) { u32(miniFatBytes, it * 4) }
            val dir = chain(u32(b, 0x30), Long.MAX_VALUE)
            entries = (0 until dir.size / 128).map { i ->
                val off = i * 128
                val nameLen = u16(dir, off + 64).coerceIn(0, 64)
                val chars = (nameLen / 2 - 1).coerceAtLeast(0)
                val name = String(CharArray(chars) { k -> u16(dir, off + k * 2).toChar() })
                val size = (u32(dir, off + 120).toLong() and 0xFFFFFFFFL)
                Entry(name, dir[off + 66].toInt() and 0xFF, u32(dir, off + 116), size)
            }
        }

        private fun offset(sector: Int): Int {
            val off = (sector.toLong() + 1) * sectorSize
            if (sector < 0 || off + sectorSize > b.size + sectorSize) throw BillFormatException("这个 .xls 文件已损坏")
            return off.toInt()
        }

        /** 沿 FAT 读一条扇区链。 */
        fun chain(start: Int, limit: Long): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var s = start
            var guard = 0
            while (s >= 0 && out.size() < limit && guard++ < 1_000_000) {
                val off = offset(s)
                val n = minOf(sectorSize, b.size - off)
                if (n <= 0) break
                out.write(b, off, n)
                s = if (s < fat.size) fat[s] else END
            }
            val bytes = out.toByteArray()
            return if (limit < bytes.size) bytes.copyOf(limit.toInt()) else bytes
        }

        fun stream(name: String): ByteArray? {
            val entry = entries.firstOrNull { it.type == 2 && it.name.equals(name, ignoreCase = true) } ?: return null
            if (entry.size > TableReader.MAX_BYTES * 2L) throw BillFormatException("文件内容过大，无法读取")
            if (entry.size >= miniCutoff) return chain(entry.start, entry.size)
            // 小于 4096 字节的流放在迷你流里：根目录项的扇区链就是迷你流本体
            val root = entries.firstOrNull { it.type == 5 } ?: return null
            val mini = chain(root.start, root.size)
            val out = java.io.ByteArrayOutputStream()
            var s = entry.start
            var guard = 0
            while (s >= 0 && out.size() < entry.size && guard++ < 1_000_000) {
                val off = s * miniSectorSize
                if (off + miniSectorSize > mini.size) break
                out.write(mini, off, miniSectorSize)
                s = if (s < miniFat.size) miniFat[s] else END
            }
            return out.toByteArray().copyOf(entry.size.toInt())
        }

        companion object {
            const val END = -2
        }
    }

    // ── BIFF8 记录 ────────────────────────────────────────────────

    private class Record(val type: Int, val data: ByteArray)

    private class Biff(private val s: ByteArray) {
        private fun recordAt(pos: Int): Pair<Record, Int>? {
            if (pos + 4 > s.size) return null
            val type = u16(s, pos)
            val len = u16(s, pos + 2)
            val end = minOf(pos + 4 + len, s.size)
            return Record(type, s.copyOfRange(pos + 4, end)) to end
        }

        fun firstSheet(): TextTable {
            // 全局区：直到第一个 EOF
            var pos = 0
            val sheets = ArrayList<Pair<Int, Int>>() // (位置, 类型)
            var sst: List<String> = emptyList()
            val first = recordAt(0) ?: throw BillFormatException("这个 .xls 文件是空的")
            if (first.first.type != BOF) throw BillFormatException("这个 .xls 文件的内容无法识别")
            val version = u16(first.first.data, 0)
            if (version != 0x0600) throw BillFormatException("这是 Excel 95 或更早的 .xls，请在 Excel / WPS 里另存为 .xlsx 后再导入")
            while (true) {
                val (rec, next) = recordAt(pos) ?: break
                when (rec.type) {
                    BOUNDSHEET -> sheets += (u32(rec.data, 0) to (rec.data.getOrNull(5)?.toInt() ?: 0))
                    SST -> {
                        val parts = arrayListOf(rec.data)
                        var p = next
                        while (true) {
                            val (cont, n2) = recordAt(p) ?: break
                            if (cont.type != CONTINUE) break
                            parts += cont.data
                            p = n2
                        }
                        sst = readSst(parts)
                    }
                    EOF -> { pos = next; break }
                }
                pos = next
            }
            val sheetPos = sheets.firstOrNull { it.second == 0 }?.first ?: sheets.firstOrNull()?.first
                ?: throw BillFormatException("这个 .xls 里没有工作表")
            return readSheet(sheetPos, sst)
        }

        private fun readSheet(start: Int, sst: List<String>): TextTable {
            val cells = HashMap<Int, HashMap<Int, String>>()
            var maxRow = -1
            fun put(row: Int, col: Int, value: String) {
                if (col >= 512 || row >= TableReader.MAX_ROWS + 1) return
                cells.getOrPut(row) { HashMap() }[col] = value
                if (row > maxRow) maxRow = row
            }
            var pos = start
            var pendingFormula: Pair<Int, Int>? = null
            var depth = 0
            while (true) {
                val (rec, next) = recordAt(pos) ?: break
                val d = rec.data
                when (rec.type) {
                    BOF -> depth++
                    EOF -> { depth--; if (depth <= 0) break }
                    LABELSST -> if (d.size >= 10) put(u16(d, 0), u16(d, 2), sst.getOrNull(u32(d, 6)) ?: "")
                    LABEL -> if (d.size >= 9) put(u16(d, 0), u16(d, 2), Cursor(listOf(d), 6).string16())
                    NUMBER -> if (d.size >= 14) put(u16(d, 0), u16(d, 2), number(java.lang.Double.longBitsToDouble(u64(d, 6))))
                    RK -> if (d.size >= 10) put(u16(d, 0), u16(d, 2), number(rk(u32(d, 6))))
                    MULRK -> if (d.size >= 6) {
                        val row = u16(d, 0)
                        val firstCol = u16(d, 2)
                        val count = (d.size - 6) / 6
                        for (i in 0 until count) put(row, firstCol + i, number(rk(u32(d, 4 + i * 6 + 2))))
                    }
                    BOOLERR -> if (d.size >= 8) {
                        val v = d[6].toInt() and 0xFF
                        if (d[7].toInt() == 0) put(u16(d, 0), u16(d, 2), if (v != 0) "TRUE" else "FALSE")
                    }
                    FORMULA -> if (d.size >= 14) {
                        val row = u16(d, 0)
                        val col = u16(d, 2)
                        if ((d[12].toInt() and 0xFF) == 0xFF && (d[13].toInt() and 0xFF) == 0xFF) {
                            when (d[6].toInt() and 0xFF) {
                                0 -> pendingFormula = row to col // 字符串结果在随后的 STRING 记录里
                                1 -> put(row, col, if (d[8].toInt() != 0) "TRUE" else "FALSE")
                                else -> Unit // 错误值 / 空字符串
                            }
                        } else {
                            put(row, col, number(java.lang.Double.longBitsToDouble(u64(d, 6))))
                        }
                    }
                    STRING -> pendingFormula?.let { (row, col) ->
                        put(row, col, Cursor(listOf(d), 0).string16())
                        pendingFormula = null
                    }
                }
                pos = next
            }
            val rows = ArrayList<List<String>>()
            for (r in 0..maxRow) {
                val line = cells[r] ?: continue
                val width = (line.keys.maxOrNull() ?: -1) + 1
                val row = List(width) { c -> clean(line[c].orEmpty()) }
                if (row.any { it.isNotEmpty() }) rows += row
                if (rows.size > TableReader.MAX_ROWS) throw BillFormatException("行数太多了（上限 ${TableReader.MAX_ROWS} 行）")
            }
            return TextTable(rows)
        }

        private fun readSst(parts: List<ByteArray>): List<String> {
            val c = Cursor(parts, 0)
            c.u32() // 总引用数
            val unique = c.u32().coerceIn(0, 2_000_000)
            val out = ArrayList<String>(minOf(unique, 100_000))
            repeat(unique) {
                if (c.atEnd()) return out
                out += c.string16()
            }
            return out
        }
    }

    /**
     * 跨 CONTINUE 读取的游标。字符串的字符部分跨段时，新段第一个字节是新的“是否双字节”标志位。
     */
    private class Cursor(private val parts: List<ByteArray>, start: Int) {
        private var part = 0
        private var pos = start

        fun atEnd(): Boolean {
            skipEmpty()
            return part >= parts.size
        }

        private fun skipEmpty() {
            while (part < parts.size && pos >= parts[part].size) { part++; pos = 0 }
        }

        fun u8(): Int {
            skipEmpty()
            if (part >= parts.size) throw IndexOutOfBoundsException()
            return parts[part][pos++].toInt() and 0xFF
        }

        fun u16(): Int = u8() or (u8() shl 8)
        fun u32(): Int = u16() or (u16() shl 16)

        fun skip(n: Long) {
            var left = n
            while (left > 0) {
                skipEmpty()
                if (part >= parts.size) return
                val take = minOf(left, (parts[part].size - pos).toLong()).toInt()
                pos += take
                left -= take
            }
        }

        /** XLUnicodeRichExtendedString：cch(2) + 标志(1) + [富文本段数(2)] + [扩展长度(4)] + 字符 + 富文本 + 扩展。 */
        fun string16(): String {
            val count = u16()
            var flags = u8()
            val runs = if (flags and 0x08 != 0) u16() else 0
            val ext = if (flags and 0x04 != 0) u32().toLong() and 0xFFFFFFFFL else 0L
            val sb = StringBuilder(count)
            var wide = flags and 0x01 != 0
            var left = count
            while (left > 0) {
                skipEmpty()
                if (part >= parts.size) break
                val available = parts[part].size - pos
                val chunk = if (wide) minOf(left, available / 2) else minOf(left, available)
                if (chunk <= 0) {
                    // 一个字被拆在两段之间（不应出现），或这一段读完了：换到下一段并读新标志位
                    part++; pos = 0
                    if (part >= parts.size) break
                    flags = u8(); wide = flags and 0x01 != 0
                    continue
                }
                val p = parts[part]
                if (wide) {
                    for (i in 0 until chunk) sb.append(((p[pos + i * 2].toInt() and 0xFF) or ((p[pos + i * 2 + 1].toInt() and 0xFF) shl 8)).toChar())
                    pos += chunk * 2
                } else {
                    for (i in 0 until chunk) sb.append((p[pos + i].toInt() and 0xFF).toChar())
                    pos += chunk
                }
                left -= chunk
                if (left > 0 && pos >= p.size) {
                    // 字符跨到 CONTINUE：下一段开头是新的标志位
                    part++; pos = 0
                    if (part >= parts.size) break
                    flags = u8(); wide = flags and 0x01 != 0
                }
            }
            skip(runs * 4L + ext)
            return sb.toString()
        }
    }

    /** RK 压缩数字：最低位表示 ÷100，次低位表示 30 位整数，否则是 double 的高 30 位。 */
    private fun rk(value: Int): Double {
        val div100 = value and 0x01 != 0
        val n = if (value and 0x02 != 0) (value shr 2).toDouble() else java.lang.Double.longBitsToDouble((value.toLong() and 0xFFFFFFFCL) shl 32)
        return if (div100) n / 100.0 else n
    }

    /** 数字格：整数不带小数点，其余用最短的十进制表示（-600.5、0.1）。 */
    private fun number(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return ""
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }
    }

    private fun clean(raw: String): String = raw.trim { it.isWhitespace() || it == '\t' || it == '\u00A0' || it == '\uFEFF' || it == '\u0000' }

    private fun u16(b: ByteArray, off: Int): Int = (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, off: Int): Int = u16(b, off) or (u16(b, off + 2) shl 16)
    private fun u64(b: ByteArray, off: Int): Long = (u32(b, off).toLong() and 0xFFFFFFFFL) or (u32(b, off + 4).toLong() shl 32)

    private const val BOF = 0x0809
    private const val EOF = 0x000A
    private const val BOUNDSHEET = 0x0085
    private const val SST = 0x00FC
    private const val CONTINUE = 0x003C
    private const val LABELSST = 0x00FD
    private const val LABEL = 0x0204
    private const val NUMBER = 0x0203
    private const val RK = 0x027E
    private const val MULRK = 0x00BD
    private const val FORMULA = 0x0006
    private const val STRING = 0x0207
    private const val BOOLERR = 0x0205
}
