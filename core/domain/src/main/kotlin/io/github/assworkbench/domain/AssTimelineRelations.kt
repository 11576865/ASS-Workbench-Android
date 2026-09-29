package io.github.assworkbench.domain

enum class AssTimelineRelationKind { GAP, TOUCH, OVERLAP }

data class AssTimelineRelation(
    val eventId: Long,
    val previousEventId: Long,
    val kind: AssTimelineRelationKind,
    val durationMs: Long,
)

object AssTimelineRelations {
    fun analyze(events: List<AssEvent>, includeComments: Boolean = false): List<AssTimelineRelation> {
        val ordered = events.asSequence()
            .filter { includeComments || !it.comment }
            .sortedWith(compareBy<AssEvent> { it.start.millis }.thenBy { it.end.millis }.thenBy { it.id })
            .toList()
        if (ordered.size < 2) return emptyList()

        val out = ArrayList<AssTimelineRelation>(ordered.size - 1)
        var frontierEvent = ordered.first()
        var frontierEnd = frontierEvent.end.millis
        for (index in 1 until ordered.size) {
            val current = ordered[index]
            val delta = current.start.millis - frontierEnd
            out += AssTimelineRelation(
                eventId = current.id,
                previousEventId = frontierEvent.id,
                kind = when {
                    delta < 0L -> AssTimelineRelationKind.OVERLAP
                    delta > 0L -> AssTimelineRelationKind.GAP
                    else -> AssTimelineRelationKind.TOUCH
                },
                durationMs = kotlin.math.abs(delta),
            )
            if (current.end.millis > frontierEnd) {
                frontierEvent = current
                frontierEnd = current.end.millis
            }
        }
        return out
    }
}
