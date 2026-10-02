package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassLayerModelTest {
    @Test
    fun frostedUsesTrueBackdropBlurWhenSystemSupportsIt() {
        val plan = resolveGlassRenderPlan(
            material = GlassMaterial.FROSTED,
            requestedAlpha = 0.7f,
            requestedBlurDp = 24f,
            performanceMode = GlassPerformanceMode.QUALITY,
            systemBackdropBlurAvailable = true,
            activeLayer = true,
            deemphasized = false,
        )

        assertTrue(plan.trueBackdropBlurActive)
        assertEquals(24f, plan.effectiveBlurDp)
        assertFalse(plan.degraded)
    }

    @Test
    fun frostedFallsBackWhenBackdropBlurIsUnavailable() {
        val plan = resolveGlassRenderPlan(
            material = GlassMaterial.FROSTED,
            requestedAlpha = 0.55f,
            requestedBlurDp = 30f,
            performanceMode = GlassPerformanceMode.AUTO,
            systemBackdropBlurAvailable = false,
            activeLayer = true,
            deemphasized = false,
        )

        assertFalse(plan.trueBackdropBlurActive)
        assertEquals(0f, plan.effectiveBlurDp)
        assertTrue(plan.degraded)
        assertTrue(plan.effectiveAlpha >= 0.78f)
    }

    @Test
    fun lowCostModeDisablesBlurWithoutChangingMaterialIdentity() {
        val plan = resolveGlassRenderPlan(
            material = GlassMaterial.FROSTED,
            requestedAlpha = 0.72f,
            requestedBlurDp = 36f,
            performanceMode = GlassPerformanceMode.LOW_COST,
            systemBackdropBlurAvailable = true,
            activeLayer = false,
            deemphasized = true,
        )

        assertEquals(GlassMaterial.FROSTED, plan.requestedMaterial)
        assertFalse(plan.trueBackdropBlurActive)
        assertTrue(plan.degraded)
    }
}
