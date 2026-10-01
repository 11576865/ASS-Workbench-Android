package io.github.assworkbench.domain

enum class KaraokeKind { K, KF, KO, KT }

data class KaraokeSegment(
    val kind: KaraokeKind,
    val valueCentiseconds: Int,
    val text: String,
)

object KaraokeSemantic {
    private val tag = Regex("""\\(k|K|kf|ko|kt)(-?\d+)""")

    fun inspect(text: String): List<KaraokeSegment> {
        val matches = tag.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexed { index, match ->
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            KaraokeSegment(
                kind = when (match.groupValues[1]) {
                    "K", "kf" -> KaraokeKind.KF
                    "ko" -> KaraokeKind.KO
                    "kt" -> KaraokeKind.KT
                    else -> KaraokeKind.K
                },
                valueCentiseconds = match.groupValues[2].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                text = AssInlineSyntax.visibleText(text.substring(start, end)),
            )
        }
    }

    fun totalDurationMs(text: String): Long =
        inspect(text).filter { it.kind != KaraokeKind.KT }.sumOf { it.valueCentiseconds.toLong() * 10L }

    fun retime(text: String, centiseconds: List<Int>): String {
        val matches = tag.findAll(text).toList()
        require(matches.size == centiseconds.size) { "Karaoke timing count does not match." }
        var result = text
        matches.zip(centiseconds).asReversed().forEach { pair ->
            val match = pair.first
            val value = pair.second
            val replacement = "\\" + match.groupValues[1] + value.coerceAtLeast(0)
            result = result.replaceRange(match.range, replacement)
        }
        return result
    }
}
