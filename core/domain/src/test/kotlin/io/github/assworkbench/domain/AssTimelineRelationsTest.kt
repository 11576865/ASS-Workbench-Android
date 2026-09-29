package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssTimelineRelationsTest {
    private fun event(id: Long, start: Long, end: Long, comment: Boolean = false) =
        AssEvent(id = id, start = SubTime(start), end = SubTime(end), comment = comment, text = "E$id")

    @Test
    fun reportsGapTouchAndOverlap() {
        val relations = AssTimelineRelations.analyze(listOf(
            event(1, 0, 1_000),
            event(2, 1_250, 2_000),
            event(3, 2_000, 2_500),
            event(4, 2_400, 3_000),
        )).associateBy { it.eventId }
        assertEquals(AssTimelineRelationKind.GAP, relations.getValue(2).kind)
        assertEquals(250, relations.getValue(2).durationMs)
        assertEquals(AssTimelineRelationKind.TOUCH, relations.getValue(3).kind)
        assertEquals(AssTimelineRelationKind.OVERLAP, relations.getValue(4).kind)
        assertEquals(100, relations.getValue(4).durationMs)
    }

    @Test
    fun nestedOverlapUsesOccupiedFrontierRatherThanOnlyImmediateNeighbor() {
        val relations = AssTimelineRelations.analyze(listOf(
            event(1, 0, 10_000),
            event(2, 1_000, 2_000),
            event(3, 3_000, 4_000),
        )).associateBy { it.eventId }
        val third = relations.getValue(3)
        assertEquals(AssTimelineRelationKind.OVERLAP, third.kind)
        assertEquals(7_000, third.durationMs)
        assertEquals(1, third.previousEventId)
    }

    @Test
    fun commentsAreExcludedUnlessRequested() {
        val events = listOf(
            event(1, 0, 1_000),
            event(2, 500, 5_000, comment = true),
            event(3, 1_500, 2_000),
        )
        val normal = AssTimelineRelations.analyze(events)
        assertEquals(AssTimelineRelationKind.GAP, normal.single().kind)
        assertEquals(500, normal.single().durationMs)
        val withComments = AssTimelineRelations.analyze(events, includeComments = true)
        assertTrue(withComments.any { it.eventId == 3L && it.kind == AssTimelineRelationKind.OVERLAP })
    }
}
