package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssStyle
import io.github.assworkbench.domain.TypesettingMath
import kotlinx.coroutines.delay

@Composable
fun TypesettingPanel(
    state: EditorState,
    viewModel: EditorViewModel,
    style: AssStyle,
    modifier: Modifier = Modifier,
) {
    var fontSize by remember(style) { mutableStateOf(style.fontSize.toString()) }
    var spacing by remember(style) { mutableStateOf(style.spacing.toString()) }
    var outline by remember(style) { mutableStateOf(style.outline.toString()) }
    var shadow by remember(style) { mutableStateOf(style.shadow.toString()) }
    var marginL by remember(style) { mutableStateOf(style.marginL.toString()) }
    var marginR by remember(style) { mutableStateOf(style.marginR.toString()) }
    var marginV by remember(style) { mutableStateOf(style.marginV.toString()) }
    var primaryColor by remember(style) { mutableStateOf(style.primaryColor) }
    var outlineColor by remember(style) { mutableStateOf(style.outlineColor) }
    var backColor by remember(style) { mutableStateOf(style.backColor) }
    var bold by remember(style) { mutableStateOf(style.bold) }
    var italic by remember(style) { mutableStateOf(style.italic) }
    var underline by remember(style) { mutableStateOf(style.underline) }
    var strikeOut by remember(style) { mutableStateOf(style.strikeOut) }
    var alignment by remember(style) { mutableIntStateOf(style.alignment) }

    val styleNames = state.document.styles.map { it.name }
    var sourceStyle by remember(styleNames) { mutableStateOf(styleNames.firstOrNull() ?: "") }
    var targetStyle by remember(styleNames) { mutableStateOf(styleNames.drop(1).firstOrNull() ?: "") }
    var sourceMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }

    val geometry = TypesettingMath.bilingual6040(state.document.playResX, state.document.playResY)

    val focusedEvent = state.focusedEventId?.let { id -> state.document.events.firstOrNull { it.id == id } }
    val hasInlineStyleOverrides = focusedEvent?.text?.let { text ->
        Regex("""\\(?:fn|fs(?!c)|b-?\d|i-?\d|u-?\d|s-?\d|fsp|bord|shad|an[1-9]|c&H|1c&H|3c&H|4c&H)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    } == true

    LaunchedEffect(
        style.name,
        fontSize,
        spacing,
        outline,
        shadow,
        marginL,
        marginR,
        marginV,
        primaryColor,
        outlineColor,
        backColor,
        bold,
        italic,
        underline,
        strikeOut,
        alignment,
    ) {
        delay(220)
        viewModel.updateStyleTypography(
            styleName = style.name,
            fontSize = fontSize.toDoubleOrNull() ?: return@LaunchedEffect,
            bold = bold,
            italic = italic,
            underline = underline,
            strikeOut = strikeOut,
            spacing = spacing.toDoubleOrNull() ?: return@LaunchedEffect,
            outline = outline.toDoubleOrNull() ?: return@LaunchedEffect,
            shadow = shadow.toDoubleOrNull() ?: return@LaunchedEffect,
            alignment = alignment,
            marginL = marginL.toIntOrNull() ?: return@LaunchedEffect,
            marginR = marginR.toIntOrNull() ?: return@LaunchedEffect,
            marginV = marginV.toIntOrNull() ?: return@LaunchedEffect,
            primaryColor = primaryColor,
            outlineColor = outlineColor,
            backColor = backColor,
        )
    }

    Card(modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 430.dp).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("排版 · Style " + style.name)
                        Text("Font: " + style.fontName)
                    }
                    Text("安全区导引")
                    Switch(checked = state.showLayoutGuides, onCheckedChange = { viewModel.toggleLayoutGuides() })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallField("字号", fontSize, { fontSize = it }, Modifier.weight(1f))
                    SmallField("字距", spacing, { spacing = it }, Modifier.weight(1f))
                    SmallField("描边", outline, { outline = it }, Modifier.weight(1f))
                    SmallField("阴影", shadow, { shadow = it }, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Flag("B", bold) { bold = it }
                    Flag("I", italic) { italic = it }
                    Flag("U", underline) { underline = it }
                    Flag("S", strikeOut) { strikeOut = it }
                }
            }
            item {
                Text("九宫格对齐")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3)).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { value ->
                                if (alignment == value) {
                                    Button(onClick = { alignment = value }, modifier = Modifier.width(54.dp)) { Text(value.toString()) }
                                } else {
                                    OutlinedButton(onClick = { alignment = value }, modifier = Modifier.width(54.dp)) { Text(value.toString()) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallField("Margin L", marginL, { marginL = it }, Modifier.weight(1f))
                    SmallField("Margin R", marginR, { marginR = it }, Modifier.weight(1f))
                    SmallField("Margin V", marginV, { marginV = it }, Modifier.weight(1f))
                }
            }
            item {
                SmallField("PrimaryColour", primaryColor, { primaryColor = it }, Modifier.fillMaxWidth())
                SmallField("OutlineColour", outlineColor, { outlineColor = it }, Modifier.fillMaxWidth())
                SmallField("BackColour", backColor, { backColor = it }, Modifier.fillMaxWidth())
                Text("ASS 颜色格式：&HAABBGGRR")
            }
            item {
                Text("排版参数会自动应用到 Style；数值输入停止约 220 ms 后刷新预览。")
                if (hasInlineStyleOverrides) {
                    Text("当前字幕含有内联 ASS 排版覆盖（例如 \\fs / \\bord / \\an / \\fn），它会优先于 Style，因此部分改动可能看不出来。")
                    OutlinedButton(
                        onClick = viewModel::clearFocusedStyleOverrides,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("让当前字幕改由 Style 控制") }
                }
            }
            item {
                Text("双语 60/40 预设")
                Text(
                    "3% 横向安全边距 · 5% 纵向安全边距 · 中央间隔 " +
                        geometry.centralGap + " · 分界 Y " +
                        geometry.sourceBoundaryY + "/" + geometry.targetBoundaryY
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StylePicker(
                        label = "上方/源",
                        value = sourceStyle,
                        names = styleNames,
                        expanded = sourceMenu,
                        onExpanded = { sourceMenu = it },
                        onPick = { sourceStyle = it; sourceMenu = false },
                        modifier = Modifier.weight(1f),
                    )
                    StylePicker(
                        label = "下方/目标",
                        value = targetStyle,
                        names = styleNames,
                        expanded = targetMenu,
                        onExpanded = { targetMenu = it },
                        onPick = { targetStyle = it; targetMenu = false },
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = { viewModel.applyBilingual6040Preset(sourceStyle, targetStyle) },
                    enabled = sourceStyle.isNotBlank() && targetStyle.isNotBlank() && sourceStyle != targetStyle,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("应用 60/40 几何预设") }
            }
        }
    }
}

@Composable
private fun SmallField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun Flag(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label)
    }
}

@Composable
private fun StylePicker(
    label: String,
    value: String,
    names: List<String>,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label)
        TextButton(onClick = { onExpanded(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(value.ifBlank { "选择 Style" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            names.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onPick(name) })
            }
        }
    }
}
