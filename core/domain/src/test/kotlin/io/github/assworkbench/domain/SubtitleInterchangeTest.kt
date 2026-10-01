package io.github.assworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleInterchangeTest {
    @Test
    fun parsesSubRipIntoCanonicalAssDocument() {
        val input = """
            1
            00:00:01,250 --> 00:00:03,500
            Hello
            world

            2
            00:01:00,000 --> 00:01:02,010
            Second cue
        """.trimIndent()

        val doc = SubRipCodec.parse(input)
        assertEquals(2, doc.events.size)
        assertEquals(1_250L, doc.events[0].start.millis)
        assertEquals(3_500L, doc.events[0].end.millis)
        assertEquals("Hello\\Nworld", doc.events[0].text)
        assertEquals(60_000L, doc.events[1].start.millis)
    }

    @Test
    fun writesSubRipAsPlainInterchangeText() {
        val doc = AssDocument(events = listOf(
            AssEvent(1, start = SubTime(0), end = SubTime(1_234), text = "{\\b1}Hello\\Nworld"),
        ))
        val output = SubRipCodec.write(doc)
        assertTrue("00:00:00,000 --> 00:00:01,234" in output)
        assertTrue("Hello\nworld" in output)
    }

    @Test
    fun parsesAndWritesWebVtt() {
        val input = """
            WEBVTT

            cue-1
            00:00:02.000 --> 00:00:04.500 align:start
            Line one
            Line two
        """.trimIndent()
        val doc = WebVttCodec.parse(input)
        assertEquals(1, doc.events.size)
        assertEquals(2_000L, doc.events.single().start.millis)
        assertEquals("Line one\\NLine two", doc.events.single().text)
        assertTrue(WebVttCodec.write(doc).startsWith("WEBVTT"))
    }
}
