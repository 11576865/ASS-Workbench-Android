package io.github.assworkbench.domain

enum class AssInlineTokenKind {
    TEXT,
    OVERRIDE_BLOCK,
    TAG_NAME,
    TAG_VALUE,
    ESCAPE,
    MALFORMED_BLOCK,
}

data class AssInlineToken(
    val kind: AssInlineTokenKind,
    val start: Int,
    val endExclusive: Int,
    val text: String,
)

data class AssInlineTag(
    val name: String,
    val value: String,
    val start: Int,
    val endExclusive: Int,
)

data class AssInlineIssue(
    val message: String,
    val start: Int,
    val endExclusive: Int,
)

data class AssInlineAnalysis(
    val tokens: List<AssInlineToken>,
    val tags: List<AssInlineTag>,
    val issues: List<AssInlineIssue>,
) {
    val tagNames: Set<String> get() = tags.mapTo(linkedSetOf()) { it.name.lowercase() }
    val hasErrors: Boolean get() = issues.isNotEmpty()
}

/**
 * Lossless lexical analysis for ASS Event Text.
 *
 * This deliberately does not normalize or rewrite text. Unknown tags remain valid lexical tags;
 * semantic editors can decide whether they understand them. The scanner exists so UI, diagnostics
 * and structured editors share one source of truth instead of duplicating ad-hoc regular expressions.
 */
object AssInlineSyntax {
    fun analyze(text: String): AssInlineAnalysis {
        if (text.isEmpty()) return AssInlineAnalysis(emptyList(), emptyList(), emptyList())

        val tokens = mutableListOf<AssInlineToken>()
        val tags = mutableListOf<AssInlineTag>()
        val issues = mutableListOf<AssInlineIssue>()

        var cursor = 0
        var plainStart = 0

        fun flushPlain(endExclusive: Int) {
            if (endExclusive > plainStart) {
                tokens += AssInlineToken(
                    kind = AssInlineTokenKind.TEXT,
                    start = plainStart,
                    endExclusive = endExclusive,
                    text = text.substring(plainStart, endExclusive),
                )
            }
        }

        while (cursor < text.length) {
            when {
                text[cursor] == '{' -> {
                    flushPlain(cursor)
                    val close = text.indexOf('}', cursor + 1)
                    if (close < 0) {
                        tokens += AssInlineToken(
                            kind = AssInlineTokenKind.MALFORMED_BLOCK,
                            start = cursor,
                            endExclusive = text.length,
                            text = text.substring(cursor),
                        )
                        issues += AssInlineIssue(
                            message = "未闭合的 ASS override block",
                            start = cursor,
                            endExclusive = text.length,
                        )
                        cursor = text.length
                        plainStart = cursor
                    } else {
                        val endExclusive = close + 1
                        tokens += AssInlineToken(
                            kind = AssInlineTokenKind.OVERRIDE_BLOCK,
                            start = cursor,
                            endExclusive = endExclusive,
                            text = text.substring(cursor, endExclusive),
                        )
                        scanBlock(text, cursor + 1, close, tokens, tags, issues)
                        cursor = endExclusive
                        plainStart = cursor
                    }
                }

                text[cursor] == '}' -> {
                    flushPlain(cursor)
                    tokens += AssInlineToken(
                        kind = AssInlineTokenKind.MALFORMED_BLOCK,
                        start = cursor,
                        endExclusive = cursor + 1,
                        text = "}",
                    )
                    issues += AssInlineIssue(
                        message = "孤立的右花括号",
                        start = cursor,
                        endExclusive = cursor + 1,
                    )
                    cursor += 1
                    plainStart = cursor
                }

                text[cursor] == '\\' &&
                    cursor + 1 < text.length &&
                    text[cursor + 1] in charArrayOf('N', 'n', 'h') -> {
                    flushPlain(cursor)
                    tokens += AssInlineToken(
                        kind = AssInlineTokenKind.ESCAPE,
                        start = cursor,
                        endExclusive = cursor + 2,
                        text = text.substring(cursor, cursor + 2),
                    )
                    cursor += 2
                    plainStart = cursor
                }

                else -> cursor += 1
            }
        }

        flushPlain(text.length)
        return AssInlineAnalysis(tokens, tags, issues)
    }

    private fun scanBlock(
        text: String,
        contentStart: Int,
        contentEndExclusive: Int,
        tokens: MutableList<AssInlineToken>,
        tags: MutableList<AssInlineTag>,
        issues: MutableList<AssInlineIssue>,
    ) {
        var cursor = contentStart
        while (cursor < contentEndExclusive) {
            if (text[cursor] != '\\') {
                cursor += 1
                continue
            }

            val tagStart = cursor
            cursor += 1
            val nameStart = cursor

            if (cursor < contentEndExclusive && text[cursor].isDigit()) {
                cursor += 1
            }
            while (cursor < contentEndExclusive && text[cursor].isLetter()) {
                cursor += 1
            }

            if (cursor == nameStart) {
                issues += AssInlineIssue(
                    message = "override block 中存在没有标签名的反斜杠",
                    start = tagStart,
                    endExclusive = (tagStart + 1).coerceAtMost(text.length),
                )
                continue
            }

            val name = text.substring(nameStart, cursor)
            tokens += AssInlineToken(
                kind = AssInlineTokenKind.TAG_NAME,
                start = tagStart,
                endExclusive = cursor,
                text = text.substring(tagStart, cursor),
            )

            val valueStart = cursor
            while (cursor < contentEndExclusive && text[cursor] != '\\') {
                cursor += 1
            }
            val rawValue = text.substring(valueStart, cursor)
            val leading = rawValue.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) rawValue.length else it }
            val trailingExclusive = rawValue.indexOfLast { !it.isWhitespace() }.let { if (it < 0) 0 else it + 1 }
            if (trailingExclusive > leading) {
                val actualStart = valueStart + leading
                val actualEnd = valueStart + trailingExclusive
                tokens += AssInlineToken(
                    kind = AssInlineTokenKind.TAG_VALUE,
                    start = actualStart,
                    endExclusive = actualEnd,
                    text = text.substring(actualStart, actualEnd),
                )
            }

            tags += AssInlineTag(
                name = name,
                value = rawValue.trim(),
                start = tagStart,
                endExclusive = cursor,
            )
        }
    }
}
