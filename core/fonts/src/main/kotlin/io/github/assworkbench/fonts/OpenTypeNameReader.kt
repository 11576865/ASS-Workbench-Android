package io.github.assworkbench.fonts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.security.MessageDigest

object OpenTypeNameReader {
    private const val NAME_TABLE = 0x6E616D65

    fun read(bytes: ByteArray): FontMetadata {
        require(bytes.size >= 12) { "Font file is too small" }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        b.int
        val numTables = b.short.toInt() and 0xFFFF
        require(bytes.size >= 12 + numTables * 16) { "Invalid sfnt table directory" }

        var nameOffset = -1
        var nameLength = -1
        repeat(numTables) {
            val tag = b.int
            b.int
            val offset = b.int
            val length = b.int
            if (tag == NAME_TABLE) {
                nameOffset = offset
                nameLength = length
            }
        }
        require(nameOffset >= 0 && nameLength > 0 && nameOffset + nameLength <= bytes.size) { "Font has no readable name table" }

        val n = ByteBuffer.wrap(bytes, nameOffset, nameLength).slice().order(ByteOrder.BIG_ENDIAN)
        val format = n.short.toInt() and 0xFFFF
        require(format in 0..1) { "Unsupported OpenType name table format: $format" }
        val count = n.short.toInt() and 0xFFFF
        val stringOffset = n.short.toInt() and 0xFFFF
        require(6 + count * 12 <= nameLength) { "Invalid name record table" }

        data class Record(val platform: Int, val encoding: Int, val language: Int, val nameId: Int, val value: String)
        val records = mutableListOf<Record>()
        repeat(count) {
            val platform = n.short.toInt() and 0xFFFF
            val encoding = n.short.toInt() and 0xFFFF
            val language = n.short.toInt() and 0xFFFF
            val nameId = n.short.toInt() and 0xFFFF
            val length = n.short.toInt() and 0xFFFF
            val offset = n.short.toInt() and 0xFFFF
            val start = nameOffset + stringOffset + offset
            if (length <= 0 || start < 0 || start + length > nameOffset + nameLength || start + length > bytes.size) return@repeat
            val raw = bytes.copyOfRange(start, start + length)
            val value = decode(platform, encoding, raw).trim().trim('\u0000')
            if (value.isNotBlank()) records += Record(platform, encoding, language, nameId, value)
        }

        fun best(vararg ids: Int): String? {
            for (id in ids) {
                val candidates = records.filter { it.nameId == id }
                val picked = candidates.maxByOrNull { r ->
                    var score = 0
                    if (r.platform == 3) score += 30
                    if (r.platform == 0) score += 25
                    if (r.language == 0x0409 || r.language == 0) score += 10
                    if (r.encoding == 1 || r.encoding == 10) score += 2
                    score
                }
                if (picked != null) return picked.value
            }
            return null
        }

        val family = best(16, 1) ?: best(4, 6) ?: error("Font name table has no family name")
        val aliases = records
            .filter { it.nameId in setOf(1, 4, 6, 16) }
            .map { it.value }
            .filter { it.isNotBlank() }
            .toSet()

        return FontMetadata(
            family = family,
            subfamily = best(17, 2),
            fullName = best(4),
            postScriptName = best(6),
            aliases = aliases,
        )
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun decode(platform: Int, encoding: Int, bytes: ByteArray): String = when (platform) {
        0, 3 -> runCatching { bytes.toString(Charsets.UTF_16BE) }.getOrDefault("")
        1 -> runCatching { bytes.toString(Charset.forName("x-MacRoman")) }
            .getOrElse { bytes.toString(Charsets.ISO_8859_1) }
        else -> if (encoding == 1 || encoding == 10) bytes.toString(Charsets.UTF_16BE) else bytes.toString(Charsets.ISO_8859_1)
    }
}
