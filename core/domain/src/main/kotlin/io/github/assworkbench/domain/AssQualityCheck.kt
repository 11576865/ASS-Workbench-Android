package io.github.assworkbench.domain

enum class AssQcSeverity { INFO, WARNING, ERROR }

enum class AssQcKind {
    EMPTY_TEXT,
    ZERO_DURATION,
    VERY_SHORT_DURATION,
    VERY_LONG_DURATION,
    HIGH_READING_SPEED,
    TOO_MANY_LINES,
    OVERLAP,
    TINY_GAP,
    UNKNOWN_STYLE,
    INLINE_SYNTAX,
    INVALID_POSITION,
    CONFLICTING_POSITION,
    OUT_OF_PLAYRES,
    DUPLICATE_POSITION_TAG,
    RENDERER_RISK,
}

enum class AssQcQuickFix(val label: String) {
    SET_DEFAULT_STYLE("改为默认 Style"),
    EXTEND_TO_MINIMUM("延长到最小时长"),
    REMOVE_INVALID_POSITIONING("移除无效定位 tag"),
}

data class AssQcIssue(
    val eventId: Long,
    val kind: AssQcKind,
    val severity: AssQcSeverity,
    val message: String,
    val ruleId: String = "ASS." + kind.name,
    val quickFix: AssQcQuickFix? = null,
)

