package io.github.assworkbench.app

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.assworkbench.app.ui.VideoPreview
import io.github.assworkbench.app.ui.interaction.WindowInteractionOverlay
import io.github.assworkbench.app.ui.interaction.rememberInteractionOverlayRegistry
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssStyle
import io.github.assworkbench.domain.SubTime
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime-backed visual evidence for the real mpv/libass preview plus the shared
 * interaction overlay. The generated video is only a deterministic media input;
 * rendering is performed by the production native preview stack.
 */
@RunWith(AndroidJUnit4::class)
class UigsRendererVisualCaptureInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var application: Application
    private lateinit var video: File
    private lateinit var configDir: File
    private lateinit var fontsDir: File
    private val diagnostics = AtomicReference<List<String>>(emptyList())

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        video = File(application.filesDir, "uigs-runtime-fixture.mp4").apply {
            writeBytes(Base64.decode(VIDEO_BASE64, Base64.DEFAULT))
        }
        configDir = File(application.filesDir, "uigs-runtime-renderer/config").apply {
            deleteRecursively()
            mkdirs()
        }
        fontsDir = File(application.filesDir, "uigs-runtime-renderer/fonts").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun captureRendererBackedPreviewAndInteractionOverlayLandscape() {
        val document = AssDocument(
            scriptInfo = linkedMapOf(
                "ScriptType" to "v4.00+",
                "PlayResX" to "1920",
                "PlayResY" to "1080",
                "ScaledBorderAndShadow" to "yes",
            ),
            styles = listOf(AssStyle(fontName = "sans-serif", fontSize = 72.0, outline = 4.0, shadow = 0.0)),
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(0),
                    end = SubTime(30_000),
                    text = "{\\pos(960,540)}UIGS runtime renderer",
                )
            ),
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val registry = rememberInteractionOverlayRegistry()
                Box(Modifier.fillMaxSize()) {
                    VideoPreview(
                        videoUri = video.absolutePath,
                        document = document,
                        renderDocument = document,
                        seekRequestMs = null,
                        seekRequestNonce = 0L,
                        onPosition = {},
                        onRendererDiagnostics = { diagnostics.set(it) },
                        configDir = configDir,
                        fontsDir = fontsDir,
                        fontRevision = 0L,
                        initialPositionMs = 1_000L,
                        focusedEventId = 1L,
                        positionEditEventId = 1L,
                        onPreviewEventPosition = { _, _ -> },
                        onSetEventPosition = { _, _ -> },
                        onPreviewEventMove = { _, _, _, _ -> },
                        onSetEventMove = { _, _, _, _ -> },
                        onPreviewEventOrigin = { _, _ -> },
                        onSetEventOrigin = { _, _ -> },
                        onPreviewEventRotation = {},
                        onSetEventRotation = {},
                        scaleLocked = true,
                        onPreviewEventScale = { _, _ -> },
                        onSetEventScale = { _, _ -> },
                        onPreviewEventShear = { _, _ -> },
                        onSetEventShear = { _, _ -> },
                        onPreviewEventClip = { _, _, _, _, _ -> },
                        onSetEventClip = { _, _, _, _, _ -> },
                        onCancelEventPositionPreview = {},
                        onFocusEvent = {},
                        onSetEventTiming = { _, _, _ -> },
                        onOpenVideo = {},
                        rendererEnabled = true,
                        interactionRegistry = registry,
                        modifier = Modifier.fillMaxSize(),
                    )
                    WindowInteractionOverlay(registry = registry)
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 30_000) {
            diagnostics.get().any { it.contains("Preview subtitle") }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("rod-handle-position-1-pos", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        composeRule.waitForIdle()
        Thread.sleep(800)
        captureDisplay("ASS.RENDERER_POSITION.RUNTIME_LANDSCAPE.png")
    }

    private fun captureDisplay(fileName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val target = instrumentation.targetContext.filesDir.resolve(fileName)
        FileOutputStream(target).use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        assertTrue(target.isFile && target.length() > 0L)
    }

    private companion object {
        const val VIDEO_BASE64 = "$videoBase64"
    }
}
