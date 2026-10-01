package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*

@Composable
internal fun SemanticSearchReplacePane(state: EditorState, viewModel: EditorViewModel, modifier: Modifier = Modifier) {
    var patternText by rememberSaveable { mutableStateOf("") }
    var replacementText by rememberSaveable { mutableStateOf("") }
    var searchScope by rememberSaveable { mutableStateOf(AssReplaceScope.VISIBLE_TEXT.name) }
    var requiredTag by rememberSaveable { mutableStateOf("") }
    var forbiddenTag by rememberSaveable { mutableStateOf("") }
    var styleRegex by rememberSaveable { mutableStateOf("") }
    var actorRegex by rememberSaveable { mutableStateOf("") }
    var ignoreCase by rememberSaveable { mutableStateOf(true) }
    var regexMode by rememberSaveable { mutableStateOf(true) }

    fun compile(raw: String): Regex? {
        if (raw.isEmpty()) return null
        val source = if (regexMode) raw else Regex.escape(raw)
        return runCatching { Regex(source, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }.getOrNull()
    }

    val mainPattern = remember(patternText, regexMode, ignoreCase) { compile(patternText) }
    val stylePattern = remember(styleRegex, ignoreCase) {
        styleRegex.takeIf(String::isNotBlank)?.let {
            runCatching { Regex(it, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }.getOrNull()
        }
    }
    val actorPattern = remember(actorRegex, ignoreCase) {
        actorRegex.takeIf(String::isNotBlank)?.let {
            runCatching { Regex(it, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }.getOrNull()
        }
    }
    val scope = AssReplaceScope.entries.firstOrNull { it.name == searchScope } ?: AssReplaceScope.VISIBLE_TEXT
    val query = remember(mainPattern, stylePattern, actorPattern, requiredTag, forbiddenTag, scope) {
        AssSearchQuery(
            visiblePattern = mainPattern.takeIf { scope == AssReplaceScope.VISIBLE_TEXT },
            rawPattern = mainPattern.takeIf { scope == AssReplaceScope.RAW_EVENT_TEXT },
            stylePattern = if (scope == AssReplaceScope.STYLE) mainPattern else stylePattern,
            actorPattern = if (scope == AssReplaceScope.ACTOR) mainPattern else actorPattern,
            requiredTags = requiredTag.trim().removePrefix("\\").takeIf(String::isNotEmpty)?.let(::setOf).orEmpty(),
            forbiddenTags = forbiddenTag.trim().removePrefix("\\").takeIf(String::isNotEmpty)?.let(::setOf).orEmpty(),
        )
    }
    val preview = remember(state.document, query, mainPattern, replacementText, scope) {
        if (mainPattern == null) null else AssSearchReplace.preview(
            state.document, query, AssSearchReplacement(scope, mainPattern, replacementText)
        )
    }

    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("结构化搜索与替换", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(regexMode, { regexMode = !regexMode }, { Text("Regex") })
            FilterChip(ignoreCase, { ignoreCase = !ignoreCase }, { Text("忽略大小写") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AssReplaceScope.entries.forEach { item ->
                FilterChip(
                    selected = scope == item,
                    onClick = { searchScope = item.name },
                    label = { Text(when (item) {
                        AssReplaceScope.VISIBLE_TEXT -> "正文"
                        AssReplaceScope.RAW_EVENT_TEXT -> "Raw ASS"
                        AssReplaceScope.STYLE -> "Style"
                        AssReplaceScope.ACTOR -> "Actor"
                    }) },
                )
            }
        }
        OutlinedTextField(patternText, { patternText = it }, label = { Text("查找表达式") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(replacementText, { replacementText = it }, label = { Text("替换为") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(requiredTag, { requiredTag = it }, label = { Text("必须含 tag") }, modifier = Modifier.weight(1f))
            OutlinedTextField(forbiddenTag, { forbiddenTag = it }, label = { Text("不得含 tag") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(styleRegex, { styleRegex = it }, label = { Text("附加 Style regex") }, modifier = Modifier.weight(1f))
            OutlinedTextField(actorRegex, { actorRegex = it }, label = { Text("附加 Actor regex") }, modifier = Modifier.weight(1f))
        }
        if (patternText.isNotEmpty() && mainPattern == null) {
            Text("正则表达式无效。", color = MaterialTheme.colorScheme.error)
        } else if (preview != null) {
            Text("命中 \${preview.hits.size} 条 · 将修改 \${preview.changedEventIds.size} 条")
            preview.hits.take(5).forEach {
                Text("#\${it.eventId} · \${it.visibleText}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        Button(
            onClick = {
                val p = mainPattern ?: return@Button
                viewModel.applySearchReplacement(query, AssSearchReplacement(scope, p, replacementText))
            },
            enabled = mainPattern != null && preview?.changedEventIds?.isNotEmpty() == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("替换全部 · 一个 Undo 事务") }
    }
}
