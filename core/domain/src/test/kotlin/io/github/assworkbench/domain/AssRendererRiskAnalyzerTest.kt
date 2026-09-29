package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssRendererRiskAnalyzerTest {
    private fun document(text: String, style: AssStyle = AssStyle()) = AssDocument(
        styles = listOf(style),
        events = listOf(
            AssEvent(
                id = 7,
                start = SubTime(0),
                end = SubTime(2_000),
                style = style.name,
                text = text,
            )
        ),
    )

    @Test
    fun ordinary_geometry_and_drawing_remain_previewable() {
        val source = "{\\pos(960,540)\\frz45\\fax0.2\\fscx120}Hello{\\p1}m 0 0 l 100 100{\\p0}"
        assertTrue(AssRendererRiskAnalyzer.inspect(document(source)).isEmpty())
    }

    @Test
    fun extreme_override_values_are_blocking_without_rewriting_source() {
        val source = "{\\frz1000000000\\fax1000000000}Text"
        val doc = document(source)
        val risks = AssRendererRiskAnalyzer.inspect(doc)

        assertTrue(risks.any { it.kind == AssRendererRiskKind.EXTREME_NUMERIC })
        assertEquals(source, doc.events.single().text)
    }

    @Test
    fun parenthesized_extreme_rotation_is_detected() {
        val risks = AssRendererRiskAnalyzer.inspect(document("{\\frx(1000000)}Text"))
        assertTrue(risks.any { it.kind == AssRendererRiskKind.EXTREME_NUMERIC })
    }

    @Test
    fun move_timestamps_are_not_misclassified_as_coordinates() {
        val risks = AssRendererRiskAnalyzer.inspect(
            document("{\\move(0,0,1920,1080,0,20000000)}Text")
        )
        assertTrue(risks.none { it.kind == AssRendererRiskKind.EXTREME_NUMERIC })
    }

    @Test
    fun extreme_drawing_coordinates_are_detected() {
        val source = "{\\p1}m -2147483648 -2147483648 l 2147483647 2147483647{\\p0}"
        val risks = AssRendererRiskAnalyzer.inspect(document(source))
        assertTrue(risks.any { it.kind == AssRendererRiskKind.EXTREME_DRAWING_COORDINATE })
    }

    @Test
    fun huge_vector_clip_is_detected_as_drawing_input() {
        val payload = buildString {
            append("m 0 0 ")
            repeat(30_000) { append("l 1 1 ") }
        }
        val risks = AssRendererRiskAnalyzer.inspect(document("{\\clip(" + payload + ")}Text"))
        assertTrue(risks.any { it.kind == AssRendererRiskKind.OVERSIZED_DRAWING })
    }

    @Test
    fun extreme_style_geometry_is_detected_on_used_style() {
        val style = AssStyle(name = "Danger", angle = 1_000_000.0)
        val risks = AssRendererRiskAnalyzer.inspect(document("Text", style))
        assertTrue(risks.any { it.kind == AssRendererRiskKind.EXTREME_NUMERIC })
    }
}