object AssQualityCheck {
    private val validPos = Regex(
        """\\pos\(\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val validMove = Regex(
        """\\move\(\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?(?:\s*,\s*\d+\s*,\s*\d+)?\s*\)""",
        RegexOption.IGNORE_CASE,
    )

    fun inspect(
        document: AssDocument,
        veryShortMs: Long = 250L,
        veryLongMs: Long = 15_000L,
        tinyGapMs: Long = 80L,
        highCps: Double = 25.0,
        maxVisibleLines: Int = 2,
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
                out += AssQcIssue(
                    event.id,
                    AssQcKind.ZERO_DURATION,
                    AssQcSeverity.ERROR,
                    "持续时间为 0 ms",
                    quickFix = AssQcQuickFix.EXTEND_TO_MINIMUM,
                )
            } else if (duration in 1L until veryShortMs) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.VERY_SHORT_DURATION,
                    AssQcSeverity.WARNING,
                    "持续时间仅 ${duration} ms",
                    quickFix = AssQcQuickFix.EXTEND_TO_MINIMUM,
                )
            } else if (duration > veryLongMs) {
                out += AssQcIssue(event.id, AssQcKind.VERY_LONG_DURATION, AssQcSeverity.INFO, "持续时间 ${duration} ms")
            }
            if (!event.comment && duration > 0L) {
                val visibleChars = visible.replace(Regex("""\s+"""), "").length
                val cps = visibleChars * 1000.0 / duration
                if (cps > highCps) {
                    out += AssQcIssue(
                        event.id,
                        AssQcKind.HIGH_READING_SPEED,
                        AssQcSeverity.WARNING,
                        "阅读速度 %.1f CPS，高于 %.1f".format(cps, highCps),
                    )
                }
                val lines = event.text.split(Regex("""\\[Nn]""")).size
                if (lines > maxVisibleLines) {
                    out += AssQcIssue(
                        event.id,
                        AssQcKind.TOO_MANY_LINES,
                        AssQcSeverity.INFO,
                        "可见行数 $lines，高于规则阈值 $maxVisibleLines",
                    )
                }
            }
            if (event.style !in styleNames) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.UNKNOWN_STYLE,
                    AssQcSeverity.ERROR,
                    "引用不存在的 Style：${event.style}",
                    quickFix = AssQcQuickFix.SET_DEFAULT_STYLE,
                )
            }
            val analysis = AssInlineSyntax.analyze(event.text)
            analysis.issues.forEach { issue ->
                out += AssQcIssue(event.id, AssQcKind.INLINE_SYNTAX, AssQcSeverity.ERROR, issue.message)
            }

            val lower = event.text.lowercase()
            val hasPos = "\\pos(" in lower
            val hasMove = "\\move(" in lower
            val posMatches = validPos.findAll(event.text).toList()
            val moveMatches = validMove.findAll(event.text).toList()
            if (hasPos && posMatches.isEmpty()) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.INVALID_POSITION,
                    AssQcSeverity.ERROR,
                    "无效 \\pos 参数",
                    quickFix = AssQcQuickFix.REMOVE_INVALID_POSITIONING,
                )
            }
            if (hasMove && moveMatches.isEmpty()) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.INVALID_POSITION,
                    AssQcSeverity.ERROR,
                    "无效 \\move 参数",
                    quickFix = AssQcQuickFix.REMOVE_INVALID_POSITIONING,
                )
            }
            if (posMatches.isNotEmpty() && moveMatches.isNotEmpty()) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.CONFLICTING_POSITION,
                    AssQcSeverity.WARNING,
                    "\\pos 与 \\move 同时存在；不同 renderer/编辑链可能产生歧义。",
                )
            }
            if (posMatches.size > 1 || moveMatches.size > 1) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.DUPLICATE_POSITION_TAG,
                    AssQcSeverity.WARNING,
                    "同一 Event 存在多个定位 tag；最终生效值依赖 ASS 解释顺序。",
                )
            }
            posMatches.forEach { match ->
                val x = match.groupValues[1].toDoubleOrNull()
                val y = match.groupValues[2].toDoubleOrNull()
                if (x != null && y != null &&
                    (x < 0.0 || x > document.playResX.toDouble() || y < 0.0 || y > document.playResY.toDouble())) {
                    out += AssQcIssue(
                        event.id,
                        AssQcKind.OUT_OF_PLAYRES,
                        AssQcSeverity.INFO,
                        "\\pos($x,$y) 位于 PlayRes ${document.playResX}×${document.playResY} 之外。",
                    )
                }
            }
        }

        AssRendererRiskAnalyzer.inspect(document).forEach { risk ->
            out += AssQcIssue(
                eventId = risk.eventId,
                kind = AssQcKind.RENDERER_RISK,
                severity = AssQcSeverity.ERROR,
                message = risk.message,
            )
        }

        AssTimelineRelations.analyze(document.events).forEach { relation ->
            when (relation.kind) {
                AssTimelineRelationKind.OVERLAP -> out += AssQcIssue(
                    relation.previousEventId,
                    AssQcKind.OVERLAP,
                    AssQcSeverity.WARNING,
                    "与 #${relation.eventId} 重叠 ${relation.durationMs} ms",
                )
                AssTimelineRelationKind.GAP -> if (relation.durationMs < tinyGapMs) {
                    out += AssQcIssue(
                        relation.previousEventId,
                        AssQcKind.TINY_GAP,
                        AssQcSeverity.INFO,
                        "与 #${relation.eventId} 间隔仅 ${relation.durationMs} ms",
                    )
                }
                AssTimelineRelationKind.TOUCH -> if (tinyGapMs > 0L) {
                    out += AssQcIssue(
                        relation.previousEventId,
                        AssQcKind.TINY_GAP,
                        AssQcSeverity.INFO,
                        "与 #${relation.eventId} 间隔仅 0 ms",
                    )
                }
            }
        }
        return out
    }
}

object AssQualityFixes {
    private val invalidPositioning = Regex(
        """\\(?:pos|move)\([^}]*\)""",
        RegexOption.IGNORE_CASE,
    )

    fun apply(
        document: AssDocument,
        issue: AssQcIssue,
        minimumDurationMs: Long = 250L,
    ): AssDocument {
        val fix = issue.quickFix ?: return document
        val defaultStyle = document.styles.firstOrNull()?.name ?: "Default"
        return document.copy(events = document.events.map { event ->
            if (event.id != issue.eventId) return@map event
            when (fix) {
                AssQcQuickFix.SET_DEFAULT_STYLE -> event.copy(style = defaultStyle)
                AssQcQuickFix.EXTEND_TO_MINIMUM -> {
                    val current = event.end.millis - event.start.millis
                    if (current >= minimumDurationMs) event
                    else event.copy(end = SubTime(event.start.millis + minimumDurationMs))
                }
                AssQcQuickFix.REMOVE_INVALID_POSITIONING -> {
                    event.copy(text = rewriteOverrideBlocks(event.text) { block ->
                        invalidPositioning.replace(block, "")
                    })
                }
            }
        })
    }

    private fun rewriteOverrideBlocks(text: String, transform: (String) -> String): String {
        val out = StringBuilder(text.length)
        var cursor = 0
        while (cursor < text.length) {
            val open = text.indexOf('{', cursor)
            if (open < 0) {
                out.append(text.substring(cursor))
                break
            }
            out.append(text.substring(cursor, open))
            val close = text.indexOf('}', open + 1)
            if (close < 0) {
                out.append(text.substring(open))
                break
            }
            val transformed = transform(text.substring(open, close + 1))
            if (transformed != "{}") out.append(transformed)
            cursor = close + 1
        }
        return out.toString()
    }
}
