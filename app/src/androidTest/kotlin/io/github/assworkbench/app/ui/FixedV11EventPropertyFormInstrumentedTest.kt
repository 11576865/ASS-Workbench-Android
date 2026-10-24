package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.domain.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedV11EventPropertyFormInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun sharedPropertyDraftsSurviveRemountAndRequireExplicitApply() {
        val event = AssEvent(id = 7L, start = SubTime(1000), end = SubTime(3000), text = "Raw", style = "MissingStyle")
        val timing = EventTimingDraftStore(42L).obtain("TEXT:primary", event)
        val metadata = EventMetadataDraftStore(42L).obtain("EVENT:primary", event)
        var mounted by mutableStateOf(true)
        var timeWrites = 0
        var metadataWrites = 0
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.width(326.dp).height(560.dp).verticalScroll(rememberScrollState())) {
                    if (mounted) FixedV11EventPropertyForm(event, AssDocument(events = listOf(event)), timing, metadata,
                        onApplyTiming = { start, end -> assertEquals(1000L, start.millis); assertEquals(4000L, end.millis); timeWrites++ },
                        onApplyMetadata = { layer, actor, _ -> assertEquals(3, layer); assertEquals("Actor draft", actor); metadataWrites++ })
                }
            }
        }
        composeRule.onNodeWithTag("event-end-7").performScrollTo().performTextReplacement("4000")
        composeRule.onNodeWithTag("event-layer-7").performScrollTo().performTextReplacement("3")
        composeRule.onNodeWithTag("event-actor-7").performScrollTo().performTextReplacement("Actor draft")
        composeRule.runOnIdle { assertEquals(0, timeWrites); assertEquals(0, metadataWrites); mounted = false }
        composeRule.runOnIdle { mounted = true }
        composeRule.onNodeWithTag("event-end-7").performScrollTo().assertTextContains("4000")
        composeRule.onNodeWithTag("event-actor-7").performScrollTo().assertTextContains("Actor draft")
        composeRule.onNodeWithTag("fixed-v11-style-source-7").performScrollTo().assertTextContains("引用的 Style 未找到")
        composeRule.onNodeWithTag("event-apply-time-7").performScrollTo().performClick()
        composeRule.onNodeWithTag("event-apply-metadata-7").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, timeWrites); assertEquals(1, metadataWrites); assertEquals("Raw", event.text) }
    }

    @Test fun invalidTimingAndLayerCannotApplyAndConflictKeepsDraft() {
        val original = AssEvent(id = 7L, start = SubTime(1000), end = SubTime(3000), text = "Raw")
        val timing = EventTimingDraftStore(42L).obtain("TEXT:primary", original)
        val metadata = EventMetadataDraftStore(42L).obtain("TEXT:primary", original)
        timing.values = timing.values.copy(end = "500")
        metadata.values = metadata.values.copy(layer = "invalid", actor = "kept")
        val changed = original.copy(name = "external")
        metadata.reconcile(EventMetadataValues.from(changed))
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.width(326.dp).height(560.dp).verticalScroll(rememberScrollState())) {
                    FixedV11EventPropertyForm(changed, AssDocument(events = listOf(changed)), timing, metadata,
                        onApplyTiming = { _, _ -> error("invalid timing must not commit") },
                        onApplyMetadata = { _, _, _ -> error("invalid layer must not commit") })
                }
            }
        }
        composeRule.onNodeWithTag("event-apply-time-7").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("event-apply-metadata-7").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("event-metadata-conflict-7").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("event-actor-7").performScrollTo().assertTextContains("kept")
    }
    @Test fun choosingStyleOnlyStagesReferenceUntilUnifiedReview() {
        val event = AssEvent(id = 7L, start = SubTime(1000), end = SubTime(3000), text = "{\\fs40}Raw")
        val style = EventTextDraftStore(42L).obtain("TEXT:primary", event.id, event.style)
        val timing = EventTimingDraftStore(42L).obtain("TEXT:primary", event)
        val metadata = EventMetadataDraftStore(42L).obtain("TEXT:primary", event)
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.width(326.dp).height(560.dp).verticalScroll(rememberScrollState())) {
                    FixedV11EventPropertyForm(event,
                        AssDocument(styles = listOf(AssStyle(), AssStyle(name = "Opening")), events = listOf(event)),
                        timing, metadata, { _, _ -> }, { _, _, _ -> }, styleDraft = style)
                }
            }
        }
        composeRule.onNodeWithTag("event-style-7").performScrollTo().performClick()
        composeRule.onNodeWithTag("event-style-option-7-Opening").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("Opening", style.value.text)
            assertEquals("Default", event.style)
            assertEquals("{\\fs40}Raw", event.text)
        }
        composeRule.onNodeWithTag("event-reset-style-7").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("Default", style.value.text) }
    }
}
