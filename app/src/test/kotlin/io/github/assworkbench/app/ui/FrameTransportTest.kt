package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTransportTest {
    @Test
    fun frameRepeatAcceleratesButKeepsBoundedCadence() {
        assertEquals(120L, frameRepeatDelayMs(0))
        assertTrue(frameRepeatDelayMs(4) < frameRepeatDelayMs(0))
        assertEquals(55L, frameRepeatDelayMs(100))
    }

    @Test
    fun fineJogQuantizesAndClampsToTwelveFrames() {
        assertEquals(0, frameJogTarget(0.2f))
        assertEquals(1, frameJogTarget(0.6f))
        assertEquals(-1, frameJogTarget(-0.6f))
        assertEquals(12, frameJogTarget(99f))
        assertEquals(-12, frameJogTarget(-99f))
    }
}
