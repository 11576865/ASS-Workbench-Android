package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import io.github.assworkbench.domain.BilingualPairing
import io.github.assworkbench.domain.SubtitlePairRow

@Composable
fun ReviewWorkspace(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val styles = state.document.styles.map { it.name }
    var sourceMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }

    val allRows = BilingualPairing.pair(
        document = state.document,
        sourceStyle = state.reviewSourceStyle,
        targetStyle = state.reviewTargetStyle,
    )
    val rows = allRows.filter { row ->
        val target = row.target
        val targetId = target?.id
        val modified = target != null && target.text != state.originalTextById[targetId]
        val confirmed = targetId != null && targetId in state.confirmedReviewIds
        when (state.reviewFilter) {
            "modified" -> modified
            "unreviewed" -> targetId != null && !confirmed
            "missing" -> row.missingSide
            "timing" -> row.timingMismatch
            else -> true
        }
    }

    Column(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewPicker(
                        label = "Source Style",
                        value = state.reviewSourceStyle,
                        items = styles,
                        expanded = sourceMenu,
                        onExpanded = { sourceMenu = it },
                        onPick = { viewModel.setReviewSourceStyle(it); sourceMenu = false },
                        modifier = Modifier.weight(1f),
                    )
                    ReviewPicker(
                        label = "Target Style",
                        value = state.reviewTargetStyle,
                        items = styles,
                        expanded = targetMenu,
                        onExpanded = { targetMenu = it },
                        onPick = { viewModel.setReviewTargetStyle(it); targetMenu = false },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Pair View：事件 " + state.document.events.size + " → 行 " + allRows.size,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { filterMenu = true }) {
                        Text("筛选：" + reviewFilterLabel(state.reviewFilter))
                    }
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        listOf(
                            "all" to "全部",
                            "modified" to "已修改",
                            "unreviewed" to "未确认",
                            "missing" to "缺失一侧",
                            "timing" to "时间不一致",
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setReviewFilter(value)
                                    filterMenu = false
                                },
                            )
                        }
                    }
                }
            }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                ReviewPairCard(row, state, viewModel)
            }
        }
    }
}

@Composable
private fun ReviewPairCard(
    row: SubtitlePairRow,
    state: EditorState,
    viewModel: EditorViewModel,
) {
    val target = row.target
    val targetId = target?.id
    val reference = targetId?.let { state.originalTextById[it] }.orEmpty()
    val modified = target != null && target.text != reference
    val confirmed = targetId != null && targetId in state.confirmedReviewIds
    var finalText by remember(targetId, target?.text) { mutableStateOf(target?.text.orEmpty()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = when {
                    row.missingSide -> "MISSING"
                    row.timingMismatch -> "TIMING"
                    modified -> "MODIFIED"
                    confirmed -> "CONFIRMED"
                    else -> "PAIR"
                }
                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                if (targetId != null) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = { viewModel.confirmReviewTarget(targetId, it) },
                    )
                    Text("确认", style = MaterialTheme.typography.labelSmall)
                }
            }

            val source = row.source
            Text(
                "SOURCE  " + (source?.start?.toAss() ?: "—") + " → " + (source?.end?.toAss() ?: "—"),
                style = MaterialTheme.typography.labelSmall,
            )
            if (source != null) {
                Text(rememberAssAnnotatedText(source.text))
            } else {
                Text("（缺失 Source）")
            }

            Text("REFERENCE", style = MaterialTheme.typography.labelSmall)
            if (target == null) {
                Text("（缺失 Target）")
            } else if (reference.isBlank()) {
                Text("（载入时为空）")
            } else {
                Text(rememberAssAnnotatedText(reference))
            }

            if (target != null) {
                OutlinedTextField(
                    value = finalText,
                    onValueChange = { finalText = it },
                    label = { Text("FINAL / ASS Event Text") },
                    visualTransformation = rememberAssSyntaxTransformation(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { viewModel.updateEventText(target.id, finalText) },
                        enabled = finalText != target.text,
                    ) { Text("应用修改") }
                    TextButton(onClick = {
                        finalText = reference
                        viewModel.updateEventText(target.id, reference)
                    }) { Text("恢复 Reference") }
                    val jumpId = row.source?.id ?: target.id
                    TextButton(onClick = { viewModel.focusEvent(jumpId, seek = true) }) { Text("跳转") }
                }
            }

            if (row.startDeltaMs != null && row.endDeltaMs != null) {
                Text(
                    "Δstart " + row.startDeltaMs + " ms · Δend " + row.endDeltaMs + " ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.timingMismatch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReviewPicker(
    label: String,
    value: String,
    items: List<String>,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = { onExpanded(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(value.ifBlank { "选择" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { onPick(item) })
            }
        }
    }
}

private fun reviewFilterLabel(value: String): String = when (value) {
    "modified" -> "已修改"
    "unreviewed" -> "未确认"
    "missing" -> "缺失一侧"
    "timing" -> "时间不一致"
    else -> "全部"
}
