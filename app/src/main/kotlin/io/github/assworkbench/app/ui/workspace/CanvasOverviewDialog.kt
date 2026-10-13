package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The list keeps even sub-pixel/overlapping map markers reachable at normal touch size. */
@Composable
internal fun CanvasOverviewDialog(
    entries: List<InfiniteCanvasEntry>, nodes: List<InfiniteCanvasNode>, enabled: Boolean,
    onDismiss: () -> Unit, onFocus: (String) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var hiddenOnly by rememberSaveable { mutableStateOf(false) }
    val available = filterCanvasOverviewEntries(entries, nodes, query = "")
    val filtered = filterCanvasOverviewEntries(entries, nodes, searchQuery, hiddenOnly)
    val matchedIds = filtered.mapTo(mutableSetOf()) { it.first.id }
    // Keep original map bounds fixed while typing: a result filter must not
    // move all the visual landmarks on every keystroke.
    val map = canvasOverview(available.map { it.second })
    val selectableMap = map.filter { it.id in matchedIds }
    val shownColor = MaterialTheme.colorScheme.primary
    val hiddenColor = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val latestFocus by rememberUpdatedState(onFocus)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("spatial-birdseye-dialog"),
        title = { Text("工作区鸟瞰") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        label = { Text("查找工具") },
                        modifier = Modifier.weight(1f).testTag("spatial-birdseye-search"),
                    )
                    if (searchQuery.isNotEmpty()) TextButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.testTag("spatial-birdseye-clear-search"),
                    ) { Text("清空") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = hiddenOnly,
                        onClick = { hiddenOnly = !hiddenOnly },
                        label = { Text("仅已收回") },
                        modifier = Modifier.testTag("spatial-birdseye-filter-hidden"),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${filtered.size}/${available.size} 个工具",
                        style = MaterialTheme.typography.labelSmall)
                }
                Canvas(Modifier.size(180.dp).align(Alignment.CenterHorizontally).clipToBounds()
                    .testTag("spatial-birdseye-map")
                    .semantics { contentDescription = "工具空间分布；下方列表可定位每个工具" }
                    .pointerInput(selectableMap, enabled) {
                        detectTapGestures { point ->
                            if (enabled && size.width > 0 && size.height > 0) {
                                hitCanvasOverview(selectableMap, point.x / size.width, point.y / size.height)
                                    ?.let(latestFocus)
                            }
                        }
                    }) {
                    drawRect(background)
                    map.sortedBy { it.z }.forEach { node ->
                        val color = if (node.hidden) hiddenColor else shownColor
                        // Unmatched nodes remain faint landmarks; only matches
                        // are touch-selectable or selectable from the list.
                        drawRect(if (node.id in matchedIds) color else color.copy(alpha = 0.15f),
                            topLeft = Offset((node.left * size.width).coerceAtMost(size.width - 3f),
                                (node.top * size.height).coerceAtMost(size.height - 3f)),
                            size = Size(maxOf(3f, (node.right - node.left) * size.width),
                                maxOf(3f, (node.bottom - node.top) * size.height)))
                    }
                }
                Text("选择图中工具或下方名称定位；灰色表示已收回，淡色表示未匹配。",
                    Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (available.isEmpty()) {
                    Text("当前没有工具", modifier = Modifier.testTag("spatial-birdseye-empty"))
                } else if (filtered.isEmpty()) {
                    Text("没有符合条件的工具，可清空搜索或关闭筛选。",
                        modifier = Modifier.testTag("spatial-birdseye-no-results"))
                }
                filtered.forEach { (entry, node) ->
                    TextButton(enabled = enabled, onClick = { onFocus(entry.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("spatial-birdseye-node-" + entry.id.replace(':', '-'))) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(entry.title + if (node.hidden) " · 已收回" else "")
                            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
