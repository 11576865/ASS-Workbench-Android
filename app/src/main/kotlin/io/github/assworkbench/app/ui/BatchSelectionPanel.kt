package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel

@Composable
fun BatchSelectionPanel(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf("0") }
    var layer by remember { mutableStateOf("0") }
    var styleMenu by remember { mutableStateOf(false) }
    var selectedStyle by remember(state.document.styles) {
        mutableStateOf(state.document.styles.firstOrNull()?.name.orEmpty())
    }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "已选 " + state.selectedEventIds.size,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
        )
        if (state.selectionAnchorId != null) {
            Text(
                "起点 #" + state.selectionAnchorId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        TextButton(onClick = { dialogOpen = true }, modifier = Modifier.height(28.dp)) {
            Text("批量…", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = viewModel::clearSelection, modifier = Modifier.height(28.dp)) {
            Text("清除", style = MaterialTheme.typography.labelSmall)
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("批量编辑 · " + state.selectedEventIds.size + " 条") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = shift,
                            onValueChange = { shift = it },
                            label = { Text("时间平移 ms") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.shiftSelected(shift.toLongOrNull() ?: 0L) }) {
                            Text("平移")
                        }
                        TextButton(onClick = viewModel::alignSelectedStartToPlayback) {
                            Text("首条→当前")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = layer,
                            onValueChange = { layer = it },
                            label = { Text("Layer") },
                            singleLine = true,
                            modifier = Modifier.width(90.dp),
                        )
                        TextButton(onClick = { viewModel.setSelectedLayer(layer.toIntOrNull() ?: 0) }) {
                            Text("应用")
                        }
                        Box {
                            TextButton(onClick = { styleMenu = true }) {
                                Text("Style · " + selectedStyle.ifBlank { "选择" })
                            }
                            DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                                state.document.styles.forEach { style ->
                                    DropdownMenuItem(
                                        text = { Text(style.name) },
                                        onClick = {
                                            selectedStyle = style.name
                                            styleMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { viewModel.assignSelectedStyle(selectedStyle) },
                            enabled = selectedStyle.isNotBlank(),
                        ) { Text("指定") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { viewModel.setSelectedComment(false) }) { Text("Dialogue") }
                        TextButton(onClick = { viewModel.setSelectedComment(true) }) { Text("Comment") }
                        TextButton(onClick = viewModel::clearSelectedStyleOverrides) { Text("清除覆盖") }
                    }
                    Text(
                        "批量操作均进入 Undo；时间平移保持每条字幕原时长。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) { Text("完成") }
            },
        )
    }
}
