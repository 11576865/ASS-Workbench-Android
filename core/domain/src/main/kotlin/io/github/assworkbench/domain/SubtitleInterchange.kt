package io.github.assworkbench.domain

enum class SubtitleInterchangeFormat { ASS, SRT, WEBVTT }

object SubtitleInterchange {
    fun detect(fileName: String?, text: String): SubtitleInterchangeFormat {
        val lower = fileName.orEmpty().lowercase()
        return when {
            lower.endsWith(".srt") -> SubtitleInterchangeFormat.SRT
            lower.endsWith(".vtt") -> SubtitleInterchangeFormat.WEBVTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> SubtitleInterchangeFormat.ASS
            text.trimStart().startsWith("WEBVTT") -> SubtitleInterchangeFormat.WEBVTT
            Regex("""(?m)^\s*\d{1,3}:\d{2}:\d{2}[,.]\d{3}\s+-->\s+\d{1,3}:\d{2}:\d{2}[,.]\d{3}""")
                .containsMatchIn(text) -> SubtitleInterchangeFormat.SRT
            else -> SubtitleInterchangeFormat.ASS
        }
    }

    fun parse(format: SubtitleInterchangeFormat, text: String): AssDocument = when (format) {
        SubtitleInterchangeFormat.ASS -> AssCodec.parse(text)
        SubtitleInterchangeFormat.SRT -> SrtCodec.parse(text)
        SubtitleInterchangeFormat.WEBVTT -> WebVttCodec.parse(text)
    }
}

object SrtCodec {
    private val timing = Regex(
        """^\s*(\d{1,3}):(\d{2}):(\d{2})[,.](\d{3})\s+-->\s+(\d{1,3}):(\d{2}):(\d{2})[,.](\d{3})(?:\s+.*)?$"""
    )

    fun parse(input: String): AssDocument {
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return AssDocument()
        val blocks = normalized.split(Regex("""\n\s*\n+"""))
        val events = mutableListOf<AssEvent>()
        var nextId = 1L
        blocks.forEach { block ->
            val lines = block.lines()
            val timingIndex = lines.indexOfFirst { timing.matches(it) }
            if (timingIndex < 0) return@forEach
            val match = timing.matchEntire(lines[timingIndex]) ?: return@forEach
            val start = parseTime(match.groupValues.slice(1..4))
            val end = parseTime(match.groupValues.slice(5..8))
            if (end < start) return@forEach
            val body = lines.drop(timingIndex + 1)
                .joinToString("\\N")
                .let(::srtMarkupToAss)
            events += AssEvent(id = nextId++, start = start, end = end, text = body)
        }
        return AssDocument(events = events)
    }

    fun write(document: AssDocument): String = buildString {
        document.events.filterNot { it.comment }.forEachIndexed { index, event ->
            append(index + 1).append('\n')
            append(formatTime(event.start)).append(" --> ").append(formatTime(event.end)).append('\n')
            append(AssInlineSyntax.visibleText(event.text).replace("\\N", "\n").replace("\\n", "\n"))
            append("\n\n")
        }
    }.trimEnd() + "\n"

    private fun parseTime(parts: List<String>): SubTime {
        val h = parts[0].toLong()
        val m = parts[1].toLong()
        val s = parts[2].toLong()
        val ms = parts[3].toLong()
        return SubTime((((h * 60 + m) * 60 + s) * 1000) + ms)
    }

    private fun formatTime(time: SubTime): String {
        val total = time.millis
        val ms = total % 1000
        val seconds = (total / 1000) % 60
        val minutes = (total / 60_000) % 60
        val hours = total / 3_600_000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, ms)
    }

    private fun srtMarkupToAss(text: String): String = text
        .replace(Regex("""<\s*i\s*>""", RegexOption.IGNORE_CASE), "{\\i1}")
        .replace(Regex("""<\s*/\s*i\s*>""", RegexOption.IGNORE_CASE), "{\\i0}")
        .replace(Regex("""<\s*b\s*>""", RegexOption.IGNORE_CASE), "{\\b1}")
        .replace(Regex("""<\s*/\s*b\s*>""", RegexOption.IGNORE_CASE), "{\\b0}")
        .replace(Regex("""<\s*u\s*>""", RegexOption.IGNORE_CASE), "{\\u1}")
        .replace(Regex("""<\s*/\s*u\s*>""", RegexOption.IGNORE_CASE), "{\\u0}")
}

object WebVttCodec {
    private val timing = Regex(
        """^\s*(?:(\d{1,3}):)?(\d{2}):(\d{2})[.](\d{3})\s+-->\s+(?:(\d{1,3}):)?(\d{2}):(\d{2})[.](\d{3})(?:\s+.*)?$"""
    )

    fun parse(input: String): AssDocument {
        val normalized = input.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
        val body = normalized.lines().dropWhile { it.isBlank() || it.trim() == "WEBVTT" }.joinToString("\n")
        val blocks = body.split(Regex("""\n\s*\n+"""))
        val events = mutableListOf<AssEvent>()
        var nextId = 1L
        blocks.forEach { block ->
            val lines = block.lines()
            val timingIndex = lines.indexOfFirst { timing.matches(it) }
            if (timingIndex < 0) return@forEach
            val match = timing.matchEntire(lines[timingIndex]) ?: return@forEach
            fun toTime(hour: String, minute: String, second: String, ms: String): SubTime {
                val h = hour.ifBlank { "0" }.toLong()
                return SubTime((((h * 60 + minute.toLong()) * 60 + second.toLong()) * 1000) + ms.toLong())
            }
            val start = toTime(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4])
            val end = toTime(match.groupValues[5], match.groupValues[6], match.groupValues[7], match.groupValues[8])
            if (end < start) return@forEach
            val cueText = lines.drop(timingIndex + 1).joinToString("\\N")
            events += AssEvent(nextId++, start = start, end = end, text = cueText)
        }
        return AssDocument(events = events)
    }
}
