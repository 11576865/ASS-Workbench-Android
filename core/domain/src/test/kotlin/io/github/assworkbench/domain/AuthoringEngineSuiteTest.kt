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
