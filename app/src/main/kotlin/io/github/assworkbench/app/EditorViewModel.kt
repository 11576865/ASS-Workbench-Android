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
import io.github.assworkbench.domain.AssQcIssue
import io.github.assworkbench.domain.AssQualityFixes
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.EventOverrideEditor
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssClipRect
import io.github.assworkbench.domain.AssAnimationSemantic
import io.github.assworkbench.domain.AssComplexFade
import io.github.assworkbench.domain.AssTransform
import io.github.assworkbench.domain.EventFormatClipboard
import io.github.assworkbench.domain.EventFormatClipboardOps
import io.github.assworkbench.domain.EventFormatPasteMode
import io.github.assworkbench.domain.FontBindingRewriter
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.SubtitleDocumentFormat
import io.github.assworkbench.domain.SubRipCodec
import io.github.assworkbench.domain.WebVttCodec
import io.github.assworkbench.domain.UndoHistory
import io.github.assworkbench.fonts.FontDiagnostics
import io.github.assworkbench.fonts.FontPackagingPlanner
import io.github.assworkbench.fonts.FontOrigin
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
import java.util.concurrent.atomic.AtomicLong

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
    private var waveformJob: Job? = null
    private var containerScanJob: Job? = null
    private var waveformSourceUri: String? = null
    private var containerScan: MatroskaScanResult? = null
    private var eventFormatClipboard: EventFormatClipboard? = null
    private val workspaceEpoch = AtomicLong(1L)
    private val writeBackSerial = AtomicLong(0L)
    private val _state = MutableStateFlow(
        EditorState(
            project = io.github.assworkbench.domain.SubtitleProject(),
            recoveryAvailable = recoveryStore.exists(),
            recoveryLabel = recoveryStore.label(),
        )
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // Playback is intentionally outside EditorState. mpv can report position at a
    // high cadence; publishing each tick through the root editor state invalidates
    // the whole workbench even though only timeline / focused-event timing UI needs it.
    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private fun clearPendingRecovery(clearStored: Boolean = true) {
        recoveryJob?.cancel()
        recoveryJob = null
        if (clearStored) recoveryStore.clear()
    }

    private fun beginWorkspaceBoundary(resetProjectFonts: Boolean = true): Long {
        containerScanJob?.cancel()
        containerScanJob = null
        containerScan = null
        val epoch = workspaceEpoch.incrementAndGet()
        if (resetProjectFonts) fontStore.beginProjectFontSession(epoch, refresh = false)
        return epoch
    }

    private fun sameMkvWorkspace(
        state: EditorState,
        epoch: Long,
        containerUri: String?,
        trackNumber: Long?,
    ): Boolean =
        workspaceEpoch.get() == epoch &&
            state.container.uri == containerUri &&
            state.container.selectedTrackNumber == trackNumber

    private fun cancelWaveformAnalysis() {
        waveformJob?.cancel()
        waveformJob = null
        waveformSourceUri = null
    }

    private fun launchWaveformAnalysis(uri: Uri) {
        val source = uri.toString()
        waveformJob?.cancel()
        waveformSourceUri = source
        _state.update {
            it.copy(
                waveform = WaveformLiteState(
                    sourceUri = source,
                    status = WaveformLiteStatus.ANALYZING,
                )
            )
        }
        waveformJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    WaveformLiteAnalyzer.loadOrAnalyze(app, uri)
                }
            }
            if (waveformSourceUri != source || _state.value.project.videoUri != source) return@launch
            result.onSuccess { envelope ->
                _state.update {
                    it.copy(
                        waveform = WaveformLiteState(
                            sourceUri = source,
                            status = WaveformLiteStatus.READY,
                            envelope = envelope,
                        )
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                _state.update {
                    it.copy(
                        waveform = WaveformLiteState(
                            sourceUri = source,
                            status = WaveformLiteStatus.UNAVAILABLE,
                            error = error.message ?: error::class.java.simpleName,
                        )
                    )
                }
            }
        }
    }

    init {
        StartupProbe.stage(application, "viewmodel_initial_refresh") {
            refreshFonts(initial = true)
        }
        StartupProbe.mark(application, "viewmodel_constructed", "success")
    }

    fun newSubtitleProject() {
        clearPendingRecovery()
        beginWorkspaceBoundary()
        val document = AssDocument()
        history.reset(document)
        _state.update {
            it.copy(
                project = it.project.copy(
                    title = "Untitled.ass",
                    subtitleUri = null,
                ),
                document = document,
                previewDocument = null,
                subtitleLoaded = true,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
                focusedEventId = null,
                dirty = false,
                canUndo = false,
                canRedo = false,
                fontPackagingSelection = emptySet(),
                container = ContainerBridgeState(),
                status = "已新建空白 ASS；可在当前播放位置添加第一条字幕。",
            )
        }
        refreshFontDiagnostics()
    }

    fun openMkvProject(uri: Uri) {
        _playbackPositionMs.value = 0L
        cancelWaveformAnalysis()
        // MKV is a separate project workflow. The picker callback reaches here only
        // after the user actually chose a file, so cancelling the picker preserves
        // the current workspace.
        clearPendingRecovery()
        val scanEpoch = beginWorkspaceBoundary()
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
                waveform = WaveformLiteState(
                    sourceUri = uri.toString(),
                    status = WaveformLiteStatus.IDLE,
                ),
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
        containerScanJob = viewModelScope.launch {
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
                                        fontStore.importEmbeddedFont(scanEpoch, attachment.fileName, attachment.data)
                                    }.getOrNull()
                                    if (asset != null) imported++ else skipped++
                                }
                            },
                        )
                    }
                    // Attachments rejected by the bounded reader (oversize, empty or
                    // malformed) never reach onAttachment, so account for them here.
                    skipped += scan.skippedAttachmentCount
                    // Project fonts are exposed to libass through sub-fonts-dir.
                    // Do not rebuild Fontconfig while an MKV is opening: live native cache
                    // mutation has caused process-level crashes on some Android devices.
                    scan
                }
            }.onSuccess { scan ->
                if (
                    workspaceEpoch.get() != scanEpoch ||
                    _state.value.container.uri != uri.toString()
                ) return@onSuccess
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
                if (_state.value.project.videoUri == uri.toString()) {
                    launchWaveformAnalysis(uri)
                }
                if (tracks.size == 1) selectContainerTrack(tracks.single().number)
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                if (
                    workspaceEpoch.get() != scanEpoch ||
                    _state.value.container.uri != uri.toString()
                ) return@onFailure
                _state.update {
                    it.copy(
                        container = it.container.copy(loading = false, error = error.message ?: "MKV 扫描失败"),
                        status = "MKV 扫描失败：" + (error.message ?: error::class.java.simpleName),
                    )
                }
                if (_state.value.project.videoUri == uri.toString()) {
                    launchWaveformAnalysis(uri)
                }
            }
        }
    }

    fun selectContainerTrack(trackNumber: Long, discardUnsaved: Boolean = false) {
        val current = _state.value
        if (
            current.container.selectedTrackNumber == trackNumber &&
            current.subtitleLoaded
        ) {
            _state.update { it.copy(status = "当前已经是所选 ASS 轨。") }
            return
        }
        if (current.dirty && !discardUnsaved) {
            _state.update {
                it.copy(status = "当前 ASS 轨有未保存修改；需明确放弃后才能切换轨道。")
            }
            return
        }
        if (current.container.writeBackBusy) {
            _state.update { it.copy(status = "MKV 写回进行中；完成后才能切换字幕轨。") }
            return
        }
        val scan = containerScan ?: return
        val track = scan.subtitleTracks.firstOrNull { it.number == trackNumber } ?: return
        workspaceEpoch.incrementAndGet()
        clearPendingRecovery()
        val document = AssCodec.parse(track.toAss())
        history.reset(document)
        _state.update {
            it.copy(
                project = it.project.copy(subtitleUri = null, title = it.container.name + " · " + track.displayName),
                document = document,
                previewDocument = null,
                subtitleLoaded = true,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
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
        if (snapshot.container.writeBackBusy) {
            _state.update { it.copy(status = "已有 MKV 写回任务正在进行。") }
            return
        }
        val saveEpoch = workspaceEpoch.get()
        val operationId = writeBackSerial.incrementAndGet()
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

        val embeddedShas = snapshot.importedFonts.asSequence()
            .filter { it.origin == FontOrigin.MKV_ATTACHMENT }
            .map { it.sha256 }
            .toSet()
        val packageAssets = snapshot.importedFonts
            .filter {
                it.origin == FontOrigin.MANUAL &&
                    it.sha256 in snapshot.fontPackagingSelection &&
                    it.sha256 !in embeddedShas
            }
            .distinctBy { it.sha256 }

        _state.update {
            it.copy(
                container = it.container.copy(writeBackBusy = true),
                status = "正在无重编码更新 MKV" +
                    if (packageAssets.isEmpty()) "；大文件可能需要一些时间……"
                    else "并封入 " + packageAssets.size + " 个所选字体；大文件可能需要一些时间……",
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val work = File(app.cacheDir, "container-writeback-" + operationId).apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    try {
                        val source = File(work, "source.mkv")
                        app.contentResolver.openInputStream(sourceUri)?.use { input ->
                            source.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                        } ?: error("无法重新读取源 MKV")

                        val editedAss = File(work, "edited.ass")
                        editedAss.writeText(AssCodec.write(snapshot.document), Charsets.UTF_8)
                        val result = File(work, "updated.mkv")

                        val packageFiles = packageAssets.map { asset ->
                            fontStore.fileFor(asset)
                                ?: error("找不到待封入字体文件：" + asset.fileName)
                        }
                        mkvGoTool.replaceAss(
                            source = source,
                            trackNumber = trackNumber,
                            editedAss = editedAss,
                            output = result,
                            fonts = packageFiles,
                        )

                        app.contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                            result.inputStream().buffered().use { input -> input.copyTo(output, 1024 * 1024) }
                        } ?: error("无法写入目标 MKV")
                        result.length()
                    } finally {
                        work.deleteRecursively()
                    }
                }
            }.onSuccess { bytes ->
                val current = _state.value
                val sameProject = sameMkvWorkspace(
                    current,
                    saveEpoch,
                    snapshot.container.uri,
                    snapshot.container.selectedTrackNumber,
                )
                if (!sameProject) return@onSuccess
                val unchangedSinceSaveStarted = current.document == snapshot.document
                if (unchangedSinceSaveStarted) clearPendingRecovery()
                _state.update { state ->
                    state.copy(
                        dirty = if (unchangedSinceSaveStarted) false else state.dirty,
                        container = state.container.copy(writeBackBusy = false),
                        recoveryAvailable = if (unchangedSinceSaveStarted) false else state.recoveryAvailable,
                        recoveryLabel = if (unchangedSinceSaveStarted) "" else state.recoveryLabel,
                        status = "新 MKV 已保存；视频/音频未重新编码，原 ASS 轨身份与顺序保持" +
                            (if (packageAssets.isEmpty()) "" else "，并封入所选字体 " + packageAssets.size + " 个") +
                            "；输出 " + (bytes / (1024 * 1024)) + " MiB。" +
                            if (unchangedSinceSaveStarted) "" else " · 保存期间出现新编辑，当前工程仍未保存。",
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                _state.update { state ->
                    if (
                        !sameMkvWorkspace(
                            state,
                            saveEpoch,
                            snapshot.container.uri,
                            snapshot.container.selectedTrackNumber,
                        )
                    ) {
                        state
                    } else {
                        state.copy(
                            container = state.container.copy(writeBackBusy = false),
                            status = "MKV 写回失败：" + (error.message ?: error::class.java.simpleName),
                        )
                    }
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
            if (current.waveform.status != WaveformLiteStatus.READY) {
                launchWaveformAnalysis(uri)
            }
            return
        }
        attachVideo(uri)
    }

    fun attachVideo(uri: Uri) {
        _playbackPositionMs.value = 0L
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
        launchWaveformAnalysis(uri)
    }


    fun buildProjectFile(
        workspaceMode: String,
        workspaceState: List<String>,
        surfaceState: List<String>,
    ): String {
        val snapshot = _state.value
        return WorkbenchProjectCodec.encode(
            WorkbenchProjectFile(
                title = snapshot.project.title,
                canonicalAss = AssCodec.write(snapshot.document),
                subtitleFormat = snapshot.subtitleFormat,
                sourceSubtitleUri = snapshot.project.subtitleUri,
                referenceVideoUri = snapshot.project.videoUri,
                sourceContainerUri = snapshot.container.uri,
                sourceContainerTrack = snapshot.container.selectedTrackNumber,
                workspaceMode = workspaceMode,
                workspaceState = workspaceState,
                surfaceState = surfaceState,
            )
        )
    }

    fun openProjectFile(text: String, projectUri: Uri? = null) {
        val manifest = WorkbenchProjectCodec.decode(text)
        clearPendingRecovery()
        beginWorkspaceBoundary()
        val document = AssCodec.parse(manifest.canonicalAss)
        history.reset(document)
        _state.update { previous ->
            previous.copy(
                project = previous.project.copy(
                    title = manifest.title,
                    subtitleUri = manifest.sourceSubtitleUri,
                    videoUri = manifest.referenceVideoUri,
                ),
                document = document,
                previewDocument = null,
                subtitleLoaded = true,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                subtitleFormat = manifest.subtitleFormat,
                projectFileUri = projectUri?.toString(),
                workspaceMode = manifest.workspaceMode,
                workspaceRestoreState = manifest.workspaceState,
                surfaceRestoreState = manifest.surfaceState,
                workspaceRestoreNonce = previous.workspaceRestoreNonce + 1L,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
                focusedEventId = document.events.firstOrNull()?.id,
                dirty = false,
                canUndo = false,
                canRedo = false,
                fontPackagingSelection = emptySet(),
                container = ContainerBridgeState(),
                status = "已打开 ASS Workbench Project · \${document.events.size} 条事件。" +
                    if (manifest.sourceContainerUri != null) " 原 MKV 关联已记录；写回前需重新打开容器。" else "",
            )
        }
        manifest.referenceVideoUri?.let { raw ->
            runCatching { launchWaveformAnalysis(Uri.parse(raw)) }
        }
        refreshFontDiagnostics()
    }

    fun markProjectFileSaved(uri: Uri) {
        _state.update { it.copy(projectFileUri = uri.toString(), status = "ASS Workbench Project 已保存。") }
    }

    fun openSubtitle(uri: Uri) {
        clearPendingRecovery()
        beginWorkspaceBoundary()
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取字幕")
        val decoded = AssTextDecoder.decode(bytes)
        val name = displayName(uri) ?: "subtitle.ass"
        val format = when {
            name.endsWith(".srt", ignoreCase = true) -> SubtitleDocumentFormat.SRT
            name.endsWith(".vtt", ignoreCase = true) -> SubtitleDocumentFormat.WEBVTT
            else -> SubtitleDocumentFormat.ASS
        }
        val document = when (format) {
            SubtitleDocumentFormat.ASS -> AssCodec.parse(decoded.text)
            SubtitleDocumentFormat.SRT -> SubRipCodec.parse(decoded.text)
            SubtitleDocumentFormat.WEBVTT -> WebVttCodec.parse(decoded.text)
        }
        history.reset(document)
        _state.update {
            it.copy(
                project = it.project.copy(subtitleUri = uri.toString(), title = name),
                document = document,
                previewDocument = null,
                subtitleLoaded = true,
                subtitleTextEncoding = decoded.encoding,
                subtitleFormat = format,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
                focusedEventId = document.events.firstOrNull()?.id,
                dirty = false,
                canUndo = false,
                canRedo = false,
                fontPackagingSelection = emptySet(),
                container = ContainerBridgeState(),
                status = "已载入 ${document.events.size} 条字幕事件 · ${format.name} · ${decoded.encoding.displayName}。",
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
                    val packageableShas = imported.asSequence()
                        .filter { font -> font.origin == FontOrigin.MANUAL }
                        .map { font -> font.sha256 }
                        .toSet()
                    it.copy(
                        importedFonts = imported,
                        fontPackagingSelection = it.fontPackagingSelection.intersect(packageableShas),
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

    fun applyQcQuickFix(issue: AssQcIssue) {
        if (issue.quickFix == null) return
        editDocument("已执行 QC Quick Fix：${issue.quickFix.label}") { doc ->
            AssQualityFixes.apply(doc, issue)
        }
        focusEvent(issue.eventId, seek = false)
    }

    fun reportError(prefix: String, error: Throwable) {
        _state.update {
            it.copy(status = "$prefix：${error.message ?: error::class.java.simpleName}")
        }
    }

    fun saveTo(uri: Uri): Boolean {
        val snapshot = _state.value
        return runCatching {
            val text = when (snapshot.subtitleFormat) {
                SubtitleDocumentFormat.ASS -> AssCodec.write(snapshot.document)
                SubtitleDocumentFormat.SRT -> SubRipCodec.write(snapshot.document)
                SubtitleDocumentFormat.WEBVTT -> WebVttCodec.write(snapshot.document)
            }
            val bytes = snapshot.subtitleTextEncoding.encode(text)
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("无法写入字幕")
        }.fold(
            onSuccess = {
                clearPendingRecovery()
                _state.update {
                    it.copy(
                        project = it.project.copy(subtitleUri = uri.toString()),
                        dirty = false,
                        recoveryAvailable = false,
                        recoveryLabel = "",
                        status = snapshot.subtitleFormat.name + " 已保存。",
                    )
                }
                true
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        dirty = true,
                        status = "字幕保存失败：" + (error.message ?: error::class.java.simpleName) +
                            "；未保存状态与恢复记录已保留。",
                    )
                }
                false
            },
        )
    }

    fun saveCurrent(): Boolean {
        val uri = _state.value.project.subtitleUri?.let(Uri::parse) ?: return false
        saveTo(uri)
        // This return value means "there is a current destination", not "the write
        // succeeded". A write failure is reported in editor state instead of
        // unexpectedly opening Save As from the toolbar callback.
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
    fun focusPreviousFilteredEvent() {
        focusRelativeFilteredEvent(-1)
    }

    fun focusNextFilteredEvent() {
        focusRelativeFilteredEvent(1)
    }

    private fun focusRelativeFilteredEvent(direction: Int) {
        val snapshot = _state.value
        val events = snapshot.filteredEvents
        if (events.isEmpty()) {
            _state.update { it.copy(status = "当前筛选没有可导航的字幕。") }
            return
        }
        val currentIndex = events.indexOfFirst { it.id == snapshot.focusedEventId }
        val targetIndex = when {
            currentIndex < 0 && direction >= 0 -> 0
            currentIndex < 0 -> events.lastIndex
            else -> (currentIndex + direction).coerceIn(0, events.lastIndex)
        }
        focusEvent(events[targetIndex].id, seek = true)
    }

    fun alignSelectedStartToPlayback() {
        val snapshot = _state.value
        val ids = snapshot.selectedEventIds
        if (ids.isEmpty()) return
        val selected = snapshot.document.events.filter { it.id in ids }
        val earliest = selected.minOfOrNull { it.start.millis } ?: return
        val delta = _playbackPositionMs.value - earliest
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

    fun previewFocusedPosition(x: Double, y: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val nx = x.coerceIn(0.0, state.document.playResX.toDouble())
        val ny = y.coerceIn(0.0, state.document.playResY.toDouble())
        val preview = withEventPosition(state.document, id, nx, ny)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setFocusedPosition(x: Double, y: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val nx = x.coerceIn(0.0, state.document.playResX.toDouble())
        val ny = y.coerceIn(0.0, state.document.playResY.toDouble())
        editDocument("已设置当前字幕位置。") { doc ->
            withEventPosition(doc, id, nx, ny)
        }
    }

    private fun withEventPosition(document: AssDocument, id: Long, x: Double, y: Double): AssDocument {
        if (!x.isFinite() || !y.isFinite()) return document
        val nx = x.coerceIn(0.0, document.playResX.toDouble())
        val ny = y.coerceIn(0.0, document.playResY.toDouble())
        return document.copy(events = document.events.map { event ->
            if (event.id != id) event
            else event.copy(text = AssGeometrySemantic.patchPosition(event.text, nx, ny))
        })
    }

    fun previewFocusedMove(startX: Double, startY: Double, endX: Double, endY: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val preview = withEventMove(
            document = state.document,
            id = id,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
        )
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setFocusedMove(startX: Double, startY: Double, endX: Double, endY: Double) {
        val id = _state.value.focusedEventId ?: return
        editDocument("已更新当前字幕的 \\move 路径。") { doc ->
            withEventMove(
                document = doc,
                id = id,
                startX = startX,
                startY = startY,
                endX = endX,
                endY = endY,
            )
        }
    }

    private fun withEventMove(
        document: AssDocument,
        id: Long,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
    ): AssDocument {
        if (listOf(startX, startY, endX, endY).any { !it.isFinite() }) return document
        val event = document.events.firstOrNull { it.id == id } ?: return document
        val move = AssGeometrySemantic.inspect(event.text).move ?: return document
        val sx = startX.coerceIn(0.0, document.playResX.toDouble())
        val sy = startY.coerceIn(0.0, document.playResY.toDouble())
        val ex = endX.coerceIn(0.0, document.playResX.toDouble())
        val ey = endY.coerceIn(0.0, document.playResY.toDouble())
        val patched = AssGeometrySemantic.patchMove(
            text = event.text,
            start = io.github.assworkbench.domain.AssPoint(sx, sy),
            end = io.github.assworkbench.domain.AssPoint(ex, ey),
            startMs = move.startMs,
            endMs = move.endMs,
        )
        if (patched == event.text) return document
        return document.copy(events = document.events.map { candidate ->
            if (candidate.id == id) candidate.copy(text = patched) else candidate
        })
    }

    fun previewFocusedOrigin(x: Double, y: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val preview = withEventOrigin(state.document, id, x, y)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setFocusedOrigin(x: Double, y: Double) {
        val id = _state.value.focusedEventId ?: return
        editDocument("已更新当前字幕的 \\org 变换原点。") { doc ->
            withEventOrigin(doc, id, x, y)
        }
    }

    fun clearFocusedOrigin() {
        val id = _state.value.focusedEventId ?: return
        editDocument("已移除当前字幕的 \\org 变换原点。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event
                else event.copy(text = AssGeometrySemantic.removeOrigin(event.text))
            })
        }
    }

    private fun withEventOrigin(document: AssDocument, id: Long, x: Double, y: Double): AssDocument {
        if (!x.isFinite() || !y.isFinite()) return document
        val safeLimit = 100_000.0
        val event = document.events.firstOrNull { it.id == id } ?: return document
        val patched = AssGeometrySemantic.patchOrigin(
            event.text,
            x.coerceIn(-safeLimit, safeLimit),
            y.coerceIn(-safeLimit, safeLimit),
        )
        if (patched == event.text) return document
        return document.copy(events = document.events.map { candidate ->
            if (candidate.id == id) candidate.copy(text = patched) else candidate
        })
    }

    fun previewFocusedRotationZ(angle: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val preview = withEventRotationZ(state.document, id, angle)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setFocusedRotationZ(angle: Double) {
        val id = _state.value.focusedEventId ?: return
        editDocument("已更新当前字幕的 Z 轴旋转。") { doc ->
            withEventRotationZ(doc, id, angle)
        }
    }

    fun clearFocusedRotationZ() {
        val id = _state.value.focusedEventId ?: return
        editDocument("已恢复当前字幕的 Style 旋转。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event
                else event.copy(text = AssGeometrySemantic.removeRotationZ(event.text))
            })
        }
    }

    private fun withEventRotationZ(document: AssDocument, id: Long, angle: Double): AssDocument {
        if (!angle.isFinite()) return document
        val event = document.events.firstOrNull { it.id == id } ?: return document
        val patched = AssGeometrySemantic.patchRotationZ(event.text, angle.coerceIn(-3600.0, 3600.0))
        if (patched == event.text) return document
        return document.copy(events = document.events.map { candidate ->
            if (candidate.id == id) candidate.copy(text = patched) else candidate
        })
    }

    fun setGeometryScaleLocked(locked: Boolean) {
        _state.update { it.copy(geometryScaleLocked = locked) }
    }

    fun previewFocusedScale(scaleX: Double, scaleY: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val preview = withEventScale(state.document, id, scaleX, scaleY)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setFocusedScale(scaleX: Double, scaleY: Double) {
        val id = _state.value.focusedEventId ?: return
        editDocument("已更新当前字幕的 X / Y 缩放。") { doc ->
            withEventScale(doc, id, scaleX, scaleY)
        }
    }

    fun clearFocusedScale() {
        val id = _state.value.focusedEventId ?: return
        editDocument("已恢复当前字幕的 Style 缩放。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event
                else event.copy(text = AssGeometrySemantic.removeScale(event.text))
            })
        }
    }

    private fun withEventScale(
        document: AssDocument,
        id: Long,
        scaleX: Double,
        scaleY: Double,
    ): AssDocument {
        if (!scaleX.isFinite() || !scaleY.isFinite()) return document
        val event = document.events.firstOrNull { it.id == id } ?: return document
        val sx = scaleX.coerceIn(1.0, 1000.0)
        val sy = scaleY.coerceIn(1.0, 1000.0)
        val patched = AssGeometrySemantic.patchScale(event.text, sx, sy)
        if (patched == event.text) return document
        return document.copy(events = document.events.map { candidate ->
            if (candidate.id == id) candidate.copy(text = patched) else candidate
        })
    }

    fun previewFocusedShear(shearX: Double, shearY: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val preview = withEventShear(state.document, id, shearX, shearY)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setFocusedShear(shearX: Double, shearY: Double) {
        val id = _state.value.focusedEventId ?: return
        editDocument("已更新当前字幕的 X / Y 错切。") { doc ->
            withEventShear(doc, id, shearX, shearY)
        }
    }

    fun clearFocusedShear() {
        val id = _state.value.focusedEventId ?: return
        editDocument("已清除当前字幕的 X / Y 错切。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event
                else event.copy(text = AssGeometrySemantic.removeShear(event.text))
            })
        }
    }

    private fun withEventShear(
        document: AssDocument,
        id: Long,
        shearX: Double,
        shearY: Double,
    ): AssDocument {
        if (!shearX.isFinite() || !shearY.isFinite()) return document
        val event = document.events.firstOrNull { it.id == id } ?: return document
        val fx = shearX.coerceIn(-10.0, 10.0)
        val fy = shearY.coerceIn(-10.0, 10.0)
        val patched = AssGeometrySemantic.patchShear(event.text, fx, fy)
        if (patched == event.text) return document
        return document.copy(events = document.events.map { candidate ->
            if (candidate.id == id) candidate.copy(text = patched) else candidate
        })
    }

    fun previewFocusedRectClip(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        inverted: Boolean,
    ) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val preview = withEventRectClip(state.document, id, left, top, right, bottom, inverted)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setFocusedRectClip(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        inverted: Boolean,
    ) {
        val id = _state.value.focusedEventId ?: return
        editDocument(if (inverted) "已更新当前字幕的矩形 \\iclip。" else "已更新当前字幕的矩形 \\clip。") { doc ->
            withEventRectClip(doc, id, left, top, right, bottom, inverted)
        }
    }

    fun clearFocusedClip() {
        val id = _state.value.focusedEventId ?: return
        editDocument("已移除当前字幕的 clip override。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event
                else event.copy(text = AssGeometrySemantic.removeClip(event.text))
            })
        }
    }

    private fun withEventRectClip(
        document: AssDocument,
        id: Long,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        inverted: Boolean,
    ): AssDocument {
        val values = listOf(left, top, right, bottom)
        if (values.any { !it.isFinite() }) return document
        val safeLimit = 100_000.0
        val event = document.events.firstOrNull { it.id == id } ?: return document
        val patched = AssGeometrySemantic.patchRectClip(
            event.text,
            AssClipRect(
                left.coerceIn(-safeLimit, safeLimit),
                top.coerceIn(-safeLimit, safeLimit),
                right.coerceIn(-safeLimit, safeLimit),
                bottom.coerceIn(-safeLimit, safeLimit),
            ),
            inverted,
        )
        if (patched == event.text) return document
        return document.copy(events = document.events.map { candidate ->
            if (candidate.id == id) candidate.copy(text = patched) else candidate
        })
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

    // Workspace-bound geometry mutations use explicit Event identity.
    // A pinned ToolInstance must never mutate global focus only to reach its target.
    fun previewEventPosition(id: Long, x: Double, y: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventPosition(state.document, id, x, y)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setEventPosition(id: Long, x: Double, y: Double) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已设置字幕 #$id 位置。") { doc ->
            withEventPosition(doc, id, x, y)
        }
    }

    fun previewEventMove(id: Long, sx: Double, sy: Double, ex: Double, ey: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventMove(state.document, id, sx, sy, ex, ey)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setEventMove(id: Long, sx: Double, sy: Double, ex: Double, ey: Double) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已更新字幕 #$id 的 \\move 路径。") { doc ->
            withEventMove(doc, id, sx, sy, ex, ey)
        }
    }

    fun previewEventOrigin(id: Long, x: Double, y: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventOrigin(state.document, id, x, y)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setEventOrigin(id: Long, x: Double, y: Double) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已更新字幕 #$id 的 \\org。") { doc ->
            withEventOrigin(doc, id, x, y)
        }
    }

    fun clearEventOrigin(id: Long) {
        editDocument("已移除字幕 #$id 的 \\org。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(text = AssGeometrySemantic.removeOrigin(event.text))
                else event
            })
        }
    }

    fun previewEventRotationZ(id: Long, angle: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventRotationZ(state.document, id, angle)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setEventRotationZ(id: Long, angle: Double) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已更新字幕 #$id 的 Z 轴旋转。") { doc ->
            withEventRotationZ(doc, id, angle)
        }
    }

    fun clearEventRotationZ(id: Long) {
        editDocument("已恢复字幕 #$id 的 Style 旋转。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(text = AssGeometrySemantic.removeRotationZ(event.text))
                else event
            })
        }
    }

    fun previewEventScale(id: Long, scaleX: Double, scaleY: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventScale(state.document, id, scaleX, scaleY)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setEventScale(id: Long, scaleX: Double, scaleY: Double) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已更新字幕 #$id 的 X / Y 缩放。") { doc ->
            withEventScale(doc, id, scaleX, scaleY)
        }
    }

    fun clearEventScale(id: Long) {
        editDocument("已恢复字幕 #$id 的 Style 缩放。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(text = AssGeometrySemantic.removeScale(event.text))
                else event
            })
        }
    }

    fun previewEventShear(id: Long, shearX: Double, shearY: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventShear(state.document, id, shearX, shearY)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setEventShear(id: Long, shearX: Double, shearY: Double) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已更新字幕 #$id 的 X / Y 错切。") { doc ->
            withEventShear(doc, id, shearX, shearY)
        }
    }

    fun clearEventShear(id: Long) {
        editDocument("已清除字幕 #$id 的 X / Y 错切。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(text = AssGeometrySemantic.removeShear(event.text))
                else event
            })
        }
    }

    fun previewEventRectClip(
        id: Long,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        inverted: Boolean,
    ) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventRectClip(state.document, id, left, top, right, bottom, inverted)
        _state.update { current ->
            current.copy(previewDocument = if (preview == state.document) null else preview)
        }
    }

    fun setEventRectClip(
        id: Long,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        inverted: Boolean,
    ) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument(if (inverted) "已更新字幕 #$id 的矩形 \\iclip。" else "已更新字幕 #$id 的矩形 \\clip。") { doc ->
            withEventRectClip(doc, id, left, top, right, bottom, inverted)
        }
    }

    fun clearEventClip(id: Long) {
        editDocument("已移除字幕 #$id 的 clip override。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(text = AssGeometrySemantic.removeClip(event.text))
                else event
            })
        }
    }

    fun setEventAlignment(id: Long, alignment: Int) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已设置字幕 #$id 对齐点。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) {
                    event
                } else {
                    val leading = Regex("""^(?:\{[^}]*\})*""").find(event.text)?.value.orEmpty()
                    val body = event.text.removePrefix(leading)
                    val cleaned = leading
                        .replace(Regex("""\\an[1-9]"""), "")
                        .replace(Regex("""\{\s*\}"""), "")
                    event.copy(
                        text = cleaned + "{\\an" + alignment.coerceIn(1, 9) + "}" + body
                    )
                }
            })
        }
    }

    fun insertEventAtPlayback() {
        val snapshot = _state.value
        val result = AssDocumentEditing.insertAtPlayback(
            document = snapshot.document,
            afterEventId = snapshot.focusedEventId,
            positionMs = _playbackPositionMs.value,
        )
        editDocument("已在当前播放位置添加字幕。") { result.document }
        _state.update {
            it.copy(
                focusedEventId = result.focusedEventId,
                selectedEventIds = result.selectedEventIds,
            )
        }
    }
    fun insertFocusedBefore() {
        val snapshot = _state.value
        val id = snapshot.focusedEventId ?: return
        runCatching { AssDocumentEditing.insertAdjacent(snapshot.document, id, before = true) }
            .onSuccess { result ->
                editDocument("已在当前字幕前插入一条空字幕。") { result.document }
                _state.update { it.copy(focusedEventId = result.focusedEventId, selectedEventIds = emptySet(), selectionAnchorId = null) }
            }
            .onFailure { reportError("前插字幕失败", it) }
    }

    fun insertFocusedAfter() {
        val snapshot = _state.value
        val id = snapshot.focusedEventId ?: return
        runCatching { AssDocumentEditing.insertAdjacent(snapshot.document, id, before = false) }
            .onSuccess { result ->
                editDocument("已在当前字幕后插入一条空字幕。") { result.document }
                _state.update { it.copy(focusedEventId = result.focusedEventId, selectedEventIds = emptySet(), selectionAnchorId = null) }
            }
            .onFailure { reportError("后插字幕失败", it) }
    }

    fun duplicateFocusedEvent() {
        val snapshot = _state.value
        val id = snapshot.focusedEventId ?: return
        runCatching { AssDocumentEditing.duplicateEvent(snapshot.document, id) }
            .onSuccess { result ->
                editDocument("已复制当前字幕。") { result.document }
                _state.update { it.copy(focusedEventId = result.focusedEventId, selectedEventIds = emptySet(), selectionAnchorId = null) }
            }
            .onFailure { reportError("复制字幕失败", it) }
    }

    fun mergeFocusedWithPrevious() { mergeFocusedAdjacent(previous = true) }

    fun mergeFocusedWithNext() { mergeFocusedAdjacent(previous = false) }

    private fun mergeFocusedAdjacent(previous: Boolean) {
        val snapshot = _state.value
        val id = snapshot.focusedEventId ?: return
        runCatching { AssDocumentEditing.mergeAdjacent(snapshot.document, id, previous = previous) }
            .onSuccess { result ->
                editDocument(if (previous) "已与上一条字幕合并。" else "已与下一条字幕合并。") { result.document }
                _state.update { it.copy(focusedEventId = result.focusedEventId, selectedEventIds = emptySet(), selectionAnchorId = null) }
            }
            .onFailure { reportError(if (previous) "与上一条合并失败" else "与下一条合并失败", it) }
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
                splitTimeMs = _playbackPositionMs.value,
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
                    selectedEventIds = emptySet(),
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
        val now = _playbackPositionMs.value
        editDocument("开始时间已设为当前播放位置。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(start = SubTime(now.coerceAtMost(event.end.millis)))
            })
        }
    }

    fun setFocusedEndToPlayback() {
        val id = _state.value.focusedEventId ?: return
        val now = _playbackPositionMs.value
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
        if ((mode == EventFormatPasteMode.STYLE || mode == EventFormatPasteMode.ALL) &&
            snapshot.document.styles.none { it.name == clipboard.style }
        ) {
            _state.update {
                it.copy(status = "无法粘贴 Style ${clipboard.style}：当前工程不存在该 Style；可改用 Margins / Position / Effects / Overrides。")
            }
            return
        }
        editDocument("已粘贴格式到 " + ids.size + " 条字幕。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id in ids) EventFormatClipboardOps.apply(event, clipboard, mode) else event
            })
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
        val snapshot = _state.value
        val ids = snapshot.selectedEventIds
        if (ids.isEmpty() || deltaMs == 0L) return
        val earliest = snapshot.document.events.asSequence()
            .filter { it.id in ids }
            .minOfOrNull { it.start.millis } ?: return
        val effectiveDelta = deltaMs.coerceAtLeast(-earliest)
        if (effectiveDelta == 0L) return
        editDocument("已将 " + ids.size + " 条字幕平移 " + effectiveDelta + " ms。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id !in ids) event else event.copy(
                    start = SubTime(event.start.millis + effectiveDelta),
                    end = SubTime(event.end.millis + effectiveDelta),
                )
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

    fun toggleFontPackaging(sha256: String) {
        _state.update { state ->
            val candidate = state.importedFonts.firstOrNull {
                it.sha256 == sha256 && it.origin == FontOrigin.MANUAL
            } ?: return@update state
            val embeddedShas = state.importedFonts.asSequence()
                .filter { it.origin == FontOrigin.MKV_ATTACHMENT }
                .map { it.sha256 }
                .toSet()
            if (candidate.sha256 in embeddedShas) {
                return@update state.copy(status = "该字体已经作为当前 MKV 附件存在，无需重复封入。")
            }
            val next = state.fontPackagingSelection.toMutableSet()
            if (!next.add(candidate.sha256)) next.remove(candidate.sha256)
            state.copy(
                fontPackagingSelection = next,
                status = if (candidate.sha256 in next) {
                    "已选择字体用于下一次 MKV 写回：" + candidate.metadata.family
                } else {
                    "已取消 MKV 字体打包：" + candidate.metadata.family
                },
            )
        }
    }

    fun selectRequestedFontsForPackaging() {
        _state.update { state ->
            if (state.container.uri == null) {
                return@update state.copy(status = "请先打开 MKV 工程，再选择需要随写回封入的字体。")
            }
            val requested = FontBindingRewriter.requestedFamilies(state.document)
            val selected = FontPackagingPlanner.selectRequested(
                state.importedFonts,
                requested,
            )
            state.copy(
                fontPackagingSelection = selected,
                status = "已按当前 ASS 字体请求选择 " + selected.size + " 个可封入字体。",
            )
        }
    }

    fun clearFontPackagingSelection() {
        _state.update {
            it.copy(
                fontPackagingSelection = emptySet(),
                status = "已清空 MKV 字体打包选择。",
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

    fun updateStylePosition(
        styleName: String,
        alignment: Int,
        marginL: Int,
        marginR: Int,
        marginV: Int,
    ) {
        editDocument("已更新 Style " + styleName + " 的位置字段。") { doc ->
            if (doc.styles.none { it.name == styleName }) return@editDocument doc
            doc.copy(styles = doc.styles.map { style ->
                if (style.name != styleName) style else style.copy(
                    alignment = alignment.coerceIn(1, 9),
                    marginL = marginL.coerceIn(0, 9999),
                    marginR = marginR.coerceIn(0, 9999),
                    marginV = marginV.coerceIn(0, 9999),
                )
            })
        }
    }

    fun previewStyleTypography(
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
        val before = _state.value.document
        val preview = withStyleTypography(
            document = before,
            styleName = styleName,
            fontSize = fontSize,
            bold = bold,
            italic = italic,
            underline = underline,
            strikeOut = strikeOut,
            spacing = spacing,
            outline = outline,
            shadow = shadow,
            alignment = alignment,
            marginL = marginL,
            marginR = marginR,
            marginV = marginV,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            outlineColor = outlineColor,
            backColor = backColor,
            scaleX = scaleX,
            scaleY = scaleY,
            angle = angle,
            borderStyle = borderStyle,
            encoding = encoding,
        )
        _state.update { state ->
            state.copy(previewDocument = if (preview == before) null else preview)
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
            withStyleTypography(
                document = doc,
                styleName = styleName,
                fontSize = fontSize,
                bold = bold,
                italic = italic,
                underline = underline,
                strikeOut = strikeOut,
                spacing = spacing,
                outline = outline,
                shadow = shadow,
                alignment = alignment,
                marginL = marginL,
                marginR = marginR,
                marginV = marginV,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                outlineColor = outlineColor,
                backColor = backColor,
                scaleX = scaleX,
                scaleY = scaleY,
                angle = angle,
                borderStyle = borderStyle,
                encoding = encoding,
            )
        }
    }

    private fun withStyleTypography(
        document: AssDocument,
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
    ): AssDocument {
        if (document.styles.none { it.name == styleName }) return document
        return document.copy(styles = document.styles.map { style ->
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

    fun updateEventText(id: Long, text: String) {
        editDocument("已修改字幕 #" + id + "。") { doc ->
            doc.copy(events = doc.events.map { if (it.id == id) it.copy(text = text) else it })
        }
    }

    fun previewEventVisualEffects(id: Long, blur: Double?, softEntry: Boolean) {
        val before = _state.value.document
        val preview = withEventVisualEffects(before, id, blur, softEntry)
        _state.update { state -> state.copy(previewDocument = if (preview == before) null else preview) }
    }

    fun applyEventVisualEffects(id: Long, blur: Double?, softEntry: Boolean) {
        editDocument("已更新字幕 #" + id + " 的 Blur / Soft Entry。") { doc ->
            withEventVisualEffects(doc, id, blur, softEntry)
        }
    }

    private fun withEventVisualEffects(document: AssDocument, id: Long, blur: Double?, softEntry: Boolean): AssDocument =
        document.copy(events = document.events.map { event ->
            if (event.id != id) event else event.copy(
                text = EventOverrideEditor.updateVisualEffects(event.text, blur, softEntry),
            )
        })

    fun setEventSimpleFade(id: Long, fadeInMs: Int, fadeOutMs: Int) {
        editDocument("已设置字幕 #" + id + " 的 \\fad。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(
                    text = AssAnimationSemantic.patchSimpleFade(event.text, fadeInMs, fadeOutMs),
                )
            })
        }
    }

    fun setEventComplexFade(id: Long, fade: AssComplexFade) {
        editDocument("已设置字幕 #" + id + " 的 \\fade。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(
                    text = AssAnimationSemantic.patchComplexFade(event.text, fade),
                )
            })
        }
    }

    fun clearEventFade(id: Long) {
        editDocument("已移除字幕 #" + id + " 的 Fade。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(text = AssAnimationSemantic.removeFade(event.text))
            })
        }
    }

    fun previewEventTransform(id: Long, index: Int, transform: AssTransform) {
        val before = _state.value.document
        val preview = before.copy(events = before.events.map { event ->
            if (event.id != id) event else event.copy(
                text = AssAnimationSemantic.patchTransform(event.text, index, transform),
            )
        })
        _state.update { state ->
            state.copy(previewDocument = if (preview == before) null else preview)
        }
    }

    fun setEventTransform(id: Long, index: Int, transform: AssTransform) {
        editDocument("已更新字幕 #" + id + " 的 Transform #" + (index + 1) + "。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(
                    text = AssAnimationSemantic.patchTransform(event.text, index, transform),
                )
            })
        }
    }

    fun addEventTransform(id: Long, transform: AssTransform) {
        editDocument("已为字幕 #" + id + " 添加 Transform。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(
                    text = AssAnimationSemantic.appendTransform(event.text, transform),
                )
            })
        }
    }

    fun removeEventTransform(id: Long, index: Int) {
        editDocument("已移除字幕 #" + id + " 的 Transform #" + (index + 1) + "。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != id) event else event.copy(
                    text = AssAnimationSemantic.removeTransform(event.text, index),
                )
            })
        }
    }
    fun nudgeEventPosition(id: Long, dx: Double, dy: Double) {
        val state = _state.value
        val event = state.document.events.firstOrNull { it.id == id } ?: return
        val geometry = AssGeometrySemantic.inspect(event.text)
        if (geometry.positionMode == io.github.assworkbench.domain.AssPositionMode.MOVE ||
            geometry.positionMode == io.github.assworkbench.domain.AssPositionMode.CONFLICT
        ) return
        val style = state.document.styles.firstOrNull { it.name == event.style }
        val base = eventAnchor(event, style, state.document.playResX, state.document.playResY)
        val x = (geometry.position?.x ?: base.first) + dx
        val y = (geometry.position?.y ?: base.second) + dy
        editDocument("已微调字幕位置。") { doc ->
            withEventPosition(
                document = doc,
                id = id,
                x = x.coerceIn(0.0, state.document.playResX.toDouble()),
                y = y.coerceIn(0.0, state.document.playResY.toDouble()),
            )
        }
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

    fun setPlaybackPosition(positionMs: Long) {
        _playbackPositionMs.value = positionMs.coerceAtLeast(0L)
    }

    fun seekPreviewTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        _playbackPositionMs.value = target
        _state.update {
            it.copy(
                seekRequestMs = target,
                seekRequestNonce = it.seekRequestNonce + 1,
            )
        }
    }

    fun restoreRecovery() {
        val snapshot = recoveryStore.read() ?: run {
            _state.update { it.copy(recoveryAvailable = false, recoveryLabel = "") }
            return
        }
        _playbackPositionMs.value = 0L
        cancelWaveformAnalysis()
        beginWorkspaceBoundary()
        // Keep the journal after Restore. If the process dies again before the
        // user saves or explicitly discards the recovered edit, the same snapshot
        // must still be available on the next launch.
        clearPendingRecovery(clearStored = false)
        history.reset(snapshot.document)
        _state.update {
            it.copy(
                project = snapshot.project,
                document = snapshot.document,
                previewDocument = null,
                waveform = snapshot.project.videoUri?.let { uri ->
                    WaveformLiteState(sourceUri = uri, status = WaveformLiteStatus.IDLE)
                } ?: WaveformLiteState(),
                subtitleLoaded = true,
                subtitleTextEncoding = snapshot.textEncoding,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
                focusedEventId = snapshot.document.events.firstOrNull()?.id,
                dirty = true,
                canUndo = false,
                canRedo = false,
                importedFonts = it.importedFonts.filter { font -> font.origin != FontOrigin.MKV_ATTACHMENT },
                fontPackagingSelection = emptySet(),
                recoveryAvailable = false,
                recoveryLabel = "",
                container = ContainerBridgeState(),
                status = "已恢复上次未保存编辑；MKV 容器写回上下文不会从恢复日志继承。",
            )
        }
        refreshFontDiagnostics()
        snapshot.project.videoUri?.let { launchWaveformAnalysis(Uri.parse(it)) }
    }

    fun discardRecovery() {
        clearPendingRecovery()
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

    fun clearTransientPreview() {
        _state.update { state ->
            if (state.previewDocument == null) state else state.copy(previewDocument = null)
        }
    }

    private inline fun editDocument(status: String, transform: (AssDocument) -> AssDocument) {
        val before = _state.value.document
        val next = transform(before)
        if (next == before) {
            clearTransientPreview()
            return
        }

        history.commit(next)
        publishDocument(next, status, dirty = true)
    }

    private fun publishDocument(document: AssDocument, status: String, dirty: Boolean = true) {
        _state.update { state ->
            val validIds = document.events.mapTo(hashSetOf()) { event -> event.id }
            val selected = state.selectedEventIds.filterTo(linkedSetOf()) { id -> id in validIds }
            val focused = when {
                state.focusedEventId == null -> null
                state.focusedEventId in validIds -> state.focusedEventId
                selected.isNotEmpty() -> selected.first()
                else -> document.events.firstOrNull()?.id
            }
            state.copy(
                document = document,
                previewDocument = null,
                selectedEventIds = selected,
                selectionAnchorId = state.selectionAnchorId?.takeIf { it in validIds },
                focusedEventId = focused,
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
        val snapshot = _state.value
        val project = snapshot.project
        val textEncoding = snapshot.subtitleTextEncoding
        recoveryJob = viewModelScope.launch {
            delay(450)
            withContext(Dispatchers.IO) {
                recoveryStore.write(project, document, textEncoding)
            }
            // The journal exists now, but do not surface the startup recovery
            // prompt inside the same live editing session. On a subsequent process
            // start, initial state derives recoveryAvailable from recoveryStore.exists().

        }
    }

    private fun refreshFonts(initial: Boolean, status: String? = null) {
        val fallback = fontStore.ensureFallbackFont()
        val imported = fontStore.listImported()
        _state.update {
            val packageableShas = FontPackagingPlanner.packageableShas(imported)
            it.copy(
                importedFonts = imported,
                fontPackagingSelection = it.fontPackagingSelection.intersect(packageableShas),
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
                    requestedFamilies = FontBindingRewriter.requestedFamilies(snapshot.document),
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
