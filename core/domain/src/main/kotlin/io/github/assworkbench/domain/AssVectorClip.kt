package io.github.assworkbench.domain

data class AssVectorToken(val command: Char?, val number: Double?)
data class AssVectorClip(val inverted: Boolean, val scale: Int?, val tokens: List<AssVectorToken>)

object AssVectorClipCodec {
    private val clip = Regex("""\\(i?clip)\(([^)]*)\)""", RegexOption.IGNORE_CASE)
    private val token = Regex("""[mnlbspc]|[-+]?\d+(?:\.\d+)?""", RegexOption.IGNORE_CASE)

    fun inspect(text: String): AssVectorClip? {
        val match = clip.find(text) ?: return null
        val payload = match.groupValues[2].trim()
        val parts = payload.split(',', limit = 2)
        val (scale, path) = if (parts.size == 2 && parts[0].trim().toIntOrNull() != null &&
            token.find(parts[1])?.value?.firstOrNull()?.isLetter() == true
        ) parts[0].trim().toInt() to parts[1] else null to payload

        val tokens = token.findAll(path).map { m ->
            val v = m.value
            if (v.length == 1 && v[0].lowercaseChar() in "mnlbspc") AssVectorToken(v[0].lowercaseChar(), null)
            else AssVectorToken(null, v.toDouble())
        }.toList()
        if (tokens.none { it.command != null }) return null
        return AssVectorClip(match.groupValues[1].startsWith("i", true), scale, tokens)
    }

    fun patch(text: String, vector: AssVectorClip): String {
        val existing = clip.find(text) ?: return text
        val payload = buildString {
            vector.scale?.let { append(it).append(',') }
            var first = true
            vector.tokens.forEach { t ->
                if (!first) append(' ')
                first = false
                if (t.command != null) append(t.command) else append(format(t.number ?: 0.0))
            }
        }
        val replacement = "\\" + (if (vector.inverted) "iclip" else "clip") + "(" + payload + ")"
        return text.replaceRange(existing.range, replacement)
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
