package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceStateTest {
    @Test
    fun pinnedMissingEventDoesNotFallback() {
        val result = WorkspaceBinding.PinnedEvent(10).resolve(
            focusedEventId = 20,
            selectedEventIds = setOf(20),
            existingEventIds = setOf(20),
        )
        assertEquals(
            WorkspaceBindingResolution.UnresolvedPinnedEvent(10),
            result,
        )
    }

    @Test
    fun hiddenWorkspaceKeepsInstances() {
        val state = WorkspaceState().openPrimary("POSITION")
        val hidden = state.withSurfacesHidden(true)
        assertTrue(hidden.surfacesHidden)
        assertEquals(state.tools, hidden.tools)
    }
}
