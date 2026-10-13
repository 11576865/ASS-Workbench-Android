package io.github.assworkbench.domain

/** Scope is resolved to stable IDs before recipe filters; an empty scope stays empty. */
enum class AssBatchScope {
    SELECTED, RESULTS, SELECTED_RESULTS, DOCUMENT;

    companion object {
        fun default(hasSelection: Boolean): AssBatchScope = if (hasSelection) SELECTED else DOCUMENT
    }

    fun eventIds(events: List<AssEvent>, selectedIds: Set<Long>, filter: SubtitleFilterState): List<Long> {
        val results = if (this == RESULTS || this == SELECTED_RESULTS) {
            filter.apply(events, selectedIds).mapTo(hashSetOf()) { it.id }
        } else emptySet()
        return events.filter { event ->
            when (this) {
                SELECTED -> event.id in selectedIds
                RESULTS -> event.id in results
                SELECTED_RESULTS -> event.id in selectedIds && event.id in results
                DOCUMENT -> true
            }
        }.map { it.id }
    }
}

/** Immutable reviewed transaction: canonical baseline, revision and session must still match at commit. */
class AssBatchReview private constructor(
    val baseline: AssDocument,
    val preview: AssBatchPreview,
    private val sessionId: Long,
    private val documentRevision: Long,
) {
    private val beforeById = baseline.events.associateBy { it.id }
    private val afterById = preview.document.events.associateBy { it.id }
    val pageCount: Int get() = (preview.changedEventIds.size + PAGE_SIZE - 1) / PAGE_SIZE

    fun page(index: Int): List<Pair<AssEvent, AssEvent>> {
        if (index < 0 || index >= pageCount) return emptyList()
        val start = index * PAGE_SIZE
        return preview.changedEventIds.subList(start, minOf(start + PAGE_SIZE, preview.changedEventIds.size)).map { id ->
            beforeById.getValue(id) to afterById.getValue(id)
        }
    }

    fun commit(document: AssDocument, sessionId: Long = 0L, documentRevision: Long = 0L): AssDocument {
        require(sessionId == this.sessionId && documentRevision == this.documentRevision && document == baseline) { "批处理预览已过期，请重新预检。" }
        return preview.document
    }

    companion object {
        const val PAGE_SIZE = 5
        fun search(document: AssDocument, query: AssSearchQuery, replacement: AssSearchReplacement,
            eventIds: Set<Long>, sessionId: Long = 0L, documentRevision: Long = 0L): AssBatchReview {
            val scoped = document.copy(events = document.events.filter { it.id in eventIds })
            val result = AssSearchReplace.preview(scoped, query, replacement)
            val updates = result.document.events.associateBy { it.id }
            val updated = document.copy(events = document.events.map { updates[it.id] ?: it })
            return AssBatchReview(document, AssBatchPreview(updated, result.hits.map { it.eventId }, result.changedEventIds), sessionId, documentRevision)
        }

        fun create(document: AssDocument, recipe: AssBatchRecipe, sessionId: Long = 0L, documentRevision: Long = 0L): AssBatchReview =
            AssBatchReview(document, AssBatchEngine.preview(document, recipe), sessionId, documentRevision)
    }
}
