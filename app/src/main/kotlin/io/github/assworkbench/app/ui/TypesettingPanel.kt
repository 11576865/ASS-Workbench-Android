package io.github.assworkbench.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var advancedOpen by remember { mutableStateOf(false) }
    var styleMenuOpen by remember { mutableStateOf(false) }
    var styleManageMode by remember { mutableStateOf<StyleManageMode?>(null) }
    var styleNameDraft by remember(style.name) { mutableStateOf(style.name) }
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


    val focusedEvent = state.focusedEventId?.let { id -> state.document.events.firstOrNull { it.id == id } }
    val focusedSources = focusedEvent?.let(::styleOverrideSources).orEmpty()
    val hasInlineStyleOverrides = focusedSources.isNotEmpty()
    val hasEventMarginOverrides = focusedEvent?.let {
        it.marginL > 0 || it.marginR > 0 || it.marginV > 0
    } == true
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
            secondaryColor = secondaryColor,
            outlineColor = outlineColor,
            backColor = backColor,
            scaleX = scaleX.toDoubleOrNull() ?: return@LaunchedEffect,
            scaleY = scaleY.toDoubleOrNull() ?: return@LaunchedEffect,
            angle = angle.toDoubleOrNull() ?: return@LaunchedEffect,
            borderStyle = borderStyle.toIntOrNull() ?: return@LaunchedEffect,
            encoding = encoding.toIntOrNull() ?: return@LaunchedEffect,
        )
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
        modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                StyleGeometryPreview(
                    playResX = state.document.playResX,
                    playResY = state.document.playResY,
                    alignment = alignment,
                    marginL = marginL.toIntOrNull() ?: style.marginL,
                    marginR = marginR.toIntOrNull() ?: style.marginR,
                    marginV = marginV.toIntOrNull() ?: style.marginV,
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallField("字号", fontSize, { fontSize = it }, Modifier.weight(1f))
                        SmallField("字距", spacing, { spacing = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallField("描边", outline, { outline = it }, Modifier.weight(1f))
                        SmallField("阴影", shadow, { shadow = it }, Modifier.weight(1f))
                    }
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { value ->
                                if (alignment == value) {
                                    Button(onClick = { alignment = value }, modifier = Modifier.weight(1f)) { Text(value.toString()) }
                                } else {
                                    OutlinedButton(onClick = { alignment = value }, modifier = Modifier.weight(1f)) { Text(value.toString()) }
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            label = "次要",
                            value = secondaryColor,
                            onValue = { secondaryColor = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                TextButton(
                    onClick = { advancedOpen = !advancedOpen },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (advancedOpen) "收起高级 Style 参数" else "高级 Style 参数")
                }
                if (advancedOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallField("Scale X %", scaleX, { scaleX = it }, Modifier.weight(1f))
                            SmallField("Scale Y %", scaleY, { scaleY = it }, Modifier.weight(1f))
                            SmallField("旋转 Z°", angle, { angle = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallField("BorderStyle", borderStyle, { borderStyle = it }, Modifier.weight(1f))
                            SmallField("Encoding", encoding, { encoding = it }, Modifier.weight(1f))
                        }
                        Text(
                            "ASS 原生 Style 字段",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                        onClick = viewModel::clearFocusedStyleOverrides,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("清除覆盖，改由 Style 控制") }
                }
            }
            item {
                TextButton(
                    onClick = { bilingualOpen = !bilingualOpen },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (bilingualOpen) "收起双语布局工具" else "双语 60/40 布局工具")
                }
                if (bilingualOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "60/40 参考线（HSR / 黑屏工作流）",
                                modifier = Modifier.weight(1f),
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            )
                            Switch(
                                checked = state.showLayoutGuides,
                                onCheckedChange = { viewModel.toggleLayoutGuides() },
                            )
                        }
                        Text(
                            "安全区 3% / 5% · 中央间隔 " + geometry.centralGap +
                                " · 分界 Y " + geometry.sourceBoundaryY + "/" + geometry.targetBoundaryY,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StylePicker(
                                label = "上方 / 源",
                                value = sourceStyle,
                                names = styleNames,
                                expanded = sourceMenu,
                                onExpanded = { sourceMenu = it },
                                onPick = { sourceStyle = it; sourceMenu = false },
                                modifier = Modifier.weight(1f),
                            )
                            StylePicker(
                                label = "下方 / 目标",
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
                        ) { Text("应用 60/40 预设") }
                    }
                }
            }
        }
}

@Composable
private fun StyleGeometryPreview(
    playResX: Int,
    playResY: Int,
    alignment: Int,
    marginL: Int,
    marginR: Int,
    marginV: Int,
    modifier: Modifier = Modifier,
) {
    val outline = androidx.compose.material3.MaterialTheme.colorScheme.outline
    val safe = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val anchorColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    val surface = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier.background(surface.copy(alpha = 0.28f))) {
        val px = playResX.coerceAtLeast(1).toFloat()
        val py = playResY.coerceAtLeast(1).toFloat()
        val sx = size.width / px
        val sy = size.height / py
        val left = marginL.coerceAtLeast(0) * sx
        val right = size.width - marginR.coerceAtLeast(0) * sx
        val top = marginV.coerceAtLeast(0) * sy
        val bottom = size.height - marginV.coerceAtLeast(0) * sy

        drawRect(
            color = outline,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawRect(
            color = safe.copy(alpha = 0.75f),
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(
                (right - left).coerceAtLeast(0f),
                (bottom - top).coerceAtLeast(0f),
            ),
            style = Stroke(width = 1.dp.toPx()),
        )

        val x = when (alignment) {
            1, 4, 7 -> left
            3, 6, 9 -> right
            else -> size.width / 2f
        }
        val y = when (alignment) {
            7, 8, 9 -> top
            4, 5, 6 -> size.height / 2f
            else -> bottom
        }
        drawCircle(anchorColor, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
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
