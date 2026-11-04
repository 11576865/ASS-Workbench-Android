package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-level drawer tests. They prove the presentation shell, not the full
 * EditorViewModel's persisted save/revision or physical IME/rotation behavior.
 */
@RunWith(AndroidJUnit4::class)
class FixedV11CompactEventDrawerInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun compactDrawerShowsNativeEventGridAndPreservesSiblingTextDraft() {
        var focusCalls = 0
        var selectionCalls = 0
        composeRule.setContent {
            MaterialTheme {
                val drawer = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var draft by rememberSaveable { mutableStateOf("初始草稿") }

                FixedV11CompactEventDrawer(
                    drawerState = drawer,
                    total = 42,
                    selected = selectionCalls,
                    onOpen = { scope.launch { drawer.open() } },
                    onClose = { scope.launch { drawer.close() } },
                    eventList = { paneModifier ->
                        Column(paneModifier) {
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
                                onClick = {
                                    focusCalls++
                                    scope.launch { drawer.close() }
                                },
                                onLongClick = { selectionCalls++ },
                            )
                        }
                    },
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.testTag("sibling-inspector-draft"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("sibling-inspector-draft")
            .performTextReplacement("未提交且不应丢失")
        composeRule.onNodeWithTag("fixed-v11-drawer-open").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fixed-v11-event-drawer").assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-v11-grid-header").assertIsDisplayed()
        composeRule.onNodeWithTag("event-row-39").performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(1, selectionCalls) }

        composeRule.onNodeWithTag("event-row-39").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, focusCalls) }
        composeRule.onNodeWithTag("sibling-inspector-draft")
            .assertTextContains("未提交且不应丢失")
        composeRule.onNodeWithTag("fixed-v11-compact-editor").assertIsDisplayed()
    }

    @Test fun openAndCloseLeaveUnderlyingEditorMounted() {
        composeRule.setContent {
            MaterialTheme {
                val drawer = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                FixedV11CompactEventDrawer(
                    drawerState = drawer,
                    total = 2,
                    selected = 0,
                    onOpen = { scope.launch { drawer.open() } },
                    onClose = { scope.launch { drawer.close() } },
                    eventList = { Text("单一导航列表", modifier = Modifier.testTag("event-list")) },
                ) {
                    Text("保留原有检查器", modifier = Modifier.testTag("existing-inspector"))
                }
            }
        }
        composeRule.onNodeWithTag("fixed-v11-drawer-open").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("event-list").assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-v11-drawer-close").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("existing-inspector").assertIsDisplayed()
    }
}
