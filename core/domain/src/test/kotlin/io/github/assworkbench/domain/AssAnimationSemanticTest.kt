package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssAnimationSemanticTest {
    @Test
    fun readsBothFadeFormsAndDetectsConflict() {
        val got = AssAnimationSemantic.inspect("{\\fad(100,200)\\fade(0,255,0,0,100,900,1000)\\t(0,500,\\fad(10,20))}Hi")
        assertEquals(AssSimpleFade(100, 200), got.simpleFade)
        assertEquals(AssComplexFade(0, 255, 0, 0, 100, 900, 1000), got.complexFade)
        assertTrue(got.fadeConflict)
    }

    @Test
    fun simpleFadePatchPreservesNestedFadeUnknownTagsAndComments() {
        val input = "{editor note}{\\x-custom(foo)\\t(0,500,\\fad(10,20))\\fad(100,200)}Hi"
        val output = AssAnimationSemantic.patchSimpleFade(input, 300, 400)
        assertTrue("{editor note}" in output)
        assertTrue("\\x-custom(foo)" in output)
        assertTrue("\\t(0,500,\\fad(10,20))" in output)
        assertTrue("\\fad(300,400)" in output)
        assertFalse("\\fad(100,200)" in output)
    }

    @Test
    fun complexFadeReplacesSimpleFadeWithoutTouchingOtherTags() {
        val input = "{\\bord4\\fad(100,200)}Hi"
        val output = AssAnimationSemantic.patchComplexFade(
            input,
            AssComplexFade(255, 0, 255, 0, 100, 900, 1000),
        )
        assertTrue("\\bord4" in output)
        assertTrue("\\fade(255,0,255,0,100,900,1000)" in output)
        assertFalse("\\fad(" in output)
    }

    @Test
    fun parsesAllTransformFormsAndNestedCommaPayload() {
        val got = AssAnimationSemantic.inspect(
            "{\\t(\\frz30)\\t(2,\\blur3)\\t(100,500,\\fscx120)\\t(100,500,1.5,\\clip(10,20,300,400)\\blur2)}Hi"
        ).transforms
        assertEquals(4, got.size)
        assertEquals("\\frz30", got[0].tags)
        assertEquals(2.0, got[1].accel)
        assertEquals(100.0, got[2].startMs)
        assertEquals(500.0, got[2].endMs)
        assertEquals(1.5, got[3].accel)
        assertEquals("\\clip(10,20,300,400)\\blur2", got[3].tags)
        assertTrue(got.none { it.malformed })
    }

    @Test
    fun patchingOneTransformPreservesSiblingTransformUnknownTagsAndComment() {
        val input = "{editor note}{\\x-custom(foo)\\t(0,500,\\frz30)\\bord4\\t(1.2,\\blur3)}Hi"
        val output = AssAnimationSemantic.patchTransform(
            input,
            0,
            AssTransform(
                startMs = 100.0,
                endMs = 700.0,
                accel = 2.0,
                tags = "\\frz45\\fscx120",
            ),
        )
        assertTrue("{editor note}" in output)
        assertTrue("\\x-custom(foo)" in output)
        assertTrue("\\bord4" in output)
        assertTrue("\\t(100,700,2,\\frz45\\fscx120)" in output)
        assertTrue("\\t(1.2,\\blur3)" in output)
    }

    @Test
    fun removeTransformRemovesOnlyRequestedTopLevelTransform() {
        val input = "{\\t(0,500,\\blur2)\\bord4\\t(500,900,\\frz20)}Hi"
        val output = AssAnimationSemantic.removeTransform(input, 0)
        assertFalse("\\t(0,500,\\blur2)" in output)
        assertTrue("\\bord4" in output)
        assertTrue("\\t(500,900,\\frz20)" in output)
    }

    @Test
    fun malformedTransformIsExposedWithoutRewrite() {
        val input = "{\\t(nope,\\blur2)\\bord4}Hi"
        val got = AssAnimationSemantic.inspect(input).transforms.single()
        assertTrue(got.malformed)
        assertEquals(input, AssAnimationSemantic.patchTransform(input, 0, got))
    }
}
