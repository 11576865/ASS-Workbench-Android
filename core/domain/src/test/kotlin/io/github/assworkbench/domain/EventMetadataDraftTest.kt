package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventMetadataDraftTest {
    private val original = EventMetadataValues("0", "Actor", false)
    @Test fun cleanDraftFollowsExternalMetadata() {
        val next = original.copy(actor = "Other")
        assertEquals(EventMetadataDraftBuffer(next), EventMetadataDraftBuffer(original).reconcile(next))
    }
    @Test fun dirtyDraftKeepsLiteralInvalidInputOnExternalChange() {
        val dirty = EventMetadataDraftBuffer(original, original.copy(layer = "oops"))
        val next = original.copy(actor = "Other")
        assertEquals(dirty, dirty.reconcile(next))
        assertEquals(RawEventDraftState.EXTERNAL_CONFLICT, dirty.classify(next))
    }
    @Test fun matchingCommittedValuesAcknowledgeBaseline() {
        val values = original.copy(layer = "-3", actor = "New", comment = true)
        val draft = EventMetadataDraftBuffer(original, values).reconcile(values)
        assertEquals(EventMetadataDraftBuffer(values), draft)
        assertEquals(RawEventDraftState.CLEAN, draft.classify(values))
    }
    @Test fun snapshotRoundTripsLiteralValuesAndKeepsOwnersIndependent() {
        val a = EventTextDraftKey(EventTextDraftKey.owner("EVENT:primary"), 1L)
        val b = EventTextDraftKey("independent-event", 1L)
        val snapshot = EventMetadataDraftSnapshot(5, mapOf(a to EventMetadataDraftBuffer(original,
            original.copy(layer = "oops", actor = "x\n|y", comment = true)), b to EventMetadataDraftBuffer(original)))
        assertEquals(snapshot, EventMetadataDraftSnapshot.restore(snapshot.save(), 5))
        assertEquals(setOf(a), snapshot.withoutInstance("independent-event").drafts.keys)
        assertEquals(snapshot, snapshot.withoutInstance("EVENT:primary"))
    }
    @Test fun differentSessionAndDeletedEventsCannotInheritMetadata() {
        val snapshot = EventMetadataDraftSnapshot(5, mapOf(EventTextDraftKey("sibling", 1) to EventMetadataDraftBuffer(original)))
        assertTrue(EventMetadataDraftSnapshot.restore(snapshot.save(), 6).drafts.isEmpty())
        assertTrue(snapshot.retainEvents(emptySet()).drafts.isEmpty())
    }
    @Test fun malformedBooleanRecordIsRejected() {
        val snapshot = EventMetadataDraftSnapshot(5, mapOf(EventTextDraftKey("sibling", 1) to EventMetadataDraftBuffer(original)))
        val malformed = snapshot.save().toMutableList().apply { this[6] = "not-a-boolean" }
        assertTrue(EventMetadataDraftSnapshot.restore(malformed, 5).drafts.isEmpty())
    }
}
