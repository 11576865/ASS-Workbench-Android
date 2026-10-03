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
}
