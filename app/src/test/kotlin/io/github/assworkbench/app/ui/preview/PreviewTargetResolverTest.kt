package io.github.assworkbench.app.ui.preview

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssStyle
import io.github.assworkbench.domain.SubTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTargetResolverTest {
    private fun event(
        id: Long,
        text: String,
        style: String = "Default",
        layer: Int = 0,
        start: Long = 0,
        end: Long = 2_000,
        comment: Boolean = false,
    ) = AssEvent(
        id = id,
        start = SubTime(start),
        end = SubTime(end),
        style = style,
        layer = layer,
        text = text,
        comment = comment,
    )

    @Test
    fun explicitPositionRanksByExactAnchor() {
        val document = AssDocument(
            events = listOf(
                event(1, "{\\pos(100,100)}A"),
                event(2, "{\\pos(900,900)}B"),
            )
        )
        val candidates = PreviewTargetResolver.candidates(document, 500, 120.0, 110.0)

        assertEquals(1L, candidates.first().eventId)
        assertEquals(PreviewTargetConfidence.EXACT_ANCHOR, candidates.first().confidence)
    }

    @Test
    fun moveInterpolatesAtCurrentPlaybackTime() {
        val document = AssDocument(
            events = listOf(event(7, "{\\move(0,0,100,100,0,1000)}Move"))
        )
        val candidate = PreviewTargetResolver.candidates(document, 500, 50.0, 50.0).single()

        assertEquals(7L, candidate.eventId)
        assertEquals(50.0, candidate.anchor!!.x, 0.01)
        assertEquals(50.0, candidate.anchor!!.y, 0.01)
    }

    @Test
    fun inheritedAlignmentIsExplicitlyApproximate() {
        val document = AssDocument(
            styles = listOf(AssStyle(name = "TopRight", alignment = 9, marginR = 40, marginV = 30)),
            events = listOf(event(3, "Text", style = "TopRight")),
        )
        val candidate = PreviewTargetResolver.candidates(
            document,
            positionMs = 500,
            x = document.playResX - 40.0,
            y = 30.0,
        ).single()

        assertEquals(PreviewTargetConfidence.APPROXIMATE_ANCHOR, candidate.confidence)
        assertEquals(document.playResX - 40.0, candidate.anchor!!.x, 0.01)
        assertEquals(30.0, candidate.anchor!!.y, 0.01)
    }

    @Test
    fun inheritedAnchorFailsClosedWhenStyleReferenceIsMissing() {
        val document = AssDocument(
            styles = listOf(AssStyle(name = "Default", alignment = 2)),
            events = listOf(event(10, "Text", style = "Missing")),
        )

        val candidate = PreviewTargetResolver.candidates(document, 500, 960.0, 1000.0).single()

        assertEquals(PreviewTargetConfidence.UNRESOLVED, candidate.confidence)
        assertTrue(candidate.anchor == null)
        assertTrue(candidate.distance.isInfinite())
    }

    @Test
    fun spanLocalAlignmentIsAmbiguousInsteadOfRepositioningTheWholePreviewTarget() {
        val document = AssDocument(
            styles = listOf(AssStyle(name = "Default", alignment = 2, marginV = 40)),
            events = listOf(event(11, "A{\\an9}B")),
        )

        val candidate = PreviewTargetResolver.candidates(document, 500, 960.0, 1040.0).single()

        assertEquals(PreviewTargetConfidence.UNRESOLVED, candidate.confidence)
        assertTrue(candidate.anchor == null)
    }

    @Test
    fun spanLocalPositionIsAmbiguousInsteadOfBecomingInheritedPlacement() {
        val document = AssDocument(
            events = listOf(event(13, "A{\\pos(100,100)}B")),
        )

        val candidate = PreviewTargetResolver.candidates(document, 500, 100.0, 100.0).single()

        assertEquals(PreviewTargetConfidence.UNRESOLVED, candidate.confidence)
        assertTrue(candidate.anchor == null)
    }

    @Test
    fun invalidMoveTimingIsUnresolvedInsteadOfBeingInterpolated() {
        val document = AssDocument(
            events = listOf(event(14, "{\\move(0,0,100,100,900,100)}Move")),
        )

        val candidate = PreviewTargetResolver.candidates(document, 500, 50.0, 50.0).single()

        assertEquals(PreviewTargetConfidence.UNRESOLVED, candidate.confidence)
        assertTrue(candidate.anchor == null)
    }

    @Test
    fun nonFiniteExplicitGeometryIsUnresolvedRatherThanAnExactAnchor() {
        val document = AssDocument(
            events = listOf(event(12, "{\\pos(1e309,100)}Huge")),
        )

        val candidate = PreviewTargetResolver.candidates(document, 500, 100.0, 100.0).single()

        assertEquals(PreviewTargetConfidence.UNRESOLVED, candidate.confidence)
        assertTrue(candidate.anchor == null)
        assertTrue(candidate.distance.isInfinite())
    }

    @Test
    fun inactiveAndCommentEventsAreNotCandidates() {
        val document = AssDocument(
            events = listOf(
                event(1, "{\\pos(100,100)}old", start = 0, end = 400),
                event(2, "{\\pos(100,100)}comment", comment = true),
                event(3, "{\\pos(100,100)}live"),
            )
        )
        val ids = PreviewTargetResolver.candidates(document, 500, 100.0, 100.0).map { it.eventId }

        assertEquals(listOf(3L), ids)
    }

    @Test
    fun conflictingPositionIsKeptButMarkedUnresolved() {
        val document = AssDocument(
            events = listOf(event(9, "{\\pos(10,10)\\move(0,0,100,100)}Conflict"))
        )
        val candidate = PreviewTargetResolver.candidates(document, 500, 10.0, 10.0).single()

        assertEquals(PreviewTargetConfidence.UNRESOLVED, candidate.confidence)
        assertTrue(candidate.distance.isInfinite())
    }
}
