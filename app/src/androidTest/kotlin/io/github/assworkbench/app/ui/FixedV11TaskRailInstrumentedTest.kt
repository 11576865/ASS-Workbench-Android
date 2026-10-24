package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixedV11TaskRailInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun directoryRemainsReachableInShortTabletRailAndTasksStillRoute() {
        val directoryClicks = mutableIntStateOf(0)
        val subtitleClicks = mutableIntStateOf(0)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(56.dp).height(340.dp)) {
                    FixedV11TaskRail(
                        activeTool = WorkbenchTool.TEXT,
                        listRequested = false,
                        onTool = { if (it == WorkbenchTool.SUBTITLES) subtitleClicks.intValue++ },
                        onDirectory = { directoryClicks.intValue++ },
                    )
                }
            }
        }
        // A shorter available height must never move the complete directory
        // beyond the visible viewport, even though ordinary tools may scroll.
        composeRule.onNodeWithTag("fixed-v11-task-rail").assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-v11-rail-scroll").assertIsDisplayed()
        composeRule.onNodeWithTag("fixed-tool-CAPABILITIES").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, directoryClicks.intValue) }
        composeRule.onNodeWithTag("fixed-tool-SUBTITLES").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, subtitleClicks.intValue) }
    }
}
