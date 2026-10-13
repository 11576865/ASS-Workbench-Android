package io.github.assworkbench.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.BatchActionBar
import io.github.assworkbench.app.ui.BatchScopePanel
import io.github.assworkbench.domain.AssBatchScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchWorkbenchChromeInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun shortPhonePaneKeepsActionsReachableWhileScopeScrollsVertically() {
        var scope by mutableStateOf(AssBatchScope.RESULTS)
        var previews = 0
        var applied = 0
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.size(320.dp, 191.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        BatchScopePanel(scope, 12) { scope = it }
                        Spacer(Modifier.height(900.dp))
                    }
                    BatchActionBar(12, 6, null, true, true, "preview", "apply", { previews++ }, { applied++ })
                }
            }
        }
        composeRule.onNodeWithTag("batch-scope-DOCUMENT").performScrollTo().performClick()
        composeRule.onNodeWithTag("preview").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("apply").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(AssBatchScope.DOCUMENT, scope)
            assertEquals(1, previews)
            assertEquals(1, applied)
        }
    }
}
