package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssCodecTest {
    private val sample = """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1920
        PlayResY: 1080

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Noto Sans,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,3,2,2,30,30,30,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello, world
        Dialogue: 2,0:00:01.50,0:00:02.50,Default,Speaker B,0,0,0,,Overlap

        [Aegisub Project Garbage]
        Last Style Storage: Default
    """.trimIndent()

    @Test
    fun parsesOverlappingEventsAndUnknownSections() {
        val doc = AssCodec.parse(sample)
        assertEquals(2, doc.events.size)
        assertEquals("Hello, world", doc.events[0].text)
        assertEquals(2, doc.events[1].layer)
        assertEquals(2, doc.activeEvents(SubTime(1_750)).size)
        assertEquals("Aegisub Project Garbage", doc.unknownSections.single().name)
    }

    @Test
    fun writesReadableAssAndPreservesUnknownSection() {
        val doc = AssCodec.parse(sample)
        val output = AssCodec.write(doc)
        assertTrue(output.contains("[Aegisub Project Garbage]"))
        assertTrue(output.contains("Hello, world"))
        val parsedAgain = AssCodec.parse(output)
        assertEquals(doc.events, parsedAgain.events)
    }

    @Test
    fun preservesCommentsAndUnknownLinesInsideKnownSections() {
        val source = """
            [Script Info]
            ; script comment
            ScriptType: v4.00+
            CustomNoColonLine

            [V4+ Styles]
            ; style comment
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1
            Tool-Metadata: keep-me

            [Events]
            ; event comment
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello
            Command: opaque payload
        """.trimIndent()

        val once = AssCodec.write(AssCodec.parse(source))
        val twice = AssCodec.write(AssCodec.parse(once))
        listOf(
            "; script comment",
            "CustomNoColonLine",
            "; style comment",
            "Tool-Metadata: keep-me",
            "; event comment",
            "Command: opaque payload",
        ).forEach { marker ->
            assertTrue(once.contains(marker), marker)
            assertTrue(twice.contains(marker), marker)
        }
    }

    @Test
    fun preservesCustomFormatColumnsAndValues() {
        val source = """
            [Script Info]
            ScriptType: v4.00+

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding, VendorMeta
            Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1,style-extra

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, VendorID, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,abc-123,Hello
        """.trimIndent()

        val parsed = AssCodec.parse(source)
        assertEquals("style-extra", parsed.styles.single().extraFields["vendormeta"])
        assertEquals("abc-123", parsed.events.single().extraFields["vendorid"])
        val output = AssCodec.write(parsed.copy(events = parsed.events.map { it.copy(text = "Edited") }))
        assertTrue(output.contains("VendorMeta"))
        assertTrue(output.contains("style-extra"))
        assertTrue(output.contains("VendorID"))
        assertTrue(output.contains("abc-123"))
        assertTrue(output.contains(",Edited"))
        val again = AssCodec.parse(output)
        assertEquals("abc-123", again.events.single().extraFields["vendorid"])
    }

    @Test
    fun preservesCommaTextWhenCustomColumnsFollowText() {
        val source = """
            [Script Info]
            ScriptType: v4.00+

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text, VendorID
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\clip(0,0,320,180)}Hello, world,vendor-42
        """.trimIndent()

        val parsed = AssCodec.parse(source)
        val event = parsed.events.single()
        assertEquals("{\\clip(0,0,320,180)}Hello, world", event.text)
        assertEquals("vendor-42", event.extraFields["vendorid"])

        val output = AssCodec.write(parsed)
        val again = AssCodec.parse(output)
        assertEquals(event.text, again.events.single().text)
        assertEquals("vendor-42", again.events.single().extraFields["vendorid"])
    }

    @Test
    fun undoHistoryIsBoundedAndRedoable() {
        val history = UndoHistory(1, limit = 3)
        history.commit(2)
        history.commit(3)
        history.commit(4)
        assertEquals(3, history.undo())
        assertEquals(2, history.undo())
        assertEquals(1, history.undo())
        assertEquals(1, history.undo())
        assertEquals(2, history.redo())
    }
}
