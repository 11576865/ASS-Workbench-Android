package io.github.assworkbench.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerEditPlanTest {
    @Test
    fun genericAttachmentIsExecutableWhileDownstreamBehaviorRemainsUnknown() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = true,
                pendingAttachments = listOf(
                    PendingContainerAttachmentUi(
                        uri = "fixture-cover",
                        name = "cover.png",
                        mimeType = "image/png",
                        sizeBytes = 4096L,
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertTrue(plan.executable)
        assertEquals(1, plan.mutations.size)
        assertEquals(ContainerMutationKind.ADD_ATTACHMENT, plan.mutations.single().kind)
        assertEquals(ContainerMutationSource.GENERIC_ATTACHMENT, plan.mutations.single().source)
        assertEquals(
            ContainerCompatibilityStatus.UNKNOWN,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.DOWNSTREAM }.status,
        )
    }

    @Test
    fun missingLocalWriterBlocksOtherwiseValidAttachmentPlan() {
        val state = EditorState(
            container = ContainerBridgeState(
                uri = "fixture-source",
                writeBackAvailable = false,
                pendingAttachments = listOf(
                    PendingContainerAttachmentUi(
                        uri = "fixture-note",
                        name = "notes.txt",
                        mimeType = "text/plain",
                    )
                ),
            ),
        )

        val plan = buildContainerEditPlan(state)

        assertFalse(plan.executable)
        assertEquals(
            ContainerCompatibilityStatus.UNSUPPORTED,
            plan.checks.single { it.dimension == ContainerCompatibilityDimension.LOCAL_WRITER }.status,
        )
    }

    @Test
    fun assReplacementOnlyExistsWhenSelectedContainerTrackIsDirty() {
        val clean = EditorState(
            subtitleLoaded = true,
            dirty = false,
            container = ContainerBridgeState(
                uri = "fixture-source",
                selectedTrackNumber = 2L,
                writeBackAvailable = true,
            ),
        )
        val dirty = clean.copy(dirty = true)

        assertTrue(buildContainerEditPlan(clean).mutations.isEmpty())
        val mutation = buildContainerEditPlan(dirty).mutations.single()
        assertEquals(ContainerMutationKind.REPLACE_ASS_TRACK, mutation.kind)
        assertEquals("replace-ass:2", mutation.id)
    }
}
