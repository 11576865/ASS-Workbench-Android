package io.github.assworkbench.domain

data class AssDrawing(
    val scale: Int,
    val baselineOffset: Double? = null,
    val tokens: List<AssVectorToken>,
)

data class AssDrawingInspection(
    val drawing: AssDrawing,
    val pathStart: Int,
    val pathEndExclusive: Int,
)

object AssDrawingCodec {
    private val pTag = Regex("""\\p\s*([1-9]\d*)""", RegexOption.IGNORE_CASE)
    private val pboTag = Regex("""\\pbo\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val pZero = Regex("""\{[^}]*\\p\s*0[^}]*\}""", RegexOption.IGNORE_CASE)
    private val token = Regex("""[mnlbspc]|[-+]?\d+(?:\.\d+)?""", RegexOption.IGNORE_CASE)

    fun inspect(text: String): AssDrawingInspection? {
        var cursor = 0
        var scale: Int? = null
        var baseline: Double? = null
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return null
            val block = text.substring(cursor + 1, close)
            pTag.findAll(block).forEach { m -> m.groupValues[1].toIntOrNull()?.let { scale = it } }
            pboTag.findAll(block).lastOrNull()?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { baseline = it }
            cursor = close + 1
        }
        val resolvedScale = scale ?: return null
        val end = pZero.find(text, cursor)?.range?.first ?: text.length
        if (end <= cursor) return null
        val path = text.substring(cursor, end).trim()
        val tokens = parseTokens(path)
        if (tokens.none { it.command != null }) return null
        return AssDrawingInspection(AssDrawing(resolvedScale, baseline, tokens), cursor, end)
    }

    fun patch(text: String, drawing: AssDrawing): String {
        val inspection = inspect(text) ?: return text
        val path = renderPath(drawing.tokens)
        var result = text.replaceRange(inspection.pathStart, inspection.pathEndExclusive, path)
        result = patchLeadingTag(result, "p", drawing.scale.coerceAtLeast(1).toString())
        result = if (drawing.baselineOffset == null) removeLeadingTag(result, "pbo")
        else patchLeadingTag(result, "pbo", format(drawing.baselineOffset))
        return result
    }

    fun insert(text: String, drawing: AssDrawing): String {
        if (inspect(text) != null) return patch(text, drawing)
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return text
            cursor = close + 1
        }
        val tags = buildString {
            append("\\p").append(drawing.scale.coerceAtLeast(1))
            drawing.baselineOffset?.let { append("\\pbo").append(format(it)) }
        }
        val block = "{$tags}" + renderPath(drawing.tokens) + "{\\p0}"
        return text.substring(0, cursor) + block + text.substring(cursor)
    }

    fun rectangle(left: Double, top: Double, right: Double, bottom: Double, scale: Int = 1): AssDrawing =
        AssDrawing(
            scale = scale,
            tokens = listOf(
                AssVectorToken('m', null),
                AssVectorToken(null, left), AssVectorToken(null, top),
                AssVectorToken('l', null),
                AssVectorToken(null, right), AssVectorToken(null, top),
                AssVectorToken(null, right), AssVectorToken(null, bottom),
                AssVectorToken(null, left), AssVectorToken(null, bottom),
            ),
        )

    fun translate(drawing: AssDrawing, dx: Double, dy: Double): AssDrawing =
        drawing.copy(tokens = transformPairs(drawing.tokens) { x, y -> x + dx to y + dy })

    fun scale(drawing: AssDrawing, factorX: Double, factorY: Double, originX: Double = 0.0, originY: Double = 0.0): AssDrawing {
        require(factorX.isFinite() && factorY.isFinite())
        return drawing.copy(tokens = transformPairs(drawing.tokens) { x, y ->
            originX + (x - originX) * factorX to originY + (y - originY) * factorY
        })
    }

    fun renderPath(tokens: List<AssVectorToken>): String = buildString {
        var first = true
        tokens.forEach { t ->
            if (!first) append(' ')
            first = false
            if (t.command != null) append(t.command.lowercaseChar()) else append(format(t.number ?: 0.0))
        }
    }

    private fun parseTokens(path: String): List<AssVectorToken> =
        token.findAll(path).map { m ->
            val v = m.value
            if (v.length == 1 && v[0].lowercaseChar() in "mnlbspc") AssVectorToken(v[0].lowercaseChar(), null)
            else AssVectorToken(null, v.toDouble())
        }.toList()

    private fun transformPairs(tokens: List<AssVectorToken>, transform: (Double, Double) -> Pair<Double, Double>): List<AssVectorToken> {
        val out = tokens.toMutableList()
        var i = 0
        while (i < out.size) {
            if (out[i].command != null) { i++; continue }
            val j = i + 1
            if (j >= out.size || out[j].command != null) { i++; continue }
            val (nx, ny) = transform(out[i].number ?: 0.0, out[j].number ?: 0.0)
            out[i] = out[i].copy(number = nx)
            out[j] = out[j].copy(number = ny)
            i += 2
        }
        return out
    }

    private fun patchLeadingTag(text: String, name: String, value: String): String {
        val tag = Regex("""\\$name\s*[-+]?\d+(?:\.\d+)?""", RegexOption.IGNORE_CASE)
        var cursor = 0
        var tagBlockClose = -1
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return text
            val block = text.substring(cursor + 1, close)
            if ('\\' in block) tagBlockClose = close
            val found = tag.find(block)
            if (found != null) {
                val a = cursor + 1 + found.range.first
                val b = cursor + 1 + found.range.last + 1
                return text.replaceRange(a, b, "\\$name$value")
            }
            cursor = close + 1
        }
        return if (tagBlockClose >= 0) text.substring(0, tagBlockClose) + "\\$name$value" + text.substring(tagBlockClose)
        else "{\\$name$value}$text"
    }

    private fun removeLeadingTag(text: String, name: String): String {
        val tag = Regex("""\\$name\s*[-+]?\d+(?:\.\d+)?""", RegexOption.IGNORE_CASE)
        var result = text
        var cursor = 0
        while (cursor < result.length && result[cursor] == '{') {
            val close = result.indexOf('}', cursor + 1)
            if (close < 0) return result
            val block = result.substring(cursor + 1, close)
            val cleaned = tag.replace(block, "")
            if (cleaned.isBlank()) result = result.removeRange(cursor, close + 1)
            else {
                result = result.replaceRange(cursor + 1, close, cleaned)
                cursor = close + 1 - (block.length - cleaned.length)
            }
        }
        return result
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
