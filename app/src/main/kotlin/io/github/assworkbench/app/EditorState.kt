package io.github.assworkbench.app

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssInlineSyntax
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubtitleProject
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
    /** Owner of the single active transient domain preview. */
    val previewOwnerId: String? = null,
    /** Ephemeral namespace for UI bindings; increments whenever the document workspace is replaced. */
    val workspaceSessionId: Long = 1L,
    val waveform: WaveformLiteState = WaveformLiteState(),
    val audioTracks: List<MediaAudioTrackInfo> = emptyList(),
    val selectedAudioTrackIndex: Int? = null,
    val sceneCutsMs: List<Long> = emptyList(),
    val geometryScaleLocked: Boolean = true,
    /** Null disables touch scale quantization; otherwise value is percentage points per snap step. */
    val geometryScaleSnapStep: Double? = null,
    val subtitleLoaded: Boolean = false,
    val subtitleTextEncoding: AssTextEncoding = AssTextEncoding.UTF8,
    val sourceFormat: SubtitleSourceFormat = SubtitleSourceFormat.ASS,
    val selectedEventIds: Set<Long> = emptySet(),
    val selectionAnchorId: Long? = null,
    val focusedEventId: Long? = null,
    val query: String = "",
    val seekRequestMs: Long? = null,
    val seekRequestNonce: Long = 0L,
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
