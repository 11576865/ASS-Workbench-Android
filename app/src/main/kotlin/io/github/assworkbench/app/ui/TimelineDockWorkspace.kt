package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel

@Composable
internal fun TimelineDockWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    onOpenVideo: () -> Unit,
    rendererEnabled: Boolean,
    onEnableRenderer: () -> Unit,
    onEditEventPosition: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var expandedHeightDp by rememberSaveable { mutableFloatStateOf(340f) }
    val dockHeight = if (expanded) expandedHeightDp.dp else 156.dp

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("timeline-dock-preview"),
        ) {
            WorkbenchPreview(
                state = state,
                viewModel = viewModel,
                positionEditEventId = null,
                onOpenVideo = onOpenVideo,
                onOpenTimeline = { expanded = true },
                rendererEnabled = rendererEnabled,
                onEnableRenderer = onEnableRenderer,
                onEditEventPosition = onEditEventPosition,
                viewportGesturesEnabled = true,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(dockHeight)
                .testTag("persistent-timeline-dock"),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 5.dp,
            shadowElevation = 7.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (expanded) "时间轴 · 展开" else "时间轴 · 常驻",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (expanded) {
                        TextButton(
                            onClick = { expandedHeightDp = (expandedHeightDp - 40f).coerceAtLeast(240f) },
                            enabled = expandedHeightDp > 240f,
                        ) { Text("矮") }
                        TextButton(
                            onClick = { expandedHeightDp = (expandedHeightDp + 40f).coerceAtMost(520f) },
                            enabled = expandedHeightDp < 520f,
                        ) { Text("高") }
                    }
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.testTag("timeline-dock-toggle"),
                    ) {
                        Text(if (expanded) "收起" else "展开")
                    }
                }
                HorizontalDivider()
                ModernTimelinePane(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize().testTag("timeline-dock-pane"),
                    compact = !expanded,
                )
            }
        }
    }
}
