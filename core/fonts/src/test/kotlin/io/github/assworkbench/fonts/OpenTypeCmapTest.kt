package io.github.assworkbench.fonts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenTypeCmapTest {
    @Test
    fun readsFormat12UnicodeGroups() {
        val font = minimalFormat12Font(
            listOf(
                Triple(0x0041, 0x005A, 1),
                Triple(0x4E00, 0x9FFF, 100),
            )
        )
        assertTrue(OpenTypeCmap.supportsCodePoint(font, 'A'.code))
        assertTrue(OpenTypeCmap.supportsCodePoint(font, '中'.code))
        assertFalse(OpenTypeCmap.supportsCodePoint(font, 0x1F600))
    }

    private fun minimalFormat12Font(groups: List<Triple<Int, Int, Int>>): ByteArray {
        val subLength = 16 + groups.size * 12
        val cmapLength = 12 + subLength
        val total = 28 + cmapLength
        val b = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)

        b.putInt(0x00010000)
        b.putShort(1)
        b.putShort(0)
        b.putShort(0)
        b.putShort(0)

        b.putInt(0x636D6170)
        b.putInt(0)
        b.putInt(28)
        b.putInt(cmapLength)

        b.position(28)
        b.putShort(0)
        b.putShort(1)
        b.putShort(3)
        b.putShort(10)
        b.putInt(12)

        b.putShort(12)
        b.putShort(0)
        b.putInt(subLength)
        b.putInt(0)
        b.putInt(groups.size)
        groups.forEach { (start, end, glyph) ->
            b.putInt(start)
            b.putInt(end)
            b.putInt(glyph)
        }
        return b.array()
    }
}
