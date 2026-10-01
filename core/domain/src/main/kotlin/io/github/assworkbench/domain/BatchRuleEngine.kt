package io.github.assworkbench.domain

data class BatchRule(
    val filters: List<BatchFilter> = emptyList(),
    val actions: List<BatchAction>,
)

sealed interface BatchFilter {
    data class Style(val name: String) : BatchFilter
    data class Actor(val name: String) : BatchFilter
    data class Layer(val value: Int) : BatchFilter
    data class TimeRange(val startMs: Long, val endMs: Long) : BatchFilter
    data class TextContains(val value: String, val ignoreCase: Boolean = true) : BatchFilter
    data class TextRegex(val pattern: String) : BatchFilter
    data class HasTag(val tagName: String) : BatchFilter
    data class EventIds(val ids: Set<Long>) : BatchFilter
}

sealed interface BatchAction {
    data class ShiftTime(val deltaMs: Long) : BatchAction
    data class SetStyle(val name: String) : BatchAction
    data class SetLayer(val value: Int) : BatchAction
    data class ReplacePlainText(val find: String, val replacement: String) : BatchAction
    data class SetMargins(val left: Int? = null, val right: Int? = null, val vertical: Int? = null) : BatchAction
}

data class BatchPreview(
    val affectedEventIds: Set<Long>,
    val document: AssDocument,
)

object BatchRuleEngine {
    fun preview(document: AssDocument, rule: BatchRule): BatchPreview {
        val ids = document.events.asSequence().filter { event ->
            rule.filters.all { filter -> matches(filter, event) }
        }.map { it.id }.toSet()

        val styles = document.styles.mapTo(hashSetOf()) { it.name }
        var next = document
        rule.actions.forEach { action ->
            next = next.copy(events = next.events.map { event ->
                if (event.id !in ids) event else apply(action, event, styles)
            })
        }
        return BatchPreview(ids, next)
    }

    private fun matches(filter: BatchFilter, event: AssEvent): Boolean = when (filter) {
        is BatchFilter.Style -> event.style == filter.name
        is BatchFilter.Actor -> event.name == filter.name
        is BatchFilter.Layer -> event.layer == filter.value
        is BatchFilter.TimeRange -> event.end.millis >= filter.startMs && event.start.millis <= filter.endMs
        is BatchFilter.TextContains -> AssInlineSyntax.visibleText(event.text).contains(filter.value, filter.ignoreCase)
        is BatchFilter.TextRegex -> runCatching {
            Regex(filter.pattern).containsMatchIn(AssInlineSyntax.visibleText(event.text))
        }.getOrDefault(false)
        is BatchFilter.HasTag -> Regex("""\\${Regex.escape(filter.tagName)}(?:\b|[0-9(&])""", RegexOption.IGNORE_CASE)
            .containsMatchIn(event.text)
        is BatchFilter.EventIds -> event.id in filter.ids
    }

    private fun apply(action: BatchAction, event: AssEvent, styles: Set<String>): AssEvent = when (action) {
        is BatchAction.ShiftTime -> {
            val start = (event.start.millis + action.deltaMs).coerceAtLeast(0L)
            val duration = event.end.millis - event.start.millis
            event.copy(start = SubTime(start), end = SubTime(start + duration))
        }
        is BatchAction.SetStyle -> if (action.name in styles) event.copy(style = action.name) else event
        is BatchAction.SetLayer -> event.copy(layer = action.value)
        is BatchAction.ReplacePlainText -> {
            val (text, _) = AssDocumentEditing.replacePlainDialogueText(event.text, action.find, action.replacement)
            event.copy(text = text)
        }
        is BatchAction.SetMargins -> event.copy(
            marginL = action.left?.coerceIn(0, 9999) ?: event.marginL,
            marginR = action.right?.coerceIn(0, 9999) ?: event.marginR,
            marginV = action.vertical?.coerceIn(0, 9999) ?: event.marginV,
        )
    }
}
