package io.github.assworkbench.domain

data class EventTimingValues(val start: String, val end: String)

/** Baselines retain exact milliseconds, not the rounded ASS display strings. */
data class EventTimingCanonical(val startMillis: Long, val endMillis: Long) {
    fun editable() = EventTimingValues(SubTime(startMillis).toAss(), SubTime(endMillis).toAss())
    companion object {
        fun from(event: AssEvent) = EventTimingCanonical(event.start.millis, event.end.millis)
    }
}

data class EventTimingDraftBuffer(val baseline: EventTimingCanonical, val values: EventTimingValues = baseline.editable()) {
    private fun matches(canonical: EventTimingCanonical): Boolean {
        val parsed = EventTimingInputValidation.parse(values.start, values.end)
        return parsed.start?.millis == canonical.startMillis && parsed.end?.millis == canonical.endMillis
    }
    fun classify(canonical: EventTimingCanonical): RawEventDraftState = when {
        (baseline == canonical && values == baseline.editable()) || matches(canonical) -> RawEventDraftState.CLEAN
        baseline == canonical -> RawEventDraftState.DRAFT_ONLY
        else -> RawEventDraftState.EXTERNAL_CONFLICT
    }
    fun reconcile(canonical: EventTimingCanonical): EventTimingDraftBuffer = when {
        canonical == baseline -> this // Host remount is not a formatting command.
        values == baseline.editable() || matches(baseline) -> EventTimingDraftBuffer(canonical)
        matches(canonical) -> copy(baseline = canonical) // Acknowledge without rewriting literal input.
        else -> this
    }
}

data class EventTimingDraftSnapshot(val sessionId: Long, val drafts: Map<EventTextDraftKey, EventTimingDraftBuffer> = emptyMap()) {
    fun retainEvents(ids: Set<Long>) = copy(drafts = drafts.filterKeys { it.eventId in ids })
    fun withoutInstance(instanceId: String) = copy(drafts = drafts.filterKeys { it.owner != instanceId })
    fun save(): List<String> = buildList {
        add("event-timing-drafts-v1")
        add(sessionId.toString())
        drafts.forEach { (key, draft) ->
            addAll(listOf(key.owner, key.eventId.toString(), draft.baseline.startMillis.toString(),
                draft.baseline.endMillis.toString(), draft.values.start, draft.values.end))
        }
    }
    companion object {
        fun restore(values: List<String>, sessionId: Long): EventTimingDraftSnapshot {
            if (values.getOrNull(0) != "event-timing-drafts-v1" || values.getOrNull(1)?.toLongOrNull() != sessionId)
                return EventTimingDraftSnapshot(sessionId)
            val drafts = linkedMapOf<EventTextDraftKey, EventTimingDraftBuffer>()
            values.drop(2).chunked(6).forEach { fields ->
                if (fields.size != 6 || fields[0].isBlank()) return@forEach
                val id = fields[1].toLongOrNull() ?: return@forEach
                val start = fields[2].toLongOrNull()?.takeIf { it >= 0L } ?: return@forEach
                val end = fields[3].toLongOrNull()?.takeIf { it >= 0L } ?: return@forEach
                drafts[EventTextDraftKey(fields[0], id)] = EventTimingDraftBuffer(
                    EventTimingCanonical(start, end), EventTimingValues(fields[4], fields[5]))
            }
            return EventTimingDraftSnapshot(sessionId, drafts)
        }
    }
}
