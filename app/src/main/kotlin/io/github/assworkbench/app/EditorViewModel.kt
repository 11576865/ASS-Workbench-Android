package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.UndoHistory
import io.github.assworkbench.fonts.FontDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    private val history = UndoHistory(AssDocument(), limit = 80)
    private val fontStore = FontStore(application)
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        refreshFonts(initial = true)
    }

    fun attachVideo(uri: Uri) {
        _state.update {
            it.copy(
                project = it.project.copy(videoUri = uri.toString()),
                status = "已更换参考视频；字幕未修改。",
            )
        }
    }

    fun openSubtitle(uri: Uri) {
        val text = app.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("无法读取字幕")
        val document = AssCodec.parse(text)
        history.reset(document)
        _state.update {
            it.copy(
                project = it.project.copy(subtitleUri = uri.toString(), title = displayName(uri) ?: "ASS project"),
                document = document,
                subtitleLoaded = true,
                selectedEventIds = emptySet(),
                focusedEventId = document.events.firstOrNull()?.id,
                dirty = false,
                canUndo = false,
                canRedo = false,
                status = "已载入 ${document.events.size} 条 ASS 事件。",
            )
        }
        refreshFontDiagnostics()
    }

    fun importFont(uri: Uri) {
        val asset = fontStore.import(uri)
        refreshFonts(
            initial = false,
            status = "已导入字体 ${asset.metadata.family}；已作为 libass 项目字体与预览 fallback 重新加载。",
        )
    }

    fun reportError(prefix: String, error: Throwable) {
        _state.update {
            it.copy(status = "$prefix：${error.message ?: error::class.java.simpleName}")
        }
    }

    fun saveTo(uri: Uri) {
        val text = AssCodec.write(_state.value.document)
        app.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
            ?: error("无法写入字幕")
        _state.update {
            it.copy(
                project = it.project.copy(subtitleUri = uri.toString()),
                dirty = false,
                status = "ASS 已保存。",
            )
        }
    }

    fun saveCurrent(): Boolean {
        val uri = _state.value.project.subtitleUri?.let(Uri::parse) ?: return false
        saveTo(uri)
        return true
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun toggleSelected(id: Long) {
        _state.update {
            val next = it.selectedEventIds.toMutableSet().apply { if (!add(id)) remove(id) }
            it.copy(selectedEventIds = next)
        }
    }

    fun focusEvent(id: Long, seek: Boolean = true) {
        val event = _state.value.document.events.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                focusedEventId = id,
                seekRequestMs = if (seek) event.start.millis else it.seekRequestMs,
                seekRequestNonce = if (seek) it.seekRequestNonce + 1 else it.seekRequestNonce,
            )
        }
    }

    fun updateFocusedText(text: String) = editDocument("已修改字幕文本。") { doc ->
        val id = _state.value.focusedEventId ?: return@editDocument doc
        doc.copy(events = doc.events.map { if (it.id == id) it.copy(text = text) else it })
    }

    fun updateFocusedTimes(start: String, end: String) {
        val parsedStart = runCatching { SubTime.fromEditable(start) }.getOrNull() ?: return
        val parsedEnd = runCatching { SubTime.fromEditable(end) }.getOrNull() ?: return
        if (parsedEnd < parsedStart) return
        editDocument("已修改字幕时间。") { doc ->
            val id = _state.value.focusedEventId ?: return@editDocument doc
            doc.copy(events = doc.events.map { if (it.id == id) it.copy(start = parsedStart, end = parsedEnd) else it })
        }
    }

    fun undo() {
        if (!history.canUndo) return
        publishDocument(history.undo(), "已撤销。")
    }

    fun redo() {
        if (!history.canRedo) return
        publishDocument(history.redo(), "已重做。")
    }

    fun setPlaybackPosition(positionMs: Long) = _state.update { it.copy(playbackPositionMs = positionMs.coerceAtLeast(0)) }

    fun setSplitRatio(value: Float) = _state.update {
        it.copy(project = it.project.copy(splitRatio = value.coerceIn(0.28f, 0.78f)))
    }

    fun rendererConfigDir() = fontStore.mpvConfigDir
    fun rendererFontsDir() = fontStore.importedDir

    private inline fun editDocument(status: String, transform: (AssDocument) -> AssDocument) {
        val next = transform(_state.value.document)
        if (next == _state.value.document) return
        history.commit(next)
        publishDocument(next, status, dirty = true)
        refreshFontDiagnostics()
    }

    private fun publishDocument(document: AssDocument, status: String, dirty: Boolean = true) {
        _state.update {
            it.copy(
                document = document,
                dirty = dirty,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                status = status,
            )
        }
        refreshFontDiagnostics()
    }

    private fun refreshFonts(initial: Boolean, status: String? = null) {
        val fallback = fontStore.ensureFallbackFont()
        val imported = fontStore.listImported()
        _state.update {
            it.copy(
                importedFonts = imported,
                fallbackFontFamily = fallback?.family,
                fontRevision = if (initial) it.fontRevision else it.fontRevision + 1,
                status = status ?: it.status,
            )
        }
        refreshFontDiagnostics()
    }

    private fun refreshFontDiagnostics() {
        _state.update { state ->
            state.copy(
                fontDiagnostics = FontDiagnostics.diagnose(
                    requestedFamilies = state.document.styles.map { it.fontName },
                    imported = state.importedFonts,
                    fallbackFamily = state.fallbackFontFamily,
                ),
            )
        }
    }

    private fun displayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return app.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }
}
