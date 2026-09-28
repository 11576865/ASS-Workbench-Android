package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var effectOpen by remember { mutableStateOf(false) }
    val syntax = remember(event.text) { AssInlineSyntax.analyze(event.text) }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "位置 · 预览十字可直接拖动",
                modifier = Modifier.weight(1f),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
            TextButton(onClick = { effectOpen = !effectOpen }, modifier = Modifier.height(28.dp)) {
                Text(if (effectOpen) "收起效果" else "效果", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallOverrideField("X", x, { x = it }, Modifier.weight(1f))
            SmallOverrideField("Y", y, { y = it }, Modifier.weight(1f))
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
                modifier = Modifier.height(44.dp),
            ) { Text("应用") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                "X−5" to Pair(-5.0, 0.0),
                "X+5" to Pair(5.0, 0.0),
                "Y−5" to Pair(0.0, -5.0),
                "Y+5" to Pair(0.0, 5.0),
            ).forEach { (label, delta) ->
                TextButton(
                    onClick = { viewModel.nudgeEventPosition(event.id, delta.first, delta.second) },
                    modifier = Modifier.height(28.dp),
                ) { Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
            }
        }

        if (effectOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SmallOverrideField("Blur", blur, { blur = it }, Modifier.weight(1f))
                SmallOverrideField("Fade In", fadeIn, { fadeIn = it }, Modifier.weight(1f))
                SmallOverrideField("Fade Out", fadeOut, { fadeOut = it }, Modifier.weight(1f))
            }
            Row {
                Checkbox(checked = softEntry, onCheckedChange = { softEntry = it })
                Text("Soft Entry · 160ms", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }

        Text(
            "Event Text · " + syntax.tags.size + " tags" +
                if (syntax.hasErrors) " · " + syntax.issues.size + " issue" else "",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = if (syntax.hasErrors) androidx.compose.material3.MaterialTheme.colorScheme.error
                else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = event.text,
            onValueChange = { viewModel.updateEventText(event.id, it) },
            visualTransformation = rememberAssSyntaxTransformation(),
            isError = syntax.hasErrors,
            supportingText = if (syntax.hasErrors) {
                { Text(syntax.issues.first().message) }
            } else null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp, max = 220.dp),
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
