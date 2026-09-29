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
        val output = AssAnimationSemantic.patchComplexFade(input, AssComplexFade(255, 0, 255, 0, 100, 900, 1000))
        assertTrue("\\bord4" in output)
        assertTrue("\\fade(255,0,255,0,100,900,1000)" in output)
        assertFalse("\\fad(" in output)
    }
}
