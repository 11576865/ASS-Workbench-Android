package io.github.assworkbench.fonts

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenTypeNameReaderTest {
    @Test
    fun keepsTypographicFamilyForDisplayButLegacyFamilyForRenderer() {
        val bytes = fakeSfnt(
            1 to "Legacy Family",
            4 to "Legacy Family Regular",
            6 to "LegacyFamily-Regular",
            16 to "Typographic Family",
        )

        val metadata = OpenTypeNameReader.read(bytes)

        assertEquals("Typographic Family", metadata.family)
        assertEquals("Typographic Family", metadata.typographicFamily)
        assertEquals("Legacy Family", metadata.legacyFamily)
        assertEquals("Legacy Family", metadata.rendererFamily)
        assertEquals("Legacy Family Regular", metadata.fullName)
        assertEquals("LegacyFamily-Regular", metadata.postScriptName)
    }

    @Test
    fun detectsSingleFaceSfntSignaturesAndRejectsCollections() {
        assertEquals("ttf", OpenTypeNameReader.singleFaceExtension(byteArrayOf(0x00, 0x01, 0x00, 0x00)))
        assertEquals("ttf", OpenTypeNameReader.singleFaceExtension("true".toByteArray(Charsets.US_ASCII)))
        assertEquals("otf", OpenTypeNameReader.singleFaceExtension("OTTO".toByteArray(Charsets.US_ASCII)))
        assertEquals(null, OpenTypeNameReader.singleFaceExtension("ttcf".toByteArray(Charsets.US_ASCII)))
        assertEquals(null, OpenTypeNameReader.singleFaceExtension(byteArrayOf(1, 2, 3)))
    }

    private fun fakeSfnt(vararg names: Pair<Int, String>): ByteArray {
        val encoded = names.map { (id, value) -> id to value.toByteArray(Charsets.UTF_16BE) }
        val nameHeaderSize = 6
        val recordsSize = encoded.size * 12
        val stringsSize = encoded.sumOf { it.second.size }
        val nameTableSize = nameHeaderSize + recordsSize + stringsSize
        val tableOffset = 12 + 16
        val out = ByteBuffer.allocate(tableOffset + nameTableSize).order(ByteOrder.BIG_ENDIAN)

        out.putInt(0x00010000)
        out.putShort(1)
        out.putShort(0)
        out.putShort(0)
        out.putShort(0)

        out.putInt(0x6E616D65)
        out.putInt(0)
        out.putInt(tableOffset)
        out.putInt(nameTableSize)

        out.position(tableOffset)
        out.putShort(0)
        out.putShort(encoded.size.toShort())
        out.putShort((nameHeaderSize + recordsSize).toShort())

        var stringOffset = 0
        encoded.forEach { (id, data) ->
            out.putShort(3)
            out.putShort(1)
            out.putShort(0x0409)
            out.putShort(id.toShort())
            out.putShort(data.size.toShort())
            out.putShort(stringOffset.toShort())
            stringOffset += data.size
        }
        encoded.forEach { (_, data) -> out.put(data) }
        return out.array()
    }
}
