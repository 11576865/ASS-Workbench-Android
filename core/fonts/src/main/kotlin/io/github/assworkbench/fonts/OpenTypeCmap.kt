package io.github.assworkbench.fonts

import java.nio.ByteBuffer
import java.nio.ByteOrder

object OpenTypeCmap {
    private const val CMAP_TAG = 0x636D6170

    fun supportsCodePoint(bytes: ByteArray, codePoint: Int): Boolean {
        if (codePoint < 0 || codePoint > 0x10FFFF) return false
        val table = findTable(bytes, CMAP_TAG) ?: return false
        val base = table.first
        val length = table.second
        if (length < 4 || base < 0 || base + length > bytes.size) return false

        val numTables = u16(bytes, base + 2)
        if (base + 4 + numTables * 8 > base + length) return false

        val candidates = mutableListOf<Pair<Int, Int>>()
        repeat(numTables) { index ->
            val rec = base + 4 + index * 8
            val platform = u16(bytes, rec)
            val encoding = u16(bytes, rec + 2)
            val subOffset = u32(bytes, rec + 4).toInt()
            val sub = base + subOffset
            if (sub < base || sub + 2 > base + length) return@repeat
            val format = u16(bytes, sub)
            val unicodeScore = when {
                platform == 0 -> 100
                platform == 3 && encoding == 10 -> 90
                platform == 3 && encoding == 1 -> 80
                else -> 0
            }
            if (unicodeScore > 0 && format in setOf(4, 12)) {
                candidates += (unicodeScore + if (format == 12) 10 else 0) to sub
            }
        }

        return candidates.sortedByDescending { it.first }.any { (_, sub) ->
            when (u16(bytes, sub)) {
                12 -> supportsFormat12(bytes, sub, base + length, codePoint)
                4 -> supportsFormat4(bytes, sub, base + length, codePoint)
                else -> false
            }
        }
    }

    fun missingCodePoints(
        bytes: ByteArray,
        codePoints: Collection<Int>,
        limit: Int = 24,
    ): List<Int> {
        if (limit <= 0) return emptyList()
        val out = ArrayList<Int>()
        for (cp in codePoints) {
            if (!supportsCodePoint(bytes, cp)) {
                out += cp
                if (out.size >= limit) break
            }
        }
        return out
    }

    private fun supportsFormat12(bytes: ByteArray, sub: Int, tableEnd: Int, codePoint: Int): Boolean {
        if (sub + 16 > tableEnd) return false
        val length = u32(bytes, sub + 4).toInt()
        val end = (sub + length).coerceAtMost(tableEnd)
        if (length < 16 || end > bytes.size) return false
        val groups = u32(bytes, sub + 12).toInt()
        if (sub + 16L + groups.toLong() * 12L > end.toLong()) return false

        var lo = 0
        var hi = groups - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val off = sub + 16 + mid * 12
            val start = u32(bytes, off).toLong()
            val finish = u32(bytes, off + 4).toLong()
            val cp = codePoint.toLong()
            when {
                cp < start -> hi = mid - 1
                cp > finish -> lo = mid + 1
                else -> {
                    val startGlyph = u32(bytes, off + 8).toLong()
                    return startGlyph + (cp - start) != 0L
                }
            }
        }
        return false
    }

    private fun supportsFormat4(bytes: ByteArray, sub: Int, tableEnd: Int, codePoint: Int): Boolean {
        if (codePoint > 0xFFFF || sub + 14 > tableEnd) return false
        val length = u16(bytes, sub + 2)
        val end = (sub + length).coerceAtMost(tableEnd)
        if (length < 16 || end > bytes.size) return false
        val segCount = u16(bytes, sub + 6) / 2
        if (segCount <= 0) return false

        val endCode = sub + 14
        val startCode = endCode + segCount * 2 + 2
        val idDelta = startCode + segCount * 2
        val idRangeOffset = idDelta + segCount * 2
        if (idRangeOffset + segCount * 2 > end) return false

        for (i in 0 until segCount) {
            val finish = u16(bytes, endCode + i * 2)
            val start = u16(bytes, startCode + i * 2)
            if (codePoint < start || codePoint > finish) continue
            val delta = u16(bytes, idDelta + i * 2)
            val rangeOffset = u16(bytes, idRangeOffset + i * 2)
            if (rangeOffset == 0) {
                return ((codePoint + delta) and 0xFFFF) != 0
            }
            val roAddress = idRangeOffset + i * 2
            val glyphAddress = roAddress + rangeOffset + (codePoint - start) * 2
            if (glyphAddress + 2 > end) return false
            val glyph = u16(bytes, glyphAddress)
            if (glyph == 0) return false
            return ((glyph + delta) and 0xFFFF) != 0
        }
        return false
    }

    private fun findTable(bytes: ByteArray, tag: Int): Pair<Int, Int>? {
        if (bytes.size < 12) return null
        val numTables = u16(bytes, 4)
        if (12 + numTables * 16 > bytes.size) return null
        repeat(numTables) { index ->
            val off = 12 + index * 16
            if (i32(bytes, off) == tag) {
                val tableOffset = u32(bytes, off + 8).toInt()
                val tableLength = u32(bytes, off + 12).toInt()
                if (tableOffset >= 0 && tableLength > 0 && tableOffset.toLong() + tableLength <= bytes.size.toLong()) {
                    return tableOffset to tableLength
                }
            }
        }
        return null
    }

    private fun u16(bytes: ByteArray, off: Int): Int {
        if (off < 0 || off + 2 > bytes.size) return 0
        return ((bytes[off].toInt() and 0xFF) shl 8) or (bytes[off + 1].toInt() and 0xFF)
    }

    private fun u32(bytes: ByteArray, off: Int): Long {
        if (off < 0 || off + 4 > bytes.size) return 0
        return ((bytes[off].toLong() and 0xFF) shl 24) or
            ((bytes[off + 1].toLong() and 0xFF) shl 16) or
            ((bytes[off + 2].toLong() and 0xFF) shl 8) or
            (bytes[off + 3].toLong() and 0xFF)
    }

    private fun i32(bytes: ByteArray, off: Int): Int {
        if (off < 0 || off + 4 > bytes.size) return 0
        return ByteBuffer.wrap(bytes, off, 4).order(ByteOrder.BIG_ENDIAN).int
    }
}
