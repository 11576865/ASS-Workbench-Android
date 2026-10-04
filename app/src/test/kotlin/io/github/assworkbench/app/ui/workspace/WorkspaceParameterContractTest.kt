package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceParameterContractTest {
    @Test
    fun catalogUsesStableUniqueSemanticKeys() {
        val descriptors = WorkspaceParameterCatalog.descriptors
        assertEquals(descriptors.size, descriptors.map { it.key }.toSet().size)
        assertSame(
            WorkspaceParameterCatalog.rotationZ,
            WorkspaceParameterCatalog.find("event.rotation.z"),
        )
    }

    @Test
    fun oneSemanticParameterSupportsMultipleVisualPresentations() {
        val descriptor = WorkspaceParameterCatalog.rotationZ
        assertTrue(descriptor.supports(WorkspaceParameterPresentation.NUMBER))
        assertTrue(descriptor.supports(WorkspaceParameterPresentation.SLIDER))
        assertTrue(descriptor.supports(WorkspaceParameterPresentation.ANGLE_DIAL))
    }

    @Test
    fun eventIntentAcceptsFollowFocusAndPinnedEventWithoutChangingDescriptorIdentity() {
        val follow = WorkspaceParameterIntent(
            address = WorkspaceParameterAddress(
                projectionId = "rotation:follow",
                descriptorKey = WorkspaceParameterCatalog.rotationZ.key,
                binding = WorkspaceBinding.FollowFocus,
            ),
            phase = WorkspaceParameterIntentPhase.PREVIEW,
            values = listOf(35.0),
            revision = 4L,
        )
        val pinned = follow.copy(
            address = follow.address.copy(
                projectionId = "rotation:41",
                binding = WorkspaceBinding.PinnedEvent(41L),
            ),
            phase = WorkspaceParameterIntentPhase.COMMIT,
            revision = 5L,
        )

        assertSame(
            WorkspaceParameterCatalog.rotationZ,
            WorkspaceParameterIntentContract.requireValid(follow),
        )
        assertSame(
            WorkspaceParameterCatalog.rotationZ,
            WorkspaceParameterIntentContract.requireValid(pinned),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun eventIntentRejectsSelectionBinding() {
        WorkspaceParameterIntentContract.requireValid(
            WorkspaceParameterIntent(
                address = WorkspaceParameterAddress(
                    projectionId = "position:selection",
                    descriptorKey = WorkspaceParameterCatalog.positionXY.key,
                    binding = WorkspaceBinding.FollowSelection,
                ),
                phase = WorkspaceParameterIntentPhase.PREVIEW,
                values = listOf(100.0, 200.0),
                revision = 1L,
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun vectorIntentRejectsWrongArity() {
        WorkspaceParameterIntentContract.requireValid(
            WorkspaceParameterIntent(
                address = WorkspaceParameterAddress(
                    projectionId = "position:1",
                    descriptorKey = WorkspaceParameterCatalog.positionXY.key,
                    binding = WorkspaceBinding.PinnedEvent(1L),
                ),
                phase = WorkspaceParameterIntentPhase.COMMIT,
                values = listOf(100.0),
                revision = 2L,
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun cancelIntentCannotSmuggleValues() {
        WorkspaceParameterIntentContract.requireValid(
            WorkspaceParameterIntent(
                address = WorkspaceParameterAddress(
                    projectionId = "scale:1",
                    descriptorKey = WorkspaceParameterCatalog.scaleXY.key,
                    binding = WorkspaceBinding.PinnedEvent(1L),
                ),
                phase = WorkspaceParameterIntentPhase.CANCEL,
                values = listOf(100.0, 100.0),
                revision = 3L,
            )
        )
    }
}
