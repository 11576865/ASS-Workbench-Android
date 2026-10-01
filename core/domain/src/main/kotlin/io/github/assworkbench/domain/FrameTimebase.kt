package io.github.assworkbench.domain

import kotlin.math.roundToLong

sealed interface FrameTimebase {
    fun frameCountHint(): Long?
    fun frameStartMs(frame: Long): Long
    fun nearestFrame(timeMs: Long): Long
    fun snap(timeMs: Long): Long = frameStartMs(nearestFrame(timeMs))

    data class Cfr(val fpsNumerator: Long, val fpsDenominator: Long = 1L) : FrameTimebase {
        init { require(fpsNumerator > 0 && fpsDenominator > 0) }
        override fun frameCountHint(): Long? = null
        override fun frameStartMs(frame: Long): Long {
            require(frame >= 0)
            return ((frame.toDouble() * 1000.0 * fpsDenominator) / fpsNumerator).roundToLong()
        }
        override fun nearestFrame(timeMs: Long): Long {
            require(timeMs >= 0)
            val f = timeMs.toDouble() * fpsNumerator / (1000.0 * fpsDenominator)
            return kotlin.math.round(f).toLong().coerceAtLeast(0)
        }
    }

    data class Vfr(private val ptsMs: List<Long>) : FrameTimebase {
        init {
            require(ptsMs.isNotEmpty())
            require(ptsMs.first() >= 0)
            require(ptsMs.zipWithNext().all { (a, b) -> b >= a })
        }
        override fun frameCountHint(): Long = ptsMs.size.toLong()
        override fun frameStartMs(frame: Long): Long {
            require(frame in 0 until ptsMs.size.toLong())
            return ptsMs[frame.toInt()]
        }
        override fun nearestFrame(timeMs: Long): Long {
            require(timeMs >= 0)
            val index = ptsMs.binarySearch(timeMs)
            if (index >= 0) return index.toLong()
            val insertion = -index - 1
            if (insertion <= 0) return 0
            if (insertion >= ptsMs.size) return (ptsMs.size - 1).toLong()
            val before = ptsMs[insertion - 1]
            val after = ptsMs[insertion]
            return if (timeMs - before <= after - timeMs) (insertion - 1).toLong() else insertion.toLong()
        }
    }
}

data class FrameTiming(val frame: Long, val timeMs: Long)

object FrameTimingTools {
    fun timing(time: SubTime, timebase: FrameTimebase): FrameTiming {
        val frame = timebase.nearestFrame(time.millis)
        return FrameTiming(frame, timebase.frameStartMs(frame))
    }

    fun shiftFrames(time: SubTime, deltaFrames: Long, timebase: FrameTimebase): SubTime {
        val current = timebase.nearestFrame(time.millis)
        val target = (current + deltaFrames).coerceAtLeast(0)
        val bounded = timebase.frameCountHint()?.let { target.coerceAtMost((it - 1).coerceAtLeast(0)) } ?: target
        return SubTime(timebase.frameStartMs(bounded))
    }
}
