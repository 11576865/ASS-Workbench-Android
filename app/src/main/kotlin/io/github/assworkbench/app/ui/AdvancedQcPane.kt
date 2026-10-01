package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.*
import io.github.assworkbench.fonts.FontMatchStatus

@Composable
internal fun AdvancedQcPane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var profileName by rememberSaveable { mutableStateOf(AssCompatibilityProfile.LIBASS_NATIVE.name) }
    var maxCpsText by rememberSaveable { mutableStateOf("25") }
    val profile = AssCompatibilityProfile.valueOf(profileName)
    val maxCps = maxCpsText.toDoubleOrNull()?.coerceIn(1.0, 100.0) ?: 25.0
    val issues = remember(state.document, state.fontDiagnostics, state.fontGlyphDiagnostics, profile, maxCps) {
        buildList {
            addAll(
                AssLinter.inspect(
                    state.document,
                    AssLintConfig(
                        maxCps = maxCps,
                        compatibilityProfile = profile.takeUnless { it == AssCompatibilityProfile.LIBASS_NATIVE },
                    ),
                )
            )
            state.fontDiagnostics.forEach { diagnostic ->
                when (diagnostic.status) {
                    FontMatchStatus.MISSING -> add(
                        AssLintIssue(
                            eventId = null,
                            code = "FONT.MISSING",
                            category = AssLintCategory.FONT,
                            severity = AssQcSeverity.ERROR,
                            message = "缺少字体：" + diagnostic.requestedFamily,
                        )
                    )
                    FontMatchStatus.FALLBACK_ONLY -> add(
                        AssLintIssue(
                            eventId = null,
                            code = "FONT.FALLBACK",
                            category = AssLintCategory.FONT,
                            severity = AssQcSeverity.WARNING,
                            message = diagnostic.requestedFamily + " 只能通过 fallback 渲染。",
                        )
                    )
                    else -> Unit
                }
            }
            state.fontGlyphDiagnostics.values.forEach { glyph ->
                if (glyph.missingCodePoints.isNotEmpty()) add(
                    AssLintIssue(
                        eventId = null,
                        code = "FONT.MISSING_GLYPH",
                        category = AssLintCategory.FONT,
                        severity = AssQcSeverity.ERROR,
                        message = glyph.requestedFamily + " 缺少 " + glyph.missingCodePoints.size + " 个已检查字形。",
                    )
                )
            }
        }
    }
    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ASS Linter", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssCompatibilityProfile.entries.forEach { entry ->
                FilterChip(
                    selected = entry == profile,
                    onClick = { profileName = entry.name },
                    label = { Text(entry.name.replace('_', ' ')) },
                )
            }
        }
        OutlinedTextField(
            value = maxCpsText,
            onValueChange = { maxCpsText = it },
            label = { Text("阅读速度警告阈值 CPS") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val errors = issues.count { it.severity == AssQcSeverity.ERROR }
        val warnings = issues.count { it.severity == AssQcSeverity.WARNING }
        Text("ERROR ${errors} · WARNING ${warnings} · INFO ${issues.size - errors - warnings}")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(issues, key = { issue -> issue.code + ":" + (issue.eventId ?: -1L) + ":" + issue.message }) { issue ->
                Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(issue.code, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text(issue.category.name, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(issue.message, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            issue.eventId?.let { id ->
                                TextButton(onClick = { viewModel.focusEvent(id, seek = true) }) { Text("跳到 #${id}") }
                            }
                            issue.quickFix?.let { fix ->
                                Button(onClick = { viewModel.applyQuickFix(issue) }) {
                                    Icon(Icons.Filled.AutoFixHigh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(fix.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompatibilityPane(
    state: EditorState,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var profileName by rememberSaveable { mutableStateOf(AssCompatibilityProfile.PORTABLE_CONSERVATIVE.name) }
    val profile = AssCompatibilityProfile.valueOf(profileName)
    val issues = remember(state.document, profile) { AssCompatibilityAnalyzer.inspect(state.document, profile) }
    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("兼容性检查", style = MaterialTheme.typography.titleMedium)
        Text("libass 仍是预览权威；这里做规则分析，不伪装成第二套渲染器。", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssCompatibilityProfile.entries.forEach { entry ->
                FilterChip(selected = profile == entry, onClick = { profileName = entry.name }, label = { Text(entry.name) })
            }
        }
        if (issues.isEmpty()) {
            Text("当前 profile 未发现规则型兼容性风险。")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(issues, key = { issue -> issue.code + ":" + (issue.eventId ?: -1L) }) { issue ->
                    ListItem(
                        headlineContent = { Text(issue.code) },
                        supportingContent = { Text(issue.message) },
                        trailingContent = {
                            issue.eventId?.let { id ->
                                TextButton(onClick = { viewModel.focusEvent(id, seek = true) }) { Text("#${id}") }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
