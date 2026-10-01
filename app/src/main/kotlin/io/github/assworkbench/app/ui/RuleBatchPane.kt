package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    var styleFilter by rememberSaveable { mutableStateOf("") }
    var textFilter by rememberSaveable { mutableStateOf("") }
    var actorFilter by rememberSaveable { mutableStateOf("") }
    var layerFilter by rememberSaveable { mutableStateOf("") }
    var tagFilter by rememberSaveable { mutableStateOf("") }
    var commentFilter by rememberSaveable { mutableStateOf("ALL") }

    var shiftText by rememberSaveable { mutableStateOf("") }
    var styleText by rememberSaveable { mutableStateOf("") }
    var layerText by rememberSaveable { mutableStateOf("") }
    var actorText by rememberSaveable { mutableStateOf("") }
    var marginL by rememberSaveable { mutableStateOf("") }
    var marginR by rememberSaveable { mutableStateOf("") }
    var marginV by rememberSaveable { mutableStateOf("") }
    var findText by rememberSaveable { mutableStateOf("") }
    var replaceText by rememberSaveable { mutableStateOf("") }
    var commentAction by rememberSaveable { mutableStateOf("KEEP") }

    fun filter(): AssBatchFilter {
        val filters = buildList<AssBatchFilter> {
            if (selectedOnly) add(AssBatchFilter.EventIds(state.selectedEventIds))
            styleFilter.trim().takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.StyleIs(it)) }
            textFilter.takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.TextContains(it)) }
            actorFilter.takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.ActorContains(it)) }
            layerFilter.toIntOrNull()?.let { add(AssBatchFilter.LayerIs(it)) }
            tagFilter.trim().removePrefix("\\").takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.HasTag(it)) }
            when (commentFilter) {
                "DIALOGUE" -> add(AssBatchFilter.CommentIs(false))
                "COMMENT" -> add(AssBatchFilter.CommentIs(true))
            }
        }
        return when (filters.size) {
            0 -> AssBatchFilter.All
            1 -> filters.single()
            else -> AssBatchFilter.And(filters)
        }
    }

    fun recipe(): AssBatchRecipe {
        val actions = buildList<AssBatchAction> {
            shiftText.toLongOrNull()?.takeIf { it != 0L }?.let { add(AssBatchAction.ShiftTime(it)) }
            styleText.trim().takeIf(String::isNotEmpty)?.let { add(AssBatchAction.SetStyle(it)) }
            layerText.toIntOrNull()?.let { add(AssBatchAction.SetLayer(it)) }
            actorText.takeIf(String::isNotEmpty)?.let { add(AssBatchAction.SetActor(it)) }
            if (marginL.isNotBlank() || marginR.isNotBlank() || marginV.isNotBlank()) {
                add(AssBatchAction.SetMargins(marginL.toIntOrNull(), marginR.toIntOrNull(), marginV.toIntOrNull()))
            }
            findText.takeIf(String::isNotEmpty)?.let { add(AssBatchAction.ReplacePlainText(it, replaceText)) }
            when (commentAction) {
                "DIALOGUE" -> add(AssBatchAction.SetComment(false))
                "COMMENT" -> add(AssBatchAction.SetComment(true))
            }
        }
        return AssBatchRecipe("interactive-rule", filter(), actions)
    }

    val recipe = remember(
        state.selectedEventIds,
        selectedOnly, styleFilter, textFilter, actorFilter, layerFilter, tagFilter, commentFilter,
        shiftText, styleText, layerText, actorText, marginL, marginR, marginV, findText, replaceText, commentAction,
    ) { recipe() }
    val preview = remember(state.document, recipe) { AssBatchEngine.preview(state.document, recipe) }
    val changedExamples = remember(state.document, preview) {
        preview.changedEventIds.take(5).mapNotNull { id ->
            val before = state.document.events.firstOrNull { it.id == id } ?: return@mapNotNull null
            val after = preview.document.events.firstOrNull { it.id == id } ?: return@mapNotNull null
            before to after
        }
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("批处理规则", style = MaterialTheme.typography.titleMedium)
        Text(
            "Scope → 多条件 AND Filter → 多动作 Transform → Preview → Commit。整个规则只产生一个 Undo 节点。",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Scope / Filter", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selectedOnly, { selectedOnly = !selectedOnly }, { Text("仅已选 ${state.selectedEventIds.size}") })
            listOf("ALL", "DIALOGUE", "COMMENT").forEach { mode ->
                FilterChip(commentFilter == mode, { commentFilter = mode }, { Text(mode) })
            }
        }
        OutlinedTextField(styleFilter, { styleFilter = it }, label = { Text("Style =") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(textFilter, { textFilter = it }, label = { Text("正文 contains") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(actorFilter, { actorFilter = it }, label = { Text("Actor contains") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(layerFilter, { layerFilter = it }, label = { Text("Layer =") }, modifier = Modifier.weight(1f))
            OutlinedTextField(tagFilter, { tagFilter = it }, label = { Text("含 tag，例如 pos") }, modifier = Modifier.weight(1f))
        }

        HorizontalDivider()
        Text("Transform", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(shiftText, { shiftText = it }, label = { Text("时间平移 ms") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(styleText, { styleText = it }, label = { Text("设 Style") }, modifier = Modifier.weight(1f))
            OutlinedTextField(layerText, { layerText = it }, label = { Text("设 Layer") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(actorText, { actorText = it }, label = { Text("设 Actor/Name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(marginL, { marginL = it }, label = { Text("MarginL") }, modifier = Modifier.weight(1f))
            OutlinedTextField(marginR, { marginR = it }, label = { Text("MarginR") }, modifier = Modifier.weight(1f))
            OutlinedTextField(marginV, { marginV = it }, label = { Text("MarginV") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(findText, { findText = it }, label = { Text("正文查找") }, modifier = Modifier.weight(1f))
            OutlinedTextField(replaceText, { replaceText = it }, label = { Text("替换") }, modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("KEEP", "DIALOGUE", "COMMENT").forEach { mode ->
                FilterChip(commentAction == mode, { commentAction = mode }, { Text("类型 ${mode}") })
            }
        }

        HorizontalDivider()
        Text("Preview", style = MaterialTheme.typography.labelLarge)
        Text("命中 ${preview.affectedEventIds.size} 条 · 实际变化 ${preview.changedEventIds.size} 条")
        changedExamples.forEach { (before, after) ->
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.small) {
                Column(Modifier.fillMaxWidth().padding(6.dp)) {
                    Text("#${before.id} · ${before.start.millis}-${before.end.millis} → ${after.start.millis}-${after.end.millis}",
                        style = MaterialTheme.typography.labelSmall)
                    if (before.style != after.style || before.layer != after.layer) {
                        Text("${before.style}/L${before.layer} → ${after.style}/L${after.layer}",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    if (before.text != after.text) {
                        Text(AssInlineSyntax.visibleText(before.text) + " → " + AssInlineSyntax.visibleText(after.text),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            onClick = { viewModel.applyBatchRecipe(recipe) },
            enabled = preview.changedEventIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("应用为一个事务") }
    }
}
