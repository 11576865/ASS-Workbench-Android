package io.github.assworkbench.domain

data class AssSimpleFade(val fadeInMs: Int, val fadeOutMs: Int)

data class AssComplexFade(
    val alpha1: Int,
    val alpha2: Int,
    val alpha3: Int,
    val time1Ms: Int,
    val time2Ms: Int,
    val time3Ms: Int,
    val time4Ms: Int,
)

data class AssTransform(
    val startMs: Double? = null,
    val endMs: Double? = null,
    val accel: Double? = null,
    val tags: String,
    val malformed: Boolean = false,
    val rawValue: String = "",
)

data class AssAnimationSnapshot(
    val simpleFade: AssSimpleFade? = null,
    val complexFade: AssComplexFade? = null,
    val fadeConflict: Boolean = false,
    val transforms: List<AssTransform> = emptyList(),
    val malformedLeadingBlock: Boolean = false,
)

object AssAnimationSemantic {
    private data class TagRef(val name: String, val value: String, val start: Int, val endExclusive: Int)
    private data class BlockRef(val closeIndex: Int, val hasTags: Boolean)
    private data class Scan(val tags: List<TagRef>, val blocks: List<BlockRef>, val malformed: Boolean)

    fun inspect(text: String): AssAnimationSnapshot {
        val scan = scanLeading(text)
        val simple = scan.tags.filter { it.name.equals("fad", true) }
        val complex = scan.tags.filter { it.name.equals("fade", true) }
        val transforms = scan.tags
            .filter { it.name.equals("t", true) }
            .map { parseTransform(it.value) }
        return AssAnimationSnapshot(
            simpleFade = simple.lastOrNull()?.value?.let(::parseSimpleFade),
            complexFade = complex.lastOrNull()?.value?.let(::parseComplexFade),
            fadeConflict = simple.isNotEmpty() && complex.isNotEmpty(),
            transforms = transforms,
            malformedLeadingBlock = scan.malformed,
        )
    }

    fun patchSimpleFade(text: String, fadeInMs: Int, fadeOutMs: Int): String {
        val stripped = removeFade(text)
        return appendTag(
            stripped,
            "\\fad(" + fadeInMs.coerceIn(0, 1_000_000) + "," + fadeOutMs.coerceIn(0, 1_000_000) + ")",
        )
    }

    fun patchComplexFade(text: String, fade: AssComplexFade): String {
        val stripped = removeFade(text)
        val tag = "\\fade(" + listOf(
            fade.alpha1.coerceIn(0, 255),
            fade.alpha2.coerceIn(0, 255),
            fade.alpha3.coerceIn(0, 255),
            fade.time1Ms.coerceAtLeast(0),
            fade.time2Ms.coerceAtLeast(0),
            fade.time3Ms.coerceAtLeast(0),
            fade.time4Ms.coerceAtLeast(0),
        ).joinToString(",") + ")"
        return appendTag(stripped, tag)
    }

    fun removeFade(text: String): String =
        removeTags(text, scanLeading(text), setOf("fad", "fade"))

    fun patchTransform(text: String, index: Int, transform: AssTransform): String {
        val scan = scanLeading(text)
        val target = scan.tags.filter { it.name.equals("t", true) }.getOrNull(index) ?: return text
        val replacement = renderTransform(transform) ?: return text
        return text.replaceRange(target.start, target.endExclusive, replacement)
    }

    fun appendTransform(text: String, transform: AssTransform): String {
        val replacement = renderTransform(transform) ?: return text
        return appendTag(text, replacement)
    }

    fun removeTransform(text: String, index: Int): String {
        val scan = scanLeading(text)
        val target = scan.tags.filter { it.name.equals("t", true) }.getOrNull(index) ?: return text
        return removeEmptyLeadingBlocks(text.removeRange(target.start, target.endExclusive))
    }

    private fun parseSimpleFade(value: String): AssSimpleFade? {
        val parts = parenthesizedParts(value) ?: return null
        if (parts.size != 2) return null
        return AssSimpleFade(
            parts[0].toIntOrNull() ?: return null,
            parts[1].toIntOrNull() ?: return null,
        )
    }

    private fun parseComplexFade(value: String): AssComplexFade? {
        val parts = parenthesizedParts(value) ?: return null
        if (parts.size != 7) return null
        val n = parts.map { it.toIntOrNull() ?: return null }
        return AssComplexFade(n[0], n[1], n[2], n[3], n[4], n[5], n[6])
    }

    private fun parseTransform(value: String): AssTransform {
        val raw = value
        val trimmed = value.trim()
        if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) {
            return AssTransform(tags = trimmed, malformed = true, rawValue = raw)
        }
        val inner = trimmed.substring(1, trimmed.length - 1)
        val parts = splitTopLevel(inner)
        val payloadIndex = parts.indexOfFirst { it.trimStart().startsWith("\\") }
        if (payloadIndex < 0) {
            return AssTransform(tags = inner, malformed = true, rawValue = raw)
        }

        val prefix = parts.take(payloadIndex).map(String::trim)
        val tags = parts.drop(payloadIndex).joinToString(",").trim()
        if (tags.isBlank()) {
            return AssTransform(tags = tags, malformed = true, rawValue = raw)
        }

        fun invalid(): AssTransform = AssTransform(tags = tags, malformed = true, rawValue = raw)

