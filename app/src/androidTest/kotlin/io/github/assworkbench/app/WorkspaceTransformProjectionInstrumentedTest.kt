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
class WorkspaceTransformProjectionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: EditorViewModel
    private var presentation by mutableStateOf(WorkspaceParameterPresentation.NUMBER_PAIR)
    private var binding: WorkspaceBinding by mutableStateOf(WorkspaceBinding.PinnedEvent(1L))
    private var descriptorKey by mutableStateOf(WorkspaceParameterCatalog.scaleXY.key)
    private val projection = WorkspaceParameterProjection(
        id = "parameter:transform:1",
        descriptorKey = WorkspaceParameterCatalog.scaleXY.key,
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
                    projection = projection.copy(descriptorKey = descriptorKey, binding = binding, presentation = presentation),
                    state = state,
                    viewModel = viewModel,
                    onRemove = {},
                    onPresentationChange = { presentation = it },
                )
            }
        }
    }

    @Test fun scaleNumbersPreviewCommitPinnedTargetAndOneUndo() {
        verifyNumbers("150", "75", true)
    }

    @Test fun shearNumbersPreserveNegativeValuesAndOneUndo() {
        composeRule.runOnIdle { descriptorKey = WorkspaceParameterCatalog.shearXY.key }
        verifyNumbers("-0.5", "0.75", false)
    }

    private fun verifyNumbers(x: String, y: String, scale: Boolean) {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithTag("parameter-projection-x-$suffix").performTextReplacement(x)
        composeRule.onNodeWithTag("parameter-projection-y-$suffix").performTextReplacement(y)
        composeRule.waitUntil(5_000) { viewModel.state.value.previewOwnerId == "geometry:1:${projection.id}" }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        composeRule.onNodeWithTag("parameter-projection-apply-$suffix").performClick()
        composeRule.waitUntil(5_000) { viewModel.state.value.document != before }
        val geometry = AssGeometrySemantic.inspect(viewModel.state.value.document.events.first().text)
        assertEquals(x.toDouble(), (if (scale) geometry.scaleX else geometry.shearX)!!, 0.001)
        assertEquals(y.toDouble(), (if (scale) geometry.scaleY else geometry.shearY)!!, 0.001)
        assertEquals(before.events[1], viewModel.state.value.document.events[1])
        assertEquals(2L, viewModel.state.value.focusedEventId)
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun shearPadCancelThenReleaseCommitsOnce() {
        composeRule.runOnIdle {
            descriptorKey = WorkspaceParameterCatalog.shearXY.key
            presentation = WorkspaceParameterPresentation.XY_PAD
        }
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        val pad = composeRule.onNodeWithTag("parameter-projection-xy-$suffix")
        pad.performTouchInput {
            down(center)
            moveTo(center + Offset(-center.x * 0.3f, center.y * 0.3f), delayMillis = 100)
            moveTo(center + Offset(-center.x * 0.5f, center.y * 0.5f), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        assertEquals(before, viewModel.state.value.document)
        pad.performTouchInput { cancel() }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument == null }
        assertFalse(viewModel.state.value.canUndo)
        pad.performTouchInput {
            down(center)
            moveTo(center + Offset(-center.x * 0.3f, center.y * 0.3f), delayMillis = 100)
            moveTo(center + Offset(-center.x * 0.5f, center.y * 0.5f), delayMillis = 100)
            up()
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.document != before }
        val geometry = AssGeometrySemantic.inspect(viewModel.state.value.document.events.first().text)
        org.junit.Assert.assertTrue(geometry.shearX!! < 0.0)
        org.junit.Assert.assertTrue(geometry.shearY!! > 0.0)
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun scaleSliderReleaseCreatesOneUndo() {
        composeRule.runOnIdle { presentation = WorkspaceParameterPresentation.SLIDER_PAIR }
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithTag("parameter-projection-slider-x-$suffix").performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.3f, 0f), delayMillis = 100)
            moveTo(center + Offset(center.x * 0.5f, 0f), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        assertEquals(before, viewModel.state.value.document)
        composeRule.onNodeWithTag("parameter-projection-slider-x-$suffix").performTouchInput { up() }
        composeRule.waitUntil(5_000) { viewModel.state.value.document != before }
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun foreignPreviewSurvivesCancellationOfTransformProjection() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithTag("parameter-projection-x-$suffix").performTextReplacement("150")
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.runOnIdle { viewModel.previewEventShear(1L, -0.25, 0.5) }
        composeRule.waitForIdle()
        val foreign = viewModel.state.value.previewDocument
        composeRule.onNodeWithTag("parameter-projection-cancel-$suffix").performClick()
        assertEquals("geometry:1", viewModel.state.value.previewOwnerId)
        assertEquals(foreign, viewModel.state.value.previewDocument)
        assertEquals(before, viewModel.state.value.document)
    }
    @Test fun focusSwitchDuringSliderDragCannotEditReplacementTarget() {
        composeRule.runOnIdle {
            binding = WorkspaceBinding.FollowFocus
            presentation = WorkspaceParameterPresentation.SLIDER_PAIR
        }
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        val slider = composeRule.onNodeWithTag("parameter-projection-slider-x-$suffix")
        slider.performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.3f, 0f), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.runOnIdle { viewModel.focusPreviousEvent() }
        composeRule.waitForIdle()
        assertEquals(1L, viewModel.state.value.focusedEventId)
        slider.performTouchInput {
            moveTo(center + Offset(center.x * 0.5f, 0f), delayMillis = 100)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        assertNull(viewModel.state.value.previewDocument)
    }

    @Test fun sliderGestureCancellationClearsPreviewWithoutUndo() {
        composeRule.runOnIdle { presentation = WorkspaceParameterPresentation.SLIDER_PAIR }
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        val slider = composeRule.onNodeWithTag("parameter-projection-slider-x-$suffix")
        slider.performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.3f, 0f), delayMillis = 100)
            moveTo(center + Offset(center.x * 0.5f, 0f), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        slider.performTouchInput { cancel() }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument == null }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

}
