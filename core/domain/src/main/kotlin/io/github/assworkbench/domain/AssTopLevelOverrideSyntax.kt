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

    fun leadingPrefixLength(text: String): Int {
        var cursor = 0
        while (cursor < text.length && text[cursor] == '{') {
            val close = text.indexOf('}', cursor + 1)
            if (close < 0) return cursor
            cursor = close + 1
        }
        return cursor
    }
}
