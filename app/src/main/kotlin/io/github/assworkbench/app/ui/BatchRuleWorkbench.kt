package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*

@Composable
internal fun BatchRuleWorkbench(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var useSelection by rememberSaveable(state.selectedEventIds.isNotEmpty()) {
        mutableStateOf(state.selectedEventIds.isNotEmpty())
    }
    var styleFilter by rememberSaveable { mutableStateOf("") }
    var actorFilter by rememberSaveable { mutableStateOf("") }
    var textFilter by rememberSaveable { mutableStateOf("") }
    var regex by rememberSaveable { mutableStateOf(false) }
    var tagFilter by rememberSaveable { mutableStateOf("") }

    var shiftMs by rememberSaveable { mutableStateOf("") }
    var setLayer by rememberSaveable { mutableStateOf("") }
    var setStyle by rememberSaveable { mutableStateOf("") }
    var findText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var marginL by rememberSaveable { mutableStateOf("") }
    var marginR by rememberSaveable { mutableStateOf("") }
    var marginV by rememberSaveable { mutableStateOf("") }

    val rule = remember(
        state.selectedEventIds, useSelection, styleFilter, actorFilter, textFilter, regex, tagFilter,
        shiftMs, setLayer, setStyle, findText, replaceText, marginL, marginR, marginV,
    ) {
        BatchRule(
            filters = buildList {
                if (useSelection && state.selectedEventIds.isNotEmpty()) add(BatchFilter.EventIds(state.selectedEventIds))
                styleFilter.trim().takeIf { it.isNotEmpty() }?.let { add(BatchFilter.Style(it)) }
                actorFilter.trim().takeIf { it.isNotEmpty() }?.let { add(BatchFilter.Actor(it)) }
                textFilter.takeIf { it.isNotEmpty() }?.let {
                    add(if (regex) BatchFilter.TextRegex(it) else BatchFilter.TextContains(it))
                }
                tagFilter.trim().removePrefix("\\").takeIf { it.isNotEmpty() }?.let { add(BatchFilter.HasTag(it)) }
            },
            actions = buildList {
                shiftMs.toLongOrNull()?.takeIf { it != 0L }?.let { add(BatchAction.ShiftTime(it)) }
                setLayer.toIntOrNull()?.let { add(BatchAction.SetLayer(it)) }
                setStyle.trim().takeIf { it.isNotEmpty() }?.let { add(BatchAction.SetStyle(it)) }
                findText.takeIf { it.isNotEmpty() }?.let { add(BatchAction.ReplacePlainText(it, replaceText)) }
                if (listOf(marginL, marginR, marginV).any { it.isNotBlank() }) {
                    add(BatchAction.SetMargins(marginL.toIntOrNull(), marginR.toIntOrNull(), marginV.toIntOrNull()))
                }
            },
        )
    }
    val preview = remember(state.document, rule) { BatchRuleEngine.preview(state.document, rule) }

    Surface(modifier.fillMaxWidth(), tonalElevation = 2.dp, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("规则批处理", style = MaterialTheme.typography.titleSmall)
            Text(
                "Scope → Filters → Actions → Diff preview → 单次 Undo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(
                    checked = useSelection,
                    onCheckedChange = { useSelection = it },
                    enabled = state.selectedEventIds.isNotEmpty(),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (useSelection && state.selectedEventIds.isNotEmpty())
                        "Scope：选择 ${state.selectedEventIds.size} 条"
                    else "Scope：全部 Event"
                )
            }

            Text("Filters", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(styleFilter, { styleFilter = it }, label = { Text("Style") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(actorFilter, { actorFilter = it }, label = { Text("Actor") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(textFilter, { textFilter = it }, label = { Text(if (regex) "Text Regex" else "Text contains") }, singleLine = true, modifier = Modifier.weight(1f))
                FilterChip(selected = regex, onClick = { regex = !regex }, label = { Text("Regex") })
            }
            OutlinedTextField(tagFilter, { tagFilter = it }, label = { Text("Has tag，例如 pos / blur / k") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Actions", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(shiftMs, { shiftMs = it }, label = { Text("Shift ms") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(setLayer, { setLayer = it }, label = { Text("Layer") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(setStyle, { setStyle = it }, label = { Text("Set Style") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(findText, { findText = it }, label = { Text("正文查找") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(replaceText, { replaceText = it }, label = { Text("替换为") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(marginL, { marginL = it }, label = { Text("Margin L") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(marginR, { marginR = it }, label = { Text("R") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(marginV, { marginV = it }, label = { Text("V") }, singleLine = true, modifier = Modifier.weight(1f))
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Diff preview：${preview.affectedEventIds.size} / ${state.document.events.size} 条 · ${rule.actions.size} actions",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Button(
                onClick = { viewModel.applyBatchRule(rule) },
                enabled = rule.actions.isNotEmpty() && preview.affectedEventIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("应用规则 · 一个 Undo transaction")
            }
        }
    }
}
