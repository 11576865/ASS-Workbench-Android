package io.github.assworkbench.app.ui.preview

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEffectiveInspector
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssPoint
import io.github.assworkbench.domain.AssPositionMode
import io.github.assworkbench.domain.SubTime
import kotlin.math.hypot

/**
 * Preview-object discovery is intentionally anchor based, not glyph/bbox based.
 *
 * libass remains the rendering authority. This resolver only narrows the active
 * Event set and ranks candidates by their ASS anchor. It never claims pixel-accurate
 * glyph hit testing.
 */
internal enum class PreviewTargetConfidence {
    EXACT_ANCHOR,
    APPROXIMATE_ANCHOR,
    UNRESOLVED,
}

internal data class PreviewTargetCandidate(
    val eventId: Long,
    val styleName: String,
    val layer: Int,
    val anchor: AssPoint?,
    val confidence: PreviewTargetConfidence,
    val distance: Double,
)

internal object PreviewTargetResolver {
    fun candidates(
        document: AssDocument,
        positionMs: Long,
        x: Double,
        y: Double,
        limit: Int = 8,
    ): List<PreviewTargetCandidate> {
        val point = AssPoint(x, y)
        return document.activeEvents(SubTime(positionMs.coerceAtLeast(0L)))
            .asSequence()
            .filterNot(AssEvent::comment)
            .map { event -> candidate(document, event, positionMs, point) }
            .sortedWith(
                compareBy<PreviewTargetCandidate> { it.confidence.ordinal }
                    .thenBy { it.distance }
                    .thenByDescending { it.layer }
                    .thenBy { it.eventId }
            )
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    private fun candidate(
        document: AssDocument,
        event: AssEvent,
        positionMs: Long,
        point: AssPoint,
    ): PreviewTargetCandidate {
        val geometry = AssGeometrySemantic.inspect(event.text)
        val resolved = when (geometry.positionMode) {
            AssPositionMode.POSITION -> geometry.position to PreviewTargetConfidence.EXACT_ANCHOR
            AssPositionMode.MOVE -> movePoint(event, geometry.move, positionMs) to PreviewTargetConfidence.EXACT_ANCHOR
            AssPositionMode.INHERITED -> inheritedAnchor(document, event) to PreviewTargetConfidence.APPROXIMATE_ANCHOR
            AssPositionMode.CONFLICT -> null to PreviewTargetConfidence.UNRESOLVED
        }
        val anchor = resolved.first
        val distance = anchor?.let { hypot(it.x - point.x, it.y - point.y) } ?: Double.POSITIVE_INFINITY
        return PreviewTargetCandidate(
            eventId = event.id,
            styleName = event.style,
            layer = event.layer,
            anchor = anchor,
            confidence = resolved.second,
            distance = distance,
        )
    }

    private fun movePoint(
        event: AssEvent,
        move: io.github.assworkbench.domain.AssMove?,
        positionMs: Long,
    ): AssPoint? {
        move ?: return null
        val eventDuration = (event.end.millis - event.start.millis).coerceAtLeast(1L).toDouble()
        val relative = (positionMs - event.start.millis).coerceAtLeast(0L).toDouble()
        val start = move.startMs ?: 0.0
        val end = move.endMs ?: eventDuration
        val t = when {
            end <= start -> if (relative >= end) 1.0 else 0.0
            else -> ((relative - start) / (end - start)).coerceIn(0.0, 1.0)
        }
        return AssPoint(
            x = move.start.x + (move.end.x - move.start.x) * t,
            y = move.start.y + (move.end.y - move.start.y) * t,
        )
    }

    private fun inheritedAnchor(document: AssDocument, event: AssEvent): AssPoint? {
        val effective = AssEffectiveInspector.inspect(document, event).associateBy { it.name }
        val alignment = effective["Alignment"]?.effectiveValue?.toIntOrNull()?.coerceIn(1, 9) ?: return null
        val marginL = effective["Margin L"]?.effectiveValue?.toDoubleOrNull() ?: return null
        val marginR = effective["Margin R"]?.effectiveValue?.toDoubleOrNull() ?: return null
        val marginV = effective["Margin V"]?.effectiveValue?.toDoubleOrNull() ?: return null

        val x = when (alignment) {
            1, 4, 7 -> marginL
            2, 5, 8 -> document.playResX / 2.0
            else -> document.playResX - marginR
        }
        val y = when (alignment) {
            7, 8, 9 -> marginV
            4, 5, 6 -> document.playResY / 2.0
            else -> document.playResY - marginV
        }
        return AssPoint(x, y)
    }
}
