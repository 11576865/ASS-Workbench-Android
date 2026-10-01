package io.github.assworkbench.domain

enum class AssCompatibilityProfile { LIBASS_NATIVE, PORTABLE_CONSERVATIVE, VSFILTER_ORIENTED }
enum class AssCompatibilitySeverity { INFO, WARNING }

data class AssCompatibilityIssue(
    val eventId: Long?,
    val profile: AssCompatibilityProfile,
    val severity: AssCompatibilitySeverity,
    val code: String,
    val message: String,
)

object AssCompatibilityAnalyzer {
    private val vectorClip = Regex("""\\i?clip\([^)]*[mnlbspc][^)]*\)""", RegexOption.IGNORE_CASE)
    private val drawingMode = Regex("""\\p\s*[1-9]\d*""", RegexOption.IGNORE_CASE)
    private val shear = Regex("""\\fa[xy]""", RegexOption.IGNORE_CASE)
    private val blur = Regex("""\\blur""", RegexOption.IGNORE_CASE)
    private val transform = Regex("""\\t\(""", RegexOption.IGNORE_CASE)

    fun inspect(document: AssDocument, profile: AssCompatibilityProfile): List<AssCompatibilityIssue> {
        if (profile == AssCompatibilityProfile.LIBASS_NATIVE) return emptyList()
        val out = mutableListOf<AssCompatibilityIssue>()
        document.events.forEach { event ->
            val text = event.text
            if (vectorClip.containsMatchIn(text)) out += issue(event.id, profile, "VECTOR_CLIP", "使用 vector clip；跨渲染器几何与边界行为应实机核对。")
            if (drawingMode.containsMatchIn(text)) out += issue(event.id, profile, "DRAWING", "使用 ASS Drawing；不同渲染器的极端路径/缩放行为可能不同。")
            if (shear.containsMatchIn(text)) out += issue(event.id, profile, "SHEAR", "使用 \\fax/\\fay；便携性配置下应避免依赖边缘行为。")
            if (blur.containsMatchIn(text) && profile == AssCompatibilityProfile.VSFILTER_ORIENTED) {
                out += issue(event.id, profile, "BLUR", "使用 \\blur；旧式 VSFilter 路径的结果不应由 libass 预览直接代替判断。")
            }
            if (transform.findAll(text).count() > 1) out += issue(event.id, profile, "MULTI_TRANSFORM", "包含多个 \\t；跨渲染器动画组合建议人工复核。")
        }
        if (document.scriptInfo.keys.any { it.equals("LayoutResX", true) || it.equals("LayoutResY", true) }) {
            out += AssCompatibilityIssue(null, profile, AssCompatibilitySeverity.INFO, "LAYOUT_RES", "脚本使用 LayoutRes；导出到其他渲染链时应确认其支持程度。")
        }
        return out
    }

    private fun issue(id: Long, profile: AssCompatibilityProfile, code: String, message: String) =
        AssCompatibilityIssue(id, profile, AssCompatibilitySeverity.WARNING, code, message)
}
