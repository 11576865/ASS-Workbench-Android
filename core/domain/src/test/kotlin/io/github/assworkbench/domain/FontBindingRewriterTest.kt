package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontBindingRewriterTest {
    @Test
    fun rewritesStylesAndExplicitInlineFontsButKeepsEmptyReset() {
        val document = AssDocument(
            styles = listOf(
                AssStyle(name = "Default", fontName = "Old Family"),
                AssStyle(name = "Signs", fontName = "Other Face"),
            ),
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime.ZERO,
                    end = SubTime(1000),
                    style = "Default",
                    text = "{\\fnWrong Name\\b1}中文{\\fn} reset {\\fnAnother Face}尾",
                ),
            ),
        )

        val rewritten = FontBindingRewriter.forceFamily(document, "HYRunYuan 55W")

        assertTrue(rewritten.styles.all { it.fontName == "HYRunYuan 55W" })
        assertEquals(
            "{\\fnHYRunYuan 55W\\b1}中文{\\fn} reset {\\fnHYRunYuan 55W}尾",
            rewritten.events.single().text,
        )
    }

    @Test
    fun reportsOnlyNonEmptyExplicitFontRequests() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime.ZERO,
                    end = SubTime(1000),
                    text = "{\\fnFace A}A{\\fn}B{\\fnFace B}C",
                ),
            ),
        )

        assertEquals(setOf("Face A", "Face B"), FontBindingRewriter.explicitFamilies(document))
    }
}
