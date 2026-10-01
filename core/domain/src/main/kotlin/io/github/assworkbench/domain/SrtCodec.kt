package io.github.assworkbench.domain

object SrtCodec {
    private val timing = Regex(
        """^\s*(\d{1,3}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,3}):(\d{2}):(\d{2})[,.](\d{1,3})(?:\s+.*)?$"""
    )

    fun parse(input: String): AssDocument {
        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("""\n[ \t]*\n+"""))
        val events = mutableListOf<AssEvent>()
        var nextId = 1L
        blocks.forEach { raw ->
            val lines = raw.lines().dropWhile { it.isBlank() }
            if (lines.isEmpty()) return@forEach
            val timingIndex = lines.indexOfFirst { timing.matches(it) }
            if (timingIndex < 0) return@forEach
            val match = timing.matchEntire(lines[timingIndex]) ?: return@forEach
            val start = parseTime(match.groupValues.subList(1, 5))
            val end = parseTime(match.groupValues.subList(5, 9))
            if (end < start) return@forEach
            val body = lines.drop(timingIndex + 1).joinToString("\\N")
            events += AssEvent(id = nextId++, start = start, end = end, text = importBasicMarkup(body))
        }
        return AssDocument(events = events)
    }

    fun write(document: AssDocument): String = buildString {
        document.events.filterNot { it.comment }.forEachIndexed { index, event ->
            if (index > 0) append("\r\n")
            append(index + 1).append("\r\n")
            append(formatTime(event.start)).append(" --> ").append(formatTime(event.end)).append("\r\n")
            append(AssInlineSyntax.visibleText(event.text).replace("\\N", "\r\n").replace("\\n", "\r\n"))
            append("\r\n")
        }
    }

    private fun importBasicMarkup(value: String): String = value
        .replace(Regex("""(?i)<i>""")) { "{\\i1}" }
        .replace(Regex("""(?i)</i>""")) { "{\\i0}" }
        .replace(Regex("""(?i)<b>""")) { "{\\b1}" }
        .replace(Regex("""(?i)</b>""")) { "{\\b0}" }
        .replace(Regex("""(?i)<u>""")) { "{\\u1}" }
        .replace(Regex("""(?i)</u>""")) { "{\\u0}" }

    private fun parseTime(parts: List<String>): SubTime {
        val h = parts[0].toLong()
        val m = parts[1].toLong()
        val s = parts[2].toLong()
        val ms = parts[3].padEnd(3, '0').take(3).toLong()
        return SubTime((((h * 60 + m) * 60 + s) * 1000) + ms)
    }

    private fun formatTime(value: SubTime): String {
        val total = value.millis
        val ms = total % 1000
        val totalSeconds = total / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, ms)
    }
}
