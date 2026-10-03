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
    fun rejectsAbsoluteKtAndExistingControlledFx() {
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\kt50}A")
        }
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\k20\\blur2}A")
        }
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\k20}A{\\blur2}B{\\k20}C")
        }
    }
}
