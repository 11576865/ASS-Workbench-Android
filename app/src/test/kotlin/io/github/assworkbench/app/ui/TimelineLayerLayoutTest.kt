package io.github.assworkbench.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineLayerLayoutTest {
    @Test
    fun overlappingEventsUseDifferentLanesAndTouchingEventsReuseLane() {
        val result = TimelineLaneLayout.assign(
            listOf(
                TimelineLayerItem(3, 1_500, 2_500),
                TimelineLayerItem(1, 0, 1_000),
                TimelineLayerItem(2, 800, 1_500),
                TimelineLayerItem(4, 2_500, 3_000),
            )
        )

        assertEquals(
            listOf(
                1L to 0,
                2L to 1,
                3L to 0,
                4L to 0,
            ),
            result.map { it.item.id to it.lane },
        )
    }

    @Test
    fun zeroDurationEventDoesNotBlockFollowingEventInSameLane() {
        val result = TimelineLaneLayout.assign(
            listOf(
                TimelineLayerItem(1, 1_000, 1_000),
                TimelineLayerItem(2, 1_000, 1_500),
            )
        )
        assertEquals(listOf(1L to 0, 2L to 0), result.map { it.item.id to it.lane })
    }

    @Test
    fun placementIsDeterministicForEqualIntervals() {
        val result = TimelineLaneLayout.assign(
            listOf(
                TimelineLayerItem(9, 1_000, 2_000),
                TimelineLayerItem(7, 1_000, 2_000),
                TimelineLayerItem(8, 1_000, 2_000),
            )
        )
        assertEquals(listOf(7L to 0, 8L to 1, 9L to 2), result.map { it.item.id to it.lane })
    }
}
