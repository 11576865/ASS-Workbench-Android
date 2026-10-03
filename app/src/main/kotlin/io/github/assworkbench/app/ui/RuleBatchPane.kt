package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorUiBatchDefaultScope
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.EditorViewModelUiActions
import io.github.assworkbench.app.toEditorUiState
import io.github.assworkbench.domain.*

@Composable
internal fun RuleBatchPane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState = state.toEditorUiState()
    val uiActions = remember(viewModel) { EditorViewModelUiActions(viewModel) }
    val hasSelection = uiState.batch.selectedEventCount > 0
    var selectedOnly by rememberSaveable(uiState.batch.defaultScope) {
        mutableStateOf(uiState.batch.defaultScope == EditorUiBatchDefaultScope.SELECTION)
    }
    var styleFilter by rememberSaveable { mutableStateOf("") }
    var textFilter by rememberSaveable { mutableStateOf("") }
    var actorFilter by rememberSaveable { mutableStateOf("") }
    var layerFilter by rememberSaveable { mutableStateOf("") }
    var tagFilter by rememberSaveable { mutableStateOf("") }
    var commentFilter by rememberSaveable { mutableStateOf("ALL") }
    var rawRegexFilter by rememberSaveable { mutableStateOf("") }
    var durationMin by rememberSaveable { mutableStateOf("") }
    var durationMax by rememberSaveable { mutableStateOf("") }

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
    var regexFind by rememberSaveable { mutableStateOf("") }
    var regexReplace by rememberSaveable { mutableStateOf("") }
    var regexRaw by rememberSaveable { mutableStateOf(false) }
    var timingOrigin by rememberSaveable { mutableStateOf("0") }
    var timingNumerator by rememberSaveable { mutableStateOf("") }
    var timingDenominator by rememberSaveable { mutableStateOf("") }
    var overridePropertyName by rememberSaveable { mutableStateOf(AssTransformVisualProperty.BORDER.name) }
    var overrideValue by rememberSaveable { mutableStateOf("") }

    var karaokeRevealEnabled by rememberSaveable { mutableStateOf(false) }
    var karaokeRevealMs by rememberSaveable { mutableStateOf("160") }
    var karaokeRevealBlur by rememberSaveable { mutableStateOf("3.5") }
    var karaokeRevealAccel by rememberSaveable { mutableStateOf("") }

    val parsedKaraokeRevealMs = karaokeRevealMs.toLongOrNull()
    val parsedKaraokeRevealBlur = karaokeRevealBlur.toDoubleOrNull()
    val parsedKaraokeRevealAccel = karaokeRevealAccel.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val karaokeRevealSpec = if (
        parsedKaraokeRevealMs != null && parsedKaraokeRevealMs >= 0L &&
        parsedKaraokeRevealBlur?.let { it.isFinite() && it in 0.0..20.0 } == true &&
        (karaokeRevealAccel.isBlank() || parsedKaraokeRevealAccel?.let { it.isFinite() && it > 0.0 } == true)
    ) {
        AssKaraokeRevealFxSpec(
            revealMs = parsedKaraokeRevealMs,
            startBlur = parsedKaraokeRevealBlur,
            accel = parsedKaraokeRevealAccel,
        )
    } else null

    fun filter(): AssBatchFilter {
        val filters = buildList<AssBatchFilter> {
            if (selectedOnly) add(AssBatchFilter.EventIds(uiState.selection.eventIds))
            if (karaokeRevealEnabled && karaokeRevealSpec != null) {
                add(AssBatchFilter.KaraokeRevealCompatible(karaokeRevealSpec))
            }
            styleFilter.trim().takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.StyleIs(it)) }
            textFilter.takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.TextContains(it)) }
            actorFilter.takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.ActorContains(it)) }
            layerFilter.toIntOrNull()?.let { add(AssBatchFilter.LayerIs(it)) }
            tagFilter.trim().removePrefix("\\").takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.HasTag(it)) }
            when (commentFilter) {
                "DIALOGUE" -> add(AssBatchFilter.CommentIs(false))
                "COMMENT" -> add(AssBatchFilter.CommentIs(true))
            }
            rawRegexFilter.takeIf(String::isNotBlank)?.let { raw ->
                runCatching { Regex(raw) }.getOrNull()?.let { add(AssBatchFilter.RawRegex(it)) }
            }
            val minDuration = durationMin.toLongOrNull()
            val maxDuration = durationMax.toLongOrNull()
            if (minDuration != null || maxDuration != null) {
                add(AssBatchFilter.DurationRange(minDuration ?: 0L, maxDuration ?: Long.MAX_VALUE))
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
            regexFind.takeIf(String::isNotBlank)?.let { raw ->
                runCatching { Regex(raw) }.getOrNull()?.let { pattern ->
                    add(
                        if (regexRaw) AssBatchAction.ReplaceRawRegex(pattern, regexReplace)
                        else AssBatchAction.ReplaceVisibleRegex(pattern, regexReplace)
                    )
                }
            }
            val numerator = timingNumerator.toLongOrNull()
            val denominator = timingDenominator.toLongOrNull()
            if (numerator != null && denominator != null && numerator > 0 && denominator > 0) {
                add(AssBatchAction.ScaleTiming(timingOrigin.toLongOrNull() ?: 0L, numerator, denominator))
            }
            overrideValue.toDoubleOrNull()?.let { value ->
                AssTransformVisualProperty.entries.firstOrNull { it.name == overridePropertyName }?.let { property ->
                    add(AssBatchAction.SetNumericOverride(property, value))
                }
            }
            if (karaokeRevealEnabled) {
                karaokeRevealSpec?.let { add(AssBatchAction.ApplyKaraokeRevealFx(it)) }
            }
        }
        return AssBatchRecipe("interactive-rule", filter(), actions)
    }

    val recipe = remember(
        uiState.selection.eventIds,
        selectedOnly, styleFilter, textFilter, actorFilter, layerFilter, tagFilter, commentFilter,
        rawRegexFilter, durationMin, durationMax,
        shiftText, styleText, layerText, actorText, marginL, marginR, marginV, findText, replaceText, commentAction,
        regexFind, regexReplace, regexRaw, timingOrigin, timingNumerator, timingDenominator, overridePropertyName, overrideValue,
        karaokeRevealEnabled, karaokeRevealMs, karaokeRevealBlur, karaokeRevealAccel,
    ) { recipe() }
    val previewResult = remember(state.document, recipe) {
        runCatching { AssBatchEngine.preview(state.document, recipe) }
    }
    val preview = previewResult.getOrNull()
    val previewError = previewResult.exceptionOrNull()?.message
    val changedExamples = remember(state.document, preview) {
        if (preview == null) {
            emptyList()
        } else {
            preview.changedEventIds.take(5).mapNotNull { id ->
                val before = state.document.events.firstOrNull { it.id == id } ?: return@mapNotNull null
                val after = preview.document.events.firstOrNull { it.id == id } ?: return@mapNotNull null
                before to after
            }
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

        if (uiState.batch.selectedEventCount > 1) {
            Text("已选字幕结构", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ viewModel.mergeSelected(useLineBreak = true) }) { Text("合并 · 换行") }
                OutlinedButton({ viewModel.mergeSelected(useLineBreak = false) }) { Text("合并 · 空格") }
            }
            Text("合并直接作用于已选字幕并可撤销；下方规则需预览后提交。", style = MaterialTheme.typography.labelSmall)
        }
        Text("Scope / Filter", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selectedOnly, { selectedOnly = !selectedOnly }, { Text("仅已选 ${uiState.batch.selectedEventCount}") })
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
        OutlinedTextField(rawRegexFilter, { rawRegexFilter = it }, label = { Text("Raw ASS Regex filter") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(durationMin, { durationMin = it }, label = { Text("最短时长 ms") }, modifier = Modifier.weight(1f))
            OutlinedTextField(durationMax, { durationMax = it }, label = { Text("最长时长 ms") }, modifier = Modifier.weight(1f))
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
        Text("高级 Transform", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(regexFind, { regexFind = it }, label = { Text("Regex 查找") }, modifier = Modifier.weight(1f))
            OutlinedTextField(regexReplace, { regexReplace = it }, label = { Text("Regex 替换") }, modifier = Modifier.weight(1f))
        }
        FilterChip(regexRaw, { regexRaw = !regexRaw }, { Text(if (regexRaw) "替换 Raw ASS" else "仅替正文") })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(timingOrigin, { timingOrigin = it }, label = { Text("缩放原点 ms") }, modifier = Modifier.weight(1f))
            OutlinedTextField(timingNumerator, { timingNumerator = it }, label = { Text("时间倍率分子") }, modifier = Modifier.weight(1f))
            OutlinedTextField(timingDenominator, { timingDenominator = it }, label = { Text("分母") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(overridePropertyName, { overridePropertyName = it }, label = { Text("数值 override 属性") }, modifier = Modifier.weight(1f))
            OutlinedTextField(overrideValue, { overrideValue = it }, label = { Text("值") }, modifier = Modifier.weight(1f))
        }

        Surface(
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().testTag("batch-karaoke-reveal"),
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(
                        checked = karaokeRevealEnabled,
                        onCheckedChange = { karaokeRevealEnabled = it },
                        modifier = Modifier.testTag("batch-karaoke-reveal-enabled"),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Karaoke · 逐音节显现 FX", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "启用后只让当前参数下可安全编译的 Karaoke Event 进入批次。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (karaokeRevealEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            karaokeRevealMs,
                            { karaokeRevealMs = it },
                            label = { Text("显现 ms") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("batch-karaoke-reveal-ms"),
                        )
                        OutlinedTextField(
                            karaokeRevealBlur,
                            { karaokeRevealBlur = it },
                            label = { Text("起始 Blur") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("batch-karaoke-reveal-blur"),
                        )
                        OutlinedTextField(
                            karaokeRevealAccel,
                            { karaokeRevealAccel = it },
                            label = { Text("Accel") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("batch-karaoke-reveal-accel"),
                        )
                    }
                    if (karaokeRevealSpec == null) {
                        Text(
                            "参数无效：显现时长需 ≥ 0；Blur 0..20；Accel 为空或 > 0。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "初始不兼容 Event 会被筛掉；若同一批处理中的前序动作使 Event 在执行阶段失去兼容性，预览/提交会整体失败，避免只应用部分动作。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Preview", style = MaterialTheme.typography.labelLarge)
        if (preview == null) {
            Text(
                "预览失败：" + (previewError ?: "unknown"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("batch-preview-error"),
            )
        } else {
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
        }
        Button(
            onClick = { uiActions.applyBatchRecipe(recipe) },
            enabled = preview?.changedEventIds?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("应用为一个事务") }

        SemanticSearchReplacePane(state, viewModel)
    }
}
