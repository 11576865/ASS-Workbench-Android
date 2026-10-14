package io.github.assworkbench.app.ui

import kotlin.math.min

/**
 * Layout-only projection of the approved-for-implementation R3 review drawing.
 * Reference canvas: 1600 × 960, after app/task bars:
 * Event navigator ≈267 px | video/time workspace ≈906 px | inspector ≈427 px.
 *
 * Do not store temporary viewport/IME measurements in the workspace snapshot.
 * Media and Timeline remain two independent, live surfaces with one document clock.
 */
internal data class R3FixedGeometry(
    val navigationWidthDp: Float,
    val inspectorWidthDp: Float,
    val mediaHeightDp: Float,
    val timelineHeightDp: Float,
)

internal object R3FixedLayoutPolicy {
    const val referenceWidth = 1600f
    const val navigationRatio = 267f / referenceWidth
    const val inspectorRatio = 427f / referenceWidth

    /**
     * Usable size excludes the global app bar and task bar. Caller must already
     * have established a supported three-pane window with adequate height.
     * A genuine aspect-ratio bar is not an invitation to stretch the video.
     */
    fun resolve(
        widthDp: Float,
        heightDp: Float,
        mediaAspect: Float,
        preview: PreviewDensity,
    ): R3FixedGeometry {
        val width = widthDp.coerceAtLeast(0f)
        val height = heightDp.coerceAtLeast(0f)
        val navigation = (width * navigationRatio).coerceIn(206f, 296f)
        val inspector = (width * inspectorRatio).coerceIn(328f, 460f)
        val mediaWidth = (width - navigation - inspector - 2f).coerceAtLeast(1f)
        val minTimeline = (height * 0.29f).coerceIn(184f, 320f)
        val targetAspect = mediaAspect.takeIf { it.isFinite() && it in 0.25f..4f } ?: 16f / 9f
        val mediaHeight = when (preview) {
            PreviewDensity.HIDDEN -> 0f
            PreviewDensity.COMPACT -> min(mediaWidth / targetAspect, height * 0.30f)
            PreviewDensity.FULL -> min(mediaWidth / targetAspect, (height - minTimeline).coerceAtLeast(0f))
        }.coerceIn(0f, height)
        return R3FixedGeometry(
            navigationWidthDp = navigation,
            inspectorWidthDp = inspector,
            mediaHeightDp = mediaHeight,
            timelineHeightDp = (height - mediaHeight).coerceAtLeast(0f),
        )
    }
}
