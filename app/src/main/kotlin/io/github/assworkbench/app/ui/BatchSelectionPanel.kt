package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var shift by remember { mutableStateOf("0") }
    var layer by remember { mutableStateOf("0") }
    var styleMenu by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("已选 " + state.selectedEventIds.size + " 条", modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::clearSelection) { Text("清除") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = shift,
                onValueChange = { shift = it },
                label = { Text("时间平移 ms") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.shiftSelected(shift.toLongOrNull() ?: 0L) }) { Text("平移") }
            TextButton(onClick = viewModel::alignSelectedStartToPlayback) { Text("首条→当前") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = layer,
                onValueChange = { layer = it },
                label = { Text("Layer") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.setSelectedLayer(layer.toIntOrNull() ?: 0) }) { Text("应用 Layer") }
            TextButton(onClick = { styleMenu = true }) { Text("指定 Style") }
            DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                state.document.styles.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.name) },
                        onClick = {
                            styleMenu = false
                            viewModel.assignSelectedStyle(style.name)
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { viewModel.setSelectedComment(false) }) { Text("Dialogue") }
            TextButton(onClick = { viewModel.setSelectedComment(true) }) { Text("Comment") }
            TextButton(onClick = viewModel::clearSelectedStyleOverrides) { Text("清覆盖") }
        }
        if (state.selectionAnchorId != null) {
            Text("区间起点 #" + state.selectionAnchorId + "：点另一条字幕完成选择。")
        } else {
            Text("长按字幕行可设为区间起点。")
        }
    }
}
