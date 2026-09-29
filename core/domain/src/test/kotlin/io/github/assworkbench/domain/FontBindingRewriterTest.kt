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
                    text = "{\\fnFace A}A{\\fn}B{\\fnFace B}C literal \\fnNotATag",
                ),
            ),
        )

        assertEquals(setOf("Face A", "Face B"), FontBindingRewriter.explicitFamilies(document))
    }

    @Test
    fun doesNotRewriteLiteralBackslashFnOutsideOverrideBlocks() {
        val document = AssDocument(
            styles = listOf(AssStyle(name = "Default", fontName = "Old")),
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime.ZERO,
                    end = SubTime(1000),
                    style = "Default",
                    text = "literal \\fnVisible {\\fnActual}styled",
                ),
            ),
        )

        val rewritten = FontBindingRewriter.forceFamily(document, "Renderer Family")

        assertEquals(
            "literal \\fnVisible {\\fnRenderer Family}styled",
            rewritten.events.single().text,
        )
    }
    @Test
    fun requestedFamiliesIgnoreUnusedStylesButIncludeInlineStyleResetsAndFonts() {
        val document = AssDocument(
            styles = listOf(
                AssStyle(name = "Default", fontName = "Base Face"),
                AssStyle(name = "Signs", fontName = "Sign Face"),
                AssStyle(name = "Unused", fontName = "Unused Face"),
            ),
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime.ZERO,
                    end = SubTime(1000),
                    style = "Default",
                    text = "{\\rSigns}A{\\fnInline Face}B{\\r}C",
                ),
            ),
        )

        assertEquals(setOf("Default", "Signs"), FontBindingRewriter.referencedStyleNames(document))
        assertEquals(
            setOf("Base Face", "Sign Face", "Inline Face"),
            FontBindingRewriter.requestedFamilies(document),
        )
    }

    @Test
    fun styleResetLikeTextOutsideOverrideBlocksDoesNotCreateAReference() {
        val document = AssDocument(
            styles = listOf(
                AssStyle(name = "Default", fontName = "Base Face"),
                AssStyle(name = "Unused", fontName = "Unused Face"),
            ),
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime.ZERO,
                    end = SubTime(1000),
                    style = "Default",
                    text = "literal \\rUnused is dialogue",
                ),
            ),
        )

        assertEquals(setOf("Default"), FontBindingRewriter.referencedStyleNames(document))
        assertEquals(setOf("Base Face"), FontBindingRewriter.requestedFamilies(document))
    }
}
