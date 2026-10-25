package io.github.assworkbench.domain

import kotlin.test.*

class EventMarginDraftTest {
    private val event = AssEvent(id = 42L, start = SubTime(1000), end = SubTime(2000), text = "Raw",
        marginL = 0, marginR = 7, marginV = 10)
    private val canonical = EventMarginValues.from(event)

    @Test fun validatesExplicitZeroAndRejectsInvalidOrOverflowValues() {
        assertEquals(Triple(0, 7, 10), canonical.parsed())
        assertNull(canonical.copy(left = "-1").parsed())
        assertNull(canonical.copy(right = "10000").parsed())
        assertNull(canonical.copy(vertical = "abc").parsed())
        assertNull(canonical.copy(left = "2147483648").parsed())
    }

    @Test fun reconcilePreservesDirtyExternalConflictAndCleansAcceptedDraft() {
        val edited = EventMarginDraftBuffer(canonical, canonical.copy(left = "25"))
        assertEquals(RawEventDraftState.DRAFT_ONLY, edited.classify(canonical))
        assertEquals(RawEventDraftState.EXTERNAL_CONFLICT,
            edited.classify(canonical.copy(right = "8")))
        assertEquals(edited, edited.reconcile(canonical.copy(right = "8")))
        assertEquals(RawEventDraftState.CLEAN,
            edited.reconcile(edited.values).classify(edited.values))
    }

    @Test fun saveRestoreIsSessionScopedAndStoresIndependentOwners() {
        val owner = EventTextDraftKey(EventTextDraftKey.owner("TEXT:primary"), event.id)
        val dirty = EventMarginDraftBuffer(canonical, canonical.copy(vertical = "90"))
        val snapshot = EventMarginDraftSnapshot(5L, mapOf(owner to dirty))
        val saved = snapshot.save()
        assertEquals(snapshot, EventMarginDraftSnapshot.restore(saved, 5L))
        assertTrue(EventMarginDraftSnapshot.restore(saved, 6L).drafts.isEmpty())
        assertTrue(snapshot.withoutInstance(owner.owner).drafts.isEmpty())
        assertTrue(EventMarginDraftSnapshot.restore(saved + listOf("bad"), 5L).drafts.isEmpty())
    }
}
