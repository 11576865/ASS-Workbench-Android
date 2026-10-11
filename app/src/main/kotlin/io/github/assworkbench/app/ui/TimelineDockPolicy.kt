package io.github.assworkbench.app.ui

internal object TimelineDockPolicy {
    const val MIN_EXTENT_FRACTION = 0.30f
    const val MAX_EXTENT_FRACTION = 0.72f
    const val DEFAULT_EXTENT_FRACTION = 0.50f

    private val snapStops = listOf(0.38f, 0.50f, 0.64f)

    fun resize(current: Float, deltaFraction: Float): Float =
        (current + deltaFraction).coerceIn(MIN_EXTENT_FRACTION, MAX_EXTENT_FRACTION)

    fun snap(current: Float): Float =
        snapStops.minByOrNull { kotlin.math.abs(it - current) } ?: DEFAULT_EXTENT_FRACTION

    fun expansionAfterDrag(current: Boolean, dragFractionY: Float): Boolean = when {
        dragFractionY <= -0.04f -> true
        dragFractionY >= 0.08f -> false
        else -> current
    }

    data class CanvasLayout(val heightDp: Float, val compact: Boolean, val suspended: Boolean)

    private const val CANVAS_MIN_DOCK_DP = 142f
    private const val CANVAS_MIN_EDITOR_DP = 208f
    private const val CANVAS_DEFAULT_EXTENT = 0.42f

    private fun canvasMaximum(viewportHeightDp: Float): Float =
        if (!viewportHeightDp.isFinite() || viewportHeightDp <= 0f) 0f
        else minOf(viewportHeightDp * MAX_EXTENT_FRACTION, viewportHeightDp - CANVAS_MIN_EDITOR_DP).coerceAtLeast(0f)

    /** Temporary projection leaves the user's preferred height unchanged. */
    fun canvasLayout(viewportHeightDp: Float, expanded: Boolean, extentFraction: Float): CanvasLayout {
        val maximum = canvasMaximum(viewportHeightDp)
        if (maximum < CANVAS_MIN_DOCK_DP) return CanvasLayout(0f, compact = true, suspended = true)
        val extent = extentFraction.takeIf { it.isFinite() } ?: CANVAS_DEFAULT_EXTENT
        val height = if (expanded) (viewportHeightDp * extent.coerceIn(0f, MAX_EXTENT_FRACTION))
            .coerceIn(CANVAS_MIN_DOCK_DP, maximum) else CANVAS_MIN_DOCK_DP
        return CanvasLayout(height, compact = !expanded || height < 220f, suspended = false)
    }

    /** Drag delta is dp, positive down. Start at actual height to avoid preset jumps. */
    fun canvasResizeExtent(viewportHeightDp: Float, startHeightDp: Float, dragDeltaDp: Float): Float {
        val maximum = canvasMaximum(viewportHeightDp)
        if (maximum < CANVAS_MIN_DOCK_DP) return CANVAS_DEFAULT_EXTENT
        val start = startHeightDp.takeIf { it.isFinite() } ?: CANVAS_MIN_DOCK_DP
        val delta = dragDeltaDp.takeIf { it.isFinite() } ?: 0f
        return (start - delta).coerceIn(CANVAS_MIN_DOCK_DP, maximum) / viewportHeightDp
    }

    fun canvasSettleExtent(viewportHeightDp: Float, extentFraction: Float): Float {
        val projected = canvasLayout(viewportHeightDp, true, extentFraction)
        if (projected.suspended) return extentFraction.takeIf { it.isFinite() } ?: CANVAS_DEFAULT_EXTENT
        val current = projected.heightDp / viewportHeightDp
        val stops = snapStops + (CANVAS_MIN_DOCK_DP / viewportHeightDp)
        val nearest = stops.minBy { kotlin.math.abs(it - current) }
        val settled = if (kotlin.math.abs(nearest - current) * viewportHeightDp <= 16f) nearest else current
        return canvasLayout(viewportHeightDp, true, settled).heightDp / viewportHeightDp
    }
}
