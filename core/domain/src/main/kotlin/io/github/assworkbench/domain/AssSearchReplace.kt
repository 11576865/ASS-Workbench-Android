package io.github.assworkbench.domain

data class AssSearchQuery(
    val visiblePattern: Regex? = null,
    val rawPattern: Regex? = null,
    val stylePattern: Regex? = null,
    val actorPattern: Regex? = null,
    val requiredTags: Set<String> = emptySet(),
    val forbiddenTags: Set<String> = emptySet(),
    val layerRange: IntRange? = null,
    val durationRangeMs: LongRange? = null,
    val timeRangeMs: LongRange? = null,
    val comment: Boolean? = null,
)

enum class AssReplaceScope { VISIBLE_TEXT, RAW_EVENT_TEXT, STYLE, ACTOR }

data class AssSearchReplacement(val scope: AssReplaceScope, val pattern: Regex, val replacement: String)
data class AssSearchHit(val eventId: Long, val visibleText: String, val startMs: Long, val endMs: Long)
data class AssSearchReplacePreview(val document: AssDocument, val hits: List<AssSearchHit>, val changedEventIds: List<Long>)

object AssSearchReplace {
    fun matches(event: AssEvent, query: AssSearchQuery): Boolean {
        val duration = (event.end.millis - event.start.millis).coerceAtLeast(0L)
        val visible = AssInlineSyntax.visibleText(event.text)
        if (query.visiblePattern?.containsMatchIn(visible) == false) return false
        if (query.rawPattern?.containsMatchIn(event.text) == false) return false
        if (query.stylePattern?.containsMatchIn(event.style) == false) return false
        if (query.actorPattern?.containsMatchIn(event.name) == false) return false
        if (query.layerRange != null && event.layer !in query.layerRange) return false
        if (query.durationRangeMs != null && duration !in query.durationRangeMs) return false
        if (query.timeRangeMs != null && (event.end.millis < query.timeRangeMs.first || event.start.millis > query.timeRangeMs.last)) return false
        if (query.comment != null && event.comment != query.comment) return false
        val tags = topLevelTagNames(event.text)
        if (!query.requiredTags.all { wanted -> tags.any { it.equals(wanted.removePrefix("\\"), true) } }) return false
        if (query.forbiddenTags.any { wanted -> tags.any { it.equals(wanted.removePrefix("\\"), true) } }) return false
        return true
    }

    fun preview(document: AssDocument, query: AssSearchQuery, replacement: AssSearchReplacement? = null): AssSearchReplacePreview {
        val hits = mutableListOf<AssSearchHit>()
        val changed = mutableListOf<Long>()
        val events = document.events.map { event ->
            if (!matches(event, query)) return@map event
            hits += AssSearchHit(event.id, AssInlineSyntax.visibleText(event.text), event.start.millis, event.end.millis)
            if (replacement == null) return@map event
            val updated = when (replacement.scope) {
                AssReplaceScope.VISIBLE_TEXT -> event.copy(text = replaceVisibleSegments(event.text, replacement.pattern, replacement.replacement))
                AssReplaceScope.RAW_EVENT_TEXT -> event.copy(text = replacement.pattern.replace(event.text, replacement.replacement))
                AssReplaceScope.STYLE -> event.copy(style = replacement.pattern.replace(event.style, replacement.replacement))
                AssReplaceScope.ACTOR -> event.copy(name = replacement.pattern.replace(event.name, replacement.replacement))
            }
            if (updated != event) changed += event.id
            updated
        }
        return AssSearchReplacePreview(document.copy(events = events), hits, changed)
    }

    fun replaceVisibleSegments(text: String, pattern: Regex, replacement: String): String {
        val out = StringBuilder(text.length)
        var cursor = 0
        var plainStart = 0
        while (cursor < text.length) {
            if (text[cursor] != '{') { cursor++; continue }
            if (plainStart < cursor) out.append(pattern.replace(text.substring(plainStart, cursor), replacement))
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) { out.append(pattern.replace(text.substring(cursor), replacement)); return out.toString() }
            out.append(text, cursor, close + 1)
            cursor = close + 1
            plainStart = cursor
        }
        if (plainStart < text.length) out.append(pattern.replace(text.substring(plainStart), replacement))
        return out.toString()
    }

    fun topLevelTagNames(text: String): Set<String> =
        AssTopLevelOverrideSyntax.tags(text)
            .mapTo(linkedSetOf()) { it.name }
}
