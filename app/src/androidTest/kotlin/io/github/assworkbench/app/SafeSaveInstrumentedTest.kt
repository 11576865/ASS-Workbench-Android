package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.EventEditReviewReceipt
import io.github.assworkbench.domain.EventMarginDraftBuffer
import io.github.assworkbench.domain.EventMarginValues
import io.github.assworkbench.domain.EventMetadataDraftBuffer
import io.github.assworkbench.domain.EventMetadataValues
import io.github.assworkbench.domain.EventTimingCanonical
import io.github.assworkbench.domain.EventTimingDraftBuffer
import io.github.assworkbench.domain.EventTimingValues
import io.github.assworkbench.domain.RawEventDraftBuffer
import io.github.assworkbench.domain.AssRoundTripVerifier
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.SubtitleProject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeSaveInstrumentedTest {
    private lateinit var application: Application
    private lateinit var store: RecoveryStore
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        SafeSaveTestProvider.reset()
        store = RecoveryStore(application)
        store.clear()
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(1_000),
                    end = SubTime(3_000),
                    text = "{\\bord2}Safe save",
                )
            )
        )
        store.write(
            SubtitleProject(title = "safe-save.ass"),
            document,
            AssTextEncoding.UTF8,
        )
        viewModel = EditorViewModel(application)
        viewModel.restoreRecovery()
    }

    @After
    fun tearDown() {
        store.clear()
        SafeSaveTestProvider.reset()
    }

    @Test
    fun verifiedSaveClearsDirtyOnlyAfterReadBack() {
        viewModel.updateEventText(1L, "{\\bord3}Saved safely")
        val uri = providerUri("verified.ass")
        seedTarget(uri, ByteArray(0))

        assertTrue(viewModel.saveTo(uri))
        assertFalse(viewModel.state.value.dirty)
        assertFalse(store.exists())
        assertTrue(viewModel.state.value.status.contains("回读验证"))

        val bytes = application.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val decoded = AssTextDecoder.decode(bytes)
        val saved = AssCodec.parse(decoded.text)
        assertEquals("{\\bord3}Saved safely", saved.events.single().text)
        assertTrue(AssRoundTripVerifier.verify(viewModel.state.value.document, decoded.text).equivalent)
    }

    @Test
    fun corruptReadBackRollsBackPreviousTargetAndKeepsRecovery() {
        viewModel.updateEventText(1L, "New unsafe candidate")
        val uri = providerUri("rollback.ass", corruptSecondRead = true)

        val previousDocument = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(0),
                    end = SubTime(1_000),
                    text = "LAST KNOWN GOOD",
                )
            )
        )
        val previous = AssTextEncoding.UTF8.encode(AssCodec.write(previousDocument))
        seedTarget(uri, previous)

        assertFalse(viewModel.saveTo(uri))
        assertTrue(viewModel.state.value.dirty)
        assertTrue(store.exists())
        assertTrue(viewModel.state.value.status.contains("已恢复保存前目标内容"))

        val restored = application.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertTrue(previous.contentEquals(restored))
    }


    @Test
    fun fiveGroupReviewedEventPersistsThroughVerifiedContentProviderSaveAndReopen() {
        // This exercises the product ViewModel transaction and the real ContentResolver
        // save/read-back boundary, not merely AssCodec.write() in memory.
        val originalAss = """
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 1920
            PlayResY: 1080

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, VendorMeta
            Style: Default,Arial,48,  first-style-opaque  
            Style: Opening,Arial,54,  second-style-opaque  

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text, VendorID
            Dialogue: 0,0:00:01.00,0:00:03.00,Default,Actor,0,0,0,,  {\pos(120,240)}Original, line  ,  custom-001  
            Dialogue: 2,0:00:06.00,0:00:07.00,Default,Other,0,0,0,,  Untouched sibling  ,  custom-002  
            ; preserve this opaque event line

            [Vendor Extension]
            Exact: must-survive
        """.trimIndent()
        val baseline = AssCodec.parse(originalAss)
        store.clear()
        store.write(SubtitleProject(title = "reviewed-five-groups.ass"), baseline, AssTextEncoding.UTF8)
        val reviewedVm = EditorViewModel(application)
        reviewedVm.restoreRecovery()
        val before = reviewedVm.state.value.document
        val target = before.events.first()
        val sessionId = reviewedVm.state.value.workspaceSessionId
        val receipt = EventEditReviewReceipt(
            eventId = target.id,
            text = RawEventDraftBuffer(target.text, "  {\\pos(280,160)}Edited, visible  "),
            timing = EventTimingDraftBuffer(
                EventTimingCanonical.from(target),
                EventTimingValues("1500", "5500"),
            ),
            metadata = EventMetadataDraftBuffer(
                EventMetadataValues.from(target),
                EventMetadataValues("4", "Reviewed Actor", true),
            ),
            style = RawEventDraftBuffer(target.style, "Opening"),
            margins = EventMarginDraftBuffer(
                EventMarginValues.from(target),
                EventMarginValues("15", "0", "28"),
            ),
        )
        assertEquals(5, receipt.changedGroupCount)
        assertTrue(reviewedVm.applyEventEditReview(receipt, sessionId))
        val committed = reviewedVm.state.value.document
        assertTrue(reviewedVm.state.value.dirty)
        assertEquals(before.events[1], committed.events[1])
        reviewedVm.undo()
        assertEquals(before, reviewedVm.state.value.document)
        reviewedVm.redo()
        assertEquals(committed, reviewedVm.state.value.document)

        val targetUri = providerUri("reviewed-five-groups.ass")
        seedTarget(targetUri, ByteArray(0))
        assertTrue(reviewedVm.saveTo(targetUri))
        assertFalse(reviewedVm.state.value.dirty)
        assertTrue(reviewedVm.state.value.status.contains("回读验证"))
        val savedBytes = application.contentResolver.openInputStream(targetUri)!!.use { it.readBytes() }
        val decoded = AssTextDecoder.decode(savedBytes)
        val reopened = AssCodec.parse(decoded.text)
        val changed = reopened.events.first()
        assertEquals("  {\\pos(280,160)}Edited, visible  ", changed.text)
        assertEquals(1500L, changed.start.millis)
        assertEquals(5500L, changed.end.millis)
        assertEquals("Opening", changed.style)
        assertEquals("Reviewed Actor", changed.name)
        assertEquals(4, changed.layer)
        assertTrue(changed.comment)
        assertEquals(15, changed.marginL)
        assertEquals(0, changed.marginR)
        assertEquals(28, changed.marginV)
        assertEquals("  custom-001  ", changed.extraFields["vendorid"])
        assertEquals(before.events[1], reopened.events[1])
        assertEquals(before.styles, reopened.styles)
        assertEquals(before.unknownSections.map { it.name }, reopened.unknownSections.map { it.name })
        assertTrue(decoded.text.contains("Exact: must-survive"))
        assertTrue(decoded.text.contains("; preserve this opaque event line"))
        assertTrue(AssRoundTripVerifier.verify(committed, decoded.text).equivalent)
    }

    private fun providerUri(name: String, corruptSecondRead: Boolean = false): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(BuildConfig.APPLICATION_ID + ".safesave")
            .appendPath(name)
            .apply { if (corruptSecondRead) appendQueryParameter("corruptSecondRead", "1") }
            .build()

    private fun seedTarget(uri: Uri, bytes: ByteArray) {
        application.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
    }
}
