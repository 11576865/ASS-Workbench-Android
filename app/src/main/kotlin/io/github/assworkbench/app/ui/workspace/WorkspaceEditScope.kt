package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.app.ui.WorkbenchTool
import io.github.assworkbench.domain.AssDocument

/**
 * UI-facing scope explanation for mutating tools.
 *
 * This does not own domain state. It derives WHO / WHERE / HOW MANY from the
 * canonical document plus the ToolInstance binding so every host can present
 * the same explanation.
 */
internal data class WorkspaceEditScopeSummary(
    val who: String,
    val where: String,
    val howMany: Int,
    val binding: String,
    val detail: String? = null,
    val unresolved: Boolean = false,
)

internal object WorkspaceEditScopeResolver {
    fun resolve(
        tool: WorkbenchTool,
        instance: WorkspaceToolInstance,
        document: AssDocument,
        focusedEventId: Long?,
        selectedEventIds: Set<Long>,
    ): WorkspaceEditScopeSummary? {
        val existingIds = document.events.asSequence().map { it.id }.toSet()
        val bindingResolution = instance.binding.resolve(
            focusedEventId = focusedEventId,
            selectedEventIds = selectedEventIds,
            existingEventIds = existingIds,
        )
        val bindingLabel = when (val binding = instance.binding) {
            WorkspaceBinding.FollowFocus -> "FollowFocus"
            WorkspaceBinding.FollowSelection -> "FollowSelection"
            is WorkspaceBinding.PinnedEvent -> "Pinned #${binding.eventId}"
        }

        if (bindingResolution is WorkspaceBindingResolution.UnresolvedPinnedEvent) {
            return WorkspaceEditScopeSummary(
                who = "#${bindingResolution.eventId}（目标已失效）",
                where = writeTarget(tool),
                howMany = 0,
                binding = bindingLabel,
                detail = "Former target: #${bindingResolution.eventId}",
                unresolved = true,
            )
        }

        return when (tool) {
            WorkbenchTool.STYLE -> {
                val eventId = (bindingResolution as? WorkspaceBindingResolution.Event)?.eventId
                    ?: return noEvent(bindingLabel, "共享 Style")
                val event = document.events.firstOrNull { it.id == eventId }
                    ?: return noEvent(bindingLabel, "共享 Style")
                val affected = document.events.count { it.style == event.style }
                WorkspaceEditScopeSummary(
                    who = "Style · ${event.style}",
                    where = "共享 Style",
                    howMany = affected,
                    binding = bindingLabel,
                    detail = "由 Event #${event.id} 上下文进入",
                )
            }

            WorkbenchTool.TEXT -> eventScope(bindingResolution, document, bindingLabel, "Event Text")
            WorkbenchTool.EVENT -> eventScope(bindingResolution, document, bindingLabel, "Event 字段")
            WorkbenchTool.POSITION -> eventScope(bindingResolution, document, bindingLabel, "Event Override")
            WorkbenchTool.EFFECTS -> eventScope(bindingResolution, document, bindingLabel, "Event Override")
            WorkbenchTool.KARAOKE -> eventScope(bindingResolution, document, bindingLabel, "Event Text / Karaoke tags")
            WorkbenchTool.VECTOR_CLIP -> eventScope(bindingResolution, document, bindingLabel, "Event Override")

            WorkbenchTool.BATCH -> {
                val ids = when (bindingResolution) {
                    is WorkspaceBindingResolution.Selection -> bindingResolution.eventIds
                    is WorkspaceBindingResolution.Event -> setOf(bindingResolution.eventId)
                    else -> emptySet()
                }
                WorkspaceEditScopeSummary(
                    who = if (ids.isEmpty()) "当前选择为空" else "已选 ${ids.size} 条 Event",
                    where = "批量 Event 变换",
                    howMany = ids.size,
                    binding = bindingLabel,
                )
            }

            else -> null
        }
    }

    private fun eventScope(
        resolution: WorkspaceBindingResolution,
        document: AssDocument,
        bindingLabel: String,
        where: String,
    ): WorkspaceEditScopeSummary {
        val eventId = (resolution as? WorkspaceBindingResolution.Event)?.eventId
            ?: return noEvent(bindingLabel, where)
        val event = document.events.firstOrNull { it.id == eventId }
            ?: return noEvent(bindingLabel, where)
        return WorkspaceEditScopeSummary(
            who = "Event #${event.id}",
            where = where,
            howMany = 1,
            binding = bindingLabel,
            detail = "Style · ${event.style}",
        )
    }

    private fun noEvent(bindingLabel: String, where: String) =
        WorkspaceEditScopeSummary(
            who = "未绑定 Event",
            where = where,
            howMany = 0,
            binding = bindingLabel,
            unresolved = true,
        )

    private fun writeTarget(tool: WorkbenchTool): String = when (tool) {
        WorkbenchTool.STYLE -> "共享 Style"
        WorkbenchTool.TEXT -> "Event Text"
        WorkbenchTool.EVENT -> "Event 字段"
        WorkbenchTool.POSITION, WorkbenchTool.EFFECTS, WorkbenchTool.VECTOR_CLIP -> "Event Override"
        WorkbenchTool.KARAOKE -> "Event Text / Karaoke tags"
        WorkbenchTool.BATCH -> "批量 Event 变换"
        else -> "—"
    }
}
