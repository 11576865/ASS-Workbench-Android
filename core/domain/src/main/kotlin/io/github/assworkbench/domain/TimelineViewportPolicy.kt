package io.github.assworkbench.domain

/**
 * Pure timeline viewport policy used by the Compose timeline and unit tests.
 * Playback telemetry must only recenter a manually panned viewport when follow
 * mode is explicitly enabled.
 */
object TimelineViewportPolicy {
    fun resolveCenter(
        playheadMs: Long,
        currentCenterMs: Long,
        halfWindowMs: Long,
        followPlayhead: Boolean,
    ): Long {
        val minimum = halfWindowMs.coerceAtLeast(0L)
        return if (followPlayhead) {
            playheadMs.coerceAtLeast(minimum)
        } else {
            currentCenterMs.coerceAtLeast(minimum)
        }
    }

    fun panCenter(
        currentCenterMs: Long,
        dragAmountPx: Float,
        widthPx: Int,
        windowDurationMs: Long,
        halfWindowMs: Long,
    ): Long {
        val minimum = halfWindowMs.coerceAtLeast(0L)
        if (widthPx <= 0 || windowDurationMs <= 0L || !dragAmountPx.isFinite()) {
            return currentCenterMs.coerceAtLeast(minimum)
        }
        val deltaMs = (dragAmountPx / widthPx.toFloat() * windowDurationMs.toDouble()).toLong()
        return (currentCenterMs - deltaMs).coerceAtLeast(minimum)
    }
}
