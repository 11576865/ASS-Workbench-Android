package io.github.assworkbench.app

/**
 * Stable, presentation-neutral slice of editor state.
 *
 * Slice A covers document summary, Focus, Selection and Undo/Redo.
 * Slice B adds a presentation-neutral current-object projection so Workspace
 * Binding resolution does not have to reach back into arbitrary EditorState fields.
 * Slice C exposes only transient-preview ownership metadata plus cancellation;
 * canonical commits remain domain intents owned by EditorViewModel rather than a
 * generic "commit arbitrary preview document" UI operation.
 * Presentations should not bypass this boundary by depending on unrelated
 * EditorViewModel internals.
 */
internal data class EditorUiState(
    val document: EditorUiDocumentSummary,
    val focus: EditorUiFocusState,
    val selection: EditorUiSelectionState,
    val objects: EditorUiObjectState,
    val preview: EditorUiPreviewState,
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

internal data class EditorUiEventIdentity(
    val id: Long,
    val styleName: String,
    val layer: Int,
)

internal data class EditorUiObjectState(
    val existingEventIds: Set<Long>,
    val currentEvent: EditorUiEventIdentity?,
)

/**
 * Presentation-neutral lease over the single transient preview channel.
 *
 * This deliberately does not expose previewDocument: transient rendering is not
 * a second canonical document and presentations must not commit it generically.
 */
internal data class EditorUiPreviewState(
    val active: Boolean,
    val ownerId: String?,
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
    fun clearTransientPreview(ownerId: String? = null)
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
        objects = EditorUiObjectState(
            existingEventIds = document.events.mapTo(linkedSetOf()) { it.id },
            currentEvent = focusedEventId
                ?.let { id -> document.events.firstOrNull { it.id == id } }
                ?.let { event ->
                    EditorUiEventIdentity(
                        id = event.id,
                        styleName = event.style,
                        layer = event.layer,
                    )
                },
        ),
        preview = EditorUiPreviewState(
            active = previewDocument != null,
            ownerId = previewOwnerId.takeIf { previewDocument != null },
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

    override fun clearTransientPreview(ownerId: String?) {
        viewModel.clearTransientPreview(ownerId)
    }

    override fun undo() {
        viewModel.undo()
    }

    override fun redo() {
        viewModel.redo()
    }
}
