package io.github.assworkbench.domain

data class AssStructuralEditResult(
    val document: AssDocument,
    val focusedEventId: Long?,
    val selectedEventIds: Set<Long> = focusedEventId?.let(::setOf).orEmpty(),
)

object AssDocumentEditing {
    fun insertAtPlayback(
        document: AssDocument,
        afterEventId: Long?,
        positionMs: Long,
        defaultDurationMs: Long = 2_000L,
    ): AssStructuralEditResult {
        val startMs = positionMs.coerceAtLeast(0L)
        val template = afterEventId?.let { id -> document.events.firstOrNull { it.id == id } }
        val newId = nextEventId(document)
        val event = AssEvent(
            id = newId,
            layer = template?.layer ?: 0,
            start = SubTime(startMs),
            end = SubTime(startMs + defaultDurationMs.coerceAtLeast(100L)),
            style = template?.style ?: document.styles.firstOrNull()?.name.orEmpty().ifBlank { "Default" },
            name = template?.name.orEmpty(),
            text = "",
        )
        val events = document.events.toMutableList()
        val focusedIndex = afterEventId?.let { id -> events.indexOfFirst { it.id == id } } ?: -1
        if (focusedIndex >= 0) {
            events.add(focusedIndex + 1, event)
        } else {
            val chronological = events.indexOfFirst { it.start.millis > startMs }
            if (chronological >= 0) events.add(chronological, event) else events.add(event)
        }
        return AssStructuralEditResult(document.copy(events = events), newId)
    }
    fun insertAdjacent(
        document: AssDocument,
        eventId: Long,
        before: Boolean,
        defaultDurationMs: Long = 2_000L,
    ): AssStructuralEditResult {
        val index = document.events.indexOfFirst { it.id == eventId }
        require(index >= 0) { "当前字幕不存在。" }
        val source = document.events[index]
        val duration = defaultDurationMs.coerceAtLeast(100L)
        var startMs: Long
        var endMs: Long
        if (before) {
            endMs = source.start.millis
            startMs = (endMs - duration).coerceAtLeast(0L)
            if (endMs - startMs < 100L) {
                // Document order is authoritative for insert-before; at time zero there is
                // no legal negative interval, so overlap rather than creating a zero-length Event.
                startMs = source.start.millis
                endMs = startMs + duration
            }
        } else {
            startMs = source.end.millis
            endMs = startMs + duration
        }
        val newId = nextEventId(document)
        val inserted = source.copy(
            id = newId,
            start = SubTime(startMs),
            end = SubTime(endMs),
            text = "",
        )
        val events = document.events.toMutableList().apply {
            add(if (before) index else index + 1, inserted)
        }
        return AssStructuralEditResult(document.copy(events = events), newId)
    }

    fun duplicateEvent(document: AssDocument, eventId: Long): AssStructuralEditResult {
        val index = document.events.indexOfFirst { it.id == eventId }
        require(index >= 0) { "当前字幕不存在。" }
        val newId = nextEventId(document)
        val duplicate = document.events[index].copy(id = newId)
        val events = document.events.toMutableList().apply { add(index + 1, duplicate) }
        return AssStructuralEditResult(document.copy(events = events), newId)
    }

    fun mergeAdjacent(
        document: AssDocument,
        eventId: Long,
        previous: Boolean,
        separator: String = "\\N",
    ): AssStructuralEditResult {
        val index = document.events.indexOfFirst { it.id == eventId }
        require(index >= 0) { "当前字幕不存在。" }
        val neighborIndex = if (previous) index - 1 else index + 1
        require(neighborIndex in document.events.indices) {
            if (previous) "当前字幕已经是第一条，无法与上一条合并。" else "当前字幕已经是最后一条，无法与下一条合并。"
        }
        return mergeEvents(
            document = document,
            eventIds = setOf(eventId, document.events[neighborIndex].id),
            separator = separator,
        )
    }

    fun splitEvent(
        document: AssDocument,
        eventId: Long,
        splitTimeMs: Long,
        textIndex: Int,
    ): AssStructuralEditResult {
        val index = document.events.indexOfFirst { it.id == eventId }
        require(index >= 0) { "当前字幕不存在。" }
        val source = document.events[index]
        require(textIndex in 1 until source.text.length) { "请把文本光标放在要拆分的位置。" }
        require(!cursorInsideOverrideBlock(source.text, textIndex)) { "不能在 ASS override block 内部拆分。" }

        val duration = (source.end.millis - source.start.millis).coerceAtLeast(2L)
        val safeTime = splitTimeMs.coerceIn(source.start.millis + 1L, source.end.millis - 1L)
        require(safeTime > source.start.millis && safeTime < source.end.millis) { "当前字幕时长不足以拆分。" }

        var leftText = source.text.substring(0, textIndex)
        var rightText = source.text.substring(textIndex)
        if (leftText.endsWith("\\N")) leftText = leftText.dropLast(2)
        if (rightText.startsWith("\\N")) rightText = rightText.drop(2)
        require(leftText.isNotBlank() && rightText.isNotBlank()) { "拆分点两侧都需要有文本。" }

        val leading = leadingOverridePrefix(source.text)
        if (leading.isNotEmpty() && !rightText.startsWith("{")) {
            rightText = leading + rightText
        }

        val secondId = nextEventId(document)
        val first = source.copy(end = SubTime(safeTime), text = leftText)
        val second = source.copy(id = secondId, start = SubTime(safeTime), text = rightText)
        val events = document.events.toMutableList().apply {
            this[index] = first
            add(index + 1, second)
        }
        return AssStructuralEditResult(
            document = document.copy(events = events),
            focusedEventId = secondId,
            selectedEventIds = setOf(source.id, secondId),
        )
    }

