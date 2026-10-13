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
    val start = startText.trim().toLongOrNull() ?: return null
    val end = endText.trim().toLongOrNull() ?: return null
    if (start < 0L || end < start || end > eventDurationMs) return null
    return TransformTimingHandleRange(start, end)
}
