package io.github.assworkbench.domain

data class EventMetadataValues(val layer: String, val actor: String, val comment: Boolean) {
    companion object {
        fun from(event: AssEvent) = EventMetadataValues(event.layer.toString(), event.name, event.comment)
    }
}

data class EventMetadataDraftBuffer(val baseline: EventMetadataValues, val values: EventMetadataValues = baseline) {
    fun classify(canonical: EventMetadataValues): RawEventDraftState = when {
        values == canonical -> RawEventDraftState.CLEAN
        baseline == canonical -> RawEventDraftState.DRAFT_ONLY
        else -> RawEventDraftState.EXTERNAL_CONFLICT
    }
    fun reconcile(canonical: EventMetadataValues): EventMetadataDraftBuffer = when {
        values == canonical || values == baseline -> EventMetadataDraftBuffer(canonical)
        else -> this
    }
}

/** Same owner identity as Event Text, stored independently of text buffers. */
data class EventMetadataDraftSnapshot(val sessionId: Long, val drafts: Map<EventTextDraftKey, EventMetadataDraftBuffer> = emptyMap()) {
    fun retainEvents(ids: Set<Long>) = copy(drafts = drafts.filterKeys { it.eventId in ids })
    fun withoutInstance(instanceId: String) = copy(drafts = drafts.filterKeys { it.owner != instanceId })
    fun save(): List<String> = buildList {
        add("event-metadata-drafts-v1")
        add(sessionId.toString())
        drafts.forEach { (key, draft) ->
            addAll(listOf(key.owner, key.eventId.toString(), draft.baseline.layer, draft.baseline.actor,
                draft.baseline.comment.toString(), draft.values.layer, draft.values.actor, draft.values.comment.toString()))
        }
    }
    companion object {
        fun restore(values: List<String>, sessionId: Long): EventMetadataDraftSnapshot {
            if (values.getOrNull(0) != "event-metadata-drafts-v1" || values.getOrNull(1)?.toLongOrNull() != sessionId)
                return EventMetadataDraftSnapshot(sessionId)
            val drafts = linkedMapOf<EventTextDraftKey, EventMetadataDraftBuffer>()
            values.drop(2).chunked(8).forEach { fields ->
                if (fields.size != 8 || fields[0].isBlank()) return@forEach
                val id = fields[1].toLongOrNull() ?: return@forEach
                val baseComment = fields[4].toBooleanStrictOrNull() ?: return@forEach
                val comment = fields[7].toBooleanStrictOrNull() ?: return@forEach
                drafts[EventTextDraftKey(fields[0], id)] = EventMetadataDraftBuffer(
                    EventMetadataValues(fields[2], fields[3], baseComment), EventMetadataValues(fields[5], fields[6], comment))
            }
            return EventMetadataDraftSnapshot(sessionId, drafts)
        }
    }
}
