package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.TypesettingMath
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
        val styleNames = document.styles.map { it.name }
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
                reviewSourceStyle = styleNames.firstOrNull().orEmpty(),
                reviewTargetStyle = styleNames.drop(1).firstOrNull().orEmpty(),
                originalTextById = document.events.associate { event -> event.id to event.text },
                confirmedReviewIds = emptySet(),
                reviewFilter = "all",
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

    fun toggleSelectAllVisible() {
        _state.update { state ->
            val visibleIds = state.filteredEvents.mapTo(linkedSetOf()) { it.id }
            if (visibleIds.isEmpty()) return@update state
            val next = state.selectedEventIds.toMutableSet()
            val allVisibleSelected = visibleIds.all { it in next }
            if (allVisibleSelected) next.removeAll(visibleIds) else next.addAll(visibleIds)
            state.copy(
                selectedEventIds = next,
                status = if (allVisibleSelected) "已取消选择当前筛选结果。" else "已选择当前筛选结果 " + visibleIds.size + " 条。",
            )
        }
    }

    fun setStyleFont(styleName: String, family: String) {
        if (family.isBlank()) return
        editDocument("Style " + styleName + " 已改用字体 " + family + "。") { doc ->
            if (doc.styles.none { it.name == styleName }) return@editDocument doc
            doc.copy(styles = doc.styles.map { style ->
                if (style.name == styleName) style.copy(fontName = family) else style
            })
        }
    }

    fun applyFontToSelectedStyles(family: String) {
        if (family.isBlank()) return
        val state = _state.value
        val styleNames = if (state.selectedEventIds.isEmpty()) {
            val styleName = state.focusedEventId?.let { id -> state.document.events.firstOrNull { it.id == id }?.style }
            if (styleName == null) emptySet() else setOf(styleName)
        } else {
            state.document.events.asSequence()
                .filter { it.id in state.selectedEventIds }
                .map { it.style }
                .toSet()
        }
        if (styleNames.isEmpty()) return
        editDocument("已将字体 " + family + " 应用到 " + styleNames.size + " 个 Style。") { doc ->
            doc.copy(styles = doc.styles.map { style ->
                if (style.name in styleNames) style.copy(fontName = family) else style
            })
        }
    }

    fun toggleLayoutGuides() = _state.update { it.copy(showLayoutGuides = !it.showLayoutGuides) }

    fun updateStyleTypography(
        styleName: String,
        fontSize: Double,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        strikeOut: Boolean,
        spacing: Double,
        outline: Double,
        shadow: Double,
        alignment: Int,
        marginL: Int,
        marginR: Int,
        marginV: Int,
        primaryColor: String,
        outlineColor: String,
        backColor: String,
    ) {
        editDocument("已更新 Style " + styleName + " 的排版。") { doc ->
            if (doc.styles.none { it.name == styleName }) return@editDocument doc
            doc.copy(styles = doc.styles.map { style ->
                if (style.name != styleName) {
                    style
                } else {
                    style.copy(
                        fontSize = fontSize.coerceIn(6.0, 240.0),
                        bold = bold,
                        italic = italic,
                        underline = underline,
                        strikeOut = strikeOut,
                        spacing = spacing.coerceIn(-20.0, 100.0),
                        outline = outline.coerceIn(0.0, 20.0),
                        shadow = shadow.coerceIn(0.0, 20.0),
                        alignment = alignment.coerceIn(1, 9),
                        marginL = marginL.coerceIn(0, 9999),
                        marginR = marginR.coerceIn(0, 9999),
                        marginV = marginV.coerceIn(0, 9999),
                        primaryColor = primaryColor.trim().ifBlank { style.primaryColor },
                        outlineColor = outlineColor.trim().ifBlank { style.outlineColor },
                        backColor = backColor.trim().ifBlank { style.backColor },
                    )
                }
            })
        }
    }

    fun applyBilingual6040Preset(sourceStyleName: String, targetStyleName: String) {
        if (sourceStyleName.isBlank() || targetStyleName.isBlank() || sourceStyleName == targetStyleName) return
        editDocument("已应用双语 60/40 排版预设。") { doc ->
            val names = doc.styles.map { it.name }.toSet()
            if (sourceStyleName !in names || targetStyleName !in names) return@editDocument doc
            val layout = TypesettingMath.bilingual6040(doc.playResX, doc.playResY)
            doc.copy(styles = doc.styles.map { style ->
                when (style.name) {
                    sourceStyleName -> style.copy(
                        fontSize = 42.0,
                        alignment = 2,
                        marginL = layout.marginHorizontal,
                        marginR = layout.marginHorizontal,
                        marginV = layout.sourceMarginV,
                        outline = 3.0,
                        shadow = 2.0,
                    )
                    targetStyleName -> style.copy(
                        fontSize = 48.0,
                        alignment = 8,
                        marginL = layout.marginHorizontal,
                        marginR = layout.marginHorizontal,
                        marginV = layout.targetMarginV,
                        outline = 3.0,
                        shadow = 2.0,
                    )
                    else -> style
                }
            })
        }
    }

    fun setReviewSourceStyle(value: String) = _state.update { it.copy(reviewSourceStyle = value) }

    fun setReviewTargetStyle(value: String) = _state.update { it.copy(reviewTargetStyle = value) }

    fun setReviewFilter(value: String) = _state.update { it.copy(reviewFilter = value) }

    fun confirmReviewTarget(id: Long, confirmed: Boolean) {
        _state.update {
            val next = it.confirmedReviewIds.toMutableSet()
            if (confirmed) next.add(id) else next.remove(id)
            it.copy(
                confirmedReviewIds = next,
                status = if (confirmed) "已确认字幕 #" + id else "已取消确认字幕 #" + id,
            )
        }
    }

    fun updateEventText(id: Long, text: String) {
        editDocument("已修改字幕 #" + id + "。") { doc ->
            doc.copy(events = doc.events.map { if (it.id == id) it.copy(text = text) else it })
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
