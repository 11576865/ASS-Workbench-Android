package io.github.assworkbench.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.BatchAuthoringDraftStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchAuthoringDraftStoreInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun twoMountedHostsObserveSameInstanceWithoutSharingSiblingDrafts() {
        val store = BatchAuthoringDraftStore(7)
        val main = store.obtain("BATCH:primary")
        val canvas = store.obtain("BATCH:primary")
        val sibling = store.obtain("BATCH:sibling")
        composeRule.setContent {
            MaterialTheme {
                Column {
                    var mainText by main.text("rule.actor", "")
                    var canvasText by canvas.text("rule.actor", "")
                    var siblingText by sibling.text("rule.actor", "")
                    OutlinedTextField(mainText, { mainText = it }, Modifier.testTag("main"))
                    OutlinedTextField(canvasText, { canvasText = it }, Modifier.testTag("canvas"))
                    OutlinedTextField(siblingText, { siblingText = it }, Modifier.testTag("sibling"))
                }
            }
        }
        composeRule.onNodeWithTag("main").performTextReplacement("shared")
        composeRule.onNodeWithTag("canvas").assertTextContains("shared")
        composeRule.runOnIdle {
            assertSame(main, canvas)
            assertEquals("", sibling.draft.values["rule.actor"])
            store.removeInstance("BATCH:sibling")
            assertNotSame(sibling, store.obtain("BATCH:sibling"))
        }
    }
}
