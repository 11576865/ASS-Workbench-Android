package io.github.assworkbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
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
import io.github.assworkbench.domain.AssInlineSyntax
import io.github.assworkbench.domain.AssStyle
import kotlinx.coroutines.delay

private enum class StyleManageMode { CREATE, RENAME, DELETE }

@Composable
fun TypesettingPanel(
    state: EditorState,
    viewModel: EditorViewModel,
    style: AssStyle,
    modifier: Modifier = Modifier,
    contextEventId: Long? = state.focusedEventId,
) {
    var fontSize by remember(style) { mutableStateOf(style.fontSize.toString()) }
    var spacing by remember(style) { mutableStateOf(style.spacing.toString()) }
    var outline by remember(style) { mutableStateOf(style.outline.toString()) }
    var shadow by remember(style) { mutableStateOf(style.shadow.toString()) }
    var marginL by remember(style) { mutableStateOf(style.marginL.toString()) }
    var marginR by remember(style) { mutableStateOf(style.marginR.toString()) }
    var marginV by remember(style) { mutableStateOf(style.marginV.toString()) }
    var primaryColor by remember(style) { mutableStateOf(style.primaryColor) }
    var secondaryColor by remember(style) { mutableStateOf(style.secondaryColor) }
    var outlineColor by remember(style) { mutableStateOf(style.outlineColor) }
    var backColor by remember(style) { mutableStateOf(style.backColor) }
    var scaleX by remember(style) { mutableStateOf(style.scaleX.toString()) }
    var scaleY by remember(style) { mutableStateOf(style.scaleY.toString()) }
    var angle by remember(style) { mutableStateOf(style.angle.toString()) }
    var borderStyle by remember(style) { mutableStateOf(style.borderStyle.toString()) }
    var encoding by remember(style) { mutableStateOf(style.encoding.toString()) }
    var styleMenuOpen by remember { mutableStateOf(false) }
    var styleManageMode by remember { mutableStateOf<StyleManageMode?>(null) }
    var styleNameDraft by remember(style.name) { mutableStateOf(style.name) }
    var bold by remember(style) { mutableStateOf(style.bold) }
    var italic by remember(style) { mutableStateOf(style.italic) }
    var underline by remember(style) { mutableStateOf(style.underline) }
    var strikeOut by remember(style) { mutableStateOf(style.strikeOut) }
    var alignment by remember(style) { mutableIntStateOf(style.alignment) }

    val styleNames = state.document.styles.map { it.name }


    val focusedEvent = contextEventId?.let { id -> state.document.events.firstOrNull { it.id == id } }
    val focusedSources = focusedEvent?.let(::styleOverrideSources).orEmpty()
    val hasInlineStyleOverrides = focusedSources.isNotEmpty()
    val hasEventMarginOverrides = focusedEvent?.let {
        it.marginL > 0 || it.marginR > 0 || it.marginV > 0
    } == true
    val selectedOverrideCount = state.document.events.count { event ->
        event.id in state.selectedEventIds &&
            (styleOverrideSources(event).isNotEmpty() || event.marginL > 0 || event.marginR > 0 || event.marginV > 0)
    }

    var continuousGestureActive by remember(style.name) { mutableStateOf(false) }

    fun previewTypography(
        fontSizeValue: Double? = null,
        spacingValue: Double? = null,
        outlineValue: Double? = null,
        shadowValue: Double? = null,
    ) {
        viewModel.previewStyleTypography(
            styleName = style.name,
            fontSize = fontSizeValue ?: fontSize.toDoubleOrNull() ?: style.fontSize,
            bold = bold,
            italic = italic,
            underline = underline,
            strikeOut = strikeOut,
            spacing = spacingValue ?: spacing.toDoubleOrNull() ?: style.spacing,
            outline = outlineValue ?: outline.toDoubleOrNull() ?: style.outline,
            shadow = shadowValue ?: shadow.toDoubleOrNull() ?: style.shadow,
            alignment = alignment,
            marginL = marginL.toIntOrNull() ?: style.marginL,
            marginR = marginR.toIntOrNull() ?: style.marginR,
            marginV = marginV.toIntOrNull() ?: style.marginV,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            outlineColor = outlineColor,
            backColor = backColor,
            scaleX = scaleX.toDoubleOrNull() ?: style.scaleX,
            scaleY = scaleY.toDoubleOrNull() ?: style.scaleY,
            angle = angle.toDoubleOrNull() ?: style.angle,
            borderStyle = borderStyle.toIntOrNull() ?: style.borderStyle,
            encoding = encoding.toIntOrNull() ?: style.encoding,
        )
    }

    fun commitTypography() {
        viewModel.updateStyleTypography(
            styleName = style.name,
            fontSize = fontSize.toDoubleOrNull() ?: return,
            bold = bold,
            italic = italic,
            underline = underline,
            strikeOut = strikeOut,
            spacing = spacing.toDoubleOrNull() ?: return,
            outline = outline.toDoubleOrNull() ?: return,
            shadow = shadow.toDoubleOrNull() ?: return,
            alignment = alignment,
            marginL = marginL.toIntOrNull() ?: return,
            marginR = marginR.toIntOrNull() ?: return,
            marginV = marginV.toIntOrNull() ?: return,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            outlineColor = outlineColor,
            backColor = backColor,
            scaleX = scaleX.toDoubleOrNull() ?: return,
            scaleY = scaleY.toDoubleOrNull() ?: return,
            angle = angle.toDoubleOrNull() ?: return,
            borderStyle = borderStyle.toIntOrNull() ?: return,
            encoding = encoding.toIntOrNull() ?: return,
        )
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
        secondaryColor,
        outlineColor,
        backColor,
        scaleX,
        scaleY,
        angle,
        borderStyle,
        encoding,
        bold,
        italic,
        underline,
        strikeOut,
        alignment,
        continuousGestureActive,
    ) {
        if (continuousGestureActive) return@LaunchedEffect
        previewTypography()
        delay(320)
        commitTypography()
    }

    DisposableEffect(style.name) {
        onDispose { viewModel.clearTransientPreview("style:${style.name}") }
    }

    when (styleManageMode) {
        StyleManageMode.CREATE -> AlertDialog(
            onDismissRequest = { styleManageMode = null },
            title = { Text("新建 Style") },
            text = {
                OutlinedTextField(
                    value = styleNameDraft,
                    onValueChange = { styleNameDraft = it },
                    label = { Text("Style 名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createStyle(styleNameDraft)
                        styleManageMode = null
                    },
                    enabled = styleNameDraft.isNotBlank(),
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { styleManageMode = null }) { Text("取消") } },
        )
        StyleManageMode.RENAME -> AlertDialog(
            onDismissRequest = { styleManageMode = null },
            title = { Text("重命名 Style") },
            text = {
                OutlinedTextField(
                    value = styleNameDraft,
                    onValueChange = { styleNameDraft = it },
                    label = { Text("新名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameStyle(style.name, styleNameDraft)
                        styleManageMode = null
                    },
                    enabled = styleNameDraft.isNotBlank() && styleNameDraft != style.name,
                ) { Text("重命名") }
            },
            dismissButton = { TextButton(onClick = { styleManageMode = null }) { Text("取消") } },
        )
        StyleManageMode.DELETE -> {
            val replacement = styleNames.firstOrNull { it != style.name }
            AlertDialog(
                onDismissRequest = { styleManageMode = null },
                title = { Text("删除 Style " + style.name + "？") },
                text = {
                    Text(
                        if (replacement == null) "至少必须保留一个 Style。"
                        else "引用这个 Style 的字幕会改用 " + replacement + "。"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (replacement != null) viewModel.deleteStyle(style.name, replacement)
                            styleManageMode = null
                        },
                        enabled = replacement != null,
                    ) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { styleManageMode = null }) { Text("取消") } },
            )
        }
        null -> Unit
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = WorkbenchDimens.Small, vertical = WorkbenchDimens.Micro),
        verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
    ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Style · " + style.name)
                        Text(
                            style.fontName,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        TextButton(onClick = { styleMenuOpen = true }) { Text("管理") }
                        DropdownMenu(expanded = styleMenuOpen, onDismissRequest = { styleMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("新建 Style…") },
                                onClick = {
                                    styleMenuOpen = false
                                    styleNameDraft = "New Style"
                                    styleManageMode = StyleManageMode.CREATE
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("复制当前 Style") },
                                onClick = {
                                    styleMenuOpen = false
                                    var candidate = style.name + "_copy"
                                    var suffix = 2
                                    while (candidate in styleNames) candidate = style.name + "_copy" + suffix++
                                    viewModel.createStyle(candidate, style.name)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("重命名…") },
                                onClick = {
                                    styleMenuOpen = false
                                    styleNameDraft = style.name
                                    styleManageMode = StyleManageMode.RENAME
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除…") },
                                enabled = state.document.styles.size > 1,
                                onClick = {
                                    styleMenuOpen = false
                                    styleManageMode = StyleManageMode.DELETE
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("清理未使用 Style") },
                                onClick = {
                                    styleMenuOpen = false
                                    viewModel.deleteUnusedStyles()
                                },
                            )
                        }
                    }

                }
                val styleUseCount = state.document.events.count { it.style == style.name }
                val unusedStyleCount = state.document.styles.count { candidate ->
                    state.document.events.none { it.style == candidate.name }
                }
                Text(
                    "共享 " + styleUseCount + " 条 · 未使用 Style " + unusedStyleCount +
                        " · Style 值可被 Event override 覆盖",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.selectedEventIds.isNotEmpty()) {
                    OutlinedButton(
                        onClick = viewModel::makeSelectedStylesIndependent,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("让已选字幕使用独立 Style 副本（" + state.selectedEventIds.size + " 条）")
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
                Column(verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    Text(
                        "连续视觉参数",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "拖动只进入 transient preview；手势结束后只写入一次 Undo 历史。",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ContinuousParameterControl(
                        label = "字号",
                        valueText = fontSize,
                        onValueTextChange = { fontSize = it },
                        range = 6f..240f,
                        step = 1.0,
                        suffix = "px",
                        onPreview = { previewTypography(fontSizeValue = it) },
                        onGestureActive = { active ->
                            continuousGestureActive = active
                            if (!active) commitTypography()
                        },
                    )
                    ContinuousParameterControl(
                        label = "字距",
                        valueText = spacing,
                        onValueTextChange = { spacing = it },
                        range = -20f..100f,
                        step = 0.5,
                        onPreview = { previewTypography(spacingValue = it) },
                        onGestureActive = { active ->
                            continuousGestureActive = active
                            if (!active) commitTypography()
                        },
                    )
                    ContinuousParameterControl(
                        label = "描边",
                        valueText = outline,
                        onValueTextChange = { outline = it },
                        range = 0f..20f,
                        step = 0.1,
                        onPreview = { previewTypography(outlineValue = it) },
                        onGestureActive = { active ->
                            continuousGestureActive = active
                            if (!active) commitTypography()
                        },
                    )
                    ContinuousParameterControl(
                        label = "阴影",
                        valueText = shadow,
                        onValueTextChange = { shadow = it },
                        range = 0f..20f,
                        step = 0.1,
                        onPreview = { previewTypography(shadowValue = it) },
                        onGestureActive = { active ->
                            continuousGestureActive = active
                            if (!active) commitTypography()
                        },
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small), verticalAlignment = Alignment.CenterVertically) {
                    Flag("B", bold) { bold = it }
                    Flag("I", italic) { italic = it }
                    Flag("U", underline) { underline = it }
                    Flag("S", strikeOut) { strikeOut = it }
                }
            }
            item {
                Text("颜色")
                Column(verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
                    ) {
                        AssColorControl(
                            label = "文字",
                            value = primaryColor,
                            onValue = { primaryColor = it },
                            modifier = Modifier.weight(1f),
                        )
                        AssColorControl(
                            label = "次要",
                            value = secondaryColor,
                            onValue = { secondaryColor = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
                    ) {
                        AssColorControl(
                            label = "描边",
                            value = outlineColor,
                            onValue = { outlineColor = it },
                            modifier = Modifier.weight(1f),
                        )
                        AssColorControl(
                            label = "阴影 / 背景",
                            value = backColor,
                            onValue = { backColor = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                Text(
                    "ASS Style 字段",
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                )
                Column(verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                        SmallField("Scale X %", scaleX, { scaleX = it }, Modifier.weight(1f))
                        SmallField("Scale Y %", scaleY, { scaleY = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small)) {
                        SmallField("旋转 Z°", angle, { angle = it }, Modifier.weight(1f))
                        SmallField("BorderStyle", borderStyle, { borderStyle = it }, Modifier.weight(1f))
                        SmallField("Encoding", encoding, { encoding = it }, Modifier.weight(1f))
                    }
                }
                Text(
                    "这些字段始终可见；是否使用取决于你对 ASS 的理解程度。",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text("有效值来源", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                if (focusedEvent == null || (!hasInlineStyleOverrides && !hasEventMarginOverrides)) {
                    Text(
                        "当前值由 Style 控制。",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val reasons = buildList {
                        if (focusedSources.isNotEmpty()) add(focusedSources.joinToString(" / "))
                        if (hasEventMarginOverrides) {
                            add(
                                "Margin " + focusedEvent.marginL + "/" +
                                    focusedEvent.marginR + "/" + focusedEvent.marginV
                            )
                        }
                    }
                    Text(
                        "当前 Event 覆盖 Style · " + reasons.joinToString(" · "),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                    )
                    OutlinedButton(
                        onClick = { focusedEvent?.id?.let(viewModel::clearEventStyleOverrides) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清除覆盖，改由 Style 控制") }
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
            horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
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
                    Modifier.fillMaxWidth().heightIn(min = WorkbenchDimens.MinTouchTarget).background(
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
    val names = AssInlineSyntax.analyze(event.text).tagNames
    fun has(vararg tags: String) = tags.any { it.lowercase() in names }

    return buildList {
        if (has("fn")) add("字体")
        if (has("fs")) add("字号")
        if (has("b")) add("粗体")
        if (has("i")) add("斜体")
        if (has("u")) add("下划线")
        if (has("s")) add("删除线")
        if (has("fsp")) add("字距")
        if (has("bord", "xbord", "ybord")) add("描边")
        if (has("shad", "xshad", "yshad")) add("阴影")
        if (has("an", "a")) add("对齐")
        if (has("pos", "move", "org")) add("位置")
        if (has("c", "1c", "2c", "3c", "4c", "alpha", "1a", "2a", "3a", "4a")) add("颜色/透明度")
        if (has("r")) add("Style 重置")
        if (has("fscx", "fscy", "fr", "frx", "fry", "frz", "fax", "fay")) add("变换")
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
