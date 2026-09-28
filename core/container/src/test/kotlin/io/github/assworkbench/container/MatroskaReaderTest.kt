package io.github.assworkbench.container

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatroskaReaderTest {
    @Test
    fun reconstructsAssAndFontAttachment() {
        val codecPrivate = "[Script Info]\nScriptType: v4.00+\n\n[V4+ Styles]\n" +
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n" +
            "Style: Default,TestFont,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1"

        val trackEntry = master(0xAE,
            uint(0xD7, 1) +
            uint(0x73C5, 7) +
            uint(0x83, 0x11) +
            text(0x536E, "English") +
            text(0x22B59C, "eng") +
            text(0x86, "S_TEXT/ASS") +
            binary(0x63A2, codecPrivate.toByteArray())
        )
        val tracks = master(0x1654AE6B, trackEntry)
        val info = master(0x1549A966, uint(0x2AD7B1, 1_000_000))
        val attachment = master(0x61A7,
            text(0x466E, "TestFont.ttf") +
            text(0x4660, "font/ttf") +
            binary(0x465C, byteArrayOf(1,2,3,4))
        )
        val attachments = master(0x1941A469, attachment)
        val payload = "0,0,Default,,0,0,0,,Hello".toByteArray()
        val block = byteArrayOf(0x81.toByte(), 0x00, 0x00, 0x00) + payload
        val group = master(0xA0, binary(0xA1, block) + uint(0x9B, 1500))
        val cluster = master(0x1F43B675, uint(0xE7, 1000) + group)
        val segment = master(0x18538067, info + tracks + attachments + cluster)

        val result = MatroskaReader().scan(ByteArrayInputStream(segment))
        assertEquals(1, result.subtitleTracks.size)
        assertEquals(1, result.attachments.size)
        assertTrue(result.attachments.single().isSupportedFont)
        val ass = result.subtitleTracks.single().toAss()
        assertTrue(ass.contains("Dialogue: 0,0:00:01.00,0:00:02.50,Default"))
        assertTrue(ass.contains("Hello"))
    }

    @Test
    fun canStreamAttachmentsWithoutRetainingTheirBytes() {
        val codecPrivate = "[Script Info]\nScriptType: v4.00+"
        val trackEntry = master(0xAE,
            uint(0xD7, 1) +
            uint(0x83, 0x11) +
            text(0x86, "S_TEXT/ASS") +
            binary(0x63A2, codecPrivate.toByteArray())
        )
        val tracks = master(0x1654AE6B, trackEntry)
        val attachment = master(0x61A7,
            text(0x466E, "TestFont.ttf") +
            text(0x4660, "font/ttf") +
            binary(0x465C, byteArrayOf(9,8,7,6))
        )
        val segment = master(0x18538067, tracks + master(0x1941A469, attachment))

        val seen = mutableListOf<MatroskaAttachment>()
        val result = MatroskaReader().scan(
            ByteArrayInputStream(segment),
            retainAttachments = false,
            onAttachment = { seen += it },
        )

        assertEquals(0, result.attachments.size)
        assertEquals(1, seen.size)
        assertEquals("TestFont.ttf", seen.single().fileName)
        assertTrue(seen.single().data.contentEquals(byteArrayOf(9,8,7,6)))
    }

    private fun master(id: Long, content: ByteArray) = id(id) + size(content.size.toLong()) + content
    private fun binary(id: Long, content: ByteArray) = master(id, content)
    private fun text(id: Long, value: String) = binary(id, value.toByteArray())
    private fun uint(id: Long, value: Long): ByteArray {
        var bytes = 1
        while (bytes < 8 && value >= (1L shl (bytes * 8))) bytes++
        val data = ByteArray(bytes)
        var v = value
        for (i in bytes - 1 downTo 0) {
            data[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return binary(id, data)
    }

    private fun id(value: Long): ByteArray {
        var bytes = 1
        while (bytes < 4 && value >= (1L shl (bytes * 8))) bytes++
        val out = ByteArray(bytes)
        var v = value
        for (i in bytes - 1 downTo 0) {
            out[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return out
    }

    private fun size(value: Long): ByteArray {
        for (bytes in 1..8) {
            val max = (1L shl (7 * bytes)) - 2
            if (value <= max) {
                val out = ByteArray(bytes)
                var v = value
                for (i in bytes - 1 downTo 0) {
                    out[i] = (v and 0xFF).toByte()
                    v = v ushr 8
                }
                out[0] = (out[0].toInt() or (1 shl (8 - bytes))).toByte()
                return out
            }
        }
        error("size too large")
    }
}
