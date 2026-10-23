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
    val textDockHeightDp: Float = 0f,
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
        timingPriority: Boolean = false,
        textDockRequested: Boolean = false,
    ): R3FixedGeometry {
        val width = widthDp.coerceAtLeast(0f)
        val totalHeight = heightDp.coerceAtLeast(0f)
        val textDock = if (textDockRequested)
            (totalHeight * 0.30f).coerceIn(200f, 280f).coerceAtMost(totalHeight * 0.40f)
            else 0f
        val height = (totalHeight - textDock).coerceAtLeast(0f)
        val navigation = (width * navigationRatio).coerceIn(206f, 296f)
        val inspector = (width * inspectorRatio).coerceIn(328f, 460f)
        val mediaWidth = (width - navigation - inspector - 2f).coerceAtLeast(1f)
        val minTimeline = (height * 0.29f).coerceIn(184f, 320f)
        val targetAspect = mediaAspect.takeIf { it.isFinite() && it in 0.25f..4f } ?: 16f / 9f
        // R3 design puts a 48dp native playback strip immediately BELOW video.
        // Budget it together with the displayed (un-stretched) image.
        val mediaWithTransport = mediaWidth / targetAspect + 48f
        val mediaHeight = when (preview) {
            PreviewDensity.HIDDEN -> 0f
            PreviewDensity.COMPACT -> min(mediaWithTransport, height * 0.36f)
            PreviewDensity.FULL -> min(mediaWithTransport, (height - minTimeline).coerceAtLeast(0f))
        }
        // V1.1 precise timing allocates more area to the SAME timeline.
        // The media clock, Event identity, and raw draft remain unchanged.
        val timingCap = if (timingPriority) height * 0.34f else height
        val boundedMediaHeight = mediaHeight.coerceAtMost(timingCap).coerceIn(0f, height)
        return R3FixedGeometry(
            navigationWidthDp = navigation,
            inspectorWidthDp = inspector,
            mediaHeightDp = boundedMediaHeight,
            timelineHeightDp = (height - boundedMediaHeight).coerceAtLeast(0f),
            textDockHeightDp = textDock,
        )
    }
}
