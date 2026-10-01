package io.github.assworkbench.domain

enum class AssCompatibilityProfile {
    LIBASS_NATIVE,
    PORTABLE_CONSERVATIVE,
    VSFILTER_ORIENTED,
}

data class AssCompatibilityIssue(
    val eventId: Long?,
    val code: String,
    val message: String,
)

object AssCompatibilityAnalyzer {
    private val advancedTags = Regex("""\\(?:frx|fry|fax|fay|blur|be|xbord|ybord|xshad|yshad)\b""", RegexOption.IGNORE_CASE)
    private val vectorClip = Regex("""\\i?clip\(\s*(?:\d+\s*,\s*)?\s*[mnlbspc]\b""", RegexOption.IGNORE_CASE)
    private val drawing = Regex("""\\p\s*[1-9]\d*""", RegexOption.IGNORE_CASE)

    fun inspect(document: AssDocument, profile: AssCompatibilityProfile): List<AssCompatibilityIssue> {
        if (profile == AssCompatibilityProfile.LIBASS_NATIVE) return emptyList()
        val out = mutableListOf<AssCompatibilityIssue>()
        document.events.forEach { event ->
            val text = event.text
            if (vectorClip.containsMatchIn(text)) {
                out += AssCompatibilityIssue(event.id, "vector-clip", "Vector clip 建议在目标 renderer 上交叉验证。")
            }
            if (drawing.containsMatchIn(text)) {
                out += AssCompatibilityIssue(event.id, "drawing", "ASS Drawing 的边缘行为可能依赖 renderer。")
            }
            if (advancedTags.containsMatchIn(text) && profile == AssCompatibilityProfile.PORTABLE_CONSERVATIVE) {
                out += AssCompatibilityIssue(event.id, "advanced-tag", "使用了保守兼容模式之外的高级视觉 tag。")
            }
            if ("\\t(" in text && ("\\clip(" in text || "\\iclip(" in text)) {
                out += AssCompatibilityIssue(event.id, "animated-clip", "动画 clip 建议在目标 renderer 上检查。")
            }
        }
        if (document.scriptInfo.keys.any { it.equals("LayoutResX", true) || it.equals("LayoutResY", true) }) {
            out += AssCompatibilityIssue(null, "layoutres", "LayoutRes* 并非所有历史 renderer 都一致支持。")
        }
        return out
    }
}
