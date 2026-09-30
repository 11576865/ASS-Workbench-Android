package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class EditorRegressionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var application: Application
    private lateinit var recoveryStore: RecoveryStore
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        recoveryStore = RecoveryStore(application)
        recoveryStore.clear()
        seedRecovery()
        viewModel = EditorViewModel(application)

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
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

    @After
    fun tearDown() {
        recoveryStore.clear()
    }

    @Test
    fun recoveryEntryRestoresAndCollapsedRawDraftSurvives() {
        composeRule.onNodeWithTag("recovery-restore")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.subtitleLoaded &&
                viewModel.state.value.document.events.size == 1
        }

        // Restoring must not consume the journal. A second process death before
        // explicit save/discard should still have something to recover.
        assertTrue(recoveryStore.exists())

        composeRule.onNodeWithTag("event-row-1")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()
            .performTextInput(" DRAFT")

        composeRule.onNodeWithTag("event-collapse-1")
            .performClick()

        composeRule.onNodeWithTag("event-row-1")
            .performClick()

        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()
            .assertTextContains("DRAFT")

        // The draft is still local UI state because "应用正文" was never pressed.
        assertFalse(viewModel.state.value.document.events.single().text.contains("DRAFT"))
    }

    @Test
    fun normalSaveFailureDoesNotEscapeAndKeepsDirtyRecovery() {
        composeRule.onNodeWithTag("recovery-restore")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.dirty
        }

        val handled = viewModel.saveTo(
            Uri.parse("content://io.github.assworkbench.invalid-provider/unwritable.ass")
        )

        assertFalse(handled)
        assertTrue(viewModel.state.value.dirty)
        assertTrue(recoveryStore.exists())
        assertTrue(viewModel.state.value.status.startsWith("字幕保存失败："))
    }

    private fun seedRecovery() {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(1_000),
                    end = SubTime(3_000),
                    text = "Recovered line",
                )
            )
        )
        recoveryStore.write(
            project = SubtitleProject(title = "Recovery regression.ass"),
            document = document,
            textEncoding = AssTextEncoding.UTF8,
        )
    }
}
