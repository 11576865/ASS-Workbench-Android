package io.github.assworkbench.domain

enum class AssQcSeverity { INFO, WARNING, ERROR }

enum class AssQcKind {
    EMPTY_TEXT,
    ZERO_DURATION,
    VERY_SHORT_DURATION,
    VERY_LONG_DURATION,
    READING_SPEED,
    OVERLAP,
    TINY_GAP,
    UNKNOWN_STYLE,
    INLINE_SYNTAX,
    INVALID_POSITION,
    POSITION_MOVE_CONFLICT,
    OUTSIDE_PLAYRES,
    COMPATIBILITY,
    RENDERER_RISK,
}

data class AssQuickFix(
    val id: String,
    val label: String,
)

data class AssQcIssue(
    val eventId: Long,
    val kind: AssQcKind,
    val severity: AssQcSeverity,
    val message: String,
    val ruleId: String = "ass." + kind.name.lowercase(),
    val quickFixes: List<AssQuickFix> = emptyList(),
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
        maxReadingCps: Double = 22.0,
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
                    quickFixes = listOf(AssQuickFix("extend-1000ms", "延长到 1000 ms")),
                )
            } else if (duration in 1L until veryShortMs) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.VERY_SHORT_DURATION,
                    AssQcSeverity.WARNING,
                    "持续时间仅 " + duration + " ms",
                    quickFixes = listOf(AssQuickFix("extend-1000ms", "延长到至少 1000 ms")),
                )
            } else if (duration > veryLongMs) {
                out += AssQcIssue(event.id, AssQcKind.VERY_LONG_DURATION, AssQcSeverity.INFO, "持续时间 " + duration + " ms")
            }

            if (!event.comment && duration > 0L) {
                val readableChars = visible.count { !it.isWhitespace() }
                val cps = readableChars * 1000.0 / duration
                if (cps > maxReadingCps) {
                    out += AssQcIssue(
                        event.id,
                        AssQcKind.READING_SPEED,
                        AssQcSeverity.WARNING,
                        "阅读速度 %.1f chars/s，超过 %.1f chars/s".format(cps, maxReadingCps),
                    )
                }
            }

            if (event.style !in styleNames) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.UNKNOWN_STYLE,
                    AssQcSeverity.ERROR,
                    "引用不存在的 Style：" + event.style,
                    quickFixes = if ("Default" in styleNames) {
                        listOf(AssQuickFix("set-default-style", "改为 Default"))
                    } else emptyList(),
                )
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

            val geometry = AssGeometrySemantic.inspect(event.text)
            if (geometry.positionMode == AssPositionMode.CONFLICT) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.POSITION_MOVE_CONFLICT,
                    AssQcSeverity.ERROR,
                    "同一 Event 的 leading overrides 同时包含 \\pos 与 \\move",
                    quickFixes = listOf(
                        AssQuickFix("remove-pos", "保留 move，移除 pos"),
                        AssQuickFix("remove-move", "保留 pos，移除 move"),
                    ),
                )
            }

            val pos = geometry.position
            if (pos != null && (pos.x !in 0.0..document.playResX.toDouble() || pos.y !in 0.0..document.playResY.toDouble())) {
                out += AssQcIssue(
                    event.id,
                    AssQcKind.OUTSIDE_PLAYRES,
                    AssQcSeverity.WARNING,
                    "\\pos 位于 PlayRes 范围外：(" + pos.x + ", " + pos.y + ")",
                    quickFixes = listOf(AssQuickFix("clamp-pos", "限制到 PlayRes 范围")),
                )
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
                    "与 #" + relation.eventId + " 重叠 " + relation.durationMs + " ms",
                )
                AssTimelineRelationKind.GAP -> if (relation.durationMs < tinyGapMs) {
                    out += AssQcIssue(
                        relation.previousEventId,
                        AssQcKind.TINY_GAP,
                        AssQcSeverity.INFO,
                        "与 #" + relation.eventId + " 间隔仅 " + relation.durationMs + " ms",
                    )
                }
                AssTimelineRelationKind.TOUCH -> if (tinyGapMs > 0L) {
                    out += AssQcIssue(
                        relation.previousEventId,
                        AssQcKind.TINY_GAP,
                        AssQcSeverity.INFO,
                        "与 #" + relation.eventId + " 间隔仅 0 ms",
                    )
                }
            }
        }
        return out
    }
}

object AssQuickFixExecutor {
    fun apply(document: AssDocument, eventId: Long, fixId: String): AssDocument {
        val event = document.events.firstOrNull { it.id == eventId } ?: return document
        val nextEvent = when (fixId) {
            "extend-1000ms" -> {
                val targetEnd = maxOf(event.end.millis, event.start.millis + 1000L)
                event.copy(end = SubTime(targetEnd))
            }
            "set-default-style" -> {
                if (document.styles.none { it.name == "Default" }) return document
                event.copy(style = "Default")
            }
            "remove-pos" -> event.copy(text = AssGeometrySemantic.removePosition(event.text))
            "remove-move" -> event.copy(text = AssGeometrySemantic.removeMove(event.text))
            "clamp-pos" -> {
                val pos = AssGeometrySemantic.inspect(event.text).position ?: return document
                event.copy(
                    text = AssGeometrySemantic.patchPosition(
                        event.text,
                        pos.x.coerceIn(0.0, document.playResX.toDouble()),
                        pos.y.coerceIn(0.0, document.playResY.toDouble()),
                    )
                )
            }
            else -> return document
        }
        if (nextEvent == event) return document
        return document.copy(events = document.events.map { if (it.id == eventId) nextEvent else it })
    }
}
