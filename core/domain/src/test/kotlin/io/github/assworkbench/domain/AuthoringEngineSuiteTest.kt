package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthoringEngineSuiteTest {
    private fun event(id: Long, start: Long, end: Long, text: String = "Hello") =
        AssEvent(id = id, start = SubTime(start), end = SubTime(end), text = text)

    @Test
    fun multiAnchorSyncCorrectsOffsetAndDrift() {
        val anchors = listOf(AssSyncAnchor(0, 1000), AssSyncAnchor(10_000, 12_000))
        assertEquals(1000L, AssSubtitleSynchronizer.mapTime(0, anchors))
        assertEquals(6500L, AssSubtitleSynchronizer.mapTime(5000, anchors))
        assertEquals(12_000L, AssSubtitleSynchronizer.mapTime(10_000, anchors))
    }

    @Test
    fun visibleRegexReplacePreservesOverrideBlocks() {
        val source = "{\\bord2}Hello {note}Hello"
        val result = AssSearchReplace.replaceVisibleSegments(source, Regex("Hello"), "Hi")
        assertEquals("{\\bord2}Hi {note}Hi", result)
    }

    @Test
    fun semanticSearchFiltersByTagsAndRawRegex() {
        val e = event(1, 0, 1000, "{\\pos(10,20)\\bord2}Sign")
        val query = AssSearchQuery(
            rawPattern = Regex("Sign"),
            requiredTags = setOf("pos"),
            forbiddenTags = setOf("move"),
        )
        assertTrue(AssSearchReplace.matches(e, query))
    }

    @Test
    fun keyframesCompileIntoChainedTransforms() {
        val plan = AssAnimationAuthoring.planNumericTrack(
            AssTransformVisualProperty.SCALE_X,
            listOf(
                AssAnimationKeyframe(0, 80.0),
                AssAnimationKeyframe(300, 110.0),
                AssAnimationKeyframe(900, 100.0),
            ),
        )
        assertTrue(plan.generatedOverrideBlock.contains("\\fscx80"))
        assertTrue(plan.generatedOverrideBlock.contains("\\t(0,300,\\fscx110)"))
        assertTrue(plan.generatedOverrideBlock.contains("\\t(300,900,\\fscx100)"))
    }

    @Test
    fun drawingRoundTripKeepsPathEditable() {
        val source = "{\\p1\\pbo2}m 0 0 l 100 0 100 50 0 50{\\p0} tail"
        val inspected = AssDrawingCodec.inspect(source)
        assertNotNull(inspected)
        val shifted = AssDrawingCodec.translate(inspected!!.drawing, 10.0, 20.0)
        val patched = AssDrawingCodec.patch(source, shifted)
        assertTrue(patched.contains("m 10 20"))
        assertTrue(patched.endsWith("{\\p0} tail"))
    }

    @Test
    fun speechAssistFindsStableAmplitudeBoundary() {
        val quiet = ShortArray(10) { 0 }
        val loud = ShortArray(10) { 16000 }
        val envelope = WaveformEnvelope(
            bucketDurationMs = 20,
            durationMs = 400,
            minimums = quiet + ShortArray(10) { -16000 },
            maximums = quiet + loud,
        )
        val boundaries = AudioTimingAssist.speechBoundaries(envelope, minActiveMs = 60, minSilenceMs = 60)
        assertTrue(boundaries.any { it in 160L..240L })
    }

    @Test
    fun karaokeBatchSkipsIncompatibleEventsWithoutAbortingTheBatch() {
        val document = AssDocument(
            events = listOf(
                event(1, 0, 1000, "{\\k20}Hi{\\kf30}there"),
                event(2, 1000, 2000, "{\\kt50}Absolute"),
                event(3, 2000, 3000, "{\\k20\\blur2}Already styled"),
                event(4, 3000, 4000, "Plain text"),
            )
        )
        val spec = AssKaraokeRevealFxSpec(revealMs = 120, startBlur = 3.0)
        val recipe = AssBatchRecipe(
            id = "karaoke-reveal",
            filter = AssBatchFilter.KaraokeRevealCompatible(spec),
            actions = listOf(AssBatchAction.ApplyKaraokeRevealFx(spec)),
        )

        val preview = AssBatchEngine.preview(document, recipe)

        assertEquals(listOf(1L), preview.affectedEventIds)
        assertEquals(listOf(1L), preview.changedEventIds)
        assertTrue(preview.document.events.first { it.id == 1L }.text.contains("\\t(0,120,"))
        assertEquals(document.events.first { it.id == 2L }, preview.document.events.first { it.id == 2L })
        assertEquals(document.events.first { it.id == 3L }, preview.document.events.first { it.id == 3L })
        assertEquals(document.events.first { it.id == 4L }, preview.document.events.first { it.id == 4L })
    }

    @Test
    fun karaokeBatchActionRechecksCurrentEventAfterEarlierActions() {
        val original = event(9, 0, 1000, "{\\k20}Safe")
        val document = AssDocument(events = listOf(original))
        val action = AssBatchAction.ApplyKaraokeRevealFx(
            AssKaraokeRevealFxSpec(revealMs = 100, startBlur = 2.0)
        )
        val nowConflicting = original.copy(text = "{\\k20\\blur4}Now conflicting")

        assertEquals(nowConflicting, action.apply(nowConflicting, document))
    }

    @Test
    fun expandedBatchRegexAndTimingRemainOnePreviewDocument() {
        val doc = AssDocument(events = listOf(event(1, 1000, 2000, "Hello 123")))
        val recipe = AssBatchRecipe(
            id = "advanced",
            filter = AssBatchFilter.VisibleRegex(Regex("\\d+")),
            actions = listOf(
                AssBatchAction.ReplaceVisibleRegex(Regex("\\d+"), "X"),
                AssBatchAction.ScaleTiming(0, 2, 1),
            ),
        )
        val preview = AssBatchEngine.preview(doc, recipe)
        assertEquals(listOf(1L), preview.changedEventIds)
        assertEquals("Hello X", preview.document.events.single().text)
        assertEquals(2000L, preview.document.events.single().start.millis)
        assertEquals(4000L, preview.document.events.single().end.millis)
    }
}
