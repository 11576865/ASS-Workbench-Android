package io.github.assworkbench.domain

enum class AssLintCategory { TIMING, TEXT, SYNTAX, STYLE, GEOMETRY, FONT, RENDERER, COMPATIBILITY }

sealed interface AssQuickFix {
    val label: String
    fun apply(document: AssDocument, eventId: Long): AssDocument

    data object ExtendZeroDuration : AssQuickFix {
        override val label = "延长到 500 ms"
        override fun apply(document: AssDocument, eventId: Long) = document.copy(events = document.events.map {
            if (it.id == eventId && it.end == it.start) it.copy(end = SubTime(it.start.millis + 500)) else it
        })
    }
    data object UseDefaultStyle : AssQuickFix {
        override val label = "改用可用 Style"
        override fun apply(document: AssDocument, eventId: Long): AssDocument {
            val replacement = document.styles.firstOrNull { it.name == "Default" }?.name
                ?: document.styles.firstOrNull()?.name
                ?: return document
            return document.copy(events = document.events.map {
                if (it.id == eventId) it.copy(style = replacement) else it
            })
        }
    }
    data object TrimVisibleWhitespace : AssQuickFix {
        override val label = "清理首尾空白"
        override fun apply(document: AssDocument, eventId: Long) = document.copy(events = document.events.map {
            if (it.id == eventId) it.copy(text = it.text.trim()) else it
        })
    }
}

data class AssLintIssue(
    val eventId: Long?,
    val code: String,
    val category: AssLintCategory,
    val severity: AssQcSeverity,
    val message: String,
    val quickFix: AssQuickFix? = null,
)

data class AssLintConfig(
    val maxCps: Double = 25.0,
    val maxVisibleCharacters: Int = 84,
    val compatibilityProfile: AssCompatibilityProfile? = null,
)

object AssLinter {
    private val pos = Regex("""\\pos\(""", RegexOption.IGNORE_CASE)
    private val move = Regex("""\\move\(""", RegexOption.IGNORE_CASE)

    fun inspect(document: AssDocument, config: AssLintConfig = AssLintConfig()): List<AssLintIssue> {
        val out = mutableListOf<AssLintIssue>()
        AssQualityCheck.inspect(document).forEach { issue ->
            out += AssLintIssue(
                issue.eventId, "QC.${issue.kind.name}", category(issue.kind), issue.severity, issue.message,
                when (issue.kind) {
                    AssQcKind.ZERO_DURATION -> AssQuickFix.ExtendZeroDuration
                    AssQcKind.UNKNOWN_STYLE -> AssQuickFix.UseDefaultStyle
                    else -> null
                }
            )
        }
        document.events.filterNot { it.comment }.forEach { event ->
            val visible = AssInlineSyntax.visibleText(event.text)
            val durationSeconds = (event.end.millis - event.start.millis) / 1000.0
            if (durationSeconds > 0.0) {
                val cps = visible.count { !it.isWhitespace() } / durationSeconds
                if (cps > config.maxCps) out += AssLintIssue(event.id, "TEXT.CPS_HIGH", AssLintCategory.TEXT, AssQcSeverity.WARNING,
                    "阅读速度 %.1f CPS，高于 %.1f".format(cps, config.maxCps))
            }
            if (visible.length > config.maxVisibleCharacters) out += AssLintIssue(event.id, "TEXT.LONG", AssLintCategory.TEXT,
                AssQcSeverity.INFO, "可见字符 ${visible.length}，超过 ${config.maxVisibleCharacters}")
            if (event.text != event.text.trim()) out += AssLintIssue(event.id, "TEXT.OUTER_WHITESPACE", AssLintCategory.TEXT,
                AssQcSeverity.INFO, "Event Text 存在首尾空白", AssQuickFix.TrimVisibleWhitespace)
            if (pos.containsMatchIn(event.text) && move.containsMatchIn(event.text)) out += AssLintIssue(event.id,
                "GEOMETRY.POS_MOVE_CONFLICT", AssLintCategory.GEOMETRY, AssQcSeverity.WARNING,
                "同时存在 \\pos 与 \\move；有效结果依赖标签顺序，应人工确认。")
        }
        val usedStyles = document.events.mapTo(hashSetOf()) { it.style }
        document.styles.filter { it.name !in usedStyles && it.name != "Default" }.forEach { style ->
            out += AssLintIssue(null, "STYLE.UNUSED", AssLintCategory.STYLE, AssQcSeverity.INFO, "Style 未被 Event 直接引用：${style.name}")
        }
        config.compatibilityProfile?.let { profile ->
            AssCompatibilityAnalyzer.inspect(document, profile).forEach { issue ->
                out += AssLintIssue(issue.eventId, "COMPAT.${issue.code}", AssLintCategory.COMPATIBILITY, AssQcSeverity.WARNING, issue.message)
            }
        }
        return out
    }

    private fun category(kind: AssQcKind): AssLintCategory = when (kind) {
        AssQcKind.EMPTY_TEXT, AssQcKind.INLINE_SYNTAX -> AssLintCategory.SYNTAX
        AssQcKind.ZERO_DURATION, AssQcKind.VERY_SHORT_DURATION, AssQcKind.VERY_LONG_DURATION,
        AssQcKind.OVERLAP, AssQcKind.TINY_GAP -> AssLintCategory.TIMING
        AssQcKind.UNKNOWN_STYLE -> AssLintCategory.STYLE
        AssQcKind.INVALID_POSITION -> AssLintCategory.GEOMETRY
        AssQcKind.RENDERER_RISK -> AssLintCategory.RENDERER
    }
}