        return when (prefix.size) {
            0 -> AssTransform(tags = tags, rawValue = raw)
            1 -> AssTransform(
                accel = prefix[0].toDoubleOrNull() ?: return invalid(),
                tags = tags,
                rawValue = raw,
            )
            2 -> AssTransform(
                startMs = prefix[0].toDoubleOrNull() ?: return invalid(),
                endMs = prefix[1].toDoubleOrNull() ?: return invalid(),
                tags = tags,
                rawValue = raw,
            )
            3 -> AssTransform(
                startMs = prefix[0].toDoubleOrNull() ?: return invalid(),
                endMs = prefix[1].toDoubleOrNull() ?: return invalid(),
                accel = prefix[2].toDoubleOrNull() ?: return invalid(),
                tags = tags,
                rawValue = raw,
            )
            else -> invalid()
        }
    }

    private fun renderTransform(transform: AssTransform): String? {
        if (transform.malformed) return null
        val tags = transform.tags.trim()
        if (tags.isBlank() || !tags.startsWith("\\")) return null
        if ((transform.startMs == null) != (transform.endMs == null)) return null
        val start = transform.startMs
        val end = transform.endMs
        if (start != null && (!start.isFinite() || end == null || !end.isFinite() || start > end)) return null
        val accel = transform.accel
        if (accel != null && (!accel.isFinite() || accel <= 0.0)) return null

        val args = buildList {
            if (start != null && end != null) {
                add(formatNumber(start))
                add(formatNumber(end))
            }
            if (accel != null) add(formatNumber(accel))
            add(tags)
        }
        return "\\t(" + args.joinToString(",") + ")"
    }

    private fun appendTag(text: String, replacement: String): String {
        val scan = scanLeading(text)
        if (scan.malformed) return text
        val tagBlock = scan.blocks.lastOrNull { it.hasTags }
        if (tagBlock != null) {
            return text.substring(0, tagBlock.closeIndex) + replacement + text.substring(tagBlock.closeIndex)
        }
        val lastLeadingClose = scan.blocks.lastOrNull()?.closeIndex
        return if (lastLeadingClose != null) {
            val insertAt = lastLeadingClose + 1
            text.substring(0, insertAt) + "{$replacement}" + text.substring(insertAt)
        } else {
            "{$replacement}$text"
        }
    }

    private fun removeTags(text: String, scan: Scan, names: Set<String>): String {
        val targets = scan.tags.filter { tag -> names.any { it.equals(tag.name, true) } }
        if (targets.isEmpty()) return text
        var result = text
        targets.sortedByDescending { it.start }.forEach {
            result = result.removeRange(it.start, it.endExclusive)
        }
        return removeEmptyLeadingBlocks(result)
    }

    private fun removeEmptyLeadingBlocks(text: String): String {
        if (text.isEmpty() || text[0] != '{') return text
        val kept = StringBuilder()
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return text
            if (text.substring(cursor + 1, close).isNotBlank()) {
                kept.append(text, cursor, close + 1)
            }
            cursor = close + 1
        }
        kept.append(text.substring(cursor))
        return kept.toString()
    }

    private fun scanLeading(text: String): Scan {
        if (text.isEmpty() || text[0] != '{') return Scan(emptyList(), emptyList(), false)
        val tags = mutableListOf<TagRef>()
        val blocks = mutableListOf<BlockRef>()
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return Scan(tags, blocks, true)
            val before = tags.size
            scanBlock(text, cursor + 1, close, tags)
            blocks += BlockRef(close, tags.size > before)
            cursor = close + 1
        }
        return Scan(tags, blocks, false)
    }

    private fun scanBlock(
        text: String,
        start: Int,
        endExclusive: Int,
        tags: MutableList<TagRef>,
    ) {
        var cursor = start
        while (cursor < endExclusive) {
            if (text[cursor] != '\\') {
                cursor++
                continue
            }
            val tagStart = cursor++
            val nameStart = cursor
            if (cursor < endExclusive && text[cursor].isDigit()) {
                cursor++
                while (cursor < endExclusive && text[cursor].isLetter()) cursor++
            } else {
                while (
                    cursor < endExclusive &&
                    (text[cursor].isLetter() || text[cursor] == '-' || text[cursor] == '_')
                ) {
                    cursor++
                }
            }
            if (cursor == nameStart) continue
            val name = text.substring(nameStart, cursor)
            val valueStart = cursor
            var depth = 0
            while (cursor < endExclusive) {
                when (text[cursor]) {
                    '(' -> depth++
                    ')' -> if (depth > 0) depth--
                    '\\' -> if (depth == 0) break
                }
                cursor++
            }
            tags += TagRef(
                name = name,
                value = text.substring(valueStart, cursor),
                start = tagStart,
                endExclusive = cursor,
            )
        }
    }

    private fun parenthesizedParts(value: String): List<String>? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) return null
        return splitTopLevel(trimmed.substring(1, trimmed.length - 1)).map(String::trim)
    }

    private fun splitTopLevel(value: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var depth = 0
        value.forEachIndexed { index, c ->
            when (c) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                ',' -> if (depth == 0) {
                    parts += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        parts += value.substring(start)
        return parts
    }

    private fun formatNumber(value: Double): String {
        val rounded = kotlin.math.round(value * 1000.0) / 1000.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
