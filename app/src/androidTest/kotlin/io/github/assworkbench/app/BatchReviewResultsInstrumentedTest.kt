package io.github.assworkbench.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.BatchReviewResults
import io.github.assworkbench.domain.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real preview controls at phone width; not a full renderer/visual fixture. */
@RunWith(AndroidJUnit4::class)
class BatchReviewResultsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun allChangedEventsAreReachableAndLocatingDoesNotCommit() {
        val document = AssDocument(events = (1L..12L).map {
            AssEvent(it, start = SubTime(0), end = SubTime(1000), text = "{\\bord2}text")
        })
        val review = AssBatchReview.create(document, AssBatchRecipe("review", actions = listOf(AssBatchAction.SetActor("Actor"))))
        var located: Long? = null
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.size(320.dp, 400.dp).verticalScroll(rememberScrollState())) {
                    BatchReviewResults(review, "review") { located = it }
                }
            }
        }
        composeRule.onNodeWithTag("review-locate-1").performScrollTo().performClick()
        composeRule.onNodeWithTag("review-next").performScrollTo().performClick()
        composeRule.onNodeWithTag("review-locate-6").performScrollTo().performClick()
        composeRule.onNodeWithTag("review-next").performScrollTo().performClick()
        composeRule.onNodeWithTag("review-locate-12").performScrollTo().performClick()
        composeRule.onNodeWithTag("review-next").performScrollTo().assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(12L, located)
            assertEquals("", document.events.last().name)
        }
    }
}
