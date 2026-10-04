package io.github.assworkbench.app.ui.preview

import org.junit.Assert.*
import org.junit.Test

class GeometryPreviewLeaseTest {
    private val lease = GeometryPreviewLease(7L, 1L, "clip", 12L)

    @Test fun matchingPublicationOwnsItsPendingCommit() {
        assertTrue(lease.owns(7L, 1L, "clip", 12L, "geometry:1", true))
    }

    @Test fun newPublicationRevokesPendingCommitEvenWithEqualValues() {
        assertFalse(lease.owns(7L, 1L, "clip", 13L, "geometry:1", true))
    }

    @Test fun anotherParameterCannotCommitThisPublication() {
        assertFalse(lease.owns(7L, 1L, "rotation-x", 12L, "geometry:1", true))
    }

    @Test fun replacementWorkspaceWithReusedEventAndRevisionCannotCommit() {
        assertFalse(lease.owns(8L, 1L, "clip", 12L, "geometry:1", true))
    }

    @Test fun missingPreviewWrongTargetAndWrongOwnerRevokeCommit() {
        assertFalse(lease.owns(7L, 1L, "clip", 12L, "geometry:1", false))
        assertFalse(lease.owns(7L, 2L, "clip", 12L, "geometry:1", true))
        assertFalse(lease.owns(7L, 1L, "clip", 12L, "effects:1", true))
    }
}
