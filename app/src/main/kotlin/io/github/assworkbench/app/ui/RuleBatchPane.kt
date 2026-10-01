package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*

@Composable
internal fun RuleBatchPane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var filterType by rememberSaveable { mutableStateOf("ALL") }
    var filterValue by rememberSaveable { mutableStateOf("") }
    var shiftText by rememberSaveable { mutableStateOf("") }
    var styleText by rememberSaveable { mutableStateOf("") }
    var layerText by rememberSaveable { mutableStateOf("") }
    var findText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }

    fun filter(): AssBatchFilter = when (filterType) {
        "STYLE" -> AssBatchFilter.StyleIs(filterValue)
        "TEXT" -> AssBatchFilter.TextContains(filterValue)
        "ACTOR" -> AssBatchFilter.ActorContains(filterValue)
        "LAYER" -> AssBatchFilter.LayerIs(filterValue.toIntOrNull() ?: Int.MIN_VALUE)
        "TAG" -> AssBatchFilter.HasTag(filterValue.removePrefix("\\"))
        else -> AssBatchFilter.All
    }

    fun recipe(): AssBatchRecipe {
        val actions = buildList<AssBatchAction> {
            shiftText.toLongOrNull()?.takeIf { it != 0L }?.let { add(AssBatchAction.ShiftTime(it)) }
            styleText.trim().takeIf { it.isNotEmpty() }?.let { add(AssBatchAction.SetStyle(it)) }
            layerText.toIntOrNull()?.let { add(AssBatchAction.SetLayer(it)) }
            findText.takeIf { it.isNotEmpty() }?.let { add(AssBatchAction.ReplacePlainText(it, replaceText)) }
        }
        return AssBatchRecipe("interactive-rule", filter(), actions)
    }

    val preview = remember(
        state.document, filterType, filterValue, shiftText, styleText, layerText, findText, replaceText
    ) {
        AssBatchEngine.preview(state.document, recipe())
    }

    Column(
        modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("批处理规则", style = MaterialTheme.typography.titleMedium)
        Text(
            "Scope → Filter → Transform → Preview → Commit；一次应用只产生一个 Undo 节点。",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("ALL", "STYLE", "TEXT", "ACTOR", "LAYER", "TAG").forEach { item ->
                FilterChip(
                    selected = filterType == item,
                    onClick = { filterType = item },
                    label = { Text(item) },
                )
            }
        }
        if (filterType != "ALL") {
            OutlinedTextField(
                filterValue,
                { filterValue = it },
                label = { Text("Filter 值") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            shiftText,
            { shiftText = it },
            label = { Text("时间平移 ms（可空）") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            styleText,
            { styleText = it },
            label = { Text("设为 Style（可空）") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            layerText,
            { layerText = it },
            label = { Text("设为 Layer（可空）") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                findText,
                { findText = it },
                label = { Text("正文查找") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                replaceText,
                { replaceText = it },
                label = { Text("替换") },
                modifier = Modifier.weight(1f),
            )
        }
        Text("命中 ${preview.affectedEventIds.size} 条 · 实际变化 ${preview.changedEventIds.size} 条")
        Button(
            onClick = { viewModel.applyBatchRecipe(recipe()) },
            enabled = preview.changedEventIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("应用为一个事务")
        }
    }
}
