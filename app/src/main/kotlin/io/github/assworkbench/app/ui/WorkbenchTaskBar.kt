package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Stable task entries; complete tool discovery and reference visibility stay reachable. */
@Composable
internal fun WorkbenchTaskBar(
    activeTool: WorkbenchTool,
    listRequested: Boolean,
    requestedPreview: PreviewDensity,
    onTool: (WorkbenchTool) -> Unit,
    onPreview: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                mainAuthoringTools().filterNot { it == WorkbenchTool.CAPABILITIES }.forEach { tool ->
                    FilterChip(
                        selected = if (tool == WorkbenchTool.SUBTITLES) listRequested
                            else !listRequested && activeTool == tool,
                        onClick = { onTool(tool) },
                        label = { Text(when (tool) {
                            WorkbenchTool.SUBTITLES -> "字幕"
                            WorkbenchTool.STYLE -> "排版"
                            WorkbenchTool.TIMELINE -> "时间"
                            else -> tool.title
                        }) },
                        modifier = Modifier.testTag("fixed-tool-${tool.name}"),
                    )
                }
            }
            FilterChip(selected = !listRequested && activeTool == WorkbenchTool.CAPABILITIES,
                onClick = { onTool(WorkbenchTool.CAPABILITIES) }, label = { Text("工具") },
                modifier = Modifier.testTag("fixed-tool-CAPABILITIES"))
            FilterChip(selected = requestedPreview != PreviewDensity.HIDDEN,
                onClick = onPreview,
                label = { Text(when (requestedPreview) {
                    PreviewDensity.FULL -> "预览"
                    PreviewDensity.COMPACT -> "紧凑"
                    PreviewDensity.HIDDEN -> "隐藏"
                }) },
                leadingIcon = {
                    Icon(if (requestedPreview == PreviewDensity.HIDDEN) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility, "预览 · ${requestedPreview.label}")
                }, modifier = Modifier.testTag("fixed-preview-density"))
        }
    }
}
