package io.github.assworkbench.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R3TextWriteReviewInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    private val draft = R3TextWriteReceipt(
        eventId = 39,
        eventStyle = "Opening",
        baseText = "{\\fs48}测试文本",
        canonicalText = "{\\fs48}测试文本",
        draftText = "{\\fs52}测试文本",
    )

    @Test fun explicitReviewShowsActualRawBeforeAfterAndDispatchesOnlyAfterConfirm() {
        var confirms = 0
        var cancels = 0
        composeRule.setContent {
            MaterialTheme {
                R3TextWriteReviewDialog(
                    receipt = draft,
                    currentEventText = draft.canonicalText,
                    onDismiss = { cancels++ },
                    onConfirm = { confirms++ },
                )
            }
        }
        composeRule.onNodeWithTag("r3-write-review").assertIsDisplayed()
        composeRule.onNodeWithTag("r3-write-before").assertIsDisplayed()
        composeRule.onNodeWithTag("r3-write-after").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, confirms) }
        composeRule.onNodeWithTag("r3-write-confirm").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, confirms)
            assertEquals(0, cancels)
        }
    }

    @Test fun externalChangeBlocksConfirmAndKeepsDialogCancelable() {
        var commits = 0
        composeRule.setContent {
            MaterialTheme {
                R3TextWriteReviewDialog(
                    receipt = draft,
                    currentEventText = "{\\fs32}并发修改",
                    onDismiss = {},
                    onConfirm = { commits++ },
                )
            }
        }
        composeRule.onNodeWithTag("r3-write-conflict").assertIsDisplayed()
        composeRule.onNodeWithTag("r3-write-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("r3-write-cancel").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(0, commits) }
    }
}
