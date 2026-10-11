package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    modifier: Modifier = Modifier,
) {
    var patternText by rememberSaveable { mutableStateOf("") }
    var replacementText by rememberSaveable { mutableStateOf("") }
    var searchScope by rememberSaveable { mutableStateOf(AssReplaceScope.VISIBLE_TEXT.name) }
    var requiredTag by rememberSaveable { mutableStateOf("") }
    var forbiddenTag by rememberSaveable { mutableStateOf("") }
    var styleRegex by rememberSaveable { mutableStateOf("") }
    var actorRegex by rememberSaveable { mutableStateOf("") }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var regexMode by rememberSaveable { mutableStateOf(true) }

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

    var review by remember(state.workspaceSessionId, state.document, query, mainPattern, replacementText, scope, eventIds) {
        mutableStateOf<AssBatchReview?>(null)
    }
    var executionError by remember(state.workspaceSessionId, state.document, query, mainPattern, replacementText, scope, eventIds) {
        mutableStateOf<String?>(null)
    }
    val preview = review?.preview
    val errorMessage = localInputError ?: queryError ?: executionError

    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("结构化搜索与替换", style = MaterialTheme.typography.titleMedium)
        Text("沿用上方作用范围：${eventIds.size} 条。正文按现有 token 边界替换，跨 override block 的匹配可能命中但不会修改；Raw ASS 直接修改标签。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(regexMode, { regexMode = !regexMode }, { Text("Regex") })
            FilterChip(ignoreCase, { ignoreCase = !ignoreCase }, { Text("忽略大小写") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AssReplaceScope.entries.forEach { item ->
                FilterChip(
                    selected = scope == item,
                    onClick = { searchScope = item.name },
                    label = {
                        Text(
                            when (item) {
                                AssReplaceScope.VISIBLE_TEXT -> "正文"
                                AssReplaceScope.RAW_EVENT_TEXT -> "Raw ASS"
                                AssReplaceScope.STYLE -> "Style"
                                AssReplaceScope.ACTOR -> "Actor"
                            }
                        )
                    },
                )
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
        OutlinedButton(
            onClick = {
                val p = mainPattern ?: return@OutlinedButton
                val q = query ?: return@OutlinedButton
                val result = runCatching { AssBatchReview.search(state.document, q,
                    AssSearchReplacement(scope, p, replacementText), eventIds, state.workspaceSessionId) }
                review = result.getOrNull()
                executionError = result.exceptionOrNull()?.message
            },
            enabled = localInputError == null && queryError == null && mainPattern != null && query != null,
            modifier = Modifier.fillMaxWidth().testTag("search-review-preview"),
        ) { Text("预检替换并查看变更") }
        if (errorMessage != null) {
            Text("搜索/替换规则无效：$errorMessage", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("search-replace-error"))
        } else {
            review?.let { BatchReviewResults(it, "search-preview") { id -> viewModel.focusEvent(id, seek = false) } }
        }
        Button(
            onClick = { review?.let(viewModel::applyBatchReview) },
            enabled = errorMessage == null && preview?.changedEventIds?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth().testTag("search-review-commit"),
        ) { Text("应用已预览的替换 · 一个 Undo 事务") }
    }
}
