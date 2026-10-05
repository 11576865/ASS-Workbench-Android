package io.github.assworkbench.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.test.performScrollTo
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
class WorkspaceParameterProjectionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: EditorViewModel
    private lateinit var snapshot: AssWorkbenchProjectSnapshot
    private var visible by mutableStateOf(true)
    private var compact by mutableStateOf(false)
    private var binding: WorkspaceBinding by mutableStateOf(WorkspaceBinding.PinnedEvent(1L))
    private var presentation by mutableStateOf(WorkspaceParameterPresentation.NUMBER)
    private val projection = WorkspaceParameterProjection(
        id = "parameter:event.rotation.z:1",
        descriptorKey = WorkspaceParameterCatalog.rotationZ.key,
        binding = WorkspaceBinding.PinnedEvent(1L),
        presentation = WorkspaceParameterPresentation.NUMBER,
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
                    text = "{\\frz10}A",
                ),
                AssEvent(id = 2L, start = SubTime(0), end = SubTime(3000), style = "Default", text = "Other"),
            ),
        )
        snapshot = AssWorkbenchProjectSnapshot(
                    title = "Parameter projection",
                    document = document,
                    videoUri = null,
                    subtitleUri = null,
                    containerUri = null,
                    containerTrackNumber = null,
                    textEncoding = AssTextEncoding.UTF8,
                    sourceFormat = SubtitleSourceFormat.ASS,
                    focusedEventId = 1L,
                    selectedEventIds = emptySet(),
                    workspaceMode = "SPATIAL_EXPERIMENTAL",
                    workspaceState = emptyList(),
                    surfaceState = emptyList(),
                )
        composeRule.runOnUiThread { viewModel.loadProjectSnapshot(snapshot) }
        composeRule.setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()
                Box(if (compact) Modifier.height(180.dp) else Modifier) {
                if (visible) WorkspaceParameterProjectionPane(
                    projection = projection.copy(binding = binding, presentation = presentation),
                    state = state,
                    viewModel = viewModel,
                    onRemove = {},
                    onPresentationChange = { presentation = it },
                )
                }
            }
        }
    }

    @Test
    fun numberProjectionPreviewsThenCommitsOneDocumentEdit() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithTag("parameter-projection-number-$suffix")
            .assertIsDisplayed()
            .performTextReplacement("45")

        composeRule.waitUntil(5_000) {
            viewModel.state.value.previewOwnerId ==
                "geometry:1:${projection.id}"
        }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)

        composeRule.onNodeWithTag("parameter-projection-apply-$suffix")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(5_000) {
            viewModel.state.value.previewDocument == null &&
                viewModel.state.value.document != before
        }
        val committed = viewModel.state.value.document
        assertEquals(
            45.0,
            AssGeometrySemantic.inspect(committed.events.first().text).rotationZ ?: Double.NaN,
            0.001,
        )

        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        assertNull(viewModel.state.value.previewDocument)
    }

    @Test
    fun dialDragPreviewsThenCommitsOneUndoAndKeepsParameterIdentity() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithText("转盘").performClick()
        val dial = composeRule.onNodeWithTag("parameter-projection-dial-$suffix")
        dial.assertIsDisplayed().performTouchInput {
            val radius = center.x * 0.7f
            down(center + Offset(radius, 0f))
            moveTo(center + Offset(radius * 0.86f, -radius * 0.5f), delayMillis = 100)
            moveTo(center + Offset(0f, -radius), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        dial.performTouchInput { up() }
        composeRule.waitUntil(5_000) { viewModel.state.value.document != before }
        assertNull(viewModel.state.value.previewDocument)
        composeRule.onNodeWithText("数值").performClick()
        composeRule.onNodeWithTag("parameter-projection-number-$suffix").assertIsDisplayed()
        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test
    fun cancelledDialDragClearsOnlyPreviewAndCreatesNoUndo() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithText("转盘").performClick()
        val dial = composeRule.onNodeWithTag("parameter-projection-dial-$suffix")
        dial.performTouchInput {
            val radius = center.x * 0.7f
            down(center + Offset(radius, 0f))
            moveTo(center + Offset(radius * 0.86f, -radius * 0.5f), delayMillis = 100)
            moveTo(center + Offset(0f, -radius), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        dial.performTouchInput { cancel() }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument == null }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun invalidNumberCancelsOwnedPreviewWithoutEditing() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        val field = composeRule.onNodeWithTag("parameter-projection-number-$suffix")
        field.performTextReplacement("45")
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        field.performTextReplacement("NaN")
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument == null }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun obsoletePaneDisposalDoesNotClearReplacementSessionPreview() {
        val owner = "geometry:1:${projection.id}"
        composeRule.runOnIdle {
            viewModel.loadProjectSnapshot(snapshot.copy(title = "Replacement session"))
            viewModel.previewEventRotationZ(1L, 99.0, ownerId = owner)
            visible = false
        }
        composeRule.waitForIdle()
        assertEquals(owner, viewModel.state.value.previewOwnerId)
        assertEquals(99.0, AssGeometrySemantic.inspect(viewModel.state.value.previewDocument!!.events.first().text).rotationZ!!, 0.001)
        assertFalse(viewModel.state.value.canUndo)
    }

    @Test fun sliderCancelDoesNotCommitOrRetainPreview() {
        composeRule.onNodeWithText("Slider").performClick()
        val before = viewModel.state.value.document
        val slider = composeRule.onNodeWithTag("parameter-projection-slider-${projection.id.replace(':', '-')}")
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

    @Test fun focusChangeWhileSliderHeldCannotEditNewEvent() {
        composeRule.runOnIdle { binding = WorkspaceBinding.FollowFocus }
        composeRule.onNodeWithText("Slider").performClick()
        val before = viewModel.state.value.document
        val slider = composeRule.onNodeWithTag("parameter-projection-slider-${projection.id.replace(':', '-')}")
        slider.performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.3f, 0f), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.runOnIdle { viewModel.focusNextEvent() }
        composeRule.waitForIdle()
        assertEquals(2L, viewModel.state.value.focusedEventId)
        slider.performTouchInput {
            moveTo(center + Offset(center.x * 0.5f, 0f), delayMillis = 100)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        assertNull(viewModel.state.value.previewDocument)
    }

    @Test fun briefForeignTakeoverInvalidatesSliderEvenAfterOwnerClears() {
        composeRule.onNodeWithText("Slider").performClick()
        val before = viewModel.state.value.document
        val slider = composeRule.onNodeWithTag("parameter-projection-slider-${projection.id.replace(':', '-')}")
        slider.performTouchInput {
            down(center)
            moveTo(center + Offset(center.x * 0.3f, 0f), delayMillis = 100)
        }
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.runOnIdle {
            viewModel.previewEventRotationZ(1L, 99.0)
            viewModel.clearTransientPreview("geometry:1")
        }
        slider.performTouchInput {
            moveTo(center + Offset(center.x * 0.5f, 0f), delayMillis = 100)
            moveTo(center + Offset(center.x * 0.6f, 0f), delayMillis = 100)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        assertNull(viewModel.state.value.previewDocument)
    }

    @Test fun compactProjectionCanReachApplyAndCancelByScrolling() {
        composeRule.runOnIdle { compact = true }
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')
        composeRule.onNodeWithTag("parameter-projection-number-$suffix")
            .performScrollTo().performTextReplacement("45")
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument != null }
        composeRule.onNodeWithTag("parameter-projection-apply-$suffix").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("parameter-projection-cancel-$suffix").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { viewModel.state.value.previewDocument == null }
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }

}
