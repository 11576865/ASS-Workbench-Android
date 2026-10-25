package io.github.assworkbench.app.ui

import kotlin.math.min

/** Figma 85:3: 56 rail | 309 events | 749 center | 326 inspector at 1440px. */
internal object FixedV11LayoutPolicy {
    const val railWidthDp = 56f

    fun resolve(widthDp: Float, heightDp: Float, mediaAspect: Float,
        preview: PreviewDensity, timingPriority: Boolean = false,
        textDockRequested: Boolean = false, compact: Boolean = false): R3FixedGeometry {
        val width = widthDp.coerceAtLeast(0f)
        val height = heightDp.coerceAtLeast(0f)
        val content = (width - railWidthDp).coerceAtLeast(0f)
        val events = if (compact) 0f else (content * 309f / 1384f).coerceIn(240f, 340f)
        val inspector = if (compact) (content * 310f / 968f).coerceIn(300f, 326f)
            else (content * 326f / 1384f).coerceIn(300f, 370f)
        val center = (content - events - inspector - 2f).coerceAtLeast(1f)
        val dock = if (textDockRequested)
            (height * 0.28f).coerceIn(184f, 240f).coerceAtMost(height * 0.38f) else 0f
        val remaining = (height - dock - if (dock > 0f) 2f else 1f).coerceAtLeast(0f)
        val timelineMin = min(184f, remaining)
        val aspect = mediaAspect.takeIf { it.isFinite() && it in 0.25f..4f } ?: (16f / 9f)
        val cap = when {
            timingPriority -> remaining * 0.34f
            compact -> height * 0.35f
            else -> height * 0.46f
        }
        val media = when (preview) {
            PreviewDensity.HIDDEN -> 0f
            PreviewDensity.COMPACT -> min(center / aspect + 48f, remaining * 0.30f)
            PreviewDensity.FULL -> min(center / aspect + 48f, cap)
        }.coerceIn(0f, (remaining - timelineMin).coerceAtLeast(0f))
        return R3FixedGeometry(events, inspector, media, remaining - media, dock)
    }
}
