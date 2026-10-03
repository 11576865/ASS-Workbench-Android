package io.github.assworkbench.app.ui.preview

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.SubTime
import org.junit.Assert.*
import org.junit.Test

class GeometryParameterDisplayTest {
    private val a = AssEvent(id = 1L, start = SubTime(0), end = SubTime(2000), text = "{\\pos(100,200)}A")
    private val b = AssEvent(id = 2L, start = SubTime(0), end = SubTime(2000), text = "{\\pos(300,400)}B")
    private val document = AssDocument(events = listOf(a, b))
    private val changed = a.copy(text = "{\\pos(125,240)}A")
    private val preview = document.copy(events = listOf(changed, b))

    @Test fun activeGeometryDisplaysTransientValuesWithoutChangingDocument() {
        assertEquals(changed, GeometryParameterDisplay.event(document, preview, "geometry:1", 1L))
        assertEquals(a, document.events.first())
    }
    @Test fun otherOwnersCannotReplaceTheParameterDisplay() {
        for (owner in listOf(null, "geometry:2", "text:1")) {
            assertEquals(a, GeometryParameterDisplay.event(document, preview, owner, 1L))
        }
    }
    @Test fun fixedTargetDoesNotReadAnotherEventsPreview() {
        assertEquals(b, GeometryParameterDisplay.event(document, preview, "geometry:1", 2L))
    }
    @Test fun deletedTargetCannotBeResurrectedByAStalePreview() {
        assertNull(GeometryParameterDisplay.event(document.copy(events = listOf(b)), preview, "geometry:1", 1L))
    }
    @Test fun cancelOrMissingPreviewFallsBackToCommittedValues() {
        assertEquals(a, GeometryParameterDisplay.event(document, null, "geometry:1", 1L))
        assertEquals(a, GeometryParameterDisplay.event(document, document.copy(events = listOf(b)), "geometry:1", 1L))
    }
}
