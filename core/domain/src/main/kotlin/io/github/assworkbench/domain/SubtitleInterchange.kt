package io.github.assworkbench.domain

enum class SubtitleInterchangeFormat { SRT, WEBVTT }

object SubRipCodec {
    private val timeLine = Regex("""^\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3}).*$""")

    fun parse(text: String): AssDocument {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(Regex("""\n{2,}"""))
        val events = mutableListOf<AssEvent>()
        var nextId = 1L
        blocks.forEach { raw ->
            val lines = raw.lines().dropWhile { it.isBlank() }
            if (lines.isEmpty()) return@forEach
            val timingIndex = lines.indexOfFirst { timeLine.matches(it) }
            if (timingIndex < 0) return@forEach
            val match = timeLine.matchEntire(lines[timingIndex]) ?: return@forEach
            val start = parseTime(match.groupValues, 1)
            val end = parseTime(match.groupValues, 5)
            if (end < start) return@forEach
            val body = lines.drop(timingIndex + 1)
                .joinToString("\\N") { it }
            events += AssEvent(
                id = nextId++,
                start = SubTime(start),
                end = SubTime(end),
                text = body,
            )
        }
        return AssDocument(events = events)
    }

    fun write(document: AssDocument): String = buildString {
        document.events.filterNot { it.comment }.forEachIndexed { index, event ->
            if (index > 0) append("\n")
            append(index + 1).append('\n')
            append(formatTime(event.start.millis))
                .append(" --> ")
                .append(formatTime(event.end.millis))
                .append('\n')
            append(toPlainInterchangeText(event.text)).append("\n\n")
        }
    }.trimEnd() + "\n"

    private fun parseTime(groups: List<String>, start: Int): Long {
        val h = groups[start].toLong()
        val m = groups[start + 1].toLong()
        val s = groups[start + 2].toLong()
        val ms = groups[start + 3].toLong()
        require(m in 0..59 && s in 0..59)
        return (((h * 60 + m) * 60 + s) * 1000) + ms
    }

    private fun formatTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val millis = safe % 1000
        val totalSeconds = safe / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}

object WebVttCodec {
    private val timeLine = Regex("""^\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})\.(\d{3})(?:\s+.*)?$""")

    fun parse(text: String): AssDocument {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val body = normalized.removePrefix("\uFEFF").lines()
            .dropWhile { it.isBlank() || it.trim().equals("WEBVTT", ignoreCase = true) }
            .joinToString("\n")
        val events = mutableListOf<AssEvent>()
        var nextId = 1L
        body.split(Regex("""\n{2,}""")).forEach { raw ->
            val lines = raw.lines().dropWhile { it.isBlank() }
            val timingIndex = lines.indexOfFirst { timeLine.matches(it) }
            if (timingIndex < 0) return@forEach
            val m = timeLine.matchEntire(lines[timingIndex]) ?: return@forEach
            val start = vttTime(m.groupValues, 1)
            val end = vttTime(m.groupValues, 5)
            if (end < start) return@forEach
            events += AssEvent(
                id = nextId++,
                start = SubTime(start),
                end = SubTime(end),
                text = lines.drop(timingIndex + 1).joinToString("\\N"),
            )
        }
        return AssDocument(events = events)
    }

    fun write(document: AssDocument): String = buildString {
        append("WEBVTT\n\n")
        document.events.filterNot { it.comment }.forEach { event ->
            append(formatTime(event.start.millis))
                .append(" --> ")
                .append(formatTime(event.end.millis))
                .append('\n')
            append(toPlainInterchangeText(event.text)).append("\n\n")
        }
    }

    private fun vttTime(groups: List<String>, start: Int): Long {
        val h = groups[start].takeIf { it.isNotBlank() }?.toLong() ?: 0L
        val m = groups[start + 1].toLong()
        val s = groups[start + 2].toLong()
        val ms = groups[start + 3].toLong()
        require(m in 0..59 && s in 0..59)
        return (((h * 60 + m) * 60 + s) * 1000) + ms
    }

    private fun formatTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val millis = safe % 1000
        val totalSeconds = safe / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }
}

private fun toPlainInterchangeText(text: String): String {
    val visible = AssInlineSyntax.visibleText(text)
    return visible
        .replace("\\N", "\n", ignoreCase = true)
        .replace("\\n", "\n", ignoreCase = true)
        .replace("\\h", " ", ignoreCase = true)
}
