package io.github.assworkbench.fonts

object OpenTypeCollection {
    private const val TTCF = 0x74746366

    fun faceOffsets(bytes: ByteArray): List<Int> {
        if (bytes.size < 12 || i32(bytes, 0) != TTCF) return emptyList()
        val count = i32(bytes, 8)
        if (count <= 0 || count > 4096 || 12L + count.toLong() * 4L > bytes.size) return emptyList()
        return (0 until count).mapNotNull { i ->
            val offset = i32(bytes, 12 + i * 4)
            offset.takeIf { it >= 0 && it + 12 <= bytes.size }
        }
    }

    fun isCollection(bytes: ByteArray): Boolean = faceOffsets(bytes).isNotEmpty()

    fun extension(bytes: ByteArray, declared: String? = null): String? {
        if (!isCollection(bytes)) return null
        return when (declared?.lowercase()) {
            "otc" -> "otc"
            else -> "ttc"
        }
    }

    private fun i32(bytes: ByteArray, off: Int): Int {
        if (off < 0 || off + 4 > bytes.size) return -1
        return ((bytes[off].toInt() and 0xff) shl 24) or
            ((bytes[off + 1].toInt() and 0xff) shl 16) or
            ((bytes[off + 2].toInt() and 0xff) shl 8) or
            (bytes[off + 3].toInt() and 0xff)
    }
}
