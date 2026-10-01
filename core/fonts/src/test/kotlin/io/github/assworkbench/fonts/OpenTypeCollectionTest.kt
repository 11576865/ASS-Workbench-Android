package io.github.assworkbench.fonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTypeCollectionTest {
    @Test
    fun readsTtcFaceDirectory() {
        val bytes = ByteArray(64)
        fun put32(offset: Int, value: Int) {
            bytes[offset] = (value ushr 24).toByte()
            bytes[offset + 1] = (value ushr 16).toByte()
            bytes[offset + 2] = (value ushr 8).toByte()
            bytes[offset + 3] = value.toByte()
        }
        put32(0, 0x74746366)
        put32(4, 0x00010000)
        put32(8, 2)
        put32(12, 24)
        put32(16, 40)

        assertEquals(listOf(24, 40), OpenTypeCollection.faceOffsets(bytes))
        assertTrue(OpenTypeCollection.isCollection(bytes))
        assertEquals("ttc", OpenTypeCollection.extension(bytes, "ttc"))
        assertEquals("otc", OpenTypeCollection.extension(bytes, "otc"))
    }
}
