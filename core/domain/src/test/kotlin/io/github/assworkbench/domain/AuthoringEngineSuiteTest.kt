package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun semanticSearchUsesAllDirectTagsButIgnoresTransformPayloadTags() {
        val laterSpan = event(2, 0, 1000, "A{\\bord4}B")
        val nestedOnly = event(3, 0, 1000, "{\\t(0,500,\\bord8)}Animated")

        assertTrue(
            AssSearchReplace.matches(
                laterSpan,
                AssSearchQuery(requiredTags = setOf("bord")),
            )
        )
        assertTrue(
            !AssSearchReplace.matches(
                nestedOnly,
                AssSearchQuery(requiredTags = setOf("bord")),
            )
        )
        assertTrue(
            AssSearchReplace.matches(
                nestedOnly,
                AssSearchQuery(requiredTags = setOf("t")),
            )
        )
    }

    @Test
    fun semanticSearchRejectsMalformedTagNamesAndUnknownStyleTargets() {
        assertFailsWith<IllegalArgumentException> {
            AssSearchQuery(requiredTags = setOf("pos("))
        }

        val document = AssDocument(
            styles = listOf(AssStyle(name = "Default")),
            events = listOf(event(4, 0, 1000, "Text")),
        )
        val query = AssSearchQuery(stylePattern = Regex("Default"))
        val error = assertFailsWith<IllegalArgumentException> {
            AssSearchReplace.preview(
                document = document,
                query = query,
                replacement = AssSearchReplacement(
                    scope = AssReplaceScope.STYLE,
                    pattern = Regex("Default"),
                    replacement = "Missing",
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("Style"))
        assertEquals("Default", document.events.single().style)
    }

    @Test
    fun visibleReplacementPreservesEscapesAndFailsClosedOnMalformedSyntax() {
        val source = "A\\NB\\hC{\\bord2}D"

        val unchangedEscapes = AssSearchReplace.replaceVisibleSegments(
            source,
            Regex("[Nh]"),
            "X",
        )
        assertEquals(source, unchangedEscapes)

        val changed = AssSearchReplace.replaceVisibleSegments(
            source,
            Regex("[BCD]"),
            "Z",
        )
        assertEquals("A\\NZ\\hZ{\\bord2}Z", changed)

        val malformed = "{\\bord2 broken"
        val error = assertFailsWith<IllegalArgumentException> {
            AssSearchReplace.replaceVisibleSegments(malformed, Regex("broken"), "changed")
        }
        assertTrue(error.message.orEmpty().contains("损坏"))
        assertEquals("{\\bord2 broken", malformed)
    }

    @Test
    fun semanticSearchRangeValidationFailsClosed() {
        assertFailsWith<IllegalArgumentException> {
            AssSearchQuery(durationRangeMs = 500L..100L)
        }
        assertFailsWith<IllegalArgumentException> {
            AssSearchQuery(timeRangeMs = -1L..100L)
        }
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
    fun karaokeBatchFailsClosedWhenEarlierActionInvalidatesCompatibility() {
        val original = event(9, 0, 1000, "{\\k20}Safe")
        val document = AssDocument(events = listOf(original))
        val spec = AssKaraokeRevealFxSpec(revealMs = 100, startBlur = 2.0)
        val recipe = AssBatchRecipe(
            id = "karaoke-conflict",
            filter = AssBatchFilter.KaraokeRevealCompatible(spec),
            actions = listOf(
                AssBatchAction.SetNumericOverride(
                    property = AssTransformVisualProperty.GAUSSIAN_BLUR,
                    value = 4.0,
                ),
                AssBatchAction.ApplyKaraokeRevealFx(spec),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            AssBatchEngine.preview(document, recipe)
        }

        assertTrue(error.message.orEmpty().contains("动作阶段失去兼容性"))
        assertEquals("{\\k20}Safe", document.events.single().text)
    }

    @Test
    fun batchTagFilterMatchesDirectOverrideTagsOnly() {
        val document = AssDocument(
            events = listOf(
                event(20, 0, 1000, "{\\pos(10,20)}Real tag"),
                event(21, 1000, 2000, "Visible \\pos(10,20) text"),
                event(22, 2000, 3000, "{\\t(0,500,\\pos(30,40))}Nested transform tag"),
                event(23, 3000, 4000, "A{\\pos(50,60)}Later direct tag"),
            )
        )
        val preview = AssBatchEngine.preview(
            document,
            AssBatchRecipe(
                id = "tag-filter",
                filter = AssBatchFilter.HasTag("pos"),
                actions = listOf(AssBatchAction.SetActor("matched")),
            ),
        )

        assertEquals(listOf(20L, 23L), preview.affectedEventIds)
        assertEquals("matched", preview.document.events.first { it.id == 20L }.name)
        assertEquals("", preview.document.events.first { it.id == 21L }.name)
        assertEquals("", preview.document.events.first { it.id == 22L }.name)
        assertEquals("matched", preview.document.events.first { it.id == 23L }.name)
    }

    @Test
    fun batchTagFilterRejectsMalformedTagNames() {
        assertFailsWith<IllegalArgumentException> {
            AssBatchFilter.HasTag("pos(")
        }
        assertFailsWith<IllegalArgumentException> {
            AssBatchFilter.HasTag("")
        }
    }

    @Test
    fun batchDomainRejectsInvalidRangesAndNumericOverrides() {
        assertFailsWith<IllegalArgumentException> {
            AssBatchFilter.DurationRange(500L, 100L)
        }
        assertFailsWith<IllegalArgumentException> {
            AssBatchFilter.TimeRange(1000L, 900L)
        }
        assertFailsWith<IllegalArgumentException> {
            AssBatchAction.SetNumericOverride(
                property = AssTransformVisualProperty.GAUSSIAN_BLUR,
                value = -1.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AssBatchAction.SetNumericOverride(
                property = AssTransformVisualProperty.ROTATION_X,
                value = Double.POSITIVE_INFINITY,
            )
        }
    }

    @Test
    fun batchRejectsUnknownStyleAndNegativeMargins() {
        val document = AssDocument(
            styles = listOf(AssStyle(name = "Default")),
            events = listOf(event(10, 0, 1000, "Safe")),
        )

        val styleError = assertFailsWith<IllegalArgumentException> {
            AssBatchEngine.preview(
                document,
                AssBatchRecipe(
                    id = "missing-style",
                    actions = listOf(AssBatchAction.SetStyle("Missing")),
                ),
            )
        }
        assertTrue(styleError.message.orEmpty().contains("Style 不存在"))
        assertEquals("Default", document.events.single().style)

        assertFailsWith<IllegalArgumentException> {
            AssBatchAction.SetMargins(left = -1)
        }
    }

    @Test
    fun batchTimingUsesExactArithmeticAndFailsClosedOnOverflow() {
        val exactStart = 9_007_199_254_740_993L
        val exactDocument = AssDocument(
            events = listOf(
                event(
                    id = 11,
                    start = exactStart,
                    end = exactStart + 100L,
                    text = "Exact",
                )
            )
        )
        val exactPreview = AssBatchEngine.preview(
            exactDocument,
            AssBatchRecipe(
                id = "exact-timing",
                actions = listOf(AssBatchAction.ScaleTiming(0L, 1L, 1L)),
            ),
        )
        assertEquals(exactStart, exactPreview.document.events.single().start.millis)
        assertEquals(exactStart + 100L, exactPreview.document.events.single().end.millis)

        val overflowDocument = AssDocument(
            events = listOf(
                event(
                    id = 12,
                    start = Long.MAX_VALUE - 10L,
                    end = Long.MAX_VALUE,
                    text = "Overflow",
                )
            )
        )
        val shiftError = assertFailsWith<IllegalArgumentException> {
            AssBatchEngine.preview(
                overflowDocument,
                AssBatchRecipe(
                    id = "overflow-shift",
                    actions = listOf(AssBatchAction.ShiftTime(100L)),
                ),
            )
        }
        assertTrue(shiftError.message.orEmpty().contains("超出可表示"))

        val scaleError = assertFailsWith<IllegalArgumentException> {
            AssBatchEngine.preview(
                overflowDocument,
                AssBatchRecipe(
                    id = "overflow-scale",
                    actions = listOf(AssBatchAction.ScaleTiming(0L, 2L, 1L)),
                ),
            )
        }
        assertTrue(scaleError.message.orEmpty().contains("超出可表示"))
        assertEquals(Long.MAX_VALUE - 10L, overflowDocument.events.single().start.millis)
        assertEquals(Long.MAX_VALUE, overflowDocument.events.single().end.millis)
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
