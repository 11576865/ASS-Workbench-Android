package io.github.assworkbench.domain

import org.junit.Assert.*
import org.junit.Test

class KaraokeInitializationTest {
    @Test
    fun initializationPreservesWhitespaceAndVisibleText() {
        val raw = "  one  two\tthree "
        val segments = AssKaraokeCodec.initializePlainText(raw)!!
        assertEquals(3, segments.size)
        assertEquals(raw, AssInlineSyntax.visibleText(AssKaraokeCodec.write(segments)))
    }

    @Test
    fun initializationCannotEraseOverridesEscapesOrDrawingSyntax() {
        for (raw in listOf("{\\\\pos(10,20)}Hello", "one\\\\Ntwo", "{\\\\p1}m 0 0 l 10 10", "{comment}hello")) {
            assertNull(AssKaraokeCodec.initializePlainText(raw))
        }
        assertTrue(AssKaraokeCodec.initializePlainText("   ")!!.isEmpty())
    }
}
