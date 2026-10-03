package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.app.EditorUiState
import io.github.assworkbench.app.ui.WorkbenchTool
import io.github.assworkbench.domain.AssDocument

/**
 * UI-facing scope explanation for mutating tools.
 *
 * This does not own domain state. It derives WHO / WHERE / HOW MANY from the
 * canonical document plus the ToolInstance binding so every host can present
 * the same explanation.
 */
internal enum class WorkspaceWriteTarget(val label: String) {
    SHARED_STYLE("共享 Style"),
    EVENT_TEXT("Event Text"),
    EVENT_FIELDS("Event 字段"),
    EVENT_OVERRIDE("Event Override"),
    EVENT_TEXT_KARAOKE("Event Text / Karaoke tags"),
    BATCH_EVENTS("批量 Event 变换"),
}

internal data class WorkspaceEditScopeSummary(
    val who: String,
    val writeTarget: WorkspaceWriteTarget,
    val howMany: Int,
    val binding: String,
    val detail: String? = null,
    val unresolved: Boolean = false,
) {
    val where: String get() = writeTarget.label
}

internal object WorkspaceEditScopeResolver {
    fun resolve(
        tool: WorkbenchTool,
        instance: WorkspaceToolInstance,
        document: AssDocument,
        editorUiState: EditorUiState,
    ): WorkspaceEditScopeSummary? {
        val bindingState = instance.resolveUiBinding(editorUiState)
        val bindingResolution = bindingState.resolution
        val bindingLabel = bindingState.label

        if (bindingResolution is WorkspaceBindingResolution.UnresolvedPinnedEvent) {
            return WorkspaceEditScopeSummary(
                who = "#${bindingResolution.eventId}（目标已失效）",
                writeTarget = writeTarget(tool),
                howMany = 0,
                binding = bindingLabel,
                detail = "Former target: #${bindingResolution.eventId}",
                unresolved = true,
            )
        }

        return when (tool) {
            WorkbenchTool.STYLE -> {
                val eventId = (bindingResolution as? WorkspaceBindingResolution.Event)?.eventId
                    ?: return noEvent(bindingLabel, WorkspaceWriteTarget.SHARED_STYLE)
                val event = document.events.firstOrNull { it.id == eventId }
                    ?: return noEvent(bindingLabel, WorkspaceWriteTarget.SHARED_STYLE)
                val affected = document.events.count { it.style == event.style }
                WorkspaceEditScopeSummary(
                    who = "Style · ${event.style}",
                    writeTarget = WorkspaceWriteTarget.SHARED_STYLE,
                    howMany = affected,
                    binding = bindingLabel,
                    detail = "由 Event #${event.id} 上下文进入",
                )
            }

            WorkbenchTool.TEXT -> eventScope(bindingResolution, document, bindingLabel, WorkspaceWriteTarget.EVENT_TEXT)
            WorkbenchTool.EVENT -> eventScope(bindingResolution, document, bindingLabel, WorkspaceWriteTarget.EVENT_FIELDS)
            WorkbenchTool.POSITION -> eventScope(bindingResolution, document, bindingLabel, WorkspaceWriteTarget.EVENT_OVERRIDE)
            WorkbenchTool.EFFECTS -> eventScope(bindingResolution, document, bindingLabel, WorkspaceWriteTarget.EVENT_OVERRIDE)
            WorkbenchTool.KARAOKE -> eventScope(bindingResolution, document, bindingLabel, WorkspaceWriteTarget.EVENT_TEXT_KARAOKE)
            WorkbenchTool.VECTOR_CLIP -> eventScope(bindingResolution, document, bindingLabel, WorkspaceWriteTarget.EVENT_OVERRIDE)

            WorkbenchTool.BATCH -> {
                val ids = when (bindingResolution) {
                    is WorkspaceBindingResolution.Selection -> bindingResolution.eventIds
                    is WorkspaceBindingResolution.Event -> setOf(bindingResolution.eventId)
                    else -> emptySet()
                }
                WorkspaceEditScopeSummary(
                    who = if (ids.isEmpty()) "当前选择为空" else "已选 ${ids.size} 条 Event",
                    writeTarget = WorkspaceWriteTarget.BATCH_EVENTS,
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
        writeTarget: WorkspaceWriteTarget,
    ): WorkspaceEditScopeSummary {
        val eventId = (resolution as? WorkspaceBindingResolution.Event)?.eventId
            ?: return noEvent(bindingLabel, writeTarget)
        val event = document.events.firstOrNull { it.id == eventId }
            ?: return noEvent(bindingLabel, writeTarget)
        return WorkspaceEditScopeSummary(
            who = "Event #${event.id}",
            writeTarget = writeTarget,
            howMany = 1,
            binding = bindingLabel,
            detail = "Style · ${event.style}",
        )
    }

    private fun noEvent(bindingLabel: String, writeTarget: WorkspaceWriteTarget) =
        WorkspaceEditScopeSummary(
            who = "未绑定 Event",
            writeTarget = writeTarget,
            howMany = 0,
            binding = bindingLabel,
            unresolved = true,
        )

    private fun writeTarget(tool: WorkbenchTool): WorkspaceWriteTarget = when (tool) {
        WorkbenchTool.STYLE -> WorkspaceWriteTarget.SHARED_STYLE
        WorkbenchTool.TEXT -> WorkspaceWriteTarget.EVENT_TEXT
        WorkbenchTool.EVENT -> WorkspaceWriteTarget.EVENT_FIELDS
        WorkbenchTool.POSITION, WorkbenchTool.EFFECTS, WorkbenchTool.VECTOR_CLIP ->
            WorkspaceWriteTarget.EVENT_OVERRIDE
        WorkbenchTool.KARAOKE -> WorkspaceWriteTarget.EVENT_TEXT_KARAOKE
        WorkbenchTool.BATCH -> WorkspaceWriteTarget.BATCH_EVENTS
        else -> error("Non-mutating tool has no write target: $tool")
    }
}
