package io.github.assworkbench.domain

data class AssTimelineSpan(
    val startMs: Long,
    val endMs: Long,
)

object AssTimelineSnap {
    fun snapPoint(
        candidateMs: Long,
        targets: Iterable<Long>,
        thresholdMs: Long,
        gridMs: Long? = null,
    ): Long {
        val threshold = thresholdMs.coerceAtLeast(0L)
        val explicit = nearestTarget(candidateMs, targets)
        if (explicit != null && distance(explicit, candidateMs) <= threshold) return explicit
        val grid = gridMs?.takeIf { it > 0L } ?: return candidateMs
        return nearestGrid(candidateMs, grid)
    }

    fun snapSpan(
        startMs: Long,
        endMs: Long,
        targets: Iterable<Long>,
        thresholdMs: Long,
        gridMs: Long? = null,
    ): AssTimelineSpan {
        require(endMs >= startMs) { "Timeline span end must not precede start." }
        val threshold = thresholdMs.coerceAtLeast(0L)
        var bestDelta: Long? = null
        targets.forEach { target ->
            val startDelta = target - startMs
            val endDelta = target - endMs
            listOf(startDelta, endDelta).forEach { delta ->
                if (distance(delta, 0L) <= threshold &&
                    (bestDelta == null || distance(delta, 0L) < distance(bestDelta!!, 0L))
                ) {
                    bestDelta = delta
                }
            }
        }
        bestDelta?.let { delta ->
            return AssTimelineSpan(startMs + delta, endMs + delta)
        }

        val grid = gridMs?.takeIf { it > 0L } ?: return AssTimelineSpan(startMs, endMs)
        val startDelta = nearestGrid(startMs, grid) - startMs
        val endDelta = nearestGrid(endMs, grid) - endMs
        val delta = if (distance(startDelta, 0L) <= distance(endDelta, 0L)) startDelta else endDelta
        return AssTimelineSpan(startMs + delta, endMs + delta)
    }

    private fun nearestTarget(candidateMs: Long, targets: Iterable<Long>): Long? {
        var best: Long? = null
        var bestDistance = Long.MAX_VALUE
        targets.forEach { target ->
            val d = distance(target, candidateMs)
            if (d < bestDistance) {
                best = target
                bestDistance = d
            }
        }
        return best
    }

    private fun nearestGrid(valueMs: Long, stepMs: Long): Long {
        val lower = Math.floorDiv(valueMs, stepMs) * stepMs
        val upper = lower + stepMs
        return if (distance(valueMs, lower) <= distance(upper, valueMs)) lower else upper
    }

    private fun distance(a: Long, b: Long): Long {
        val delta = if (a >= b) a - b else b - a
        return if (delta < 0L) Long.MAX_VALUE else delta
    }
}
