package io.github.assworkbench.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.assworkbench.app.ui.ModernEditorScreen
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.SubtitleProject

/**
 * Debug-only host for connected Android UI regression tests.
 *
 * This Activity must live in the target app APK (not the instrumentation APK),
 * otherwise ActivityScenario sees the test package/process and refuses to launch it.
 */
class EditorRegressionHostActivity : ComponentActivity() {
    val editorViewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            RecoveryStore(application).also { store ->
                store.clear()
                seedRecovery(store)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by editorViewModel.state.collectAsState()
                ModernEditorScreen(
                    state = state,
                    viewModel = editorViewModel,
                    onOpenReferenceVideo = {},
                    onOpenMkvProject = {},
                    onOpenSubtitle = {},
                    onOpenProject = {},
                    onImportFont = {},
                    onSave = {},
                    onSaveAs = {},
                    onSaveMkv = {},
                    onSaveProject = { _, _ -> },
                    rendererEnabled = false,
                    onEnableRenderer = {},
                )
            }
        }
    }

    private fun seedRecovery(store: RecoveryStore) {
        val document = AssDocument(
            events = listOf(
                AssEvent(
                    id = 1L,
                    start = SubTime(1_000),
                    end = SubTime(3_000),
                    text = "Recovered line",
                ),
                AssEvent(
                    id = 2L,
                    start = SubTime(3_200),
                    end = SubTime(5_000),
                    text = "Second recovered line",
                ),
            )
        )
        store.write(
            project = SubtitleProject(title = "Recovery regression.ass"),
            document = document,
            textEncoding = AssTextEncoding.UTF8,
        )
    }
}
