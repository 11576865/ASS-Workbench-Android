package io.github.assworkbench.app

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssInlineSyntax
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubtitleProject
import io.github.assworkbench.domain.SubtitleDocumentFormat
import io.github.assworkbench.domain.WaveformEnvelope
import io.github.assworkbench.fonts.FontAsset
import io.github.assworkbench.fonts.FontDiagnostic
import io.github.assworkbench.fonts.FontGlyphDiagnostic

enum class WaveformLiteStatus { IDLE, ANALYZING, READY, UNAVAILABLE }

data class WaveformLiteState(
    val sourceUri: String? = null,
    val status: WaveformLiteStatus = WaveformLiteStatus.IDLE,
    val envelope: WaveformEnvelope? = null,
    val error: String? = null,
)

data class EditorState(
    val project: SubtitleProject = SubtitleProject(),
    val document: AssDocument = AssDocument(),
    val previewDocument: AssDocument? = null,
    val batchRulePreviewEventIds: Set<Long> = emptySet(),
    val waveform: WaveformLiteState = WaveformLiteState(),
    val geometryScaleLocked: Boolean = true,
    val subtitleLoaded: Boolean = false,
    val subtitleTextEncoding: AssTextEncoding = AssTextEncoding.UTF8,
    val subtitleFormat: SubtitleDocumentFormat = SubtitleDocumentFormat.ASS,
    val projectFileUri: String? = null,
    val workspaceMode: String = "FIXED",
    val workspaceRestoreState: List<String> = emptyList(),
    val surfaceRestoreState: List<String> = emptyList(),
    val workspaceRestoreNonce: Long = 0L,
    val selectedEventIds: Set<Long> = emptySet(),
    val selectionAnchorId: Long? = null,
    val focusedEventId: Long? = null,
    val query: String = "",
    val seekRequestMs: Long? = null,
    val seekRequestNonce: Long = 0L,
    val currentFrameNumber: Long? = null,
    val estimatedVideoFps: Double? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val dirty: Boolean = false,
    val importedFonts: List<FontAsset> = emptyList(),
    val fontDiagnostics: List<FontDiagnostic> = emptyList(),
    val fontGlyphDiagnostics: Map<String, FontGlyphDiagnostic> = emptyMap(),
    val rendererDiagnostics: List<String> = emptyList(),
    val fallbackFontFamily: String? = null,
    val fontRevision: Long = 0L,
    val fontImportBusy: Boolean = false,
    /** Manually imported font SHA-256 values selected for the next MKV write-back. */
    val fontPackagingSelection: Set<String> = emptySet(),
    val container: ContainerBridgeState = ContainerBridgeState(),
    val recoveryAvailable: Boolean = false,
    val recoveryLabel: String = "",
    val status: String = "可先打开 ASS，也可先选择参考视频；两者互不依赖。",
) {
    val filteredEvents get() = document.events.filter {
        query.isBlank() ||
            AssInlineSyntax.visibleText(it.text).contains(query, ignoreCase = true) ||
            it.name.contains(query, ignoreCase = true) ||
            it.style.contains(query, ignoreCase = true)
    }
}
