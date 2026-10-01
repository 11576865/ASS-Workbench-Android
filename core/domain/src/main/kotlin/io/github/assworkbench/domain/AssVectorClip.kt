package io.github.assworkbench.domain

data class AssVectorPoint(
    val index: Int,
    val command: String,
    val x: Double,
    val y: Double,
    val xStart: Int,
    val xEndExclusive: Int,
    val yStart: Int,
    val yEndExclusive: Int,
)

data class AssVectorPath(
    val raw: String,
    val points: List<AssVectorPoint>,
)

data class AssVectorClip(
    val inverted: Boolean,
    val scale: Int?,
    val path: AssVectorPath,
    val tagStart: Int,
    val tagEndExclusive: Int,
    val pathStartInTag: Int,
    val pathEndInTag: Int,
)

object AssVectorPathSemantic {
    private val token = Regex("""[A-Za-z]+|-?\d+(?:\.\d+)?""")

    fun parse(raw: String): AssVectorPath {
        val matches = token.findAll(raw).toList()
        val points = mutableListOf<AssVectorPoint>()
        var command = ""
        var pendingX: MatchResult? = null

        matches.forEach { match ->
            val value = match.value
            if (value.firstOrNull()?.isLetter() == true) {
                command = value.lowercase()
                pendingX = null
                return@forEach
            }
            if (command !in setOf("m", "n", "l", "b", "s", "p")) return@forEach
            if (pendingX == null) {
                pendingX = match
            } else {
                val x = pendingX!!
                points += AssVectorPoint(
                    index = points.size,
                    command = command,
                    x = x.value.toDoubleOrNull() ?: 0.0,
                    y = value.toDoubleOrNull() ?: 0.0,
                    xStart = x.range.first,
                    xEndExclusive = x.range.last + 1,
                    yStart = match.range.first,
                    yEndExclusive = match.range.last + 1,
                )
                pendingX = null
            }
        }
        return AssVectorPath(raw, points)
    }

    fun patchPoint(raw: String, pointIndex: Int, x: Double, y: Double): String {
        val path = parse(raw)
        val point = path.points.getOrNull(pointIndex) ?: return raw
        var result = raw
        result = result.substring(0, point.yStart) + format(y) + result.substring(point.yEndExclusive)
        result = result.substring(0, point.xStart) + format(x) + result.substring(point.xEndExclusive)
        return result
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString().trimEnd('0').trimEnd('.')
    }
}

object AssVectorClipSemantic {
    private val clip = Regex("""\\(i?clip)\(([^}]*)\)""", RegexOption.IGNORE_CASE)
    private val command = Regex("""(?:^|[\s,])(m|n|l|b|s|p|c)(?:[\s,]|$)""", RegexOption.IGNORE_CASE)

    fun inspect(text: String): List<AssVectorClip> {
        val out = mutableListOf<AssVectorClip>()
        scanOverrideBlocks(text) { blockStart, block ->
            clip.findAll(block).forEach { match ->
                val payload = match.groupValues[2]
                if (!command.containsMatchIn(payload)) return@forEach
                val comma = payload.indexOf(',')
                val leading = if (comma >= 0) payload.substring(0, comma).trim() else ""
                val scale = leading.toIntOrNull()
                val pathRelative = if (scale != null) {
                    val start = comma + 1
                    start to payload.length
                } else {
                    0 to payload.length
                }
                val tagStart = blockStart + match.range.first
                val payloadStartInTag = match.value.indexOf('(') + 1
                val pathStartInTag = payloadStartInTag + pathRelative.first
                val pathEndInTag = payloadStartInTag + pathRelative.second
                val rawPath = match.value.substring(pathStartInTag, pathEndInTag)
                out += AssVectorClip(
                    inverted = match.groupValues[1].equals("iclip", ignoreCase = true),
                    scale = scale,
                    path = AssVectorPathSemantic.parse(rawPath),
                    tagStart = tagStart,
                    tagEndExclusive = blockStart + match.range.last + 1,
                    pathStartInTag = pathStartInTag,
                    pathEndInTag = pathEndInTag,
                )
            }
        }
        return out
    }

    fun patchPoint(text: String, clipIndex: Int, pointIndex: Int, x: Double, y: Double): String {
        val clips = inspect(text)
        val target = clips.getOrNull(clipIndex) ?: return text
        val tag = text.substring(target.tagStart, target.tagEndExclusive)
        val rawPath = tag.substring(target.pathStartInTag, target.pathEndInTag)
        val patchedPath = AssVectorPathSemantic.patchPoint(rawPath, pointIndex, x, y)
        if (patchedPath == rawPath) return text
        val patchedTag = tag.substring(0, target.pathStartInTag) + patchedPath + tag.substring(target.pathEndInTag)
        return text.substring(0, target.tagStart) + patchedTag + text.substring(target.tagEndExclusive)
    }

    private inline fun scanOverrideBlocks(text: String, block: (Int, String) -> Unit) {
        var cursor = 0
        while (cursor < text.length) {
            val open = text.indexOf('{', cursor)
            if (open < 0) break
            val close = text.indexOf('}', open + 1)
            if (close < 0) break
            block(open, text.substring(open, close + 1))
            cursor = close + 1
        }
    }
}
