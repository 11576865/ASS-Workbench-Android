package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.SubtitleEventKind
import io.github.assworkbench.domain.SubtitleFilterState

@Composable
internal fun SubtitleNavigationHost(filter: SubtitleFilterState, searchOpen: Boolean,
    onChange: (SubtitleFilterState) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        val layout = SubtitleFilterLayout.resolve(maxHeight.value)
        var dialogOpen by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            if (searchOpen && layout.useDialog) TextButton(
                onClick = { dialogOpen = true },
                modifier = Modifier.fillMaxWidth().heightIn(max = layout.inlineHeightDp.dp).testTag("subtitle-filter-open-dialog"),
            ) { Text("编辑筛选条件") }
            if (searchOpen && !layout.useDialog) SubtitleFilterPane(filter, onChange, onClose,
                Modifier.heightIn(max = layout.inlineHeightDp.dp))
            content()
        }
        // Opening a global search never creates dialogs from masked board compositions.
        if (searchOpen && layout.useDialog && dialogOpen) AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text("字幕筛选") },
            text = { SubtitleFilterPane(filter, onChange, { dialogOpen = false }) },
            confirmButton = { TextButton({ dialogOpen = false }, Modifier.testTag("subtitle-filter-dialog-done")) { Text("返回结果") } },
        )
    }
}

@Composable
internal fun SubtitleFilterPane(filter: SubtitleFilterState, onChange: (SubtitleFilterState) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = modifier, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(filter.quickText, { onChange(filter.copy(quickText = it)) },
                    placeholder = { Text("正文 / Actor / Style") }, singleLine = true,
                    modifier = Modifier.weight(1f).testTag("subtitle-filter-quick"))
                IconButton(onClose) { Icon(Icons.Filled.Close, "关闭筛选面板（保留条件）") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ expanded = !expanded }, Modifier.testTag("subtitle-filter-expand")) { Text(if (expanded) "收起条件" else "更多条件") }
                TextButton({ onChange(SubtitleFilterState()) }, enabled = filter.isActive,
                    modifier = Modifier.testTag("subtitle-filter-clear")) { Text("清空条件") }
                Text("字面匹配 · 忽略大小写", style = MaterialTheme.typography.labelSmall)
            }
            if (expanded) {
                OutlinedTextField(filter.text, { onChange(filter.copy(text = it)) }, label = { Text("可见正文包含") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("subtitle-filter-text"))
                OutlinedTextField(filter.actor, { onChange(filter.copy(actor = it)) }, label = { Text("Actor 包含") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("subtitle-filter-actor"))
                OutlinedTextField(filter.style, { onChange(filter.copy(style = it)) }, label = { Text("Style 包含") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("subtitle-filter-style"))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleEventKind.entries.forEach { kind ->
                        FilterChip(filter.kind == kind, { onChange(filter.copy(kind = kind)) },
                            label = { Text(when (kind) { SubtitleEventKind.ALL -> "全部类型"; SubtitleEventKind.DIALOGUE -> "Dialogue"; SubtitleEventKind.COMMENT -> "Comment" }) },
                            modifier = Modifier.testTag("subtitle-filter-kind-${kind.name}"))
                    }
                    FilterChip(filter.selectedOnly, { onChange(filter.copy(selectedOnly = !filter.selectedOnly)) },
                        label = { Text("仅已选") }, modifier = Modifier.testTag("subtitle-filter-selected"))
                }
            }
        }
    }
}

@Composable
internal fun SubtitleResultsBar(total: Int, matched: Int, selected: Int, excludedFocus: Boolean,
    hasFocus: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, onSelectAll: () -> Unit,
    onLocate: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("subtitle-results")) {
        Text("匹配 $matched / 全部 $total · 已选 $selected", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp))
        if (excludedFocus) Text("当前字幕不在结果中", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp).testTag("subtitle-focus-excluded"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onPrevious, enabled = matched > 0, modifier = Modifier.testTag("subtitle-result-previous")) { Text("上一命中") }
            TextButton(onNext, enabled = matched > 0, modifier = Modifier.testTag("subtitle-result-next")) { Text("下一命中") }
            TextButton(onSelectAll, enabled = matched > 0, modifier = Modifier.testTag("subtitle-result-select-all")) { Text("替换选择为 $matched 条结果") }
            TextButton(onLocate, enabled = hasFocus, modifier = Modifier.testTag("subtitle-result-locate")) { Text(if (excludedFocus) "清空筛选并定位当前字幕" else "定位当前字幕") }
            if (matched == 0) TextButton(onClear, modifier = Modifier.testTag("subtitle-result-empty-clear")) { Text("无结果 · 清空条件") }
        }
    }
}
