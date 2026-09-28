package io.github.assworkbench.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.assworkbench.app.ui.EditorScreen

class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()

    private val openVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri, read = true, write = false)
        viewModel.attachVideo(uri)
    }

    private val openMkvProject = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri, read = true, write = false)
        viewModel.openMkvProject(uri)
    }

    private val openSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri, read = true, write = true)
        runCatching { viewModel.openSubtitle(uri) }
            .onFailure { viewModel.reportError("字幕导入失败", it) }
    }

    private val importFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri, read = true, write = false)
        runCatching { viewModel.importFont(uri) }
            .onFailure { viewModel.reportError("字体导入失败", it) }
    }

    private val saveMkvAs = registerForActivityResult(ActivityResultContracts.CreateDocument("video/x-matroska")) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.saveMkvTo(uri)
    }

    private val saveSubtitleAs = registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-ssa")) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri, read = true, write = true)
        runCatching { viewModel.saveTo(uri) }
            .onFailure { viewModel.reportError("字幕保存失败", it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsState()
            MaterialTheme(colorScheme = darkColorScheme()) {
                EditorScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenVideo = { openVideo.launch(arrayOf("video/*")) },
                    onOpenMkvProject = { openMkvProject.launch(arrayOf("video/x-matroska", "video/*", "application/octet-stream")) },
                    onOpenSubtitle = { openSubtitle.launch(arrayOf("text/*", "application/x-ass", "application/x-ssa")) },
                    onImportFont = { importFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream")) },
                    onSave = {
                        if (!viewModel.saveCurrent()) saveSubtitleAs.launch(defaultFileName(state.project.title))
                    },
                    onSaveAs = { saveSubtitleAs.launch(defaultFileName(state.project.title)) },
                    onSaveMkv = {
                        saveMkvAs.launch(defaultMkvFileName(state.container.name.ifBlank { state.project.title }))
                    },
                )
            }
        }
    }

    private fun persist(uri: Uri, read: Boolean, write: Boolean) {
        var flags = 0
        if (read) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (write) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
    }

    private fun defaultMkvFileName(title: String): String {
        val stem = title.substringBeforeLast('.').ifBlank { "updated" }
        return stem + "-edited.mkv"
    }

    private fun defaultFileName(title: String): String {
        val stem = title.substringBeforeLast('.').ifBlank { "subtitle" }
        return "$stem.ass"
    }
}
