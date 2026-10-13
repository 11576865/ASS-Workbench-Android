package io.github.assworkbench.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.assworkbench.domain.*

internal class EventMetadataDraftEntry(buffer: EventMetadataDraftBuffer) {
    var values by mutableStateOf(buffer.values)
    var baseline by mutableStateOf(buffer.baseline)
    fun snapshot() = EventMetadataDraftBuffer(baseline, values)
    fun reconcile(canonical: EventMetadataValues) {
        val next = snapshot().reconcile(canonical)
        baseline = next.baseline
        values = next.values
    }
    fun reset(canonical: EventMetadataValues) {
        baseline = canonical
        values = canonical
    }
}

internal class EventMetadataDraftStore(val sessionId: Long, snapshot: EventMetadataDraftSnapshot = EventMetadataDraftSnapshot(sessionId)) {
    private val entries = snapshot.drafts.mapValues { EventMetadataDraftEntry(it.value) }.toMutableMap()
    fun obtain(owner: String?, event: AssEvent): EventMetadataDraftEntry =
        entries.getOrPut(EventTextDraftKey(EventTextDraftKey.owner(owner), event.id)) {
            EventMetadataDraftEntry(EventMetadataDraftBuffer(EventMetadataValues.from(event)))
        }
    fun reconcile(events: List<AssEvent>) {
        val canonical = events.associateBy { it.id }
        entries.keys.retainAll { it.eventId in canonical }
        entries.forEach { (key, entry) -> canonical[key.eventId]?.let { entry.reconcile(EventMetadataValues.from(it)) } }
    }
    fun removeInstance(instanceId: String) { entries.keys.retainAll { it.owner != instanceId } }
    fun save() = EventMetadataDraftSnapshot(sessionId, entries.mapValues { it.value.snapshot() }).save()
}

internal val LocalEventMetadataDraftStore = staticCompositionLocalOf<EventMetadataDraftStore> {
    error("Event metadata requires the project-session draft owner")
}

@Composable
internal fun rememberEventMetadataDraftStore(sessionId: Long): EventMetadataDraftStore =
    rememberSaveable(sessionId, saver = listSaver(
        save = { it.save() }, restore = { EventMetadataDraftStore(sessionId, EventMetadataDraftSnapshot.restore(it, sessionId)) },
    )) { EventMetadataDraftStore(sessionId) }
