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
