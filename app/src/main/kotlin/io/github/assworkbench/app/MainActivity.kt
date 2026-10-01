package io.github.assworkbench.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.assworkbench.app.ui.ModernEditorScreen

class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()

    private val openReferenceVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri, read = true, write = false)
        viewModel.openPickedVideo(uri)
    }

    private val openMkvProject = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri, read = true, write = false)
        viewModel.openMkvProject(uri)
    }

    private val openSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        if (!isSubtitleDocument(uri)) {
            viewModel.reportError("字幕导入失败", IllegalArgumentException("当前支持 .ass / .srt / .vtt 字幕文件"))
            return@registerForActivityResult
        }
        persist(uri, read = true, write = true)
        runCatching { viewModel.openSubtitle(uri) }
            .onFailure { viewModel.reportError("字幕导入失败", it) }
    }

    private val importFont = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        uris.forEach { persist(it, read = true, write = false) }
        runCatching { viewModel.importFonts(uris) }
            .onFailure { viewModel.reportError("字体导入失败", it) }
    }

    private val saveMkvAs = registerForActivityResult(ActivityResultContracts.CreateDocument("video/x-matroska")) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.saveMkvTo(uri)
    }

    private val saveSubtitleAs = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@registerForActivityResult
        persist(uri, read = true, write = true)
        runCatching { viewModel.saveTo(uri) }
            .onFailure { viewModel.reportError("字幕保存失败", it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupProbe.mark(this, "activity_onCreate", "starting")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        StartupProbe.mark(this, "activity_setContent", "starting")
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var rendererEnabled by rememberSaveable {
                    mutableStateOf(!StartupProbe.rendererCoreCrashSuspected(this@MainActivity))
                }
                var editorReady by remember {
                    mutableStateOf(!BuildConfig.ASSWB_RENDERER_EXPERIMENTAL)
                }
                var startupError by remember { mutableStateOf<String?>(null) }
                var breadcrumb by remember { mutableStateOf(StartupProbe.read(this@MainActivity)) }
                var latestBreadcrumb by remember { mutableStateOf(StartupProbe.readLatest(this@MainActivity)) }

                if (BuildConfig.ASSWB_RENDERER_EXPERIMENTAL && !editorReady) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("ASS Workbench FC 安全启动")
                        Text("此页面尚未创建 EditorViewModel，也不会加载 mpv/libass/Fontconfig。")
                        Text("最新记录（新→旧）：\n$latestBreadcrumb")
                        startupError?.let { Text("初始化错误：\n$it") }
                        Text("完整记录：\n$breadcrumb")
                        Button(
                            onClick = {
                                StartupProbe.mark(this@MainActivity, "activity_request_viewmodel", "starting")
                                runCatching { viewModel.state }
                                    .onSuccess {
                                        StartupProbe.mark(this@MainActivity, "activity_request_viewmodel", "success")
                                        breadcrumb = StartupProbe.read(this@MainActivity)
                                        latestBreadcrumb = StartupProbe.readLatest(this@MainActivity)
                                        editorReady = true
                                    }
                                    .onFailure { error ->
                                        startupError = StartupProbe.describe(error)
                                        StartupProbe.mark(
                                            this@MainActivity,
                                            "activity_request_viewmodel",
                                            "failure",
                                            startupError.orEmpty(),
                                        )
                                        breadcrumb = StartupProbe.read(this@MainActivity)
                                        latestBreadcrumb = StartupProbe.readLatest(this@MainActivity)
                                    }
                            },
                        ) {
                            Text("初始化编辑器")
                        }
                    }
                } else {
                    val state by viewModel.state.collectAsState()
                    StartupProbe.mark(this@MainActivity, "editor_compose", "success")
                    ModernEditorScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenReferenceVideo = {
                            openReferenceVideo.launch(arrayOf("video/*", "video/x-matroska", "application/octet-stream"))
                        },
                        onOpenMkvProject = { openMkvProject.launch(arrayOf("video/x-matroska", "video/*", "application/octet-stream")) },
                        onOpenSubtitle = {
                            openSubtitle.launch(arrayOf("application/x-ass", "text/x-ass", "text/x-ssa", "application/x-subrip", "text/vtt", "text/plain"))
                        },
                        onImportFont = { importFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "application/octet-stream")) },
                        onSave = {
                            if (!viewModel.saveCurrent()) saveSubtitleAs.launch(defaultFileName(state.project.title))
                        },
                        onSaveAs = { saveSubtitleAs.launch(defaultFileName(state.project.title)) },
                        onSaveMkv = {
                            saveMkvAs.launch(defaultMkvFileName(state.container.name.ifBlank { state.project.title }))
                        },
                        rendererEnabled = rendererEnabled,
                        onEnableRenderer = { rendererEnabled = true },
                    )
                }
            }
        }
        StartupProbe.mark(this, "activity_setContent", "success")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun isSubtitleDocument(uri: Uri): Boolean {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        val name = contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        } ?: uri.lastPathSegment
        return name?.let { file -> listOf(".ass", ".srt", ".vtt").any { file.endsWith(it, ignoreCase = true) } } == true
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
        val safe = title.ifBlank { "subtitle.ass" }
        val lower = safe.lowercase()
        return if (lower.endsWith(".ass") || lower.endsWith(".srt") || lower.endsWith(".vtt")) safe
        else safe.substringBeforeLast('.').ifBlank { "subtitle" } + ".ass"
    }
}
