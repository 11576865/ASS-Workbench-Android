package io.github.assworkbench.domain

enum class SubtitleEventKind { ALL, DIALOGUE, COMMENT }

/** Presentation-only literal filters; never change canonical ASS or its event IDs. */
data class SubtitleFilterState(
    val quickText: String = "",
    val text: String = "",
    val actor: String = "",
    val style: String = "",
    val kind: SubtitleEventKind = SubtitleEventKind.ALL,
    val selectedOnly: Boolean = false,
) {
    val isActive: Boolean get() = quickText.isNotBlank() || text.isNotBlank() ||
        actor.isNotBlank() || style.isNotBlank() || kind != SubtitleEventKind.ALL || selectedOnly

    fun apply(events: List<AssEvent>, selectedIds: Set<Long> = emptySet()): List<AssEvent> = events.filter { event ->
        if (selectedOnly && event.id !in selectedIds) return@filter false
        if (kind == SubtitleEventKind.COMMENT && !event.comment) return@filter false
        if (kind == SubtitleEventKind.DIALOGUE && event.comment) return@filter false
        val visible = AssInlineSyntax.visibleText(event.text)
        (quickText.isBlank() || visible.contains(quickText, true) || event.name.contains(quickText, true) || event.style.contains(quickText, true)) &&
            (text.isBlank() || visible.contains(text, true)) &&
            (actor.isBlank() || event.name.contains(actor, true)) &&
            (style.isBlank() || event.style.contains(style, true))
    }

    fun relativeTarget(events: List<AssEvent>, selectedIds: Set<Long>, focusId: Long?, direction: Int): Long? {
        val results = apply(events, selectedIds)
        if (results.isEmpty()) return null
        val index = results.indexOfFirst { it.id == focusId }
        val next = when {
            index < 0 && direction >= 0 -> 0
            index < 0 -> results.lastIndex
            else -> (index + direction).coerceIn(0, results.lastIndex)
        }
        return results[next].id
    }

    fun save(): List<String> = listOf(quickText, text, actor, style, kind.name, selectedOnly.toString())

    companion object {
        fun restore(values: List<String>?): SubtitleFilterState {
            if (values == null || values.size != 6) return SubtitleFilterState()
            val kind = SubtitleEventKind.entries.firstOrNull { it.name == values[4] } ?: return SubtitleFilterState()
            val selectedOnly = values[5].toBooleanStrictOrNull() ?: return SubtitleFilterState()
            return SubtitleFilterState(values[0], values[1], values[2], values[3], kind, selectedOnly)
        }
    }
}
