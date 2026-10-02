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

    /**
     * Keep the temporal point under the touch centroid stable while the viewport duration changes.
     *
     * [anchorFraction] is the horizontal touch position in the old viewport, from 0.0 (left)
     * to 1.0 (right). This is presentation-neutral timeline math; callers decide gesture policy.
     */
    fun zoomCenterAroundAnchor(
        currentCenterMs: Long,
        oldWindowDurationMs: Long,
        newWindowDurationMs: Long,
        anchorFraction: Float,
    ): Long {
        val oldDuration = oldWindowDurationMs.coerceAtLeast(1L)
        val newDuration = newWindowDurationMs.coerceAtLeast(1L)
        val fraction = if (anchorFraction.isFinite()) anchorFraction.coerceIn(0f, 1f) else 0.5f
        val oldHalf = oldDuration / 2.0
        val oldStart = currentCenterMs.toDouble() - oldHalf
        val anchorTime = oldStart + oldDuration.toDouble() * fraction
        val newCenter = anchorTime + newDuration.toDouble() * (0.5 - fraction)
        val minimum = newDuration / 2.0
        return kotlin.math.round(newCenter.coerceAtLeast(minimum)).toLong()
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
