package io.github.assworkbench.app.ui

import androidx.compose.material3.MaterialTheme
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

@RunWith(AndroidJUnit4::class)
class R3ToolCatalogInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun groupedCatalogSearchExecutesExistingPositionToolRoute() {
        val selected = mutableListOf<WorkbenchTool>()
        composeRule.setContent {
            MaterialTheme {
                R3ToolCatalogDialog(
                    onDismiss = {},
                    onOpenTool = { selected += it },
                )
            }
        }
        composeRule.onNodeWithTag("r3-tool-catalog").assertIsDisplayed()
        composeRule.onNodeWithTag("r3-tool-search")
            .performTextReplacement("POSITION")
        composeRule.onNodeWithTag("r3-tool-POSITION")
            .performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(WorkbenchTool.POSITION), selected)
        }
    }

    @Test fun directoryIsCurrentWorkspaceNotRecursiveNewEditor() {
        composeRule.setContent {
            MaterialTheme {
                R3ToolCatalogDialog(onDismiss = {}, onOpenTool = {
                    error("A directory must not re-open itself")
                })
            }
        }
        composeRule.onNodeWithTag("r3-tool-search").performTextReplacement("CAPABILITIES")
        composeRule.onNodeWithTag("r3-tool-CAPABILITIES")
            .performScrollTo().assertIsDisplayed()
    }
}
