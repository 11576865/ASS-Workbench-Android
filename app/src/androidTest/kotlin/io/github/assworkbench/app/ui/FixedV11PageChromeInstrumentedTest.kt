package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedV11PageChromeInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun permanentRailKeepsExistingToolCommandsAndDirectoryReachable() {
        val commands = mutableListOf<WorkbenchTool>()
        var directories = 0
        composeRule.setContent {
            MaterialTheme {
                FixedV11TaskRail(WorkbenchTool.STYLE, false, { commands += it }, { directories++ },
                    Modifier.height(560.dp))
            }
        }
        composeRule.onNodeWithTag("fixed-tool-STYLE").assertIsSelected()
        for(tool in listOf(WorkbenchTool.TEXT, WorkbenchTool.TIMELINE, WorkbenchTool.POSITION, WorkbenchTool.FONTS)) {
            composeRule.onNodeWithTag("fixed-tool-${tool.name}").performScrollTo().performClick()
        }
        // Complete catalog is pinned, not a member of the scrollable shortcut list.
        composeRule.onNodeWithTag("fixed-tool-CAPABILITIES").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(WorkbenchTool.TEXT, WorkbenchTool.TIMELINE, WorkbenchTool.POSITION, WorkbenchTool.FONTS), commands)
            assertEquals(1, directories)
        }
    }
    @Test fun tabCommandsRetainPreviewHostAndDrawerActionsInOneRow() {
        var previews = 0
        var spatial = 0
        var drawer = 0
        composeRule.setContent {
            MaterialTheme {
                FixedV11TabCommands(WorkbenchTool.TEXT, PreviewDensity.FULL, {},
                    { previews++ }, { spatial++ }, onDrawer = { drawer++ }, eventCount = 2)
            }
        }
        composeRule.onNodeWithTag("fixed-preview-density").performClick()
        composeRule.onNodeWithTag("fixed-r3-spatial-switch").performClick()
        composeRule.onNodeWithTag("fixed-v11-drawer-open").performClick()
        composeRule.runOnIdle {
            assertEquals(1, previews)
            assertEquals(1, spatial)
            assertEquals(1, drawer)
        }
    }
}
