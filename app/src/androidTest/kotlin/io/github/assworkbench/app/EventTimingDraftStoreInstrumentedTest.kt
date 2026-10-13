package io.github.assworkbench.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.EventTimingDraftStore
import io.github.assworkbench.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventTimingDraftStoreInstrumentedTest {
    private val event = AssEvent(id = 1L, start = SubTime(1049), end = SubTime(2000), text = "Line")
    @Test fun actualStoreSharesOwnersAndClearsClosedOrDeletedEntries() {
        val store = EventTimingDraftStore(5)
        val primary = store.obtain("TEXT:primary", event)
        primary.values = EventTimingValues("broken", "3000")
        assertSame(primary, store.obtain(null, event))
        val sibling = store.obtain("sibling", event)
        assertEquals(EventTimingCanonical.from(event).editable(), sibling.values)
        sibling.values = EventTimingValues("1500", "3000")
        store.removeInstance("sibling")
        assertEquals(EventTimingCanonical.from(event).editable(), store.obtain("sibling", event).values)
        store.reconcile(emptyList())
        assertEquals(EventTimingCanonical.from(event).editable(), store.obtain(null, event).values)
    }
    @Test fun restoredDirtyPairKeepsExactBaselineAndResetDoesNotCommit() {
        val store = EventTimingDraftStore(5)
        store.obtain(null, event).values = EventTimingValues("1500", "3000")
        val restored = EventTimingDraftStore(5, EventTimingDraftSnapshot.restore(store.save(), 5))
        val external = event.copy(start = SubTime(1041))
        restored.reconcile(listOf(external))
        val entry = restored.obtain(null, external)
        assertEquals(RawEventDraftState.EXTERNAL_CONFLICT, entry.snapshot().classify(EventTimingCanonical.from(external)))
        assertEquals(EventTimingValues("1500", "3000"), entry.values)
        entry.reset(EventTimingCanonical.from(external))
        assertEquals(RawEventDraftState.CLEAN, entry.snapshot().classify(EventTimingCanonical.from(external)))
        assertEquals(1041L, entry.baseline.startMillis)
    }
}
