package io.github.assworkbench.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.workspace.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceParameterExtractionInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private var workspace by mutableStateOf(WorkspaceState())
    private var gestureKey by mutableStateOf("session:1:event:1")

    private fun mount() {
        composeRule.setContent {
            MaterialTheme { ExtractionFixture(workspace, gestureKey) { workspace = it } }
        }
    }

    @Test fun dragUsesLatestWorkspaceAndRetainsEarlierProjection() {
        mount()
        val control = composeRule.onNodeWithTag("extraction")
        control.performClick()
        composeRule.waitForIdle()
        val firstId = workspace.parameterProjections.single().id
        control.performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(72f, 24f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(2, workspace.parameterProjections.size)
        assertEquals(firstId, workspace.parameterProjections.first().id)
    }

    @Test fun cancelledDragDoesNotExtract() {
        mount()
        composeRule.onNodeWithTag("extraction").performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(72f, 24f))
            cancel()
        }
        composeRule.waitForIdle()
        assertEquals(0, workspace.parameterProjections.size)
    }

    @Test fun changedSessionOrTargetCancelsHeldDrag() {
        mount()
        val control = composeRule.onNodeWithTag("extraction")
        control.performTouchInput {
            down(center)
            advanceEventTime(800)
            moveBy(Offset(72f, 24f))
        }
        composeRule.runOnIdle { gestureKey = "session:2:event:2" }
        control.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(0, workspace.parameterProjections.size)
    }
}

@Composable
private fun ExtractionFixture(workspace: WorkspaceState, gestureKey: String, onChange: (WorkspaceState) -> Unit) {
    // Immutable parameter captures match the real SpatialWorkspace host.
    WorkspaceParameterExtractionControl("缩放 X/Y", gestureKey, onExtract = {
        onChange(workspace.addParameterProjection(WorkspaceParameterCatalog.scaleXY.key,
            WorkspaceParameterPresentation.SLIDER_PAIR, WorkspaceBinding.FollowFocus))
    }, modifier = Modifier.testTag("extraction"))
}
