package io.github.assworkbench.domain

enum class AssKaraokeMode(val tag: String) { K("k"), KF("kf"), KO("ko"), KT("kt") }

data class AssKaraokeSegment(
    val mode: AssKaraokeMode,
    val centiseconds: Int,
    val text: String,
    val leadingText: String = "",
    val overridePrefix: String = "",
    val overrideSuffix: String = "",
    val sourceTag: String = mode.tag,
)

object AssKaraokeCodec {
    private val overrideBlock = Regex("""\{[^}]*\}""")
    private val karaokeTag = Regex("""\\(k|K|kf|ko|kt)\s*(\d+)""")

    /** Initial authoring owns plain text only; never strip unknown ASS syntax. */
    fun initializePlainText(text: String, centiseconds: Int = 20): List<AssKaraokeSegment>? {
        require(centiseconds >= 0)
        if (text.any { it == '{' || it == '}' || it == '\\' }) return null
        val words = Regex("\\S+\\s*").findAll(text).toList()
        if (words.isEmpty()) return emptyList()
        return words.mapIndexed { index, word ->
            AssKaraokeSegment(
                AssKaraokeMode.K, centiseconds, word.value,
                leadingText = if (index == 0) text.substring(0, word.range.first) else "",
            )
        }
    }

    fun parse(text: String): List<AssKaraokeSegment> {
        data class Marker(val block: MatchResult, val tag: MatchResult)
        val markers = overrideBlock.findAll(text).mapNotNull { block ->
            karaokeTag.find(block.value)?.let { Marker(block, it) }
        }.toList()
        if (markers.isEmpty()) return emptyList()

        return markers.mapIndexed { index, marker ->
            val block = marker.block
            val tag = marker.tag
            val start = block.range.last + 1
            val end = markers.getOrNull(index + 1)?.block?.range?.first ?: text.length
            val rawMode = tag.groupValues[1]
            val mode = when {
                rawMode.equals("kf", true) || rawMode == "K" -> AssKaraokeMode.KF
                rawMode.equals("ko", true) -> AssKaraokeMode.KO
                rawMode.equals("kt", true) -> AssKaraokeMode.KT
                else -> AssKaraokeMode.K
            }
            AssKaraokeSegment(
                mode = mode,
                centiseconds = tag.groupValues[2].toInt(),
                text = text.substring(start, end),
                leadingText = if (index == 0) text.substring(0, block.range.first) else "",
                overridePrefix = block.value.substring(1, tag.range.first),
                overrideSuffix = block.value.substring(tag.range.last + 1, block.value.length - 1),
                sourceTag = rawMode,
            )
        }
    }

    fun write(segments: List<AssKaraokeSegment>): String = buildString {
        segments.forEach { segment ->
            require(segment.centiseconds >= 0)
            append(segment.leadingText)
            val tag = if (modeForTag(segment.sourceTag) == segment.mode) segment.sourceTag else segment.mode.tag
            append('{').append(segment.overridePrefix)
            append('\\').append(tag).append(segment.centiseconds)
            append(segment.overrideSuffix).append('}').append(segment.text)
        }
    }

    fun totalDurationMs(segments: List<AssKaraokeSegment>): Long = segments.sumOf { it.centiseconds.toLong() } * 10L

    private fun modeForTag(tag: String): AssKaraokeMode = when {
        tag.equals("kf", true) || tag == "K" -> AssKaraokeMode.KF
        tag.equals("ko", true) -> AssKaraokeMode.KO
        tag.equals("kt", true) -> AssKaraokeMode.KT
        else -> AssKaraokeMode.K
    }
}
