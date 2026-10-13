package io.github.assworkbench.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.assworkbench.domain.EventTextDraftKey
import io.github.assworkbench.domain.RawEventDraftBuffer
import io.github.assworkbench.domain.RawEventDraftSnapshot

internal class EventTextDraftEntry(buffer: RawEventDraftBuffer) {
    var value by mutableStateOf(TextFieldValue(buffer.text, TextRange(buffer.selectionStart, buffer.selectionEnd)))
    var baseText by mutableStateOf(buffer.baseText)

    fun snapshot() = RawEventDraftBuffer(baseText, value.text, value.selection.start, value.selection.end)

    fun reconcile(canonicalText: String) {
        val next = snapshot().reconcile(canonicalText)
        baseText = next.baseText
        if (value.text != next.text) {
            value = TextFieldValue(next.text, TextRange(next.selectionStart, next.selectionEnd))
        }
        // A baseline acknowledgement must not clear the IME composition range.
    }
}

internal class EventTextDraftStore(val sessionId: Long, snapshot: RawEventDraftSnapshot = RawEventDraftSnapshot(sessionId)) {
    private val entries = snapshot.drafts.mapValues { EventTextDraftEntry(it.value) }.toMutableMap()

    fun obtain(owner: String?, eventId: Long, canonicalText: String): EventTextDraftEntry =
        entries.getOrPut(EventTextDraftKey(EventTextDraftKey.owner(owner), eventId)) {
            EventTextDraftEntry(RawEventDraftBuffer(canonicalText, canonicalText))
        }

    fun retainEvents(existingEventIds: Set<Long>) {
        entries.keys.retainAll { it.eventId in existingEventIds }
    }

    fun removeInstance(instanceId: String) {
        val retained = RawEventDraftSnapshot(sessionId, entries.mapValues { it.value.snapshot() })
            .withoutInstance(instanceId).drafts.keys
        entries.keys.retainAll(retained)
    }

    fun save(): List<String> = RawEventDraftSnapshot(sessionId, entries.mapValues { it.value.snapshot() }).save()
}

internal val LocalEventTextDraftStore = staticCompositionLocalOf<EventTextDraftStore> {
    error("Event Text requires the project-session draft owner")
}

@Composable
internal fun rememberEventTextDraftStore(sessionId: Long): EventTextDraftStore =
    rememberSaveable(sessionId, saver = listSaver(
        save = { it.save() },
        restore = { EventTextDraftStore(sessionId, RawEventDraftSnapshot.restore(it, sessionId)) },
    )) { EventTextDraftStore(sessionId) }
