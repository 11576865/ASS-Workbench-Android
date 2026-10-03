package io.github.assworkbench.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssFlipEntranceSpec
import io.github.assworkbench.domain.AssGlowFxSpec
import io.github.assworkbench.domain.AssReflectionFxSpec
import io.github.assworkbench.domain.AssReflectionFadeSpec
import io.github.assworkbench.domain.AssReflectionFadeDirection
import io.github.assworkbench.domain.AssFxTemplate

/**
 * First composition-level FX authoring slice.
 *
 * This deliberately generates ordinary independent ASS Events. There is no hidden persistent
 * parent/child relation after generation, so raw ASS remains the canonical and portable result.
 */
@Composable
internal fun FxCompositionPane(
    event: AssEvent,
    targetEventIds: Set<Long>,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
) {
    var offsetY by rememberSaveable(event.id) { mutableStateOf("56") }
    var scaleY by rememberSaveable(event.id) { mutableStateOf("35") }
    var opacity by rememberSaveable(event.id) { mutableStateOf("35") }
    var blur by rememberSaveable(event.id) { mutableStateOf("1.5") }
    var withFade by rememberSaveable(event.id) { mutableStateOf(false) }
    var fadeBands by rememberSaveable(event.id) { mutableStateOf("6") }
    var fadeDepth by rememberSaveable(event.id) { mutableStateOf("120") }
    var fadeFarOpacity by rememberSaveable(event.id) { mutableStateOf("0") }
    var fadeDirection by rememberSaveable(event.id) { mutableStateOf(AssReflectionFadeDirection.AUTO.name) }
    var withGlow by rememberSaveable(event.id) { mutableStateOf(true) }
    var glowOpacity by rememberSaveable(event.id) { mutableStateOf("22") }
    var glowBlur by rememberSaveable(event.id) { mutableStateOf("4") }
    var glowBorder by rememberSaveable(event.id) { mutableStateOf("3") }
    var withEntrance by rememberSaveable(event.id) { mutableStateOf(true) }
    var entranceMs by rememberSaveable(event.id) { mutableStateOf("280") }
    var entranceStartScale by rememberSaveable(event.id) { mutableStateOf("8") }
    var entranceOvershoot by rememberSaveable(event.id) { mutableStateOf("118") }
    var entranceRotationX by rememberSaveable(event.id) { mutableStateOf("88") }
    var entranceAccel by rememberSaveable(event.id) { mutableStateOf("") }

    val savedTemplates by viewModel.fxTemplates.collectAsState()
    var templateName by rememberSaveable { mutableStateOf("") }
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }

    val parsedOffset = offsetY.toDoubleOrNull()
    val parsedScale = scaleY.toDoubleOrNull()
    val parsedOpacity = opacity.toDoubleOrNull()
    val parsedBlur = blur.toDoubleOrNull()
    val parsedFadeBands = fadeBands.toIntOrNull()
    val parsedFadeDepth = fadeDepth.toDoubleOrNull()
    val parsedFadeFarOpacity = fadeFarOpacity.toDoubleOrNull()
    val parsedGlowOpacity = glowOpacity.toDoubleOrNull()
    val parsedGlowBlur = glowBlur.toDoubleOrNull()
    val parsedGlowBorder = glowBorder.toDoubleOrNull()
    val parsedEntranceMs = entranceMs.toLongOrNull()
    val parsedEntranceStartScale = entranceStartScale.toDoubleOrNull()
    val parsedEntranceOvershoot = entranceOvershoot.toDoubleOrNull()
    val parsedEntranceRotationX = entranceRotationX.toDoubleOrNull()
    val parsedEntranceAccel = entranceAccel.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val valid =
        parsedOffset?.isFinite() == true &&
            parsedScale?.let { it.isFinite() && it > 0.0 } == true &&
            parsedOpacity?.let { it.isFinite() && it in 0.0..100.0 } == true &&
            parsedBlur?.let { it.isFinite() && it in 0.0..20.0 } == true &&
            (!withFade || (
                parsedFadeBands != null && parsedFadeBands in 2..16 &&
                    parsedFadeDepth?.let { it.isFinite() && it > 0.0 } == true &&
                    parsedFadeFarOpacity?.let {
                        it.isFinite() && it in 0.0..100.0 && it <= parsedOpacity
                    } == true &&
                    runCatching { AssReflectionFadeDirection.valueOf(fadeDirection) }.isSuccess
                )) &&
            (!withGlow || (
                parsedGlowOpacity?.let { it.isFinite() && it in 0.0..100.0 } == true &&
                    parsedGlowBlur?.let { it.isFinite() && it in 0.0..20.0 } == true &&
                    parsedGlowBorder?.let { it.isFinite() && it in 0.0..20.0 } == true
                )) &&
            (!withEntrance || (
                parsedEntranceMs != null && parsedEntranceMs >= 2L &&
                    parsedEntranceStartScale?.let { it.isFinite() && it > 0.0 } == true &&
                    parsedEntranceOvershoot?.let { it.isFinite() && it > 0.0 } == true &&
                    parsedEntranceRotationX?.isFinite() == true &&
                    (entranceAccel.isBlank() || parsedEntranceAccel?.let { it.isFinite() && it > 0.0 } == true)
                ))

    fun currentTemplate(name: String): AssFxTemplate? {
        if (!valid || name.isBlank()) return null
        return AssFxTemplate(
            name = name.trim(),
            reflection = AssReflectionFxSpec(
                offsetY = requireNotNull(parsedOffset),
                verticalScalePercent = requireNotNull(parsedScale),
                opacityPercent = requireNotNull(parsedOpacity),
                blur = requireNotNull(parsedBlur),
            ),
            glow = if (withGlow) {
                AssGlowFxSpec(
                    opacityPercent = requireNotNull(parsedGlowOpacity),
                    blur = requireNotNull(parsedGlowBlur),
                    border = requireNotNull(parsedGlowBorder),
                )
            } else null,
            fade = if (withFade) {
                AssReflectionFadeSpec(
                    bands = requireNotNull(parsedFadeBands),
                    depthPx = requireNotNull(parsedFadeDepth),
                    farOpacityPercent = requireNotNull(parsedFadeFarOpacity),
                    direction = AssReflectionFadeDirection.valueOf(fadeDirection),
                )
            } else null,
            entrance = if (withEntrance) {
                AssFlipEntranceSpec(
                    durationMs = requireNotNull(parsedEntranceMs),
                    startScalePercent = requireNotNull(parsedEntranceStartScale),
                    overshootScalePercent = requireNotNull(parsedEntranceOvershoot),
                    startRotationXDegrees = requireNotNull(parsedEntranceRotationX),
                    accel = parsedEntranceAccel,
                )
            } else null,
        )
    }

    fun loadTemplate(template: AssFxTemplate) {
        offsetY = template.reflection.offsetY.toString()
        scaleY = template.reflection.verticalScalePercent.toString()
        opacity = template.reflection.opacityPercent.toString()
        blur = template.reflection.blur.toString()
        withFade = template.fade != null
        template.fade?.let { fade ->
            fadeBands = fade.bands.toString()
            fadeDepth = fade.depthPx.toString()
            fadeFarOpacity = fade.farOpacityPercent.toString()
            fadeDirection = fade.direction.name
        }
        withGlow = template.glow != null
        template.glow?.let { glow ->
            glowOpacity = glow.opacityPercent.toString()
            glowBlur = glow.blur.toString()
            glowBorder = glow.border.toString()
        }
        withEntrance = template.entrance != null
        template.entrance?.let { entrance ->
            entranceMs = entrance.durationMs.toString()
            entranceStartScale = entrance.startScalePercent.toString()
            entranceOvershoot = entrance.overshootScalePercent.toString()
            entranceRotationX = entrance.startRotationXDegrees.toString()
            entranceAccel = entrance.accel?.toString().orEmpty()
        }
        templateName = template.name
    }

    DisposableEffect(event.id, targetEventIds) {
        onDispose { viewModel.clearTransientPreview("fx-composition") }
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("fx-composition-pane"),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("FX Composition · 多层镜像", style = MaterialTheme.typography.titleSmall)
            Text(
                if (targetEventIds.size > 1) {
                    "当前将对选中的 ${targetEventIds.size} 条字幕一次性生成柔光层 + 倒影层，并可给各自主体写入翻转 / 拉伸关键帧。整个批次只产生一次 Undo 事务。"
                } else {
                    "一次提交可生成柔光层 + 倒影层，并可给主体写入翻转 / 拉伸关键帧。生成后都是普通独立 ASS Event，没有隐藏联动。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("可复用模板", style = MaterialTheme.typography.labelMedium)
            if (savedTemplates.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    savedTemplates.forEach { saved ->
                        FilterChip(
                            selected = selectedTemplateId == saved.id,
                            onClick = {
                                selectedTemplateId = saved.id
                                loadTemplate(saved.template)
                            },
                            label = { Text(saved.template.name) },
                        )
                    }
                }
                if (selectedTemplateId != null) {
                    TextButton(
                        onClick = {
                            selectedTemplateId?.let(viewModel::deleteFxTemplate)
                            selectedTemplateId = null
                        },
                        modifier = Modifier.testTag("fx-template-delete"),
                    ) {
                        Text("删除已选模板")
                    }
                }
            } else {
                Text(
                    "尚未保存模板。模板只保存 FX 参数，不写入 ASS；应用时仍编译成普通 Event。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it.take(80) },
                    label = { Text("模板名称") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-template-name"),
                )
                Button(
                    enabled = currentTemplate(templateName) != null,
                    onClick = {
                        currentTemplate(templateName)?.let(viewModel::saveFxTemplate)
                        selectedTemplateId = null
                    },
                    modifier = Modifier.testTag("fx-template-save"),
                ) {
                    Text("保存")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = offsetY,
                    onValueChange = { offsetY = it },
                    label = { Text("倒影 Y 偏移") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-reflection-offset-y"),
                )
                OutlinedTextField(
                    value = scaleY,
                    onValueChange = { scaleY = it },
                    label = { Text("高度 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-reflection-scale-y"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = opacity,
                    onValueChange = { opacity = it },
                    label = { Text("不透明度 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-reflection-opacity"),
                )
                OutlinedTextField(
                    value = blur,
                    onValueChange = { blur = it },
                    label = { Text("Blur") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("fx-reflection-blur"),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = withFade,
                    onCheckedChange = { withFade = it },
                    modifier = Modifier.testTag("fx-reflection-with-fade"),
                )
                Text("空间渐隐 · 分带 Clip")
            }
            if (withFade) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = fadeBands,
                        onValueChange = { fadeBands = it },
                        label = { Text("分段 2..16") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-fade-bands"),
                    )
                    OutlinedTextField(
                        value = fadeDepth,
                        onValueChange = { fadeDepth = it },
                        label = { Text("深度 px") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-fade-depth"),
                    )
                    OutlinedTextField(
                        value = fadeFarOpacity,
                        onValueChange = { fadeFarOpacity = it },
                        label = { Text("末端不透明度 %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-fade-far-opacity"),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        AssReflectionFadeDirection.AUTO to "自动",
                        AssReflectionFadeDirection.DOWN to "向下",
                        AssReflectionFadeDirection.UP to "向上",
                    ).forEach { (direction, label) ->
                        FilterChip(
                            selected = fadeDirection == direction.name,
                            onClick = { fadeDirection = direction.name },
                            label = { Text(label) },
                        )
                    }
                }
                Text(
                    "渐隐通过多个互不重叠的矩形 Clip Event 近似空间透明度梯度。当前拒绝 \\move、矢量/iClip、额外行内 Clip，以及已有 alpha/fad/fade 控制，避免语义覆盖或几何脱节。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = withGlow,
                    onCheckedChange = { withGlow = it },
                    modifier = Modifier.testTag("fx-mirror-with-glow"),
                )
                Text("生成主体后方柔光层")
            }
            if (withGlow) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = glowOpacity,
                        onValueChange = { glowOpacity = it },
                        label = { Text("柔光不透明度 %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-glow-opacity"),
                    )
                    OutlinedTextField(
                        value = glowBlur,
                        onValueChange = { glowBlur = it },
                        label = { Text("柔光 Blur") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-glow-blur"),
                    )
                }
                OutlinedTextField(
                    value = glowBorder,
                    onValueChange = { glowBorder = it },
                    label = { Text("柔光 Border") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("fx-glow-border"),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = withEntrance,
                    onCheckedChange = { withEntrance = it },
                    modifier = Modifier.testTag("fx-reflection-with-entrance"),
                )
                Text("同时给主体添加翻转 / 拉伸入场")
            }
            if (withEntrance) {
                OutlinedTextField(
                    value = entranceMs,
                    onValueChange = { entranceMs = it },
                    label = { Text("入场时长 ms") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("fx-entrance-duration"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = entranceStartScale,
                        onValueChange = { entranceStartScale = it },
                        label = { Text("起始高度 %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-entrance-start-scale"),
                    )
                    OutlinedTextField(
                        value = entranceOvershoot,
                        onValueChange = { entranceOvershoot = it },
                        label = { Text("回弹高度 %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-entrance-overshoot"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = entranceRotationX,
                        onValueChange = { entranceRotationX = it },
                        label = { Text("起始 X 旋转 °") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-entrance-rotation-x"),
                    )
                    OutlinedTextField(
                        value = entranceAccel,
                        onValueChange = { entranceAccel = it },
                        label = { Text("Accel（空=线性）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("fx-entrance-accel"),
                    )
                }
            }

            if (!valid) {
                Text(
                    "参数无效：高度需 > 0；各不透明度 0..100；渐隐末端不透明度不得高于倒影；Blur/Border 0..20；渐隐分段 2..16、深度 > 0；入场至少 2 ms；起始/回弹高度需 > 0；Accel 为空或 > 0。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val currentRecipe = currentTemplate("当前参数")
            currentRecipe?.let { recipe ->
                val generatedPerSource = (if (recipe.glow != null) 1 else 0) +
                    (recipe.fade?.bands ?: 1)
                val estimatedGenerated = generatedPerSource * targetEventIds.size
                Text(
                    "预计生成 ${estimatedGenerated} 个 companion Event（不含 ${targetEventIds.size} 个源 Event）。" +
                        if (estimatedGenerated >= 128) " 当前批次较大，建议先预览并分批提交。" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (estimatedGenerated >= 128) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("fx-generated-event-estimate"),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    enabled = currentRecipe != null,
                    onClick = {
                        currentRecipe?.let { recipe ->
                            viewModel.previewMirrorFxComposition(
                                eventIds = targetEventIds,
                                reflection = recipe.reflection,
                                glow = recipe.glow,
                                fade = recipe.fade,
                                entrance = recipe.entrance,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("fx-preview-reflection"),
                ) {
                    Text("预览")
                }
                Button(
                    enabled = currentRecipe != null,
                    onClick = {
                        currentRecipe?.let { recipe ->
                            viewModel.createMirrorFxComposition(
                                eventIds = targetEventIds,
                                reflection = recipe.reflection,
                                glow = recipe.glow,
                                fade = recipe.fade,
                                entrance = recipe.entrance,
                            )
                        }
                    },
                    modifier = Modifier.weight(2f).testTag("fx-compose-reflection"),
                ) {
                    Text(
                        (if (targetEventIds.size > 1) "对 ${targetEventIds.size} 条字幕 · " else "") +
                        buildList {
                            if (withGlow) add("柔光")
                            add(if (withFade) "渐隐倒影" else "倒影")
                            if (withEntrance) add("翻转入场")
                        }.joinToString(" + ")
                    )
                }
            }

            Text(
                "位置继承会被解析成显式 \\pos；普通倒影可整体偏移 \\move 路径。启用空间渐隐后，由于 Clip 固定在屏幕坐标中，\\move 会被明确拒绝。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
