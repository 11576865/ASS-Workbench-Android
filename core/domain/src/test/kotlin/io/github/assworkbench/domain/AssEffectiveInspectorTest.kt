package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssEffectiveInspectorTest {
    @Test
    fun laterSpanOverridesDoNotMasqueradeAsOneEventWideValue() {
        val document = AssDocument(
            styles = listOf(
                AssStyle(name = "Default", fontName = "Base", fontSize = 48.0),
                AssStyle(name = "Alt", fontName = "AltFont", fontSize = 72.0),
            ),
            events = listOf(
                AssEvent(
                    id = 2,
                    start = SubTime(0),
                    end = SubTime(1_000),
                    style = "Default",
                    text = "{\\fs56}A{\\rAlt}B{\\fs80}C",
                )
            ),
        )

        val values = AssEffectiveInspector.inspect(document, document.events.single()).associateBy { it.name }

        assertEquals("56", values.getValue("Size").effectiveValue)
        assertEquals("Default", values.getValue("Size").effectiveStyle)
        assertTrue(values.getValue("Size").spanDependent)
        assertEquals("Base", values.getValue("Font").effectiveValue)
        assertTrue(values.getValue("Font").spanDependent)
    }

    @Test
    fun leadingStyleResetResolvesInitialSpanAgainstExactReferencedStyle() {
        val document = AssDocument(
            styles = listOf(
                AssStyle(name = "Default", fontName = "Base", fontSize = 48.0),
                AssStyle(name = "Alt", fontName = "AltFont", fontSize = 72.0),
            ),
            events = listOf(
                AssEvent(
                    id = 3,
                    start = SubTime(0),
                    end = SubTime(1_000),
                    style = "Default",
                    text = "{\\rAlt\\fs64}A",
                )
            ),
        )

        val values = AssEffectiveInspector.inspect(document, document.events.single()).associateBy { it.name }

        assertEquals("AltFont", values.getValue("Font").effectiveValue)
        assertEquals("64", values.getValue("Size").effectiveValue)
        assertEquals("Alt", values.getValue("Font").effectiveStyle)
        assertEquals("Alt", values.getValue("Size").effectiveStyle)
        assertTrue(!values.getValue("Font").spanDependent)
    }

    @Test
    fun missingBaseOrResetStyleFailsClosed() {
        val missingBase = AssDocument(
            styles = listOf(AssStyle(name = "Default")),
            events = listOf(
                AssEvent(
                    id = 4,
                    start = SubTime(0),
                    end = SubTime(1_000),
                    style = "Missing",
                    text = "A",
                )
            ),
        )
        assertFailsWith<IllegalStateException> {
            AssEffectiveInspector.inspect(missingBase, missingBase.events.single())
        }

        val missingReset = missingBase.copy(
            events = listOf(
                missingBase.events.single().copy(
                    id = 5,
                    style = "Default",
                    text = "{\\rMissing}A",
                )
            ),
        )
        assertFailsWith<IllegalStateException> {
            AssEffectiveInspector.inspect(missingReset, missingReset.events.single())
        }
    }

    @Test
    fun reports_style_event_override_and_effective_values() {
        val document = AssDocument(
            styles = listOf(
                AssStyle(
                    name = "Default",
                    fontName = "BaseFont",
                    fontSize = 48.0,
                    outline = 2.0,
                    alignment = 2,
                    marginV = 40,
                )
            ),
            events = listOf(
                AssEvent(
                    id = 1,
                    start = SubTime(0),
                    end = SubTime(1_000),
                    style = "Default",
                    marginV = 60,
                    text = "{\\fnOverrideFont\\fs56\\bord6\\an8\\pos(960,120)}Text",
                )
            ),
        )
        val values = AssEffectiveInspector.inspect(document, document.events.single()).associateBy { it.name }
        assertEquals("OverrideFont", values.getValue("Font").effectiveValue)
        assertEquals("56", values.getValue("Size").effectiveValue)
        assertEquals("6", values.getValue("Border").effectiveValue)
        assertEquals("8", values.getValue("Alignment").effectiveValue)
        assertEquals("60", values.getValue("Margin V").effectiveValue)
        assertEquals("960, 120", values.getValue("Position").effectiveValue)
    }
}
