package io.github.assworkbench.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.assworkbench.domain.*

internal class EventTimingDraftEntry(buffer: EventTimingDraftBuffer) {
    var values by mutableStateOf(buffer.values)
    var baseline by mutableStateOf(buffer.baseline)
    fun snapshot() = EventTimingDraftBuffer(baseline, values)
    fun reconcile(canonical: EventTimingCanonical) {
        val next = snapshot().reconcile(canonical)
        baseline = next.baseline
        values = next.values
    }
    fun reset(canonical: EventTimingCanonical) {
        baseline = canonical
        values = canonical.editable()
    }
}

internal class EventTimingDraftStore(val sessionId: Long, snapshot: EventTimingDraftSnapshot = EventTimingDraftSnapshot(sessionId)) {
    private val entries = snapshot.drafts.mapValues { EventTimingDraftEntry(it.value) }.toMutableMap()
    fun obtain(owner: String?, event: AssEvent): EventTimingDraftEntry =
        entries.getOrPut(EventTextDraftKey(EventTextDraftKey.owner(owner), event.id)) {
            EventTimingDraftEntry(EventTimingDraftBuffer(EventTimingCanonical.from(event)))
        }
    fun reconcile(events: List<AssEvent>) {
        val canonical = events.associateBy { it.id }
        entries.keys.retainAll { it.eventId in canonical }
        entries.forEach { (key, entry) -> canonical[key.eventId]?.let { entry.reconcile(EventTimingCanonical.from(it)) } }
    }
    fun removeInstance(instanceId: String) { entries.keys.retainAll { it.owner != instanceId } }
    fun save() = EventTimingDraftSnapshot(sessionId, entries.mapValues { it.value.snapshot() }).save()
}

internal val LocalEventTimingDraftStore = staticCompositionLocalOf<EventTimingDraftStore> {
    error("Event timing requires the project-session draft owner")
}

@Composable
internal fun rememberEventTimingDraftStore(sessionId: Long): EventTimingDraftStore =
    rememberSaveable(sessionId, saver = listSaver(
        save = { it.save() }, restore = { EventTimingDraftStore(sessionId, EventTimingDraftSnapshot.restore(it, sessionId)) },
    )) { EventTimingDraftStore(sessionId) }
