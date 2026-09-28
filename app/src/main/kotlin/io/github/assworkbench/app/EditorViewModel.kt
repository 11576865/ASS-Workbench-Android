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