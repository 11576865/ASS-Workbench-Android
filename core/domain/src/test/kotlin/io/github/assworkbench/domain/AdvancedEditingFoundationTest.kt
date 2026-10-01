package io.github.assworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedEditingFoundationTest {
    @Test
    fun cfrAndVfrFrameMapsSnapToDisplayedFrames() {
        val cfr = CfrFrameTimeMap(60, 1)
        assertEquals(1_000L, cfr.timeMsForFrame(60))
        assertEquals(60L, cfr.frameForTimeMs(1_000))

        val vfr = VfrFrameTimeMap(longArrayOf(0, 17, 34, 68, 85))
        assertEquals(2L, vfr.frameForTimeMs(35))
        assertEquals(68L, vfr.timeMsForFrame(3))
    }

    @Test
    fun batchRulePreviewsWithoutMutatingSource() {
        val source = AssDocument(events = listOf(
            AssEvent(1, start = SubTime(100), end = SubTime(1_100), style = "Default", text = "hello"),
            AssEvent(2, start = SubTime(200), end = SubTime(1_200), style = "Default", text = "other"),
        ))
        val preview = BatchRuleEngine.preview(
            source,
            BatchRule(
                predicate = BatchPredicate(textContains = "hello"),
                actions = listOf(BatchAction.ShiftTime(50), BatchAction.SetLayer(3)),
            ),
        )
        assertEquals(setOf(1L), preview.affectedEventIds)
        assertEquals(100L, source.events[0].start.millis)
        assertEquals(150L, preview.document.events[0].start.millis)
        assertEquals(3, preview.document.events[0].layer)
    }

    @Test
    fun karaokePatchesOnlySelectedTimingTag() {
        val raw = "{\\k20}Hel{\\kf30}lo"
        val track = AssKaraokeSemantic.inspect(raw)
        assertEquals(2, track.segments.size)
        assertEquals(50, track.totalDurationCs)
        assertEquals("Hel", track.segments[0].visibleText)
        val patched = AssKaraokeSemantic.patchValue(raw, 1, 45)
        assertEquals("{\\k20}Hel{\\kf45}lo", patched)
    }

    @Test
    fun compatibilityWarnsWhenExportingAssStylingToSrt() {
        val doc = AssDocument(events = listOf(
            AssEvent(1, start = SubTime(0), end = SubTime(1_000), text = "{\\b1}Hello"),
        ))
        val issues = SubtitleCompatibilityAnalyzer.inspect(doc, SubtitleCompatibilityProfile.SUBRIP)
        assertTrue(issues.any { it.code == "ASS_OVERRIDE_LOSS" })
    }

    @Test
    fun vectorClipPatchesOnePointWithoutNormalizingThePath() {
        val raw = "{\\clip(2,m 0 0 l 100 0 l 100 100)}Hello"
        val clips = AssVectorClipSemantic.inspect(raw)
        assertEquals(1, clips.size)
        assertEquals(3, clips.single().path.points.size)
        val patched = AssVectorClipSemantic.patchPoint(raw, 0, 1, 120.0, 10.0)
        assertEquals("{\\clip(2,m 0 0 l 120 10 l 100 100)}Hello", patched)
    }
}
