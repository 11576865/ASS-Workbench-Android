package io.github.assworkbench.domain

import kotlin.math.roundToLong

data class AssSyncAnchor(
    val sourceMs: Long,
    val targetMs: Long,
)

data class AssSyncPreview(
    val document: AssDocument,
    val changedEventIds: List<Long>,
    val anchors: List<AssSyncAnchor>,
)

/**
 * Piecewise-linear subtitle synchronizer.
 *
 * One anchor means a constant offset. Two or more anchors define a monotonic
 * piecewise-linear mapping. Outside the anchor range the first/last segment slope
 * is extrapolated so drift correction remains continuous across the document.
 */
object AssSubtitleSynchronizer {
    fun normalizeAnchors(anchors: Collection<AssSyncAnchor>): List<AssSyncAnchor> {
        val sorted = anchors.distinctBy { it.sourceMs }.sortedBy { it.sourceMs }
        require(sorted.isNotEmpty()) { "至少需要一个同步锚点" }
        require(sorted.zipWithNext().all { (a, b) -> b.sourceMs > a.sourceMs }) {
            "源时间必须严格递增"
        }
        require(sorted.zipWithNext().all { (a, b) -> b.targetMs > a.targetMs }) {
            "目标时间必须严格递增；同步映射不能倒序"
        }
        return sorted
    }

    fun parseAnchors(text: String): List<AssSyncAnchor> {
        val anchors = text.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split('=', limit = 2)
                require(parts.size == 2) { "第 ${index + 1} 行应为 source=target" }
                AssSyncAnchor(
                    sourceMs = parseTime(parts[0].trim()),
                    targetMs = parseTime(parts[1].trim()),
                )
            }
            .toList()
        return normalizeAnchors(anchors)
    }

    fun mapTime(timeMs: Long, anchors: Collection<AssSyncAnchor>): Long {
        val points = normalizeAnchors(anchors)
        if (points.size == 1) {
            val a = points.single()
            return (timeMs + (a.targetMs - a.sourceMs)).coerceAtLeast(0L)
        }

        val segment = when {
            timeMs <= points.first().sourceMs -> points[0] to points[1]
            timeMs >= points.last().sourceMs -> points[points.lastIndex - 1] to points.last()
            else -> points.zipWithNext().first { (a, b) -> timeMs in a.sourceMs..b.sourceMs }
        }
        val (a, b) = segment
        val sourceSpan = (b.sourceMs - a.sourceMs).toDouble()
        val targetSpan = (b.targetMs - a.targetMs).toDouble()
        val mapped = a.targetMs + (timeMs - a.sourceMs) * (targetSpan / sourceSpan)
        return mapped.roundToLong().coerceAtLeast(0L)
    }

    fun preview(
        document: AssDocument,
        anchors: Collection<AssSyncAnchor>,
        eventIds: Set<Long>? = null,
    ): AssSyncPreview {
        val normalized = normalizeAnchors(anchors)
        val changed = mutableListOf<Long>()
        val events = document.events.map { event ->
            if (eventIds != null && event.id !in eventIds) return@map event
            val start = mapTime(event.start.millis, normalized)
            val end = mapTime(event.end.millis, normalized).coerceAtLeast(start)
            val updated = event.copy(start = SubTime(start), end = SubTime(end))
            if (updated != event) changed += event.id
            updated
        }
        return AssSyncPreview(document.copy(events = events), changed, normalized)
    }

    private fun parseTime(raw: String): Long {
        raw.toLongOrNull()?.let { return it }
        return SubTime.fromEditable(raw).millis
    }
}
