package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AssEffectiveInspectorTest {
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
