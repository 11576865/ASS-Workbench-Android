package io.github.assworkbench.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.assworkbench.domain.AssTransform

/** Exact strings: typing a fractional ASS time must never be rounded by storage. */
internal data class TransformDraftFields(
    val start: String,
    val end: String,
    val accel: String,
    val tags: String,
) {
    companion object {
        fun from(transform: AssTransform) = TransformDraftFields(
            transform.startMs?.toString().orEmpty(),
            transform.endMs?.toString().orEmpty(),
            transform.accel?.toString().orEmpty(),
            transform.tags,
        )
    }
}

internal data class TransformDraftKey(val eventId: Long, val transformIndex: Int)

/**
 * Source-based ownership: a transform index alone is insufficient after an
 * external insert/reorder. Comparing the complete Event source fails closed.
 */
internal class TransformDraftEntry(
    baseSource: String,
    baseFields: TransformDraftFields,
    draft: TransformDraftFields = baseFields,
) {
    var baseSource by mutableStateOf(baseSource)
        private set
    var baseFields by mutableStateOf(baseFields)
        private set
    var fields by mutableStateOf(draft)

    val dirty: Boolean get() = fields != baseFields

    fun conflict(currentSource: String): Boolean = dirty && baseSource != currentSource

    fun reconcile(currentSource: String, current: TransformDraftFields) {
        if (baseSource == currentSource) return
        // Preserve dirty input and its original write target on external changes.
        if (dirty) return
        baseSource = currentSource
        baseFields = current
        fields = current
    }

    /** Explicitly discard local changes and accept the source currently on disk/in memory. */
    fun reset(currentSource: String, current: TransformDraftFields) {
        baseSource = currentSource
        baseFields = current
        fields = current
    }

    /**
     * Only invoked after a successful native ViewModel document transaction.
     * This is not an ASS writer; the ViewModel remains the sole write authority.
     */
    fun acknowledgeCommittedSource(source: String, committed: TransformDraftFields) {
        reset(source, committed)
    }
}

internal class TransformDraftStore(
    val sessionId: Long,
    restored: Map<TransformDraftKey, TransformDraftEntry> = emptyMap(),
) {
    private val entries = restored.toMutableMap()

    fun obtain(eventId: Long, index: Int, source: String, transform: AssTransform): TransformDraftEntry =
        entries.getOrPut(TransformDraftKey(eventId, index)) {
            TransformDraftEntry(source, TransformDraftFields.from(transform))
        }

    fun hasDirtyInEvent(eventId: Long): Boolean =
        entries.any { (key, entry) -> key.eventId == eventId && entry.dirty }

    fun retainEvents(ids: Set<Long>) {
        entries.keys.retainAll { it.eventId in ids }
    }

    // Each string remains an independent saveable element (no delimiter collision
    // with arbitrary ASS text); a record is 11 elements after the header.
    fun save(): List<String> = buildList {
        add("v1")
        add(sessionId.toString())
        entries.forEach { (key, entry) ->
            add(key.eventId.toString())
            add(key.transformIndex.toString())
            add(entry.baseSource)
            add(entry.baseFields.start)
            add(entry.baseFields.end)
            add(entry.baseFields.accel)
            add(entry.baseFields.tags)
            add(entry.fields.start)
            add(entry.fields.end)
            add(entry.fields.accel)
            add(entry.fields.tags)
        }
    }

    companion object {
        fun restore(sessionId: Long, values: List<String>): TransformDraftStore {
            if (values.size < 2 || values[0] != "v1" || values[1].toLongOrNull() != sessionId ||
                (values.size - 2) % 11 != 0
            ) return TransformDraftStore(sessionId)
            val restored = mutableMapOf<TransformDraftKey, TransformDraftEntry>()
            for (i in 2 until values.size step 11) {
                val id = values[i].toLongOrNull() ?: return TransformDraftStore(sessionId)
                val index = values[i + 1].toIntOrNull() ?: return TransformDraftStore(sessionId)
                if (index < 0) return TransformDraftStore(sessionId)
                val base = TransformDraftFields(values[i + 3], values[i + 4], values[i + 5], values[i + 6])
                val draft = TransformDraftFields(values[i + 7], values[i + 8], values[i + 9], values[i + 10])
                restored[TransformDraftKey(id, index)] = TransformDraftEntry(values[i + 2], base, draft)
            }
            return TransformDraftStore(sessionId, restored)
        }
    }
}

internal val LocalTransformDraftStore = staticCompositionLocalOf<TransformDraftStore> {
    error("Transform editing requires the project-session draft store")
}

@Composable
internal fun rememberTransformDraftStore(sessionId: Long): TransformDraftStore =
    rememberSaveable(sessionId, saver = listSaver(
        save = { it.save() },
        restore = { TransformDraftStore.restore(sessionId, it) },
    )) { TransformDraftStore(sessionId) }
