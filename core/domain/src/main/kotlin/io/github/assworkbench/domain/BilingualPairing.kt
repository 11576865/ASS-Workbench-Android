package io.github.assworkbench.domain

import kotlin.math.abs

data class SubtitlePairRow(
    val key: String,
    val source: AssEvent?,
    val target: AssEvent?,
    val startDeltaMs: Long?,
    val endDeltaMs: Long?,
    val timingMismatch: Boolean,
) {
    val missingSide: Boolean get() = source == null || target == null
}

object BilingualPairing {
    fun pair(
        document: AssDocument,
        sourceStyle: String,
        targetStyle: String,
        pairToleranceMs: Long = 800,
        mismatchThresholdMs: Long = 250,
    ): List<SubtitlePairRow> {
        if (sourceStyle.isBlank() || targetStyle.isBlank() || sourceStyle == targetStyle) return emptyList()

        val sources = document.events.filter { !it.comment && it.style == sourceStyle }.sortedBy { it.start.millis }
        val targets = document.events.filter { !it.comment && it.style == targetStyle }.sortedBy { it.start.millis }
        val remainingTargets = targets.associateBy { it.id }.toMutableMap()
        val rows = mutableListOf<SubtitlePairRow>()

        for (source in sources) {
            val candidates = remainingTargets.values.mapNotNull { target ->
                val startDelta = abs(source.start.millis - target.start.millis)
                val endDelta = abs(source.end.millis - target.end.millis)
                val overlaps = source.start < target.end && target.start < source.end
                val eligible = overlaps || (startDelta <= pairToleranceMs && endDelta <= pairToleranceMs)
                if (!eligible) null else Triple(target, startDelta, endDelta)
            }
            val best = candidates.minByOrNull { (_, startDelta, endDelta) -> startDelta + endDelta }
            if (best == null) {
                rows += SubtitlePairRow(
                    key = "source-" + source.id,
                    source = source,
                    target = null,
                    startDeltaMs = null,
                    endDeltaMs = null,
                    timingMismatch = false,
                )
            } else {
                val (target, startDelta, endDelta) = best
                remainingTargets.remove(target.id)
                rows += SubtitlePairRow(
                    key = "pair-" + source.id + "-" + target.id,
                    source = source,
                    target = target,
                    startDeltaMs = startDelta,
                    endDeltaMs = endDelta,
                    timingMismatch = startDelta > mismatchThresholdMs || endDelta > mismatchThresholdMs,
                )
            }
        }

        remainingTargets.values.sortedBy { it.start.millis }.forEach { target ->
            rows += SubtitlePairRow(
                key = "target-" + target.id,
                source = null,
                target = target,
                startDeltaMs = null,
                endDeltaMs = null,
                timingMismatch = false,
            )
        }

        return rows.sortedBy { it.source?.start?.millis ?: it.target?.start?.millis ?: Long.MAX_VALUE }
    }
}
