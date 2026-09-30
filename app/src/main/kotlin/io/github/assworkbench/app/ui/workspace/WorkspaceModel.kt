package io.github.assworkbench.app.ui.workspace

internal sealed interface WorkspaceBinding {
    data object FollowFocus : WorkspaceBinding
    data object FollowSelection : WorkspaceBinding
    data class PinnedEvent(val eventId: Long) : WorkspaceBinding
}

internal sealed interface WorkspaceBindingResolution {
    data class Event(val eventId: Long) : WorkspaceBindingResolution
    data class Selection(val eventIds: Set<Long>) : WorkspaceBindingResolution
    data class UnresolvedPinnedEvent(val eventId: Long) : WorkspaceBindingResolution
    data object None : WorkspaceBindingResolution
}

internal fun WorkspaceBinding.resolve(
    focusedEventId: Long?,
    selectedEventIds: Set<Long>,
    existingEventIds: Set<Long>,
): WorkspaceBindingResolution = when (this) {
    WorkspaceBinding.FollowFocus -> focusedEventId
        ?.takeIf(existingEventIds::contains)
        ?.let(WorkspaceBindingResolution::Event)
        ?: WorkspaceBindingResolution.None
    WorkspaceBinding.FollowSelection -> {
        val result = selectedEventIds.filterTo(linkedSetOf(), existingEventIds::contains)
        if (result.isEmpty()) WorkspaceBindingResolution.None
        else WorkspaceBindingResolution.Selection(result)
    }
    is WorkspaceBinding.PinnedEvent -> {
        if (eventId in existingEventIds) WorkspaceBindingResolution.Event(eventId)
        else WorkspaceBindingResolution.UnresolvedPinnedEvent(eventId)
    }
}

internal data class WorkspaceToolInstance(
    val id: String,
    val toolKey: String,
    val binding: WorkspaceBinding = WorkspaceBinding.FollowFocus,
)

internal data class WorkspaceState(
    val tools: List<WorkspaceToolInstance> = emptyList(),
    val surfacesHidden: Boolean = false,
    val activeInstanceId: String? = null,
) {
    fun hasTool(key: String) = tools.any { it.toolKey == key }

    fun openPrimary(key: String): WorkspaceState {
        val id = primaryInstanceId(key)
        if (tools.any { it.id == id }) return copy(activeInstanceId = id, surfacesHidden = false)
        return copy(
            tools = tools + WorkspaceToolInstance(id, key),
            activeInstanceId = id,
            surfacesHidden = false,
        )
    }

    fun closeInstance(id: String): WorkspaceState = copy(
        tools = tools.filterNot { it.id == id },
        activeInstanceId = activeInstanceId?.takeUnless { it == id },
    )

    fun activate(id: String) = copy(activeInstanceId = id)

    fun withSurfacesHidden(hidden: Boolean) = copy(surfacesHidden = hidden)

    companion object {
        fun primaryInstanceId(toolKey: String) = "$toolKey:primary"
    }
}
