package io.github.assworkbench.app.ui.preview

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssInlineSyntax
import io.github.assworkbench.domain.AssPoint
import io.github.assworkbench.domain.AssPositionMode
import io.github.assworkbench.domain.AssTopLevelOverrideSyntax
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
    val textLabel: String,
    val layer: Int,
    val anchor: AssPoint?,
    val confidence: PreviewTargetConfidence,
    val distance: Double,
)

internal object PreviewTargetResolver {
    /** Static parameter editing never silently replaces motion/conflicting/late anchors. */
    fun staticAnchor(document: AssDocument, event: AssEvent): AssPoint? {
        if (hasLateAnchorTag(event.text)) return null
        val geometry = AssGeometrySemantic.inspect(event.text)
        return when (geometry.positionMode) {
            AssPositionMode.POSITION -> geometry.position?.takeIf(::isFinitePoint)
            AssPositionMode.INHERITED -> inheritedAnchor(document, event)
            else -> null
        }
    }

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
                compareBy<PreviewTargetCandidate> { it.distance }
                    .thenBy { it.confidence.ordinal }
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
        val resolved = if (hasLateAnchorTag(event.text)) {
            null to PreviewTargetConfidence.UNRESOLVED
        } else when (geometry.positionMode) {
            AssPositionMode.POSITION -> geometry.position
                ?.takeIf(::isFinitePoint)
                ?.let { it to PreviewTargetConfidence.EXACT_ANCHOR }
                ?: (null to PreviewTargetConfidence.UNRESOLVED)
            AssPositionMode.MOVE -> geometry.move
                ?.takeIf(::isValidMove)
                ?.let {
                    movePoint(event, it, positionMs)
                        .takeIf(::isFinitePoint)
                        ?.let { point -> point to PreviewTargetConfidence.EXACT_ANCHOR }
                } ?: (null to PreviewTargetConfidence.UNRESOLVED)
            AssPositionMode.INHERITED -> inheritedAnchor(document, event)
                ?.let { it to PreviewTargetConfidence.APPROXIMATE_ANCHOR }
                ?: (null to PreviewTargetConfidence.UNRESOLVED)
            AssPositionMode.CONFLICT -> null to PreviewTargetConfidence.UNRESOLVED
        }
        val anchor = resolved.first
        val distance = anchor?.let { hypot(it.x - point.x, it.y - point.y) } ?: Double.POSITIVE_INFINITY
        return PreviewTargetCandidate(
            eventId = event.id,
            styleName = event.style,
            textLabel = AssInlineSyntax.visibleText(event.text)
                .replace("\\N", " ")
                .replace("\\n", " ")
                .trim()
                .ifBlank { "（空字幕）" }
                .take(48),
            layer = event.layer,
            anchor = anchor,
            confidence = resolved.second,
            distance = distance,
        )
    }

    private fun movePoint(
        event: AssEvent,
        move: io.github.assworkbench.domain.AssMove,
        positionMs: Long,
    ): AssPoint {
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
        val style = document.styles.firstOrNull { it.name == event.style } ?: return null
        val analysis = AssInlineSyntax.analyze(event.text)
        if (analysis.hasErrors) return null

        val leadingEnd = AssTopLevelOverrideSyntax.leadingPrefixLength(event.text)
        val alignmentTags = AssTopLevelOverrideSyntax.tags(event.text)
            .filter { it.name.equals("an", ignoreCase = true) }
        if (alignmentTags.any { it.start >= leadingEnd }) return null

        val alignment = alignmentTags.lastOrNull()?.value?.toIntOrNull() ?: style.alignment
        if (alignment !in 1..9) return null
        if (event.marginL < 0 || event.marginR < 0 || event.marginV < 0) return null
        if (style.marginL < 0 || style.marginR < 0 || style.marginV < 0) return null

        val marginL = if (event.marginL > 0) event.marginL.toDouble() else style.marginL.toDouble()
        val marginR = if (event.marginR > 0) event.marginR.toDouble() else style.marginR.toDouble()
        val marginV = if (event.marginV > 0) event.marginV.toDouble() else style.marginV.toDouble()

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
        return AssPoint(x, y).takeIf(::isFinitePoint)
    }

    private fun hasLateAnchorTag(text: String): Boolean {
        val analysis = AssInlineSyntax.analyze(text)
        if (analysis.hasErrors) return true
        val leadingEnd = AssTopLevelOverrideSyntax.leadingPrefixLength(text)
        return AssTopLevelOverrideSyntax.tags(text).any { tag ->
            tag.start >= leadingEnd &&
                (
                    tag.name.equals("an", ignoreCase = true) ||
                        tag.name.equals("pos", ignoreCase = true) ||
                        tag.name.equals("move", ignoreCase = true)
                    )
        }
    }

    private fun isFinitePoint(point: AssPoint): Boolean =
        point.x.isFinite() && point.y.isFinite()

    private fun isValidMove(move: io.github.assworkbench.domain.AssMove): Boolean {
        if (!isFinitePoint(move.start) || !isFinitePoint(move.end)) return false
        val start = move.startMs
        val end = move.endMs
        if ((start == null) != (end == null)) return false
        if (start == null || end == null) return true
        return start.isFinite() && end.isFinite() && start >= 0.0 && end >= start
    }
}
