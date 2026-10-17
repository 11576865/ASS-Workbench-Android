package io.github.assworkbench.app.ui

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssStyle
import io.github.assworkbench.domain.SubTime
import org.junit.Assert.*
import org.junit.Test

class R3WriteSourceDetailsTest {
    private val sourceText = "{\\pos(960,720)\\fs48}世界依然值得凝视"
    private val nextText = "{\\pos(960,720)\\fs52}世界依然值得凝视"
    private val event = AssEvent(
        id = 39L, start = SubTime(80_800), end = SubTime(82_400),
        style = "Opening", text = sourceText,
    )
    private val document = AssDocument(
        styles = listOf(AssStyle(name = "Opening", fontSize = 36.0)),
        events = listOf(event),
    )
    private val receipt = R3TextWriteReceipt(
        eventId = 39L, eventStyle = "Opening",
        baseText = sourceText, canonicalText = sourceText,
        draftText = nextText,
    )

    @Test fun writeReviewShowsCurrentAndDraftFromRealAssAndStyleBaseline() {
        val summary = r3WriteSourceSummary(document, event, receipt)
        assertNotNull(summary)
        assertEquals("字号", summary!!.property)
        assertEquals("48", summary.originalValue)
        assertEquals("52", summary.draftValue)
        assertEquals("36", summary.styleValue)
        assertEquals("Opening", summary.styleLabel)
        assertEquals("Event #39 · 局部 override", summary.originLabel)
        assertEquals(1, summary.changedEffectiveProperties)
        assertFalse(summary.spanOrTimeDependent)
        assertTrue(receipt.mayCommit(event.text))
        assertEquals("36", document.styles.single().fontSize.toInt().toString())
    }

    @Test fun staleOrWrongBindingCannotRenderAConfidentReadChain() {
        assertNull(r3WriteSourceSummary(document, event.copy(text = "{\\fs99}外部修改"), receipt))
        assertNull(r3WriteSourceSummary(document, event.copy(id = 40), receipt))
        assertNull(r3WriteSourceSummary(document, event, receipt.copy(eventStyle = "Different")))
    }

    @Test fun malformedDraftFailsClosedToRawInspectionInsteadOfInventingSource() {
        val malformed = receipt.copy(draftText = "{\\fs")
        assertNull(r3WriteSourceSummary(document, event, malformed))
    }

    @Test fun styleResetRetainsTruthfulEventStyleBaseline() {
        val resetEvent = event.copy(text = "{\\rAlternate}当前已切换 Style")
        val resetDocument = document.copy(
            styles = document.styles + AssStyle(name = "Alternate", fontSize = 24.0),
            events = listOf(resetEvent),
        )
        val resetReceipt = receipt.copy(
            baseText = resetEvent.text,
            canonicalText = resetEvent.text,
            draftText = "{\\rAlternate\\fs52}当前已切换 Style",
        )
        val summary = r3WriteSourceSummary(resetDocument, resetEvent, resetReceipt)
        assertNotNull(summary)
        assertEquals("36", summary!!.styleValue)
        assertEquals("Opening", summary.styleLabel)
        assertEquals("Style · Alternate", summary.originLabel)
        assertEquals("24", summary.originalValue)
        assertEquals("52", summary.draftValue)
    }

    @Test fun rawChangeWithNoEffectiveValueDifferenceIsNotClaimedAsSizeEdit() {
        val changedWords = receipt.copy(draftText = "{\\pos(960,720)\\fs48}新的字幕正文")
        val summary = r3WriteSourceSummary(document, event, changedWords)
        assertNotNull(summary)
        assertEquals(0, summary!!.changedEffectiveProperties)
        assertEquals(summary.originalValue, summary.draftValue)
    }
}
