package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceParameterProjectionTest {
    @Test
    fun extractedProjectionPersistsDescriptorPresentationAndPinnedBinding() {
        val state = WorkspaceState(sessionId = 41L)
            .addParameterProjection(
                descriptorKey = WorkspaceParameterCatalog.rotationZ.key,
                presentation = WorkspaceParameterPresentation.SLIDER,
                binding = WorkspaceBinding.PinnedEvent(7L),
            )

        val restored = WorkspaceState.fromSaveableList(state.toSaveableList())
        assertEquals(1, restored.parameterProjections.size)
        val projection = restored.parameterProjections.single()
        assertEquals("event.rotation.z", projection.descriptorKey)
        assertEquals(WorkspaceParameterPresentation.SLIDER, projection.presentation)
        assertEquals(WorkspaceBinding.PinnedEvent(7L), projection.binding)
        assertEquals(41L, restored.sessionId)
    }

    @Test
    fun sameDescriptorCanHaveIndependentPresentationInstances() {
        val state = WorkspaceState()
            .addParameterProjection(
                descriptorKey = WorkspaceParameterCatalog.rotationZ.key,
                presentation = WorkspaceParameterPresentation.SLIDER,
                binding = WorkspaceBinding.FollowFocus,
            )
            .addParameterProjection(
                descriptorKey = WorkspaceParameterCatalog.rotationZ.key,
                presentation = WorkspaceParameterPresentation.NUMBER,
                binding = WorkspaceBinding.FollowFocus,
            )

        assertEquals(2, state.parameterProjections.size)
        assertEquals(1, state.parameterProjections.map { it.descriptorKey }.toSet().size)
        assertEquals(2, state.parameterProjections.map { it.id }.toSet().size)
    }

    @Test
    fun unsupportedPresentationUpdateIsFailClosed() {
        val state = WorkspaceState()
            .addParameterProjection(
                descriptorKey = WorkspaceParameterCatalog.positionXY.key,
                presentation = WorkspaceParameterPresentation.XY_PAD,
                binding = WorkspaceBinding.FollowFocus,
            )
        val id = state.parameterProjections.single().id

        val next = state.updateParameterPresentation(id, WorkspaceParameterPresentation.ANGLE_DIAL)

        assertEquals(WorkspaceParameterPresentation.XY_PAD, next.parameterProjections.single().presentation)
    }

    @Test
    fun legacyV3WorkspaceRestoresWithoutInventingParameterInstances() {
        val separator = "\u001F"
        val restored = WorkspaceState.fromSaveableList(
            listOf(
                "workspace-v3",
                "0",
                "POSITION:primary",
                "9",
                listOf(
                    "POSITION:primary",
                    "POSITION",
                    "focus",
                    "",
                    "RESIDENT",
                    "STANDARD",
                ).joinToString(separator),
            )
        )

        assertTrue(restored.parameterProjections.isEmpty())
        assertEquals("POSITION", restored.tools.single().toolKey)
    }
}
