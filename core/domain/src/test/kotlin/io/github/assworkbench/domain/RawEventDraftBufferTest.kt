package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RawEventDraftBufferTest {
    @Test fun cleanBufferFollowsCanonicalAndClampsSelection() {
        val buffer = RawEventDraftBuffer("long text", "long text", 9, 9)
        assertEquals(RawEventDraftBuffer("new", "new", 3, 3), buffer.reconcile("new"))
    }
    @Test fun dirtyBufferPreservesBaselineAndSelectionOnExternalWrite() {
        val draft = RawEventDraftBuffer("source", "my draft", 2, 5)
        assertEquals(draft, draft.reconcile("other panel"))
        assertEquals(RawEventDraftState.EXTERNAL_CONFLICT, draft.classify("other panel"))
    }
    @Test fun explicitApplyAcknowledgesNewCanonicalWithoutMovingCursor() {
        val draft = RawEventDraftBuffer("source", "my draft", 2, 5)
        assertEquals(RawEventDraftBuffer("my draft", "my draft", 2, 5), draft.reconcile("my draft"))
    }
    @Test fun primaryEditorFamilySharesOwnerAcrossHostsAndSections() {
        assertEquals(EventTextDraftKey.owner(null), EventTextDraftKey.owner("TEXT:primary"))
        assertEquals(EventTextDraftKey.owner("TEXT:primary"), EventTextDraftKey.owner("EFFECTS:primary"))
        assertEquals(EventTextDraftKey.owner("TEXT:primary"), EventTextDraftKey.owner("EVENT:primary"))
    }
    @Test fun siblingInstancesNeverSharePrimaryBuffer() {
        assertNotEquals(EventTextDraftKey.owner("TEXT:2"), EventTextDraftKey.owner("TEXT:primary"))
        assertNotEquals(EventTextDraftKey.owner("TEXT:2"), EventTextDraftKey.owner("TEXT:3"))
    }
    @Test fun savedDraftsRoundTripMultilineAndRejectDifferentSession() {
        val source = RawEventDraftSnapshot(42, mapOf(EventTextDraftKey("TEXT:2", 7) to
            RawEventDraftBuffer("source\n\\pos(1,2)", "草稿\n\u001f", 1, 3)))
        assertEquals(source, RawEventDraftSnapshot.restore(source.save(), 42))
        assertEquals(RawEventDraftSnapshot(43), RawEventDraftSnapshot.restore(source.save(), 43))
    }
    @Test fun closedSiblingOwnerCannotLeakDraftIntoAReusedInstanceId() {
        val primary = EventTextDraftKey(EventTextDraftKey.owner("TEXT:primary"), 7)
        val sibling = EventTextDraftKey(EventTextDraftKey.owner("TEXT:2"), 7)
        val buffer = RawEventDraftBuffer("base", "old sibling draft")
        val snapshot = RawEventDraftSnapshot(42, mapOf(primary to buffer, sibling to buffer))
        assertEquals(mapOf(primary to buffer), snapshot.withoutInstance("TEXT:2").drafts)
        assertEquals(snapshot, snapshot.withoutInstance("TEXT:primary"))
    }
    @Test fun pruneDeletedEventsKeepsOtherInstanceDrafts() {
        val a = EventTextDraftKey("TEXT:2", 7)
        val b = EventTextDraftKey("TEXT:3", 8)
        val buffer = RawEventDraftBuffer("base", "draft", 0, 0)
        assertEquals(mapOf(b to buffer), RawEventDraftSnapshot(42, mapOf(a to buffer, b to buffer)).retainEvents(setOf(8)).drafts)
    }
}
