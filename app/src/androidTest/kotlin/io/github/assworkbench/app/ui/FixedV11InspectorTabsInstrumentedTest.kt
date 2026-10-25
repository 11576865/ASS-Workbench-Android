package io.github.assworkbench.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedV11InspectorTabsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun textUsesEventTabAndDispatchesExistingStyleAndPositionCommands() {
        val commands = mutableListOf<WorkbenchTool>()
        composeRule.setContent {
            MaterialTheme { FixedV11InspectorTabs(WorkbenchTool.TEXT, true, { commands += it }) }
        }
        composeRule.onNodeWithTag("fixed-v11-inspector-EVENT").assertIsSelected()
        composeRule.onNodeWithTag("fixed-v11-inspector-STYLE").performClick()
        composeRule.onNodeWithTag("fixed-v11-inspector-POSITION").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(WorkbenchTool.STYLE, WorkbenchTool.POSITION), commands)
        }
    }

    @Test fun lockedBindingDisablesAllCrossToolCommandsWithoutHidingCurrentContext() {
        var commands = 0
        composeRule.setContent {
            MaterialTheme { FixedV11InspectorTabs(WorkbenchTool.POSITION, false, { commands++ }) }
        }
        for (tool in listOf("EVENT", "STYLE", "POSITION")) {
            composeRule.onNodeWithTag("fixed-v11-inspector-$tool").assertIsNotEnabled()
        }
        composeRule.onNodeWithTag("fixed-v11-inspector-POSITION").assertIsSelected()
        composeRule.runOnIdle { assertEquals(0, commands) }
    }
}
