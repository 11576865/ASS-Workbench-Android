package io.github.assworkbench.domain

/**
 * Top-level view over ASS override tags.
 *
 * AssInlineSyntax intentionally exposes nested tags inside constructs such as \t(...,\fs80)
 * for syntax highlighting and diagnostics. Structured Event-level semantics must not confuse those
 * nested payload tags with direct override tags. This helper filters the lexical tag stream by
 * parenthesis depth without rewriting or normalizing the source.
 */
object AssTopLevelOverrideSyntax {
    fun tags(text: String): List<AssInlineTag> {
        val analysis = AssInlineSyntax.analyze(text)
        if (analysis.tags.isEmpty()) return emptyList()

        val tagsByStart = analysis.tags.associateBy { it.start }
        val result = ArrayList<AssInlineTag>(analysis.tags.size)
        var depth = 0
        var insideBlock = false
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{' -> {
                    insideBlock = true
                    depth = 0
                }
                '}' -> {
                    insideBlock = false
                    depth = 0
                }
                '(' -> if (insideBlock) depth++
                ')' -> if (insideBlock && depth > 0) depth--
                '\\' -> if (insideBlock && depth == 0) {
                    tagsByStart[index]?.let(result::add)
                }
            }
            index++
        }
        return result
    }

    fun leadingTags(text: String): List<AssInlineTag> {
        val end = leadingPrefixLength(text)
        return tags(text).filter { it.start < end }
    }

    fun removeTags(text: String, names: Set<String>): String {
        if (names.isEmpty()) return text
        val normalized = names.mapTo(linkedSetOf()) { it.lowercase() }
        val targets = tags(text).filter { it.name.lowercase() in normalized }
        if (targets.isEmpty()) return text

        var result = text
        targets.sortedByDescending { it.start }.forEach { tag ->
            result = result.removeRange(tag.start, tag.endExclusive)
        }
        return removeEmptyOverrideBlocks(result)
    }

    fun leadingPrefixLength(text: String): Int {
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return cursor
            cursor = close + 1
        }
        return cursor
    }

    private fun removeEmptyOverrideBlocks(text: String): String {
        val result = StringBuilder(text.length)
        var cursor = 0
        while (cursor < text.length) {
            if (text[cursor] != '{') {
                result.append(text[cursor++])
                continue
            }
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) {
                result.append(text.substring(cursor))
                break
            }
            val inner = text.substring(cursor + 1, close)
            if (inner.isNotBlank()) {
                result.append(text, cursor, close + 1)
            }
            cursor = close + 1
        }
        return result.toString()
    }
}