    fun mergeEvents(
        document: AssDocument,
        eventIds: Set<Long>,
        separator: String,
    ): AssStructuralEditResult {
        require(eventIds.size >= 2) { "至少选择两条字幕才能合并。" }
        val selected = document.events.filter { it.id in eventIds }
        require(selected.size >= 2) { "选择中可合并的字幕不足两条。" }

        val first = selected.first()
        val merged = first.copy(
            start = selected.minBy { it.start.millis }.start,
            end = selected.maxBy { it.end.millis }.end,
            text = selected.joinToString(separator) { it.text },
        )
        val firstIndex = document.events.indexOfFirst { it.id == first.id }
        val events = document.events.filterNot { it.id in eventIds }.toMutableList()
        events.add(firstIndex.coerceIn(0, events.size), merged)
        return AssStructuralEditResult(document.copy(events = events), merged.id)
    }

    fun deleteEvents(document: AssDocument, eventIds: Set<Long>): AssStructuralEditResult {
        if (eventIds.isEmpty()) return AssStructuralEditResult(document, null, emptySet())
        val firstIndex = document.events.indexOfFirst { it.id in eventIds }
        val remaining = document.events.filterNot { it.id in eventIds }
        val focus = when {
            remaining.isEmpty() -> null
            firstIndex < 0 -> remaining.first().id
            firstIndex < remaining.size -> remaining[firstIndex].id
            else -> remaining.last().id
        }
        return AssStructuralEditResult(document.copy(events = remaining), focus)
    }

    fun replacePlainDialogueText(text: String, find: String, replacement: String): Pair<String, Boolean> {
        if (find.isEmpty()) return text to false
        val analysis = AssInlineSyntax.analyze(text)
        val plain = analysis.tokens
            .filter { it.kind == AssInlineTokenKind.TEXT }
            .sortedByDescending { it.start }
        var changed = false
        var result = text
        plain.forEach { token ->
            val replaced = token.text.replace(find, replacement)
            if (replaced != token.text) {
                result = result.substring(0, token.start) + replaced + result.substring(token.endExclusive)
                changed = true
            }
        }
        return result to changed
    }

    fun copyEventFormatting(
        document: AssDocument,
        sourceEventId: Long,
        targetEventIds: Set<Long>,
    ): AssDocument {
        val source = document.events.firstOrNull { it.id == sourceEventId }
            ?: error("格式来源字幕不存在。")
        val prefix = leadingOverridePrefix(source.text)
        return document.copy(events = document.events.map { event ->
            if (event.id !in targetEventIds || event.id == sourceEventId) {
                event
            } else {
                event.copy(
                    style = source.style,
                    marginL = source.marginL,
                    marginR = source.marginR,
                    marginV = source.marginV,
                    text = prefix + stripLeadingOverridePrefix(event.text),
                )
            }
        })
    }

    fun createStyle(document: AssDocument, name: String, copyFrom: String? = null): AssDocument {
        val normalized = validateNewStyleName(document, name)
        val source = copyFrom?.let { sourceName -> document.styles.firstOrNull { it.name == sourceName } }
        val style = source?.copy(name = normalized) ?: AssStyle(name = normalized)
        return document.copy(styles = document.styles + style)
    }

    fun renameStyle(document: AssDocument, oldName: String, newName: String): AssDocument {
        val existing = document.styles.firstOrNull { it.name == oldName }
            ?: error("Style 不存在：$oldName")
        val normalized = newName.trim()
        require(normalized.isNotBlank()) { "Style 名称不能为空。" }
        require(document.styles.none { it.name != oldName && it.name.equals(normalized, ignoreCase = true) }) {
            "Style 名称已存在：$normalized"
        }
        return document.copy(
            styles = document.styles.map { if (it.name == oldName) existing.copy(name = normalized) else it },
            events = document.events.map { if (it.style == oldName) it.copy(style = normalized) else it },
        )
    }

    fun deleteStyle(document: AssDocument, name: String, replacement: String): AssDocument {
        require(document.styles.size > 1) { "至少必须保留一个 Style。" }
        require(name != replacement) { "替代 Style 不能与被删除 Style 相同。" }
        require(document.styles.any { it.name == name }) { "Style 不存在：$name" }
        require(document.styles.any { it.name == replacement }) { "替代 Style 不存在：$replacement" }
        return document.copy(
            styles = document.styles.filterNot { it.name == name },
            events = document.events.map { if (it.style == name) it.copy(style = replacement) else it },
        )
    }

    private fun validateNewStyleName(document: AssDocument, name: String): String {
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "Style 名称不能为空。" }
        require(document.styles.none { it.name.equals(normalized, ignoreCase = true) }) {
            "Style 名称已存在：$normalized"
        }
        return normalized
    }

    private fun nextEventId(document: AssDocument): Long =
        (document.events.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun cursorInsideOverrideBlock(text: String, index: Int): Boolean {
        val open = text.lastIndexOf('{', startIndex = (index - 1).coerceAtLeast(0))
        val close = text.lastIndexOf('}', startIndex = (index - 1).coerceAtLeast(0))
        return open > close
    }

    private fun leadingOverridePrefix(text: String): String {
        var cursor = 0
        val out = StringBuilder()
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) break
            val block = text.substring(cursor, close + 1)
            if (block.indexOf('\\') >= 0) out.append(block)
            cursor = close + 1
        }
        return out.toString()
    }

    private fun stripLeadingOverridePrefix(text: String): String {
        var cursor = 0
        val preservedComments = StringBuilder()
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) break
            val block = text.substring(cursor, close + 1)
            if (block.indexOf('\\') < 0) preservedComments.append(block)
            cursor = close + 1
        }
        return preservedComments.append(text.substring(cursor)).toString()
    }
}
