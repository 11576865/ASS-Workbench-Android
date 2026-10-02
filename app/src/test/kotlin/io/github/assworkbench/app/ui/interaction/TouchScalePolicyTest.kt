package io.github.assworkbench.app.ui.interaction

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchScalePolicyTest {
    @Test
    fun lockedXYPreservesExistingNonSquareRatio() {
        val value = TouchScalePolicy.xy(
            baseX = 120.0,
            baseY = 80.0,
            deltaX = 12.0,
            deltaY = 8.0,
            locked = true,
        )
        assertEquals(1.5, value.x / value.y, 0.0001)
    }

    @Test
    fun lockedSingleAxisUsesAxisAsDriverWithoutCollapsingRatio() {
        val fromX = TouchScalePolicy.x(120.0, 80.0, deltaX = 30.0, locked = true)
        val fromY = TouchScalePolicy.y(120.0, 80.0, deltaY = 20.0, locked = true)
        assertEquals(1.5, fromX.x / fromX.y, 0.0001)
        assertEquals(1.5, fromY.x / fromY.y, 0.0001)
    }

    @Test
    fun lockedScaleClampsFactorBeforeAxesSoRatioSurvivesBounds() {
        val value = TouchScalePolicy.x(900.0, 100.0, deltaX = 500.0, locked = true)
        assertEquals(9.0, value.x / value.y, 0.0001)
        assertEquals(1000.0, value.x, 0.0001)
    }

    @Test
    fun unlockedAxesRemainIndependent() {
        assertEquals(TouchScaleValue(150.0, 80.0), TouchScalePolicy.x(120.0, 80.0, 30.0, false))
        assertEquals(TouchScaleValue(120.0, 100.0), TouchScalePolicy.y(120.0, 80.0, 20.0, false))
    }
    @Test
    fun unlockedScaleCanSnapToExplicitPercentageStep() {
        assertEquals(
            TouchScaleValue(150.0, 80.0),
            TouchScalePolicy.x(
                baseX = 120.0,
                baseY = 80.0,
                deltaX = 22.0,
                locked = false,
                snapStep = 25.0,
            ),
        )
    }

    @Test
    fun lockedSnapUsesDriverAxisButKeepsExistingRatio() {
        val value = TouchScalePolicy.x(
            baseX = 120.0,
            baseY = 80.0,
            deltaX = 22.0,
            locked = true,
            snapStep = 25.0,
        )
        assertEquals(150.0, value.x, 0.0001)
        assertEquals(100.0, value.y, 0.0001)
        assertEquals(1.5, value.x / value.y, 0.0001)
    }

    @Test
    fun invalidSnapStepBehavesAsSnapOff() {
        val value = TouchScalePolicy.y(
            baseX = 120.0,
            baseY = 80.0,
            deltaY = 13.0,
            locked = false,
            snapStep = 0.0,
        )
        assertEquals(TouchScaleValue(120.0, 93.0), value)
    }
}
