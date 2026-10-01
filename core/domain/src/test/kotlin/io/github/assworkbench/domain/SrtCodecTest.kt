package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SrtCodecTest {
    @Test
    fun imports_multiline_srt_into_ass_document() {
        val srt = """
            1
            00:00:01,250 --> 00:00:03,500
            Hello
            <i>world</i>

            2
            00:00:04.000 --> 00:00:05.100
            Second
        """.trimIndent()

        val doc = SrtCodec.parse(srt)
        assertEquals(2, doc.events.size)
        assertEquals(1_250L, doc.events[0].start.millis)
        assertEquals(3_500L, doc.events[0].end.millis)
        assertEquals("""Hello\N{\i1}world{\i0}""", doc.events[0].text)
    }

    @Test
    fun explicit_srt_export_is_plain_and_numbered() {
        val doc = AssDocument(events = listOf(
            AssEvent(7, start = SubTime(1000), end = SubTime(2500), text = "{\\b1}Hello{\\b0}\\NWorld"),
        ))
        val out = SrtCodec.write(doc)
        assertTrue(out.contains("00:00:01,000 --> 00:00:02,500"))
        assertTrue(out.contains("Hello\nWorld"))
        assertTrue(!out.contains("\\b1"))
    }
}
