package io.github.assworkbench.domain

enum class SubtitleCompatibilityProfile(val label: String) {
    LIBASS_ASS("ASS / libass"),
    SUBRIP("SubRip / SRT"),
    WEBVTT("WebVTT"),
}

enum class CompatibilitySeverity { INFO, WARNING, ERROR }

data class CompatibilityIssue(
    val profile: SubtitleCompatibilityProfile,
    val severity: CompatibilitySeverity,
    val eventId: Long? = null,
    val code: String,
    val message: String,
)

object SubtitleCompatibilityAnalyzer {
    fun inspect(
        document: AssDocument,
        profile: SubtitleCompatibilityProfile,
    ): List<CompatibilityIssue> = when (profile) {
        SubtitleCompatibilityProfile.LIBASS_ASS -> emptyList()
        SubtitleCompatibilityProfile.SUBRIP,
        SubtitleCompatibilityProfile.WEBVTT -> inspectPlainTextTarget(document, profile)
    }

    private fun inspectPlainTextTarget(
        document: AssDocument,
        profile: SubtitleCompatibilityProfile,
    ): List<CompatibilityIssue> {
        val out = mutableListOf<CompatibilityIssue>()
        if (document.styles.size > 1 || document.styles.firstOrNull() != AssStyle()) {
            out += CompatibilityIssue(
                profile,
                CompatibilitySeverity.WARNING,
                code = "STYLE_LOSS",
                message = "目标格式不能保留 ASS Style；导出时只保留可见正文与时间。",
            )
        }
        document.events.forEach { event ->
            if (event.comment) {
                out += CompatibilityIssue(
                    profile,
                    CompatibilitySeverity.INFO,
                    event.id,
                    "COMMENT_DROPPED",
                    "Comment Event 不会导出。",
                )
            }
            if (event.layer != 0 || event.marginL != 0 || event.marginR != 0 || event.marginV != 0) {
                out += CompatibilityIssue(
                    profile,
                    CompatibilitySeverity.WARNING,
                    event.id,
                    "EVENT_LAYOUT_LOSS",
                    "Layer / Event Margin 无法在目标格式中等价表达。",
                )
            }
            val analysis = AssInlineSyntax.analyze(event.text)
            if (analysis.tokens.any { it.kind == AssInlineTokenKind.OVERRIDE }) {
                out += CompatibilityIssue(
                    profile,
                    CompatibilitySeverity.WARNING,
                    event.id,
                    "ASS_OVERRIDE_LOSS",
                    "ASS override tags 将在导出时移除。",
                )
            }
            if ("\\N" in event.text || "\\n" in event.text) {
                // Supported as physical line breaks by both interchange codecs.
            }
            if ("\\h" in event.text) {
                out += CompatibilityIssue(
                    profile,
                    CompatibilitySeverity.INFO,
                    event.id,
                    "HARD_SPACE_NORMALIZED",
                    "\\h 将导出为空格。",
                )
            }
        }
        return out
    }
}
