package io.github.assworkbench.app.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.RecoveryStore
import io.github.assworkbench.domain.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedV11TextDockInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    @After fun clearRecovery() { RecoveryStore(application).clear() }

    @Test fun dirtyRawSurvivesDockDisposalAndInspectorRemountWithoutCanonicalWrite() {
        val original = "{\\fs40}原始字幕"
        val document = AssDocument(events = listOf(
            AssEvent(id = 1L, start = SubTime(1000), end = SubTime(3000), text = original)))
        RecoveryStore(application).write(SubtitleProject(title = "Dock regression.ass"),
            document, AssTextEncoding.UTF8)
        val viewModel = EditorViewModel(application)
        viewModel.restoreRecovery()
        composeRule.waitUntil(10_000) { viewModel.state.value.subtitleLoaded }
        val session = viewModel.state.value.workspaceSessionId
        val drafts = EventTextDraftStore(session)
        var docked by mutableStateOf(true)
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            MaterialTheme {
                CompositionLocalProvider(LocalEventTextDraftStore provides drafts) {
                    Column(Modifier.fillMaxWidth()) {
                        val event = state.document.events.first { it.id == 1L }
                        if (docked) FixedV11TextDock(event, state, viewModel, "TEXT:primary",
                            Modifier.fillMaxWidth().height(240.dp))
                        else EventRawTextEditor(event, state, viewModel, "TEXT:primary")
                    }
                }
            }
        }
        val dirty = "{\\fs40}未提交的中央草稿"
        composeRule.onNodeWithTag("event-raw-1").performTextReplacement(dirty)
        composeRule.runOnIdle {
            assertEquals(original, viewModel.state.value.document.events.first().text)
            docked = false
        }
        composeRule.onNodeWithTag("event-raw-1").assertTextContains(dirty)
        composeRule.runOnIdle { docked = true }
        composeRule.onNodeWithTag("event-raw-1").assertTextContains(dirty)
        composeRule.runOnIdle {
            assertEquals(original, viewModel.state.value.document.events.first().text)
            assertEquals(session, viewModel.state.value.workspaceSessionId)
            assertEquals(dirty, drafts.obtain("TEXT:primary", 1L, original).value.text)
        }
    }
}
