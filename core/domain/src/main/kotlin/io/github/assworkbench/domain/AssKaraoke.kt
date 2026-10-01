package io.github.assworkbench.domain

enum class KaraokeTagKind(val assName: String) {
    K("k"),
    KF("kf"),
    KO("ko"),
    KT("kt"),
}

data class KaraokeSegment(
    val index: Int,
    val kind: KaraokeTagKind,
    val valueCs: Int,
    val tagStart: Int,
    val tagEndExclusive: Int,
    val textStart: Int,
    val textEndExclusive: Int,
    val visibleText: String,
)

data class KaraokeTrack(
    val segments: List<KaraokeSegment>,
) {
    val totalDurationCs: Int
        get() = segments.filter { it.kind != KaraokeTagKind.KT }.sumOf { it.valueCs }
}

object AssKaraokeSemantic {
    private val tagRegex = Regex("""\\(k|K|kf|ko|kt)(-?\d+)""")

    fun inspect(text: String): KaraokeTrack {
        data class Pending(
            val kind: KaraokeTagKind,
            val valueCs: Int,
            val tagStart: Int,
            val tagEnd: Int,
            val textStart: Int,
        )

        val pending = mutableListOf<Pending>()
        var cursor = 0
        while (cursor < text.length) {
            val open = text.indexOf('{', cursor)
            if (open < 0) break
            val close = text.indexOf('}', open + 1)
            if (close < 0) break
            val block = text.substring(open, close + 1)
            tagRegex.findAll(block).forEach { match ->
                val raw = match.groupValues[1]
                val kind = when (raw.lowercase()) {
                    "k" -> if (raw == "K") KaraokeTagKind.KF else KaraokeTagKind.K
                    "kf" -> KaraokeTagKind.KF
                    "ko" -> KaraokeTagKind.KO
                    "kt" -> KaraokeTagKind.KT
                    else -> return@forEach
                }
                pending += Pending(
                    kind = kind,
                    valueCs = match.groupValues[2].toIntOrNull() ?: 0,
                    tagStart = open + match.range.first,
                    tagEnd = open + match.range.last + 1,
                    textStart = close + 1,
                )
            }
            cursor = close + 1
        }

        val segments = pending.mapIndexed { index, item ->
            val end = pending.getOrNull(index + 1)?.let { next ->
                // Text belongs to this karaoke command up to the next override block
                // that introduces a karaoke timing command.
                text.lastIndexOf('{', next.tagStart).takeIf { it >= item.textStart } ?: next.tagStart
            } ?: text.length
            KaraokeSegment(
                index = index,
                kind = item.kind,
                valueCs = item.valueCs,
                tagStart = item.tagStart,
                tagEndExclusive = item.tagEnd,
                textStart = item.textStart.coerceAtMost(end),
                textEndExclusive = end,
                visibleText = AssInlineSyntax.visibleText(text.substring(item.textStart.coerceAtMost(end), end)),
            )
        }
        return KaraokeTrack(segments)
    }

    fun patchValue(text: String, segmentIndex: Int, valueCs: Int): String {
        val track = inspect(text)
        val segment = track.segments.getOrNull(segmentIndex) ?: return text
        val safe = valueCs.coerceAtLeast(0)
        val replacement = "\\" + segment.kind.assName + safe
        return text.substring(0, segment.tagStart) + replacement + text.substring(segment.tagEndExclusive)
    }

    fun convertKind(text: String, segmentIndex: Int, kind: KaraokeTagKind): String {
        val track = inspect(text)
        val segment = track.segments.getOrNull(segmentIndex) ?: return text
        val replacement = "\\" + kind.assName + segment.valueCs.coerceAtLeast(0)
        return text.substring(0, segment.tagStart) + replacement + text.substring(segment.tagEndExclusive)
    }
}
