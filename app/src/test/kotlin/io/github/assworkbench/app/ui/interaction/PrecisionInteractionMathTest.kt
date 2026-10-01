package io.github.assworkbench.app.ui.interaction

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrecisionInteractionMathTest {
    @Test
    fun fineGainScalesMovementWithoutChangingDirection() {
        val result = PrecisionInteractionMath.applyGain(Offset(20f, -10f), PrecisionGain.FINE)
        assertEquals(3.6f, result.x, 0.001f)
        assertEquals(-1.8f, result.y, 0.001f)
    }

    @Test
    fun snapAdvertisesCenterBeforeApplyingIt() {
        val result = PrecisionInteractionMath.snap(
            target = Offset(480f, 300f),
            delta = Offset(15f, 0f),
            width = 1000f,
            height = 600f,
            thresholdPx = 12f,
        )
        assertEquals(20f, result.delta.x, 0.001f)
        assertEquals(500f, result.verticalGuidePx ?: -1f, 0.001f)
        assertEquals("水平中心", result.label)
    }

    @Test
    fun snapDoesNothingOutsideThreshold() {
        val result = PrecisionInteractionMath.snap(
            target = Offset(200f, 200f),
            delta = Offset(30f, 30f),
            width = 1000f,
            height = 600f,
            thresholdPx = 8f,
        )
        assertEquals(30f, result.delta.x, 0.001f)
        assertEquals(30f, result.delta.y, 0.001f)
        assertNull(result.label)
    }
}
