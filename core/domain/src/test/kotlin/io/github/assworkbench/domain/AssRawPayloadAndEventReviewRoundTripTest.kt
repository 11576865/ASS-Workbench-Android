package io.github.assworkbench.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real ASS serialization boundary, including opaque columns that structured editing must not touch. */
class AssRawPayloadAndEventReviewRoundTripTest {
    private val source = """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1920
        PlayResY: 1080

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, VendorMeta
        Style: Default,Arial,48,  retained-style-data  
        Style: Opening,Arial,54,  opening-style-data  

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text, VendorID
        Dialogue: 0,0:00:01.00,0:00:03.00,Default,Actor,0,0,0,,  {\pos(120,240)}Hello, world  ,  cue-001  
        Dialogue: 2,0:00:06.00,0:00:07.00,Default,Other,0,0,0,,  Another line  ,  cue-002  
        ; an opaque Event section line

        [Vendor Registry]
        Owner: leave-me-as-is
    """.trimIndent()

    @Test
    fun parsesAndSerializesWhitespaceSensitiveTextAndOpaqueColumnsWithoutTrimming() {
        val document = AssCodec.parse(source)
        assertEquals("  {\\pos(120,240)}Hello, world  ", document.events.first().text)
        assertEquals("  cue-001  ", document.events.first().extraFields["vendorid"])
        assertEquals("  retained-style-data  ", document.styles.first().extraFields["vendormeta"])
        assertEquals("  Another line  ", document.events[1].text)

        val encoded = AssCodec.write(document)
        val reopened = AssCodec.parse(encoded)
        assertEquals(document.events, reopened.events)
        assertEquals(document.styles, reopened.styles)
        assertTrue(AssRoundTripVerifier.verify(document, encoded).equivalent)
        assertTrue(encoded.contains("Owner: leave-me-as-is"))
        assertTrue(encoded.contains("; an opaque Event section line"))
    }

    @Test
    fun fiveGroupReviewCanBeSavedAndReopenedWithoutDroppingUnknownAssData() {
        val loaded = AssCodec.parse(source)
        val current = loaded.events.first()
        val review = EventEditReviewReceipt(
            eventId = current.id,
            text = RawEventDraftBuffer(current.text, "  {\\pos(280,160)}Edited, preserved  "),
            timing = EventTimingDraftBuffer(
                EventTimingCanonical.from(current),
                EventTimingValues("1500", "5500"),
            ),
            metadata = EventMetadataDraftBuffer(
                EventMetadataValues.from(current),
                EventMetadataValues("4", "Reviewed Actor", true),
            ),
            style = RawEventDraftBuffer(current.style, "Opening"),
            margins = EventMarginDraftBuffer(
                EventMarginValues.from(current),
                EventMarginValues("15", "0", "28"),
            ),
        )
        val styleNames = loaded.styles.mapTo(mutableSetOf()) { it.name }
        assertEquals(5, review.changedGroupCount)
        assertTrue(review.validationErrors(current, styleNames).isEmpty())
        val edited = assertNotNull(review.applyTo(current, styleNames))
        val editedDocument = loaded.copy(
            events = loaded.events.map { if (it.id == current.id) edited else it },
        )

        val savedAss = AssCodec.write(editedDocument)
        val reopened = AssCodec.parse(savedAss)
        val restored = reopened.events.first()
        assertEquals("  {\\pos(280,160)}Edited, preserved  ", restored.text)
        assertEquals(1500L, restored.start.millis)
        assertEquals(5500L, restored.end.millis)
        assertEquals("Opening", restored.style)
        assertEquals("Reviewed Actor", restored.name)
        assertEquals(4, restored.layer)
        assertTrue(restored.comment)
        assertEquals(15, restored.marginL)
        assertEquals(0, restored.marginR)
        assertEquals(28, restored.marginV)
        assertEquals("  cue-001  ", restored.extraFields["vendorid"])
        assertEquals(loaded.events[1], reopened.events[1])
        assertEquals(loaded.styles, reopened.styles)
        assertEquals(loaded.unknownSections, reopened.unknownSections)
        assertEquals(loaded.eventSectionExtras, reopened.eventSectionExtras)
        assertTrue(AssRoundTripVerifier.verify(editedDocument, savedAss).equivalent)

        // A missing Style rejects the complete proposal, not just the reference change.
        assertFalse(review.validationErrors(current, setOf("Default")).isEmpty())
        assertEquals(null, review.applyTo(current, setOf("Default")))
    }

    @Test
    fun finalTextColumnPreservesOnlyWhitespaceAndInlineAssEscapes() {
        val sample = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,   
            Dialogue: 0,0:00:02.00,0:00:03.00,Default,,0,0,0,,  {\k20}A\N B  
        """.trimIndent()
        val loaded = AssCodec.parse(sample)
        assertEquals("   ", loaded.events[0].text)
        assertEquals("  {\\k20}A\\N B  ", loaded.events[1].text)
        val reopened = AssCodec.parse(AssCodec.write(loaded))
        assertEquals(loaded.events, reopened.events)
    }
}
