package io.github.assworkbench.app.ui

internal data class TimelineLayerItem(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
)

internal data class TimelineLanePlacement(
    val item: TimelineLayerItem,
    val lane: Int,
)

/**
 * Greedy interval partitioning for a temporal canvas.
 *
 * Events that overlap in time never occupy the same lane. Touching boundaries may reuse a lane.
 * Sorting includes the stable event id so the same document produces deterministic placement.
 */
internal object TimelineLaneLayout {
    fun assign(items: List<TimelineLayerItem>): List<TimelineLanePlacement> {
        if (items.isEmpty()) return emptyList()
        val laneEnds = mutableListOf<Long>()
        return items
            .sortedWith(
                compareBy<TimelineLayerItem> { it.startMs }
                    .thenBy { it.endMs }
                    .thenBy { it.id }
            )
            .map { item ->
                val lane = laneEnds.indexOfFirst { it <= item.startMs }.let { index ->
                    if (index >= 0) index else laneEnds.size.also { laneEnds += Long.MIN_VALUE }
                }
                laneEnds[lane] = item.endMs.coerceAtLeast(item.startMs)
                TimelineLanePlacement(item, lane)
            }
    }
}
