package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEvidenceViewportTest {
    @Test fun seekDuringDragKeepsSignalAndTouchInTheSameTimeRange() {
        val captured = audioEvidenceRange(10_000L)
        val range = audioEvidenceRange(13_000L, captured)
        assertEquals(6_000L to 14_000L, range)
        assertEquals(12_000L, audioEvidenceTimeAt(range, 300f, 400f))
    }

    @Test fun releasingOrCancellingResumesFollowingPlayback() {
        assertEquals(9_000L to 17_000L, audioEvidenceRange(13_000L))
        assertEquals(0L to 8_000L, audioEvidenceRange(1_000L))
    }

    @Test fun pointerOutsideSignalBoundsClampsToVisibleTimeRange() {
        val range = 6_000L to 14_000L
        assertEquals(6_000L, audioEvidenceTimeAt(range, -20f, 400f))
        assertEquals(14_000L, audioEvidenceTimeAt(range, 420f, 400f))
    }
}
