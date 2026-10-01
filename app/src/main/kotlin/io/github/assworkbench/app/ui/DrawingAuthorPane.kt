package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*

@Composable
internal fun DrawingAuthorPane(event: AssEvent, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    val inspection = remember(event.text) { AssDrawingCodec.inspect(event.text) }
    val drawing = inspection?.drawing
    var dx by rememberSaveable(event.id) { mutableStateOf("0") }
    var dy by rememberSaveable(event.id) { mutableStateOf("0") }
    var sx by rememberSaveable(event.id) { mutableStateOf("1") }
    var sy by rememberSaveable(event.id) { mutableStateOf("1") }
    var baseline by rememberSaveable(event.id, drawing?.baselineOffset) { mutableStateOf(drawing?.baselineOffset?.toString().orEmpty()) }
    var scaleText by rememberSaveable(event.id, drawing?.scale) { mutableStateOf((drawing?.scale ?: 1).toString()) }

    Column(modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("ASS Drawing · \\p / \\pbo", style = MaterialTheme.typography.titleMedium)
        if (drawing == null) {
            Text("当前 Event 没有 Drawing。可创建基础矩形，再直接编辑完整 m/n/l/b/s/p/c token。")
            Button(
                onClick = {
                    val created = AssDrawingCodec.rectangle(0.0, 0.0, 200.0, 80.0)
                    viewModel.updateEventText(event.id, AssDrawingCodec.insert(event.text, created))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("创建 200×80 Drawing") }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(scaleText, { scaleText = it }, label = { Text("\\p scale") }, modifier = Modifier.weight(1f))
            OutlinedTextField(baseline, { baseline = it }, label = { Text("\\pbo") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                val next = drawing.copy(
                    scale = scaleText.toIntOrNull()?.coerceAtLeast(1) ?: drawing.scale,
                    baselineOffset = baseline.takeIf(String::isNotBlank)?.toDoubleOrNull(),
                )
                viewModel.updateEventText(event.id, AssDrawingCodec.patch(event.text, next))
            }) { Text("应用") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(dx, { dx = it }, label = { Text("ΔX") }, modifier = Modifier.weight(1f))
            OutlinedTextField(dy, { dy = it }, label = { Text("ΔY") }, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                val next = AssDrawingCodec.translate(drawing, dx.toDoubleOrNull() ?: 0.0, dy.toDoubleOrNull() ?: 0.0)
                viewModel.updateEventText(event.id, AssDrawingCodec.patch(event.text, next))
            }) { Text("平移") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(sx, { sx = it }, label = { Text("Scale X") }, modifier = Modifier.weight(1f))
            OutlinedTextField(sy, { sy = it }, label = { Text("Scale Y") }, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                val next = AssDrawingCodec.scale(drawing, sx.toDoubleOrNull() ?: 1.0, sy.toDoubleOrNull() ?: 1.0)
                viewModel.updateEventText(event.id, AssDrawingCodec.patch(event.text, next))
            }) { Text("缩放路径") }
        }
        Text(
            "Path · \${AssDrawingCodec.renderPath(drawing.tokens)}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(drawing.tokens) { index, token ->
                if (token.command != null) {
                    Text("\${index}: \${token.command}", style = MaterialTheme.typography.labelLarge)
                } else {
                    OutlinedTextField(
                        value = token.number?.toString().orEmpty(),
                        onValueChange = { raw ->
                            val value = raw.toDoubleOrNull() ?: return@OutlinedTextField
                            val nextTokens = drawing.tokens.toMutableList()
                            nextTokens[index] = token.copy(number = value)
                            viewModel.updateEventText(
                                event.id,
                                AssDrawingCodec.patch(event.text, drawing.copy(tokens = nextTokens)),
                            )
                        },
                        label = { Text("token #\$index") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
