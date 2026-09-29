package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun selectivePastePreservesTextAndTiming() {
        val clipboard = EventFormatClipboardOps.capture(source)
        val all = EventFormatClipboardOps.apply(target, clipboard, EventFormatPasteMode.ALL)
        assertEquals("Fancy", all.style)
        assertEquals(2_000, all.start.millis)
        assertTrue(all.text.endsWith("Target"))

        val pos = EventFormatClipboardOps.apply(target, clipboard, EventFormatPasteMode.POSITION)
        assertTrue("\\pos(100,200)" in pos.text)
        assertTrue(pos.text.endsWith("Target"))
    }

    @Test
    fun fullOverridePastePreservesTargetCommentsAndNeverCopiesSourceComments() {
        val sourceWithComment = source.copy(text = "{source note}{\\bord5}{\\pos(100,200)}Source")
        val targetWithComment = target.copy(text = "{target note}{\\blur2}Target")
        val clipboard = EventFormatClipboardOps.capture(sourceWithComment)

        assertFalse("source note" in clipboard.leadingOverrides)

        val all = EventFormatClipboardOps.apply(targetWithComment, clipboard, EventFormatPasteMode.ALL)
        assertEquals("{target note}{\\bord5}{\\pos(100,200)}Target", all.text)

        val overrides = EventFormatClipboardOps.apply(targetWithComment, clipboard, EventFormatPasteMode.OVERRIDES)
        assertEquals("{target note}{\\bord5}{\\pos(100,200)}Target", overrides.text)
    }

    @Test
    fun positionPasteDoesNotTouchNestedTransformPosition() {
        val sourceEvent = source.copy(
            text = "{\\pos(100,200)\\t(0,500,\\pos(900,900))\\x-custom(foo)}Source",
        )
        val targetEvent = target.copy(
            text = "{target note}{\\pos(50,60)\\t(0,500,\\pos(5,5))\\x-custom(bar)}Target",
        )
        val clipboard = EventFormatClipboardOps.capture(sourceEvent)
        val result = EventFormatClipboardOps.apply(targetEvent, clipboard, EventFormatPasteMode.POSITION)

        assertTrue("{target note}" in result.text)
        assertTrue("\\pos(100,200)" in result.text)
        assertTrue("\\t(0,500,\\pos(5,5))" in result.text)
        assertTrue("\\x-custom(bar)" in result.text)
        assertFalse("\\pos(50,60)" in result.text)
        assertFalse("\\pos(900,900)" in clipboard.positionTags)
    }

    @Test
    fun effectsPasteMovesWholeTopLevelTransformWithoutEditingNestedPayload() {
        val sourceEvent = source.copy(
            text = "{\\bord4\\t(0,500,\\blur3\\pos(7,8))\\fad(10,20)}Source",
        )
        val targetEvent = target.copy(
            text = "{\\blur2\\t(0,200,\\blur8\\pos(1,2))\\bord1}Target",
        )
        val clipboard = EventFormatClipboardOps.capture(sourceEvent)
        val result = EventFormatClipboardOps.apply(targetEvent, clipboard, EventFormatPasteMode.EFFECTS)

        assertTrue("\\bord1" in result.text)
        assertTrue("\\t(0,500,\\blur3\\pos(7,8))" in result.text)
        assertTrue("\\fad(10,20)" in result.text)
        assertFalse("\\t(0,200,\\blur8\\pos(1,2))" in result.text)
    }
}
