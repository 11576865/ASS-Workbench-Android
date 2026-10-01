package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineeringSuiteTest {
    @Test fun srtImportsAndWrites() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,250
            Hello
            world

            2
            00:00:03,000 --> 00:00:04,000
            <i>Italic</i>
        """.trimIndent()
        val doc = SrtCodec.parse(srt)
        assertEquals(2, doc.events.size)
        assertEquals("Hello\\Nworld", doc.events[0].text)
        assertTrue(doc.events[1].text.contains("\\i1"))
        assertTrue(SrtCodec.write(doc).contains("00:00:01,000 --> 00:00:02,250"))
    }

    @Test fun cfrAndVfrSnap() {
        val cfr = FrameTimebase.Cfr(60)
        assertEquals(60L, cfr.nearestFrame(1000))
        val vfr = FrameTimebase.Vfr(listOf(0, 17, 35, 52))
        assertEquals(2L, vfr.nearestFrame(34))
    }

    @Test fun batchRecipeIsTransactionalPreview() {
        val doc = AssDocument(events = listOf(
            AssEvent(1, start = SubTime(1000), end = SubTime(2000), style = "Default", text = "a"),
            AssEvent(2, start = SubTime(2000), end = SubTime(3000), style = "Alt", text = "b"),
        ))
        val recipe = AssBatchRecipe("x", AssBatchFilter.StyleIs("Alt"), listOf(AssBatchAction.ShiftTime(100)))
        val preview = AssBatchEngine.preview(doc, recipe)
        assertEquals(listOf(2L), preview.changedEventIds)
        assertEquals(2100L, preview.document.events[1].start.millis)
        assertEquals(2000L, doc.events[1].start.millis)
    }

    @Test fun karaokeRoundTripAndVectorClipPatch() {
        val segments = listOf(AssKaraokeSegment(AssKaraokeMode.K, 20, "Hi"), AssKaraokeSegment(AssKaraokeMode.KF, 30, "!"))
        assertEquals(500L, AssKaraokeCodec.totalDurationMs(segments))
        assertEquals(2, AssKaraokeCodec.parse(AssKaraokeCodec.write(segments)).size)
        val text = "{\\clip(m 0 0 l 100 0 l 100 100)}x"
        val clip = AssVectorClipCodec.inspect(text)!!
        val moved = clip.copy(tokens = clip.tokens.map { if (it.number != null) it.copy(number = it.number + 1) else it })
        assertTrue(AssVectorClipCodec.patch(text, moved).contains("m 1 1"))
    }

    @Test fun fontRequirementsIncludeStyleInlineAndReset() {
        val doc = AssDocument(
            styles = listOf(AssStyle(), AssStyle(name = "Alt", fontName = "Alt Font")),
            events = listOf(AssEvent(1, start = SubTime(0), end = SubTime(1000), text = "{\\fnInline Font}A{\\rAlt}B"))
        )
        val families = FontRequirementResolver.resolve(doc).map { it.family }.toSet()
        assertTrue("Arial" in families)
        assertTrue("Inline Font" in families)
        assertTrue("Alt Font" in families)
    }

    @Test fun linterOffersExplicitQuickFixes() {
        val doc = AssDocument(events = listOf(AssEvent(1, start = SubTime(1000), end = SubTime(1000), style = "Missing", text = " x ")))
        val issues = AssLinter.inspect(doc)
        assertTrue(issues.any { it.quickFix == AssQuickFix.ExtendZeroDuration })
        assertTrue(issues.any { it.quickFix == AssQuickFix.UseDefaultStyle })
        assertTrue(issues.any { it.quickFix == AssQuickFix.TrimVisibleWhitespace })
    }
}
