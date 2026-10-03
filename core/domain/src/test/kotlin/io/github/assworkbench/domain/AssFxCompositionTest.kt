package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssFxCompositionTest {
    @Test
    fun reflectionCreatesIndependentCompanionFromInheritedAnchor() {
        val document = AssDocument(
            scriptInfo = linkedMapOf("ScriptType" to "v4.00+", "PlayResX" to "1920", "PlayResY" to "1080"),
            styles = listOf(
                AssStyle(
                    name = "Top",
                    alignment = 8,
                    marginV = 100,
                    scaleY = 80.0,
                )
            ),
            events = listOf(
                AssEvent(
                    id = 7,
                    layer = 3,
                    start = SubTime(1000),
                    end = SubTime(3000),
                    style = "Top",
                    text = "{\\an8\\bord2}Hello",
                )
            ),
        )

        val result = AssFxComposition.createReflection(
            document,
            7,
            AssReflectionFxSpec(
                offsetY = 60.0,
                verticalScalePercent = 35.0,
                opacityPercent = 40.0,
                blur = 1.5,
            ),
        )

        assertEquals(2, result.document.events.size)
        assertEquals(8L, result.generatedEventId)
        val reflection = result.document.events.first()
        val source = result.document.events.last()
        assertEquals(2, reflection.layer)
        assertEquals(document.events.single().text, source.text)
        assertTrue(reflection.text.contains("\\pos(960,160)"))
        assertTrue(reflection.text.contains("\\frx180"))
        assertTrue(reflection.text.contains("\\fscy28"))
        assertTrue(reflection.text.contains("\\alpha&H99&"))
        assertTrue(reflection.text.contains("\\blur1.5"))
    }

    @Test
    fun reflectionOffsetsMoveAndTransformOriginWithoutDestroyingTiming() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime(0),
                    end = SubTime(2000),
                    text = "{\\move(100,120,300,320,20,900)\\org(200,200)}Move",
                )
            )
        )

        val result = AssFxComposition.createReflection(
            document,
            1,
            AssReflectionFxSpec(offsetY = 50.0),
        )
        val reflection = result.document.events.first()

        assertTrue(reflection.text.contains("\\move(100,170,300,370,20,900)"))
        assertTrue(reflection.text.contains("\\org(200,250)"))
    }

    @Test
    fun positionConflictIsRejectedInsteadOfGuessing() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime(0),
                    end = SubTime(1000),
                    text = "{\\pos(100,100)\\move(100,100,200,200)}Conflict",
                )
            )
        )

        assertFailsWith<IllegalArgumentException> {
            AssFxComposition.createReflection(document, 1)
        }
    }

    @Test
    fun mirrorStackCreatesGlowAndReflectionBeforeSourceInOneResult() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 10,
                    layer = 2,
                    start = SubTime(0),
                    end = SubTime(1500),
                    text = "{\\pos(400,300)\\bord2}Stack",
                )
            )
        )

        val result = AssFxComposition.composeMirrorStack(
            document = document,
            eventId = 10,
            reflection = AssReflectionFxSpec(offsetY = 40.0),
            glow = AssGlowFxSpec(opacityPercent = 25.0, blur = 4.0, border = 3.0),
            entrance = null,
        )

        assertEquals(3, result.document.events.size)
        assertEquals(2, result.generatedEventIds.size)
        val glow = result.document.events[0]
        val reflection = result.document.events[1]
        val source = result.document.events[2]
        assertEquals(10L, source.id)
        assertTrue(glow.id in result.generatedEventIds)
        assertTrue(reflection.id in result.generatedEventIds)
        assertTrue(glow.text.contains("\\alpha&HBF&"))
        assertTrue(glow.text.contains("\\blur4"))
        assertTrue(glow.text.contains("\\bord3"))
        assertTrue(reflection.text.contains("\\pos(400,340)"))
        assertTrue(reflection.text.contains("\\frx180"))
        assertEquals("{\\pos(400,300)\\bord2}Stack", source.text)
    }

    @Test
    fun entranceKeepsGlowAndReflectionGeometrySynchronized() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 20,
                    layer = 3,
                    start = SubTime(0),
                    end = SubTime(1200),
                    text = "{\\pos(500,400)\\fscy80\\frx10}Stack",
                )
            )
        )

        val result = AssFxComposition.composeMirrorStack(
            document = document,
            eventId = 20,
            reflection = AssReflectionFxSpec(
                offsetY = 48.0,
                verticalScalePercent = 50.0,
                opacityPercent = 35.0,
                blur = 1.5,
            ),
            glow = AssGlowFxSpec(opacityPercent = 20.0, blur = 4.0, border = 3.0),
            entrance = AssFlipEntranceSpec(
                durationMs = 300L,
                startScalePercent = 10.0,
                overshootScalePercent = 125.0,
                startRotationXDegrees = 90.0,
            ),
        )

        val source = result.document.events.first { it.id == 20L }
        val glow = result.document.events.first { it.id == result.generatedEventIds[0] }
        val reflection = result.document.events.first { it.id == result.generatedEventIds[1] }

        assertTrue(source.text.contains("\\fscy8"))
        assertTrue(source.text.contains("\\t(200,300,\\fscy80)"))
        assertTrue(glow.text.contains("\\fscy8"))
        assertTrue(glow.text.contains("\\t(200,300,\\fscy80)"))

        // Reflection's own base is 50% of source scale and +180deg X rotation.
        assertTrue(reflection.text.contains("\\fscy4"))
        assertTrue(reflection.text.contains("\\t(200,300,\\fscy40)"))
        assertTrue(reflection.text.contains("\\frx280"))
        assertTrue(reflection.text.contains("\\t(0,300,\\frx190)"))
    }

    @Test
    fun batchCompositionIsDeterministicAndAtomic() {
        val document = AssDocument(
            events = listOf(
                AssEvent(1, start = SubTime(0), end = SubTime(1000), text = "{\\pos(200,200)}A"),
                AssEvent(2, start = SubTime(1000), end = SubTime(2000), text = "{\\pos(300,300)}B"),
            )
        )

        val result = AssFxComposition.composeMirrorStackBatch(
            document = document,
            eventIds = setOf(2, 1),
            glow = AssGlowFxSpec(opacityPercent = 20.0, blur = 3.0, border = 2.0),
            entrance = null,
        )

        assertEquals(listOf(1L, 2L), result.sourceEventIds)
        assertEquals(4, result.generatedEventIds.size)
        assertEquals(6, result.document.events.size)
        assertTrue(result.generatedEventIds.distinct().size == 4)
        assertEquals("{\\pos(200,200)}A", result.document.events.first { it.id == 1L }.text)
        assertEquals("{\\pos(300,300)}B", result.document.events.first { it.id == 2L }.text)

        val invalid = document.copy(
            events = listOf(
                document.events[0],
                document.events[1].copy(text = "{\\pos(300,300)\\move(300,300,400,400)}B"),
            )
        )
        assertFailsWith<IllegalArgumentException> {
            AssFxComposition.composeMirrorStackBatch(
                document = invalid,
                eventIds = setOf(1, 2),
            )
        }
        // Pure compiler semantics: a failed batch cannot partially mutate its input.
        assertEquals(2, invalid.events.size)
        assertEquals("{\\pos(200,200)}A", invalid.events[0].text)
    }

    @Test
    fun flipEntranceUsesExistingScaleAndRotationAsItsFinalState() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 4,
                    start = SubTime(0),
                    end = SubTime(1000),
                    text = "{\\fscy80\\frx10}Flip",
                )
            )
        )

        val next = AssFxComposition.applyFlipEntrance(
            document,
            4,
            AssFlipEntranceSpec(
                durationMs = 300,
                startScalePercent = 10.0,
                overshootScalePercent = 125.0,
                startRotationXDegrees = 90.0,
            ),
        )
        val text = next.events.single().text

        assertTrue(text.contains("\\fscy8"))
        assertTrue(text.contains("\\t(0,200,\\fscy100)"))
        assertTrue(text.contains("\\t(200,300,\\fscy80)"))
        assertTrue(text.contains("\\frx100"))
        assertTrue(text.contains("\\t(0,300,\\frx10)"))
        assertTrue(text.endsWith("Flip"))
    }
}
