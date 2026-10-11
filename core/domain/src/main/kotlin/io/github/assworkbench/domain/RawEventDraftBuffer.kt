package io.github.assworkbench.domain

/** A presentation-independent, uncommitted Event Text buffer. */
data class RawEventDraftBuffer(
    val baseText: String,
    val text: String,
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
) {
    fun classify(canonicalText: String): RawEventDraftState =
        RawEventDraftPolicy.classify(baseText, text, canonicalText)

    fun reconcile(canonicalText: String): RawEventDraftBuffer = when {
        text == canonicalText -> copy(baseText = canonicalText)
        text == baseText -> RawEventDraftBuffer(
            canonicalText, canonicalText,
            selectionStart.coerceIn(0, canonicalText.length),
            selectionEnd.coerceIn(0, canonicalText.length),
        )
        else -> this // Never replace a dirty buffer on another writer's behalf.
    }
}

data class EventTextDraftKey(val owner: String, val eventId: Long) {
    companion object {
        fun owner(instanceId: String?): String = when (instanceId) {
            null, "TEXT:primary", "EVENT:primary", "EFFECTS:primary" -> "primary-event-editor"
            else -> instanceId
        }
    }
}

/** Flat records preserve arbitrary Raw ASS without delimiter-based serialization. */
data class RawEventDraftSnapshot(
    val sessionId: Long,
    val drafts: Map<EventTextDraftKey, RawEventDraftBuffer> = emptyMap(),
) {
    fun retainEvents(existingEventIds: Set<Long>): RawEventDraftSnapshot =
        copy(drafts = drafts.filterKeys { it.eventId in existingEventIds })

    // Primary sections intentionally share a session buffer. Independent
    // instance IDs are disposable and may be reused by WorkspaceState.
    fun withoutInstance(instanceId: String): RawEventDraftSnapshot =
        copy(drafts = drafts.filterKeys { it.owner != instanceId })

    fun save(): List<String> = buildList {
        add("event-text-drafts-v1")
        add(sessionId.toString())
        drafts.forEach { (key, buffer) ->
            addAll(listOf(key.owner, key.eventId.toString(), buffer.baseText, buffer.text,
                buffer.selectionStart.toString(), buffer.selectionEnd.toString()))
        }
    }

    companion object {
        fun restore(values: List<String>, sessionId: Long): RawEventDraftSnapshot {
            if (values.getOrNull(0) != "event-text-drafts-v1" ||
                values.getOrNull(1)?.toLongOrNull() != sessionId) return RawEventDraftSnapshot(sessionId)
            val drafts = linkedMapOf<EventTextDraftKey, RawEventDraftBuffer>()
            values.drop(2).chunked(6).forEach { fields ->
                if (fields.size != 6 || fields[0].isBlank()) return@forEach
                val eventId = fields[1].toLongOrNull() ?: return@forEach
                val start = fields[4].toIntOrNull() ?: return@forEach
                val end = fields[5].toIntOrNull() ?: return@forEach
                drafts[EventTextDraftKey(fields[0], eventId)] = RawEventDraftBuffer(
                    fields[2], fields[3], start.coerceIn(0, fields[3].length), end.coerceIn(0, fields[3].length))
            }
            return RawEventDraftSnapshot(sessionId, drafts)
        }
    }
}
