package io.github.assworkbench.app.ui

internal enum class GlassMaterial(val label: String) {
    TRANSPARENT("透明"),
    FROSTED("磨砂"),
    SOLID("实底");

    fun next(): GlassMaterial = entries[(ordinal + 1) % entries.size]
}

internal enum class GlassPerformanceMode(val label: String) {
    QUALITY("高质量"),
    AUTO("自动"),
    LOW_COST("低成本"),
}

internal data class GlassRenderPlan(
    val requestedMaterial: GlassMaterial,
    val effectiveBlurDp: Float,
    val effectiveAlpha: Float,
    val trueBackdropBlurActive: Boolean,
    val degraded: Boolean,
)

internal fun resolveGlassRenderPlan(
    material: GlassMaterial,
    requestedAlpha: Float,
    requestedBlurDp: Float,
    performanceMode: GlassPerformanceMode,
    systemBackdropBlurAvailable: Boolean,
    activeLayer: Boolean,
    deemphasized: Boolean,
): GlassRenderPlan {
    val safeAlpha = requestedAlpha.coerceIn(0.18f, 1f)
    val safeBlur = requestedBlurDp.coerceIn(0f, 50f)
    val qualityAllowsBlur = performanceMode != GlassPerformanceMode.LOW_COST
    val blurActive = material == GlassMaterial.FROSTED &&
        qualityAllowsBlur &&
        systemBackdropBlurAvailable &&
        safeBlur > 0f

    val materialAlpha = when (material) {
        GlassMaterial.TRANSPARENT -> (safeAlpha * 0.62f).coerceAtLeast(0.18f)
        GlassMaterial.FROSTED -> if (blurActive) safeAlpha else safeAlpha.coerceAtLeast(0.78f)
        GlassMaterial.SOLID -> 1f
    }
    val activeFactor = if (activeLayer) 1f else 0.9f
    val relationFactor = if (deemphasized) 0.7f else 1f
    val effectiveAlpha = (materialAlpha * activeFactor * relationFactor).coerceIn(0.16f, 1f)

    return GlassRenderPlan(
        requestedMaterial = material,
        effectiveBlurDp = if (blurActive) safeBlur else 0f,
        effectiveAlpha = effectiveAlpha,
        trueBackdropBlurActive = blurActive,
        degraded = material == GlassMaterial.FROSTED && !blurActive,
    )
}
