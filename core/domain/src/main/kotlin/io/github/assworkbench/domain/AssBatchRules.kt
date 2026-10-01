package io.github.assworkbench.domain

sealed interface AssBatchFilter {
    fun matches(event: AssEvent, document: AssDocument): Boolean

    data object All : AssBatchFilter { override fun matches(event: AssEvent, document: AssDocument) = true }
    data class StyleIs(val style: String) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.style == style
    }
    data class ActorContains(val value: String, val ignoreCase: Boolean = true) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.name.contains(value, ignoreCase)
    }
    data class TextContains(val value: String, val ignoreCase: Boolean = true) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = AssInlineSyntax.visibleText(event.text).contains(value, ignoreCase)
    }
    data class LayerIs(val layer: Int) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.layer == layer
    }
    data class EventIds(val ids: Set<Long>) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.id in ids
    }
    data class CommentIs(val comment: Boolean) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.comment == comment
    }
    data class HasTag(val tag: String) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) =
            Regex("""\\${Regex.escape(tag)}(?:[^A-Za-z]|$)""", RegexOption.IGNORE_CASE).containsMatchIn(event.text)
    }
    data class TimeRange(val startMs: Long, val endMs: Long) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = event.end.millis >= startMs && event.start.millis <= endMs
    }
    data class And(val filters: List<AssBatchFilter>) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = filters.all { it.matches(event, document) }
    }
    data class Or(val filters: List<AssBatchFilter>) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = filters.any { it.matches(event, document) }
    }
    data class Not(val filter: AssBatchFilter) : AssBatchFilter {
        override fun matches(event: AssEvent, document: AssDocument) = !filter.matches(event, document)
    }
}

sealed interface AssBatchAction {
    fun apply(event: AssEvent, document: AssDocument): AssEvent

    data class ShiftTime(val deltaMs: Long) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument): AssEvent {
            val minStart = (event.start.millis + deltaMs).coerceAtLeast(0L)
            val duration = event.end.millis - event.start.millis
            return event.copy(start = SubTime(minStart), end = SubTime(minStart + duration))
        }
    }
    data class SetStyle(val style: String) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(style = style)
    }
    data class SetLayer(val layer: Int) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(layer = layer)
    }
    data class SetActor(val actor: String) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(name = actor)
    }
    data class SetComment(val comment: Boolean) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(comment = comment)
    }
    data class ReplacePlainText(val find: String, val replacement: String) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument): AssEvent {
            if (find.isEmpty()) return event
            val (text, changed) = AssDocumentEditing.replacePlainDialogueText(event.text, find, replacement)
            return if (changed) event.copy(text = text) else event
        }
    }
    data class SetMargins(val left: Int? = null, val right: Int? = null, val vertical: Int? = null) : AssBatchAction {
        override fun apply(event: AssEvent, document: AssDocument) = event.copy(
            marginL = left ?: event.marginL, marginR = right ?: event.marginR, marginV = vertical ?: event.marginV)
    }
}

data class AssBatchRecipe(val id: String, val filter: AssBatchFilter = AssBatchFilter.All, val actions: List<AssBatchAction>)
data class AssBatchPreview(val document: AssDocument, val affectedEventIds: List<Long>, val changedEventIds: List<Long>)

object AssBatchEngine {
    fun preview(document: AssDocument, recipe: AssBatchRecipe): AssBatchPreview {
        val affected = mutableListOf<Long>()
        val changed = mutableListOf<Long>()
        val events = document.events.map { original ->
            if (!recipe.filter.matches(original, document)) return@map original
            affected += original.id
            val updated = recipe.actions.fold(original) { event, action -> action.apply(event, document) }
            if (updated != original) changed += original.id
            updated
        }
        return AssBatchPreview(document.copy(events = events), affected, changed)
    }
}
