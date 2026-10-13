package io.github.assworkbench.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFxDraftStoreTest {
    private val original = "{\\pos(200,300)\\fad(120,180)\\blur2}字幕"
    private val changed = "{\\pos(300,400)\\fad(120,180)\\blur2}字幕"

    @Test fun draftsSurviveNavigationAndRemainIndependentBetweenEvents() {
        val store = EventFxDraftStore(41L)
        val event39 = store.obtain(39L, original)
        event39.fields = event39.fields.copy(fadeIn = "250", blur = "3.125")
        val event40 = store.obtain(40L, "{\\fad(80,90)}B")
        event40.fields = event40.fields.copy(fadeOut = "150")
        assertTrue(event39 === store.obtain(39L, original))
        assertEquals("250", store.obtain(39L, original).fields.fadeIn)
        assertEquals("3.125", store.obtain(39L, original).fields.blur)
        assertEquals("150", store.obtain(40L, "{\\fad(80,90)}B").fields.fadeOut)
    }

    @Test fun externalSourceChangeBlocksDraftAndPreservesExactText() {
        val entry = EventFxDraftStore(41L).obtain(39L, original)
        entry.fields = entry.fields.copy(fadeMode = "fade", t1 = "150.5")
        entry.reconcile(changed)
        assertTrue(entry.conflict(changed))
        assertEquals(original, entry.baseSource)
        assertEquals("150.5", entry.fields.t1)
        entry.discard(changed)
        assertFalse(entry.conflict(changed))
        assertFalse(entry.dirty)
        assertEquals("120", entry.fields.fadeIn)
    }

    @Test fun cleanDraftRebasesAndCommittedFadeKeepsDirtyBlur() {
        val store = EventFxDraftStore(41L)
        val entry = store.obtain(39L, original)
        entry.reconcile(changed)
        assertFalse(entry.dirty)
        assertEquals(changed, entry.baseSource)
        entry.fields = entry.fields.copy(fadeIn = "300", blur = "7.25")
        val newFade = "{\\pos(300,400)\\fad(300,180)\\blur2}字幕"
        entry.acknowledge(newFade, appliedBlur = false)
        assertEquals(newFade, entry.baseSource)
        assertFalse(entry.fadeDirty)
        assertTrue(entry.blurDirty)
        assertEquals("7.25", entry.fields.blur)
        assertFalse(entry.conflict(newFade))
    }

    @Test fun committedBlurKeepsDirtyFadeWhileUpdatingItsOwnBaseline() {
        val entry = EventFxDraftStore(41L).obtain(39L, original)
        entry.fields = entry.fields.copy(blur = "4.5", fadeOut = "420")
        val changedBlur = "{\\pos(200,300)\\fad(120,180)\\blur4.5}字幕"
        entry.acknowledge(changedBlur, appliedBlur = true)
        assertFalse(entry.blurDirty)
        assertTrue(entry.fadeDirty)
        assertEquals("420", entry.fields.fadeOut)
        assertFalse(entry.conflict(changedBlur))
    }

    @Test fun saveRestoreRetainsUnknownRawAndSessionIdentity() {
        val store = EventFxDraftStore(41L)
        val unusual = "{\\x-unknown(foo)\\fad(10,20)}漢字"
        val entry = store.obtain(12L, unusual)
        entry.fields = entry.fields.copy(fadeIn = "22", a1 = "0")
        val saved = store.save()
        assertEquals(16, saved.size)
        val restored = EventFxDraftStore.restore(41L, saved).obtain(12L, unusual)
        assertEquals(unusual, restored.baseSource)
        assertEquals("22", restored.fields.fadeIn)
        assertTrue(restored.dirty)
        assertFalse(EventFxDraftStore.restore(42L, saved).obtain(12L, unusual).dirty)
        assertFalse(EventFxDraftStore.restore(41L, listOf("v1", "41", "x"))
            .obtain(12L, unusual).dirty)
    }

    @Test fun removingEventDoesNotRestoreItsOldDraft() {
        val store = EventFxDraftStore(41L)
        val a = store.obtain(39L, original)
        a.fields = a.fields.copy(blur = "18")
        val b = store.obtain(40L, changed)
        b.fields = b.fields.copy(fadeIn = "400")
        store.retainEvents(setOf(40L))
        assertFalse(store.obtain(39L, original).dirty)
        assertEquals("400", store.obtain(40L, changed).fields.fadeIn)
    }
}
