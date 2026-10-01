package io.github.assworkbench.domain

data class BatchRule(
    val name: String = "Untitled rule",
    val predicate: BatchPredicate = BatchPredicate(),
    val actions: List<BatchAction> = emptyList(),
)

data class BatchPredicate(
    val styleEquals: String? = null,
    val actorContains: String? = null,
    val textContains: String? = null,
    val requiredTag: String? = null,
    val comment: Boolean? = null,
    val minDurationMs: Long? = null,
    val maxDurationMs: Long? = null,
)

sealed interface BatchAction {
    data class ShiftTime(val deltaMs: Long) : BatchAction
    data class SetStyle(val style: String) : BatchAction
    data class SetLayer(val layer: Int) : BatchAction
    data class SetComment(val comment: Boolean) : BatchAction
    data class ReplacePlainText(val find: String, val replacement: String) : BatchAction
}

data class BatchRulePreview(
    val document: AssDocument,
    val affectedEventIds: Set<Long>,
)

object BatchRuleEngine {
    fun preview(
        document: AssDocument,
        rule: BatchRule,
        scopeEventIds: Set<Long>? = null,
    ): BatchRulePreview {
        val styleNames = document.styles.map { it.name }.toSet()
        rule.actions.filterIsInstance<BatchAction.SetStyle>().forEach {
            require(it.style in styleNames) { "Style 不存在：${it.style}" }
        }

        val affected = linkedSetOf<Long>()
        val events = document.events.map { event ->
            if (scopeEventIds != null && event.id !in scopeEventIds) return@map event
            if (!matches(event, rule.predicate)) return@map event
            affected += event.id
            applyActions(event, rule.actions)
        }
        return BatchRulePreview(document.copy(events = events), affected)
    }

    private fun matches(event: AssEvent, predicate: BatchPredicate): Boolean {
        if (predicate.styleEquals != null && event.style != predicate.styleEquals) return false
        if (predicate.actorContains != null &&
            !event.name.contains(predicate.actorContains, ignoreCase = true)) return false
        if (predicate.textContains != null &&
            !AssInlineSyntax.visibleText(event.text).contains(predicate.textContains, ignoreCase = true)) return false
        if (predicate.requiredTag != null) {
            val needle = predicate.requiredTag.trim().removePrefix("\\").lowercase()
            val has = AssInlineSyntax.analyze(event.text).tokens.any {
                it.kind == AssInlineTokenKind.OVERRIDE_BLOCK &&
                    it.text.lowercase().contains("\\${needle}")
            }
            if (!has) return false
        }
        if (predicate.comment != null && event.comment != predicate.comment) return false
        val duration = event.end.millis - event.start.millis
        if (predicate.minDurationMs != null && duration < predicate.minDurationMs) return false
        if (predicate.maxDurationMs != null && duration > predicate.maxDurationMs) return false
        return true
    }

    private fun applyActions(event: AssEvent, actions: List<BatchAction>): AssEvent {
        var current = event
        actions.forEach { action ->
            current = when (action) {
                is BatchAction.ShiftTime -> {
                    val delta = action.deltaMs
                    val start = (current.start.millis + delta).coerceAtLeast(0L)
                    val duration = current.end.millis - current.start.millis
                    current.copy(start = SubTime(start), end = SubTime(start + duration))
                }
                is BatchAction.SetStyle -> current.copy(style = action.style)
                is BatchAction.SetLayer -> current.copy(layer = action.layer)
                is BatchAction.SetComment -> current.copy(comment = action.comment)
                is BatchAction.ReplacePlainText -> {
                    val (text, changed) = AssDocumentEditing.replacePlainDialogueText(
                        current.text,
                        action.find,
                        action.replacement,
                    )
                    if (changed) current.copy(text = text) else current
                }
            }
        }
        return current
    }
}
