package io.github.assworkbench.app

import io.github.assworkbench.domain.AssComplexFade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFadeWritePlanTest {
    @Test fun simpleFadeProposalPreservesNonFadeOverrides() {
        val input = "{\\pos(100,200)\\blur2\\x-owner(foo)\\fad(50,100)}你好"
        val output = proposeEventFadeSource(input, "fad", 200, 300)
        requireNotNull(output)
        assertTrue(output.contains("\\pos(100,200)"))
        assertTrue(output.contains("\\blur2"))
        assertTrue(output.contains("\\x-owner(foo)"))
        assertTrue(output.contains("\\fad(200,300)"))
        assertTrue(output.endsWith("你好"))
    }

    @Test fun complexFadeNeedsOrderedTimesAndBoundedAlpha() {
        val source = "{\\pos(100,200)\\fad(50,100)}Line"
        assertNull(proposeEventFadeSource(
            source, "fade", complex = AssComplexFade(0, 255, 0, 300, 200, 600, 800),
        ))
        assertNull(proposeEventFadeSource(
            source, "fade", complex = AssComplexFade(0, 256, 0, 0, 200, 600, 800),
        ))
        val result = proposeEventFadeSource(
            source, "fade", complex = AssComplexFade(255, 0, 255, 0, 200, 600, 800),
        )
        requireNotNull(result)
        assertTrue(result.contains("\\pos(100,200)"))
        assertTrue(result.contains("\\fade(255,0,255,0,200,600,800)"))
    }

    @Test fun invalidNumericArgumentsNeverGenerateClampedOutput() {
        val source = "{\\fad(50,100)}Line"
        assertNull(proposeEventFadeSource(source, "fad", -1, 100))
        assertNull(proposeEventFadeSource(source, "fad", 1_000_001, 100))
        assertNull(proposeEventFadeSource(source, "fad", null, 100))
        assertNull(proposeEventFadeSource(source, "fade"))
        assertNull(proposeEventFadeSource(source, "unsupported"))
        assertNull(proposeEventFadeSource("{\\fad(50,100)Broken", "fad", 200, 300))
    }

    @Test fun unchangedAndClearProposalDoNotCreateDifferentRawText() {
        val source = "{\\fad(120,180)}Line"
        val same = proposeEventFadeSource(source, "fad", 120, 180)
        assertEquals(source, same)
        val clear = proposeEventFadeSource(source, "none")
        requireNotNull(clear)
        assertFalse(clear.contains("\\fad"))
        assertTrue(clear.contains("Line"))
    }
}
