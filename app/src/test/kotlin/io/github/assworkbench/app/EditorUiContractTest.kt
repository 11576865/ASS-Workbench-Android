package io.github.assworkbench.app

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.SubTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUiContractTest {
    @Test
    fun projectionPreservesStableEditorAndCurrentObjectSemanticsWithoutAliasing() {
        val selected = linkedSetOf(1L, 2L)
        val document = AssDocument(
            scriptInfo = linkedMapOf(
                "ScriptType" to "v4.00+",
                "PlayResX" to "1280",
                "PlayResY" to "720",
            ),
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(1_000),
                    end = SubTime(2_000),
                    text = "one",
                ),
                AssEvent(
                    id = 2L,
                    start = SubTime(2_100),
                    end = SubTime(3_000),
                    layer = 3,
                    style = "Signs",
                    text = "two",
                ),
            ),
        )
        val state = EditorState(
            document = document,
            subtitleLoaded = true,
            selectedEventIds = selected,
            selectionAnchorId = 1L,
            focusedEventId = 2L,
            canUndo = true,
            canRedo = false,
            dirty = true,
            workspaceSessionId = 42L,
        )

        val ui = state.toEditorUiState()

        assertEquals(2, ui.document.eventCount)
        assertEquals(1280, ui.document.playResX)
        assertEquals(720, ui.document.playResY)
        assertTrue(ui.document.subtitleLoaded)
        assertTrue(ui.document.dirty)
        assertEquals(2L, ui.focus.eventId)
        assertEquals(setOf(1L, 2L), ui.selection.eventIds)
        assertEquals(1L, ui.selection.anchorId)
        assertEquals(setOf(1L, 2L), ui.objects.existingEventIds)
        assertEquals(2L, ui.objects.currentEvent?.id)
        assertEquals("Signs", ui.objects.currentEvent?.styleName)
        assertEquals(3, ui.objects.currentEvent?.layer)
        assertFalse(ui.preview.active)
        assertEquals(null, ui.preview.ownerId)
        assertEquals(EditorUiBatchDefaultScope.SELECTION, ui.batch.defaultScope)
        assertEquals(2, ui.batch.selectedEventCount)
        assertTrue(ui.history.canUndo)
        assertFalse(ui.history.canRedo)
        assertEquals(42L, ui.workspaceSessionId)

        selected += 99L
        assertFalse(99L in ui.selection.eventIds)
        assertFalse(99L in ui.objects.existingEventIds)
    }

    @Test
    fun batchIntentDefaultsToAllEventsWithoutSelection() {
        val ui = EditorState(
            selectedEventIds = emptySet(),
        ).toEditorUiState()

        assertEquals(EditorUiBatchDefaultScope.ALL_EVENTS, ui.batch.defaultScope)
        assertEquals(0, ui.batch.selectedEventCount)
    }

    @Test
    fun transientPreviewProjectionDoesNotReplaceCanonicalDocumentOrHistory() {
        val canonical = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(1_000),
                    end = SubTime(2_000),
                    text = "canonical",
                ),
            ),
        )
        val preview = canonical.copy(
            events = canonical.events + AssEvent(
                id = 2L,
                start = SubTime(2_100),
                end = SubTime(3_000),
                text = "preview-only",
            ),
        )
        val ui = EditorState(
            document = canonical,
            previewDocument = preview,
            previewOwnerId = "style:Default",
            dirty = false,
            canUndo = false,
            canRedo = false,
        ).toEditorUiState()

        assertEquals(1, ui.document.eventCount)
        assertEquals(setOf(1L), ui.objects.existingEventIds)
        assertTrue(ui.preview.active)
        assertEquals("style:Default", ui.preview.ownerId)
        assertFalse(ui.document.dirty)
        assertFalse(ui.history.canUndo)
        assertFalse(ui.history.canRedo)
    }

    @Test
    fun previewOwnerIsNotProjectedWithoutAnActivePreviewDocument() {
        val ui = EditorState(
            previewDocument = null,
            previewOwnerId = "stale-owner",
        ).toEditorUiState()

        assertFalse(ui.preview.active)
        assertEquals(null, ui.preview.ownerId)
    }
    @Test
    fun resourceAndContainerProjectionStaysBoundedAndCopiesDiagnostics() {
        val rendererDiagnostics = mutableListOf(
            "Preview subtitle：sid=1",
            "Font match：Noto Sans",
        )
        val state = EditorState(
            rendererDiagnostics = rendererDiagnostics,
            fallbackFontFamily = "Noto Sans CJK SC",
            fontRevision = 7L,
            fontImportBusy = true,
            fontPackagingSelection = linkedSetOf("font-a", "font-b"),
            container = ContainerBridgeState(
                uri = "content://container/private.mkv",
                name = "fixture.mkv",
                loading = true,
                tracks = listOf(
                    ContainerTrackUi(2L, "Signs", "eng", 12),
                    ContainerTrackUi(4L, "Dialogue", "jpn", 28),
                ),
                selectedTrackNumber = 4L,
                extractedFontCount = 3,
                skippedAttachmentCount = 1,
                writeBackAvailable = true,
                writeBackBusy = true,
                error = "fixture error",
            ),
        )

        val ui = state.toEditorUiState()

        assertEquals(0, ui.resources.importedFontCount)
        assertTrue(ui.resources.fontImportBusy)
        assertEquals(7L, ui.resources.fontRevision)
        assertEquals(2, ui.resources.packagingSelectionCount)
        assertEquals("Noto Sans CJK SC", ui.resources.fallbackFontFamily)
        assertEquals(
            listOf("Preview subtitle：sid=1", "Font match：Noto Sans"),
            ui.diagnostics.rendererMessages,
        )

        assertTrue(ui.container.attached)
        assertEquals("fixture.mkv", ui.container.name)
        assertTrue(ui.container.loading)
        assertEquals(2, ui.container.trackCount)
        assertEquals(4L, ui.container.selectedTrackNumber)
        assertEquals(3, ui.container.extractedFontCount)
        assertEquals(1, ui.container.skippedAttachmentCount)
        assertTrue(ui.container.writeBackAvailable)
        assertTrue(ui.container.writeBackBusy)
        assertEquals("fixture error", ui.container.error)

        rendererDiagnostics += "late mutation"
        assertFalse("late mutation" in ui.diagnostics.rendererMessages)
    }

}
