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
import io.github.assworkbench.domain.AssRoundTripVerifier
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SrtCodec
import io.github.assworkbench.domain.AssLintIssue
import io.github.assworkbench.domain.AssBatchRecipe
import io.github.assworkbench.domain.AssBatchEngine
import io.github.assworkbench.domain.EventOverrideEditor
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssClipRect
import io.github.assworkbench.domain.AssAnimationSemantic
import io.github.assworkbench.domain.AssComplexFade
import io.github.assworkbench.domain.AssTransform
import io.github.assworkbench.domain.AssSyncAnchor
import io.github.assworkbench.domain.AssSubtitleSynchronizer
import io.github.assworkbench.domain.AssSearchQuery
import io.github.assworkbench.domain.AssSearchReplacement
import io.github.assworkbench.domain.AssSearchReplace
import io.github.assworkbench.domain.AssStyleInheritance
import io.github.assworkbench.domain.AssAnimationKeyframe
import io.github.assworkbench.domain.AssAnimationAuthoring
import io.github.assworkbench.domain.AssTransformVisualProperty
import io.github.assworkbench.domain.AssFxComposition
import io.github.assworkbench.domain.AssReflectionFxSpec
import io.github.assworkbench.domain.AssFlipEntranceSpec
import io.github.assworkbench.domain.AssGlowFxSpec
import io.github.assworkbench.domain.AssFxTemplate
import io.github.assworkbench.domain.AssKaraokeFxAuthoring
import io.github.assworkbench.domain.AssKaraokeRevealFxSpec
import io.github.assworkbench.domain.EventFormatClipboard
import io.github.assworkbench.domain.EventFormatClipboardOps
import io.github.assworkbench.domain.EventFormatPasteMode
import io.github.assworkbench.domain.FontBindingRewriter
import io.github.assworkbench.domain.SubTime
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
    private val fxTemplateStore = StartupProbe.stage(application, "viewmodel_fx_template_store") {
        FxTemplateStore(application)
    }
    private val initialFxTemplateSnapshot = StartupProbe.stage(application, "viewmodel_fx_template_load") {
        fxTemplateStore.load()
    }
    private var recoveryJob: Job? = null
    private var fontDiagnosticJob: Job? = null
    private var waveformJob: Job? = null
    private var sceneCutJob: Job? = null
    private var mediaCatalogJob: Job? = null
    private var containerScanJob: Job? = null
    private var trackImportJob: Job? = null
    private var waveformSourceUri: String? = null
    private var containerScan: MatroskaScanResult? = null
    private var containerBaselineScan: MatroskaScanResult? = null
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

    private val _fxTemplates = MutableStateFlow(initialFxTemplateSnapshot.templates)
    val fxTemplates: StateFlow<List<SavedFxTemplate>> = _fxTemplates.asStateFlow()

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
        trackImportJob?.cancel()
        trackImportJob = null
        containerScan = null
        containerBaselineScan = null
        val epoch = workspaceEpoch.incrementAndGet()
        if (resetProjectFonts) fontStore.beginProjectFontSession(epoch, refresh = false)
        _state.update { it.copy(workspaceSessionId = epoch) }
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
        sceneCutJob?.cancel()
        mediaCatalogJob?.cancel()
        waveformJob = null
        sceneCutJob = null
        mediaCatalogJob = null
        waveformSourceUri = null
    }

    private fun launchMediaAssist(uri: Uri) {
        val source = uri.toString()
        mediaCatalogJob?.cancel()
        sceneCutJob?.cancel()
        mediaCatalogJob = viewModelScope.launch {
            val tracks = runCatching { withContext(Dispatchers.IO) { MediaTrackCatalog.audioTracks(app, uri) } }
                .getOrDefault(emptyList())
            if (_state.value.project.videoUri != source) return@launch
            _state.update { current ->
                val selected = current.selectedAudioTrackIndex?.takeIf { index -> tracks.any { it.extractorIndex == index } }
                    ?: tracks.firstOrNull()?.extractorIndex
                current.copy(audioTracks = tracks, selectedAudioTrackIndex = selected)
            }
            launchWaveformAnalysis(uri)
        }
        sceneCutJob = viewModelScope.launch {
            val cuts = runCatching { withContext(Dispatchers.IO) { SceneCutAnalyzer.analyze(app, uri) } }
                .getOrDefault(emptyList())
            if (_state.value.project.videoUri == source) {
                _state.update { it.copy(sceneCutsMs = cuts) }
            }
        }
    }

    fun selectAudioTrack(extractorIndex: Int) {
        val current = _state.value
        if (current.audioTracks.none { it.extractorIndex == extractorIndex }) return
        _state.update { it.copy(selectedAudioTrackIndex = extractorIndex) }
        current.project.videoUri?.let { launchWaveformAnalysis(Uri.parse(it)) }
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
                    WaveformLiteAnalyzer.loadOrAnalyze(app, uri, _state.value.selectedAudioTrackIndex)
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
                previewOwnerId = null,
                subtitleLoaded = true,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                sourceFormat = SubtitleSourceFormat.ASS,
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
                workspaceSessionId = scanEpoch,
                waveform = WaveformLiteState(
                    sourceUri = uri.toString(),
                    status = WaveformLiteStatus.IDLE,
                ),
                subtitleLoaded = false,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                sourceFormat = SubtitleSourceFormat.ASS,
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
                status = "正在扫描 MKV 容器内容……",
            )
        }
        containerScanJob = viewModelScope.launch {
            var imported = 0
            runCatching {
                withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri) ?: error("无法读取 MKV")
                    val scan = stream.use {
                        MatroskaReader().scan(
                            input = it,
                            retainAttachments = false,
                            onAttachment = { attachment ->
                                if (attachment.isSupportedFont) {
                                    val asset = runCatching {
                                        fontStore.importEmbeddedFont(scanEpoch, attachment.fileName, attachment.data)
                                    }.getOrNull()
                                    if (asset != null) imported++
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
                if (
                    workspaceEpoch.get() != scanEpoch ||
                    _state.value.container.uri != uri.toString()
                ) return@onSuccess
                containerScan = scan
                containerBaselineScan = scan
                val tracks = scan.subtitleTracks.map {
                    ContainerTrackUi(it.number, it.displayName, it.language, it.packets.size)
                }
                _state.update {
                    it.copy(
                        project = it.project.copy(videoUri = uri.toString()),
                        container = it.container.copy(
                            loading = false,
                            tracks = tracks,
                            resources = baselineContainerResources(scan),
                            inventoryEvidence = ContainerInventoryEvidence.BASELINE,
                            extractedFontCount = imported,
                            skippedAttachmentCount = scan.skippedAttachmentCount,
                            error = null,
                        ),
                        status = "MKV：已检测 " + scan.trackInfos.size + " 条轨道、" +
                            scan.attachmentInfos.size + " 个附件" +
                            (if (scan.chapterCount > 0) "、" + scan.chapterCount + " 个章节" else "") +
                            "；可编辑 ASS 轨 " + tracks.size + " 条。",
                    )
                }
                refreshFonts(initial = false)
                if (_state.value.project.videoUri == uri.toString()) {
                    launchMediaAssist(uri)
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
                    launchMediaAssist(uri)
                }
            }
        }
    }

    fun rescanContainer() {
        val snapshot = _state.value
        val uriText = snapshot.container.uri ?: return
        if (snapshot.container.loading || snapshot.container.writeBackBusy) return
        val scanEpoch = workspaceEpoch.get()
        val uri = Uri.parse(uriText)
        containerScanJob?.cancel()
        _state.update {
            it.copy(
                container = it.container.copy(loading = true, error = null),
                status = "正在重新检测 MKV 容器内容……",
            )
        }
        containerScanJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri) ?: error("无法读取 MKV")
                    stream.use {
                        MatroskaReader().scan(
                            input = it,
                            retainAttachments = false,
                        )
                    }
                }
            }.onSuccess { scan ->
                if (
                    workspaceEpoch.get() != scanEpoch ||
                    _state.value.container.uri != uriText
                ) return@onSuccess
                containerScan = scan
                val baseline = containerBaselineScan ?: scan.also { containerBaselineScan = it }
                val tracks = scan.subtitleTracks.map {
                    ContainerTrackUi(it.number, it.displayName, it.language, it.packets.size)
                }
                _state.update { state ->
                    val selected = state.container.selectedTrackNumber?.takeIf { number ->
                        scan.subtitleTracks.any { it.number == number }
                    }
                    state.copy(
                        container = state.container.copy(
                            loading = false,
                            tracks = tracks,
                            resources = diffContainerResources(baseline, scan),
                            inventoryEvidence = ContainerInventoryEvidence.CURRENT_SOURCE,
                            selectedTrackNumber = selected,
                            skippedAttachmentCount = scan.skippedAttachmentCount,
                            error = null,
                        ),
                        status = if (
                            state.container.selectedTrackNumber != null &&
                            selected == null
                        ) {
                            "MKV 已重新检测；当前编辑 ASS 的源轨已不存在，写回前需重新选择轨道。"
                        } else {
                            "MKV 容器内容已重新检测；变化已与首次载入基线比较。"
                        },
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                if (
                    workspaceEpoch.get() != scanEpoch ||
                    _state.value.container.uri != uriText
                ) return@onFailure
                _state.update {
                    it.copy(
                        container = it.container.copy(
                            loading = false,
                            error = error.message ?: "MKV 重新检测失败",
                        ),
                        status = "MKV 重新检测失败：" + (error.message ?: error::class.java.simpleName),
                    )
                }
            }
        }
    }

    fun probeContainerTrackSource(uri: Uri) {
        val snapshot = _state.value
        val containerUri = snapshot.container.uri ?: run {
            _state.update { it.copy(status = "请先打开 MKV 工程，再导入外部轨道。") }
            return
        }
        if (snapshot.container.writeBackBusy || snapshot.container.attachmentExtractBusy) {
            _state.update { it.copy(status = "容器任务进行中；完成后再导入外部轨道。") }
            return
        }
        if (snapshot.container.inventoryEvidence == ContainerInventoryEvidence.VERIFIED_OUTPUT) {
            _state.update { it.copy(status = "当前显示的是已验证输出快照；请先重新打开该 MKV，再继续修改轨道。") }
            return
        }

        val sessionId = snapshot.workspaceSessionId
        val sourceName = displayName(uri)
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
            ?: "外部 Matroska"
        trackImportJob?.cancel()
        _state.update {
            it.copy(
                container = it.container.copy(
                    trackImportLoading = true,
                    trackImportCandidates = emptyList(),
                ),
                status = "正在扫描外部 Matroska 轨道……",
            )
        }
        trackImportJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri)
                        ?: error("无法读取外部 Matroska")
                    stream.use {
                        MatroskaReader().scan(
                            input = it,
                            retainAttachments = false,
                        )
                    }
                }
            }.onSuccess { scan ->
                if (
                    _state.value.workspaceSessionId != sessionId ||
                    _state.value.container.uri != containerUri
                ) return@onSuccess
                val candidates = scan.trackInfos.mapNotNull { info ->
                    val kind = info.kind.toContainerResourceKind()
                    if (
                        kind != ContainerResourceKind.VIDEO &&
                        kind != ContainerResourceKind.AUDIO &&
                        kind != ContainerResourceKind.SUBTITLE
                    ) return@mapNotNull null
                    PendingContainerTrackAdditionUi(
                        sourceUri = uri.toString(),
                        sourceName = sourceName,
                        sourceTrackNumber = info.number,
                        sourceTrackUid = info.uid,
                        kind = kind,
                        typeCode = info.typeCode,
                        codecId = info.codecId,
                        name = info.name,
                        language = info.language,
                        isDefault = info.isDefault,
                        isForced = info.isForced,
                    )
                }
                _state.update {
                    it.copy(
                        container = it.container.copy(
                            trackImportLoading = false,
                            trackImportCandidates = candidates,
                        ),
                        status = if (candidates.isEmpty()) {
                            "外部 Matroska 没有可导入的视频 / 音频 / 字幕轨道。"
                        } else {
                            "外部 Matroska 已检测 ${candidates.size} 条可导入轨道；请选择需要加入当前容器的轨道。"
                        },
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                if (
                    _state.value.workspaceSessionId != sessionId ||
                    _state.value.container.uri != containerUri
                ) return@onFailure
                _state.update {
                    it.copy(
                        container = it.container.copy(
                            trackImportLoading = false,
                            trackImportCandidates = emptyList(),
                        ),
                        status = "外部轨道扫描失败：" + (error.message ?: error::class.java.simpleName),
                    )
                }
            }
        }
    }

    fun planContainerTrackAddition(candidate: PendingContainerTrackAdditionUi) {
        _state.update { state ->
            if (state.container.writeBackBusy || state.container.attachmentExtractBusy) {
                return@update state.copy(status = "容器任务进行中；完成后再修改轨道计划。")
            }
            if (state.container.inventoryEvidence == ContainerInventoryEvidence.VERIFIED_OUTPUT) {
                return@update state.copy(status = "当前显示的是已验证输出快照；请先重新打开该 MKV，再继续修改轨道。")
            }
            val exists = state.container.pendingTrackAdditions.any {
                it.sourceUri == candidate.sourceUri &&
                    it.sourceTrackNumber == candidate.sourceTrackNumber
            }
            if (exists) {
                return@update state.copy(status = "该外部源轨已经在待添加列表中。")
            }
            state.copy(
                container = state.container.copy(
                    pendingTrackAdditions = state.container.pendingTrackAdditions + candidate,
                ),
                status = "已计划添加 ${candidate.sourceName} · Track #${candidate.sourceTrackNumber}；保存新 MKV 前不会修改源文件。",
            )
        }
    }

    fun removeContainerTrackAddition(sourceUri: String, sourceTrackNumber: Long) {
        _state.update { state ->
            if (state.container.writeBackBusy) {
                return@update state.copy(status = "MKV 写回进行中；完成后才能修改轨道计划。")
            }
            val next = state.container.pendingTrackAdditions.filterNot {
                it.sourceUri == sourceUri && it.sourceTrackNumber == sourceTrackNumber
            }
            if (next.size == state.container.pendingTrackAdditions.size) state
            else state.copy(
                container = state.container.copy(pendingTrackAdditions = next),
                status = "已从待写入计划移除外部轨道。",
            )
        }
    }

    fun clearContainerTrackImportCandidates() {
        trackImportJob?.cancel()
        trackImportJob = null
        _state.update {
            it.copy(
                container = it.container.copy(
                    trackImportLoading = false,
                    trackImportCandidates = emptyList(),
                ),
            )
        }
    }

    fun addContainerAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val snapshot = _state.value
        if (snapshot.container.uri == null) {
            _state.update { it.copy(status = "请先打开 MKV 工程，再添加容器附件。") }
            return
        }
        if (snapshot.container.writeBackBusy) {
            _state.update { it.copy(status = "MKV 写回进行中；完成后才能修改附件计划。") }
            return
        }
        val next = snapshot.container.pendingAttachments.toMutableList()
        var added = 0
        for (uri in uris) {
            val uriText = uri.toString()
            if (next.any { it.uri == uriText }) continue
            val name = displayName(uri)
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
                ?: "attachment-${next.size + 1}.bin"
            val mimeType = app.contentResolver.getType(uri)
                ?.takeIf { it.isNotBlank() }
                ?: "application/octet-stream"
            val size = runCatching {
                app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.length.takeIf { it >= 0L }
                }
            }.getOrNull()
            next += PendingContainerAttachmentUi(
                uri = uriText,
                name = name,
                mimeType = mimeType,
                sizeBytes = size,
            )
            added++
        }
        _state.update {
            it.copy(
                container = it.container.copy(pendingAttachments = next),
                status = if (added == 0) {
                    "所选附件已经在待写入列表中。"
                } else {
                    "已加入 $added 个待写入 MKV 附件；保存时将与其他容器内容一起无重编码重封装。"
                },
            )
        }
    }

    fun removeContainerAttachment(uri: String) {
        _state.update { state ->
            if (state.container.writeBackBusy) {
                return@update state.copy(status = "MKV 写回进行中；完成后才能修改附件计划。")
            }
            val next = state.container.pendingAttachments.filterNot { it.uri == uri }
            if (next.size == state.container.pendingAttachments.size) {
                state
            } else {
                state.copy(
                    container = state.container.copy(pendingAttachments = next),
                    status = "已从待写入计划移除附件。",
                )
            }
        }
    }

    fun planExistingAttachmentRemoval(target: String, name: String) {
        if (target.isBlank()) return
        _state.update { state ->
            if (state.container.writeBackBusy) {
                return@update state.copy(status = "MKV 写回进行中；完成后才能修改附件计划。")
            }
            val withoutReplacement = state.container.pendingAttachmentReplacements
                .filterNot { it.target == target }
            val withoutMetadata = state.container.pendingAttachmentMetadataEdits
                .filterNot { it.target == target }
            val next = if (state.container.pendingAttachmentRemovals.any { it.target == target }) {
                state.container.pendingAttachmentRemovals
            } else {
                state.container.pendingAttachmentRemovals + PendingContainerAttachmentRemovalUi(
                    target = target,
                    name = name,
                )
            }
            state.copy(
                container = state.container.copy(
                    pendingAttachmentRemovals = next,
                    pendingAttachmentReplacements = withoutReplacement,
                    pendingAttachmentMetadataEdits = withoutMetadata,
                ),
                status = "已计划删除附件 $name；保存新 MKV 前不会修改源文件。",
            )
        }
    }

    fun cancelExistingAttachmentRemoval(target: String) {
        _state.update { state ->
            val next = state.container.pendingAttachmentRemovals.filterNot { it.target == target }
            if (next.size == state.container.pendingAttachmentRemovals.size) state
            else state.copy(
                container = state.container.copy(pendingAttachmentRemovals = next),
                status = "已取消附件删除计划。",
            )
        }
    }

    fun planExistingAttachmentReplacement(
        target: String,
        originalName: String,
        uri: Uri,
    ) {
        if (target.isBlank()) return
        val snapshot = _state.value
        if (snapshot.container.writeBackBusy) {
            _state.update { it.copy(status = "MKV 写回进行中；完成后才能修改附件计划。") }
            return
        }
        val uriText = uri.toString()
        val name = displayName(uri)
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: originalName
        val mimeType = app.contentResolver.getType(uri)
            ?.takeIf { it.isNotBlank() }
            ?: "application/octet-stream"
        val size = runCatching {
            app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
            }
        }.getOrNull()
        val replacement = PendingContainerAttachmentReplacementUi(
            target = target,
            originalName = originalName,
            uri = uriText,
            name = name,
            mimeType = mimeType,
            sizeBytes = size,
        )
        _state.update { state ->
            state.copy(
                container = state.container.copy(
                    pendingAttachmentRemovals = state.container.pendingAttachmentRemovals
                        .filterNot { it.target == target },
                    pendingAttachmentReplacements = state.container.pendingAttachmentReplacements
                        .filterNot { it.target == target } + replacement,
                    pendingAttachmentMetadataEdits = state.container.pendingAttachmentMetadataEdits
                        .filterNot { it.target == target },
                ),
                status = "已计划替换附件 $originalName → $name；保存新 MKV 前不会修改源文件。",
            )
        }
    }

    fun cancelExistingAttachmentReplacement(target: String) {
        _state.update { state ->
            val next = state.container.pendingAttachmentReplacements.filterNot { it.target == target }
            if (next.size == state.container.pendingAttachmentReplacements.size) state
            else state.copy(
                container = state.container.copy(pendingAttachmentReplacements = next),
                status = "已取消附件替换计划。",
            )
        }
    }

    fun planExistingAttachmentMetadata(
        target: String,
        originalName: String,
        name: String,
        description: String,
    ) {
        val cleanName = name.trim()
        if (target.isBlank() || cleanName.isBlank()) {
            _state.update { it.copy(status = "附件名称不能为空。") }
            return
        }
        if (cleanName.contains('/') || cleanName.contains('\\')) {
            _state.update { it.copy(status = "附件名称必须是文件名，不能包含路径分隔符。") }
            return
        }
        _state.update { state ->
            if (state.container.writeBackBusy) {
                return@update state.copy(status = "MKV 写回进行中；完成后才能修改附件计划。")
            }
            val current = state.container.resources.firstOrNull { it.attachmentTarget == target }
            if (
                current != null &&
                cleanName == current.title &&
                description == current.attachmentDescription
            ) {
                return@update state.copy(
                    container = state.container.copy(
                        pendingAttachmentMetadataEdits = state.container.pendingAttachmentMetadataEdits
                            .filterNot { it.target == target },
                    ),
                    status = "附件信息未变化；没有加入写入计划。",
                )
            }
            val edit = PendingContainerAttachmentMetadataUi(
                target = target,
                originalName = originalName,
                name = cleanName,
                description = description,
            )
            state.copy(
                container = state.container.copy(
                    pendingAttachmentRemovals = state.container.pendingAttachmentRemovals
                        .filterNot { it.target == target },
                    pendingAttachmentReplacements = state.container.pendingAttachmentReplacements
                        .filterNot { it.target == target },
                    pendingAttachmentMetadataEdits = state.container.pendingAttachmentMetadataEdits
                        .filterNot { it.target == target } + edit,
                ),
                status = "已计划修改附件信息 $originalName → $cleanName；保存新 MKV 前不会修改源文件。",
            )
        }
    }

    fun cancelExistingAttachmentMetadata(target: String) {
        _state.update { state ->
            val next = state.container.pendingAttachmentMetadataEdits.filterNot { it.target == target }
            if (next.size == state.container.pendingAttachmentMetadataEdits.size) state
            else state.copy(
                container = state.container.copy(pendingAttachmentMetadataEdits = next),
                status = "已取消附件信息修改计划。",
            )
        }
    }

    fun planExistingTrackRemoval(
        target: String,
        number: Long,
        name: String,
    ) {
        if (target.isBlank()) return
        _state.update { state ->
            if (state.container.writeBackBusy) {
                return@update state.copy(status = "MKV 写回进行中；完成后才能修改轨道计划。")
            }
            if (state.container.inventoryEvidence == ContainerInventoryEvidence.VERIFIED_OUTPUT) {
                return@update state.copy(status = "当前显示的是已验证输出快照；请先重新打开该 MKV，再修改其中轨道。")
            }
            val next = if (state.container.pendingTrackRemovals.any { it.target == target }) {
                state.container.pendingTrackRemovals
            } else {
                state.container.pendingTrackRemovals + PendingContainerTrackRemovalUi(
                    target = target,
                    number = number,
                    name = name,
                )
            }
            state.copy(
                container = state.container.copy(
                    pendingTrackRemovals = next,
                    pendingTrackMetadataEdits = state.container.pendingTrackMetadataEdits
                        .filterNot { it.target == target },
                ),
                status = "已计划删除 Track #$number（$name）；保存新 MKV 前不会修改源文件。",
            )
        }
    }

    fun cancelExistingTrackRemoval(target: String) {
        _state.update { state ->
            val next = state.container.pendingTrackRemovals.filterNot { it.target == target }
            if (next.size == state.container.pendingTrackRemovals.size) state
            else state.copy(
                container = state.container.copy(pendingTrackRemovals = next),
                status = "已取消轨道删除计划。",
            )
        }
    }

    fun planExistingTrackMetadata(
        target: String,
        number: Long,
        originalName: String,
        name: String,
        language: String,
        isDefault: Boolean,
        isForced: Boolean,
    ) {
        if (target.isBlank()) return
        val cleanLanguage = language.trim().lowercase()
        if (cleanLanguage.isNotEmpty() && !cleanLanguage.matches(Regex("[a-z]{3}"))) {
            _state.update {
                it.copy(status = "轨道 Language 当前编辑的是 legacy ISO 639-2 字段；请输入 3 字母代码（例如 jpn / eng / und）或留空。")
            }
            return
        }
        _state.update { state ->
            if (state.container.writeBackBusy) {
                return@update state.copy(status = "MKV 写回进行中；完成后才能修改轨道计划。")
            }
            if (state.container.inventoryEvidence == ContainerInventoryEvidence.VERIFIED_OUTPUT) {
                return@update state.copy(status = "当前显示的是已验证输出快照；请先重新打开该 MKV，再修改其中轨道。")
            }
            val current = state.container.resources.firstOrNull { it.trackTarget == target }
            if (
                current != null &&
                name == current.trackName &&
                cleanLanguage == current.trackLanguage &&
                isDefault == current.trackIsDefault &&
                isForced == current.trackIsForced
            ) {
                return@update state.copy(
                    container = state.container.copy(
                        pendingTrackMetadataEdits = state.container.pendingTrackMetadataEdits
                            .filterNot { it.target == target },
                    ),
                    status = "轨道信息未变化；没有加入写入计划。",
                )
            }
            val edit = PendingContainerTrackMetadataUi(
                target = target,
                number = number,
                originalName = originalName,
                name = name,
                language = cleanLanguage,
                isDefault = isDefault,
                isForced = isForced,
            )
            state.copy(
                container = state.container.copy(
                    pendingTrackRemovals = state.container.pendingTrackRemovals
                        .filterNot { it.target == target },
                    pendingTrackMetadataEdits = state.container.pendingTrackMetadataEdits
                        .filterNot { it.target == target } + edit,
                ),
                status = "已计划修改 Track #$number 信息；保存新 MKV 前不会修改源文件。",
            )
        }
    }

    fun cancelExistingTrackMetadata(target: String) {
        _state.update { state ->
            val next = state.container.pendingTrackMetadataEdits.filterNot { it.target == target }
            if (next.size == state.container.pendingTrackMetadataEdits.size) state
            else state.copy(
                container = state.container.copy(pendingTrackMetadataEdits = next),
                status = "已取消轨道信息修改计划。",
            )
        }
    }

    fun extractContainerAttachment(
        target: String,
        name: String,
        outputUri: Uri,
    ) {
        val snapshot = _state.value
        val sourceUri = snapshot.container.uri?.let(Uri::parse) ?: run {
            _state.update { it.copy(status = "没有已打开的 MKV 工程。") }
            return
        }
        if (target.isBlank() || snapshot.container.loading || snapshot.container.writeBackBusy ||
            snapshot.container.attachmentExtractBusy
        ) {
            return
        }
        if (!mkvGoTool.isAvailable()) {
            _state.update { it.copy(status = "当前 ABI 没有可用的 MKV 附件提取工具。") }
            return
        }
        val extractSessionId = snapshot.workspaceSessionId
        val operationId = writeBackSerial.incrementAndGet()
        _state.update {
            it.copy(
                container = it.container.copy(attachmentExtractBusy = true),
                status = "正在提取附件 $name……",
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val work = File(app.cacheDir, "container-extract-" + operationId).apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    try {
                        val source = File(work, "source.mkv")
                        app.contentResolver.openInputStream(sourceUri)?.use { input ->
                            source.outputStream().buffered().use { output ->
                                input.copyTo(output, 1024 * 1024)
                            }
                        } ?: error("无法重新读取源 MKV")
                        val extracted = File(work, "attachment.bin")
                        mkvGoTool.extractAttachment(
                            source = source,
                            target = target,
                            output = extracted,
                        )
                        app.contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                            extracted.inputStream().buffered().use { input ->
                                input.copyTo(output, 1024 * 1024)
                            }
                        } ?: error("无法写入附件目标文件")
                        extracted.length()
                    } finally {
                        work.deleteRecursively()
                    }
                }
            }.onSuccess { bytes ->
                if (
                    _state.value.workspaceSessionId != extractSessionId ||
                    _state.value.container.uri != snapshot.container.uri
                ) return@onSuccess
                _state.update {
                    it.copy(
                        container = it.container.copy(attachmentExtractBusy = false),
                        status = "附件 $name 已提取 · ${formatContainerExtractBytes(bytes)}。",
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                if (
                    _state.value.workspaceSessionId != extractSessionId ||
                    _state.value.container.uri != snapshot.container.uri
                ) return@onFailure
                _state.update {
                    it.copy(
                        container = it.container.copy(attachmentExtractBusy = false),
                        status = "附件提取失败：" + (error.message ?: error::class.java.simpleName),
                    )
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
                previewOwnerId = null,
                subtitleLoaded = true,
                subtitleTextEncoding = AssTextEncoding.UTF8,
                sourceFormat = SubtitleSourceFormat.ASS,
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
        if (snapshot.container.attachmentExtractBusy) {
            _state.update { it.copy(status = "附件提取进行中；完成后再保存 MKV。") }
            return
        }
        if (snapshot.container.trackImportLoading) {
            _state.update { it.copy(status = "外部轨道扫描进行中；完成后再保存 MKV。") }
            return
        }
        val saveEpoch = workspaceEpoch.get()
        val sourceScanSnapshot = containerScan
        val baselineScanSnapshot = containerBaselineScan ?: sourceScanSnapshot
        val operationId = writeBackSerial.incrementAndGet()
        val sourceUri = snapshot.container.uri?.let(Uri::parse) ?: run {
            reportError("MKV 写回失败", IllegalStateException("没有已打开的 MKV 工程"))
            return
        }
        val runtimeWriterAvailable = mkvGoTool.isAvailable()
        val planState = if (snapshot.container.writeBackAvailable == runtimeWriterAvailable) {
            snapshot
        } else {
            snapshot.copy(
                container = snapshot.container.copy(writeBackAvailable = runtimeWriterAvailable),
            )
        }
        val editPlan = buildContainerEditPlan(planState)
        if (editPlan.mutations.isEmpty()) {
            reportError("MKV 写回失败", IllegalStateException("没有待写入的容器修改"))
            return
        }
        editPlan.blockingChecks.firstOrNull()?.let { blocking ->
            reportError(
                "MKV 写回失败",
                IllegalStateException(blocking.title + "：" + blocking.detail),
            )
            return
        }

        val trackNumber = snapshot.container.selectedTrackNumber
        val replaceAss = editPlan.mutations.any {
            it.kind == ContainerMutationKind.REPLACE_ASS_TRACK
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
        val plannedAttachments = snapshot.container.pendingAttachments
        val plannedRemovals = snapshot.container.pendingAttachmentRemovals
        val plannedReplacements = snapshot.container.pendingAttachmentReplacements
        val plannedMetadataEdits = snapshot.container.pendingAttachmentMetadataEdits
        val plannedTrackRemovals = snapshot.container.pendingTrackRemovals
        val plannedTrackMetadataEdits = snapshot.container.pendingTrackMetadataEdits
        val plannedTrackAdditions = snapshot.container.pendingTrackAdditions

        val plannedAttachmentCount = editPlan.mutations.count {
            it.kind == ContainerMutationKind.ADD_ATTACHMENT
        }
        val plannedRemovalCount = editPlan.mutations.count {
            it.kind == ContainerMutationKind.REMOVE_ATTACHMENT
        }
        _state.update {
            it.copy(
                container = it.container.copy(writeBackBusy = true),
                status = buildString {
                    append("容器预检通过 · 正在无重编码更新 MKV")
                    if (replaceAss) append(" · ASS")
                    if (packageAssets.isNotEmpty()) append(" · 字体 ").append(packageAssets.size)
                    if (plannedAttachments.isNotEmpty()) append(" · 新附件 ").append(plannedAttachments.size)
                    if (plannedRemovals.isNotEmpty()) append(" · 删除 ").append(plannedRemovals.size)
                    if (plannedReplacements.isNotEmpty()) append(" · 替换 ").append(plannedReplacements.size)
                    if (plannedMetadataEdits.isNotEmpty()) append(" · 附件信息 ").append(plannedMetadataEdits.size)
                    if (plannedTrackAdditions.isNotEmpty()) append(" · 加轨 ").append(plannedTrackAdditions.size)
                    if (plannedTrackRemovals.isNotEmpty()) append(" · 删轨 ").append(plannedTrackRemovals.size)
                    if (plannedTrackMetadataEdits.isNotEmpty()) append(" · 轨道信息 ").append(plannedTrackMetadataEdits.size)
                    append("；大文件可能需要一些时间……")
                },
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

                        val result = File(work, "updated.mkv")
                        val packageFiles = packageAssets.map { asset ->
                            fontStore.fileFor(asset)
                                ?: error("找不到待封入字体文件：" + asset.fileName)
                        }
                        val attachmentFiles = plannedAttachments.mapIndexed { index, attachment ->
                            val safeName = attachment.name
                                .substringAfterLast('/')
                                .substringAfterLast('\\')
                                .takeIf { it.isNotBlank() && it != "." && it != ".." }
                                ?: "attachment-${index + 1}.bin"
                            val dir = File(work, "attachment-$index").apply { mkdirs() }
                            val target = File(dir, safeName)
                            app.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { input ->
                                target.outputStream().buffered().use { output ->
                                    input.copyTo(output, 1024 * 1024)
                                }
                            } ?: error("无法读取待封入附件：" + attachment.name)
                            require(target.length() > 0L) { "待封入附件为空：" + attachment.name }
                            target
                        }
                        val replacementInputs = plannedReplacements.mapIndexed { index, replacement ->
                            val safeName = replacement.name
                                .substringAfterLast('/')
                                .substringAfterLast('\\')
                                .takeIf { it.isNotBlank() && it != "." && it != ".." }
                                ?: "replacement-${index + 1}.bin"
                            val dir = File(work, "replacement-$index").apply { mkdirs() }
                            val targetFile = File(dir, safeName)
                            app.contentResolver.openInputStream(Uri.parse(replacement.uri))?.use { input ->
                                targetFile.outputStream().buffered().use { output ->
                                    input.copyTo(output, 1024 * 1024)
                                }
                            } ?: error("无法读取附件替换文件：" + replacement.name)
                            require(targetFile.length() > 0L) { "附件替换文件为空：" + replacement.name }
                            AttachmentReplacementInput(
                                target = replacement.target,
                                file = targetFile,
                            )
                        }
                        val metadataInputs = plannedMetadataEdits.map { metadata ->
                            AttachmentMetadataEditInput(
                                target = metadata.target,
                                name = metadata.name,
                                description = metadata.description,
                            )
                        }
                        val trackMetadataInputs = plannedTrackMetadataEdits.map { metadata ->
                            TrackMetadataEditInput(
                                target = metadata.target,
                                name = metadata.name,
                                language = metadata.language,
                                isDefault = metadata.isDefault,
                                isForced = metadata.isForced,
                            )
                        }
                        val trackSourceFiles = mutableMapOf<String, File>()
                        val trackAdditionInputs = plannedTrackAdditions.map { addition ->
                            val sourceFile = trackSourceFiles.getOrPut(addition.sourceUri) {
                                val target = File(work, "track-source-${trackSourceFiles.size}.mkv")
                                app.contentResolver.openInputStream(Uri.parse(addition.sourceUri))?.use { input ->
                                    target.outputStream().buffered().use { output ->
                                        input.copyTo(output, 1024 * 1024)
                                    }
                                } ?: error("无法读取外部轨道来源：" + addition.sourceName)
                                require(target.length() > 0L) {
                                    "外部轨道来源为空：" + addition.sourceName
                                }
                                target
                            }
                            TrackAdditionInput(
                                source = sourceFile,
                                sourceTrackNumber = addition.sourceTrackNumber,
                                name = addition.name,
                                language = addition.language,
                                isDefault = addition.isDefault,
                                isForced = addition.isForced,
                            )
                        }

                        if (replaceAss) {
                            val editedAss = File(work, "edited.ass")
                            editedAss.writeText(AssCodec.write(snapshot.document), Charsets.UTF_8)
                            mkvGoTool.replaceAss(
                                source = source,
                                trackNumber = requireNotNull(trackNumber),
                                editedAss = editedAss,
                                output = result,
                                fonts = packageFiles,
                                attachments = attachmentFiles,
                                removeAttachments = plannedRemovals.map { it.target },
                                replaceAttachments = replacementInputs,
                                metadataEdits = metadataInputs,
                                removeTracks = plannedTrackRemovals.map { it.target },
                                trackMetadataEdits = trackMetadataInputs,
                                addTracks = trackAdditionInputs,
                            )
                        } else {
                            mkvGoTool.editContainer(
                                source = source,
                                output = result,
                                additions = packageFiles + attachmentFiles,
                                removals = plannedRemovals.map { it.target },
                                replacements = replacementInputs,
                                metadataEdits = metadataInputs,
                                removeTracks = plannedTrackRemovals.map { it.target },
                                trackMetadataEdits = trackMetadataInputs,
                                addTracks = trackAdditionInputs,
                            )
                        }

                        // Verify the complete remux product before publishing it to
                        // the user-selected destination. The source MKV is never
                        // overwritten by this workflow.
                        val verifiedScan = result.inputStream().buffered().use { input ->
                            MatroskaReader().scan(input, retainAttachments = false)
                        }
                        if (replaceAss) {
                            val verifiedTrack = verifiedScan.subtitleTracks
                                .firstOrNull { it.number == trackNumber }
                                ?: error("写回验证失败：目标 ASS 轨不存在")
                            val verifiedDocument = AssCodec.parse(verifiedTrack.toAss())
                            val roundTrip = AssRoundTripVerifier.compare(
                                AssRoundTripVerifier.snapshot(snapshot.document),
                                AssRoundTripVerifier.snapshot(verifiedDocument),
                            )
                            require(roundTrip.equivalent) {
                                "写回验证失败：" + roundTrip.summary
                            }
                        }
                        sourceScanSnapshot?.let { sourceScan ->
                            verifyContainerTrackMutations(
                                source = sourceScan,
                                output = verifiedScan,
                                removals = plannedTrackRemovals,
                                metadataEdits = plannedTrackMetadataEdits,
                                additions = plannedTrackAdditions,
                            )
                            require(verifiedScan.chapterCount == sourceScan.chapterCount) {
                                "写回验证失败：章节数量发生意外变化"
                            }
                            val removalTargets = plannedRemovals.mapTo(hashSetOf()) { it.target }
                            val replacementTargets = plannedReplacements.mapTo(hashSetOf()) { it.target }
                            val metadataTargets = plannedMetadataEdits.mapTo(hashSetOf()) { it.target }
                            val mutatedTargets = removalTargets + replacementTargets + metadataTargets
                            val verifiedAttachments = verifiedScan.attachmentPreservationKeys().toSet()
                            sourceScan.attachmentInfos.forEach { info ->
                                val target = info.uid?.toString() ?: info.fileName
                                if (target !in mutatedTargets) {
                                    val key = info.uid?.let { "uid:$it" }
                                        ?: info.sha256?.let { "sha256:$it" }
                                        ?: "weak:${info.fileName}\u001f${info.mimeType}\u001f${info.sizeBytes ?: -1L}"
                                    require(key in verifiedAttachments) {
                                        "写回验证失败：未修改附件缺失或身份无法确认：" + info.fileName
                                    }
                                }
                            }
                            plannedRemovals.forEach { removal ->
                                require(
                                    verifiedScan.attachmentInfos.none { info ->
                                        info.uid?.toString() == removal.target ||
                                            (info.uid == null && info.fileName == removal.target)
                                    }
                                ) {
                                    "写回验证失败：计划删除的附件仍存在：" + removal.name
                                }
                            }
                            plannedReplacements.forEach { replacement ->
                                val sourceInfo = sourceScan.attachmentInfos.firstOrNull { info ->
                                    info.uid?.toString() == replacement.target ||
                                        (info.uid == null && info.fileName == replacement.target)
                                } ?: error("写回验证失败：替换目标在源 Inventory 中不存在：" + replacement.originalName)
                                val verified = if (sourceInfo.uid != null) {
                                    verifiedScan.attachmentInfos.firstOrNull { it.uid == sourceInfo.uid }
                                } else {
                                    verifiedScan.attachmentInfos.firstOrNull { it.fileName == replacement.name }
                                }
                                require(verified != null) {
                                    "写回验证失败：替换后的附件不存在：" + replacement.name
                                }
                                require(verified.fileName == replacement.name) {
                                    "写回验证失败：替换附件名称不匹配：" + replacement.name
                                }
                                replacement.sizeBytes?.let { expectedSize ->
                                    require(verified.sizeBytes == expectedSize) {
                                        "写回验证失败：替换附件大小不匹配：" + replacement.name
                                    }
                                }
                            }
                            plannedMetadataEdits.forEach { metadata ->
                                val sourceInfo = sourceScan.attachmentInfos.firstOrNull { info ->
                                    info.uid?.toString() == metadata.target ||
                                        (info.uid == null && info.fileName == metadata.target)
                                } ?: error("写回验证失败：附件信息修改目标不存在：" + metadata.originalName)
                                val verified = if (sourceInfo.uid != null) {
                                    verifiedScan.attachmentInfos.firstOrNull { it.uid == sourceInfo.uid }
                                } else {
                                    verifiedScan.attachmentInfos.firstOrNull { it.fileName == metadata.name }
                                }
                                require(verified != null) {
                                    "写回验证失败：信息修改后的附件不存在：" + metadata.name
                                }
                                require(verified.fileName == metadata.name) {
                                    "写回验证失败：附件名称修改未生效：" + metadata.name
                                }
                                require(verified.description == metadata.description) {
                                    "写回验证失败：附件描述修改未生效：" + metadata.name
                                }
                                require(verified.mimeType == sourceInfo.mimeType) {
                                    "写回验证失败：附件信息修改意外改变 MIME：" + metadata.name
                                }
                                require(verified.sizeBytes == sourceInfo.sizeBytes) {
                                    "写回验证失败：附件信息修改意外改变 payload 大小：" + metadata.name
                                }
                            }
                            val expectedAttachmentCount =
                                sourceScan.attachmentInfos.size + plannedAttachmentCount - plannedRemovalCount
                            require(verifiedScan.attachmentInfos.size == expectedAttachmentCount) {
                                "写回验证失败：预期附件数 $expectedAttachmentCount，实际 ${verifiedScan.attachmentInfos.size}"
                            }
                        }

                        app.contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                            result.inputStream().buffered().use { input -> input.copyTo(output, 1024 * 1024) }
                        } ?: error("无法写入目标 MKV")
                        result.length() to verifiedScan
                    } finally {
                        work.deleteRecursively()
                    }
                }
            }.onSuccess { (bytes, verifiedScan) ->
                val current = _state.value
                val sameProject = sameMkvWorkspace(
                    current,
                    saveEpoch,
                    snapshot.container.uri,
                    snapshot.container.selectedTrackNumber,
                )
                if (!sameProject) return@onSuccess
                val documentSaved = replaceAss && current.document == snapshot.document
                if (documentSaved) clearPendingRecovery()
                val savedAttachmentUris = plannedAttachments.mapTo(hashSetOf()) { it.uri }
                val savedRemovalTargets = plannedRemovals.mapTo(hashSetOf()) { it.target }
                val savedReplacementTargets = plannedReplacements.mapTo(hashSetOf()) { it.target }
                val savedMetadataTargets = plannedMetadataEdits.mapTo(hashSetOf()) { it.target }
                val savedTrackRemovalTargets = plannedTrackRemovals.mapTo(hashSetOf()) { it.target }
                val savedTrackMetadataTargets = plannedTrackMetadataEdits.mapTo(hashSetOf()) { it.target }
                val savedTrackAdditionKeys = plannedTrackAdditions.mapTo(hashSetOf()) {
                    it.sourceUri + "\u0000" + it.sourceTrackNumber
                }
                _state.update { state ->
                    state.copy(
                        dirty = if (documentSaved) false else state.dirty,
                        container = state.container.copy(
                            writeBackBusy = false,
                            pendingAttachments = state.container.pendingAttachments
                                .filterNot { it.uri in savedAttachmentUris },
                            pendingAttachmentRemovals = state.container.pendingAttachmentRemovals
                                .filterNot { it.target in savedRemovalTargets },
                            pendingAttachmentReplacements = state.container.pendingAttachmentReplacements
                                .filterNot { it.target in savedReplacementTargets },
                            pendingAttachmentMetadataEdits = state.container.pendingAttachmentMetadataEdits
                                .filterNot { it.target in savedMetadataTargets },
                            pendingTrackRemovals = state.container.pendingTrackRemovals
                                .filterNot { it.target in savedTrackRemovalTargets },
                            pendingTrackMetadataEdits = state.container.pendingTrackMetadataEdits
                                .filterNot { it.target in savedTrackMetadataTargets },
                            pendingTrackAdditions = state.container.pendingTrackAdditions
                                .filterNot {
                                    (it.sourceUri + "\u0000" + it.sourceTrackNumber) in savedTrackAdditionKeys
                                },
                            trackImportCandidates = emptyList(),
                            resources = baselineScanSnapshot?.let { baseline ->
                                diffContainerResources(baseline, verifiedScan)
                            } ?: baselineContainerResources(verifiedScan),
                            inventoryEvidence = ContainerInventoryEvidence.VERIFIED_OUTPUT,
                        ),
                        recoveryAvailable = if (documentSaved) false else state.recoveryAvailable,
                        recoveryLabel = if (documentSaved) "" else state.recoveryLabel,
                        status = buildString {
                            append("新 MKV 已保存并重新扫描；未删除轨道的身份/顺序、章节与未修改附件已验证。输出 ")
                            append(bytes / (1024 * 1024)).append(" MiB。")
                            if (packageAssets.isNotEmpty()) append(" 新封入字体 ").append(packageAssets.size).append(" 个。")
                            if (plannedAttachments.isNotEmpty()) append(" 新封入附件 ").append(plannedAttachments.size).append(" 个。")
                            if (plannedRemovals.isNotEmpty()) append(" 删除附件 ").append(plannedRemovals.size).append(" 个。")
                            if (plannedReplacements.isNotEmpty()) append(" 替换附件 ").append(plannedReplacements.size).append(" 个。")
                            if (plannedMetadataEdits.isNotEmpty()) append(" 修改附件信息 ").append(plannedMetadataEdits.size).append(" 个。")
                            if (plannedTrackAdditions.isNotEmpty()) append(" 添加轨道 ").append(plannedTrackAdditions.size).append(" 个。")
                            if (plannedTrackRemovals.isNotEmpty()) append(" 删除轨道 ").append(plannedTrackRemovals.size).append(" 个。")
                            if (plannedTrackMetadataEdits.isNotEmpty()) append(" 修改轨道信息 ").append(plannedTrackMetadataEdits.size).append(" 个。")
                            if (replaceAss && !documentSaved) append(" · 保存期间出现新字幕编辑，当前工程仍未保存。")
                        },
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
                launchMediaAssist(uri)
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
                audioTracks = emptyList(),
                selectedAudioTrackIndex = null,
                sceneCutsMs = emptyList(),
                status = if ((displayName(uri) ?: "").endsWith(".mkv", ignoreCase = true)) {
                    "已把 MKV 作为参考视频载入；如需编辑它的内嵌 ASS，请使用顶部 MKV 入口。"
                } else {
                    "已更换参考视频；字幕未修改。"
                },
            )
        }
        launchMediaAssist(uri)
    }

    fun openSubtitle(uri: Uri) {
        clearPendingRecovery()
        beginWorkspaceBoundary()
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取字幕")
        val decoded = AssTextDecoder.decode(bytes)
        val name = displayName(uri) ?: "subtitle"
        val isSrt = name.endsWith(".srt", ignoreCase = true)
        val document = if (isSrt) SrtCodec.parse(decoded.text) else AssCodec.parse(decoded.text)
        history.reset(document)
        _state.update {
            it.copy(
                project = it.project.copy(
                    subtitleUri = if (isSrt) null else uri.toString(),
                    title = name,
                ),
                document = document,
                previewDocument = null,
                previewOwnerId = null,
                subtitleLoaded = true,
                subtitleTextEncoding = decoded.encoding,
                sourceFormat = if (isSrt) SubtitleSourceFormat.SRT else SubtitleSourceFormat.ASS,
                selectedEventIds = emptySet(),
                selectionAnchorId = null,
                focusedEventId = document.events.firstOrNull()?.id,
                dirty = false,
                canUndo = false,
                canRedo = false,
                fontPackagingSelection = emptySet(),
                container = ContainerBridgeState(),
                status = if (isSrt) {
                    "已导入 ${document.events.size} 条 SRT；内部转换为 ASS 工作文档。保存按钮默认另存为 ASS，SRT 使用显式导出。"
                } else {
                    "已载入 ${document.events.size} 条 ASS 事件 · ${decoded.encoding.displayName}。"
                },
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

    fun reportError(prefix: String, error: Throwable) {
        _state.update {
            it.copy(status = "$prefix：${error.message ?: error::class.java.simpleName}")
        }
    }

    fun saveTo(uri: Uri): Boolean {
        val snapshot = _state.value
        return runCatching {
            SafeSubtitleSave.write(
                resolver = app.contentResolver,
                cacheDir = app.cacheDir,
                uri = uri,
                document = snapshot.document,
                encoding = snapshot.subtitleTextEncoding,
            )
        }.fold(
            onSuccess = { result ->
                clearPendingRecovery()
                _state.update {
                    it.copy(
                        project = it.project.copy(subtitleUri = uri.toString()),
                        sourceFormat = SubtitleSourceFormat.ASS,
                        dirty = false,
                        recoveryAvailable = false,
                        recoveryLabel = "",
                        status = "ASS 已安全保存并回读验证 · " +
                            (result.bytesWritten / 1024L).coerceAtLeast(1L) + " KiB · " +
                            result.verifiedEncoding.displayName + "。",
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

    fun saveSrtTo(uri: Uri): Boolean {
        val snapshot = _state.value
        return runCatching {
            val text = SrtCodec.write(snapshot.document)
            app.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                ?: error("无法写入 SRT")
        }.fold(
            onSuccess = {
                _state.update { it.copy(status = "SRT 已导出；ASS 工作文档、Style 与高级效果没有被降格或覆盖。") }
                true
            },
            onFailure = { error ->
                _state.update { it.copy(status = "SRT 导出失败：" + (error.message ?: error::class.java.simpleName)) }
                false
            },
        )
    }

    fun loadProjectSnapshot(snapshot: AssWorkbenchProjectSnapshot): Long {
        clearPendingRecovery()
        val sessionId = beginWorkspaceBoundary()
        cancelWaveformAnalysis()
        history.reset(snapshot.document)
        val eventIds = snapshot.document.events.mapTo(hashSetOf()) { it.id }
        val focused = snapshot.focusedEventId?.takeIf(eventIds::contains)
            ?: snapshot.document.events.firstOrNull()?.id
        _state.update { old ->
            old.copy(
                project = io.github.assworkbench.domain.SubtitleProject(
                    title = snapshot.title,
                    videoUri = snapshot.videoUri,
                    subtitleUri = snapshot.subtitleUri,
                ),
                document = snapshot.document,
                previewDocument = null,
                previewOwnerId = null,
                waveform = WaveformLiteState(
                    sourceUri = snapshot.videoUri,
                    status = if (snapshot.videoUri == null) WaveformLiteStatus.IDLE else WaveformLiteStatus.ANALYZING,
                ),
                subtitleLoaded = true,
                subtitleTextEncoding = snapshot.textEncoding,
                sourceFormat = SubtitleSourceFormat.PROJECT,
                selectedEventIds = snapshot.selectedEventIds.filterTo(linkedSetOf(), eventIds::contains),
                selectionAnchorId = null,
                focusedEventId = focused,
                dirty = false,
                canUndo = false,
                canRedo = false,
                container = snapshot.containerUri?.let { containerUri ->
                    ContainerBridgeState(
                        uri = containerUri,
                        name = snapshot.title,
                        selectedTrackNumber = snapshot.containerTrackNumber,
                        writeBackAvailable = false,
                    )
                } ?: ContainerBridgeState(),
                status = if (snapshot.containerUri != null) {
                    "ASS Workbench Project 已恢复。MKV 来源关系已记录；容器写回需重新打开 MKV Bridge 后再执行。"
                } else {
                    "ASS Workbench Project 已恢复。"
                },
            )
        }
        snapshot.videoUri?.let { launchMediaAssist(Uri.parse(it)) }
        refreshFontDiagnostics()
        return sessionId
    }

    fun applyQuickFix(issue: AssLintIssue) {
        val eventId = issue.eventId ?: return
        val fix = issue.quickFix ?: return
        editDocument("Quick Fix：${issue.code} · ${fix.label}") { document ->
            fix.apply(document, eventId)
        }
    }

    fun applyBatchRecipe(recipe: AssBatchRecipe) {
        val preview = AssBatchEngine.preview(_state.value.document, recipe)
        if (preview.changedEventIds.isEmpty()) {
            _state.update { it.copy(status = "批处理规则没有产生修改。") }
            return
        }
        editDocument("批处理 ${recipe.id}：修改 ${preview.changedEventIds.size} 条字幕。") { preview.document }
    }

    fun applySubtitleSynchronization(
        anchors: List<AssSyncAnchor>,
        selectedOnly: Boolean = false,
    ) {
        val snapshot = _state.value
        val ids = snapshot.selectedEventIds.takeIf { selectedOnly && it.isNotEmpty() }
        val preview = runCatching {
            AssSubtitleSynchronizer.preview(snapshot.document, anchors, ids)
        }.getOrElse { error ->
            _state.update { it.copy(status = "同步参数无效：" + (error.message ?: "unknown")) }
            return
        }
        if (preview.changedEventIds.isEmpty()) {
            _state.update { it.copy(status = "同步映射没有产生修改。") }
            return
        }
        editDocument(
            "高级同步：${preview.anchors.size} 个锚点 · 修改 ${preview.changedEventIds.size} 条字幕。"
        ) { preview.document }
    }

    fun applySearchReplacement(
        query: AssSearchQuery,
        replacement: AssSearchReplacement,
    ) {
        val preview = runCatching {
            AssSearchReplace.preview(_state.value.document, query, replacement)
        }.getOrElse { error ->
            _state.update { it.copy(status = "搜索/替换规则无效：" + (error.message ?: "unknown")) }
            return
        }
        if (preview.changedEventIds.isEmpty()) {
            _state.update { it.copy(status = "搜索命中 ${preview.hits.size} 条，但替换没有产生修改。") }
            return
        }
        editDocument(
            "搜索/替换：命中 ${preview.hits.size} 条 · 修改 ${preview.changedEventIds.size} 条。"
        ) { preview.document }
    }

    fun applyEventNumericAnimation(
        eventId: Long,
        property: AssTransformVisualProperty,
        keyframes: List<AssAnimationKeyframe>,
        accel: Double?,
    ) {
        editDocument("已为字幕 #$eventId 写入关键帧动画 · ${property.tag}。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id != eventId) event else event.copy(
                    text = AssAnimationAuthoring.applyNumericTrack(event.text, property, keyframes, accel)
                )
            })
        }
    }

    fun previewKaraokeRevealFx(
        eventId: Long,
        spec: AssKaraokeRevealFxSpec,
    ) = previewKaraokeRevealFx(setOf(eventId), spec)

    fun previewKaraokeRevealFx(
        eventIds: Set<Long>,
        spec: AssKaraokeRevealFxSpec,
    ) {
        val document = _state.value.document
        val plan = runCatching {
            AssKaraokeFxAuthoring.planProgressiveRevealBatch(document, eventIds, spec)
        }.getOrElse { error ->
            _state.update {
                it.copy(
                    previewDocument = null,
                    previewOwnerId = null,
                    status = "Karaoke FX 预览失败：" + (error.message ?: error::class.java.simpleName),
                )
            }
            return
        }
        _state.update {
            it.copy(
                previewDocument = plan.document,
                previewOwnerId = "karaoke-fx",
                status = "Karaoke FX 预览：${plan.sourceEventIds.size} 条字幕 · ${plan.sourceSegmentCount} 个音节；尚未写入。",
            )
        }
    }

    fun applyKaraokeRevealFx(
        eventId: Long,
        spec: AssKaraokeRevealFxSpec,
    ) = applyKaraokeRevealFx(setOf(eventId), spec)

    fun applyKaraokeRevealFx(
        eventIds: Set<Long>,
        spec: AssKaraokeRevealFxSpec,
    ) {
        val document = _state.value.document
        val plan = runCatching {
            AssKaraokeFxAuthoring.planProgressiveRevealBatch(document, eventIds, spec)
        }.getOrElse { error ->
            _state.update {
                it.copy(status = "Karaoke FX 生成失败：" + (error.message ?: error::class.java.simpleName))
            }
            return
        }

        editDocument(
            "已为 ${plan.sourceEventIds.size} 条字幕写入 ${plan.sourceSegmentCount} 个逐音节显现 FX。"
        ) { plan.document }
    }

    fun saveFxTemplate(template: AssFxTemplate) {
        val snapshot = runCatching { fxTemplateStore.save(template) }.getOrElse { error ->
            _state.update {
                it.copy(status = "保存 FX 模板失败：" + (error.message ?: error::class.java.simpleName))
            }
            return
        }
        _fxTemplates.value = snapshot.templates
        _state.update {
            it.copy(
                status = buildString {
                    append("已保存 FX 模板“").append(template.name).append("”。")
                    if (snapshot.corruptFileCount > 0) {
                        append(" 另有 ").append(snapshot.corruptFileCount).append(" 个损坏模板文件已跳过。")
                    }
                }
            )
        }
    }

    fun deleteFxTemplate(id: String) {
        val snapshot = runCatching { fxTemplateStore.delete(id) }.getOrElse { error ->
            _state.update {
                it.copy(status = "删除 FX 模板失败：" + (error.message ?: error::class.java.simpleName))
            }
            return
        }
        _fxTemplates.value = snapshot.templates
        _state.update {
            it.copy(
                status = buildString {
                    append("已删除 FX 模板。")
                    if (snapshot.corruptFileCount > 0) {
                        append(" 另有 ").append(snapshot.corruptFileCount).append(" 个损坏模板文件已跳过。")
                    }
                }
            )
        }
    }

    fun previewMirrorFxComposition(
        eventIds: Set<Long>,
        reflection: AssReflectionFxSpec,
        glow: AssGlowFxSpec?,
        entrance: AssFlipEntranceSpec?,
    ) {
        val snapshot = _state.value
        val result = runCatching {
            AssFxComposition.composeMirrorStackBatch(
                document = snapshot.document,
                eventIds = eventIds,
                reflection = reflection,
                glow = glow,
                entrance = entrance,
            )
        }.getOrElse { error ->
            _state.update {
                it.copy(
                    previewDocument = null,
                    previewOwnerId = null,
                    status = "FX 预览失败：" + (error.message ?: error::class.java.simpleName),
                )
            }
            return
        }
        _state.update { current ->
            current.copy(
                previewDocument = result.document,
                previewOwnerId = "fx-composition",
                status = "FX 预览：${result.sourceEventIds.size} 条源字幕 · ${result.generatedEventIds.size} 个生成层；尚未写入。",
            )
        }
    }

    fun createMirrorFxComposition(
        eventId: Long,
        reflection: AssReflectionFxSpec,
        glow: AssGlowFxSpec?,
        entrance: AssFlipEntranceSpec?,
    ) = createMirrorFxComposition(
        eventIds = setOf(eventId),
        reflection = reflection,
        glow = glow,
        entrance = entrance,
    )

    fun createMirrorFxComposition(
        eventIds: Set<Long>,
        reflection: AssReflectionFxSpec,
        glow: AssGlowFxSpec?,
        entrance: AssFlipEntranceSpec?,
    ) {
        val snapshot = _state.value
        val result = runCatching {
            AssFxComposition.composeMirrorStackBatch(
                document = snapshot.document,
                eventIds = eventIds,
                reflection = reflection,
                glow = glow,
                entrance = entrance,
            )
        }.getOrElse { error ->
            _state.update {
                it.copy(status = "FX 组合失败：" + (error.message ?: error::class.java.simpleName))
            }
            return
        }

        val status = buildString {
            append("已为 ").append(result.sourceEventIds.size).append(" 条字幕生成 ")
            append(result.generatedEventIds.size).append(" 个 FX Event")
            if (glow != null) append("（柔光 + 倒影）") else append("（倒影）")
            if (entrance != null) append("，并写入主体翻转入场")
            append("。")
        }
        editDocument(status) { result.document }
        _state.update { state ->
            state.copy(
                focusedEventId = result.sourceEventIds.firstOrNull()
                    ?.takeIf { id -> state.document.events.any { it.id == id } },
                selectedEventIds = (result.sourceEventIds + result.generatedEventIds)
                    .filterTo(linkedSetOf()) { id -> state.document.events.any { it.id == id } },
                selectionAnchorId = null,
            )
        }
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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

    private fun withEventRotationX(document: AssDocument, id: Long, angle: Double): AssDocument {
        if (!angle.isFinite()) return document
        val event = document.events.firstOrNull { it.id == id } ?: return document
        val patched = AssGeometrySemantic.patchRotationX(event.text, angle.coerceIn(-3600.0, 3600.0))
        if (patched == event.text) return document
        return document.copy(events = document.events.map { candidate ->
            if (candidate.id == id) candidate.copy(text = patched) else candidate
        })
    }

    private fun withEventRotationY(document: AssDocument, id: Long, angle: Double): AssDocument {
        if (!angle.isFinite()) return document
        val event = document.events.firstOrNull { it.id == id } ?: return document
        val patched = AssGeometrySemantic.patchRotationY(event.text, angle.coerceIn(-3600.0, 3600.0))
        if (patched == event.text) return document
        return document.copy(events = document.events.map { candidate ->
            if (candidate.id == id) candidate.copy(text = patched) else candidate
        })
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

    fun setGeometryScaleSnapStep(step: Double?) {
        val normalized = step?.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(1.0, 100.0)
        _state.update { it.copy(geometryScaleSnapStep = normalized) }
    }

    fun previewFocusedScale(scaleX: Double, scaleY: Double) {
        val state = _state.value
        val id = state.focusedEventId ?: return
        val preview = withEventScale(state.document, id, scaleX, scaleY)
        _state.update { current ->
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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

    fun previewEventRotationX(id: Long, angle: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventRotationX(state.document, id, angle)
        _state.update { current ->
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
        }
    }

    fun setEventRotationX(id: Long, angle: Double) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已更新字幕 #$id 的 X 轴旋转。") { doc ->
            withEventRotationX(doc, id, angle)
        }
    }

    fun clearEventRotationX(id: Long) {
        editDocument("已清除字幕 #$id 的 X 轴旋转。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(text = AssGeometrySemantic.removeRotationX(event.text))
                else event
            })
        }
    }

    fun previewEventRotationY(id: Long, angle: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventRotationY(state.document, id, angle)
        _state.update { current ->
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
        }
    }

    fun setEventRotationY(id: Long, angle: Double) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已更新字幕 #$id 的 Y 轴旋转。") { doc ->
            withEventRotationY(doc, id, angle)
        }
    }

    fun clearEventRotationY(id: Long) {
        editDocument("已清除字幕 #$id 的 Y 轴旋转。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.copy(text = AssGeometrySemantic.removeRotationY(event.text))
                else event
            })
        }
    }

    fun previewEventRotationZ(id: Long, angle: Double) {
        val state = _state.value
        if (state.document.events.none { it.id == id }) return
        val preview = withEventRotationZ(state.document, id, angle)
        _state.update { current ->
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
            current.copy(
                previewDocument = if (preview == state.document) null else preview,
                previewOwnerId = if (preview == state.document) null else "geometry:$id",
            )
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
        clearEventStyleOverrides(id)
    }

    fun clearEventStyleOverrides(id: Long) {
        if (_state.value.document.events.none { it.id == id }) return
        editDocument("已清除字幕 #$id 的直接样式/位置覆盖；Transform 动画等嵌套语义保持不变。") { doc ->
            doc.copy(events = doc.events.map { event ->
                if (event.id == id) event.inheritStyle() else event
            })
        }
    }

    fun clearSelectedStyleOverrides() {
        val ids = _state.value.selectedEventIds
        if (ids.isEmpty()) return
        editDocument("已清除 " + ids.size + " 条选中字幕的直接 Style 覆盖；Transform 动画等嵌套语义保持不变。") { doc ->
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
            state.copy(
                previewDocument = if (preview == before) null else preview,
                previewOwnerId = if (preview == before) null else "style:$styleName",
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
        _state.update { state ->
            state.copy(
                previewDocument = if (preview == before) null else preview,
                previewOwnerId = if (preview == before) null else "effects:$id",
            )
        }
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
            state.copy(
                previewDocument = if (preview == before) null else preview,
                previewOwnerId = if (preview == before) null else "effects:$id",
            )
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

    fun nudgeSelectedObjects(dx: Double, dy: Double) {
        if (!dx.isFinite() || !dy.isFinite()) return
        val snapshot = _state.value
        val ids = snapshot.selectedEventIds
        if (ids.isEmpty()) return
        editDocument("已移动选中字幕对象 " + ids.size + " 条。") { document ->
            var next = document
            ids.forEach { id ->
                val event = next.events.firstOrNull { it.id == id } ?: return@forEach
                val geometry = AssGeometrySemantic.inspect(event.text)
                val style = next.styles.firstOrNull { it.name == event.style }
                val anchor = geometry.position ?: eventAnchor(
                    event = event,
                    style = style,
                    playResX = next.playResX,
                    playResY = next.playResY,
                ).let { io.github.assworkbench.domain.AssPoint(it.first, it.second) }
                next = withEventPosition(
                    document = next,
                    id = id,
                    x = anchor.x + dx,
                    y = anchor.y + dy,
                )
            }
            next
        }
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
                previewOwnerId = null,
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
        snapshot.project.videoUri?.let { launchMediaAssist(Uri.parse(it)) }
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

    fun clearTransientPreview(ownerId: String? = null) {
        _state.update { state ->
            if (
                state.previewDocument == null ||
                (ownerId != null && state.previewOwnerId != ownerId)
            ) {
                state
            } else {
                state.copy(previewDocument = null, previewOwnerId = null)
            }
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
                previewOwnerId = null,
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

    private fun stripInlineStyleOverrides(text: String): String =
        AssStyleInheritance.clearDirectManagedOverrides(text)

    private fun displayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return app.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }
}


private fun formatContainerExtractBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes.toDouble() / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes.toDouble() / 1024.0)
    else -> "$bytes B"
}
