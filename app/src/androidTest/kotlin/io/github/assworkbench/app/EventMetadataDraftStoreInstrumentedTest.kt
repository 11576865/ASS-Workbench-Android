package io.github.assworkbench.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.EventMetadataDraftStore
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.EventMetadataDraftSnapshot
import io.github.assworkbench.domain.EventMetadataValues
import io.github.assworkbench.domain.SubTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventMetadataDraftStoreInstrumentedTest {
    private val event = AssEvent(id = 1L, start = SubTime(1000), end = SubTime(2000), text = "Line")

    @Test fun sharedEntryIndependentEntryAndCloseCleanupUseActualStore() {
        val store = EventMetadataDraftStore(5)
        val primary = store.obtain("EVENT:primary", event)
        primary.values = primary.values.copy(actor = "Local", layer = "oops")
        assertSame(primary, store.obtain(null, event))
        assertSame(primary, store.obtain("TEXT:primary", event))
        val sibling = store.obtain("sibling", event)
        assertEquals(EventMetadataValues.from(event), sibling.values)
        sibling.values = sibling.values.copy(actor = "Sibling")
        store.removeInstance("sibling")
        assertEquals(EventMetadataValues.from(event), store.obtain("sibling", event).values)
        assertEquals("Local", primary.values.actor)
        store.reconcile(emptyList())
        assertEquals(EventMetadataValues.from(event), store.obtain(null, event).values)
    }

    @Test fun savedLiteralValuesRestoreAndDirtyExternalChangesAreRetained() {
        val store = EventMetadataDraftStore(5)
        store.obtain(null, event).values = EventMetadataValues("oops", "Local", true)
        val restored = EventMetadataDraftStore(5, EventMetadataDraftSnapshot.restore(store.save(), 5))
        restored.reconcile(listOf(event.copy(name = "External")))
        val draft = restored.obtain("EVENT:primary", event)
        assertEquals(EventMetadataValues("oops", "Local", true), draft.values)
        draft.reset(EventMetadataValues.from(event))
        restored.reconcile(listOf(event.copy(name = "External")))
        assertEquals("External", draft.values.actor)
    }
}
