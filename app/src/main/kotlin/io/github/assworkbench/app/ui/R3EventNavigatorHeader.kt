package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.SubtitleFilterState

/**
 * R3's 267dp event navigator: compact actions remain discoverable without
 * clipping the list at the left edge. Uses the same domain filter and commands
 * as the previous SubtitleResultsBar; no duplicate Event-list state.
 */
@Composable
internal fun R3EventNavigatorHeader(
    filter: SubtitleFilterState,
    total: Int,
    matched: Int,
    selected: Int,
    onFilterChange: (SubtitleFilterState) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onLocate: () -> Unit,
) {
    var filterOpen by remember { mutableStateOf(false) }
    var operationsOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("字幕事件", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small) {
                Text(matched.toString(), modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { filterOpen = true },
                modifier = Modifier.testTag("r3-event-filter")) {
                Text("筛选", style = MaterialTheme.typography.labelSmall)
                Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(16.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("匹配 $matched / 全部 $total · 已选 $selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onPrevious, enabled = matched > 0,
                modifier = Modifier.size(32.dp).testTag("subtitle-result-previous")) {
                Icon(Icons.Filled.KeyboardArrowUp, "上一命中", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onNext, enabled = matched > 0,
                modifier = Modifier.size(32.dp).testTag("subtitle-result-next")) {
                Icon(Icons.Filled.KeyboardArrowDown, "下一命中", modifier = Modifier.size(20.dp))
            }
            Box {
                TextButton(onClick = { operationsOpen = true },
                    modifier = Modifier.testTag("r3-event-operations")) {
                    Text("更多", style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = operationsOpen, onDismissRequest = { operationsOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("选择全部匹配 $matched 条") },
                        enabled = matched > 0,
                        modifier = Modifier.testTag("subtitle-result-select-all"),
                        onClick = { operationsOpen = false; onSelectAll() },
                    )
                    DropdownMenuItem(
                        text = { Text("定位当前字幕") },
                        modifier = Modifier.testTag("subtitle-result-locate"),
                        onClick = { operationsOpen = false; onLocate() },
                    )
                    DropdownMenuItem(
                        text = { Text("清除筛选") },
                        enabled = filter.isActive,
                        modifier = Modifier.testTag("subtitle-result-empty-clear"),
                        onClick = { operationsOpen = false; onClear() },
                    )
                }
            }
        }
    }
    if (filterOpen) {
        AlertDialog(
            onDismissRequest = { filterOpen = false },
            title = { Text("字幕筛选") },
            text = {
                SubtitleFilterPane(
                    filter = filter,
                    onChange = onFilterChange,
                    onClose = { filterOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { filterOpen = false }) { Text("返回字幕列表") }
            },
        )
    }
}
