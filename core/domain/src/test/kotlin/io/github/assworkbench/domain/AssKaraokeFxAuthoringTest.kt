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
        assertTrue(plan.generatedText.contains("\\1a&HFF&\\2a&HFF&\\3a&HFF&\\4a&HFF&\\blur4"))
        assertTrue(plan.generatedText.contains("\\t(0,150,1.2,\\1a&H00&\\2a&H00&\\3a&H00&\\4a&H00&\\blur0)"))
        assertTrue(plan.generatedText.contains("{\\kf30\\1a&HFF&\\2a&HFF&\\3a&HFF&\\4a&HFF&\\blur4"))
        assertTrue(plan.generatedText.contains("\\t(200,350,1.2,\\1a&H00&\\2a&H00&\\3a&H00&\\4a&H00&\\blur0)}there"))
        assertTrue(plan.generatedText.contains("{\\ko10\\1a&HFF&\\2a&HFF&\\3a&HFF&\\4a&HFF&\\blur4"))
        assertTrue(plan.generatedText.contains("\\t(500,600,1.2,\\1a&H00&\\2a&H00&\\3a&H00&\\4a&H00&\\blur0)}!"))
        assertTrue(plan.generatedText.endsWith("!"))
    }

    @Test
    fun revealDurationIsClampedToShortSyllableAndZeroDurationEndsVisible() {
        val source = "{\\k5}A{\\k0}B"
        val output = AssKaraokeFxAuthoring.applyProgressiveReveal(
            source,
            AssKaraokeRevealFxSpec(revealMs = 160, startBlur = 3.5),
        )

        assertTrue(output.contains("\\t(0,50,\\1a&H00&\\2a&H00&\\3a&H00&\\4a&H00&\\blur0)"))
        assertTrue(output.contains("{\\k0\\1a&H00&\\2a&H00&\\3a&H00&\\4a&H00&\\blur0}B"))
    }

    @Test
    fun documentPlannerRestoresStyleChannelAlphaInsteadOfForcingOpaque() {
        val document = AssDocument(
            styles = listOf(
                AssStyle(
                    name = "Alpha",
                    primaryColor = "&H80FFFFFF",
                    secondaryColor = "&H400000FF",
                    outlineColor = "&H20000000",
                    backColor = "&HC0000000",
                )
            ),
            events = listOf(
                AssEvent(
                    id = 3,
                    start = SubTime(0),
                    end = SubTime(1000),
                    style = "Alpha",
                    text = "{\\k20}A",
                )
            ),
        )

        val plan = AssKaraokeFxAuthoring.planProgressiveReveal(
            document,
            3,
            AssKaraokeRevealFxSpec(revealMs = 100, startBlur = 0.0),
        )

        assertTrue(plan.generatedText.contains("\\1a&HFF&\\2a&HFF&\\3a&HFF&\\4a&HFF&"))
        assertTrue(
            plan.generatedText.contains(
                "\\t(0,100,\\1a&H80&\\2a&H40&\\3a&H20&\\4a&HC0&\\blur0)"
            )
        )
    }

    @Test
    fun flipRevealResolvesAgainstExistingEventBaseGeometry() {
        val document = AssDocument(
            styles = listOf(AssStyle(name = "K", scaleY = 75.0)),
            events = listOf(
                AssEvent(
                    id = 9,
                    start = SubTime(0),
                    end = SubTime(1000),
                    style = "K",
                    text = "{\\fscy80\\frx10}{\\k20}Flip",
                )
            ),
        )

        val plan = AssKaraokeFxAuthoring.planProgressiveReveal(
            document = document,
            eventId = 9,
            spec = AssKaraokeRevealFxSpec(
                revealMs = 150,
                startBlur = 3.0,
                flip = AssKaraokeFlipFxSpec(
                    startScalePercent = 10.0,
                    overshootScalePercent = 125.0,
                    startRotationXDegrees = 90.0,
                ),
            ),
        )

        assertTrue(plan.generatedText.contains("\\fscy8\\frx100"))
        assertTrue(plan.generatedText.contains("\\t(0,100,\\fscy100\\frx10)"))
        assertTrue(plan.generatedText.contains("\\t(100,150,\\fscy80\\frx10)"))
        assertTrue(plan.generatedText.startsWith("{\\fscy80\\frx10}{\\k20"))
    }

    @Test
    fun flipRevealUsesStyleScaleWhenEventHasNoScaleOverride() {
        val document = AssDocument(
            styles = listOf(AssStyle(name = "K", scaleY = 60.0)),
            events = listOf(
                AssEvent(
                    id = 4,
                    start = SubTime(0),
                    end = SubTime(1000),
                    style = "K",
                    text = "{\\k20}A",
                )
            ),
        )

        val plan = AssKaraokeFxAuthoring.planProgressiveReveal(
            document,
            4,
            AssKaraokeRevealFxSpec(
                revealMs = 120,
                flip = AssKaraokeFlipFxSpec(
                    startScalePercent = 20.0,
                    overshootScalePercent = 110.0,
                    startRotationXDegrees = 80.0,
                ),
            ),
        )

        assertTrue(plan.generatedText.contains("\\fscy12\\frx80"))
        assertTrue(plan.generatedText.contains("\\fscy66\\frx0"))
        assertTrue(plan.generatedText.contains("\\fscy60\\frx0"))
    }

    @Test
    fun flipRevealRejectsSyllableLevelScaleOrRotationButAllowsLeadingBaseGeometry() {
        val ok = AssKaraokeFxAuthoring.planProgressiveReveal(
            "{\\fscy80\\frx10}{\\k20}A",
            AssKaraokeRevealFxSpec(
                flip = AssKaraokeFlipFxSpec(),
            ),
            baseScaleY = 80.0,
            baseRotationX = 10.0,
        )
        assertTrue(ok.generatedText.contains("\\fscy9.6\\frx96"))

        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.planProgressiveReveal(
                "{\\k20\\fscy50}A",
                AssKaraokeRevealFxSpec(flip = AssKaraokeFlipFxSpec()),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.planProgressiveReveal(
                "{\\k20}A{\\frx20}{\\k20}B",
                AssKaraokeRevealFxSpec(flip = AssKaraokeFlipFxSpec()),
            )
        }
    }

    @Test
    fun rejectsFadeAndStyleResetBecauseTheyChangeOwnedBaseState() {
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\fad(100,100)}{\\k20}A")
        }
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\fade(255,0,255,0,100,500,600)}{\\k20}A")
        }
        assertFailsWith<IllegalArgumentException> {
            AssKaraokeFxAuthoring.applyProgressiveReveal("{\\rAlt}{\\k20}A")
        }
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
