package io.github.assworkbench.app.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.assworkbench.domain.*

internal class BatchAuthoringDraftEntry(initial: BatchAuthoringDraft = BatchAuthoringDraft()) {
    private val pages = mutableMapOf<String, MutableState<Int>>()
    var draft by mutableStateOf(initial)
        private set

    fun text(key: String, default: String = "", invalidateReview: Boolean = true): MutableState<String> {
        draft = draft.initialize(key, default)
        return object : MutableState<String> {
            override var value: String
                get() = draft.values.getValue(key)
                set(value) { draft = if (invalidateReview) draft.edit(key, value) else draft.present(key, value) }
            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
    fun flag(key: String, default: Boolean): MutableState<Boolean> {
        val text = text(key, default.toString())
        return object : MutableState<Boolean> {
            override var value: Boolean
                get() = text.value.toBooleanStrictOrNull() ?: default
                set(value) { text.value = value.toString() }
            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
    fun preview(channel: String, document: AssDocument, eventIds: Set<Long>, documentRevision: Long = 0L): BatchAuthoringPreview? {
        draft = draft.reconcile(channel, document, eventIds, documentRevision)
        return draft.previews[channel]
    }
    fun page(channel: String): MutableState<Int> = pages.getOrPut(channel) { mutableStateOf(0) }
    fun record(channel: String, document: AssDocument, eventIds: Set<Long>, result: Result<AssBatchReview>, documentRevision: Long = 0L) {
        pages[channel]?.value = 0
        draft = draft.record(channel, document, eventIds, result.getOrNull(), result.exceptionOrNull()?.message, documentRevision)
    }
}

internal class BatchAuthoringDraftStore(val sessionId: Long, snapshot: BatchAuthoringDraftSnapshot = BatchAuthoringDraftSnapshot(sessionId)) {
    private val entries = snapshot.drafts.mapValues { BatchAuthoringDraftEntry(it.value) }.toMutableMap()
    fun obtain(instanceId: String): BatchAuthoringDraftEntry = entries.getOrPut(instanceId) { BatchAuthoringDraftEntry() }
    fun removeInstance(instanceId: String) { entries.remove(instanceId) }
    fun reconcile(document: AssDocument, selectedIds: Set<Long>, filter: SubtitleFilterState, documentRevision: Long = 0L) {
        entries.values.forEach { entry ->
            val scope = AssBatchScope.entries.firstOrNull { it.name == entry.draft.values["rule.scope"] } ?: return@forEach
            val ids = scope.eventIds(document.events, selectedIds, filter).toSet()
            entry.preview("rule", document, ids, documentRevision)
            entry.preview("search", document, ids, documentRevision)
        }
    }
    fun save(): List<String> = BatchAuthoringDraftSnapshot(sessionId, entries.mapValues { it.value.draft }).save()
}

internal val LocalBatchAuthoringDraftStore = staticCompositionLocalOf<BatchAuthoringDraftStore> {
    error("Batch authoring requires the project-session draft owner")
}

@Composable
internal fun rememberBatchAuthoringDraftStore(sessionId: Long): BatchAuthoringDraftStore =
    rememberSaveable(sessionId, saver = listSaver(
        save = { it.save() },
        restore = { BatchAuthoringDraftStore(sessionId, BatchAuthoringDraftSnapshot.restore(it, sessionId)) },
    )) { BatchAuthoringDraftStore(sessionId) }
