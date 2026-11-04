package io.github.assworkbench.app.ui

/** Session presentation keys only; never persisted as WorkbenchTool identities. */
internal enum class FixedV11WorkspaceMode(val title: String, val tag: String, val widthDp: Int) {
    SUBTITLE_EDIT("字幕编辑", "subtitle", 118),
    PRECISE_TIMING("专业校时", "timing", 119),
    GEOMETRY("排版与几何", "geometry", 138),
    EFFECTS("效果 / Karaoke", "effects", 158);

    fun toolFor(current: WorkbenchTool): WorkbenchTool = when (this) {
        GEOMETRY -> WorkbenchTask.TYPESETTING.toolFor(current)
        EFFECTS -> WorkbenchTask.EFFECTS.toolFor(current)
        SUBTITLE_EDIT, PRECISE_TIMING -> {
            // Preserve the original layout-only subtitle/timing switch. Leaving
            // a specialist scene must nevertheless reach an actual authoring tool.
            if (current.task == WorkbenchTask.TYPESETTING || current.task == WorkbenchTask.EFFECTS) {
                if (this == SUBTITLE_EDIT) WorkbenchTool.TEXT else WorkbenchTool.TIMELINE
            } else current
        }
    }
}

/** External directory/rail commands cannot leave a misleading specialist tab selected. */
internal fun fixedV11SceneForTool(
    activeTool: WorkbenchTool,
    requested: FixedV11WorkspaceMode,
): FixedV11WorkspaceMode = when (activeTool.task) {
    WorkbenchTask.TYPESETTING -> FixedV11WorkspaceMode.GEOMETRY
    WorkbenchTask.EFFECTS -> FixedV11WorkspaceMode.EFFECTS
    else -> if (requested == FixedV11WorkspaceMode.GEOMETRY || requested == FixedV11WorkspaceMode.EFFECTS) {
        if (activeTool.task == WorkbenchTask.TIME) FixedV11WorkspaceMode.PRECISE_TIMING
        else FixedV11WorkspaceMode.SUBTITLE_EDIT
    } else requested
}
