package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test-only Activity so ActivityScenario.recreate() exercises the same Compose
 * save/restore boundary as a real configuration change. The recovery journal
 * is seeded before the ViewModel is first requested on the initial creation.
 */
class EditorRegressionHostActivity : ComponentActivity() {
    val editorViewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            RecoveryStore(application).also { store ->
                store.clear()
                seedRecovery(store)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by editorViewModel.state.collectAsState()
                ModernEditorScreen(
                    state = state,
                    viewModel = editorViewModel,
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
    }
}

@RunWith(AndroidJUnit4::class)
class EditorRegressionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    private lateinit var application: Application
    private lateinit var recoveryStore: RecoveryStore
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        recoveryStore = RecoveryStore(application)
        viewModel = composeRule.activity.editorViewModel
    }

    @After
    fun tearDown() {
        recoveryStore.clear()
    }

    @Test
    fun recoveryEntryRestoresAndCollapsedRawDraftSurvives() {
        restoreRecovery()

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

        // The canonical Event is still untouched after collapse/re-open. Applying
        // the visible editor now must commit the draft that survived disposal.
        assertFalse(eventText(1L).contains("DRAFT"))
        composeRule.onNodeWithText("应用正文")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            eventText(1L) == "Recovered line DRAFT"
        }
    }

    @Test
    fun rawDraftSurvivesSwitchingBetweenEvents() {
        restoreRecovery()

        composeRule.onNodeWithTag("event-row-1")
            .performClick()
        composeRule.onNodeWithTag("event-raw-1")
            .performTextInput(" SWITCH")

        composeRule.onNodeWithTag("event-row-2")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("event-raw-2")
            .assertIsDisplayed()

        composeRule.onNodeWithTag("event-row-1")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()

        assertFalse(eventText(1L).contains("SWITCH"))
        composeRule.onNodeWithText("应用正文")
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            eventText(1L) == "Recovered line SWITCH"
        }
        assertEquals("Second recovered line", eventText(2L))
    }

    @Test
    fun rawDraftSurvivesActivityRecreation() {
        restoreRecovery()

        composeRule.onNodeWithTag("event-row-1")
            .performClick()
        composeRule.onNodeWithTag("event-raw-1")
            .performTextInput(" ROTATED")

        // Recreate the Activity rather than merely recomposing. This exercises
        // rememberSaveable + SaveableStateHolder across Android state restoration.
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("event-raw-1")
            .assertIsDisplayed()
        assertFalse(eventText(1L).contains("ROTATED"))

        composeRule.onNodeWithText("应用正文")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            eventText(1L) == "Recovered line ROTATED"
        }
    }

    @Test
    fun recoveryDiscardDeletesJournalWithoutLoadingIt() {
        composeRule.onNodeWithTag("recovery-discard")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.state.value.recoveryAvailable
        }
        assertFalse(recoveryStore.exists())
        assertFalse(viewModel.state.value.subtitleLoaded)
    }

    @Test
    fun latestEditIsRecoverableByFreshViewModel() {
        restoreRecovery()

        viewModel.updateEventText(1L, "Recovered line UPDATED")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            recoveryStore.read()
                ?.document
                ?.events
                ?.firstOrNull { it.id == 1L }
                ?.text == "Recovered line UPDATED"
        }

        // A fresh ViewModel approximates the next process launch: it must discover
        // the journal from storage rather than inheriting the live editor state.
        val fresh = EditorViewModel(application)
        assertTrue(fresh.state.value.recoveryAvailable)
        fresh.restoreRecovery()
        assertEquals(
            "Recovered line UPDATED",
            fresh.state.value.document.events.first { it.id == 1L }.text,
        )
        assertTrue(fresh.state.value.dirty)
    }

    @Test
    fun normalSaveFailureDoesNotEscapeAndKeepsDirtyRecovery() {
        restoreRecovery()

        val handled = viewModel.saveTo(
            Uri.parse("content://io.github.assworkbench.invalid-provider/unwritable.ass")
        )

        assertFalse(handled)
        assertTrue(viewModel.state.value.dirty)
        assertTrue(recoveryStore.exists())
        assertTrue(viewModel.state.value.status.startsWith("字幕保存失败："))
    }

    private fun restoreRecovery() {
        composeRule.onNodeWithTag("recovery-restore")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.subtitleLoaded &&
                viewModel.state.value.document.events.size == 2
        }
    }

    private fun eventText(id: Long): String =
        viewModel.state.value.document.events.first { it.id == id }.text
}

private fun seedRecovery(store: RecoveryStore) {
    val document = AssDocument(
        events = listOf(
            AssEvent(
                id = 1L,
                start = SubTime(1_000),
                end = SubTime(3_000),
                text = "Recovered line",
            ),
            AssEvent(
                id = 2L,
                start = SubTime(3_200),
                end = SubTime(5_000),
                text = "Second recovered line",
            ),
        )
    )
    store.write(
        project = SubtitleProject(title = "Recovery regression.ass"),
        document = document,
        textEncoding = AssTextEncoding.UTF8,
    )
}
