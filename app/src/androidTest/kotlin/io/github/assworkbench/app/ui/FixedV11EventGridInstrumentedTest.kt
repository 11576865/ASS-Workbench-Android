package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedV11EventGridInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun wideGridExposesEventTimesStyleAndTextWithoutAddingEditorState() {
        composeRule.setContent {
            MaterialTheme {
                // Model the finite-width navigation pane; do not let the
                // fixture depend on wrap-content root measurement.
                Column(Modifier.fillMaxWidth()) {
                    FixedV11EventGridHeader()
                    FixedV11EventGridRow(
                        eventId = 39L,
                        start = "1:20.800",
                        end = "1:22.400",
                        styleName = "Opening",
                        visibleText = "世界依然值得凝视",
                        focused = true,
                        selected = false,
                        selectionMode = false,
                        issues = emptyList(),
                        onClick = {},
                        onLongClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("fixed-v11-grid-header").assertIsDisplayed()
        composeRule.onNodeWithTag("event-row-39").assertIsDisplayed()
        // The field Row is a structural semantics grouping under a clickable
        // parent. Its semantics bounds need not be an independently painted
        // surface: require the group to exist and verify every rendered cell.
        composeRule.onNodeWithTag("fixed-v11-grid-fields-39", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithText("#39", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("1:20.800").assertIsDisplayed()
        composeRule.onNodeWithText("1:22.400").assertIsDisplayed()
        composeRule.onNodeWithText("Opening").assertIsDisplayed()
        // Inspect the painted unmerged Text semantics rather than a merged
        // clickable parent. Both the user-visible body and the tagged body
        // must occupy actual on-screen bounds.
        composeRule.onNodeWithTag("fixed-v11-grid-text-39", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("世界依然值得凝视", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test fun tapAndLongPressUseProvidedExistingNavigationActions() {
        var focused = 0
        var selected = 0
        composeRule.setContent {
            MaterialTheme {
                FixedV11EventGridRow(
                    eventId = 39L,
                    start = "1:20.800",
                    end = "1:22.400",
                    styleName = "Opening",
                    visibleText = "测试行",
                    focused = false,
                    selected = false,
                    selectionMode = false,
                    issues = emptyList(),
                    onClick = { focused++ },
                    onLongClick = { selected++ },
                )
            }
        }
        composeRule.onNodeWithTag("event-row-39").performClick()
        composeRule.onNodeWithTag("event-row-39").performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(1, focused)
            assertEquals(1, selected)
        }
    }
}
