package io.github.assworkbench.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R3WriteSourceDetailsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun sourcePaneIsReadOnlyAndBackDoesNotCommit() {
        var dismissed = 0
        val receipt = R3TextWriteReceipt(
            eventId = 39L,
            eventStyle = "Opening",
            baseText = "{\\fs48}测试",
            canonicalText = "{\\fs48}测试",
            draftText = "{\\fs52}测试",
        )
        val source = R3WriteSourceSummary(
            property = "字号",
            originalValue = "48",
            draftValue = "52",
            styleValue = "36",
            originLabel = "Event #39 · 局部 override",
            styleLabel = "Opening",
            changedEffectiveProperties = 1,
            spanOrTimeDependent = false,
        )
        composeRule.setContent {
            MaterialTheme {
                R3WriteSourceDetails(
                    receipt = receipt,
                    summary = source,
                    onBack = { dismissed++ },
                )
            }
        }
        composeRule.onNodeWithTag("r3-write-source-details").assertIsDisplayed()
        composeRule.onNodeWithTag("r3-source-original").assertIsDisplayed()
        composeRule.onNodeWithTag("r3-source-draft").assertIsDisplayed()
        composeRule.onNodeWithTag("r3-source-back").performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissed)
            // No onCommit exists in this read-only pane by design.
        }
    }
}
