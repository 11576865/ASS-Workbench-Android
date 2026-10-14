package io.github.assworkbench.app.ui.workspace

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Visible spatial entries must dispatch to their existing production actions. */
@RunWith(AndroidJUnit4::class)
class SpatialCapabilityMenuInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun availableCapabilitiesHaveAVisibleEntryAndDispatchOnce() {
        var directoryCalls = 0
        var listCalls = 0
        var overviewCalls = 0
        var timelineCalls = 0
        var timelineVisible by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                SpatialCapabilityMenu(
                    enabled = true,
                    timelineVisible = timelineVisible,
                    onOpenDirectory = { directoryCalls++ },
                    onOpenToolList = { listCalls++ },
                    onOpenOverview = { overviewCalls++ },
                    onToggleTimeline = { timelineCalls++; timelineVisible = !timelineVisible },
                )
            }
        }

        fun invoke(tag: String) {
            composeRule.onNodeWithTag("spatial-capability-menu").assertIsDisplayed().performClick()
            composeRule.onNodeWithTag(tag).assertIsDisplayed().performClick()
        }

        invoke("spatial-capability-directory")
        invoke("spatial-capability-tool-list")
        invoke("spatial-capability-overview")
        invoke("spatial-capability-timeline")

        composeRule.runOnIdle {
            assertEquals(1, directoryCalls)
            assertEquals(1, listCalls)
            assertEquals(1, overviewCalls)
            assertEquals(1, timelineCalls)
        }
        composeRule.onNodeWithTag("spatial-capability-menu").performClick()
        composeRule.onNodeWithText("收起常驻时间轴").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-capability-precision").assertDoesNotExist()
    }

    @Test fun precisionOnlyAppearsForSupportedFocusedPositionTool() {
        var precision by mutableStateOf(false)
        var changes = 0
        composeRule.setContent {
            MaterialTheme {
                SpatialCapabilityMenu(
                    enabled = true,
                    timelineVisible = false,
                    onOpenDirectory = {},
                    onOpenToolList = {},
                    onOpenOverview = {},
                    onToggleTimeline = {},
                    onTogglePrecision = { changes++; precision = !precision },
                    precisionEnabled = precision,
                )
            }
        }
        composeRule.onNodeWithTag("spatial-capability-menu").performClick()
        composeRule.onNodeWithText("开启精密操控").assertIsDisplayed()
        composeRule.onNodeWithTag("spatial-capability-precision").performClick()
        composeRule.onNodeWithTag("spatial-capability-menu").performClick()
        composeRule.onNodeWithText("关闭精密操控").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, changes) }
    }

    @Test fun ownedGestureBlocksWorkspaceActions() {
        composeRule.setContent {
            MaterialTheme {
                SpatialCapabilityMenu(
                    enabled = false,
                    timelineVisible = false,
                    onOpenDirectory = { error("Disabled directory") },
                    onOpenToolList = { error("Disabled list") },
                    onOpenOverview = { error("Disabled overview") },
                    onToggleTimeline = { error("Disabled timeline") },
                )
            }
        }
        composeRule.onNodeWithTag("spatial-capability-menu").assertIsNotEnabled()
        composeRule.onNodeWithTag("spatial-capability-directory").assertDoesNotExist()
    }
}
