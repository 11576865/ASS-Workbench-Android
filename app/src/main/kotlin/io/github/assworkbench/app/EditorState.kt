package io.github.assworkbench.app

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.SubtitleProject
import io.github.assworkbench.fonts.FontAsset
import io.github.assworkbench.fonts.FontDiagnostic

data class EditorState(
    val project: SubtitleProject = SubtitleProject(),
    val document: AssDocument = AssDocument(),
    val subtitleLoaded: Boolean = false,
    val selectedEventIds: Set<Long> = emptySet(),
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
    val fallbackFontFamily: String? = null,
    val fontRevision: Long = 0L,
    val status: String = "打开视频和 ASS 开始编辑。",
) {
    val filteredEvents get() = document.events.filter {
        query.isBlank() || it.text.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
    }
}
