package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.assworkbench.container.MatroskaReader
import io.github.assworkbench.container.MatroskaScanResult
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssDocumentEditing
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.EventOverrideEditor
import io.github.assworkbench.domain.EventFormatClipboard
import io.github.assworkbench.domain.EventFormatClipboardOps
import io.github.assworkbench.domain.EventFormatPasteMode
import io.github.assworkbench.domain.FontBindingRewriter
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.UndoHistory
import io.github.assworkbench.fonts.FontDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    private val history = StartupProbe.stage(application, "viewmodel_history") {
        UndoHistory(AssDocument(), limit = 80)
    }
    private val fontStore = StartupProbe.stage(application, "viewmodel_fontstore") {
        FontStore(application)
    }
    private val mkvGoTool = StartupProbe.stage(application, "viewmodel_mkvgo") {
        MkvGoTool(application)
    }
    private val recoveryStore = StartupProbe.stage(application, "viewmodel_recovery_store") {
        RecoveryStore(application)
    }
    private var recoveryJob: Job? = null
    private var fontDiagnosticJob: Job? = null
    private var containerScan: MatroskaScanResult? = null
    private var eventFormatClipboard: EventFormatClipboard? = null
    private val _state = MutableStateFlow(
        EditorState(
            project = io.github.assworkbench.domain.SubtitleProject(),
            recoveryAvailable = recoveryStore.exists(),
            recoveryLabel = recoveryStore.label(),
        )
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        StartupProbe.stage(application, "viewmodel_initial_refresh") {
            refreshFonts(initial = true)
        }
        StartupProbe.mark(application, "viewmodel_constructed", "success")
    }

    fun newSubtitleProject() {
        fontStore.clearProjectFonts(refresh = false)
        val document = AssDocument()
        history.reset(document)
        _state.update {
            it.copy(
                project = it.project.copy(
                    title = "Untitled.ass",
                    subtitleUri = null,
                ),
                document = document,
                subtitleLoaded = true,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
                focusedEventId = null,
                dirty = false,
                canUndo = false,
                canRedo = false,
                container = ContainerBridgeState(),
                status = "已新建空白 ASS；可在当前播放位置添加第一条字幕。",
            )
        }
        refreshFontDiagnostics()
    }

    fun openMkvProject(uri: Uri) {
        // MKV is a separate project workflow. The picker callback reaches here only
        // after the user actually chose a file, so cancelling the picker preserves
        // the current workspace.
        fontStore.clearProjectFonts(refresh = false)
        recoveryStore.clear()
        containerScan = null
        val blank = AssDocument()
        history.reset(blank)
        _state.update {
            EditorState(
                project = io.github.assworkbench.domain.SubtitleProject(
                    title = displayName(uri) ?: "Matroska project",
                    videoUri = uri.toString(),
                    subtitleUri = null,
                ),
                document = blank,
                subtitleLoaded = false,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                importedFonts = it.importedFonts.filter { font ->
                    font.origin != io.github.assworkbench.fonts.FontOrigin.MKV_ATTACHMENT
                },
                fallbackFontFamily = it.fallbackFontFamily,
                fontRevision = it.fontRevision,
                recoveryAvailable = false,
                recoveryLabel = "",
                container = ContainerBridgeState(
                    uri = uri.toString(),
                    name = displayName(uri) ?: "Matroska project",
                    loading = true,
                    writeBackAvailable = mkvGoTool.isAvailable(),
                ),
                status = "正在扫描 MKV 字幕轨与字体附件……",
            )
        }
        viewModelScope.launch {
            var imported = 0
            var skipped = 0
            runCatching {
                withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri) ?: error("无法读取 MKV")
                    val scan = stream.use {
                        MatroskaReader().scan(
                            input = it,
                            retainAttachments = false,
                            onAttachment = { attachment ->
                                if (!attachment.isSupportedFont) {
                                    skipped++
                                } else {
                                    val asset = runCatching {
                                        fontStore.importEmbeddedFont(attachment.fileName, attachment.data)
                                    }.getOrNull()
                                    if (asset != null) imported++ else skipped++
                                }
                            },
                        )
                    }
                    // Project fonts are exposed to libass through sub-fonts-dir.
                    // Do not rebuild Fontconfig while an MKV is opening: live native cache
                    // mutation has caused process-level crashes on some Android devices.
                    scan
                }
            }.onSuccess { scan ->
                containerScan = scan
                val tracks = scan.subtitleTracks.map {
                    ContainerTrackUi(it.number, it.displayName, it.language, it.packets.size)
                }
                _state.update {
                    it.copy(
                        project = it.project.copy(videoUri = uri.toString()),
                        container = it.container.copy(
                            loading = false,
                            tracks = tracks,
                            extractedFontCount = imported,
                            skippedAttachmentCount = skipped,
                            error = null,
                        ),
                        status = "MKV：发现 " + tracks.size + " 个 ASS 轨；注册字体 " + imported + " 个。",
                    )
                }
                refreshFonts(initial = false)
                if (tracks.size == 1) selectContainerTrack(tracks.single().number)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        container = it.container.copy(loading = false, error = error.message ?: "MKV 扫描失败"),
                        status = "MKV 扫描失败：" + (error.message ?: error::class.java.simpleName),
                    )
                }
            }
        }
    }

    fun selectContainerTrack(trackNumber: Long) {
        val scan = containerScan ?: return
        val track = scan.subtitleTracks.firstOrNull { it.number == trackNumber } ?: return
        val document = AssCodec.parse(track.toAss())
        history.reset(document)
        _state.update {
            it.copy(
                project = it.project.copy(subtitleUri = null, title = it.container.name + " · " + track.displayName),
                document = document,
                subtitleLoaded = true,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                selectedEventIds = emptySet(),
                focusedEventId = document.events.firstOrNull()?.id,
                dirty = false,
                canUndo = false,
                canRedo = false,
                container = it.container.copy(selectedTrackNumber = trackNumber),
                status = "已从 MKV 载入 " + track.displayName + "，共 " + document.events.size + " 条。",
            )
        }
        refreshFontDiagnostics()
    }

    fun saveMkvTo(outputUri: Uri) {
        val snapshot = _state.value
        val sourceUri = snapshot.container.uri?.let(Uri::parse) ?: run {
            reportError("MKV 写回失败", IllegalStateException("没有已打开的 MKV 工程"))
            return
        }
        val trackNumber = snapshot.container.selectedTrackNumber ?: run {
            reportError("MKV 写回失败", IllegalStateException("尚未选择 ASS 轨"))
            return
        }
        if (!mkvGoTool.isAvailable()) {
            reportError("MKV 写回失败", IllegalStateException("当前设备 ABI 没有 MKV 写回工具"))
            return
        }

        _state.update {
            it.copy(
                container = it.container.copy(writeBackBusy = true),
                status = "正在无重编码更新 MKV；大文件可能需要一些时间……",
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val work = File(app.cacheDir, "container-writeback").apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    val source = File(work, "source.mkv")
                    app.contentResolver.openInputStream(sourceUri)?.use { input ->
                        source.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                    } ?: error("无法重新读取源 MKV")

                    val editedAss = File(work, "edited.ass")
                    editedAss.writeText(AssCodec.write(_state.value.document), Charsets.UTF_8)
                    val result = File(work, "updated.mkv")

                    mkvGoTool.replaceAss(
                        source = source,
                        trackNumber = trackNumber,
                        editedAss = editedAss,
                        output = result,
                    )

                    app.contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                        result.inputStream().buffered().use { input -> input.copyTo(output, 1024 * 1024) }
                    } ?: error("无法写入目标 MKV")
                    result.length()
                }
            }.onSuccess { bytes ->
                _state.update {
                    it.copy(
                        dirty = false,
                        container = it.container.copy(writeBackBusy = false),
                        status = "新 MKV 已保存；视频/音频未重新编码，原 ASS 轨身份与顺序保持，输出 " + (bytes / (1024 * 1024)) + " MiB。",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        container = it.container.copy(writeBackBusy = false),
                        status = "MKV 写回失败：" + (error.message ?: error::class.java.simpleName),
                    )
                }
            }
        }
    }

    fun openPickedVideo(uri: Uri) {
        val current = _state.value
        if (current.container.uri == uri.toString()) {
            _state.update {
                it.copy(
                    project = it.project.copy(videoUri = uri.toString()),
                    status = "当前 MKV 工程已经是参考视频，无需重复载入。",
                )
            }
            return
        }
        attachVideo(uri)
    }

    fun attachVideo(uri: Uri) {
        _state.update {
            it.copy(
                project = it.project.copy(videoUri = uri.toString()),
                status = if ((displayName(uri) ?: "").endsWith(".mkv", ignoreCase = true)) {
                    "已把 MKV 作为参考视频载入；如需编辑它的内嵌 ASS，请使用顶部 MKV 入口。"
                } else {
                    "已更换参考视频；字幕未修改。"
                },
            )
        }
    }

    fun openSubtitle(uri: Uri) {
        fontStore.clearProjectFonts(refresh = false)
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取字幕")
        val decoded = AssTextDecoder.decode(bytes)
        val document = AssCodec.parse(decoded.text)
        history.reset(document)
        _state.update {
            it.copy(
                project = it.project.copy(subtitleUri = uri.toString(), title = displayName(uri) ?: "ASS project"),
                document = document,
                subtitleLoaded = true,
                subtitleTextEncoding = decoded.encoding,
                selectedEventIds = emptySet(),
                focusedEventId = document.events.firstOrNull()?.id,
                dirty = false,
                canUndo = false,
                canRedo = false,
                container = ContainerBridgeState(),
                status = "已载入 ${document.events.size} 条 ASS 事件 · ${decoded.encoding.displayName}。",
            )
        }
        refreshFontDiagnostics()
    }

    fun importFont(uri: Uri) {
        importFonts(listOf(uri))
    }

    fun importFonts(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.fontImportBusy) return
        _state.update { it.copy(fontImportBusy = true, status = "正在验证并注册字体……") }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { fontStore.importAll(uris) }
            }
            result.onSuccess { batch ->
                if (batch.assets.isEmpty()) {
                    _state.update {
                        it.copy(
                            fontImportBusy = false,
                            status = "字体导入失败" +
                                batch.failures.firstOrNull()?.let { "：$it" }.orEmpty(),
                        )
                    }
                    return@onSuccess
                }
                val fallback = withContext(Dispatchers.IO) { fontStore.ensureFallbackFont() }
                val imported = withContext(Dispatchers.IO) { fontStore.listImported() }
                val families = batch.assets.map { it.metadata.rendererFamily }.distinct()
                val failureSuffix = if (batch.failures.isEmpty()) "" else " · 失败 " + batch.failures.size + " 个"
                _state.update {
                    it.copy(
                        importedFonts = imported,
                        fallbackFontFamily = fallback?.rendererFamily,
                        fontImportBusy = false,
                        fontRevision = it.fontRevision + 1,
                        status = "已导入 " + batch.assets.size + " 个字体" + failureSuffix +
                            " · " + families.take(3).joinToString(", ") +
                            " · 已请求安全重载字幕字体",
                    )
                }
                refreshFontDiagnostics()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        fontImportBusy = false,
                        status = "字体导入失败：" + (error.message ?: error::class.java.simpleName),
                    )
                }
            }
        }
    }

    fun reportError(prefix: String, error: Throwable) {
        _state.update {
            it.copy(status = "$prefix：${error.message ?: error::class.java.simpleName}")
        }
    }

    fun saveTo(uri: Uri) {
        val snapshot = _state.value
        val text = AssCodec.write(snapshot.document)
        val bytes = snapshot.subtitleTextEncoding.encode(text)
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("无法写入字幕")
        recoveryStore.clear()
        _state.update {
            it.copy(
                project = it.project.copy(subtitleUri = uri.toString()),
                dirty = false,
                recoveryAvailable = false,
                recoveryLabel = "",
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
            it.copy(selectedEventIds = next, selectionAnchorId = null)
        }
    }

    fun beginRangeSelection(id: Long) {
        _state.update {
            it.copy(
                selectedEventIds = it.selectedEventIds + id,
                selectionAnchorId = id,
                status = "区间选择起点 #" + id + "；点另一条字幕完成整段选择。",
            )
        }
    }

    fun selectRangeTo(id: Long) {
        _state.update { state ->
            val anchor = state.selectionAnchorId ?: return@update state
            val ordered = state.filteredEvents.map { it.id }
            val a = ordered.indexOf(anchor)
            val b = ordered.indexOf(id)
            if (a < 0 || b < 0) return@update state.copy(selectionAnchorId = null)
            val from = minOf(a, b)
            val to = maxOf(a, b)
            val range = ordered.subList(from, to + 1)
            state.copy(
                selectedEventIds = state.selectedEventIds + range,
                selectionAnchorId = null,
                status = "已选择区间 " + range.size + " 条字幕。",
            )
        }
    }

    fun previewRangeSelection(id: Long) {
        _state.update { state ->
            val anchor = state.selectionAnchorId ?: return@update state
            val ordered = state.filteredEvents.map { it.id }
            val a = ordered.indexOf(anchor)
            val b = ordered.indexOf(id)
            if (a < 0 || b < 0) return@update state
            val from = minOf(a, b)
            val to = maxOf(a, b)
            val range = ordered.subList(from, to + 1).toSet()
            state.copy(
                selectedEventIds = range,
                status = "滑动选择 " + range.size + " 条字幕。",
            )
        }
    }

    fun finishRangeSelection() {
        _state.update { state ->
            if (state.selectionAnchorId == null) state
            else state.copy(
                selectionAnchorId = null,
                status = "已完成滑动选择 " + state.selectedEventIds.size + " 条字幕。",
            )
        }
    }

    fun clearSelection() = _state.update {
        it.copy(selectedEventIds = emptySet(), selectionAnchorId = null, status = "已清除选择。")
    }

    fun focusPreviousEvent() {
        val events = _state.value.document.events
        if (events.isEmpty()) return
        val current = _state.value.focusedEventId
        val index = events.indexOfFirst { it.id == current }
        val target = events[(if (index <= 0) 0 else index - 1)]
        focusEvent(target.id, seek = true)
    }

    fun focusNextEvent() {
        val events = _state.value.document.events
        if (events.isEmpty()) return
        val current = _state.value.focusedEventId
        val index = events.indexOfFirst { it.id == current }
        val target = events[(if (index < 0) 0 else (index + 1).coerceAtMost(events.lastIndex))]
        focusEvent(target.id, seek = true)
    }

    fun alignSelectedStartToPlayback() {
        val snapshot = _state.value
        val ids = snapshot.selectedEventIds
        if (ids.isEmpty()) return
        val selected = snapshot.document.events.filter { it.id in ids }
        val earliest = selected.minOfOrNull { it.start.millis } ?: return
        val delta = snapshot.playbackPositionMs - earliest
        shiftSelected(delta)
    }

    fun updateFocusedMetadata(layer: Int, actor: String, comment: Boolean) {
        val id = _state.value.focusedEventId ?: return
        editDocument("已更新当前字幕的 Layer / Actor / 类型。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) {
                    event.copy(
                        layer = layer.coerceIn(-999, 999),
                        name = actor,
                        comment = comment,
                    )
                } else event
            })
        }
    }

    fun setSelectedComment(comment: Boolean) {
        val ids = _state.value.selectedEventIds
        if (ids.isEmpty()) return
        editDocument(if (comment) "已把选中字幕设为 Comment。" else "已把选中字幕设为 Dialogue。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id in ids) event.copy(comment = comment) else event
            })
        }
    }

    fun setEventTiming(id: Long, startMs: Long, endMs: Long) {
        if (endMs < startMs) return
        editDocument("已在时间轴修改字幕 #" + id + "。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(
                    start = SubTime(startMs.coerceAtLeast(0L)),
                    end = SubTime(endMs.coerceAtLeast(startMs.coerceAtLeast(0L))),
                ) else event
            })
        }
    }

    fun setFocusedPosition(x: Double, y: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val event = state.document.events.firstOrNull { it.id == id } ?: return
        val current = EventOverrideEditor.inspect(event.text)
        applyEventOverrides(
            id = id,
            x = x.coerceIn(0.0, state.document.playResX.toDouble()),
            y = y.coerceIn(0.0, state.document.playResY.toDouble()),
            blur = current.blur,
            fadeInMs = current.fadeInMs,
            fadeOutMs = current.fadeOutMs,
            softEntry = current.softEntry,
        )
    }

    fun setFocusedAlignment(alignment: Int) {
        val id = _state.value.focusedEventId ?: return
        editDocument("已设置当前字幕对齐点。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else {
                    val leading = Regex("""^(?:\{[^}]*\})*""").find(event.text)?.value.orEmpty()
                    val body = event.text.removePrefix(leading)
                    val cleaned = leading.replace(Regex("""\\an[1-9]"""), "").replace(Regex("""\{\s*\}"""), "")
                    event.copy(text = cleaned + "{\\an" + alignment.coerceIn(1, 9) + "}" + body)
                }
            })
        }
    }

    fun insertEventAtPlayback() {
        val snapshot = _state.value
        val result = AssDocumentEditing.insertAtPlayback(
            document = snapshot.document,
            afterEventId = snapshot.focusedEventId,
            positionMs = snapshot.playbackPositionMs,
        )
        editDocument("已在当前播放位置添加字幕。") { result.document }
        _state.update {
            it.copy(
                focusedEventId = result.focusedEventId,
                selectedEventIds = result.selectedEventIds,
            )
        }
    }

    fun deleteSelectedOrFocused() {
        val snapshot = _state.value
        val ids = snapshot.selectedEventIds.ifEmpty {
            snapshot.focusedEventId?.let(::setOf).orEmpty()
        }
        if (ids.isEmpty()) return
        val result = AssDocumentEditing.deleteEvents(snapshot.document, ids)
        editDocument("已删除 " + ids.size + " 条字幕。") { result.document }
        _state.update {
            it.copy(
                focusedEventId = result.focusedEventId,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
            )
        }
    }

    fun splitFocusedEvent(textIndex: Int) {
        val snapshot = _state.value
        val id = snapshot.focusedEventId ?: return
        runCatching {
            AssDocumentEditing.splitEvent(
                document = snapshot.document,
                eventId = id,
                splitTimeMs = snapshot.playbackPositionMs,
                textIndex = textIndex,
            )
        }.onSuccess { result ->
            editDocument("已在当前播放时间和文本光标处分割字幕。") { result.document }
            val newIds = result.document.events.map { it.id }.toSet() - snapshot.document.events.map { it.id }.toSet()
            _state.update { state ->
                state.copy(
                    focusedEventId = result.focusedEventId,
                    selectedEventIds = result.selectedEventIds,
                )
            }
        }.onFailure { reportError("拆分失败", it) }
    }

    fun mergeSelected(useLineBreak: Boolean = true) {
        val snapshot = _state.value
        val ids = snapshot.selectedEventIds
        if (ids.size < 2) return
        runCatching {
            AssDocumentEditing.mergeEvents(
                document = snapshot.document,
                eventIds = ids,
                separator = if (useLineBreak) "\\N" else " ",
            )
        }.onSuccess { result ->
            editDocument("已合并 " + ids.size + " 条字幕。") { result.document }
            _state.update {
                it.copy(
                    focusedEventId = result.focusedEventId,
                    selectedEventIds = result.selectedEventIds,
                    selectionAnchorId = null,
                )
            }
        }.onFailure { reportError("合并失败", it) }
    }

    fun nudgeFocusedTime(deltaMs: Long) {
        val id = _state.value.focusedEventId ?: return
        if (deltaMs == 0L) return
        editDocument("当前字幕整体平移 " + deltaMs + " ms。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else {
                    val duration = event.end.millis - event.start.millis
                    val start = (event.start.millis + deltaMs).coerceAtLeast(0L)
                    event.copy(start = SubTime(start), end = SubTime(start + duration))
                }
            })
        }
    }

    fun nudgeFocusedStart(deltaMs: Long) {
        val id = _state.value.focusedEventId ?: return
        editDocument("开始时间微调 " + deltaMs + " ms。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else {
                    val next = (event.start.millis + deltaMs).coerceIn(0L, event.end.millis)
                    event.copy(start = SubTime(next))
                }
            })
        }
    }

    fun nudgeFocusedEnd(deltaMs: Long) {
        val id = _state.value.focusedEventId ?: return
        editDocument("结束时间微调 " + deltaMs + " ms。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else {
                    val next = (event.end.millis + deltaMs).coerceAtLeast(event.start.millis)
                    event.copy(end = SubTime(next))
                }
            })
        }
    }

    fun setFocusedStartToPlayback() {
        val id = _state.value.focusedEventId ?: return
        val now = _state.value.playbackPositionMs
        editDocument("开始时间已设为当前播放位置。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(start = SubTime(now.coerceAtMost(event.end.millis)))
            })
        }
    }

    fun setFocusedEndToPlayback() {
        val id = _state.value.focusedEventId ?: return
        val now = _state.value.playbackPositionMs
        editDocument("结束时间已设为当前播放位置。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(end = SubTime(now.coerceAtLeast(event.start.millis)))
            })
        }
    }

    fun copyFocusedFormatToClipboard() {
        val snapshot = _state.value
        val event = snapshot.focusedEventId?.let { id ->
            snapshot.document.events.firstOrNull { it.id == id }
        } ?: return
        eventFormatClipboard = EventFormatClipboardOps.capture(event)
        _state.update { it.copy(status = "已复制字幕 #" + event.id + " 的格式到内部剪贴板。") }
    }

    fun pasteFormatClipboardToSelected(mode: EventFormatPasteMode) {
        val clipboard = eventFormatClipboard ?: run {
            _state.update { it.copy(status = "格式剪贴板为空。") }
            return
        }
        val snapshot = _state.value
        val ids = snapshot.selectedEventIds.ifEmpty {
            snapshot.focusedEventId?.let(::setOf).orEmpty()
        }
        if (ids.isEmpty()) return
        editDocument("已粘贴格式到 " + ids.size + " 条字幕。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id in ids) EventFormatClipboardOps.apply(event, clipboard, mode) else event
            })
        }
    }

    fun copyFocusedFormattingToSelected() {
        val snapshot = _state.value
        val sourceId = snapshot.focusedEventId ?: return
        val targets = snapshot.selectedEventIds - sourceId
        if (targets.isEmpty()) return
        editDocument("已把当前字幕格式应用到 " + targets.size + " 条字幕。") { doc ->
            AssDocumentEditing.copyEventFormatting(doc, sourceId, targets)
        }
    }

    fun replaceAll(find: String, replacement: String, inActor: Boolean) {
        if (find.isEmpty()) return
        var count = 0
        editDocument("批量替换完成。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (inActor) {
                    if (!event.name.contains(find, ignoreCase = false)) event else {
                        count++
                        event.copy(name = event.name.replace(find, replacement))
                    }
                } else {
                    val (text, changed) = AssDocumentEditing.replacePlainDialogueText(event.text, find, replacement)
                    if (!changed) event else {
                        count++
                        event.copy(text = text)
                    }
                }
            })
        }
        _state.update { it.copy(status = "已替换 " + count + " 条字幕中的" + if (inActor) " Actor。" else "正文。") }
    }

    fun createStyle(name: String, copyFrom: String? = null) {
        runCatching {
            AssDocumentEditing.createStyle(_state.value.document, name, copyFrom)
        }.onSuccess { next ->
            editDocument("已创建 Style " + name.trim() + "。") { next }
        }.onFailure { reportError("创建 Style 失败", it) }
    }

    fun renameStyle(oldName: String, newName: String) {
        runCatching {
            AssDocumentEditing.renameStyle(_state.value.document, oldName, newName)
        }.onSuccess { next ->
            editDocument("Style 已重命名为 " + newName.trim() + "。") { next }
        }.onFailure { reportError("重命名 Style 失败", it) }
    }

    fun deleteUnusedStyles() {
        val snapshot = _state.value
        val used = snapshot.document.events.mapTo(hashSetOf()) { it.style }
        val removable = snapshot.document.styles.filterNot { it.name in used }
        if (removable.isEmpty()) {
            _state.update { it.copy(status = "没有未使用的 Style。") }
            return
        }
        editDocument("已清理未使用 Style。") { doc ->
            val keepAtLeast = if (doc.styles.all { it.name !in used }) doc.styles.firstOrNull()?.name else null
            doc.copy(styles = doc.styles.filter { it.name in used || it.name == keepAtLeast })
        }
        _state.update { it.copy(status = "已删除 " + removable.size + " 个未使用 Style。") }
    }

    fun deleteStyle(name: String, replacement: String) {
        runCatching {
            AssDocumentEditing.deleteStyle(_state.value.document, name, replacement)
        }.onSuccess { next ->
            editDocument("已删除 Style " + name + "，引用已转到 " + replacement + "。") { next }
        }.onFailure { reportError("删除 Style 失败", it) }
    }

    fun shiftSelected(deltaMs: Long) {
        val ids = _state.value.selectedEventIds
        if (ids.isEmpty() || deltaMs == 0L) return
        editDocument("已将 " + ids.size + " 条字幕平移 " + deltaMs + " ms。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id !in ids) {
                    event
                } else {
                    val duration = event.end.millis - event.start.millis
                    val newStart = (event.start.millis + deltaMs).coerceAtLeast(0L)
                    event.copy(start = SubTime(newStart), end = SubTime(newStart + duration))
                }
            })
        }
    }

    fun setSelectedLayer(layer: Int) {
        val ids = _state.value.selectedEventIds
        if (ids.isEmpty()) return
        editDocument("已修改 " + ids.size + " 条字幕的 Layer。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id in ids) event.copy(layer = layer.coerceIn(-999, 999)) else event
            })
        }
    }

    fun assignFocusedStyle(styleName: String) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        if (state.document.styles.none { it.name == styleName }) return
        editDocument("当前字幕已指定为 Style " + styleName + "。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(style = styleName) else event
            })
        }
    }

    fun assignSelectedStyle(styleName: String) {
        val state = _state.value
        if (state.selectedEventIds.isEmpty() || state.document.styles.none { it.name == styleName }) return
        editDocument("已将 " + state.selectedEventIds.size + " 条字幕指定为 Style " + styleName + "。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id in state.selectedEventIds) event.copy(style = styleName) else event
            })
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

    fun forceFontFamily(family: String) {
        val target = family.trim()
        if (target.isEmpty()) return
        editDocument("已强制绑定字体 $target：全部 Style Fontname 与非空显式 \\fn 已统一改写。") { doc ->
            FontBindingRewriter.forceFamily(doc, target)
        }
    }

    fun clearFocusedStyleOverrides() {
        val id = _state.value.focusedEventId ?: return
        editDocument("已清除当前字幕的样式/位置覆盖；该字幕现在继承 Style。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.inheritStyle() else event
            })
        }
    }

    fun clearSelectedStyleOverrides() {
        val ids = _state.value.selectedEventIds
        if (ids.isEmpty()) return
        editDocument("已让 " + ids.size + " 条选中字幕完全继承各自 Style。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id in ids) event.inheritStyle() else event
            })
        }
    }

    fun makeSelectedStylesIndependent() {
        val selectedIds = _state.value.selectedEventIds
        if (selectedIds.isEmpty()) return
        editDocument("已为选中字幕创建独立 Style 副本。") { doc ->
            val selectedEvents = doc.events.filter { it.id in selectedIds }
            if (selectedEvents.isEmpty()) return@editDocument doc

            val styleByName = doc.styles.associateBy { it.name }
            val usedNames = doc.styles.mapTo(linkedSetOf()) { it.name }
            val cloneBySource = linkedMapOf<String, String>()
            val clones = mutableListOf<io.github.assworkbench.domain.AssStyle>()

            selectedEvents.map { it.style }.distinct().forEach { sourceName ->
                val source = styleByName[sourceName] ?: return@forEach
                val base = sourceName + "_selection"
                var candidate = base
                var suffix = 2
                while (candidate in usedNames) {
                    candidate = base + "_" + suffix++
                }
                usedNames += candidate
                cloneBySource[sourceName] = candidate
                clones += source.copy(name = candidate)
            }

            if (clones.isEmpty()) return@editDocument doc
            doc.copy(
                styles = doc.styles + clones,
                events = doc.events.map { event ->
                    if (event.id !in selectedIds) {
                        event
                    } else {
                        cloneBySource[event.style]?.let { event.copy(style = it) } ?: event
                    }
                },
            )
        }
    }

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
        secondaryColor: String,
        outlineColor: String,
        backColor: String,
        scaleX: Double,
        scaleY: Double,
        angle: Double,
        borderStyle: Int,
        encoding: Int,
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
                        secondaryColor = secondaryColor.trim().ifBlank { style.secondaryColor },
                        outlineColor = outlineColor.trim().ifBlank { style.outlineColor },
                        backColor = backColor.trim().ifBlank { style.backColor },
                        scaleX = scaleX.coerceIn(1.0, 1000.0),
                        scaleY = scaleY.coerceIn(1.0, 1000.0),
                        angle = angle.coerceIn(-3600.0, 3600.0),
                        borderStyle = borderStyle.coerceIn(1, 4),
                        encoding = encoding.coerceIn(0, 255),
                    )
                }
            })
        }
    }

    fun updateEventText(id: Long, text: String) {
        editDocument("已修改字幕 #" + id + "。") { doc ->
            doc.copy(events = doc.events.map { if (it.id == id) it.copy(text = text) else it })
        }
    }

    fun applyEventOverrides(
        id: Long,
        x: Double?,
        y: Double?,
        blur: Double?,
        fadeInMs: Int?,
        fadeOutMs: Int?,
        softEntry: Boolean,
    ) {
        editDocument("已更新字幕 #" + id + " 的事件级效果。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) {
                    event
                } else {
                    event.copy(
                        text = EventOverrideEditor.update(
                            text = event.text,
                            x = x,
                            y = y,
                            blurRadius = blur,
                            fadeInMs = fadeInMs,
                            fadeOutMs = fadeOutMs,
                            enableSoftEntry = softEntry,
                        )
                    )
                }
            })
        }
    }

    fun nudgeEventPosition(id: Long, dx: Double, dy: Double) {
        val state = _state.value
        val event = state.document.events.firstOrNull { it.id == id } ?: return
        val snapshot = EventOverrideEditor.inspect(event.text)
        val style = state.document.styles.firstOrNull { it.name == event.style }
        val base = eventAnchor(event, style, state.document.playResX, state.document.playResY)
        val x = (snapshot.x ?: base.first) + dx
        val y = (snapshot.y ?: base.second) + dy
        applyEventOverrides(
            id = id,
            x = x.coerceIn(0.0, state.document.playResX.toDouble()),
            y = y.coerceIn(0.0, state.document.playResY.toDouble()),
            blur = snapshot.blur,
            fadeInMs = snapshot.fadeInMs,
            fadeOutMs = snapshot.fadeOutMs,
            softEntry = snapshot.softEntry,
        )
    }

    private fun eventAnchor(
        event: io.github.assworkbench.domain.AssEvent,
        style: io.github.assworkbench.domain.AssStyle?,
        playResX: Int,
        playResY: Int,
    ): Pair<Double, Double> {
        val alignment = style?.alignment ?: 2
        val marginL = if (event.marginL > 0) event.marginL else style?.marginL ?: 10
        val marginR = if (event.marginR > 0) event.marginR else style?.marginR ?: 10
        val marginV = if (event.marginV > 0) event.marginV else style?.marginV ?: 10
        val x = when (alignment) {
            1, 4, 7 -> marginL.toDouble()
            3, 6, 9 -> (playResX - marginR).toDouble()
            else -> playResX / 2.0
        }
        val y = when (alignment) {
            7, 8, 9 -> marginV.toDouble()
            4, 5, 6 -> playResY / 2.0
            else -> (playResY - marginV).toDouble()
        }
        return x to y
    }

    fun focusEvent(id: Long, seek: Boolean = true) {
        val event = _state.value.document.events.firstOrNull { it.id == id } ?: return
        val seekInsideEvent = if (seek) {
            val start = event.start.millis
            val end = event.end.millis
            val duration = (end - start).coerceAtLeast(0L)
            val inset = when {
                duration <= 2L -> 0L
                duration < 160L -> (duration / 3L).coerceAtLeast(1L)
                else -> 80L
            }
            (start + inset).coerceAtMost((end - 1L).coerceAtLeast(start))
        } else null
        _state.update {
            it.copy(
                focusedEventId = id,
                seekRequestMs = seekInsideEvent ?: it.seekRequestMs,
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

    fun updateFocusedMargins(marginL: Int, marginR: Int, marginV: Int) {
        val id = _state.value.focusedEventId ?: return
        editDocument("已更新当前字幕的事件级 Margin。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) {
                    event.copy(
                        marginL = marginL.coerceIn(0, 9999),
                        marginR = marginR.coerceIn(0, 9999),
                        marginV = marginV.coerceIn(0, 9999),
                    )
                } else event
            })
        }
    }

    fun clearFocusedMargins() = updateFocusedMargins(0, 0, 0)

    fun undo() {
        if (!history.canUndo) return
        publishDocument(history.undo(), "已撤销。")
    }

    fun redo() {
        if (!history.canRedo) return
        publishDocument(history.redo(), "已重做。")
    }

    fun setPlaybackPosition(positionMs: Long) = _state.update { it.copy(playbackPositionMs = positionMs.coerceAtLeast(0)) }

    fun restoreRecovery() {
        val snapshot = recoveryStore.read() ?: run {
            _state.update { it.copy(recoveryAvailable = false, recoveryLabel = "") }
            return
        }
        history.reset(snapshot.document)
        _state.update {
            it.copy(
                project = snapshot.project,
                document = snapshot.document,
                subtitleLoaded = true,
                subtitleTextEncoding = snapshot.textEncoding,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
                focusedEventId = snapshot.document.events.firstOrNull()?.id,
                dirty = true,
                canUndo = false,
                canRedo = false,
                recoveryAvailable = false,
                recoveryLabel = "",
                status = "已恢复上次未保存编辑。",
            )
        }
        refreshFontDiagnostics()
    }

    fun discardRecovery() {
        recoveryStore.clear()
        _state.update { it.copy(recoveryAvailable = false, recoveryLabel = "", status = "已丢弃恢复日志。") }
    }

    fun rendererConfigDir() = fontStore.mpvConfigDir
    fun rendererFontsDir() = fontStore.activeRendererFontsDir()

    fun rebuildRendererFontCache() {
        runCatching { fontStore.rebuildFontconfigCache() }
            .onSuccess { prepared ->
                refreshFonts(
                    initial = false,
                    status = "已重建 Fontconfig 缓存 · " + prepared.fingerprint.take(12),
                )
            }
            .onFailure { error ->
                _state.update {
                    it.copy(status = "Fontconfig 缓存重建失败：" + (error.message ?: error::class.java.simpleName))
                }
            }
    }

    fun updateRendererDiagnostics(lines: List<String>) {
        val normalized = lines.filter(String::isNotBlank).takeLast(12)
        if (_state.value.rendererDiagnostics == normalized) return
        _state.update { it.copy(rendererDiagnostics = normalized) }
    }

    private inline fun editDocument(status: String, transform: (AssDocument) -> AssDocument) {
        val before = _state.value.document
        val next = transform(before)
        if (next == before) return

        history.commit(next)
        publishDocument(next, status, dirty = true)
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
        scheduleFontDiagnostics()
        if (dirty) scheduleRecovery(document)
    }

    private fun scheduleRecovery(document: AssDocument) {
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(450)
            val project = _state.value.project
            val textEncoding = _state.value.subtitleTextEncoding
            withContext(Dispatchers.IO) {
                recoveryStore.write(project, document, textEncoding)
            }
            _state.update {
                it.copy(recoveryAvailable = true, recoveryLabel = project.title)
            }
        }
    }

    private fun refreshFonts(initial: Boolean, status: String? = null) {
        val fallback = fontStore.ensureFallbackFont()
        val imported = fontStore.listImported()
        _state.update {
            it.copy(
                importedFonts = imported,
                fallbackFontFamily = fallback?.rendererFamily,
                fontRevision = if (initial) it.fontRevision else it.fontRevision + 1,
                status = status ?: it.status,
            )
        }
        refreshFontDiagnostics()
    }

    private fun scheduleFontDiagnostics(delayMs: Long = 280L) {
        fontDiagnosticJob?.cancel()
        fontDiagnosticJob = viewModelScope.launch {
            delay(delayMs)
            refreshFontDiagnostics()
        }
    }

    private fun refreshFontDiagnostics() {
        val snapshot = _state.value
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val byStyle = snapshot.document.events.groupBy { it.style }
                val glyphs = linkedMapOf<String, io.github.assworkbench.fonts.FontGlyphDiagnostic>()
                snapshot.document.styles.forEach { style ->
                    val sampleText = buildString {
                        for (event in byStyle[style.name].orEmpty()) {
                            if (length >= 4096) break
                            val clean = io.github.assworkbench.domain.AssInlineSyntax.visibleText(event.text)
                                .replace("\\N", " ")
                                .replace("\\n", " ")
                            append(clean.take((4096 - length).coerceAtLeast(0)))
                            append(' ')
                        }
                    }
                    fontStore.glyphDiagnostic(style.fontName, sampleText)?.let { glyphs[style.name] = it }
                }
                val diagnostics = FontDiagnostics.diagnose(
                    requestedFamilies = snapshot.document.styles.map { it.fontName } +
                        FontBindingRewriter.explicitFamilies(snapshot.document),
                    imported = snapshot.importedFonts,
                    fallbackFamily = snapshot.fallbackFontFamily,
                )
                diagnostics to glyphs
            }

            _state.update { current ->
                if (
                    current.document != snapshot.document ||
                    current.importedFonts != snapshot.importedFonts ||
                    current.fallbackFontFamily != snapshot.fallbackFontFamily
                ) {
                    current
                } else {
                    current.copy(
                        fontDiagnostics = result.first,
                        fontGlyphDiagnostics = result.second,
                    )
                }
            }
        }
    }

    private fun io.github.assworkbench.domain.AssEvent.inheritStyle(): io.github.assworkbench.domain.AssEvent =
        copy(
            text = stripInlineStyleOverrides(text),
            marginL = 0,
            marginR = 0,
            marginV = 0,
        )

    private fun stripInlineStyleOverrides(text: String): String {
        val overrideBlock = Regex("""\{[^}]*\}""")
        val managedTag = Regex(
            """\\(?:fn[^\\}]*|fs(?!c)[+-]?(?:\d+(?:\.\d+)?)?|b-?\d+|i-?\d+|u-?\d+|s-?\d+|fsp[+-]?(?:\d+(?:\.\d+)?)?|bord[+-]?(?:\d+(?:\.\d+)?)?|shad[+-]?(?:\d+(?:\.\d+)?)?|an[1-9]|a\d+|pos\([^)]*\)|move\([^)]*\)|org\([^)]*\)|r[^\\}]*|(?:c|1c|3c|4c)&H[0-9A-Fa-f]+&)""",
            RegexOption.IGNORE_CASE,
        )
        return overrideBlock.replace(text) { block ->
            val inner = block.value.substring(1, block.value.length - 1)
            val stripped = managedTag.replace(inner, "")
            if (stripped.isBlank()) "" else "{$stripped}"
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
