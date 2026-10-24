package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.assworkbench.domain.*

/**
 * Figma 85:21: four task scenes with an underline and live binding context.
 * Callback routing stays in FIXED; this composable owns no editor or tool state.
 */
@Composable
internal fun FixedV11WorkspaceTabs(
    selected: FixedV11WorkspaceMode,
    onSelect: (FixedV11WorkspaceMode) -> Unit,
    modifier: Modifier = Modifier,
    contextDescription: String = "",
    actions: (@Composable () -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxWidth().testTag("fixed-v11-workspace-tabs"),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FixedV11WorkspaceMode.entries.forEach { mode ->
                        val active = selected == mode
                        Box(Modifier.width(mode.widthDp.dp).height(48.dp)
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(mode) })
                            .testTag("fixed-v11-tab-${mode.tag}")) {
                            Box(Modifier.matchParentSize().padding(vertical = 5.dp)
                                .background(
                                    if (active) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    RoundedCornerShape(5.dp),
                                ))
                            Text(mode.title,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 13.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            if (active) Box(Modifier.align(Alignment.BottomCenter)
                                .padding(start = 8.dp, end = 8.dp, bottom = 5.dp)
                                .fillMaxWidth().height(3.dp)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("fixed-v11-tab-indicator"))
                        }
                    }
                }
                if (contextDescription.isNotBlank()) Text(contextDescription,
                    modifier = Modifier.widthIn(max = 360.dp).padding(start = 16.dp)
                        .testTag("fixed-v11-workspace-context"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                actions?.invoke()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
