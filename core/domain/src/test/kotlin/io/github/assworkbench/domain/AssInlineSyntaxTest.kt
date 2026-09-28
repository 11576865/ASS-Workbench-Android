package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssInlineSyntaxTest {
    @Test
    fun analyzes_text_tags_values_and_escapes_without_rewriting() {
        val source = """{\fnHYRunYuan-55W\fs56\bord6\an2}正文\N第二行"""
        val analysis = AssInlineSyntax.analyze(source)

        assertFalse(analysis.hasErrors)
        assertEquals(setOf("fn", "fs", "bord", "an"), analysis.tagNames)
        assertEquals("HYRunYuan-55W", analysis.tags.first { it.name.equals("fn", true) }.value)
        assertEquals("56", analysis.tags.first { it.name.equals("fs", true) }.value)
        assertTrue(analysis.tokens.any { it.kind == AssInlineTokenKind.OVERRIDE_BLOCK })
        assertTrue(analysis.tokens.any { it.kind == AssInlineTokenKind.ESCAPE && it.text == "\\N" })
        assertTrue(analysis.tokens.any { it.kind == AssInlineTokenKind.TEXT && it.text.contains("正文") })
    }

    @Test
    fun preserves_unknown_tags_for_professional_editing() {
        val source = """{\x-custom(foo,bar)\t(0,400,\fs72)}Text"""
        val analysis = AssInlineSyntax.analyze(source)

        assertFalse(analysis.hasErrors)
        assertTrue("x-custom" in analysis.tagNames)
        assertTrue("t" in analysis.tagNames)
        assertTrue("fs" in analysis.tagNames)
    }

    @Test
    fun reports_unclosed_override_block() {
        val analysis = AssInlineSyntax.analyze("""{\fs56 broken""")

        assertTrue(analysis.hasErrors)
        assertTrue(analysis.tokens.any { it.kind == AssInlineTokenKind.MALFORMED_BLOCK })
        assertTrue(analysis.issues.any { "未闭合" in it.message })
    }

    @Test
    fun reports_stray_closing_brace() {
        val analysis = AssInlineSyntax.analyze("text}more")

        assertTrue(analysis.hasErrors)
        assertTrue(analysis.issues.any { "孤立" in it.message })
    }
}
