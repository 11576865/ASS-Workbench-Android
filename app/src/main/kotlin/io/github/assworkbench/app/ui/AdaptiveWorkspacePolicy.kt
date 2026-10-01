package io.github.assworkbench.app.ui

/**
 * Presentation policy for the adaptive editor host.
 *
 * The policy consumes both width and usable height. IME pressure is treated as a
 * temporary presentation constraint: it can compact the preview, but it never
 * rewrites the user's requested preview mode or any Workspace geometry.
 */
internal enum class WorkbenchLayoutProfile {
    COMPACT,
    DUAL_PANE,
    THREE_PANE,
}

internal enum class PreviewDensity(val label: String) {
    FULL("完整预览"),
    COMPACT("紧凑预览"),
    HIDDEN("隐藏预览");

    fun next(): PreviewDensity = entries[(ordinal + 1) % entries.size]
}

internal enum class ToolHostHint {
    INLINE,
    SIDE_INSPECTOR,
    BOTTOM_LAYER,
}

internal data class WorkbenchLayoutDecision(
    val profile: WorkbenchLayoutProfile,
    val effectivePreview: PreviewDensity,
    val previewHeightDp: Float,
    val navigationFraction: Float,
    val inspectorFraction: Float,
    val hostHint: ToolHostHint,
)

/**
 * Pure, testable workspace policy. It intentionally avoids device labels such as
 * "phone" and "tablet"; the same window can change strategy when height, IME or
 * multi-window constraints change.
 */
internal object AdaptiveWorkspacePolicy {
    fun resolve(
        widthDp: Float,
        heightDp: Float,
        imeVisible: Boolean,
        requestedPreview: PreviewDensity,
    ): WorkbenchLayoutDecision {
        val width = widthDp.coerceAtLeast(0f)
        val height = heightDp.coerceAtLeast(0f)
        val short = height < 520f || imeVisible
        val profile = when {
            width >= 1180f && height >= 560f && !imeVisible -> WorkbenchLayoutProfile.THREE_PANE
            width >= 720f && height >= 420f -> WorkbenchLayoutProfile.DUAL_PANE
            else -> WorkbenchLayoutProfile.COMPACT
        }

        val effectivePreview = when {
            requestedPreview == PreviewDensity.HIDDEN -> PreviewDensity.HIDDEN
            imeVisible && requestedPreview == PreviewDensity.FULL -> PreviewDensity.COMPACT
            height < 420f && requestedPreview == PreviewDensity.FULL -> PreviewDensity.COMPACT
            else -> requestedPreview
        }

        val previewHeight = when (effectivePreview) {
            PreviewDensity.FULL -> when (profile) {
                WorkbenchLayoutProfile.COMPACT -> 196f
                WorkbenchLayoutProfile.DUAL_PANE -> 228f
                WorkbenchLayoutProfile.THREE_PANE -> height
            }
            PreviewDensity.COMPACT -> if (short) 112f else 136f
            PreviewDensity.HIDDEN -> 0f
        }

        return when (profile) {
            WorkbenchLayoutProfile.THREE_PANE -> WorkbenchLayoutDecision(
                profile = profile,
                effectivePreview = effectivePreview,
                previewHeightDp = previewHeight,
                navigationFraction = 0.28f,
                inspectorFraction = 0.38f,
                hostHint = ToolHostHint.SIDE_INSPECTOR,
            )
            WorkbenchLayoutProfile.DUAL_PANE -> WorkbenchLayoutDecision(
                profile = profile,
                effectivePreview = effectivePreview,
                previewHeightDp = previewHeight,
                navigationFraction = if (short) 0.62f else 0.54f,
                inspectorFraction = if (short) 0.50f else 0.46f,
                hostHint = ToolHostHint.SIDE_INSPECTOR,
            )
            WorkbenchLayoutProfile.COMPACT -> WorkbenchLayoutDecision(
                profile = profile,
                effectivePreview = effectivePreview,
                previewHeightDp = previewHeight,
                navigationFraction = 1f,
                inspectorFraction = 1f,
                hostHint = if (imeVisible) ToolHostHint.BOTTOM_LAYER else ToolHostHint.INLINE,
            )
        }
    }
}
