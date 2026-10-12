package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*

@Composable
internal fun SemanticSearchReplacePane(
    state: EditorState,
    viewModel: EditorViewModel,
    eventIds: Set<Long>,
    draftEntry: BatchAuthoringDraftEntry,
    modifier: Modifier = Modifier,
    scopeContent: @Composable () -> Unit,
) {
    var patternText by draftEntry.text("search.patternText", "")
    var replacementText by draftEntry.text("search.replacementText", "")
    var searchScope by draftEntry.text("search.searchScope", AssReplaceScope.VISIBLE_TEXT.name)
    var requiredTag by draftEntry.text("search.requiredTag", "")
    var forbiddenTag by draftEntry.text("search.forbiddenTag", "")
    var styleRegex by draftEntry.text("search.styleRegex", "")
    var actorRegex by draftEntry.text("search.actorRegex", "")
    var ignoreCase by draftEntry.flag("search.ignoreCase", true)
    var regexMode by draftEntry.flag("search.regexMode", true)

    fun compileMain(raw: String): Regex? {
        if (raw.isEmpty()) return null
        val source = if (regexMode) raw else Regex.escape(raw)
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return runCatching { Regex(source, options) }.getOrNull()
    }

    fun compileAdditional(raw: String): Regex? {
        if (raw.isBlank()) return null
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return runCatching { Regex(raw, options) }.getOrNull()
    }

    val mainPattern = remember(patternText, regexMode, ignoreCase) { compileMain(patternText) }
    val stylePattern = remember(styleRegex, ignoreCase) { compileAdditional(styleRegex) }
    val actorPattern = remember(actorRegex, ignoreCase) { compileAdditional(actorRegex) }
    val normalizedRequiredTag = requiredTag.trim().removePrefix("\\").takeIf(String::isNotEmpty)
    val normalizedForbiddenTag = forbiddenTag.trim().removePrefix("\\").takeIf(String::isNotEmpty)
    val scope = AssReplaceScope.entries.firstOrNull { it.name == searchScope } ?: AssReplaceScope.VISIBLE_TEXT

    val localInputError = when {
        patternText.isNotEmpty() && mainPattern == null -> "主查找正则表达式无效。"
        styleRegex.isNotBlank() && stylePattern == null -> "附加 Style regex 无效。"
        actorRegex.isNotBlank() && actorPattern == null -> "附加 Actor regex 无效。"
        normalizedRequiredTag != null &&
            !AssTopLevelOverrideSyntax.isValidTagName(normalizedRequiredTag) ->
            "必须含 tag 的名称无效。"
        normalizedForbiddenTag != null &&
            !AssTopLevelOverrideSyntax.isValidTagName(normalizedForbiddenTag) ->
            "不得含 tag 的名称无效。"
        else -> null
    }

    val queryResult = remember(
        mainPattern,
        stylePattern,
        actorPattern,
        normalizedRequiredTag,
        normalizedForbiddenTag,
        scope,
        localInputError,
    ) {
        if (localInputError != null) {
            null
        } else {
            runCatching {
                AssSearchQuery(
                    visiblePattern = mainPattern.takeIf { scope == AssReplaceScope.VISIBLE_TEXT },
                    rawPattern = mainPattern.takeIf { scope == AssReplaceScope.RAW_EVENT_TEXT },
                    stylePattern = if (scope == AssReplaceScope.STYLE) mainPattern else stylePattern,
                    actorPattern = if (scope == AssReplaceScope.ACTOR) mainPattern else actorPattern,
                    requiredTags = normalizedRequiredTag?.let(::setOf).orEmpty(),
                    forbiddenTags = normalizedForbiddenTag?.let(::setOf).orEmpty(),
                )
            }
        }
    }
    val query = queryResult?.getOrNull()
    val queryError = queryResult?.exceptionOrNull()?.message

    val draftPreview = draftEntry.preview("search", state.document, eventIds, state.documentRevision)
    val review = draftPreview?.review
    val executionError = draftPreview?.error
    val preview = review?.preview
    val errorMessage = localInputError ?: queryError ?: executionError

    Column(modifier) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        scopeContent()
        HorizontalDivider()
        Text("结构化搜索与替换", style = MaterialTheme.typography.titleMedium)
        Text("沿用上方作用范围：${eventIds.size} 条。正文按现有 token 边界替换，跨 override block 的匹配可能命中但不会修改；Raw ASS 直接修改标签。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(regexMode, { regexMode = !regexMode }, { Text("Regex") })
            FilterChip(ignoreCase, { ignoreCase = !ignoreCase }, { Text("忽略大小写") })
        }
        Column(Modifier.fillMaxWidth()) {
            AssReplaceScope.entries.chunked(2).forEach { choices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    choices.forEach { item ->
                        FilterChip(
                            selected = scope == item,
                            onClick = { searchScope = item.name },
                            modifier = Modifier.weight(1f),
                            label = { Text(when (item) {
                                AssReplaceScope.VISIBLE_TEXT -> "正文"
                                AssReplaceScope.RAW_EVENT_TEXT -> "Raw ASS"
                                AssReplaceScope.STYLE -> "Style"
                                AssReplaceScope.ACTOR -> "Actor"
                            }) },
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            patternText,
            { patternText = it },
            label = { Text("查找表达式") },
            modifier = Modifier.fillMaxWidth().testTag("search-pattern"),
        )
        OutlinedTextField(
            replacementText,
            { replacementText = it },
            label = { Text("替换为") },
            modifier = Modifier.fillMaxWidth().testTag("search-replacement"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                requiredTag,
                { requiredTag = it },
                label = { Text("必须含 tag") },
                modifier = Modifier.weight(1f).testTag("search-required-tag"),
            )
            OutlinedTextField(
                forbiddenTag,
                { forbiddenTag = it },
                label = { Text("不得含 tag") },
                modifier = Modifier.weight(1f).testTag("search-forbidden-tag"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                styleRegex,
                { styleRegex = it },
                label = { Text("附加 Style regex") },
                modifier = Modifier.weight(1f).testTag("search-style-regex"),
            )
            OutlinedTextField(
                actorRegex,
                { actorRegex = it },
                label = { Text("附加 Actor regex") },
                modifier = Modifier.weight(1f).testTag("search-actor-regex"),
            )
        }
        if (errorMessage != null) {
            Text("搜索/替换规则无效：$errorMessage", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("search-replace-error"))
        } else {
            review?.let { BatchReviewResults(it, "search-preview", draftEntry.page("search")) { id -> viewModel.focusEvent(id, seek = false) } }
        }
    }
    BatchActionBar(eventIds.size, preview?.changedEventIds?.size, errorMessage,
        previewEnabled = localInputError == null && queryError == null && mainPattern != null && query != null,
        applyEnabled = errorMessage == null && preview?.changedEventIds?.isNotEmpty() == true,
        previewTag = "search-review-preview", applyTag = "search-review-commit",
        onPreview = {
            val p = mainPattern
            val q = query
            if (p != null && q != null) draftEntry.record("search", state.document, eventIds,
                runCatching { AssBatchReview.search(state.document, q,
                    AssSearchReplacement(scope, p, replacementText), eventIds, state.workspaceSessionId, state.documentRevision) }, state.documentRevision)
        },
        onApply = { review?.let(viewModel::applyBatchReview) })
    }
}
