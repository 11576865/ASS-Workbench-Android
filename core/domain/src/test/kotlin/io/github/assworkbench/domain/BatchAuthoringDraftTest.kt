package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class BatchAuthoringDraftTest {
    private val document = AssDocument(events = listOf(AssEvent(1, start = SubTime(0), end = SubTime(1000), text = "line")))
    private val ids = setOf(1L)
    private fun reviewed() = BatchAuthoringDraft().initialize("actor", "Alice").record("rule", document, ids,
        AssBatchReview.create(document, AssBatchRecipe("rule", actions = listOf(AssBatchAction.SetActor("Alice")))), null)

    @Test fun sameInputsAndTargetsRetainReviewAcrossHostRemount() {
        val draft = reviewed()
        assertSame(draft, draft.reconcile("rule", document, ids))
        assertEquals("Alice", draft.values["actor"])
        assertEquals(listOf(1L), draft.previews.getValue("rule").review?.preview?.changedEventIds)
    }
    @Test fun parameterEditInvalidatesBothPipelinesWithoutCommitting() {
        val draft = reviewed().record("search", document, ids, null, "failed")
        val edited = draft.edit("actor", "Bob")
        assertEquals(emptyMap(), edited.previews)
        assertEquals("Bob", edited.values["actor"])
        assertEquals("line", document.events.single().text)
        assertSame(draft, draft.edit("actor", "Alice"))
    }
    @Test fun documentMutationInvalidatesPreviewButKeepsDraft() {
        val draft = reviewed().reconcile("rule", document.copy(events = emptyList()), ids)
        assertEquals("Alice", draft.values["actor"])
        assertNull(draft.previews["rule"])
    }
    @Test fun changedTargetsRequireAnotherPrecheckEvenAfterRestoringTargets() {
        val draft = reviewed().reconcile("rule", document, emptySet()).reconcile("rule", document, ids)
        assertNull(draft.previews["rule"])
    }
    @Test fun changingPresentationKeepsReviewedParametersAndAuthorization() {
        val draft = reviewed().present("presentation.mode", "search")
        assertEquals("search", draft.values["presentation.mode"])
        assertEquals("Alice", draft.values["actor"])
        assertEquals(listOf(1L), draft.previews.getValue("rule").review?.preview?.changedEventIds)
    }
    @Test fun defaultInitializationDoesNotOverwriteChosenScope() {
        val draft = reviewed().initialize("scope", "DOCUMENT").edit("scope", "SELECTED")
        assertEquals("SELECTED", draft.initialize("scope", "DOCUMENT").values["scope"])
    }
    @Test fun persistenceKeepsLiteralInputsAndOwnersButRequiresNewPrecheck() {
        val snapshot = BatchAuthoringDraftSnapshot(7, mapOf("BATCH:primary" to reviewed().edit("regex", "[\\n{\\bord2}]"), "sibling" to BatchAuthoringDraft().edit("actor", "Bob")))
        val restored = BatchAuthoringDraftSnapshot.restore(snapshot.save(), 7)
        assertEquals(snapshot.drafts.mapValues { it.value.values }, restored.drafts.mapValues { it.value.values })
        assertEquals(emptyMap(), restored.drafts.getValue("BATCH:primary").previews)
    }
    @Test fun differentSessionAndMalformedSnapshotCannotRecoverOtherProjectDraft() {
        val snapshot = BatchAuthoringDraftSnapshot(7, mapOf("BATCH:primary" to reviewed()))
        assertEquals(emptyMap(), BatchAuthoringDraftSnapshot.restore(snapshot.save(), 8).drafts)
        assertEquals(emptyMap(), BatchAuthoringDraftSnapshot.restore(listOf("bad"), 7).drafts)
    }
    @Test fun closedInstanceCannotLeakDraftWhenItsIdIsReused() {
        val snapshot = BatchAuthoringDraftSnapshot(7, mapOf("BATCH:primary" to reviewed(), "sibling" to BatchAuthoringDraft().edit("actor", "Bob")))
        val cleaned = snapshot.withoutInstance("sibling")
        assertNull(cleaned.drafts["sibling"])
        assertEquals("Alice", cleaned.drafts.getValue("BATCH:primary").values["actor"])
    }
    @Test fun skippedIntermediateUiFramesCannotKeepOldPreview() {
        val draft = BatchAuthoringDraft().edit("actor", "Alice").record("rule", document, setOf(1L), reviewed().previews.getValue("rule").review, null,
            documentRevision = 11)
        val reconciled = draft.reconcile("rule", document, setOf(1L), documentRevision = 13)
        assertNull(reconciled.previews["rule"])
        assertEquals("Alice", reconciled.values["actor"])
        assertSame(draft, draft.reconcile("rule", document, setOf(1L), documentRevision = 11))
    }

}
