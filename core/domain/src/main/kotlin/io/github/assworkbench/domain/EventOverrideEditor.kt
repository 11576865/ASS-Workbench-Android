package io.github.assworkbench.domain

data class EventOverrideSnapshot(
    val x: Double? = null,
    val y: Double? = null,
    val blur: Double? = null,
    val fadeInMs: Int? = null,
    val fadeOutMs: Int? = null,
    val softEntry: Boolean = false,
)

object EventOverrideEditor {
    private val position = Regex("""\\pos\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)""")
    private val blur = Regex("""\\blur(-?\d+(?:\.\d+)?)""")
    private val fade = Regex("""\\fad\(\s*(\d+)\s*,\s*(\d+)\s*\)""")
    private val softEntry = Regex(
        """\\fscx98(?:\.0+)?\\fscy98(?:\.0+)?\\blur1\.5\\t\(0,(\d+),\\fscx100(?:\.0+)?\\fscy100(?:\.0+)?\\blur0(?:\.0+)?\)"""
    )
    private val leadingBlocks = Regex("""^(?:\{[^}]*\})*""")

    fun inspect(text: String): EventOverrideSnapshot {
        val pos = position.find(text)
        val fadeMatch = fade.find(text)
        return EventOverrideSnapshot(
            x = pos?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
            y = pos?.groupValues?.getOrNull(2)?.toDoubleOrNull(),
            blur = blur.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
            fadeInMs = fadeMatch?.groupValues?.getOrNull(1)?.toIntOrNull(),
            fadeOutMs = fadeMatch?.groupValues?.getOrNull(2)?.toIntOrNull(),
            softEntry = softEntry.containsMatchIn(text),
        )
    }

    fun update(
        text: String,
        x: Double?,
        y: Double?,
        blurRadius: Double?,
        fadeInMs: Int?,
        fadeOutMs: Int?,
        enableSoftEntry: Boolean,
        softEntryMs: Int = 160,
    ): String {
        val leading = leadingBlocks.find(text)?.value.orEmpty()
        val body = text.removePrefix(leading)

        var preserved = leading
            .replace(position, "")
            .replace(fade, "")
            .replace(softEntry, "")

        // The generated soft-entry sequence contains its own blur tag. Remove ordinary
        // blur values independently after stripping that exact generated sequence.
        preserved = preserved.replace(blur, "")
        preserved = preserved.replace(Regex("""\{\s*\}"""), "")

        val tags = buildString {
            if (x != null && y != null) {
                append("\\pos(")
                append(formatNumber(x))
                append(",")
                append(formatNumber(y))
                append(")")
            }
            if (blurRadius != null && blurRadius > 0.0) {
                append("\\blur")
                append(formatNumber(blurRadius.coerceIn(0.0, 20.0)))
            }
            if (fadeInMs != null || fadeOutMs != null) {
                append("\\fad(")
                append((fadeInMs ?: 0).coerceIn(0, 10000))
                append(",")
                append((fadeOutMs ?: 0).coerceIn(0, 10000))
                append(")")
            }
            if (enableSoftEntry) {
                append("\\fscx98\\fscy98\\blur1.5\\t(0,")
                append(softEntryMs.coerceIn(80, 1000))
                append(",\\fscx100\\fscy100\\blur0)")
            }
        }

        val managed = if (tags.isEmpty()) "" else "{" + tags + "}"
        return preserved + managed + body
    }

    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
