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
        val geometry = AssGeometrySemantic.inspect(text)
        val animation = AssAnimationSemantic.inspect(text)
        val leading = leadingBlocks.find(text)?.value.orEmpty()
        val topLevelBlur = Regex("""\\blur(-?\d+(?:\.\d+)?)""").find(removeTransformPayloads(leading))
        return EventOverrideSnapshot(
            x = geometry.position?.x,
            y = geometry.position?.y,
            blur = topLevelBlur?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
            fadeInMs = animation.simpleFade?.fadeInMs,
            fadeOutMs = animation.simpleFade?.fadeOutMs,
            softEntry = softEntry.containsMatchIn(text),
        )
    }

    fun updateVisualEffects(
        text: String,
        blurRadius: Double?,
        enableSoftEntry: Boolean,
        softEntryMs: Int = 160,
    ): String {
        val leading = leadingBlocks.find(text)?.value.orEmpty()
        val body = text.removePrefix(leading)
        var preserved = leading.replace(softEntry, "")
        preserved = removeTopLevelBlur(preserved)
        preserved = preserved.replace(Regex("""\{\s*\}"""), "")
        val tags = buildString {
            if (blurRadius != null && blurRadius > 0.0) {
                append("\\blur")
                append(formatNumber(blurRadius.coerceIn(0.0, 20.0)))
            }
            if (enableSoftEntry) {
                append("\\fscx98\\fscy98\\blur1.5\\t(0,")
                append(softEntryMs.coerceIn(80, 1000))
                append(",\\fscx100\\fscy100\\blur0)")
            }
        }
        val managed = if (tags.isEmpty()) "" else "{$tags}"
        return preserved + managed + body
    }

    private fun removeTopLevelBlur(leading: String): String {
        val out = StringBuilder()
        var cursor = 0
        while (cursor < leading.length) {
            if (leading[cursor] != '{') { out.append(leading[cursor++]); continue }
            val close = leading.indexOf('}', cursor + 1)
            if (close < 0) return leading
            val block = leading.substring(cursor + 1, close)
            val cleaned = stripBlurTagsFromBlock(block)
            if (cleaned.isNotBlank()) out.append('{').append(cleaned).append('}')
            cursor = close + 1
        }
        return out.toString()
    }

    private fun stripBlurTagsFromBlock(block: String): String {
        val remove = mutableListOf<IntRange>()
        var cursor = 0
        while (cursor < block.length) {
            if (block[cursor] != '\\') { cursor++; continue }
            val start = cursor++
            val nameStart = cursor
            while (cursor < block.length && (block[cursor].isLetter() || block[cursor] == '-' || block[cursor] == '_')) cursor++
            if (cursor == nameStart) continue
            val name = block.substring(nameStart, cursor)
            var depth = 0
            while (cursor < block.length) {
                when (block[cursor]) {
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                    '\\' -> if (depth == 0) break
                }
                cursor++
            }
            if (name.equals("blur", true)) remove += start until cursor
        }
        var result = block
        remove.asReversed().forEach { result = result.removeRange(it.first, it.last + 1) }
        return result
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

    private fun removeTransformPayloads(text: String): String {
        val out = StringBuilder()
        var cursor = 0
        while (cursor < text.length) {
            if (text.startsWith("\\t(", cursor)) {
                var depth = 0
                var end = cursor
                while (end < text.length) {
                    if (text[end] == '(') depth++
                    if (text[end] == ')') { depth--; if (depth == 0) { end++; break } }
                    end++
                }
                cursor = end
            } else {
                out.append(text[cursor++])
            }
        }
        return out.toString()
    }
    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
