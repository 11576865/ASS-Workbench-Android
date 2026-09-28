package io.github.assworkbench.domain

enum class AssQcSeverity { INFO, WARNING, ERROR }

enum class AssQcKind {
    EMPTY_TEXT,
    ZERO_DURATION,
    VERY_SHORT_DURATION,
    VERY_LONG_DURATION,
    OVERLAP,
    TINY_GAP,
    UNKNOWN_STYLE,
    INLINE_SYNTAX,
    INVALID_POSITION,
}

data class AssQcIssue(
    val eventId: Long,
    val kind: AssQcKind,
    val severity: AssQcSeverity,
    val message: String,
)

object AssQualityCheck {
    private val validPos = Regex("""\\pos\(\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*\)""", RegexOption.IGNORE_CASE)
    private val validMove = Regex(
        """\\move\(\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?(?:\s*,\s*\d+\s*,\s*\d+)?\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    fun inspect(
        document: AssDocument,
        veryShortMs: Long = 250L,
        veryLongMs: Long = 15_000L,
        tinyGapMs: Long = 80L,
    ): List<AssQcIssue> {
        val styleNames = document.styles.mapTo(hashSetOf()) { it.name }
        val out = mutableListOf<AssQcIssue>()

        document.events.forEach { event ->
            val visible = AssInlineSyntax.visibleText(event.text)
            val duration = event.end.millis - event.start.millis
            if (!event.comment && visible.isBlank()) {
                out += AssQcIssue(event.id, AssQcKind.EMPTY_TEXT, AssQcSeverity.WARNING, "可见正文为空")
            }
            if (duration == 0L) {
                out += AssQcIssue(event.id, AssQcKind.ZERO_DURATION, AssQcSeverity.ERROR, "持续时间为 0 ms")
            } else if (duration in 1L until veryShortMs) {
                out += AssQcIssue(event.id, AssQcKind.VERY_SHORT_DURATION, AssQcSeverity.WARNING, "持续时间仅 ${duration} ms")
            } else if (duration > veryLongMs) {
                out += AssQcIssue(event.id, AssQcKind.VERY_LONG_DURATION, AssQcSeverity.INFO, "持续时间 ${duration} ms")
            }
            if (event.style !in styleNames) {
                out += AssQcIssue(event.id, AssQcKind.UNKNOWN_STYLE, AssQcSeverity.ERROR, "引用不存在的 Style：${event.style}")
            }
            val analysis = AssInlineSyntax.analyze(event.text)
            analysis.issues.forEach { issue ->
                out += AssQcIssue(event.id, AssQcKind.INLINE_SYNTAX, AssQcSeverity.ERROR, issue.message)
            }
            val lower = event.text.lowercase()
            if ("\\pos(" in lower && !validPos.containsMatchIn(event.text)) {
                out += AssQcIssue(event.id, AssQcKind.INVALID_POSITION, AssQcSeverity.ERROR, "无效 \\pos 参数")
            }
            if ("\\move(" in lower && !validMove.containsMatchIn(event.text)) {
                out += AssQcIssue(event.id, AssQcKind.INVALID_POSITION, AssQcSeverity.ERROR, "无效 \\move 参数")
            }
        }

        val ordered = document.events.filterNot { it.comment }.sortedBy { it.start.millis }
        for (i in 0 until ordered.lastIndex) {
            val current = ordered[i]
            val next = ordered[i + 1]
            val gap = next.start.millis - current.end.millis
            if (gap < 0L) {
                out += AssQcIssue(
                    current.id,
                    AssQcKind.OVERLAP,
                    AssQcSeverity.WARNING,
                    "与下一条 #${next.id} 重叠 ${-gap} ms",
                )
            } else if (gap in 0L until tinyGapMs) {
                out += AssQcIssue(
                    current.id,
                    AssQcKind.TINY_GAP,
                    AssQcSeverity.INFO,
                    "与下一条 #${next.id} 间隔仅 ${gap} ms",
                )
            }
        }
        return out
    }
}
