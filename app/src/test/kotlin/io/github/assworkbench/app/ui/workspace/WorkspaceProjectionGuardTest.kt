package io.github.assworkbench.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class WorkspaceProjectionGuardTest {
    private val guard = WorkspaceProjectionGuard(4L, 12L, "geometry:12:projection")

    @Test fun replacementSessionCannotBeWrittenOrClearedEvenWithSameEventAndOwner() {
        assertFalse(guard.mayWrite(5L, 12L, "geometry:12:projection"))
        assertFalse(guard.mayClear(5L))
    }
    @Test fun obsoleteTargetCannotWriteButCanClearItsOldPreviewInSameSession() {
        assertFalse(guard.mayWrite(4L, 13L, null))
        assertFalse(guard.mayWrite(4L, null, null))
        assertTrue(guard.mayClear(4L))
    }
    @Test fun foreignOwnerCannotBeOverwritten() {
        assertFalse(guard.mayWrite(4L, 12L, "geometry:12"))
        assertFalse(guard.mayWrite(4L, 12L, "geometry:12:other"))
        assertTrue(guard.mayWrite(4L, 12L, null))
        assertTrue(guard.mayWrite(4L, 12L, "geometry:12:projection"))
    }
    @Test fun foreignTakeoverThenClearStillInvalidatesOldGesture() {
        assertTrue(guard.mayFinish(4L, 12L, null, 18L, 18L))
        assertFalse(guard.mayFinish(4L, 12L, null, 20L, 18L))
        assertFalse(guard.mayFinish(4L, 12L, "geometry:12:projection", 20L, 18L))
        assertFalse(guard.mayFinish(4L, 12L, null, 18L, null))
    }

}
