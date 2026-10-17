package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.*

/**
 * Presentation-only FIXED workspace mode, not a WorkbenchTool replacement.
 * Switching changes space allocation of the EXISTING video and timeline;
 * Focus/Selection, inspector identity, drafts, write target and Undo do not change.
 */
internal enum class FixedV11WorkspaceMode { SUBTITLE_EDIT, PRECISE_TIMING }

@Composable
internal fun FixedV11WorkspaceTabs(
    selected: FixedV11WorkspaceMode,
    onSelect: (FixedV11WorkspaceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth().testTag("fixed-v11-workspace-tabs"),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == FixedV11WorkspaceMode.SUBTITLE_EDIT,
                onClick = { onSelect(FixedV11WorkspaceMode.SUBTITLE_EDIT) },
                label = { Text("字幕编辑") },
                modifier = Modifier.testTag("fixed-v11-tab-subtitle"),
            )
            FilterChip(
                selected = selected == FixedV11WorkspaceMode.PRECISE_TIMING,
                onClick = { onSelect(FixedV11WorkspaceMode.PRECISE_TIMING) },
                label = { Text("专业校时") },
                modifier = Modifier.testTag("fixed-v11-tab-timing"),
            )
            Text("FIXED · 同一工程与编辑状态",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Read-only evidence beside the SAME inspector, never a second timing editor.
 * Pinned/selection binding is passed through; a missing target cannot be edited.
 */
@Composable
internal fun FixedV11TimingContext(
    event: AssEvent?,
    bindingDescription: String,
    mediaPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("fixed-v11-timing-context"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("专业校时 · 已保存值", style = MaterialTheme.typography.titleSmall)
            Text("绑定：" + bindingDescription,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (event == null) {
                Text("未找到可读取的事件；此面板不改变写入对象",
                    style = MaterialTheme.typography.labelSmall)
            } else {
                Text("开始 " + event.start.toAss() + "    结束 " + event.end.toAss(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("fixed-v11-timing-cue"))
                Text("时长 " + (event.end.millis - event.start.millis) + " ms",
                    style = MaterialTheme.typography.labelSmall)
            }
            Text("媒体 PTS " + mediaPositionMs + " ms · 时间轴与视频共享时钟",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("fixed-v11-timing-pts"),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
