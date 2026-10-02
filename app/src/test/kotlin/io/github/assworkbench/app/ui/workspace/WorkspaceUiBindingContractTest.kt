package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.toEditorUiState
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.SubTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceUiBindingContractTest {
    private val document = AssDocument(
        events = listOf(
            AssEvent(41, start = SubTime(0), end = SubTime(1_000), style = "Default", text = "A"),
            AssEvent(87, start = SubTime(1_100), end = SubTime(2_000), style = "Signs", text = "B"),
        ),
    )

    private fun editorUi(
        focus: Long? = 87,
        selection: Set<Long> = emptySet(),
    ) = EditorState(
        document = document,
        focusedEventId = focus,
        selectedEventIds = selection,
        workspaceSessionId = 11L,
    ).toEditorUiState()

    @Test
    fun followFocusResolvesFromStableCurrentObjectProjection() {
        val instance = WorkspaceToolInstance(
            id = "TEXT:primary",
            toolKey = "TEXT",
            binding = WorkspaceBinding.FollowFocus,
        )

        val binding = instance.resolveUiBinding(editorUi())

        assertEquals(87L, binding.eventId)
        assertEquals("FollowFocus", binding.label)
    }

    @Test
    fun pinnedEventDoesNotRetargetToGlobalFocus() {
        val instance = WorkspaceToolInstance(
            id = "POSITION:primary",
            toolKey = "POSITION",
            binding = WorkspaceBinding.PinnedEvent(41),
        )

        val binding = instance.resolveUiBinding(editorUi(focus = 87))

        assertEquals(41L, binding.eventId)
        assertEquals(null, binding.unresolvedPinnedEventId)
    }

    @Test
    fun missingPinnedEventRemainsExplicitlyUnresolved() {
        val instance = WorkspaceToolInstance(
            id = "POSITION:primary",
            toolKey = "POSITION",
            binding = WorkspaceBinding.PinnedEvent(404),
        )

        val binding = instance.resolveUiBinding(editorUi(focus = 87))

        assertEquals(null, binding.eventId)
        assertEquals(404L, binding.unresolvedPinnedEventId)
        assertTrue(binding.resolution is WorkspaceBindingResolution.UnresolvedPinnedEvent)
    }

    @Test
    fun followSelectionUsesSelectionWithoutChangingFocus() {
        val instance = WorkspaceToolInstance(
            id = "BATCH:primary",
            toolKey = "BATCH",
            binding = WorkspaceBinding.FollowSelection,
        )

        val binding = instance.resolveUiBinding(editorUi(focus = 87, selection = setOf(41, 87)))

        assertEquals(setOf(41L, 87L), binding.selectionEventIds)
    }
}
