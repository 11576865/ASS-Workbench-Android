package io.github.assworkbench.domain

/** Uncommitted inputs belong to a tool instance, independent of its UI host. */
data class BatchAuthoringDraft(
    val values: Map<String, String> = emptyMap(),
    val previews: Map<String, BatchAuthoringPreview> = emptyMap(),
) {
    fun initialize(key: String, default: String): BatchAuthoringDraft =
        if (key in values) this else copy(values = values + (key to default))

    fun edit(key: String, value: String): BatchAuthoringDraft =
        if (values[key] == value) this else copy(values = values + (key to value), previews = emptyMap())

    fun record(channel: String, document: AssDocument, eventIds: Set<Long>, review: AssBatchReview?, error: String?): BatchAuthoringDraft =
        copy(previews = previews + (channel to BatchAuthoringPreview(document, eventIds.toSet(), review, error)))

    fun reconcile(channel: String, document: AssDocument, eventIds: Set<Long>): BatchAuthoringDraft {
        val current = previews[channel] ?: return this
        return if (current.document == document && current.eventIds == eventIds) this
            else copy(previews = previews - channel)
    }
}

data class BatchAuthoringPreview(
    val document: AssDocument,
    val eventIds: Set<Long>,
    val review: AssBatchReview?,
    val error: String?,
)

/** Only literal input records persist. Recovered inputs always require a fresh precheck. */
data class BatchAuthoringDraftSnapshot(val sessionId: Long, val drafts: Map<String, BatchAuthoringDraft> = emptyMap()) {
    fun withoutInstance(instanceId: String) = copy(drafts = drafts - instanceId)
    fun save(): List<String> = buildList {
        add("batch-authoring-drafts-v1")
        add(sessionId.toString())
        drafts.forEach { (owner, draft) -> draft.values.forEach { (key, value) -> addAll(listOf(owner, key, value)) } }
    }
    companion object {
        fun restore(values: List<String>, sessionId: Long): BatchAuthoringDraftSnapshot {
            if (values.getOrNull(0) != "batch-authoring-drafts-v1" || values.getOrNull(1)?.toLongOrNull() != sessionId ||
                (values.size - 2) % 3 != 0) return BatchAuthoringDraftSnapshot(sessionId)
            val drafts = linkedMapOf<String, BatchAuthoringDraft>()
            values.drop(2).chunked(3).forEach { (owner, key, value) ->
                if (owner.isNotBlank() && key.isNotBlank()) drafts[owner] = (drafts[owner] ?: BatchAuthoringDraft()).edit(key, value)
            }
            return BatchAuthoringDraftSnapshot(sessionId, drafts)
        }
    }
}
