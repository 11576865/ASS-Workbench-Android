package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
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
        Regex("""\\(?:fn|fs(?!c)|b-?\d|i-?\d|u-?\d|s-?\d|fsp|bord|shad|an[1-9]|a\d+|pos\(|move\(|r|c&H|1c&H|3c&H|4c&H)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    } == true
    val hasEventMarginOverrides = focusedEvent?.let {
        it.marginL > 0 || it.marginR > 0 || it.marginV > 0
    } == true
    val focusedSources = focusedEvent?.let(::styleOverrideSources).orEmpty()
    val selectedOverrideCount = state.document.events.count { event ->
        event.id in state.selectedEventIds &&
            (styleOverrideSources(event).isNotEmpty() || event.marginL > 0 || event.marginR > 0 || event.marginV > 0)
    }

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

    Card(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(10.dp),
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
                val styleUseCount = state.document.events.count { it.style == style.name }
                Text(
                    "作用域：这个 Style 被 " + styleUseCount + " 条字幕共用；修改 Style 会同时影响它们。",
                )
                if (state.selectedEventIds.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.makeSelectedStyleIndependent(style.name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("仅让已选字幕使用独立 Style（" + state.selectedEventIds.size + " 条已选）")
                    }
                    if (selectedOverrideCount > 0) {
                        OutlinedButton(
                            onClick = viewModel::clearSelectedStyleOverrides,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("让已选字幕全部继承 Style（$selectedOverrideCount 条存在覆盖）")
                        }
                    }
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
                Text("颜色")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssColorControl(
                        label = "文字",
                        value = primaryColor,
                        onValue = { primaryColor = it },
                        modifier = Modifier.weight(1f),
                    )
                    AssColorControl(
                        label = "描边",
                        value = outlineColor,
                        onValue = { outlineColor = it },
                        modifier = Modifier.weight(1f),
                    )
                    AssColorControl(
                        label = "阴影/背景",
                        value = backColor,
                        onValue = { backColor = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Text("有效值来源", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                if (focusedEvent != null) {
                    Text(
                        if (focusedSources.isEmpty() && !hasEventMarginOverrides)
                            "当前字幕没有检测到样式覆盖：以下参数由 Style 决定。"
                        else
                            buildString {
                                if (focusedSources.isNotEmpty()) append("内联覆盖：").append(focusedSources.joinToString("、"))
                                if (hasEventMarginOverrides) {
                                    if (isNotEmpty()) append(" · ")
                                    append("事件 Margin=")
                                        .append(focusedEvent.marginL).append("/")
                                        .append(focusedEvent.marginR).append("/")
                                        .append(focusedEvent.marginV)
                                }
                            },
                        color = if (focusedSources.isEmpty() && !hasEventMarginOverrides)
                            androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text("排版参数会自动应用到 Style；数值输入停止约 220 ms 后刷新预览。")
                if (hasInlineStyleOverrides || hasEventMarginOverrides) {
                    val reasons = buildList {
                        if (hasInlineStyleOverrides) add("内联 ASS 标签")
                        if (hasEventMarginOverrides) add("事件级 Margin")
                    }.joinToString("、")
                    Text(
                        "当前字幕存在 $reasons，会覆盖同名 Style 属性。你的文件中像 \\fs56、\\b0、\\i0、\\bord6、\\an2 这类标签就是这种情况。",
                    )
                    OutlinedButton(
                        onClick = viewModel::clearFocusedStyleOverrides,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("让当前字幕完全继承 Style") }
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
private fun AssColorControl(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val rgba = remember(value) { parseAssColor(value) }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(18.dp).background(
                    Color(
                        red = rgba.red / 255f,
                        green = rgba.green / 255f,
                        blue = rgba.blue / 255f,
                        alpha = (255 - rgba.assAlpha) / 255f,
                    )
                )
            )
            Text(label)
        }
    }
    if (open) {
        AssColorDialog(
            label = label,
            initial = rgba,
            onDismiss = { open = false },
            onConfirm = { picked ->
                onValue(formatAssColor(picked))
                open = false
            },
        )
    }
}

private data class AssRgba(
    val red: Int,
    val green: Int,
    val blue: Int,
    val assAlpha: Int,
)

@Composable
private fun AssColorDialog(
    label: String,
    initial: AssRgba,
    onDismiss: () -> Unit,
    onConfirm: (AssRgba) -> Unit,
) {
    var red by remember(initial) { mutableIntStateOf(initial.red) }
    var green by remember(initial) { mutableIntStateOf(initial.green) }
    var blue by remember(initial) { mutableIntStateOf(initial.blue) }
    var opacity by remember(initial) { mutableIntStateOf(255 - initial.assAlpha) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择" + label + "颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 42.dp).background(
                        Color(red / 255f, green / 255f, blue / 255f, opacity / 255f)
                    )
                )
                ColorSlider("R", red) { red = it }
                ColorSlider("G", green) { green = it }
                ColorSlider("B", blue) { blue = it }
                ColorSlider("不透明度", opacity) { opacity = it }
                Text(
                    "ASS：" + formatAssColor(AssRgba(red, green, blue, 255 - opacity)),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(AssRgba(red, green, blue, 255 - opacity)) },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Int,
    onValue: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text(value.toString())
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
        )
    }
}

private fun parseAssColor(value: String): AssRgba {
    val hex = value.trim()
        .removePrefix("&H")
        .removeSuffix("&")
        .padStart(8, '0')
        .takeLast(8)
    val a = hex.substring(0, 2).toIntOrNull(16) ?: 0
    val b = hex.substring(2, 4).toIntOrNull(16) ?: 0
    val g = hex.substring(4, 6).toIntOrNull(16) ?: 0
    val r = hex.substring(6, 8).toIntOrNull(16) ?: 255
    return AssRgba(r, g, b, a)
}

private fun formatAssColor(value: AssRgba): String =
    "&H%02X%02X%02X%02X".format(
        value.assAlpha.coerceIn(0, 255),
        value.blue.coerceIn(0, 255),
        value.green.coerceIn(0, 255),
        value.red.coerceIn(0, 255),
    )

private fun styleOverrideSources(event: io.github.assworkbench.domain.AssEvent): List<String> {
    val text = event.text
    val checks = listOf(
        "字体" to Regex("""\\fn""", RegexOption.IGNORE_CASE),
        "字号" to Regex("""\\fs(?!c)""", RegexOption.IGNORE_CASE),
        "粗体" to Regex("""\\b-?\d""", RegexOption.IGNORE_CASE),
        "斜体" to Regex("""\\i-?\d""", RegexOption.IGNORE_CASE),
        "下划线" to Regex("""\\u-?\d""", RegexOption.IGNORE_CASE),
        "删除线" to Regex("""\\s-?\d""", RegexOption.IGNORE_CASE),
        "字距" to Regex("""\\fsp""", RegexOption.IGNORE_CASE),
        "描边" to Regex("""\\bord""", RegexOption.IGNORE_CASE),
        "阴影" to Regex("""\\shad""", RegexOption.IGNORE_CASE),
        "对齐" to Regex("""\\(?:an[1-9]|a\d+)""", RegexOption.IGNORE_CASE),
        "位置" to Regex("""\\(?:pos|move|org)\(""", RegexOption.IGNORE_CASE),
        "颜色" to Regex("""\\(?:c|1c|3c|4c)&H""", RegexOption.IGNORE_CASE),
        "Style 重置" to Regex("""\\r(?:[^\\}]*)""", RegexOption.IGNORE_CASE),
    )
    return checks.mapNotNull { (label, regex) -> label.takeIf { regex.containsMatchIn(text) } }
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
