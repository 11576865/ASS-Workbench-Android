package io.github.assworkbench.domain

import kotlin.math.abs

data class TimingAssistTargets(
    val speechBoundariesMs: List<Long> = emptyList(),
    val sceneCutsMs: List<Long> = emptyList(),
) {
    val all: List<Long> get() = (speechBoundariesMs + sceneCutsMs).distinct().sorted()
}

object AudioTimingAssist {
    fun speechBoundaries(
        envelope: WaveformEnvelope,
        thresholdRatio: Double = 0.075,
        minActiveMs: Long = 80,
        minSilenceMs: Long = 100,
    ): List<Long> {
        if (envelope.bucketCount == 0) return emptyList()
        val levels = DoubleArray(envelope.bucketCount) { i ->
            maxOf(abs(envelope.minimums[i].toInt()), abs(envelope.maximums[i].toInt())) / 32767.0
        }
        val peak = levels.maxOrNull()?.takeIf { it > 0.0 } ?: return emptyList()
        val threshold = (peak * thresholdRatio.coerceIn(0.01, 0.8)).coerceAtLeast(0.012)
        val active = BooleanArray(levels.size)
        for (i in levels.indices) {
            val from = (i - 1).coerceAtLeast(0)
            val to = (i + 1).coerceAtMost(levels.lastIndex)
            var sum = 0.0
            for (j in from..to) sum += levels[j]
            active[i] = sum / (to - from + 1) >= threshold
        }
        val activeBuckets = (minActiveMs / envelope.bucketDurationMs).coerceAtLeast(1).toInt()
        val silenceBuckets = (minSilenceMs / envelope.bucketDurationMs).coerceAtLeast(1).toInt()
        val out = mutableListOf<Long>()
        var lastStable = active.first()
        var candidateStart = 0
        for (i in 1..active.size) {
            val value = if (i < active.size) active[i] else lastStable
            if (value == lastStable) { candidateStart = i; continue }
            val required = if (value) activeBuckets else silenceBuckets
            var run = 0
            var j = i
            while (j < active.size && active[j] == value && run < required) { run++; j++ }
            if (run >= required) {
                out += i.toLong() * envelope.bucketDurationMs
                lastStable = value
            }
        }
        return out.distinct().sorted()
    }

    fun nearest(candidateMs: Long, targets: Collection<Long>, thresholdMs: Long): Long? =
        targets.asSequence().map { it to abs(it - candidateMs) }.filter { it.second <= thresholdMs }.minByOrNull { it.second }?.first
}
