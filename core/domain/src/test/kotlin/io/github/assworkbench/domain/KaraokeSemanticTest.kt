package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class KaraokeSemanticTest {
    @Test
    fun reads_and_retimes_karaoke() {
        val source = "{\\k20}Hel{\\kf30}lo {\\ko10}world"
        val segments = KaraokeSemantic.inspect(source)
        assertEquals(3, segments.size)
        assertEquals(600L, KaraokeSemantic.totalDurationMs(source))
        assertEquals("{\\k25}Hel{\\kf25}lo {\\ko10}world", KaraokeSemantic.retime(source, listOf(25, 25, 10)))
    }
}
