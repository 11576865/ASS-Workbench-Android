package io.github.assworkbench.domain

/** Event margins are independent ASS fields. Zero delegates to the Style margin. */
data class EventMarginValues(val left: String, val right: String, val vertical: String) {
    companion object {
        fun from(event: AssEvent) = EventMarginValues(
            event.marginL.toString(), event.marginR.toString(), event.marginV.toString())
    }

    fun parsed(): Triple<Int, Int, Int>? {
        fun valid(value: String) = value.toIntOrNull()?.takeIf { it in 0..9999 }
        val l = valid(left) ?: return null
        val r = valid(right) ?: return null
        val v = valid(vertical) ?: return null
        return Triple(l, r, v)
    }
}

data class EventMarginDraftBuffer(
    val baseline: EventMarginValues,
    val values: EventMarginValues = baseline,
) {
    fun classify(canonical: EventMarginValues): RawEventDraftState = when {
        values == canonical -> RawEventDraftState.CLEAN
        baseline == canonical -> RawEventDraftState.DRAFT_ONLY
        else -> RawEventDraftState.EXTERNAL_CONFLICT
    }

    fun reconcile(canonical: EventMarginValues): EventMarginDraftBuffer = when {
        values == canonical || values == baseline -> EventMarginDraftBuffer(canonical)
        else -> this
    }
}

/** Same Event/owner identity as text and timing, but a separate saveable draft ledger. */
data class EventMarginDraftSnapshot(
    val sessionId: Long,
    val drafts: Map<EventTextDraftKey, EventMarginDraftBuffer> = emptyMap(),
) {
    fun withoutInstance(instanceId: String) =
        copy(drafts = drafts.filterKeys { it.owner != instanceId })

    fun save(): List<String> = buildList {
        add("event-margin-drafts-v1")
        add(sessionId.toString())
        drafts.forEach { (key, draft) ->
            if (draft.values != draft.baseline) {
                addAll(listOf(key.owner, key.eventId.toString(),
                    draft.baseline.left, draft.baseline.right, draft.baseline.vertical,
                    draft.values.left, draft.values.right, draft.values.vertical))
            }
        }
    }

    companion object {
        fun restore(values: List<String>, sessionId: Long): EventMarginDraftSnapshot {
            if (values.getOrNull(0) != "event-margin-drafts-v1" ||
                values.getOrNull(1)?.toLongOrNull() != sessionId ||
                (values.size - 2) % 8 != 0) return EventMarginDraftSnapshot(sessionId)
            val drafts = linkedMapOf<EventTextDraftKey, EventMarginDraftBuffer>()
            values.drop(2).chunked(8).forEach { fields ->
                val id = fields[1].toLongOrNull() ?: return@forEach
                if (fields[0].isBlank()) return@forEach
                val key = EventTextDraftKey(fields[0], id)
                if (key in drafts) return EventMarginDraftSnapshot(sessionId)
                drafts[key] = EventMarginDraftBuffer(
                    EventMarginValues(fields[2], fields[3], fields[4]),
                    EventMarginValues(fields[5], fields[6], fields[7]))
            }
            return EventMarginDraftSnapshot(sessionId, drafts)
        }
    }
}
