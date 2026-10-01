package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AssQuickFixTest {
    @Test
    fun position_move_conflict_requires_explicit_fix_and_preserves_other_tags() {
        val doc = AssDocument(events = listOf(
            AssEvent(
                1,
                start = SubTime(0),
                end = SubTime(1000),
                text = "{\\blur2\\pos(10,20)\\move(0,0,100,100)}x",
            ),
        ))
        val issue = AssQualityCheck.inspect(doc).first { it.kind == AssQcKind.POSITION_MOVE_CONFLICT }
        assertEquals(2, issue.quickFixes.size)
        val fixed = AssQuickFixExecutor.apply(doc, 1, "remove-pos")
        val text = fixed.events.single().text
        assertFalse(text.contains("\\pos("))
        kotlin.test.assertTrue(text.contains("\\move("))
        kotlin.test.assertTrue(text.contains("\\blur2"))
    }
}
