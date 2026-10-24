package io.github.assworkbench.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.assworkbench.domain.*

internal class EventMarginDraftEntry(buffer: EventMarginDraftBuffer) {
    var baseline by mutableStateOf(buffer.baseline)
    var values by mutableStateOf(buffer.values)

    fun snapshot() = EventMarginDraftBuffer(baseline, values)

    fun reconcile(canonical: EventMarginValues) {
        val next = snapshot().reconcile(canonical)
        baseline = next.baseline
        values = next.values
    }

    fun reset(canonical: EventMarginValues) {
        baseline = canonical
        values = canonical
    }
}

internal class EventMarginDraftStore(
    val sessionId: Long,
    snapshot: EventMarginDraftSnapshot = EventMarginDraftSnapshot(sessionId),
) {
    private val entries = snapshot.drafts.mapValues { EventMarginDraftEntry(it.value) }.toMutableMap()

    fun obtain(owner: String?, event: AssEvent): EventMarginDraftEntry =
        entries.getOrPut(EventTextDraftKey(EventTextDraftKey.owner(owner), event.id)) {
            EventMarginDraftEntry(EventMarginDraftBuffer(EventMarginValues.from(event)))
        }

    fun reconcile(events: List<AssEvent>) {
        val canonical = events.associateBy { it.id }
        entries.keys.retainAll { it.eventId in canonical }
        entries.forEach { (key, entry) ->
            canonical[key.eventId]?.let { entry.reconcile(EventMarginValues.from(it)) }
        }
    }

    fun removeInstance(instanceId: String) {
        entries.keys.retainAll { it.owner != instanceId }
    }

    fun save() = EventMarginDraftSnapshot(sessionId, entries.mapValues { it.value.snapshot() }).save()
}

internal val LocalEventMarginDraftStore = staticCompositionLocalOf<EventMarginDraftStore> {
    error("Event margins require the project-session draft owner")
}

@Composable
internal fun rememberEventMarginDraftStore(sessionId: Long): EventMarginDraftStore =
    rememberSaveable(sessionId, saver = listSaver(
        save = { it.save() },
        restore = { EventMarginDraftStore(sessionId, EventMarginDraftSnapshot.restore(it, sessionId)) },
    )) { EventMarginDraftStore(sessionId) }
