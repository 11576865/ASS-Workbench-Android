package io.github.assworkbench.domain

import kotlin.test.*

class EventEditReviewReceiptTest {
    private val event = AssEvent(id = 1L, start = SubTime(1000), end = SubTime(3000), text = "{\\fs40}old", name = "Actor")
    private fun receipt(raw: String = event.text, end: String = event.end.toAss(), layer: String = "0") =
        EventEditReviewReceipt(event.id, RawEventDraftBuffer(event.text, raw),
            EventTimingDraftBuffer(EventTimingCanonical.from(event), EventTimingValues(event.start.toAss(), end)),
            EventMetadataDraftBuffer(EventMetadataValues.from(event), EventMetadataValues(layer, "Actor", false)))

    @Test fun appliesAllDirtyGroupsToOneEventAndPreservesUneditedProperties() {
        val current = event.copy(style = "Other", effect = "effect", marginL = 12)
        val next = receipt(raw = "{\\fs40}new", end = "5000", layer = "3").applyTo(current)!!
        assertEquals("{\\fs40}new", next.text)
        assertEquals(5000L, next.end.millis)
        assertEquals(3, next.layer)
        assertEquals(current.style, next.style)
        assertEquals(current.effect, next.effect)
        assertEquals(current.marginL, next.marginL)
    }

    @Test fun anyDirtyGroupConflictRejectsWholeWrite() {
        val review = receipt(raw = "new", end = "5000", layer = "3")
        assertNull(review.applyTo(event.copy(name = "external")))
        assertNull(review.applyTo(event.copy(text = "external")))
        assertNull(review.applyTo(event.copy(end = SubTime(4500))))
    }

    @Test fun untouchedGroupsPreserveConcurrentCanonicalChanges() {
        val current = event.copy(text = "external raw", name = "external actor")
        val next = receipt(end = "5000").applyTo(current)!!
        assertEquals(current.text, next.text)
        assertEquals(current.name, next.name)
        assertEquals(5000L, next.end.millis)
    }

    @Test fun invalidFieldRejectsAllOtherChanges() {
        assertNull(receipt(raw = "new", end = "500").applyTo(event))
        assertNull(receipt(raw = "new", layer = "not an int").applyTo(event))
        assertNull(receipt(raw = "new", layer = "1000").applyTo(event))
    }

    @Test fun absentOrDifferentTargetCannotApply() {
        assertNull(receipt(raw = "new").applyTo(null))
        assertNull(receipt(raw = "new").applyTo(event.copy(id = 2L)))
    }

    @Test fun cleanAndFormattingOnlyTimeInputDoNotCreateChange() {
        assertFalse(receipt().hasChanges)
        assertFalse(receipt(end = "3000").hasChanges)
        assertEquals(event, receipt().applyTo(event))
    }
    @Test fun styleReferenceSharesTransactionWithoutStrippingRawOverrides() {
        val review = receipt(raw = "{\\fs40}new").copy(style = RawEventDraftBuffer("Default", "Opening"))
        val next = review.applyTo(event, setOf("Default", "Opening"))!!
        assertEquals("Opening", next.style)
        assertEquals("{\\fs40}new", next.text)
        assertEquals(2, review.changedGroupCount)
    }

    @Test fun missingStyleOrConcurrentReferenceRejectsWholeTransaction() {
        val review = receipt(raw = "new").copy(style = RawEventDraftBuffer("Default", "Opening"))
        assertNull(review.applyTo(event, setOf("Default")))
        assertNull(review.applyTo(event.copy(style = "Other"), setOf("Default", "Opening", "Other")))
        assertNotNull(review.applyTo(event, setOf("Default", "Opening")))
    }

    @Test fun cleanStyleDoesNotBlockUnrelatedReferenceChange() {
        val review = receipt(raw = "new").copy(style = RawEventDraftBuffer("Default", "Default"))
        assertEquals("Other", review.applyTo(event.copy(style = "Other"))!!.style)
    }
}
