package io.github.assworkbench.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.SubtitleNavigationHost
import io.github.assworkbench.app.ui.SubtitleResultsBar
import io.github.assworkbench.domain.SubtitleFilterState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production filter/layout components; fixture content is not renderer/visual acceptance evidence. */
@RunWith(AndroidJUnit4::class)
class SubtitleFilterInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun expandedFilterInShortPaneUsesDialogAndLeavesResultsReachable() {
        var filter by mutableStateOf(SubtitleFilterState())
        var searchOpen by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 320.dp, height = 191.dp)) {
                    SubtitleNavigationHost(filter, searchOpen, { filter = it }, { searchOpen = false }, Modifier.fillMaxSize()) {
                        SubtitleResultsBar(2, 2, 0, false, true, {}, {}, {}, {}, {})
                        Box(Modifier.weight(1f).fillMaxWidth().testTag("filter-fixture-list"))
                    }
                }
            }
        }
        composeRule.onNodeWithTag("subtitle-filter-open-dialog").performClick()
        composeRule.onNodeWithTag("subtitle-filter-expand").performScrollTo().performClick()
        composeRule.onNodeWithTag("subtitle-filter-actor").performScrollTo().performTextReplacement("Alice")
        composeRule.onNodeWithTag("subtitle-filter-dialog-done").performClick()
        composeRule.onNodeWithTag("subtitle-result-next").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("filter-fixture-list").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("Alice", filter.actor) }
    }
}
