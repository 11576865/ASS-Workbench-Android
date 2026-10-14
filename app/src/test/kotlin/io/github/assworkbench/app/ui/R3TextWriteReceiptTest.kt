package io.github.assworkbench.app.ui

import io.github.assworkbench.domain.RawEventDraftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R3TextWriteReceiptTest {
    private val receipt = R3TextWriteReceipt(
        eventId = 39L,
        eventStyle = "Opening",
        baseText = "{\\pos(960,720)\\fs48}世界依然值得凝视",
        canonicalText = "{\\pos(960,720)\\fs48}世界依然值得凝视",
        draftText = "{\\pos(960,720)\\fs52}世界依然值得凝视",
    )

    @Test fun oneEventWithFreshTextCanBeCommitted() {
        assertEquals(RawEventDraftState.DRAFT_ONLY, receipt.state)
        assertTrue(receipt.mayCommit(receipt.canonicalText))
        assertEquals(39L, receipt.eventId)
        assertEquals("Opening", receipt.eventStyle)
    }

    @Test fun externalOverwriteBlocksSilentStaleWrite() {
        assertFalse(receipt.mayCommit("{\\pos(960,720)\\fs45}世界依然值得凝视"))
        val externallyEdited = receipt.copy(canonicalText = "{\\fs45}世界依然值得凝视")
        assertEquals(RawEventDraftState.EXTERNAL_CONFLICT, externallyEdited.state)
        assertFalse(externallyEdited.mayCommit(externallyEdited.canonicalText))
    }

    @Test fun deletedEventAndUnchangedTextAreNotWritable() {
        assertFalse(receipt.mayCommit(null))
        val unchanged = receipt.copy(draftText = receipt.canonicalText)
        assertEquals(RawEventDraftState.CLEAN, unchanged.state)
        assertFalse(unchanged.mayCommit(unchanged.canonicalText))
    }

    @Test fun oldBaseCannotBeUsedToOverwriteNewCanonicalText() {
        val stale = receipt.copy(baseText = "旧版本")
        assertFalse(stale.mayCommit(stale.canonicalText))
    }
}
