package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AssTopLevelOverrideSyntaxTest {
    @Test
    fun nestedTransformPayloadTagsAreNotReportedAsTopLevel() {
        val text = "{\\fs50\\t(0,500,\\fs80\\clip(10,20,300,400))\\bord2}Text"

        val tags = AssTopLevelOverrideSyntax.tags(text)

        assertEquals(listOf("fs", "t", "bord"), tags.map { it.name.lowercase() })
        assertEquals("50", tags.first { it.name.equals("fs", true) }.value)
    }

    @Test
    fun leadingTagsStopBeforeVisibleTextButTopLevelTagsContinueAcrossSpans() {
        val text = "{\\an8}A{\\fs72}B"

        assertEquals(
            listOf("an", "fs"),
            AssTopLevelOverrideSyntax.tags(text).map { it.name.lowercase() },
        )
        assertEquals(
            listOf("an"),
            AssTopLevelOverrideSyntax.leadingTags(text).map { it.name.lowercase() },
        )
    }
}
