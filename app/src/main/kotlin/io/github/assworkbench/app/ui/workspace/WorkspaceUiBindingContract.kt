package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.app.EditorUiState

/**
 * Presentation-neutral Binding projection.
 *
 * Focus, Selection and Binding remain separate. This type only resolves a
 * ToolInstance Binding against the stable EditorUiState identity projection;
 * it does not own or mutate editor state.
 */
internal data class WorkspaceUiBindingState(
    val binding: WorkspaceBinding,
    val resolution: WorkspaceBindingResolution,
) {
    val eventId: Long?
        get() = (resolution as? WorkspaceBindingResolution.Event)?.eventId

    val selectionEventIds: Set<Long>
        get() = (resolution as? WorkspaceBindingResolution.Selection)?.eventIds ?: emptySet()

    val unresolvedPinnedEventId: Long?
        get() = (resolution as? WorkspaceBindingResolution.UnresolvedPinnedEvent)?.eventId

    val label: String
        get() = when (val value = binding) {
            WorkspaceBinding.FollowFocus -> "FollowFocus"
            WorkspaceBinding.FollowSelection -> "FollowSelection"
            is WorkspaceBinding.PinnedEvent -> "Pinned #${value.eventId}"
        }
}

internal fun WorkspaceToolInstance.resolveUiBinding(
    editorUiState: EditorUiState,
): WorkspaceUiBindingState =
    WorkspaceUiBindingState(
        binding = binding,
        resolution = binding.resolve(
            focusedEventId = editorUiState.focus.eventId,
            selectedEventIds = editorUiState.selection.eventIds,
            existingEventIds = editorUiState.objects.existingEventIds,
        ),
    )
