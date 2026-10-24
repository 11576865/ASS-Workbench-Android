package io.github.assworkbench.app.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.RecoveryStore
import io.github.assworkbench.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventEditReviewInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    @After fun clearRecovery() { RecoveryStore(application).clear() }

    private fun load(): EditorViewModel {
        RecoveryStore(application).write(SubtitleProject(title = "Atomic review.ass"),
            AssDocument(styles = listOf(AssStyle(), AssStyle(name = "Opening")), events = listOf(
                AssEvent(id = 1L, start = SubTime(1000), end = SubTime(3000), text = "Original"),
                AssEvent(id = 2L, start = SubTime(5000), end = SubTime(6000), text = "Other"))), AssTextEncoding.UTF8)
        return EditorViewModel(application).also { vm ->
            vm.restoreRecovery()
            composeRule.waitUntil(10_000) { vm.state.value.subtitleLoaded }
        }
    }

    @Test fun reviewIsReadOnlyThenCommitsFiveGroupsWithOneUndoWithoutChangingFocus() {
        val vm = load()
        val before = vm.state.value.document
        val session = vm.state.value.workspaceSessionId
        val text = EventTextDraftStore(session)
        val style = EventTextDraftStore(session)
        val time = EventTimingDraftStore(session)
        val meta = EventMetadataDraftStore(session)
        val margins = EventMarginDraftStore(session)
        val event = before.events.first()
        text.obtain("TEXT:primary", event.id, event.text).value = TextFieldValue("Reviewed")
        style.obtain("TEXT:primary", event.id, event.style).value = TextFieldValue("Opening")
        time.obtain("TEXT:primary", event).values = EventTimingValues("1500", "4500")
        meta.obtain("TEXT:primary", event).values = EventMetadataValues("3", "Actor", true)
        margins.obtain("TEXT:primary", event).values = EventMarginValues("15", "0", "28")
        composeRule.runOnIdle { vm.focusEvent(2L, seek = false) }
        composeRule.setContent {
            val state by vm.state.collectAsState()
            MaterialTheme {
                CompositionLocalProvider(LocalEventTextDraftStore provides text,
                    LocalEventStyleDraftStore provides style,
                    LocalEventTimingDraftStore provides time, LocalEventMetadataDraftStore provides meta,
                    LocalEventMarginDraftStore provides margins) {
                    FixedV11InspectorStatus(state.document.events.first(), state, vm, "TEXT:primary", true,
                        emptyList(), onOpenQc = {})
                }
            }
        }
        composeRule.onNodeWithTag("fixed-v11-review-current").performClick()
        composeRule.onNodeWithTag("r3-write-after").assertTextContains("Reviewed")
        composeRule.onNodeWithTag("event-review-style-after").performScrollTo().assertTextContains("Opening")
        composeRule.onNodeWithTag("event-review-margins-after").performScrollTo().assertTextContains("15 / 0 / 28")
        composeRule.runOnIdle { assertEquals(before, vm.state.value.document) }
        composeRule.onNodeWithTag("r3-write-confirm").performClick()
        composeRule.runOnIdle {
            val after = vm.state.value.document
            assertEquals("Reviewed", after.events.first().text)
            assertEquals("Opening", after.events.first().style)
            assertEquals(1500L, after.events.first().start.millis)
            assertEquals(4500L, after.events.first().end.millis)
            assertEquals(3, after.events.first().layer)
            assertEquals("Actor", after.events.first().name)
            assertTrue(after.events.first().comment)
            assertEquals(15, after.events.first().marginL)
            assertEquals(0, after.events.first().marginR)
            assertEquals(28, after.events.first().marginV)
            assertEquals(before.events[1], after.events[1])
            assertEquals(2L, vm.state.value.focusedEventId)
            vm.undo()
            assertEquals(before, vm.state.value.document)
            assertFalse(vm.state.value.canUndo)
            vm.redo()
            assertEquals(after, vm.state.value.document)
        }
    }

    @Test fun frozenReviewRejectsChangedDraftAndWrongSessionCannotWrite() {
        val vm = load()
        val before = vm.state.value.document
        val session = vm.state.value.workspaceSessionId
        val text = EventTextDraftStore(session)
        val time = EventTimingDraftStore(session)
        val meta = EventMetadataDraftStore(session)
        val margins = EventMarginDraftStore(session)
        val event = before.events.first()
        val entry = text.obtain("TEXT:primary", event.id, event.text)
        entry.value = TextFieldValue("First draft")
        val receipt = EventEditReviewReceipt(event.id, entry.snapshot(), time.obtain("TEXT:primary", event).snapshot(),
            meta.obtain("TEXT:primary", event).snapshot())
        composeRule.setContent {
            val state by vm.state.collectAsState()
            MaterialTheme {
                CompositionLocalProvider(LocalEventTextDraftStore provides text,
                    LocalEventStyleDraftStore provides rememberEventTextDraftStore(state.workspaceSessionId),
                    LocalEventTimingDraftStore provides time, LocalEventMetadataDraftStore provides meta,
                    LocalEventMarginDraftStore provides margins) {
                    FixedV11InspectorStatus(state.document.events.first(), state, vm, "TEXT:primary", true,
                        emptyList(), onOpenQc = {})
                }
            }
        }
        composeRule.onNodeWithTag("fixed-v11-review-current").performClick()
        composeRule.runOnIdle { entry.value = TextFieldValue("Changed after review") }
        composeRule.onNodeWithTag("r3-write-confirm").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertFalse(vm.applyEventEditReview(receipt, session + 1L))
            vm.updateEventText(1L, "External canonical")
            val external = vm.state.value.document
            assertFalse(vm.applyEventEditReview(receipt, session))
            assertEquals(external, vm.state.value.document)
            assertEquals("Changed after review", entry.value.text)
        }
    }
    @Test fun deletedProposedStyleBlocksWholeWriteAndSeparateStyleDraftSurvivesRestore() {
        val vm = load()
        val session = vm.state.value.workspaceSessionId
        val event = vm.state.value.document.events.first()
        val text = EventTextDraftStore(session)
        val styles = EventTextDraftStore(session)
        val timing = EventTimingDraftStore(session)
        val metadata = EventMetadataDraftStore(session)
        val margins = EventMarginDraftStore(session)
        text.obtain("TEXT:primary", event.id, event.text).value = TextFieldValue("Raw draft")
        val style = styles.obtain("TEXT:primary", event.id, event.style)
        style.value = TextFieldValue("Opening")
        val restored = EventTextDraftStore(session, RawEventDraftSnapshot.restore(styles.save(), session))
        assertEquals("Opening", restored.obtain("EVENT:primary", event.id, event.style).value.text)
        assertEquals("Raw draft", text.obtain("EVENT:primary", event.id, event.text).value.text)
        composeRule.setContent {
            val state by vm.state.collectAsState()
            MaterialTheme {
                CompositionLocalProvider(LocalEventTextDraftStore provides text,
                    LocalEventStyleDraftStore provides styles, LocalEventTimingDraftStore provides timing,
                    LocalEventMetadataDraftStore provides metadata,
                    LocalEventMarginDraftStore provides margins) {
                    FixedV11InspectorStatus(state.document.events.first(), state, vm, "TEXT:primary", true,
                        emptyList(), onOpenQc = {})
                }
            }
        }
        composeRule.onNodeWithTag("fixed-v11-review-current").performClick()
        composeRule.runOnIdle { vm.deleteStyle("Opening", "Default") }
        composeRule.onNodeWithTag("r3-write-confirm").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(event, vm.state.value.document.events.first())
            assertEquals("Opening", style.value.text)
            assertEquals("Raw draft", text.obtain("TEXT:primary", event.id, event.text).value.text)
        }
    }
}
