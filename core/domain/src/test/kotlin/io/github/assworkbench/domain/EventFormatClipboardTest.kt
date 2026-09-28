package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventFormatClipboardTest {
    private val source = AssEvent(
        id = 1,
        start = SubTime(0),
        end = SubTime(1_000),
        style = "Fancy",
        marginL = 10,
        marginR = 20,
        marginV = 30,
        text = "{\\pos(100,200)\\bord5\\fad(100,200)}Source",
    )
    private val target = AssEvent(
        id = 2,
        start = SubTime(2_000),
        end = SubTime(3_000),
        style = "Default",
        text = "{\\pos(50,60)\\blur2}Target",
    )

    @Test
    fun selective_paste_preserves_text_and_timing() {
        val clipboard = EventFormatClipboardOps.capture(source)
        val all = EventFormatClipboardOps.apply(target, clipboard, EventFormatPasteMode.ALL)
        assertEquals("Fancy", all.style)
        assertEquals(2_000, all.start.millis)
        assertTrue(all.text.endsWith("Target"))

        val pos = EventFormatClipboardOps.apply(target, clipboard, EventFormatPasteMode.POSITION)
        assertTrue("\\pos(100,200)" in pos.text)
        assertTrue(pos.text.endsWith("Target"))
    }
}
