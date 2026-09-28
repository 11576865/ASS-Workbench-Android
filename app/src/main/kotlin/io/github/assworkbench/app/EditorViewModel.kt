package io.github.assworkbench.app

import android.app.Application
import android.net.Uri
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.assworkbench.container.MatroskaReader
import io.github.assworkbench.container.MatroskaScanResult
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssTextDecoder
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.EventOverrideEditor
import io.github.assworkbench.domain.FontBindingRewriter
import io.github.assworkbench.domain.ReviewEventKey
import io.github.assworkbench.domain.ReviewSidecar
import io.github.assworkbench.domain.SubTime
import io.github.assworkbench.domain.TypesettingMath
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
    private val prefs = StartupProbe.stage(application, "viewmodel_prefs") {
        application.getSharedPreferences("ass_workbench_editor", Context.MODE_PRIVATE)
    }
    private val recoveryStore = StartupProbe.stage(application, "viewmodel_recovery_store") {
        RecoveryStore(application)
    }
    private val reviewStateStore = StartupProbe.stage(application, "viewmodel_review_store") {
        ReviewStateStore(application)
    }
    private var recoveryJob: Job? = null
    private var reviewPersistJob: Job? = null
    private var fontDiagnosticJob: Job? = null
    private var containerScan: MatroskaScanResult? = null
    private val _state = MutableStateFlow(
        EditorState(
            project = io.github.assworkbench.domain.SubtitleProject(
                splitRatio = prefs.getFloat("split_ratio", 0.56f).coerceIn(0.28f, 0.78f)
            ),
            recoveryAvailable = recoveryStore.exists(),
            recoveryLabel = recoveryStore.label(),
        )
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private data class RestoredReviewState(
        val sourceStyle: String,
        val targetStyle: String,
        val confirmedIds: Set<Long>,
        val originalTextById: Map<Long, String>,
    )

    init {
        StartupProbe.stage(application, "viewmodel_initial_refresh") {
            refreshFonts(initial = true)
        }
        StartupProbe.mark(application, "viewmodel_constructed", "success")
    }

    fun openMkvProject(uri: Uri) {
        _state.update {
            it.copy(
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
                    if (imported > 0) {
                        fontStore.refreshFontconfig(pruneOldCaches = true)
                    }
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
        val styleNames = document.styles.map { it.name }
        val identity = containerReviewIdentity(it = _state.value, trackNumber = trackNumber)
        val review = restoreReviewSidecar(identity, document, styleNames)
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
                reviewSourceStyle = review.sourceStyle,
                reviewTargetStyle = review.targetStyle,
                originalTextById = review.originalTextById,
                confirmedReviewIds = review.confirmedIds,
                reviewFilter = "all",
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

    fun attachVideo(uri: Uri) {
        _state.update {
            it.copy(
                project = it.project.copy(videoUri = uri.toString()),
                status = "已更换参考视频；字幕未修改。",
            )
        }
    }

    fun openSubtitle(uri: Uri) {
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取字幕")
        val decoded = AssTextDecoder.decode(bytes)
        val document = AssCodec.parse(decoded.text)
        history.reset(document)
        val styleNames = document.styles.map { it.name }
        val review = restoreReviewSidecar(uri.toString(), document, styleNames)
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
                reviewSourceStyle = review.sourceStyle,
                reviewTargetStyle = review.targetStyle,
                originalTextById = review.originalTextById,
                confirmedReviewIds = review.confirmedIds,
                reviewFilter = "all",
                status = "已载入 ${document.events.size} 条 ASS 事件 · ${decoded.encoding.displayName}。",
            )
        }
        refreshFontDiagnostics()
    }

    fun importFont(uri: Uri) {
        importFonts(listOf(uri))
    }

    fun importFonts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val assets = fontStore.importAll(uris)
        val families = assets.map { it.metadata.rendererFamily }.distinct()
        refreshFonts(
            initial = false,
            status = "已导入 " + assets.size + " 个字体文件 · renderer family：" +
                families.take(4).joinToString(", ") +
                if (families.size > 4) " …" else "",
        )
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
        persistReviewSidecar()
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

    fun clearSelection() = _state.update {
        it.copy(selectedEventIds = emptySet(), selectionAnchorId = null, status = "已清除选择。")
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


    fun toggleLayoutGuides() = _state.update { it.copy(showLayoutGuides = !it.showLayoutGuides) }

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

    fun setReviewSourceStyle(value: String) {
        _state.update { it.copy(reviewSourceStyle = value) }
        persistReviewSidecar()
    }

    fun setReviewTargetStyle(value: String) {
        _state.update { it.copy(reviewTargetStyle = value) }
        persistReviewSidecar()
    }

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
        persistReviewSidecar()
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

    fun setSplitRatio(value: Float) {
        val next = value.coerceIn(0.28f, 0.78f)
        prefs.edit().putFloat("split_ratio", next).apply()
        _state.update { it.copy(project = it.project.copy(splitRatio = next)) }
    }

    fun restoreRecovery() {
        val snapshot = recoveryStore.read() ?: run {
            _state.update { it.copy(recoveryAvailable = false, recoveryLabel = "") }
            return
        }
        history.reset(snapshot.document)
        val styleNames = snapshot.document.styles.map { it.name }
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
                reviewSourceStyle = styleNames.firstOrNull().orEmpty(),
                reviewTargetStyle = styleNames.drop(1).firstOrNull().orEmpty(),
                originalTextById = snapshot.document.events.associate { event -> event.id to event.text },
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
    fun rendererFontsDir() = fontStore.importedDir

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

    private fun restoreReviewSidecar(
        identity: String,
        document: AssDocument,
        styleNames: List<String>,
    ): RestoredReviewState {
        val loaded = reviewStateStore.load(identity)
        val validIds = document.events.mapTo(hashSetOf()) { it.id }
        val defaults = document.events.associate { event -> event.id to event.text }
        if (loaded == null) {
            return RestoredReviewState(
                sourceStyle = styleNames.firstOrNull().orEmpty(),
                targetStyle = styleNames.drop(1).firstOrNull().orEmpty(),
                confirmedIds = emptySet(),
                originalTextById = defaults,
            )
        }

        val source = loaded.sourceStyle.takeIf { it in styleNames } ?: styleNames.firstOrNull().orEmpty()
        val target = loaded.targetStyle.takeIf { it in styleNames && it != source }
            ?: styleNames.firstOrNull { it != source }.orEmpty()

        val keyById = ReviewEventKey.keys(document.events)
        val idByKey = keyById.entries.associate { (id, key) -> key to id }
        val originals = defaults.toMutableMap()
        loaded.legacyOriginalTextById.forEach { (id, text) ->
            if (id in validIds) originals[id] = text
        }
        loaded.originalTextByKey.forEach { (key, text) ->
            idByKey[key]?.let { id -> originals[id] = text }
        }

        val confirmed = linkedSetOf<Long>()
        loaded.legacyConfirmedIds.filterTo(confirmed) { it in validIds }
        loaded.confirmedKeys.mapNotNullTo(confirmed) { idByKey[it] }

        return RestoredReviewState(source, target, confirmed, originals)
    }

    private fun persistReviewSidecar() {
        val snapshot = _state.value
        val identity = reviewIdentity(snapshot) ?: return
        val keyById = ReviewEventKey.keys(snapshot.document.events)
        val value = ReviewSidecar(
            sourceStyle = snapshot.reviewSourceStyle,
            targetStyle = snapshot.reviewTargetStyle,
            confirmedKeys = snapshot.confirmedReviewIds.mapNotNull { keyById[it] }.toSet(),
            originalTextByKey = snapshot.originalTextById.entries.mapNotNull { (id, text) ->
                keyById[id]?.let { key -> key to text }
            }.toMap(),
        )
        reviewPersistJob?.cancel()
        reviewPersistJob = viewModelScope.launch {
            delay(250)
            withContext(Dispatchers.IO) { reviewStateStore.write(identity, value) }
        }
    }

    private fun reviewIdentity(state: EditorState): String? {
        val containerUri = state.container.uri
        val track = state.container.selectedTrackNumber
        return when {
            !containerUri.isNullOrBlank() && track != null -> "mkv:" + containerUri + "#track=" + track
            !state.project.subtitleUri.isNullOrBlank() -> state.project.subtitleUri
            else -> null
        }
    }

    private fun containerReviewIdentity(it: EditorState, trackNumber: Long): String {
        return "mkv:" + it.container.uri.orEmpty() + "#track=" + trackNumber
    }

    private inline fun editDocument(status: String, transform: (AssDocument) -> AssDocument) {
        val beforeState = _state.value
        val before = beforeState.document
        val next = transform(before)
        if (next == before) return

        val changedConfirmedIds = beforeState.confirmedReviewIds.filterTo(hashSetOf()) { id ->
            before.events.firstOrNull { it.id == id } != next.events.firstOrNull { it.id == id }
        }

        history.commit(next)
        publishDocument(next, status, dirty = true)
        if (changedConfirmedIds.isNotEmpty()) {
            _state.update { it.copy(confirmedReviewIds = it.confirmedReviewIds - changedConfirmedIds) }
        }
        scheduleRecovery(next)
        persistReviewSidecar()
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
        _state.update { state ->
            val byStyle = state.document.events.groupBy { it.style }
            val glyphs = linkedMapOf<String, io.github.assworkbench.fonts.FontGlyphDiagnostic>()
            state.document.styles.forEach { style ->
                val sampleText = buildString {
                    for (event in byStyle[style.name].orEmpty()) {
                        if (length >= 4096) break
                        val clean = event.text
                            .replace(Regex("\\{[^}]*\\}"), "")
                            .replace("\\N", " ")
                            .replace("\\n", " ")
                        append(clean.take((4096 - length).coerceAtLeast(0)))
                        append(' ')
                    }
                }
                fontStore.glyphDiagnostic(style.fontName, sampleText)?.let { glyphs[style.name] = it }
            }
            state.copy(
                fontDiagnostics = FontDiagnostics.diagnose(
                    requestedFamilies = state.document.styles.map { it.fontName } +
                        FontBindingRewriter.explicitFamilies(state.document),
                    imported = state.importedFonts,
                    fallbackFamily = state.fallbackFontFamily,
                ),
                fontGlyphDiagnostics = glyphs,
            )
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
