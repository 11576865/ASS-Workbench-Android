package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.ui.workspace.WorkspaceBinding

/** Cross-tool commands currently reopen FollowFocus tools, so never infer a pinned target. */
internal fun fixedV11InspectorCanSwitch(
    binding: WorkspaceBinding,
    resolvedEventId: Long?,
    focusedEventId: Long?,
): Boolean = binding == WorkspaceBinding.FollowFocus &&
    resolvedEventId != null && resolvedEventId == focusedEventId

/** Read-only selection projection; commands remain owned by the existing FIXED host. */
internal fun fixedV11InspectorTabFor(tool: WorkbenchTool): WorkbenchTool? = when (tool) {
    WorkbenchTool.TEXT, WorkbenchTool.EVENT -> WorkbenchTool.EVENT
    WorkbenchTool.STYLE -> WorkbenchTool.STYLE
    WorkbenchTool.POSITION, WorkbenchTool.VECTOR_CLIP -> WorkbenchTool.POSITION
    else -> null
}

@Composable
internal fun FixedV11InspectorTabs(
    activeTool: WorkbenchTool,
    canSwitch: Boolean,
    onSelect: (WorkbenchTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = fixedV11InspectorTabFor(activeTool)
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp).selectableGroup()
        .testTag("fixed-v11-inspector-tabs")) {
        listOf("Event" to WorkbenchTool.EVENT, "Style" to WorkbenchTool.STYLE,
            "位置" to WorkbenchTool.POSITION).forEach { (title, tool) ->
            val active = selected == tool
            Box(Modifier.weight(1f).height(48.dp)
                .selectable(selected = active, enabled = canSwitch, role = Role.Tab,
                    onClick = { if (activeTool != tool) onSelect(tool) })
                .testTag("fixed-v11-inspector-${tool.name}")) {
                Text(title, Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.labelLarge,
                    color = (if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        .copy(alpha = if (canSwitch) 1f else 0.5f))
                if (active) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
