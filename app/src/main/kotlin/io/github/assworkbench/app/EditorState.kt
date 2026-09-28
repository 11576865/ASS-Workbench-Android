package io.github.assworkbench.app

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssInlineSyntax
import io.github.assworkbench.domain.AssTextEncoding
import io.github.assworkbench.domain.SubtitleProject
import io.github.assworkbench.fonts.FontAsset
import io.github.assworkbench.fonts.FontDiagnostic
import io.github.assworkbench.fonts.FontGlyphDiagnostic

data class EditorState(
    val project: SubtitleProject = SubtitleProject(),
    val document: AssDocument = AssDocument(),
    val subtitleLoaded: Boolean = false,
    val subtitleTextEncoding: AssTextEncoding = AssTextEncoding.UTF8,
    val selectedEventIds: Set<Long> = emptySet(),
    val selectionAnchorId: Long? = null,
    val focusedEventId: Long? = null,
    val query: String = "",
    val playbackPositionMs: Long = 0L,
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
    val showLayoutGuides: Boolean = false,
    val reviewSourceStyle: String = "",
    val reviewTargetStyle: String = "",
    val originalTextById: Map<Long, String> = emptyMap(),
    val confirmedReviewIds: Set<Long> = emptySet(),
    val reviewFilter: String = "all",
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
