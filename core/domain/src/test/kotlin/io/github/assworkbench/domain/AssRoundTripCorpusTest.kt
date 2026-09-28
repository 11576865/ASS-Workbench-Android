package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssRoundTripCorpusTest {
    private data class Fixture(val name: String, val sentinels: List<String>)

    private val fixtures = listOf(
        Fixture("aegisub-basic.ass", listOf("[Aegisub Project Garbage]", "Last Style Storage: Default", "{\\pos(960,120)}招牌")),
        Fixture("custom-columns.ass", listOf("VendorMeta", "style-extra", "VendorID", "QCState", "line-001", "reviewed")),
        Fixture(
            "opaque-sections.ass",
            listOf(
                "; preserve-script-comment",
                "Tool-Opaque-No-Colon",
                "; preserve-style-comment",
                "Tool-Metadata: keep-me",
                "; preserve-event-comment",
                "Command: opaque payload",
                "[Custom Tool Section]",
                "Opaque-Key: Opaque-Value",
            ),
        ),
    )

    @Test
    fun corpusSurvivesOneFieldEditWithoutDroppingSupportedOpaqueData() {
        fixtures.forEach { fixture ->
            val source = javaClass.getResource("/roundtrip/" + fixture.name)?.readText()
                ?: error("Missing fixture " + fixture.name)
            val parsed = AssCodec.parse(source)
            assertTrue(parsed.events.isNotEmpty(), fixture.name)

            val styleExtras = parsed.styles.map { it.extraFields }
            val eventExtras = parsed.events.map { it.extraFields }
            val unknownNames = parsed.unknownSections.map { it.name }
            val first = parsed.events.first()
            val editedText = first.text + " [roundtrip-edit]"
            val edited = parsed.copy(
                events = parsed.events.map { if (it.id == first.id) it.copy(text = editedText) else it }
            )

            val output = AssCodec.write(edited)
            fixture.sentinels.forEach { marker ->
                assertTrue(output.contains(marker), fixture.name + " lost " + marker)
            }

            val reparsed = AssCodec.parse(output)
            assertEquals(parsed.styles.size, reparsed.styles.size, fixture.name)
            assertEquals(parsed.events.size, reparsed.events.size, fixture.name)
            assertEquals(styleExtras, reparsed.styles.map { it.extraFields }, fixture.name)
            assertEquals(eventExtras, reparsed.events.map { it.extraFields }, fixture.name)
            assertEquals(unknownNames, reparsed.unknownSections.map { it.name }, fixture.name)
            assertEquals(editedText, reparsed.events.first().text, fixture.name)
        }
    }
}
