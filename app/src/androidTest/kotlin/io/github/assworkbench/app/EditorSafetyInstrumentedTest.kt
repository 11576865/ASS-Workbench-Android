package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.ModernEditorScreen
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.SubtitleProject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorSafetyInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var recoveryStore: RecoveryStore

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        recoveryStore = RecoveryStore(app)
        recoveryStore.clear()
    }

    @After
    fun tearDown() {
        recoveryStore.clear()
    }

    @Test
    fun recoveryPromptRestoresJournalAndKeepsItUntilExplicitDiscard() {
        recoveryStore.write(
            SubtitleProject(title = "Recovered.ass"),
            sampleDocument("recovered text"),
            AssTextEncoding.UTF8,
        )
        val viewModel = EditorViewModel(app)

        composeRule.setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                editorScreen(state, viewModel)
            }
        }

        composeRule.onNodeWithText("发现未保存编辑").assertExists()
        composeRule.onNodeWithText("恢复").performClick()
        composeRule.waitUntil(5_000) {
            viewModel.state.value.document.events.firstOrNull()?.text == "recovered text"
        }

        assertTrue("Restore must keep the crash journal until save/discard", recoveryStore.exists())
        composeRule.runOnUiThread { viewModel.discardRecovery() }
        composeRule.waitForIdle()
        assertFalse(recoveryStore.exists())
    }

    @Test
    fun collapsedAndSwitchedEventKeepsUnappliedRawDraft() {
        recoveryStore.write(
            SubtitleProject(title = "Drafts.ass"),
            AssDocument(
                events = listOf(
                    AssEvent(
                        id = 1L,
                        start = SubTime(0),
                        end = SubTime(2_000),
                        text = "original one",
                    ),
                    AssEvent(
                        id = 2L,
                        start = SubTime(2_500),
                        end = SubTime(4_500),
                        text = "original two",
                    ),
                ),
            ),
            AssTextEncoding.UTF8,
        )
        val viewModel = EditorViewModel(app)
        viewModel.restoreRecovery()

        composeRule.setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                editorScreen(state, viewModel)
            }
        }

        composeRule.onNodeWithText("#1").performClick()
        composeRule.onNode(hasSetTextAction() and hasText("original one"))
            .performTextReplacement("draft survives")

        composeRule.onNodeWithContentDescription("收起").performClick()
        composeRule.onNodeWithText("#2").performClick()
        composeRule.onNodeWithText("#1").performClick()

        composeRule.onNode(hasSetTextAction() and hasText("draft survives"))
            .assertTextEquals("draft survives")
        assertTrue(
            "Draft must remain outside the canonical document until Apply",
            viewModel.state.value.document.events.first { it.id == 1L }.text == "original one",
        )
    }

    @Test
    fun ordinarySaveFailureIsContainedAndRecoveryRemainsAvailable() {
        recoveryStore.write(
            SubtitleProject(title = "Unsaved.ass"),
            sampleDocument("must survive"),
            AssTextEncoding.UTF8,
        )
        val viewModel = EditorViewModel(app)
        viewModel.restoreRecovery()

        val saved = viewModel.saveTo(
            Uri.parse("content://io.github.assworkbench.nonexistent-provider/blocked.ass")
        )

        assertFalse(saved)
        assertTrue(viewModel.state.value.dirty)
        assertTrue(recoveryStore.exists())
        assertTrue(viewModel.state.value.status.startsWith("字幕保存失败："))
    }

    private fun sampleDocument(text: String) = AssDocument(
        events = listOf(
            AssEvent(
                id = 1L,
                start = SubTime(0),
                end = SubTime(2_000),
                text = text,
            ),
        ),
    )

    @androidx.compose.runtime.Composable
    private fun editorScreen(state: EditorState, viewModel: EditorViewModel) {
        ModernEditorScreen(
            state = state,
            viewModel = viewModel,
            onOpenReferenceVideo = {},
            onOpenMkvProject = {},
            onOpenSubtitle = {},
            onImportFont = {},
            onSave = {},
            onSaveAs = {},
            onSaveMkv = {},
            rendererEnabled = false,
            onEnableRenderer = {},
        )
    }
}
