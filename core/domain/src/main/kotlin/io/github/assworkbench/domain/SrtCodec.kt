package io.github.assworkbench.domain

/**
 * Loss-minimising SRT import adapter. ASS remains the canonical editing model.
 */
object SrtCodec {
    private val timing = Regex(
        """^\s*(\d{1,3}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,3}):(\d{2}):(\d{2})[,.](\d{3})(?:\s+.*)?$"""
    )

    fun parse(source: String): AssDocument {
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n').trimStart('\uFEFF')
        val blocks = normalized.split(Regex("""\n\s*\n"""))
        val events = mutableListOf<AssEvent>()
        var nextId = 1L

        for (block in blocks) {
            val lines = block.lines()
            if (lines.isEmpty()) continue
            val timingIndex = lines.indexOfFirst { timing.matches(it) }
            if (timingIndex < 0) continue
            val match = timing.matchEntire(lines[timingIndex]) ?: continue
            val start = parseTime(match, 1)
            val end = parseTime(match, 5)
            if (end < start) continue
            val text = lines.drop(timingIndex + 1).joinToString("\\N") { importInlineMarkup(it) }
            events += AssEvent(
                id = nextId++,
                start = start,
                end = end,
                style = "Default",
                text = text,
            )
        }
        return AssDocument(events = events)
    }

    fun write(document: AssDocument): String = buildString {
        document.events
            .filterNot { it.comment }
            .sortedWith(compareBy<AssEvent> { it.start.millis }.thenBy { it.end.millis }.thenBy { it.id })
            .forEachIndexed { index, event ->
                if (index > 0) appendLine()
                appendLine(index + 1)
                append(formatTime(event.start)).append(" --> ").appendLine(formatTime(event.end))
                appendLine(exportPlainText(event.text))
            }
    }.trimEnd() + "\n"

    private fun parseTime(match: MatchResult, offset: Int): SubTime {
        val hours = match.groupValues[offset].toLong()
        val minutes = match.groupValues[offset + 1].toLong()
        val seconds = match.groupValues[offset + 2].toLong()
        val millis = match.groupValues[offset + 3].toLong()
        return SubTime((((hours * 60 + minutes) * 60 + seconds) * 1000) + millis)
    }

    private fun formatTime(time: SubTime): String {
        val total = time.millis
        val ms = total % 1000
        val totalSeconds = total / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, ms)
    }

    private fun importInlineMarkup(line: String): String =
        line.replace(Regex("""(?i)<i>"""), "{\\i1}")
            .replace(Regex("""(?i)</i>"""), "{\\i0}")
            .replace(Regex("""(?i)<b>"""), "{\\b1}")
            .replace(Regex("""(?i)</b>"""), "{\\b0}")
            .replace(Regex("""(?i)<u>"""), "{\\u1}")
            .replace(Regex("""(?i)</u>"""), "{\\u0}")

    private fun exportPlainText(text: String): String =
        AssInlineSyntax.visibleText(text).replace("\\N", "\n").replace("\\n", "\n")
}
