package io.github.assworkbench.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
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
                )
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
                    focusedEventId = 1L,
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
                var presentation by remember {
                    mutableStateOf(WorkspaceParameterPresentation.NUMBER)
                }
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
            AssGeometrySemantic.inspect(committed.events.single().text).rotationZ ?: Double.NaN,
            0.001,
        )

        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
        assertNull(viewModel.state.value.previewDocument)
    }

    @Test
    fun angleDialUsesSamePreviewCommitUndoBoundary() {
        val before = viewModel.state.value.document
        val suffix = projection.id.replace(':', '-')

        composeRule.onNodeWithText("角度盘").performClick()
        composeRule.onNodeWithTag("parameter-projection-dial-$suffix")
            .assertIsDisplayed()
            .performTouchInput {
                down(center)
                moveBy(Offset(80f, 0f))
                up()
            }

        composeRule.waitUntil(5_000) {
            viewModel.state.value.previewDocument == null &&
                viewModel.state.value.document != before
        }
        val angle = AssGeometrySemantic.inspect(
            viewModel.state.value.document.events.single().text
        ).rotationZ ?: Double.NaN
        assertEquals(90.0, angle, 3.0)

        composeRule.runOnIdle { viewModel.undo() }
        composeRule.waitForIdle()
        assertEquals(before, viewModel.state.value.document)
        assertFalse(viewModel.state.value.canUndo)
    }
}
