package io.github.assworkbench.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

    @Test fun filteredCatalogExposesLateGroupHitAndRealEmptyState() {
        composeRule.setContent {
            MaterialTheme {
                R3ToolCatalogDialog(onDismiss = {}, onOpenTool = {})
            }
        }
        // BATCH is in the seventh group. Empty groups must not take the
        // first four columns or push a unique match below the viewport.
        composeRule.onNodeWithTag("r3-tool-search").performTextReplacement("BATCH")
        composeRule.onNodeWithTag("r3-tool-BATCH")
            .performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("r3-tool-POSITION")
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size)

        composeRule.onNodeWithTag("r3-tool-search")
            .performTextReplacement("no-such-tool-in-the-directory")
        composeRule.onNodeWithTag("r3-tool-empty").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("r3-tool-BATCH")
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size)
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
