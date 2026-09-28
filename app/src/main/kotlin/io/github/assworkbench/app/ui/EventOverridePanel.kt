package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssInlineSyntax
import io.github.assworkbench.domain.EventOverrideEditor

@Composable
fun EventOverridePanel(
    event: AssEvent,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val snapshot = EventOverrideEditor.inspect(event.text)
    var x by remember(event.id, event.text) { mutableStateOf(snapshot.x?.toString().orEmpty()) }
    var y by remember(event.id, event.text) { mutableStateOf(snapshot.y?.toString().orEmpty()) }
    var blur by remember(event.id, event.text) { mutableStateOf(snapshot.blur?.toString().orEmpty()) }
    var fadeIn by remember(event.id, event.text) { mutableStateOf(snapshot.fadeInMs?.toString().orEmpty()) }
    var fadeOut by remember(event.id, event.text) { mutableStateOf(snapshot.fadeOutMs?.toString().orEmpty()) }
    var softEntry by remember(event.id, event.text) { mutableStateOf(snapshot.softEntry) }
    val syntax = remember(event.text) { AssInlineSyntax.analyze(event.text) }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
            Text("事件效果 / 位置")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallOverrideField("X", x, { x = it }, Modifier.weight(1f))
                SmallOverrideField("Y", y, { y = it }, Modifier.weight(1f))
                SmallOverrideField("Blur", blur, { blur = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallOverrideField("Fade In ms", fadeIn, { fadeIn = it }, Modifier.weight(1f))
                SmallOverrideField("Fade Out ms", fadeOut, { fadeOut = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { viewModel.nudgeEventPosition(event.id, -5.0, 0.0) }) { Text("X−5") }
                OutlinedButton(onClick = { viewModel.nudgeEventPosition(event.id, 5.0, 0.0) }) { Text("X+5") }
                OutlinedButton(onClick = { viewModel.nudgeEventPosition(event.id, 0.0, -5.0) }) { Text("Y−5") }
                OutlinedButton(onClick = { viewModel.nudgeEventPosition(event.id, 0.0, 5.0) }) { Text("Y+5") }
            }
            Row {
                Checkbox(checked = softEntry, onCheckedChange = { softEntry = it })
                Text("Soft Entry（98% → 100%，160ms）")
            }
            androidx.compose.material3.Button(
                onClick = {
                    viewModel.applyEventOverrides(
                        id = event.id,
                        x = x.toDoubleOrNull(),
                        y = y.toDoubleOrNull(),
                        blur = blur.toDoubleOrNull(),
                        fadeInMs = fadeIn.toIntOrNull(),
                        fadeOutMs = fadeOut.toIntOrNull(),
                        softEntry = softEntry,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("应用事件效果") }

        Text(
            "ASS Event Text · " + syntax.tags.size + " tags" +
                if (syntax.hasErrors) " · " + syntax.issues.size + " syntax issue(s)" else ""
        )
        OutlinedTextField(
            value = event.text,
            onValueChange = { viewModel.updateEventText(event.id, it) },
            label = { Text("Event Text / Override Tags") },
            visualTransformation = rememberAssSyntaxTransformation(),
            isError = syntax.hasErrors,
            supportingText = if (syntax.hasErrors) {
                { Text(syntax.issues.first().message) }
            } else null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp, max = 240.dp),
        )
    }
}

@Composable
private fun SmallOverrideField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}
