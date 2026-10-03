package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssKaraokeFxAuthoringTest {
    @Test
    fun progressiveRevealUsesCumulativeKaraokeTimeAndPreservesMarkers() {
        val source = "{\\an8}{\\k20\\bord2}Hi{\\kf30}there{\\ko10}!"
        val plan = AssKaraokeFxAuthoring.planProgressiveReveal(
            source,
            AssKaraokeRevealFxSpec(revealMs = 150, startBlur = 4.0, accel = 1.2),
        )

        assertEquals(3, plan.sourceSegmentCount)
        assertTrue(plan.generatedText.startsWith("{\\an8}{\\k20\\bord2"))
        assertTrue(plan.generatedText.contains("\\alpha&HFF&\\blur4\\t(0,150,1.2,\\alpha&H00&\\blur0)"))
        assertTrue(plan.generatedText.contains("{\\kf30\\alpha&HFF&\\blur4\\t(200,350,1.2,\\alpha&H00&\\blur0)}there"))
        assertTrue(plan.generatedText.contains("{\\ko10\\alpha&HFF&\\blur4\\t(500,600,1.2,\\alpha&H00&\\blur0)}!"))
        assertTrue(plan.generatedText.endsWith("!"))
    }

    @Test
    fun revealDurationIsClampedToShortSyllableAndZeroDurationEndsVisible() {
        val source = "{\\k5}A{\\k0}B"
        val output = AssKaraokeFxAuthoring.applyProgressiveReveal(
            source,
            AssKaraokeRevealFxSpec(revealMs = 160, startBlur = 3.5),
        )

        assertTrue(output.contains("\\t(0,50,\\alpha&H00&\\blur0)"))
        assertTrue(output.contains("{\\k0\\alpha&H00&\\blur0}B"))
    }

    @Test
    fun batchRevealPreservesDocumentOrderAndFailsAtomically() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 7,
                    start = SubTime(0),
                    end = SubTime(1000),
                    text = "{\\k10}A{\\k20}B",
                ),
                AssEvent(
                    id = 3,
                    start = SubTime(1000),
                    end = SubTime(2000),
                    text = "{\\kf15}C{\\ko15}D",
                ),
            )
        )

        val result = AssKaraokeFxAuthoring.planProgressiveRevealBatch(
            document = document,
            eventIds = setOf(3, 7),
            spec = AssKaraokeRevealFxSpec(revealMs = 80, startBlur = 2.0),
        )

        assertEquals(listOf(7L, 3L), result.sourceEventIds)
        assertEquals(mapOf(7L to 2, 3L to 2), result.segmentCountByEvent)
        assertEquals(4, result.totalSegmentCount)
        assertTrue(result.document.events[0].text.contains("\\t(0,80,\\alpha&H00&\\blur0)"))
        assertTrue(result.document.events[1].text.contains("\\t(0,80,\\alpha&H00&\\blur0)"))

        val invalid = document.copy(
            events = listOf(
                document.events[0],
                document.events[1].copy(text = "{\\kt15}C"),
            )
        )
        val error = assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.planProgressiveRevealBatch(
                document = invalid,
                eventIds = setOf(7, 3),
            )
        }
        assertTrue(error.message.orEmpty().contains("字幕 #3"))
        assertEquals(document.events[0].text, invalid.events[0].text)
        assertEquals("{\\kt15}C", invalid.events[1].text)
    }

    @Test
    fun batchRejectsCommentEventsInsteadOfGeneratingInvisibleFx() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1,
                    comment = true,
                    start = SubTime(0),
                    end = SubTime(1000),
                    text = "{\\k20}Hidden",
                )
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.planProgressiveRevealBatch(document, setOf(1))
        }
        assertTrue(error.message.orEmpty().contains("Comment"))
    }

    @Test
    fun rejectsAbsoluteKtAndExistingControlledFx() {
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\kt50}A")
        }
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\blur2}{\\k20}A")
        }
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\k20\\blur2}A")
        }
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\k20}A{\\blur2}B{\\k20}C")
        }
    }
}
