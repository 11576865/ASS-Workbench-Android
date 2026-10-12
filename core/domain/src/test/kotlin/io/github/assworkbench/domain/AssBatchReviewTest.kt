package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssBatchReviewTest {
    private val events = (1L..12L).map { AssEvent(it, start = SubTime(0), end = SubTime(1000), text = if (it % 2L == 0L) "hit" else "other") }
    private val doc = AssDocument(events = events)
    @Test fun defaultScopePreservesExistingSelectionOrWholeDocumentIntent() {
        assertEquals(AssBatchScope.DOCUMENT, AssBatchScope.default(hasSelection = false))
        assertEquals(AssBatchScope.SELECTED, AssBatchScope.default(hasSelection = true))
    }
    @Test fun emptySelectionNeverFallsBackToDocument() {
        assertEquals(emptyList(), AssBatchScope.SELECTED.eventIds(events, emptySet(), SubtitleFilterState()))
    }
    @Test fun resultScopeUsesSharedFilterAndDocumentOrder() {
        assertEquals(listOf(2L, 4L, 6L, 8L, 10L, 12L), AssBatchScope.RESULTS.eventIds(events, emptySet(), SubtitleFilterState(text = "hit")))
    }
    @Test fun intersectionExcludesSelectedOutsideResults() {
        assertEquals(listOf(4L), AssBatchScope.SELECTED_RESULTS.eventIds(events, setOf(3, 4, 999), SubtitleFilterState(text = "hit")))
        assertEquals(events.map { it.id }, AssBatchScope.DOCUMENT.eventIds(events, emptySet(), SubtitleFilterState(text = "absent")))
    }
    @Test fun reviewPagesIncludeEveryChange() {
        val recipe = AssBatchRecipe("review", actions = listOf(AssBatchAction.SetActor("Alice")))
        val review = AssBatchReview.create(doc, recipe)
        assertEquals(3, review.pageCount)
        assertEquals(events.map { it.id }, (0 until review.pageCount).flatMap { review.page(it).map { pair -> pair.first.id } })
        assertEquals("Alice", review.page(2).last().second.name)
        assertEquals(emptyList(), review.page(3))
    }
    @Test fun semanticSearchUsesScopeWithoutDroppingOtherEventsOrOverrideTags() {
        val document = doc.copy(events = events.map { it.copy(text = "{\\bord2}hit") })
        val review = AssBatchReview.search(document, AssSearchQuery(visiblePattern = Regex("hit")),
            AssSearchReplacement(AssReplaceScope.VISIBLE_TEXT, Regex("hit"), "new"), setOf(2L), 7)
        assertEquals(listOf(2L), review.preview.changedEventIds)
        assertEquals("{\\bord2}new", review.commit(document, 7).events[1].text)
        assertEquals(document.events[0], review.commit(document, 7).events[0])
        assertEquals(document.events[1] to document.events[1].copy(text = "{\\bord2}new"), review.page(0).single())
    }
    @Test fun emptySemanticSearchScopeNeverExpands() {
        val review = AssBatchReview.search(doc, AssSearchQuery(),
            AssSearchReplacement(AssReplaceScope.VISIBLE_TEXT, Regex("hit"), "new"), emptySet())
        assertEquals(emptyList(), review.preview.changedEventIds)
        assertEquals(doc, review.commit(doc))
    }
    @Test fun differentSessionCannotCommitAnIdenticalDocument() {
        val review = AssBatchReview.create(doc, AssBatchRecipe("review", actions = listOf(AssBatchAction.SetActor("Alice"))), sessionId = 3)
        assertFailsWith<IllegalArgumentException> { review.commit(doc, sessionId = 4) }
    }
    @Test fun changedDocumentCannotCommitAnOldPreview() {
        val review = AssBatchReview.create(doc, AssBatchRecipe("review", actions = listOf(AssBatchAction.SetActor("Alice"))))
        assertFailsWith<IllegalArgumentException> { review.commit(doc.copy(events = events.drop(1))) }
        assertEquals("Alice", review.commit(doc).events.first().name)
        assertEquals("", doc.events.first().name)
    }
    @Test fun undoToIdenticalContentDoesNotReauthorizeOldReview() {
        val history = UndoHistory(doc)
        val review = AssBatchReview.create(doc,
            AssBatchRecipe("review", actions = listOf(AssBatchAction.SetActor("Alice"))),
            sessionId = 3, documentRevision = 11)
        val changed = history.commit(doc.copy(events = events.drop(1)))
        val revisionAfterEdit = AssDocumentRevision.advance(11, doc, changed)
        val restored = history.undo()
        val revisionAfterUndo = AssDocumentRevision.advance(revisionAfterEdit, changed, restored)
        assertEquals(doc, restored)
        assertEquals(13L, revisionAfterUndo)
        assertFailsWith<IllegalArgumentException> { review.commit(restored, 3, documentRevision = revisionAfterUndo) }
        assertEquals("Alice", review.commit(doc, 3, documentRevision = 11).events.first().name)
    }
    @Test fun searchReviewAlsoRequiresTheSameDocumentRevision() {
        val review = AssBatchReview.search(doc, AssSearchQuery(),
            AssSearchReplacement(AssReplaceScope.VISIBLE_TEXT, Regex("hit"), "new"), setOf(2L),
            sessionId = 3, documentRevision = 11)
        assertFailsWith<IllegalArgumentException> { review.commit(doc, 3, documentRevision = 12) }
        assertEquals("new", review.commit(doc, 3, documentRevision = 11).events[1].text)
    }

    @Test fun unchangedDocumentDoesNotInvalidateReview() {
        val revision = AssDocumentRevision.advance(11, doc, doc.copy())
        val review = AssBatchReview.create(doc,
            AssBatchRecipe("review", actions = listOf(AssBatchAction.SetActor("Alice"))), documentRevision = 11)
        assertEquals(11L, revision)
        assertEquals("Alice", review.commit(doc, documentRevision = revision).events.first().name)
    }

}
