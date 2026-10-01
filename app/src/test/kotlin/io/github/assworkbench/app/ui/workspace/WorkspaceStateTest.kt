package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val state = WorkspaceState()
            .openPrimary("POSITION")
            .openPrimary("STYLE")
        val hidden = state.withSurfacesHidden(true)

        assertTrue(hidden.surfacesHidden)
        assertEquals(state.tools, hidden.tools)

        val restored = hidden.withSurfacesHidden(false)
        assertFalse(restored.surfacesHidden)
        assertEquals(state.tools, restored.tools)
    }

    @Test
    fun sameToolCanHaveIndependentInstances() {
        var state = WorkspaceState().openPrimary("POSITION")
        val primary = state.primary("POSITION")!!
        state = state.updateBinding(primary.id, WorkspaceBinding.PinnedEvent(41))

        val sibling = state.newSibling(primary.id)!!
        state = state.addInstance(sibling)
        state = state.updateBinding(sibling.id, WorkspaceBinding.PinnedEvent(87))
        state = state.activate(sibling.id)

        assertEquals(2, state.instances("POSITION").size)
        assertEquals(
            WorkspaceBinding.PinnedEvent(41),
            state.primary("POSITION")!!.binding,
        )
        assertEquals(
            WorkspaceBinding.PinnedEvent(87),
            state.activeForTool("POSITION")!!.binding,
        )
    }

    @Test
    fun saveableRoundTripPreservesBindingsInstancesAndVisibility() {
        var state = WorkspaceState().openPrimary("POSITION")
        val primary = state.primary("POSITION")!!
        state = state.updateBinding(primary.id, WorkspaceBinding.PinnedEvent(41))

        val sibling = state.newSibling(primary.id)!!
        state = state
            .addInstance(sibling.copy(binding = WorkspaceBinding.FollowFocus))
            .activate(sibling.id)
            .withSurfacesHidden(true)

        val restored = WorkspaceState.fromSaveableList(state.toSaveableList())

        assertEquals(state, restored)
    }

    @Test
    fun openingDifferentPinnedTargetCreatesSiblingInsteadOfRetargetingPrimary() {
        var state = WorkspaceState().openPinnedEvent("POSITION", 41)
        val primary = state.primary("POSITION")!!
        assertEquals(WorkspaceBinding.PinnedEvent(41), primary.binding)

        state = state.openPinnedEvent("POSITION", 87)

        assertEquals(2, state.instances("POSITION").size)
        assertEquals(WorkspaceBinding.PinnedEvent(41), state.primary("POSITION")!!.binding)
        assertEquals(WorkspaceBinding.PinnedEvent(87), state.activeForTool("POSITION")!!.binding)
    }

    @Test
    fun openingSamePinnedTargetReusesPrimary() {
        val state = WorkspaceState()
            .openPinnedEvent("POSITION", 41)
            .openPinnedEvent("POSITION", 41)

        assertEquals(1, state.instances("POSITION").size)
        assertEquals(WorkspaceBinding.PinnedEvent(41), state.activeForTool("POSITION")!!.binding)
    }

    @Test
    fun sessionNamespaceRoundTripAndRebindAreExplicit() {
        val state = WorkspaceState(sessionId = 41L)
            .openPrimary("POSITION")
            .updateBinding(WorkspaceState.primaryInstanceId("POSITION"), WorkspaceBinding.PinnedEvent(7))
        val restored = WorkspaceState.fromSaveableList(state.toSaveableList())
        assertEquals(41L, restored.sessionId)
        assertEquals(99L, restored.forSession(99L).sessionId)
        assertEquals(WorkspaceBinding.PinnedEvent(7), restored.primary("POSITION")!!.binding)
    }
}
