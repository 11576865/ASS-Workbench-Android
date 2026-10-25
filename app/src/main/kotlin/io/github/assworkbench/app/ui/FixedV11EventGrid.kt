package io.github.assworkbench.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.AssQcIssue

/**
 * V1.1 wide FIXED navigation: index/start/end/style plus a separate text line.
 * Uses the existing EventWorkspace focus, selection and range-gesture owner.
 * This view never creates another Event selection or document draft state.
 */
@Composable
internal fun FixedV11EventGridHeader(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("fixed-v11-grid-header"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.height(30.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall)
            Text("开始", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text("结束", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text("样式", modifier = Modifier.width(66.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FixedV11EventGridRow(
    eventId: Long,
    start: String,
    end: String,
    styleName: String,
    visibleText: String,
    focused: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    issues: List<AssQcIssue>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var issuesOpen by remember(eventId) { mutableStateOf(false) }
    val rowColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        focused -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("event-row-$eventId")
            .background(rowColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().testTag("fixed-v11-grid-fields-$eventId"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() })
                } else {
                    Text("#$eventId", style = MaterialTheme.typography.labelSmall,
                        color = if (focused) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(start, modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall)
            Text(end, modifier = Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall)
            Text(styleName, modifier = Modifier.width(66.dp), maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A no-QC row has no competing sibling and must not depend on
        // weight allocation for its subtitle body. Keep a finite text height
        // and retain weight only when a QC button needs the trailing space.
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val textWidth = if (issues.isEmpty()) Modifier.fillMaxWidth()
                else Modifier.weight(1f)
            Text(visibleText.ifBlank { "（空字幕）" },
                modifier = textWidth.heightIn(min = 20.dp)
                    .testTag("fixed-v11-grid-text-$eventId"),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
            if (issues.isNotEmpty()) {
                Box {
                    TextButton(
                        onClick = { issuesOpen = true },
                        modifier = Modifier.widthIn(min = 36.dp)
                            .testTag("fixed-v11-grid-qc-$eventId"),
                    ) {
                        Text("!" + issues.size, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    DropdownMenu(expanded = issuesOpen, onDismissRequest = { issuesOpen = false }) {
                        issues.forEach { issue ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(issue.message)
                                        Text(issue.kind.name,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = { issuesOpen = false },
                            )
                        }
                    }
                }
            }
        }
    }
}
