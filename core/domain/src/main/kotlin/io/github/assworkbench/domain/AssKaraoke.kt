package io.github.assworkbench.domain

enum class AssKaraokeMode(val tag: String) { K("k"), KF("kf"), KO("ko"), KT("kt") }
data class AssKaraokeSegment(val mode: AssKaraokeMode, val centiseconds: Int, val text: String)

object AssKaraokeCodec {
    private val marker = Regex("""\{[^}]*\\(k|K|kf|ko|kt)\s*(\d+)[^}]*\}""")

    fun parse(text: String): List<AssKaraokeSegment> {
        val matches = marker.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexed { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val rawMode = match.groupValues[1]
            val mode = when {
                rawMode.equals("kf", true) || rawMode == "K" -> AssKaraokeMode.KF
                rawMode.equals("ko", true) -> AssKaraokeMode.KO
                rawMode.equals("kt", true) -> AssKaraokeMode.KT
                else -> AssKaraokeMode.K
            }
            AssKaraokeSegment(mode, match.groupValues[2].toInt(), text.substring(start, end))
        }
    }

    fun write(segments: List<AssKaraokeSegment>): String = buildString {
        segments.forEach { segment ->
            require(segment.centiseconds >= 0)
            append("{\\").append(segment.mode.tag).append(segment.centiseconds).append("}").append(segment.text)
        }
    }

    fun totalDurationMs(segments: List<AssKaraokeSegment>): Long = segments.sumOf { it.centiseconds.toLong() } * 10L
}
