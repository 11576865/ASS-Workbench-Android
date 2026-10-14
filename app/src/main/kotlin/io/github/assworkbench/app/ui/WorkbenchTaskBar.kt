package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A task is not a second tool registry. Wide tablets show concise task names;
 * compact windows expose the same actions as icons, with descriptions on long press.
 * Every persisted WorkbenchTool is still reachable from the complete directory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkbenchTaskBar(
    activeTool: WorkbenchTool,
    listRequested: Boolean,
    requestedPreview: PreviewDensity,
    onTool: (WorkbenchTool) -> Unit,
    onPreview: () -> Unit,
    onDirectory: () -> Unit,
) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val previewLabel = when (requestedPreview) {
        PreviewDensity.FULL -> "预览"
        PreviewDensity.COMPACT -> "紧凑"
        PreviewDensity.HIDDEN -> "隐藏"
    }
    val previewIcon = if (requestedPreview == PreviewDensity.HIDDEN)
        Icons.Filled.VisibilityOff else Icons.Filled.Visibility
    val contextual = if (listRequested) emptyList() else contextualToolsFor(activeTool)
    var contextualMenuOpen by remember(activeTool.task, listRequested) { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WorkbenchTask.entries.filterNot { it == WorkbenchTask.OTHER }.forEach { task ->
                    val taskIcon = when (task) {
                        WorkbenchTask.SUBTITLES -> Icons.Filled.Subtitles
                        WorkbenchTask.TEXT -> Icons.Filled.Article
                        WorkbenchTask.TYPESETTING -> Icons.Filled.Palette
                        WorkbenchTask.TIME -> Icons.Filled.AccessTime
                        WorkbenchTask.EFFECTS -> Icons.Filled.AutoFixHigh
                        WorkbenchTask.OTHER -> Icons.Filled.Apps
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(task.defaultTool.description) } },
                        state = rememberTooltipState(),
                    ) {
                        FilterChip(
                            selected = if (task == WorkbenchTask.SUBTITLES) listRequested
                                else !listRequested && activeTool.task == task,
                            onClick = { onTool(task.toolFor(activeTool)) },
                            label = {
                                if (compact) Icon(taskIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                                else Text(task.title)
                            },
                            modifier = Modifier
                                .semantics { contentDescription = task.title }
                                .testTag("fixed-tool-${task.defaultTool.name}"),
                        )
                    }
                }
            }
            // Relevant specialized editors are one tap away, not buried in the global catalog.
            if (contextual.size > 1) {
                Box {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("当前任务工具") } },
                        state = rememberTooltipState(),
                    ) {
                        if (compact) {
                            IconButton(
                                onClick = { contextualMenuOpen = true },
                                modifier = Modifier.testTag("fixed-context-tools"),
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = "当前任务工具")
                            }
                        } else {
                            TextButton(
                                onClick = { contextualMenuOpen = true },
                                modifier = Modifier.testTag("fixed-context-tools"),
                            ) {
                                Text(activeTool.title)
                                Icon(Icons.Filled.ExpandMore, contentDescription = null)
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = contextualMenuOpen,
                        onDismissRequest = { contextualMenuOpen = false },
                    ) {
                        contextual.forEach { tool ->
                            DropdownMenuItem(
                                text = { Text(tool.title) },
                                onClick = {
                                    contextualMenuOpen = false
                                    onTool(tool)
                                },
                                modifier = Modifier.testTag("fixed-context-${tool.name}"),
                            )
                        }
                    }
                }
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("全部专业工具") } },
                state = rememberTooltipState(),
            ) {
                FilterChip(
                    selected = !listRequested && activeTool.task == WorkbenchTask.OTHER,
                    onClick = onDirectory,
                    label = {
                        if (compact) Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(20.dp))
                        else Text("工具")
                    },
                    modifier = Modifier
                        .semantics { contentDescription = "工具目录" }
                        .testTag("fixed-tool-CAPABILITIES"),
                )
            }
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("预览：${requestedPreview.label}；点击切换") } },
                state = rememberTooltipState(),
            ) {
                FilterChip(
                    selected = requestedPreview != PreviewDensity.HIDDEN,
                    onClick = onPreview,
                    label = {
                        if (compact) Icon(previewIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        else Text(previewLabel)
                    },
                    modifier = Modifier
                        .semantics { contentDescription = "预览：${requestedPreview.label}" }
                        .testTag("fixed-preview-density"),
                )
            }
        }
    }
}
