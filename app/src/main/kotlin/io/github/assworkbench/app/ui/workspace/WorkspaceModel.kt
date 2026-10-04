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

internal enum class WorkspaceToolPresence(val label: String) {
    TEMPORARY("临时"),
    RESIDENT("驻留"),
    BOOKMARKED("侧书签"),
    HIDDEN("已收回"),
}

internal enum class ToolContentDensity(val label: String) {
    COMPACT("紧凑"),
    STANDARD("标准"),
    PRECISION("精确");

    fun next(): ToolContentDensity = entries[(ordinal + 1) % entries.size]
}

internal data class WorkspaceToolInstance(
    val id: String,
    val toolKey: String,
    val binding: WorkspaceBinding = WorkspaceBinding.FollowFocus,
    val presence: WorkspaceToolPresence = WorkspaceToolPresence.TEMPORARY,
    val contentDensity: ToolContentDensity = ToolContentDensity.STANDARD,
)

internal data class WorkspaceState(
    val tools: List<WorkspaceToolInstance> = emptyList(),
    val parameterProjections: List<WorkspaceParameterProjection> = emptyList(),
    val surfacesHidden: Boolean = false,
    val activeInstanceId: String? = null,
    val sessionId: Long? = null,
) {
    fun hasTool(toolKey: String): Boolean = tools.any { it.toolKey == toolKey }

    fun instances(toolKey: String): List<WorkspaceToolInstance> =
        tools.filter { it.toolKey == toolKey }

    fun primary(toolKey: String): WorkspaceToolInstance? =
        tools.firstOrNull { it.id == primaryInstanceId(toolKey) }

    fun activeForTool(toolKey: String): WorkspaceToolInstance? {
        val active = activeInstanceId?.let { activeId ->
            tools.firstOrNull { it.id == activeId && it.toolKey == toolKey }
        }
        return active ?: primary(toolKey) ?: tools.firstOrNull { it.toolKey == toolKey }
    }

    fun openPrimary(
        toolKey: String,
        binding: WorkspaceBinding = WorkspaceBinding.FollowFocus,
    ): WorkspaceState {
        val id = primaryInstanceId(toolKey)
        if (tools.any { it.id == id }) {
            return copy(
                activeInstanceId = id,
                surfacesHidden = false,
            )
        }
        return copy(
            tools = tools + WorkspaceToolInstance(
                id = id,
                toolKey = toolKey,
                binding = binding,
            ),
            activeInstanceId = id,
            surfacesHidden = false,
        )
    }

    fun togglePrimary(toolKey: String): WorkspaceState =
        if (primary(toolKey) != null) closeInstance(primaryInstanceId(toolKey))
        else openPrimary(toolKey)

    /**
     * Open an event-bound tool without silently retargeting an existing pinned
     * primary instance. A different pinned target therefore gets a sibling.
     */
    fun openPinnedEvent(toolKey: String, eventId: Long): WorkspaceState {
        val primaryId = primaryInstanceId(toolKey)
        val primary = primary(toolKey)
        if (primary == null) {
            return openPrimary(toolKey, WorkspaceBinding.PinnedEvent(eventId)).activate(primaryId)
        }
        val pinned = primary.binding as? WorkspaceBinding.PinnedEvent
        if (pinned != null && pinned.eventId != eventId) {
            val sibling = newSibling(primary.id)?.copy(binding = WorkspaceBinding.PinnedEvent(eventId))
                ?: return this
            return addInstance(sibling).activate(sibling.id).withSurfacesHidden(false)
        }
        return openPrimary(toolKey)
            .updateBinding(primaryId, WorkspaceBinding.PinnedEvent(eventId))
            .activate(primaryId)
            .withSurfacesHidden(false)
    }

    fun addInstance(instance: WorkspaceToolInstance): WorkspaceState {
        if (tools.any { it.id == instance.id }) return this
        return copy(tools = tools + instance)
    }

    fun addParameterProjection(
        descriptorKey: String,
        presentation: WorkspaceParameterPresentation,
        binding: WorkspaceBinding,
    ): WorkspaceState {
        val descriptor = WorkspaceParameterCatalog.find(descriptorKey) ?: return this
        if (!descriptor.supports(presentation)) return this
        var ordinal = 1
        var id: String
        do {
            id = "parameter:" + descriptorKey + ":" + ordinal
            ordinal += 1
        } while (parameterProjections.any { it.id == id })
        return copy(
            parameterProjections = parameterProjections + WorkspaceParameterProjection(
                id = id,
                descriptorKey = descriptorKey,
                binding = binding,
                presentation = presentation,
            ),
            surfacesHidden = false,
        )
    }

    fun updateParameterPresentation(
        projectionId: String,
        presentation: WorkspaceParameterPresentation,
    ): WorkspaceState = copy(
        parameterProjections = parameterProjections.map { projection ->
            if (projection.id != projectionId) projection
            else {
                val descriptor = WorkspaceParameterCatalog.find(projection.descriptorKey)
                if (descriptor?.supports(presentation) == true) {
                    projection.copy(presentation = presentation)
                } else {
                    projection
                }
            }
        },
    )

    fun removeParameterProjection(projectionId: String): WorkspaceState = copy(
        parameterProjections = parameterProjections.filterNot { it.id == projectionId },
    )

    fun updatePresence(
        instanceId: String,
        presence: WorkspaceToolPresence,
    ): WorkspaceState = copy(
        tools = tools.map { instance ->
            if (instance.id == instanceId) instance.copy(presence = presence) else instance
        },
        activeInstanceId = if (presence == WorkspaceToolPresence.HIDDEN ||
            presence == WorkspaceToolPresence.BOOKMARKED
        ) {
            if (activeInstanceId == instanceId) tools.firstOrNull {
                it.id != instanceId && it.presence == WorkspaceToolPresence.RESIDENT
            }?.id else activeInstanceId
        } else {
            instanceId
        },
    )

    fun cycleContentDensity(instanceId: String): WorkspaceState = copy(
        tools = tools.map { instance ->
            if (instance.id == instanceId) {
                instance.copy(contentDensity = instance.contentDensity.next())
            } else {
                instance
            }
        },
    )

    fun hideOtherTemporary(exceptId: String): WorkspaceState = copy(
        tools = tools.map { instance ->
            if (instance.id != exceptId && instance.presence == WorkspaceToolPresence.TEMPORARY) {
                instance.copy(presence = WorkspaceToolPresence.HIDDEN)
            } else {
                instance
            }
        },
    )

    fun newSibling(sourceId: String): WorkspaceToolInstance? {
        val source = tools.firstOrNull { it.id == sourceId } ?: return null
        var ordinal = 2
        var candidate: String
        do {
            candidate = source.toolKey + ":" + ordinal
            ordinal += 1
        } while (tools.any { it.id == candidate })
        return source.copy(id = candidate)
    }

    fun closeInstance(instanceId: String): WorkspaceState {
        if (tools.none { it.id == instanceId }) return this
        val remaining = tools.filterNot { it.id == instanceId }
        return copy(
            tools = remaining,
            activeInstanceId = if (activeInstanceId == instanceId) {
                remaining.lastOrNull()?.id
            } else {
                activeInstanceId
            },
        )
    }

    fun activate(instanceId: String): WorkspaceState =
        if (tools.any { it.id == instanceId }) copy(activeInstanceId = instanceId)
        else this

    fun updateBinding(
        instanceId: String,
        binding: WorkspaceBinding,
    ): WorkspaceState = copy(
        tools = tools.map { instance ->
            if (instance.id == instanceId) instance.copy(binding = binding)
            else instance
        },
    )

    fun withSurfacesHidden(hidden: Boolean): WorkspaceState =
        copy(surfacesHidden = hidden)

    fun forSession(id: Long): WorkspaceState = copy(sessionId = id)

    fun toSaveableList(): List<String> = buildList {
        add(SCHEMA_VERSION)
        add(if (surfacesHidden) "1" else "0")
        add(activeInstanceId.orEmpty())
        add(sessionId?.toString().orEmpty())
        tools.forEach { instance ->
            val bindingCode: String
            val bindingArgument: String
            when (val binding = instance.binding) {
                WorkspaceBinding.FollowFocus -> {
                    bindingCode = "focus"
                    bindingArgument = ""
                }

                WorkspaceBinding.FollowSelection -> {
                    bindingCode = "selection"
                    bindingArgument = ""
                }

                is WorkspaceBinding.PinnedEvent -> {
                    bindingCode = "event"
                    bindingArgument = binding.eventId.toString()
                }
            }
            add(
                listOf(
                    instance.id,
                    instance.toolKey,
                    bindingCode,
                    bindingArgument,
                    instance.presence.name,
                    instance.contentDensity.name,
                ).joinToString(SEPARATOR)
            )
        }
        parameterProjections.forEach { projection ->
            val bindingCode: String
            val bindingArgument: String
            when (val binding = projection.binding) {
                WorkspaceBinding.FollowFocus -> {
                    bindingCode = "focus"
                    bindingArgument = ""
                }

                WorkspaceBinding.FollowSelection -> {
                    bindingCode = "selection"
                    bindingArgument = ""
                }

                is WorkspaceBinding.PinnedEvent -> {
                    bindingCode = "event"
                    bindingArgument = binding.eventId.toString()
                }
            }
            add(
                listOf(
                    PARAMETER_RECORD,
                    projection.id,
                    projection.descriptorKey,
                    bindingCode,
                    bindingArgument,
                    projection.presentation.name,
                ).joinToString(SEPARATOR)
            )
        }
    }

    companion object {
        private const val SCHEMA_VERSION = "workspace-v4"
        private const val V3_SCHEMA_VERSION = "workspace-v3"
        private const val V2_SCHEMA_VERSION = "workspace-v2"
        private const val LEGACY_SCHEMA_VERSION = "workspace-v1"
        private const val PARAMETER_RECORD = "@parameter"
        private const val SEPARATOR = "\u001F"

        fun primaryInstanceId(toolKey: String): String =
            toolKey + ":primary"

        fun fromSaveableList(values: List<String>): WorkspaceState {
            val version = values.firstOrNull()
            if (version != SCHEMA_VERSION && version != V3_SCHEMA_VERSION &&
                version != V2_SCHEMA_VERSION && version != LEGACY_SCHEMA_VERSION
            ) return WorkspaceState()

            val hidden = values.getOrNull(1) == "1"
            val serializedActive = values.getOrNull(2)?.takeIf(String::isNotBlank)
            val sessionId = if (
                version == SCHEMA_VERSION || version == V3_SCHEMA_VERSION ||
                version == V2_SCHEMA_VERSION
            ) {
                values.getOrNull(3)?.toLongOrNull()
            } else {
                null
            }
            val toolStart = if (
                version == SCHEMA_VERSION || version == V3_SCHEMA_VERSION ||
                version == V2_SCHEMA_VERSION
            ) 4 else 3
            val restoredTools = values.drop(toolStart).mapNotNull { encoded ->
                val fields = encoded.split(SEPARATOR)
                if (fields.firstOrNull() == PARAMETER_RECORD) return@mapNotNull null
                if (fields.size != 4 && fields.size != 6) return@mapNotNull null

                val binding = when (fields[2]) {
                    "focus" -> WorkspaceBinding.FollowFocus
                    "selection" -> WorkspaceBinding.FollowSelection
                    "event" -> fields[3].toLongOrNull()?.let(WorkspaceBinding::PinnedEvent)
                    else -> null
                } ?: return@mapNotNull null

                WorkspaceToolInstance(
                    id = fields[0],
                    toolKey = fields[1],
                    binding = binding,
                    presence = fields.getOrNull(4)
                        ?.let { name -> WorkspaceToolPresence.entries.firstOrNull { it.name == name } }
                        ?: WorkspaceToolPresence.TEMPORARY,
                    contentDensity = fields.getOrNull(5)
                        ?.let { name -> ToolContentDensity.entries.firstOrNull { it.name == name } }
                        ?: ToolContentDensity.STANDARD,
                )
            }.distinctBy { it.id }

            val restoredParameters = if (version == SCHEMA_VERSION) {
                values.drop(toolStart).mapNotNull { encoded ->
                    val fields = encoded.split(SEPARATOR)
                    if (fields.size != 6 || fields[0] != PARAMETER_RECORD) {
                        return@mapNotNull null
                    }
                    val binding = when (fields[3]) {
                        "focus" -> WorkspaceBinding.FollowFocus
                        "event" -> fields[4].toLongOrNull()?.let(WorkspaceBinding::PinnedEvent)
                        else -> null
                    } ?: return@mapNotNull null
                    val presentation = WorkspaceParameterPresentation.entries
                        .firstOrNull { it.name == fields[5] }
                        ?: return@mapNotNull null
                    runCatching {
                        WorkspaceParameterProjection(
                            id = fields[1],
                            descriptorKey = fields[2],
                            binding = binding,
                            presentation = presentation,
                        )
                    }.getOrNull()
                }.distinctBy { it.id }
            } else {
                emptyList()
            }

            val active = serializedActive?.takeIf { activeId ->
                restoredTools.any { it.id == activeId }
            }

            return WorkspaceState(
                tools = restoredTools,
                parameterProjections = restoredParameters,
                surfacesHidden = hidden,
                activeInstanceId = active,
                sessionId = sessionId,
            )
        }
    }
}
