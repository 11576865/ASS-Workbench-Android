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

data class AssAnimationSnapshot(
    val simpleFade: AssSimpleFade? = null,
    val complexFade: AssComplexFade? = null,
    val fadeConflict: Boolean = false,
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
        return AssAnimationSnapshot(
            simpleFade = simple.lastOrNull()?.value?.let(::parseSimpleFade),
            complexFade = complex.lastOrNull()?.value?.let(::parseComplexFade),
            fadeConflict = simple.isNotEmpty() && complex.isNotEmpty(),
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

    fun removeFade(text: String): String = removeTags(text, scanLeading(text), setOf("fad", "fade"))

    private fun parseSimpleFade(value: String): AssSimpleFade? {
        val parts = parenthesizedParts(value) ?: return null
        if (parts.size != 2) return null
        return AssSimpleFade(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)
    }

    private fun parseComplexFade(value: String): AssComplexFade? {
        val parts = parenthesizedParts(value) ?: return null
        if (parts.size != 7) return null
        val n = parts.map { it.toIntOrNull() ?: return null }
        return AssComplexFade(n[0], n[1], n[2], n[3], n[4], n[5], n[6])
    }

    private fun appendTag(text: String, replacement: String): String {
        val scan = scanLeading(text)
        if (scan.malformed) return text
        val block = scan.blocks.lastOrNull { it.hasTags }
        return if (block != null) {
            text.substring(0, block.closeIndex) + replacement + text.substring(block.closeIndex)
        } else {
            "{$replacement}$text"
        }
    }

    private fun removeTags(text: String, scan: Scan, names: Set<String>): String {
        val targets = scan.tags.filter { tag -> names.any { it.equals(tag.name, true) } }
        if (targets.isEmpty()) return text
        var result = text
        targets.sortedByDescending { it.start }.forEach { result = result.removeRange(it.start, it.endExclusive) }
        return removeEmptyLeadingBlocks(result)
    }

    private fun removeEmptyLeadingBlocks(text: String): String {
        if (text.isEmpty() || text[0] != '{') return text
        val kept = StringBuilder()
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return text
            if (text.substring(cursor + 1, close).isNotBlank()) kept.append(text, cursor, close + 1)
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

    private fun scanBlock(text: String, start: Int, endExclusive: Int, tags: MutableList<TagRef>) {
        var cursor = start
        while (cursor < endExclusive) {
            if (text[cursor] != '\\') { cursor++; continue }
            val tagStart = cursor++
            val nameStart = cursor
            while (cursor < endExclusive && (text[cursor].isLetter() || text[cursor] == '-' || text[cursor] == '_')) cursor++
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
            tags += TagRef(name, text.substring(valueStart, cursor), tagStart, cursor)
        }
    }

    private fun parenthesizedParts(value: String): List<String>? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) return null
        return trimmed.substring(1, trimmed.length - 1).split(',').map(String::trim)
    }
}
