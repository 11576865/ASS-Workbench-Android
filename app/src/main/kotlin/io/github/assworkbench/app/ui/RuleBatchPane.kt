package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorUiBatchDefaultScope
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.app.toEditorUiState
import io.github.assworkbench.domain.*

@Composable
internal fun RuleBatchPane(
    state: EditorState,
    viewModel: EditorViewModel,
    instanceId: String,
    modifier: Modifier = Modifier,
) {
    val uiState = state.toEditorUiState()
    val draftEntry = LocalBatchAuthoringDraftStore.current.obtain(instanceId)
    var scopeName by draftEntry.text("rule.scope", AssBatchScope.default(uiState.batch.defaultScope == EditorUiBatchDefaultScope.SELECTION).name)
    val scope = AssBatchScope.entries.firstOrNull { it.name == scopeName } ?: AssBatchScope.RESULTS
    val scopeIds = remember(state.document.events, state.selectedEventIds, state.subtitleFilter, scope) {
        scope.eventIds(state.document.events, state.selectedEventIds, state.subtitleFilter).toSet()
    }
    var styleFilter by draftEntry.text("rule.styleFilter", "")
    var textFilter by draftEntry.text("rule.textFilter", "")
    var actorFilter by draftEntry.text("rule.actorFilter", "")
    var layerFilter by draftEntry.text("rule.layerFilter", "")
    var tagFilter by draftEntry.text("rule.tagFilter", "")
    var commentFilter by draftEntry.text("rule.commentFilter", "ALL")
    var rawRegexFilter by draftEntry.text("rule.rawRegexFilter", "")
    var durationMin by draftEntry.text("rule.durationMin", "")
    var durationMax by draftEntry.text("rule.durationMax", "")

    var shiftText by draftEntry.text("rule.shiftText", "")
    var styleText by draftEntry.text("rule.styleText", "")
    var layerText by draftEntry.text("rule.layerText", "")
    var actorText by draftEntry.text("rule.actorText", "")
    var marginL by draftEntry.text("rule.marginL", "")
    var marginR by draftEntry.text("rule.marginR", "")
    var marginV by draftEntry.text("rule.marginV", "")
    var findText by draftEntry.text("rule.findText", "")
    var replaceText by draftEntry.text("rule.replaceText", "")
    var commentAction by draftEntry.text("rule.commentAction", "KEEP")
    var regexFind by draftEntry.text("rule.regexFind", "")
    var regexReplace by draftEntry.text("rule.regexReplace", "")
    var regexRaw by draftEntry.flag("rule.regexRaw", false)
    var timingOrigin by draftEntry.text("rule.timingOrigin", "0")
    var timingNumerator by draftEntry.text("rule.timingNumerator", "")
    var timingDenominator by draftEntry.text("rule.timingDenominator", "")
    var overridePropertyName by draftEntry.text("rule.overridePropertyName", AssTransformVisualProperty.BORDER.name)
    var overrideValue by draftEntry.text("rule.overrideValue", "")

    var karaokeRevealEnabled by draftEntry.flag("rule.karaokeRevealEnabled", false)
    var karaokeRevealMs by draftEntry.text("rule.karaokeRevealMs", "160")
    var karaokeRevealBlur by draftEntry.text("rule.karaokeRevealBlur", "3.5")
    var karaokeRevealAccel by draftEntry.text("rule.karaokeRevealAccel", "")

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

    val parsedLayerFilter = layerFilter.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val parsedTagFilter = tagFilter.trim().removePrefix("\\").takeIf { it.isNotEmpty() }
    val tagFilterValid = parsedTagFilter == null || Regex("""[A-Za-z0-9_-]+""").matches(parsedTagFilter)
    val parsedRawRegexFilter = rawRegexFilter.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { Regex(raw) }.getOrNull()
    }
    val parsedDurationMin = durationMin.takeIf { it.isNotBlank() }?.toLongOrNull()
    val parsedDurationMax = durationMax.takeIf { it.isNotBlank() }?.toLongOrNull()
    val parsedShift = shiftText.takeIf { it.isNotBlank() }?.toLongOrNull()
    val parsedStyleAction = styleText.trim().takeIf { it.isNotEmpty() }
    val parsedLayerAction = layerText.takeIf { it.isNotBlank() }?.toIntOrNull()
    val parsedMarginL = marginL.takeIf { it.isNotBlank() }?.toIntOrNull()
    val parsedMarginR = marginR.takeIf { it.isNotBlank() }?.toIntOrNull()
    val parsedMarginV = marginV.takeIf { it.isNotBlank() }?.toIntOrNull()
    val parsedReplaceRegex = regexFind.takeIf { it.isNotBlank() }?.let { raw ->
        runCatching { Regex(raw) }.getOrNull()
    }
    val parsedTimingOrigin = timingOrigin.toLongOrNull()
    val parsedTimingNumerator = timingNumerator.takeIf { it.isNotBlank() }?.toLongOrNull()
    val parsedTimingDenominator = timingDenominator.takeIf { it.isNotBlank() }?.toLongOrNull()
    val timingScaleRequested =
        timingOrigin.trim() != "0" || timingNumerator.isNotBlank() || timingDenominator.isNotBlank()
    val parsedOverrideProperty = AssTransformVisualProperty.entries
        .firstOrNull { it.name == overridePropertyName }
    val parsedOverrideValue = overrideValue.takeIf { it.isNotBlank() }?.toDoubleOrNull()

    val batchInputError = buildList {
        if (layerFilter.isNotBlank() && parsedLayerFilter == null) {
            add("Layer Filter 必须是整数。")
        }
        if (!tagFilterValid) {
            add("Tag Filter 只能包含字母、数字、下划线或连字符。")
        }
        if (rawRegexFilter.isNotBlank() && parsedRawRegexFilter == null) {
            add("Raw ASS Regex filter 无效。")
        }
        if (durationMin.isNotBlank() && (parsedDurationMin == null || parsedDurationMin < 0L)) {
            add("最短时长必须是 ≥ 0 的整数毫秒。")
        }
        if (durationMax.isNotBlank() && (parsedDurationMax == null || parsedDurationMax < 0L)) {
            add("最长时长必须是 ≥ 0 的整数毫秒。")
        }
        if (parsedDurationMin != null && parsedDurationMax != null && parsedDurationMin > parsedDurationMax) {
            add("最短时长不能大于最长时长。")
        }
        if (shiftText.isNotBlank() && parsedShift == null) {
            add("时间平移必须是整数毫秒。")
        }
        if (
            parsedStyleAction != null &&
            state.document.styles.none { it.name == parsedStyleAction }
        ) {
            add("目标 Style 不存在：$parsedStyleAction")
        }
        if (layerText.isNotBlank() && parsedLayerAction == null) {
            add("目标 Layer 必须是整数。")
        }
        if (marginL.isNotBlank()) {
            if (parsedMarginL == null) add("MarginL 必须是整数。")
            else if (parsedMarginL < 0) add("MarginL 不能为负数。")
        }
        if (marginR.isNotBlank()) {
            if (parsedMarginR == null) add("MarginR 必须是整数。")
            else if (parsedMarginR < 0) add("MarginR 不能为负数。")
        }
        if (marginV.isNotBlank()) {
            if (parsedMarginV == null) add("MarginV 必须是整数。")
            else if (parsedMarginV < 0) add("MarginV 不能为负数。")
        }
        if (regexFind.isNotBlank() && parsedReplaceRegex == null) {
            add("Regex 查找表达式无效。")
        }
        if (timingScaleRequested) {
            if (parsedTimingOrigin == null) add("时间缩放原点必须是整数毫秒。")
            if (parsedTimingNumerator == null || parsedTimingNumerator <= 0L) {
                add("时间倍率分子必须是正整数。")
            }
            if (parsedTimingDenominator == null || parsedTimingDenominator <= 0L) {
                add("时间倍率分母必须是正整数。")
            }
        }
        if (overrideValue.isNotBlank()) {
            if (parsedOverrideProperty == null) {
                add("数值 override 属性无效。")
            }
            if (parsedOverrideValue?.isFinite() != true) {
                add("数值 override 必须是有限数字。")
            } else {
                val minimum = parsedOverrideProperty?.minimum
                if (minimum != null && parsedOverrideValue != null && parsedOverrideValue < minimum) {
                    add("${parsedOverrideProperty.name} 不能小于 $minimum。")
                }
            }
        }
        if (karaokeRevealEnabled && karaokeRevealSpec == null) {
            add("Karaoke FX 参数无效：显现时长需 ≥ 0；Blur 0..20；Accel 为空或 > 0。")
        }
    }.firstOrNull()

    fun filter(): AssBatchFilter {
        val filters = buildList<AssBatchFilter> {
            add(AssBatchFilter.EventIds(scopeIds))
            styleFilter.trim().takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.StyleIs(it)) }
            textFilter.takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.TextContains(it)) }
            actorFilter.takeIf(String::isNotEmpty)?.let { add(AssBatchFilter.ActorContains(it)) }
            parsedLayerFilter?.let { add(AssBatchFilter.LayerIs(it)) }
            parsedTagFilter?.takeIf { tagFilterValid }?.let { add(AssBatchFilter.HasTag(it)) }
            when (commentFilter) {
                "DIALOGUE" -> add(AssBatchFilter.CommentIs(false))
                "COMMENT" -> add(AssBatchFilter.CommentIs(true))
            }
            parsedRawRegexFilter?.let { add(AssBatchFilter.RawRegex(it)) }
            if (parsedDurationMin != null || parsedDurationMax != null) {
                add(AssBatchFilter.DurationRange(parsedDurationMin ?: 0L, parsedDurationMax ?: Long.MAX_VALUE))
            }
            // Keep the compiler-backed compatibility filter last so cheap scope/text/style
            // predicates can short-circuit before full Karaoke FX inspection.
            if (karaokeRevealEnabled && karaokeRevealSpec != null) {
                add(AssBatchFilter.KaraokeRevealCompatible(karaokeRevealSpec))
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
            parsedShift?.takeIf { it != 0L }?.let { add(AssBatchAction.ShiftTime(it)) }
            parsedStyleAction?.let { add(AssBatchAction.SetStyle(it)) }
            parsedLayerAction?.let { add(AssBatchAction.SetLayer(it)) }
            actorText.takeIf(String::isNotEmpty)?.let { add(AssBatchAction.SetActor(it)) }
            if (marginL.isNotBlank() || marginR.isNotBlank() || marginV.isNotBlank()) {
                add(AssBatchAction.SetMargins(parsedMarginL, parsedMarginR, parsedMarginV))
            }
            findText.takeIf(String::isNotEmpty)?.let { add(AssBatchAction.ReplacePlainText(it, replaceText)) }
            when (commentAction) {
                "DIALOGUE" -> add(AssBatchAction.SetComment(false))
                "COMMENT" -> add(AssBatchAction.SetComment(true))
            }
            parsedReplaceRegex?.let { pattern ->
                add(
                    if (regexRaw) AssBatchAction.ReplaceRawRegex(pattern, regexReplace)
                    else AssBatchAction.ReplaceVisibleRegex(pattern, regexReplace)
                )
            }
            if (
                timingScaleRequested &&
                parsedTimingOrigin != null &&
                parsedTimingNumerator != null &&
                parsedTimingDenominator != null &&
                parsedTimingNumerator > 0L &&
                parsedTimingDenominator > 0L
            ) {
                add(
                    AssBatchAction.ScaleTiming(
                        parsedTimingOrigin,
                        parsedTimingNumerator,
                        parsedTimingDenominator,
                    )
                )
            }
            val parsedOverrideMinimum = parsedOverrideProperty?.minimum
            if (
                parsedOverrideProperty != null &&
                parsedOverrideValue?.isFinite() == true &&
                (parsedOverrideMinimum == null || parsedOverrideValue >= parsedOverrideMinimum)
            ) {
                add(AssBatchAction.SetNumericOverride(parsedOverrideProperty, parsedOverrideValue))
            }
            if (karaokeRevealEnabled) {
                karaokeRevealSpec?.let { add(AssBatchAction.ApplyKaraokeRevealFx(it)) }
            }
        }
        return AssBatchRecipe("interactive-rule", filter(), actions)
    }

    val recipeResult = remember(
        uiState.selection.eventIds,
        scopeIds, styleFilter, textFilter, actorFilter, layerFilter, tagFilter, commentFilter,
        rawRegexFilter, durationMin, durationMax,
        shiftText, styleText, layerText, actorText, marginL, marginR, marginV, findText, replaceText, commentAction,
        regexFind, regexReplace, regexRaw, timingOrigin, timingNumerator, timingDenominator, overridePropertyName, overrideValue,
        karaokeRevealEnabled, karaokeRevealMs, karaokeRevealBlur, karaokeRevealAccel,
        batchInputError,
    ) {
        if (batchInputError == null) runCatching { recipe() } else null
    }
    val recipe = recipeResult?.getOrNull()
    val recipeError = recipeResult?.exceptionOrNull()?.message
    val draftPreview = draftEntry.preview("rule", state.document, scopeIds)
    val explicitReview = draftPreview?.review
    val explicitPreviewError = draftPreview?.error
    val preview = explicitReview?.preview
    val previewError = batchInputError ?: recipeError ?: explicitPreviewError
    val previewPending = previewError == null && recipe != null && preview == null

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
            AssBatchScope.entries.forEach { candidate ->
                val label = when (candidate) {
                    AssBatchScope.SELECTED -> "已选字幕"
                    AssBatchScope.RESULTS -> "筛选结果"
                    AssBatchScope.SELECTED_RESULTS -> "已选∩结果"
                    AssBatchScope.DOCUMENT -> "整个文档"
                }
                FilterChip(scope == candidate, { scopeName = candidate.name }, { Text(label) },
                    modifier = Modifier.testTag("batch-scope-${candidate.name}"))
            }
        }
        Text("作用范围 ${scopeIds.size} 条 · 下方条件继续取交集；空范围不会扩大到整个文档。",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("batch-scope-count"))
        Text("规则处理已提交的 ASS；不会自动应用其他工具中的未提交草稿。", style = MaterialTheme.typography.labelSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        OutlinedTextField(
            rawRegexFilter,
            { rawRegexFilter = it },
            label = { Text("Raw ASS Regex filter") },
            modifier = Modifier.fillMaxWidth().testTag("batch-raw-regex-filter"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(durationMin, { durationMin = it }, label = { Text("最短时长 ms") }, modifier = Modifier.weight(1f))
            OutlinedTextField(durationMax, { durationMax = it }, label = { Text("最长时长 ms") }, modifier = Modifier.weight(1f))
        }

        HorizontalDivider()
        Text("Transform", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(shiftText, { shiftText = it }, label = { Text("时间平移 ms") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                styleText,
                { styleText = it },
                label = { Text("设 Style") },
                modifier = Modifier.weight(1f).testTag("batch-style-action"),
            )
            OutlinedTextField(layerText, { layerText = it }, label = { Text("设 Layer") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(actorText, { actorText = it }, label = { Text("设 Actor/Name") }, modifier = Modifier.fillMaxWidth().testTag("batch-actor-action"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                marginL,
                { marginL = it },
                label = { Text("MarginL") },
                modifier = Modifier.weight(1f).testTag("batch-margin-l"),
            )
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
        OutlinedButton(
            onClick = {
                val currentRecipe = recipe ?: return@OutlinedButton
                val result = runCatching { AssBatchReview.create(state.document, currentRecipe, state.workspaceSessionId) }
                draftEntry.record("rule", state.document, scopeIds, result)
            },
            enabled = batchInputError == null && recipeError == null && recipe != null,
            modifier = Modifier.fillMaxWidth().testTag("batch-preview-explicit"),
        ) {
            Text("预检并生成批处理预览")
        }
        when {
            previewError != null -> {
                Text(
                    "预览失败：" + previewError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("batch-preview-error"),
                )
            }
            previewPending -> {
                Text(
                    "当前配方尚未执行完整预检；修改参数时不会扫描整个文档。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("batch-preview-pending"),
                )
            }
            preview != null -> {
                explicitReview?.let { review ->
                    BatchReviewResults(review, "batch-preview", draftEntry.page("rule")) { viewModel.focusEvent(it, seek = false) }
                }
            }
        }
        Button(
            onClick = { explicitReview?.let(viewModel::applyBatchReview) },
            enabled = recipe != null && preview?.changedEventIds?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth().testTag("batch-review-commit"),
        ) { Text("应用已预览的 ${preview?.changedEventIds?.size ?: 0} 条 · 一个事务") }

        SemanticSearchReplacePane(state, viewModel, scopeIds, draftEntry)
    }
}
