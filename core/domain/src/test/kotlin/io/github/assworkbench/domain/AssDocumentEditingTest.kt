package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssDocumentEditingTest {
    private fun doc(): AssDocument = AssDocument(
        styles = listOf(
            AssStyle(name = "Default", fontName = "Arial", fontSize = 48.0),
            AssStyle(name = "Alt", fontName = "Noto Sans", fontSize = 56.0),
        ),
        events = listOf(
            AssEvent(
                id = 1,
                start = SubTime(1_000),
                end = SubTime(4_000),
                style = "Default",
                marginV = 62,
                text = "{\\fs56}{\\bord6}你好世界",
            ),
            AssEvent(
                id = 2,
                start = SubTime(5_000),
                end = SubTime(7_000),
                style = "Alt",
                text = "第二句",
            ),
        ),
    )

    @Test
    fun insertAtPlaybackCopiesContextButNotText() {
        val result = AssDocumentEditing.insertAtPlayback(doc(), 1, 3_000)
        val inserted = result.document.events.first { it.id == result.focusedEventId }
        assertEquals(3_000, inserted.start.millis)
        assertEquals(5_000, inserted.end.millis)
        assertEquals("Default", inserted.style)
        assertEquals("", inserted.text)
    }

    @Test
    fun insertAdjacentCopiesEventContextAndUsesNeighborTiming() {
        val before = AssDocumentEditing.insertAdjacent(doc(), 2, before = true)
        val beforeEvent = before.document.events.first { it.id == before.focusedEventId }
        assertEquals(3_000, beforeEvent.start.millis)
        assertEquals(5_000, beforeEvent.end.millis)
        assertEquals("Alt", beforeEvent.style)
        assertEquals("", beforeEvent.text)
        assertEquals(listOf(1L, beforeEvent.id, 2L), before.document.events.map { it.id })

        val after = AssDocumentEditing.insertAdjacent(doc(), 1, before = false)
        val afterEvent = after.document.events.first { it.id == after.focusedEventId }
        assertEquals(4_000, afterEvent.start.millis)
        assertEquals(6_000, afterEvent.end.millis)
        assertEquals("Default", afterEvent.style)
        assertEquals(listOf(1L, afterEvent.id, 2L), after.document.events.map { it.id })
    }
    @Test
    fun insertBeforeTimeZeroNeverCreatesZeroDurationEvent() {
        val source = doc().copy(events = listOf(doc().events.first().copy(start = SubTime(0), end = SubTime(1_000))))
        val result = AssDocumentEditing.insertAdjacent(source, 1, before = true)
        val inserted = result.document.events.first { it.id == result.focusedEventId }
        assertEquals(0, inserted.start.millis)
        assertEquals(2_000, inserted.end.millis)
        assertTrue(inserted.end.millis > inserted.start.millis)
    }

    @Test
    fun duplicatePreservesExactEventAndOnlyChangesId() {
        val source = doc().events.first()
        val result = AssDocumentEditing.duplicateEvent(doc(), source.id)
        val duplicate = result.document.events[1]
        assertEquals(source.copy(id = duplicate.id), duplicate)
        assertTrue(duplicate.id != source.id)
    }

    @Test
    fun mergeAdjacentUsesDocumentNeighborOrder() {
        val withPrevious = AssDocumentEditing.mergeAdjacent(doc(), 2, previous = true)
        assertEquals(1, withPrevious.document.events.size)
        assertEquals("{\\fs56}{\\bord6}你好世界\\N第二句", withPrevious.document.events.single().text)

        val withNext = AssDocumentEditing.mergeAdjacent(doc(), 1, previous = false)
        assertEquals(1, withNext.document.events.size)
        assertEquals("{\\fs56}{\\bord6}你好世界\\N第二句", withNext.document.events.single().text)
    }
    @Test
    fun splitUsesCursorAndPlaybackTimeAndCarriesLeadingOverrides() {
        val source = doc()
        val splitIndex = source.events.first().text.indexOf("世界")
        val result = AssDocumentEditing.splitEvent(source, 1, 2_500, splitIndex)
        val first = result.document.events[0]
        val second = result.document.events[1]
        assertEquals(2_500, first.end.millis)
        assertEquals(2_500, second.start.millis)
        assertEquals("{\\fs56}{\\bord6}你好", first.text)
        assertTrue(second.text.startsWith("{\\fs56}{\\bord6}"))
        assertTrue(second.text.endsWith("世界"))
    }
    @Test
    fun splitAndMergePreserveVisibleBoundaryWhitespace() {
        val source = doc().copy(events = listOf(
            doc().events[0].copy(id = 1, start = SubTime(1_000), end = SubTime(4_000), text = "left  right"),
            doc().events[1].copy(id = 2, start = SubTime(5_000), end = SubTime(7_000), text = " tail "),
        ))
        val split = AssDocumentEditing.splitEvent(source, 1, 2_500, 6)
        assertEquals("left  ", split.document.events[0].text)
        assertEquals("right", split.document.events[1].text)

        val merged = AssDocumentEditing.mergeEvents(source, setOf(1, 2), "\\N")
        assertEquals("left  right\\N tail ", merged.document.events.single().text)
    }

    @Test
    fun splitMergeUndoRedoRestoresExactSnapshots() {
        val original = doc().copy(
            events = listOf(
                doc().events[0].copy(
                    id = 1,
                    start = SubTime(1_000),
                    end = SubTime(4_000),
                    text = "{\\fs56}left  right ",
                ),
            ),
        )
        val history = UndoHistory(original)
        val splitIndex = original.events.single().text.indexOf("right")
        val split = AssDocumentEditing.splitEvent(original, 1, 2_500, splitIndex).document
        history.commit(split)

        val splitIds = split.events.map { it.id }.toSet()
        val merged = AssDocumentEditing.mergeEvents(split, splitIds, "").document
        history.commit(merged)

        assertEquals("{\\fs56}left  right ", merged.events.single().text)
        assertEquals(split, history.undo())
        assertEquals(original, history.undo())
        assertEquals(split, history.redo())
        assertEquals(merged, history.redo())
    }

    @Test
    fun mergeRejectsNonContiguousSelection() {
        val source = doc().copy(events = listOf(
            doc().events[0].copy(id = 1),
            doc().events[0].copy(id = 2, text = "middle"),
            doc().events[1].copy(id = 3),
        ))
        val error = runCatching { AssDocumentEditing.mergeEvents(source, setOf(1, 3), "\\N") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("连续") == true)
    }
    @Test
    fun mergeKeepsFirstEventFormattingAndJoinsTexts() {
        val result = AssDocumentEditing.mergeEvents(doc(), setOf(1, 2), "\\N")
        val merged = result.document.events.single()
        assertEquals(1_000, merged.start.millis)
        assertEquals(7_000, merged.end.millis)
        assertEquals("Default", merged.style)
        assertEquals(62, merged.marginV)
        assertEquals("{\\fs56}{\\bord6}你好世界\\N第二句", merged.text)
    }

    @Test
    fun copyFormattingPreservesTargetTimingAndDialogue() {
        val result = AssDocumentEditing.copyEventFormatting(doc(), 1, setOf(2))
        val target = result.events.first { it.id == 2L }
        assertEquals(5_000, target.start.millis)
        assertEquals(7_000, target.end.millis)
        assertEquals("Default", target.style)
        assertEquals(62, target.marginV)
        assertEquals("{\\fs56}{\\bord6}第二句", target.text)
    }
    @Test
    fun copyFormattingPreservesTargetCommentsAndDoesNotCopySourceComments() {
        val source = doc().copy(events = listOf(
            doc().events[0].copy(id = 1, text = "{source note}{\\fs56}Hello"),
            doc().events[1].copy(id = 2, text = "{target note}{\\bord2}Second"),
        ))
        val result = AssDocumentEditing.copyEventFormatting(source, 1, setOf(2))
        assertEquals("{\\fs56}{target note}Second", result.events.first { it.id == 2L }.text)
    }

    @Test
    fun splitCarriesLeadingOverridesButNotLeadingCommentsToSecondEvent() {
        val source = doc().copy(events = listOf(
            doc().events[0].copy(id = 1, text = "{source note}{\\fs56}HelloWorld"),
        ))
        val splitIndex = source.events.single().text.indexOf("World")
        val result = AssDocumentEditing.splitEvent(source, 1, 2_500, splitIndex)
        assertEquals("{source note}{\\fs56}Hello", result.document.events[0].text)
        assertEquals("{\\fs56}World", result.document.events[1].text)
    }

    @Test
    fun renameStyleUpdatesAllEventReferences() {
        val result = AssDocumentEditing.renameStyle(doc(), "Default", "Dialogue")
        assertTrue(result.styles.any { it.name == "Dialogue" })
        assertEquals("Dialogue", result.events.first { it.id == 1L }.style)
    }

    @Test
    fun deleteStyleRedirectsReferences() {
        val result = AssDocumentEditing.deleteStyle(doc(), "Alt", "Default")
        assertEquals(listOf("Default"), result.styles.map { it.name })
        assertEquals("Default", result.events.first { it.id == 2L }.style)
    }
}
