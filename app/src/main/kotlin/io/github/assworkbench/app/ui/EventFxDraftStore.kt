package io.github.assworkbench.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.assworkbench.domain.AssAnimationSemantic
import io.github.assworkbench.domain.EventOverrideEditor

/** Raw numeric strings are retained exactly until native authoring accepts a transaction. */
internal data class EventFxFields(
    val blur: String,
    val softEntry: Boolean,
    val fadeMode: String,
    val fadeIn: String,
    val fadeOut: String,
    val a1: String,
    val a2: String,
    val a3: String,
    val t1: String,
    val t2: String,
    val t3: String,
    val t4: String,
) {
    fun blurFieldsEqual(other: EventFxFields): Boolean =
        blur == other.blur && softEntry == other.softEntry

    fun fadeFieldsEqual(other: EventFxFields): Boolean =
        fadeMode == other.fadeMode && fadeIn == other.fadeIn && fadeOut == other.fadeOut &&
            a1 == other.a1 && a2 == other.a2 && a3 == other.a3 &&
            t1 == other.t1 && t2 == other.t2 && t3 == other.t3 && t4 == other.t4

    fun withBlurFrom(other: EventFxFields): EventFxFields =
        copy(blur = other.blur, softEntry = other.softEntry)

    fun withFadeFrom(other: EventFxFields): EventFxFields =
        copy(fadeMode = other.fadeMode, fadeIn = other.fadeIn, fadeOut = other.fadeOut,
            a1 = other.a1, a2 = other.a2, a3 = other.a3,
            t1 = other.t1, t2 = other.t2, t3 = other.t3, t4 = other.t4)

    fun save(): List<String> = listOf(
        blur, softEntry.toString(), fadeMode, fadeIn, fadeOut,
        a1, a2, a3, t1, t2, t3, t4,
    )

    companion object {
        const val WIDTH = 12

        fun fromSource(source: String): EventFxFields {
            val visual = EventOverrideEditor.inspect(source)
            val fade = AssAnimationSemantic.inspect(source)
            val complex = fade.complexFade
            return EventFxFields(
                blur = visual.blur?.toString().orEmpty(),
                softEntry = visual.softEntry,
                fadeMode = when {
                    complex != null -> "fade"
                    fade.simpleFade != null -> "fad"
                    else -> "none"
                },
                fadeIn = (fade.simpleFade?.fadeInMs ?: 150).toString(),
                fadeOut = (fade.simpleFade?.fadeOutMs ?: 150).toString(),
                a1 = (complex?.alpha1 ?: 255).toString(),
                a2 = (complex?.alpha2 ?: 0).toString(),
                a3 = (complex?.alpha3 ?: 255).toString(),
                t1 = (complex?.time1Ms ?: 0).toString(),
                t2 = (complex?.time2Ms ?: 150).toString(),
                t3 = (complex?.time3Ms ?: 850).toString(),
                t4 = (complex?.time4Ms ?: 1000).toString(),
            )
        }

        fun restore(values: List<String>): EventFxFields? {
            if (values.size != WIDTH) return null
            val soft = values[1].toBooleanStrictOrNull() ?: return null
            if (values[2] !in setOf("none", "fad", "fade")) return null
            return EventFxFields(values[0], soft, values[2], values[3], values[4],
                values[5], values[6], values[7], values[8], values[9], values[10], values[11])
        }
    }
}

internal class EventFxDraftEntry(
    baseSource: String,
    baseFields: EventFxFields,
    fields: EventFxFields = baseFields,
) {
    var baseSource by mutableStateOf(baseSource)
        private set
    var baseFields by mutableStateOf(baseFields)
        private set
    var fields by mutableStateOf(fields)

    val dirty: Boolean get() = fields != baseFields
    val blurDirty: Boolean get() = !fields.blurFieldsEqual(baseFields)
    val fadeDirty: Boolean get() = !fields.fadeFieldsEqual(baseFields)

    fun conflict(source: String): Boolean = dirty && baseSource != source

    fun reconcile(source: String) {
        if (source == baseSource || dirty) return
        baseSource = source
        baseFields = EventFxFields.fromSource(source)
        fields = baseFields
    }

    fun discard(source: String) {
        baseSource = source
        baseFields = EventFxFields.fromSource(source)
        fields = baseFields
    }

    /** Rebases a successful edit but preserves an unfinished draft in the other group. */
    fun acknowledge(source: String, appliedBlur: Boolean) {
        val updated = EventFxFields.fromSource(source)
        val previous = fields
        baseSource = source
        baseFields = updated
        fields = if (appliedBlur) updated.withFadeFrom(previous)
            else updated.withBlurFrom(previous)
    }
}

internal class EventFxDraftStore(
    val sessionId: Long,
    restored: Map<Long, EventFxDraftEntry> = emptyMap(),
) {
    private val entries = restored.toMutableMap()

    fun obtain(eventId: Long, source: String): EventFxDraftEntry =
        entries.getOrPut(eventId) {
            EventFxDraftEntry(source, EventFxFields.fromSource(source))
        }

    fun retainEvents(ids: Set<Long>) {
        entries.keys.retainAll(ids)
    }

    // Persist only dirty entries. Source remains intact, including unknown extensions.
    fun save(): List<String> = buildList {
        add("v1")
        add(sessionId.toString())
        entries.forEach { (id, entry) ->
            if (entry.dirty) {
                add(id.toString())
                add(entry.baseSource)
                addAll(entry.fields.save())
            }
        }
    }

    companion object {
        fun restore(sessionId: Long, values: List<String>): EventFxDraftStore {
            val width = EventFxFields.WIDTH + 2
            if (values.size < 2 || values[0] != "v1" ||
                values[1].toLongOrNull() != sessionId ||
                (values.size - 2) % width != 0
            ) return EventFxDraftStore(sessionId)
            val drafts = mutableMapOf<Long, EventFxDraftEntry>()
            for (i in 2 until values.size step width) {
                val eventId = values[i].toLongOrNull() ?: return EventFxDraftStore(sessionId)
                val source = values[i + 1]
                val fields = EventFxFields.restore(values.subList(i + 2, i + width))
                    ?: return EventFxDraftStore(sessionId)
                if (eventId in drafts) return EventFxDraftStore(sessionId)
                drafts[eventId] = EventFxDraftEntry(source, EventFxFields.fromSource(source), fields)
            }
            return EventFxDraftStore(sessionId, drafts)
        }
    }
}

internal val LocalEventFxDraftStore = staticCompositionLocalOf<EventFxDraftStore> {
    error("Effects editing requires the project-session draft store")
}

@Composable
internal fun rememberEventFxDraftStore(sessionId: Long): EventFxDraftStore =
    rememberSaveable(sessionId, saver = listSaver(
        save = { it.save() },
        restore = { EventFxDraftStore.restore(sessionId, it) },
    )) { EventFxDraftStore(sessionId) }
