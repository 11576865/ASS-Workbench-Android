package io.github.assworkbench.app

/**
 * Stable, presentation-neutral slice of editor state.
 *
 * Slice A intentionally covers only document identity/summary, Focus, Selection,
 * and Undo/Redo. Later slices may extend the contract, but presentations should
 * not bypass this boundary by depending on unrelated EditorViewModel internals.
 */
internal data class EditorUiState(
    val document: EditorUiDocumentSummary,
    val focus: EditorUiFocusState,
    val selection: EditorUiSelectionState,
    val history: EditorUiHistoryState,
    val workspaceSessionId: Long,
)

internal data class EditorUiDocumentSummary(
    val eventCount: Int,
    val playResX: Int,
    val playResY: Int,
    val subtitleLoaded: Boolean,
    val dirty: Boolean,
)

internal data class EditorUiFocusState(
    val eventId: Long?,
)

internal data class EditorUiSelectionState(
    val eventIds: Set<Long>,
    val anchorId: Long?,
)

internal data class EditorUiHistoryState(
    val canUndo: Boolean,
    val canRedo: Boolean,
)

/**
 * Stable intent surface for the first UI-contract migration slice.
 *
 * This adapter does not own state. EditorViewModel remains the implementation
 * authority while presentations migrate away from direct method coupling.
 */
internal interface EditorUiActions {
    fun focusEvent(id: Long, seek: Boolean = true)
    fun toggleSelection(id: Long)
    fun clearSelection()
    fun undo()
    fun redo()
}

internal fun EditorState.toEditorUiState(): EditorUiState =
    EditorUiState(
        document = EditorUiDocumentSummary(
            eventCount = document.events.size,
            playResX = document.playResX,
            playResY = document.playResY,
            subtitleLoaded = subtitleLoaded,
            dirty = dirty,
        ),
        focus = EditorUiFocusState(
            eventId = focusedEventId,
        ),
        selection = EditorUiSelectionState(
            eventIds = selectedEventIds.toSet(),
            anchorId = selectionAnchorId,
        ),
        history = EditorUiHistoryState(
            canUndo = canUndo,
            canRedo = canRedo,
        ),
        workspaceSessionId = workspaceSessionId,
    )

internal class EditorViewModelUiActions(
    private val viewModel: EditorViewModel,
) : EditorUiActions {
    override fun focusEvent(id: Long, seek: Boolean) {
        viewModel.focusEvent(id, seek)
    }

    override fun toggleSelection(id: Long) {
        viewModel.toggleSelected(id)
    }

    override fun clearSelection() {
        viewModel.clearSelection()
    }

    override fun undo() {
        viewModel.undo()
    }

    override fun redo() {
        viewModel.redo()
    }
}
