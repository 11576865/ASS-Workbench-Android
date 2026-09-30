package io.github.assworkbench.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.assworkbench.app.ui.VideoPreview
import io.github.assworkbench.domain.AssDocument
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RendererLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var application: Application
    private lateinit var probeFile: File
    private lateinit var configDir: File
    private lateinit var fontsDir: File

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        probeFile = File(application.filesDir, "startup-probe.txt")
        probeFile.delete()

        configDir = File(application.filesDir, "renderer-lifecycle-test/config").apply {
            deleteRecursively()
            mkdirs()
        }
        fontsDir = File(application.filesDir, "renderer-lifecycle-test/fonts").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @Test
    fun fontRevisionRecreatesNativeMpvCore() {
        var revision by mutableLongStateOf(0L)

        composeRule.setContent {
            VideoPreview(
                videoUri = null,
                document = AssDocument(),
                seekRequestMs = null,
                seekRequestNonce = 0L,
                onPosition = {},
                onRendererDiagnostics = {},
                configDir = configDir,
                fontsDir = fontsDir,
                fontRevision = revision,
                initialPositionMs = 0L,
                focusedEventId = null,
                positionEditEventId = null,
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
            )
        }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            successfulCoreStarts() >= 1
        }

        composeRule.runOnUiThread {
            revision += 1L
        }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            successfulCoreStarts() >= 2
        }

        assertTrue(
            "fontRevision should create a second successful native preview core",
            successfulCoreStarts() >= 2,
        )
    }

    private fun successfulCoreStarts(): Int {
        if (!probeFile.isFile) return 0
        return probeFile.readText(Charsets.UTF_8)
            .split("\n---\n")
            .count { entry ->
                entry.contains("stage=" + StartupProbe.NORMAL_PREVIEW_CORE_STAGE) &&
                    entry.contains("status=success")
            }
    }
}
