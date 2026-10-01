package io.github.assworkbench.domain

import kotlin.math.abs
import kotlin.math.roundToLong

interface FrameTimeMap {
    val frameCount: Long?
    fun timeMsForFrame(frameIndex: Long): Long
    fun frameForTimeMs(timeMs: Long): Long

    fun snapMs(timeMs: Long): Long = timeMsForFrame(frameForTimeMs(timeMs))
}

data class CfrFrameTimeMap(
    val fpsNumerator: Long,
    val fpsDenominator: Long = 1L,
) : FrameTimeMap {
    init {
        require(fpsNumerator > 0L)
        require(fpsDenominator > 0L)
    }

    override val frameCount: Long? = null

    override fun timeMsForFrame(frameIndex: Long): Long {
        val frame = frameIndex.coerceAtLeast(0L)
        return (frame.toDouble() * 1000.0 * fpsDenominator.toDouble() / fpsNumerator.toDouble()).roundToLong()
    }

    override fun frameForTimeMs(timeMs: Long): Long =
        (timeMs.coerceAtLeast(0L).toDouble() * fpsNumerator.toDouble() /
            (1000.0 * fpsDenominator.toDouble())).roundToLong().coerceAtLeast(0L)
}

/**
 * Presentation-timestamp-based mapping for VFR material.
 * Values must be monotonically nondecreasing and represent the displayed frame start times.
 */
class VfrFrameTimeMap(
    presentationTimestampsMs: LongArray,
) : FrameTimeMap {
    private val pts = presentationTimestampsMs.copyOf()

    init {
        require(pts.isNotEmpty()) { "VFR frame map cannot be empty" }
        require(pts.first() >= 0L) { "Frame PTS must be non-negative" }
        require((1 until pts.size).all { index -> pts[index] >= pts[index - 1] }) {
            "Frame PTS must be monotonic"
        }
    }

    override val frameCount: Long = pts.size.toLong()

    override fun timeMsForFrame(frameIndex: Long): Long =
        pts[frameIndex.coerceIn(0L, pts.lastIndex.toLong()).toInt()]

    override fun frameForTimeMs(timeMs: Long): Long {
        val target = timeMs.coerceAtLeast(0L)
        var low = 0
        var high = pts.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            when {
                pts[mid] < target -> low = mid + 1
                pts[mid] > target -> high = mid - 1
                else -> return mid.toLong()
            }
        }
        val right = low.coerceAtMost(pts.lastIndex)
        val left = (low - 1).coerceAtLeast(0)
        return if (abs(pts[right] - target) < abs(target - pts[left])) right.toLong() else left.toLong()
    }

    fun timestampsCopy(): LongArray = pts.copyOf()
}

data class FrameTiming(
    val timeMs: Long,
    val frameIndex: Long,
)

object FrameTimingTools {
    fun at(map: FrameTimeMap, timeMs: Long): FrameTiming {
        val frame = map.frameForTimeMs(timeMs)
        return FrameTiming(map.timeMsForFrame(frame), frame)
    }

    fun shiftFrames(map: FrameTimeMap, timeMs: Long, deltaFrames: Long): FrameTiming {
        val source = map.frameForTimeMs(timeMs)
        val max = map.frameCount?.minus(1L)
        val target = if (max == null) {
            (source + deltaFrames).coerceAtLeast(0L)
        } else {
            (source + deltaFrames).coerceIn(0L, max.coerceAtLeast(0L))
        }
        return FrameTiming(map.timeMsForFrame(target), target)
    }
}
