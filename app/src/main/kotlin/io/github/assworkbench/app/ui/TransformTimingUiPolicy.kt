package io.github.assworkbench.app.ui

/** A touch slider is a bounded manipulator, not the validity domain of ASS \t times. */
internal data class TransformTimingHandleRange(
    val startMs: Long,
    val endMs: Long,
)

/**
 * Show interval handles only when both exact fields are integral, in the current
 * Event, and safely representable by the Float-based Compose RangeSlider.
 *
 * Shorthand \t, fractional/negative offsets and values outside the Event retain
 * their exact editable text; these must not be silently clamped or rewritten.
 */
internal fun transformTimingHandleRange(
    startText: String,
    endText: String,
    eventDurationMs: Long,
): TransformTimingHandleRange? {
    if (eventDurationMs <= 0L || eventDurationMs > 16_000_000L) return null
    // Domain values are Doubles and commonly display as "120.0".
    // Accept integral decimal notation without quantizing a fractional source.
    fun exactIntegralMs(text: String): Long? {
        val value = text.trim().toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0.0 || value > eventDurationMs.toDouble()) return null
        val integral = value.toLong()
        return integral.takeIf { integral.toDouble() == value }
    }
    val start = exactIntegralMs(startText) ?: return null
    val end = exactIntegralMs(endText) ?: return null
    if (end < start) return null
    return TransformTimingHandleRange(start, end)
}
