package io.github.assworkbench.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.assworkbench.app.ui.InteractionOverlayRegistry
import io.github.assworkbench.app.ui.InteractionProxySpec
import io.github.assworkbench.app.ui.WindowInteractionOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RodInteractionInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<EditorRegressionHostActivity>()

    @Test fun oneRodSwitchesSemanticsAndOrbitNeverCommits() {
        val registry = InteractionOverlayRegistry()
        var previews = 0
        var commits = 0
        var cancelled = 0
        var selected = ""
        composeRule.activityRule.scenario.onActivity { activity ->
            registry.publish("position-42", listOf("pos", "scale", "shear", "rotation").map { suffix ->
                InteractionProxySpec("position-42-$suffix", suffix, Offset(400f, 650f), Offset(100f, -100f),
                    onDragDelta = { previews++; selected = suffix },
                    onCommit = { commits++ }, onCancel = { cancelled++ })
            })
            activity.setContent { MaterialTheme(colorScheme = darkColorScheme()) { WindowInteractionOverlay(registry) } }
        }
        composeRule.onNodeWithTag("rod-handle-position-42-pos").assertIsDisplayed()
        composeRule.onNodeWithTag("rod-handle-position-42-scale").assertDoesNotExist()
        composeRule.onNodeWithTag("rod-orbit-only").performScrollTo().performClick()
        composeRule.onNodeWithTag("rod-handle-position-42-pos").performTouchInput {
            swipe(start = center, end = center + Offset(120f, 80f), durationMillis = 400)
        }
        composeRule.waitForIdle()
        assertEquals(0, previews)
        assertEquals(0, commits)
        composeRule.onNodeWithTag("rod-reposition").performScrollTo().performClick()
        assertEquals(0, commits)
        for ((mode, suffix) in listOf("POSITION" to "pos", "SCALE" to "scale", "SHEAR" to "shear", "ROTATION" to "rotation")) {
            composeRule.onNodeWithTag("rod-mode-$mode").performScrollTo().performClick()
            composeRule.onNodeWithTag("rod-handle-position-42-$suffix").assertIsDisplayed().performTouchInput {
                swipe(start = center, end = center + Offset(90f, 70f), durationMillis = 400)
            }
            composeRule.waitForIdle()
            assertEquals(suffix, selected)
        }
        assertTrue(previews > 4)
        assertEquals("One history transaction per released gesture", 4, commits)
        composeRule.onNodeWithTag("rod-handle-position-42-rotation").performTouchInput {
            down(center)
            moveBy(Offset(60f, 40f))
            cancel()
        }
        composeRule.waitForIdle()
        assertEquals(4, commits)
        assertEquals(1, cancelled)
    }
}
