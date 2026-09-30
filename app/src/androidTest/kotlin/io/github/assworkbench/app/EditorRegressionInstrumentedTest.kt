package io.github.assworkbench.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            .performScrollTo()
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
            .performScrollTo()
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
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            eventText(1L) == "Recovered line ROTATED"
        }
    }

    @Test
    fun inspectorDraftSurvivesToolSwitchAndRotation() {
        restoreRecovery()
        composeRule.onNodeWithTag("event-row-1").performClick()
        composeRule.onNodeWithTag("event-raw-1").performTextInput(" WORKBENCH")
        composeRule.onNodeWithTag("tool-EFFECTS").performScrollTo().performClick()
        composeRule.onNodeWithTag("event-inspector").assertIsDisplayed()
        composeRule.onNodeWithTag("tool-TEXT").performScrollTo().performClick()
        composeRule.onNodeWithTag("event-raw-1").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.onNodeWithTag("preview-workspace").assertIsDisplayed()
        composeRule.onNodeWithTag("subtitle-navigation").assertIsDisplayed()
        composeRule.onNodeWithTag("event-inspector").assertIsDisplayed()
        captureLayout("landscape")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.executeShellCommand("wm size 1920x1200").close()
            automation.executeShellCommand("wm density 160").close()
            composeRule.activityRule.scenario.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntil(10_000) {
                val configuration = composeRule.activity.resources.configuration
                configuration.screenWidthDp >= 1600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            composeRule.waitForIdle()
            val preview = composeRule.onNodeWithTag("preview-workspace").fetchSemanticsNode().boundsInRoot
            val navigation = composeRule.onNodeWithTag("subtitle-navigation").fetchSemanticsNode().boundsInRoot
            assertTrue("Landscape preview must be beside navigation", preview.right <= navigation.left)
            val inspector = composeRule.onNodeWithTag("event-inspector").fetchSemanticsNode().boundsInRoot
            assertTrue("Expanded workbench must show navigation beside inspector", inspector.left >= navigation.right)
            captureLayout("tablet-landscape")
        } finally {
            automation.executeShellCommand("wm size reset").close()
            automation.executeShellCommand("wm density reset").close()
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.screenWidthDp < 1200
        }
        composeRule.onNodeWithText("应用正文").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { eventText(1L) == "Recovered line WORKBENCH" }
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        captureLayout("portrait")
    }

    private fun captureLayout(name: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val scaled = Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * 900f / bitmap.width).toInt(), true)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP).chunked(2800).forEachIndexed { index, chunk ->
            Log.i("AsswbVisual", "UI_CAPTURE:$name:$index:$chunk")
        }
        Log.i("AsswbVisual", "UI_CAPTURE_END:$name")
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
