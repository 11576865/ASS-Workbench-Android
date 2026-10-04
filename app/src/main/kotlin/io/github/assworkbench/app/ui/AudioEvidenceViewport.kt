package io.github.assworkbench.app.ui

/** Freeze the displayed signal range for exactly the lifetime of a seek gesture. */
internal fun audioEvidenceRange(playhead: Long, captured: Pair<Long, Long>? = null): Pair<Long, Long> {
    if (captured != null) return captured
    val start = (playhead - 4000L).coerceAtLeast(0L)
    return start to start + 8000L
}

internal fun audioEvidenceTimeAt(range: Pair<Long, Long>, x: Float, width: Float): Long =
    range.first + ((range.second - range.first) * (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)).toLong()
