package io.github.assworkbench.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val available = entries.mapNotNull { entry -> nodes.firstOrNull { it.id == entry.id }?.let { entry to it } }
    val map = canvasOverview(available.map { it.second })
    val shownColor = MaterialTheme.colorScheme.primary
    val hiddenColor = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val latestFocus by rememberUpdatedState(onFocus)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("spatial-birdseye-dialog"),
        title = { Text("工作区鸟瞰") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Canvas(Modifier.size(180.dp).align(Alignment.CenterHorizontally).clipToBounds()
                    .testTag("spatial-birdseye-map")
                    .semantics { contentDescription = "工具空间分布；下方列表可定位每个工具" }
                    .pointerInput(map, enabled) {
                        detectTapGestures { point ->
                            if (enabled && size.width > 0 && size.height > 0) {
                                hitCanvasOverview(map, point.x / size.width, point.y / size.height)?.let(latestFocus)
                            }
                        }
                    }) {
                    drawRect(background)
                    map.sortedBy { it.z }.forEach { node ->
                        drawRect(if (node.hidden) hiddenColor else shownColor,
                            topLeft = Offset((node.left * size.width).coerceAtMost(size.width - 3f),
                                (node.top * size.height).coerceAtMost(size.height - 3f)),
                            size = Size(maxOf(3f, (node.right - node.left) * size.width),
                                maxOf(3f, (node.bottom - node.top) * size.height)))
                    }
                }
                Text("点击图中工具或下方名称定位；灰色表示已收回。",
                    Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (available.isEmpty()) Text("当前没有工具")
                available.forEach { (entry, node) ->
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
