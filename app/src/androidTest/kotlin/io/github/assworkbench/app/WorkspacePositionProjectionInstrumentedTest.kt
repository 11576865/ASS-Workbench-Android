package io.github.assworkbench.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.workspace.WorkspaceBinding
import io.github.assworkbench.app.ui.workspace.WorkspaceParameterCatalog
import io.github.assworkbench.app.ui.workspace.WorkspaceParameterPresentation
import io.github.assworkbench.app.ui.workspace.WorkspaceParameterProjection
import io.github.assworkbench.app.ui.workspace.WorkspaceParameterProjectionPane
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssStyle
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspacePositionProjectionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: EditorViewModel
    private var presentation by mutableStateOf(WorkspaceParameterPresentation.NUMBER_PAIR)
    private val projection = WorkspaceParameterProjection(
        id = "parameter:event.position.xy:1",
        descriptorKey = WorkspaceParameterCatalog.positionXY.key,
        binding = WorkspaceBinding.PinnedEvent(1L),
        presentation = WorkspaceParameterPresentation.NUMBER_PAIR,
    )

    @Before
    fun mount() {
        viewModel = ViewModelProvider(composeRule.activity)[EditorViewModel::class.java]
        val document = AssDocument(
            styles = listOf(AssStyle(name = "Default", angle = 0.0)),
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(0),
                    end = SubTime(3000),
                    style = "Default",
                    text = "{\\pos(500,200)}A",
                ),
                AssEvent(id = 2L, start = SubTime(0), end = SubTime(3000), style = "Default", text = "Other"),
            ),
        )
        composeRule.runOnUiThread {
            viewModel.loadProjectSnapshot(
                AssWorkbenchProjectSnapshot(
                    title = "Parameter projection",
                    document = document,
                    videoUri = null,
                    subtitleUri = null,
                    containerUri = null,
                    containerTrackNumber = null,
                    textEncoding = AssTextEncoding.UTF8,
                    sourceFormat = SubtitleSourceFormat.ASS,
                    focusedEventId = 2L,
                    selectedEventIds = emptySet(),
                    workspaceMode = "SPATIAL_EXPERIMENTAL",
                    workspaceState = emptyList(),
                    surfaceState = emptyList(),
                )
            )
        }
        composeRule.setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                WorkspaceParameterProjectionPane(
                    projection = projection.copy(presentation = presentation),
                    state = state,
                    viewModel = viewModel,
                    onRemove = {},
                    onPresentationChange = { presentation = it },
                )
            }
        }
    }

    @Test fun pairedNumbersPreviewPinnedTargetAndCommitOneUndo() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithTag("parameter-projection-x-$suffix").performTextReplacement("700")
        composeRule.onNodeWithTag("parameter-projection-y-$suffix").performTextReplacement("350")
        composeRule.waitUntil(5_000) { viewModel.state.value.previewOwnerId == "geometry:1:${projection.id}" }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        assertEquals(2L, viewModel.state.value.focusedEventId)
        composeRule.onNodeWithTag("parameter-projection-apply-$suffix").performClick()
        composeRule.waitUntil(5_000) { viewModel.state.value.document != before }
        val position = AssGeometrySemantic.inspect(viewModel.state.value.document.events.first().text).position!!
        assertEquals(700.0, position.x, 0.001)
        assertEquals(350.0, position.y, 0.001)
        assertEquals(before.events[1], viewModel.state.value.document.events[1])
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun xyPadPreviewsThenCommitsOnceAndCancelDoesNotEdit() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithText("二维板").performClick()
        val pad = composeRule.onNodeWithTag("parameter-projection-xy-$suffix")
        pad.assertIsDisplayed().performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.3f, 0f), delayMillis = 100)
            moveTo(center + Offset(center.x * 0.6f, center.y * 0.4f), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        pad.performTouchInput { cancel() }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument == null }
        assertEquals(before, viewModel.state.value.document)
        pad.performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.3f, 0f), delayMillis = 100)
            moveTo(center + Offset(center.x * 0.6f, center.y * 0.4f), delayMillis = 100)
            up()
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.document != before }
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        assertNull(viewModel.state.value.previewDocument)
    }

    @Test fun foreignPreviewTakeoverSurvivesPadCancellation() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithText("二维板").performClick()
        val pad = composeRule.onNodeWithTag("parameter-projection-xy-$suffix")
        pad.performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.3f, 0f), delayMillis = 100)
            moveTo(center + Offset(center.x * 0.6f, center.y * 0.4f), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.runOnIdle { viewModel.previewEventPosition(1L, 100.0, 150.0) }
        composeRule.waitForIdle()
        val foreign = viewModel.state.value.previewDocument
        pad.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("geometry:1", viewModel.state.value.previewOwnerId)
        assertEquals(foreign, viewModel.state.value.previewDocument)
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }
}
