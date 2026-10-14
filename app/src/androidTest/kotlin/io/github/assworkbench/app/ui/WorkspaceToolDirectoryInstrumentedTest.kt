package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The existing directory is shared by both Hosts: a persisted WorkbenchTool key
 * must be selectable from an actual visible button, not only listed in an enum.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceToolDirectoryInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun everySpecializedToolHasVisibleSearchableExecutableEntry() {
        val activated = mutableListOf<WorkbenchTool>()
        composeRule.setContent {
            MaterialTheme {
                WorkspaceToolDirectory(
                    onOpenTool = { activated += it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val tools = WorkbenchTool.entries.filterNot { it == WorkbenchTool.CAPABILITIES }
        for (tool in tools) {
            composeRule.onNodeWithTag("tool-search").performTextReplacement(tool.name)
            composeRule.onNodeWithTag("tool-${tool.name}")
                .performScrollTo().assertIsDisplayed().performClick()
        }
        composeRule.runOnIdle { assertEquals(tools, activated) }
    }
}
