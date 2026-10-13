package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventTimingDraftTest {
    private val original = EventTimingCanonical(1000, 2000)
    @Test fun cleanPairFollowsExternalTiming() {
        val next = original.copy(endMillis = 3000)
        assertEquals(EventTimingDraftBuffer(next), EventTimingDraftBuffer(original).reconcile(next))
    }
    @Test fun invalidLiteralAndOtherFieldRemainTogetherOnExternalChange() {
        val dirty = EventTimingDraftBuffer(original, EventTimingValues("broken", "3000"))
        val next = original.copy(startMillis = 0)
        assertEquals(dirty, dirty.reconcile(next))
        assertEquals(RawEventDraftState.EXTERNAL_CONFLICT, dirty.classify(next))
    }
    @Test fun matchingPairAcknowledgesItsCommittedBaseline() {
        val next = original.copy(endMillis = 3000)
        val literal = EventTimingValues("1000", "3000")
        assertEquals(EventTimingDraftBuffer(next, literal), EventTimingDraftBuffer(original, literal).reconcile(next))
    }
    @Test fun equivalentMillisecondInputsDoNotNormalizeOnHostRemount() {
        val draft = EventTimingDraftBuffer(original, EventTimingValues(" 1000 ", "2000"))
        assertEquals(draft, draft.reconcile(original))
    }
    @Test fun roundTripPreservesLiteralInputAndIndependentOwner() {
        val primary = EventTextDraftKey(EventTextDraftKey.owner(null), 1L)
        val sibling = EventTextDraftKey("sibling", 1L)
        val snapshot = EventTimingDraftSnapshot(5, mapOf(primary to EventTimingDraftBuffer(original,
            EventTimingValues(" 1000 ", "bad\n|end")), sibling to EventTimingDraftBuffer(original)))
        assertEquals(snapshot, EventTimingDraftSnapshot.restore(snapshot.save(), 5))
        assertEquals(setOf(primary), snapshot.withoutInstance("sibling").drafts.keys)
        assertEquals(snapshot, snapshot.withoutInstance("TEXT:primary"))
    }
    @Test fun sessionMismatchAndDeletedEventRejectOldTiming() {
        val snapshot = EventTimingDraftSnapshot(5, mapOf(EventTextDraftKey("sibling", 1L) to EventTimingDraftBuffer(original)))
        assertTrue(EventTimingDraftSnapshot.restore(snapshot.save(), 6).drafts.isEmpty())
        assertTrue(snapshot.retainEvents(emptySet()).drafts.isEmpty())
    }
    @Test fun subCentisecondExternalChangeIsNotHiddenByAssFormatting() {
        val base = EventTimingCanonical(1049, 2000)
        val dirty = EventTimingDraftBuffer(base, EventTimingValues("1500", "2000"))
        val next = base.copy(startMillis = 1041)
        assertEquals(base.editable(), next.editable())
        assertEquals(dirty, dirty.reconcile(next))
        assertEquals(RawEventDraftState.EXTERNAL_CONFLICT, dirty.classify(next))
    }
    @Test fun truncatedRecordDoesNotInventMissingEnd() {
        val snapshot = EventTimingDraftSnapshot(5, mapOf(EventTextDraftKey("sibling", 1L) to EventTimingDraftBuffer(original)))
        assertTrue(EventTimingDraftSnapshot.restore(snapshot.save().dropLast(1), 5).drafts.isEmpty())
    }
}
