package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedV11WorkspaceTabsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun allFourScenesAreReachableAndExposeOneSelectedTabWithRealContext() {
        val selected = mutableListOf<FixedV11WorkspaceMode>()
        composeRule.setContent {
            MaterialTheme {
                var mode by rememberSaveable { mutableStateOf(FixedV11WorkspaceMode.SUBTITLE_EDIT) }
                FixedV11WorkspaceTabs(
                    selected = mode,
                    onSelect = { selected += it; mode = it },
                    contextDescription = "POSITION · Event #39 · 固定绑定",
                )
            }
        }
        composeRule.onNodeWithTag("fixed-v11-workspace-context")
            .assertTextEquals("POSITION · Event #39 · 固定绑定")
        for (mode in FixedV11WorkspaceMode.entries) {
            composeRule.onNodeWithTag("fixed-v11-tab-${mode.tag}")
                .performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            FixedV11WorkspaceMode.entries.filter { it != mode }.forEach { other ->
                composeRule.onNodeWithTag("fixed-v11-tab-${other.tag}").assertIsNotSelected()
            }
        }
        composeRule.runOnIdle { assertEquals(FixedV11WorkspaceMode.entries.toList(), selected) }
    }

    @Test fun switchingTimingAndBackDoesNotCallToolOrDiscardSiblingDraft() {
        val modes = mutableListOf<FixedV11WorkspaceMode>()
        composeRule.setContent {
            MaterialTheme {
                var mode by rememberSaveable {
                    mutableStateOf(FixedV11WorkspaceMode.SUBTITLE_EDIT)
                }
                var draft by rememberSaveable { mutableStateOf("初始草稿") }
                Column {
                    FixedV11WorkspaceTabs(
                        selected = mode,
                        onSelect = { next ->
                            modes += next
                            mode = next
                        },
                    )
                    // Represents a *sibling* in the same composition, not a new
                    // screen or ViewModel. Real editor lifecycle has separate tests.
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.testTag("existing-inspector-draft"),
                    )
                    Text("Focus #39 / unchanged", modifier = Modifier.testTag("existing-focus"))
                }
            }
        }
        composeRule.onNodeWithTag("existing-inspector-draft")
            .performTextReplacement("未提交的正文草稿")
        composeRule.onNodeWithTag("fixed-v11-tab-timing").performClick()
        composeRule.onNodeWithTag("existing-inspector-draft")
            .assertTextContains("未提交的正文草稿")
        composeRule.onNodeWithTag("existing-focus").assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-v11-tab-subtitle").performClick()
        composeRule.onNodeWithTag("existing-inspector-draft")
            .assertTextContains("未提交的正文草稿")
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    FixedV11WorkspaceMode.PRECISE_TIMING,
                    FixedV11WorkspaceMode.SUBTITLE_EDIT,
                ),
                modes,
            )
        }
    }

    @Test fun readOnlyTimingContextDoesNotInventMissingEventTarget() {
        composeRule.setContent {
            MaterialTheme {
                FixedV11TimingContext(
                    event = null,
                    bindingDescription = "固定 #38（目标缺失）",
                    mediaPositionMs = 80_800L,
                )
            }
        }
        composeRule.onNodeWithTag("fixed-v11-timing-context").assertIsDisplayed()
        composeRule.onNodeWithText("未找到可读取的事件；此面板不改变写入对象")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-v11-timing-pts")
            // Compose assertTextContains defaults to exact text matching unless
            // substring=true; verify the complete visible read-only PTS label.
            .assertTextEquals("媒体 PTS 80800 ms · 时间轴与视频共享时钟")
    }
}
