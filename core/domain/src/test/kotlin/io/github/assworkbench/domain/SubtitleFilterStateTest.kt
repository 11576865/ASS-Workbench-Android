package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleFilterStateTest {
    private val events = listOf(
        AssEvent(90, start = SubTime(0), end = SubTime(1000), text = "{\\pos(10,20)}Hello [x]", name = "Alice", style = "Title"),
        AssEvent(3, start = SubTime(1000), end = SubTime(2000), text = "other", name = "HELLO", style = "Default", comment = true),
        AssEvent(70, start = SubTime(2000), end = SubTime(3000), text = "last", name = "Bob", style = "Hello"),
    )
    @Test fun emptyFilterPreservesDocumentOrderAndStableIds() {
        assertEquals(listOf(90L, 3L, 70L), SubtitleFilterState().apply(events).map { it.id })
    }
    @Test fun quickTextUsesOrAcrossVisibleTextActorAndStyle() {
        assertEquals(events, SubtitleFilterState(quickText = "hello").apply(events))
        assertEquals(listOf(90L), SubtitleFilterState(quickText = "[x]").apply(events).map { it.id })
        assertEquals(emptyList(), SubtitleFilterState(quickText = "pos").apply(events))
    }
    @Test fun quickSearchFindsStableEventIdAndDisplayedTimeWithoutMutatingEvents() {
        assertEquals(listOf(90L), SubtitleFilterState(quickText = "#90").apply(events).map { it.id })
        assertEquals(listOf(90L, 3L), SubtitleFilterState(quickText = "0:00:01.00")
            .apply(events).map { it.id })
        assertEquals(listOf(70L), SubtitleFilterState(quickText = "0:00:03.00")
            .apply(events).map { it.id })
        assertEquals(listOf(90L, 3L, 70L), events.map { it.id })
    }
    @Test fun fieldConditionsAreCombinedWithAnd() {
        assertEquals(listOf(90L), SubtitleFilterState(quickText = "hello", text = "[x]", actor = "ali", style = "tit").apply(events).map { it.id })
        assertEquals(emptyList(), SubtitleFilterState(text = "hello", actor = "bob").apply(events))
    }
    @Test fun kindDistinguishesDialogueAndComment() {
        assertEquals(listOf(3L), SubtitleFilterState(kind = SubtitleEventKind.COMMENT).apply(events).map { it.id })
        assertEquals(listOf(90L, 70L), SubtitleFilterState(kind = SubtitleEventKind.DIALOGUE).apply(events).map { it.id })
    }
    @Test fun selectedOnlyIntersectsConditionsAndEmptySelectionStaysEmpty() {
        val filter = SubtitleFilterState(quickText = "hello", selectedOnly = true)
        assertEquals(listOf(70L), filter.apply(events, setOf(70L, 999L)).map { it.id })
        assertEquals(emptyList(), filter.apply(events))
    }
    @Test fun navigationUsesResultOrderAndClampsAtBoundaries() {
        val filter = SubtitleFilterState(kind = SubtitleEventKind.DIALOGUE)
        assertEquals(70L, filter.relativeTarget(events, emptySet(), 90L, 1))
        assertEquals(90L, filter.relativeTarget(events, emptySet(), 90L, -1))
        assertEquals(70L, filter.relativeTarget(events, emptySet(), 70L, 1))
    }
    @Test fun excludedFocusNavigatesWithoutExpandingFilter() {
        val filter = SubtitleFilterState(kind = SubtitleEventKind.DIALOGUE)
        assertEquals(90L, filter.relativeTarget(events, emptySet(), 3L, 1))
        assertEquals(70L, filter.relativeTarget(events, emptySet(), 3L, -1))
        assertNull(SubtitleFilterState(text = "absent").relativeTarget(events, emptySet(), 90L, 1))
    }
    @Test fun primitiveSaveRestoresAllConditionsWithoutNormalizingText() {
        val filter = SubtitleFilterState("[x]", "hello\nworld", " Alice ", "Title", SubtitleEventKind.COMMENT, true)
        assertEquals(filter, SubtitleFilterState.restore(filter.save()))
    }
    @Test fun malformedPersistenceFallsBackToEmptyFilter() {
        assertEquals(SubtitleFilterState(), SubtitleFilterState.restore(listOf("bad")))
        assertEquals(SubtitleFilterState(), SubtitleFilterState.restore(listOf("", "", "", "", "OTHER", "true")))
    }
}
