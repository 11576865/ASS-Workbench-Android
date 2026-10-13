package io.github.assworkbench.app.ui

import io.github.assworkbench.domain.AssTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformDraftStoreTest {
    private fun transform(start: Double = 120.0, end: Double = 780.0, tags: String = "\\fscx140") =
        AssTransform(startMs = start, endMs = end, tags = tags, rawValue = "(120,780,\\fscx140)")

    @Test fun uncommittedFieldsSurviveEventAndHostNavigation() {
        val store = TransformDraftStore(9L)
        val source = "{\\t(120,780,\\fscx140)}Hello"
        val first = store.obtain(39L, 0, source, transform())
        first.fields = first.fields.copy(start = "120.5", tags = "\\fscx205\\x-custom(foo)")
        store.obtain(40L, 0, "{\\t(0,300,\\fscx100)}World", transform(0.0, 300.0))
        val again = store.obtain(39L, 0, source, transform())
        assertTrue(first === again)
        assertEquals("120.5", again.fields.start)
        assertEquals("\\fscx205\\x-custom(foo)", again.fields.tags)
        assertTrue(again.dirty)
        assertFalse(again.conflict(source))
    }

    @Test fun externalChangeMustRetainDraftAndBlockWriteUntilExplicitReload() {
        val source = "{\\t(120,780,\\fscx140)}Hello"
        val changed = "{\\pos(100,200)\\t(120,780,\\fscx140)}Hello"
        val entry = TransformDraftStore(9L).obtain(39L, 0, source, transform())
        entry.fields = entry.fields.copy(end = "700")
        entry.reconcile(changed, TransformDraftFields.from(transform()))
        assertTrue(entry.conflict(changed))
        assertEquals("700", entry.fields.end)
        assertEquals(source, entry.baseSource)
        entry.reset(changed, TransformDraftFields.from(transform()))
        assertFalse(entry.conflict(changed))
        assertFalse(entry.dirty)
        assertEquals("780.0", entry.fields.end)
    }

    @Test fun cleanDraftRebasesWhenCanonicalTextChanges() {
        val entry = TransformDraftStore(9L).obtain(39L, 0, "A", transform())
        entry.reconcile("B", TransformDraftFields.from(transform(250.0, 850.0)))
        assertEquals("B", entry.baseSource)
        assertEquals("250.0", entry.fields.start)
        assertFalse(entry.dirty)
    }

    @Test fun saveRestoreKeepsUnknownTagsAndProtectsAgainstDifferentSessions() {
        val store = TransformDraftStore(9L)
        val entry = store.obtain(39L, 1, "{\\t(120,780,\\x-custom(foo)\\fscx140)}你好", transform())
        entry.fields = entry.fields.copy(tags = "\\x-custom(foo)\\fscx205", start = "120.5")
        val restored = TransformDraftStore.restore(9L, store.save())
        val same = restored.obtain(39L, 1, "ignored", transform())
        assertTrue(same.dirty)
        assertEquals("120.5", same.fields.start)
        assertEquals("\\x-custom(foo)\\fscx205", same.fields.tags)
        assertEquals("你好", same.baseSource.substringAfter('}'))
        val isolated = TransformDraftStore.restore(10L, store.save())
        assertFalse(isolated.obtain(39L, 1, "fresh", transform()).dirty)
    }

    @Test fun removeEventPrunesOwnedDraftsWithoutRemovingOtherEvents() {
        val store = TransformDraftStore(9L)
        val a = store.obtain(39, 0, "a", transform())
        val b = store.obtain(40, 0, "b", transform())
        a.fields = a.fields.copy(start = "99")
        b.fields = b.fields.copy(start = "88")
        store.retainEvents(setOf(40))
        assertEquals(1, (store.save().size - 2) / 11)
        assertEquals("88", store.obtain(40, 0, "b", transform()).fields.start)
        assertFalse(store.obtain(39, 0, "a", transform()).dirty)
    }

    @Test fun corruptedOrIncompleteSnapshotsFailClosed() {
        val store = TransformDraftStore.restore(9L, listOf("v1", "9", "39"))
        assertFalse(store.obtain(39, 0, "original", transform()).dirty)
        val olderVersion = TransformDraftStore.restore(9L, listOf("v0", "9"))
        assertFalse(olderVersion.obtain(39, 0, "original", transform()).dirty)
    }
}
