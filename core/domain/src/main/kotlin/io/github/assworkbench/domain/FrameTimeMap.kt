package io.github.assworkbench.domain

/** Frame/time mapping used by the editor. ASS serialization remains time-based. */
sealed interface FrameTimeMap {
    fun frameAtOrBefore(timeMs: Long): Long
    fun timeForFrame(frame: Long): Long
    fun snap(timeMs: Long): Long = timeForFrame(frameAtOrBefore(timeMs))

    fun snapNearest(timeMs: Long): Long {
        val beforeFrame = frameAtOrBefore(timeMs)
        val before = timeForFrame(beforeFrame)
        val after = timeForFrame(beforeFrame + 1L)
        return if (after != before && kotlin.math.abs(after - timeMs) < kotlin.math.abs(timeMs - before)) after else before
    }

    data class Cfr(val numerator: Long, val denominator: Long = 1L) : FrameTimeMap {
        init { require(numerator > 0 && denominator > 0) }

        override fun frameAtOrBefore(timeMs: Long): Long =
            (timeMs.coerceAtLeast(0L) * numerator) / (1000L * denominator)

        override fun timeForFrame(frame: Long): Long =
            (frame.coerceAtLeast(0L) * 1000L * denominator) / numerator
    }

    /** Exact VFR frame-boundary timestamps in milliseconds. */
    class Vfr(frameTimestampsMs: LongArray) : FrameTimeMap {
        private val frames = frameTimestampsMs.copyOf()
        val frameCount: Int get() = frames.size

        init {
            require(frames.isNotEmpty())
            require(frames.first() >= 0L)
            for (i in 1 until frames.size) require(frames[i] >= frames[i - 1])
        }

        override fun frameAtOrBefore(timeMs: Long): Long {
            val target = timeMs.coerceAtLeast(0L)
            var low = 0
            var high = frames.lastIndex
            while (low <= high) {
                val mid = (low + high).ushr(1)
                if (frames[mid] <= target) low = mid + 1 else high = mid - 1
            }
            return high.coerceAtLeast(0).toLong()
        }

        override fun timeForFrame(frame: Long): Long =
            frames[frame.coerceIn(0L, frames.lastIndex.toLong()).toInt()]
    }
}
