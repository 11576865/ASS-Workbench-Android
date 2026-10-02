package io.github.assworkbench.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import io.github.assworkbench.app.BuildConfig
import io.github.assworkbench.app.StartupProbe
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssPositionMode
import io.github.assworkbench.domain.AssRendererRiskAnalyzer
import io.github.assworkbench.fonts.RendererLogParser
import io.github.assworkbench.app.ui.interaction.ClearInteractionOwnerOnDispose
import io.github.assworkbench.app.ui.interaction.InteractionOverlayRegistry
import io.github.assworkbench.app.ui.interaction.InteractionProxySpec
import io.github.assworkbench.app.ui.preview.PreviewTargetCandidate
import io.github.assworkbench.app.ui.preview.PreviewTargetConfidence
import io.github.assworkbench.app.ui.preview.PreviewTargetResolver
import io.github.yuroyami.libmpvkt.Mpv
import io.github.yuroyami.libmpvkt.MpvCommands
import io.github.yuroyami.libmpvkt.MpvResult
import io.github.yuroyami.libmpvkt.MpvPlaybackState
import io.github.yuroyami.libmpvkt.MpvProperties
import io.github.yuroyami.libmpvkt.getOrNull
import io.github.yuroyami.libmpvkt.compose.MpvSurface
import io.github.yuroyami.libmpvkt.compose.rememberMpv
import io.github.yuroyami.libmpvkt.stream.ContentResolverStreamProvider
import io.github.yuroyami.libmpvkt.view.MpvOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal data class PreviewObjectPick(
    val frozenPositionMs: Long,
    val playX: Double,
    val playY: Double,
    val viewportFractionX: Float,
    val viewportFractionY: Float,
    val candidates: List<PreviewTargetCandidate>,
)

@Composable
internal fun VideoPreview(
    videoUri: String?,
    document: AssDocument,
    renderDocument: AssDocument = document,
    seekRequestMs: Long?,
    seekRequestNonce: Long,
    onPosition: (Long) -> Unit,
    onRendererDiagnostics: (List<String>) -> Unit,
    configDir: File,
    fontsDir: File,
    fontRevision: Long,
    initialPositionMs: Long,
    focusedEventId: Long?,
    selectedAudioOrdinal: Int? = null,
    positionEditEventId: Long?,
    onPreviewEventPosition: (Double, Double) -> Unit,
    onSetEventPosition: (Double, Double) -> Unit,
    onPreviewEventMove: (Double, Double, Double, Double) -> Unit,
    onSetEventMove: (Double, Double, Double, Double) -> Unit,
    onPreviewEventOrigin: (Double, Double) -> Unit,
    onSetEventOrigin: (Double, Double) -> Unit,
    onPreviewEventRotation: (Double) -> Unit,
    onSetEventRotation: (Double) -> Unit,
    scaleLocked: Boolean,
    onPreviewEventScale: (Double, Double) -> Unit,
    onSetEventScale: (Double, Double) -> Unit,
    onPreviewEventShear: (Double, Double) -> Unit,
    onSetEventShear: (Double, Double) -> Unit,
    onPreviewEventClip: (Double, Double, Double, Double, Boolean) -> Unit,
    onSetEventClip: (Double, Double, Double, Double, Boolean) -> Unit,
    onCancelEventPositionPreview: () -> Unit,
    onFocusEvent: (Long) -> Unit,
    onEditEventPosition: (Long) -> Unit = onFocusEvent,
    onSetEventTiming: (Long, Long, Long) -> Unit,
    onOpenVideo: () -> Unit,
    onOpenTimeline: () -> Unit = {},
    rendererEnabled: Boolean = true,
    onEnableRenderer: () -> Unit = {},
    fillViewport: Boolean = false,
    onVideoAspectRatio: (Float) -> Unit = {},
    interactionRegistry: InteractionOverlayRegistry? = null,
    viewportGesturesEnabled: Boolean = false,
    onObjectLongPress: ((PreviewObjectPick) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (!rendererEnabled) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Renderer 安全模式",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "上次启动记录显示 native 预览 core 在初始化完成前退出。编辑、Raw ASS、保存与 MKV 工作流仍可使用；当前不会加载 mpv/libass。",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Button(onClick = onEnableRenderer) { Text("再次尝试启用预览") }
            }
        }
        return
    }

    var normalPreview by remember { mutableStateOf(!BuildConfig.ASSWB_RENDERER_EXPERIMENTAL) }

    if (BuildConfig.ASSWB_RENDERER_EXPERIMENTAL && !normalPreview) {
        ExperimentalRendererStartupProbe(
            configDir = configDir,
            fontsDir = fontsDir,
            onEnterPreview = {
                writeStartupProbe(configDir, "7_preview_core", "starting", "handoff to rememberMpv")
                normalPreview = true
            },
            modifier = modifier,
        )
        return
    }

    var resumePositionMs by remember(videoUri) { mutableLongStateOf(initialPositionMs.coerceAtLeast(0L)) }
    LaunchedEffect(initialPositionMs, videoUri) {
        if (initialPositionMs > 0L && kotlin.math.abs(initialPositionMs - resumePositionMs) > 1000L) {
            resumePositionMs = initialPositionMs
        }
    }

    // Recreate the entire native preview subtree when the published font set
    // changes. Depending on MpvOptions as a rememberMpv argument is not a strong
    // enough lifecycle guarantee: forcing a new composition identity disposes the
    // old mpv/libass core and constructs a fresh one that rescans fonts. The
    // playback position lives outside this key and is handed back in below.
    key(fontRevision) {
        AuthoritativeMpvPreview(
            videoUri = videoUri,
            document = document,
            renderDocument = renderDocument,
            seekRequestMs = seekRequestMs,
            seekRequestNonce = seekRequestNonce,
            onPosition = { positionMs ->
                resumePositionMs = positionMs
                onPosition(positionMs)
            },
            onRendererDiagnostics = onRendererDiagnostics,
            configDir = configDir,
            fontsDir = fontsDir,
            fontRevision = fontRevision,
            initialPositionMs = resumePositionMs,
            focusedEventId = focusedEventId,
            selectedAudioOrdinal = selectedAudioOrdinal,
            positionEditEventId = positionEditEventId,
            onPreviewEventPosition = onPreviewEventPosition,
            onSetEventPosition = onSetEventPosition,
            onPreviewEventMove = onPreviewEventMove,
            onSetEventMove = onSetEventMove,
            onPreviewEventOrigin = onPreviewEventOrigin,
            onSetEventOrigin = onSetEventOrigin,
            onPreviewEventRotation = onPreviewEventRotation,
            onSetEventRotation = onSetEventRotation,
            scaleLocked = scaleLocked,
            onPreviewEventScale = onPreviewEventScale,
            onSetEventScale = onSetEventScale,
            onPreviewEventShear = onPreviewEventShear,
            onSetEventShear = onSetEventShear,
            onPreviewEventClip = onPreviewEventClip,
            onSetEventClip = onSetEventClip,
            onCancelEventPositionPreview = onCancelEventPositionPreview,
            onFocusEvent = onFocusEvent,
            onEditEventPosition = onEditEventPosition,
            onSetEventTiming = onSetEventTiming,
            onOpenVideo = onOpenVideo,
            onOpenTimeline = onOpenTimeline,
            fillViewport = fillViewport,
            onVideoAspectRatio = onVideoAspectRatio,
            interactionRegistry = interactionRegistry,
            viewportGesturesEnabled = viewportGesturesEnabled,
            onObjectLongPress = onObjectLongPress,
            modifier = modifier,
        )
    }
}

@Composable
private fun ExperimentalRendererStartupProbe(
    configDir: File,
    fontsDir: File,
    onEnterPreview: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current.applicationContext
    val probeFile = remember(configDir) { File(configDir, "renderer-startup-probe.txt") }
    val rendererLogFile = remember(configDir) { File(configDir, "renderer-font.log") }
    var core by remember { mutableStateOf<Mpv?>(null) }
    var step by remember { mutableStateOf(1) }
    var status by remember { mutableStateOf("应用 UI 已启动；native renderer 尚未加载。") }
    var breadcrumb by remember(probeFile) {
        mutableStateOf(
            runCatching { if (probeFile.isFile) probeFile.readText() else "无历史探针记录" }
                .getOrDefault("无法读取历史探针记录"),
        )
    }

    val activeCore = core
    DisposableEffect(activeCore) {
        onDispose {
            if (activeCore != null && !activeCore.isClosed) {
                runCatching { activeCore.close() }
            }
        }
    }

    fun mark(stage: String, state: String, detail: String = "") {
        writeStartupProbe(configDir, stage, state, detail)
        breadcrumb = runCatching { probeFile.readText() }.getOrDefault("$stage / $state")
    }

    fun fail(stage: String, t: Throwable) {
        val detail = "${t::class.java.simpleName}: ${t.message ?: "无消息"}"
        status = "失败：$detail"
        mark(stage, "failure", detail)
    }

    val buttonLabel = when (step) {
        1 -> "1  加载 native 库"
        2 -> "2  创建 mpv core"
        3 -> "3  应用最小 Fontconfig options"
        4 -> "4  mpv_initialize（最小）"
        5 -> "5  创建完整配置 core"
        6 -> "6  mpv_initialize（完整）"
        7 -> "7  进入正常 Compose 预览"
        else -> "探针完成"
    }

    Box(
        modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Fontconfig 分阶段启动探针", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(
                "每一步执行前都会持久化 starting；若 native 直接杀进程，下次打开即可看到上次停在哪一步。",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(status, color = Color.White, style = MaterialTheme.typography.labelMedium)
            Text(
                "上次记录：\n$breadcrumb",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 6,
            )

            Button(
                enabled = step in 1..7,
                onClick = {
                    when (step) {
                        1 -> {
                            mark("1_native_load", "starting")
                            runCatching { Mpv.apiVersion }
                                .onSuccess { version ->
                                    status = "native 加载成功；mpv client API=$version"
                                    mark("1_native_load", "success", "clientApi=$version")
                                    step = 2
                                }
                                .onFailure { fail("1_native_load", it) }
                        }
                        2 -> {
                            mark("2_mpv_create", "starting")
                            runCatching { Mpv.create(context) }
                                .onSuccess { created ->
                                    core = created
                                    status = "mpv core 创建成功：${created.clientName}"
                                    mark("2_mpv_create", "success", "client=${created.clientName}")
                                    step = 3
                                }
                                .onFailure { fail("2_mpv_create", it) }
                        }
                        3 -> {
                            val current = core
                            if (current == null) {
                                status = "内部状态错误：没有 mpv core"
                            } else {
                                mark("3_minimal_fontconfig_options", "starting")
                                runCatching {
                                    rendererLogFile.parentFile?.mkdirs()
                                    if (rendererLogFile.exists()) rendererLogFile.delete()
                                    val critical = listOf(
                                        "config" to "yes",
                                        "config-dir" to configDir.absolutePath,
                                        "vo" to "null",
                                        "force-window" to "no",
                                        "idle" to "yes",
                                        "sub-auto" to "no",
                                        "sub-ass-use-video-data" to "all",
                                        "sid" to "no",
                                        "secondary-sid" to "no",
                                        "embeddedfonts" to "yes",
                                        "sub-fonts-dir" to fontsDir.absolutePath,
                                        "sub-font-provider" to BuildConfig.ASSWB_RENDERER_FONT_PROVIDER,
                                        "log-file" to rendererLogFile.absolutePath,
                                        "msg-level" to "all=v",
                                    )
                                    for ((name, value) in critical) {
                                        when (val result = current.setOption(name, value)) {
                                            is MpvResult.Ok -> Unit
                                            is MpvResult.Fail -> error("$name: ${result.error} ${result.detail.orEmpty()}")
                                        }
                                    }
                                }.onSuccess {
                                    status = "最小 Fontconfig options 全部被 mpv 接受"
                                    mark("3_minimal_fontconfig_options", "success")
                                    step = 4
                                }.onFailure { fail("3_minimal_fontconfig_options", it) }
                            }
                        }
                        4 -> {
                            val current = core
                            if (current == null) {
                                status = "内部状态错误：没有 mpv core"
                            } else {
                                mark("4_minimal_initialize", "starting")
                                runCatching { current.initialize() }
                                    .onSuccess { result ->
                                        when (result) {
                                            is MpvResult.Ok -> {
                                                status = "最小 Fontconfig mpv_initialize 成功"
                                                mark("4_minimal_initialize", "success")
                                                step = 5
                                            }
                                            is MpvResult.Fail -> {
                                                val detail = "${result.error} ${result.detail.orEmpty()}"
                                                status = "mpv_initialize 返回失败：$detail"
                                                mark("4_minimal_initialize", "failure", detail)
                                            }
                                        }
                                    }
                                    .onFailure { fail("4_minimal_initialize", it) }
                            }
                        }
                        5 -> {
                            mark("5_full_core_and_options", "starting")
                            runCatching {
                                core?.let { if (!it.isClosed) it.close() }
                                core = null
                                rendererLogFile.parentFile?.mkdirs()
                                if (rendererLogFile.exists()) rendererLogFile.delete()
                                val created = Mpv.create(context)
                                val options = MpvOptions(
                                    configDir = configDir,
                                    cacheDir = File(context.cacheDir, "mpv-cache").apply { mkdirs() },
                                    extra = mapOf(
                                        "sub-auto" to "no",
                                        "sub-ass-override" to "no",
                                        "sub-ass-use-video-data" to "all",
                                        "embeddedfonts" to "yes",
                                        "sub-fonts-dir" to fontsDir.absolutePath,
                                        "sub-font-provider" to BuildConfig.ASSWB_RENDERER_FONT_PROVIDER,
                                        "log-file" to rendererLogFile.absolutePath,
                                        "msg-level" to "all=v",
                                    ),
                                )
                                options.applyTo(created)
                                when (val provider = created.setOption("sub-font-provider", BuildConfig.ASSWB_RENDERER_FONT_PROVIDER)) {
                                    is MpvResult.Ok -> Unit
                                    is MpvResult.Fail -> error("sub-font-provider: ${provider.error} ${provider.detail.orEmpty()}")
                                }
                                created
                            }.onSuccess { created ->
                                core = created
                                status = "完整配置 core 已创建，尚未 initialize"
                                mark("5_full_core_and_options", "success")
                                step = 6
                            }.onFailure { fail("5_full_core_and_options", it) }
                        }
                        6 -> {
                            val current = core
                            if (current == null) {
                                status = "内部状态错误：没有完整配置 core"
                            } else {
                                mark("6_full_initialize", "starting")
                                runCatching { current.initialize() }
                                    .onSuccess { result ->
                                        when (result) {
                                            is MpvResult.Ok -> {
                                                status = "完整配置 mpv_initialize 成功"
                                                mark("6_full_initialize", "success")
                                                step = 7
                                            }
                                            is MpvResult.Fail -> {
                                                val detail = "${result.error} ${result.detail.orEmpty()}"
                                                status = "完整配置 initialize 返回失败：$detail"
                                                mark("6_full_initialize", "failure", detail)
                                            }
                                        }
                                    }
                                    .onFailure { fail("6_full_initialize", it) }
                            }
                        }
                        7 -> {
                            mark("7_preview_core", "starting", "handoff to rememberMpv")
                            runCatching {
                                core?.let { if (!it.isClosed) it.close() }
                                core = null
                            }.onFailure {
                                fail("7_preview_core", it)
                                return@Button
                            }
                            status = "切换到正常 Compose 预览"
                            onEnterPreview()
                        }
                    }
                },
            ) {
                Text(buttonLabel)
            }

            Button(
                onClick = {
                    core?.let { if (!it.isClosed) runCatching { it.close() } }
                    core = null
                    step = 1
                    status = "探针已重置；native renderer 尚未加载。"
                    runCatching { probeFile.delete() }
                    breadcrumb = "无历史探针记录"
                },
            ) {
                Text("重置探针")
            }
        }
    }
}

@Composable
private fun AuthoritativeMpvPreview(
    videoUri: String?,
    document: AssDocument,
    renderDocument: AssDocument,
    seekRequestMs: Long?,
    seekRequestNonce: Long,
    onPosition: (Long) -> Unit,
    onRendererDiagnostics: (List<String>) -> Unit,
    configDir: File,
    fontsDir: File,
    fontRevision: Long,
    initialPositionMs: Long,
    focusedEventId: Long?,
    selectedAudioOrdinal: Int?,
    positionEditEventId: Long?,
    onPreviewEventPosition: (Double, Double) -> Unit,
    onSetEventPosition: (Double, Double) -> Unit,
    onPreviewEventMove: (Double, Double, Double, Double) -> Unit,
    onSetEventMove: (Double, Double, Double, Double) -> Unit,
    onPreviewEventOrigin: (Double, Double) -> Unit,
    onSetEventOrigin: (Double, Double) -> Unit,
    onPreviewEventRotation: (Double) -> Unit,
    onSetEventRotation: (Double) -> Unit,
    scaleLocked: Boolean,
    onPreviewEventScale: (Double, Double) -> Unit,
    onSetEventScale: (Double, Double) -> Unit,
    onPreviewEventShear: (Double, Double) -> Unit,
    onSetEventShear: (Double, Double) -> Unit,
    onPreviewEventClip: (Double, Double, Double, Double, Boolean) -> Unit,
    onSetEventClip: (Double, Double, Double, Double, Boolean) -> Unit,
    onCancelEventPositionPreview: () -> Unit,
    onFocusEvent: (Long) -> Unit,
    onEditEventPosition: (Long) -> Unit,
    onSetEventTiming: (Long, Long, Long) -> Unit,
    onOpenVideo: () -> Unit,
    onOpenTimeline: () -> Unit,
    fillViewport: Boolean,
    onVideoAspectRatio: (Float) -> Unit,
    interactionRegistry: InteractionOverlayRegistry?,
    viewportGesturesEnabled: Boolean,
    onObjectLongPress: ((PreviewObjectPick) -> Unit)?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val rendererLogFile = remember(configDir) { File(configDir, "renderer-font.log") }
    // Font discovery happens during mpv/libass initialization. A fontRevision
    // therefore creates a fresh core after the new files have been published,
    // rather than relying on sub-reload to rescan provider state in-place.
    val options = remember(configDir, fontsDir, rendererLogFile, fontRevision) {
        rendererLogFile.parentFile?.mkdirs()
        if (rendererLogFile.exists()) rendererLogFile.delete()
        MpvOptions(
            configDir = configDir,
            cacheDir = File(context.cacheDir, "mpv-cache").apply { mkdirs() },
            extra = mapOf(
                "sub-auto" to "no",
                "sub-ass-override" to "no",
                "sub-ass-use-video-data" to "all",
                "sid" to "no",
                "secondary-sid" to "no",
                "embeddedfonts" to "yes",
                "sub-fonts-dir" to fontsDir.absolutePath,
                "sub-font-provider" to BuildConfig.ASSWB_RENDERER_FONT_PROVIDER,
                "hwdec" to "auto-safe",
                "log-file" to rendererLogFile.absolutePath,
                "msg-level" to if (BuildConfig.ASSWB_RENDERER_EXPERIMENTAL) "all=v" else "all=warn",
            ),
        )
    }
    val rendererAttempt = remember(options) {
        StartupProbe.mark(
            context.applicationContext,
            StartupProbe.NORMAL_PREVIEW_CORE_STAGE,
            "starting",
            "provider=" + BuildConfig.ASSWB_RENDERER_FONT_PROVIDER +
                " fontsDir=" + fontsDir.absolutePath,
        )
        System.nanoTime()
    }
    val mpv = rememberMpv(options)
    LaunchedEffect(mpv, rendererAttempt) {
        StartupProbe.mark(
            context.applicationContext,
            StartupProbe.NORMAL_PREVIEW_CORE_STAGE,
            "success",
            "client=" + mpv.clientName,
        )
    }
    LaunchedEffect(mpv, videoUri, selectedAudioOrdinal) {
        if (!videoUri.isNullOrBlank() && selectedAudioOrdinal != null) {
            // mpv audio track IDs are 1-based in the ordinary single-file case.
            // The MediaExtractor catalog keeps ordinal selection stable for waveform
            // analysis while this selects the corresponding playback track.
            mpv.command("set", "aid", (selectedAudioOrdinal + 1).toString())
        }
    }
    val playback by mpv.playback.collectAsState()
    val estimatedFrameNumber by remember(mpv) {
        mpv.observe(MpvProperties.EstimatedFrameNumber)
    }.collectAsState(initial = null)
    val estimatedVideoFps by remember(mpv) {
        mpv.observe(MpvProperties.EstimatedVfFps)
    }.collectAsState(initial = null)
    var protocolReady by remember(mpv) { mutableStateOf(false) }
    var subtitleAttached by remember(mpv, videoUri) { mutableStateOf(false) }
    var osdWidth by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdHeight by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdMarginTop by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdMarginBottom by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdMarginLeft by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdMarginRight by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var playbackControlsVisible by remember(mpv, videoUri) { mutableStateOf(true) }
    var targetCandidates by remember(mpv, videoUri) { mutableStateOf<List<PreviewTargetCandidate>>(emptyList()) }
    var targetPickerOpen by remember(mpv, videoUri) { mutableStateOf(false) }
    var viewportScale by remember(videoUri) { mutableStateOf(1f) }
    var viewportPan by remember(videoUri) { mutableStateOf(Offset.Zero) }
    var lastReportedPositionMs by remember(mpv) { mutableLongStateOf(initialPositionMs.coerceAtLeast(0L)) }
    val previewFile = remember(mpv) { File(context.cacheDir, "ass-preview/current.ass").apply { parentFile?.mkdirs() } }
    val previewPublishGeneration = remember(mpv, videoUri) { AtomicLong(0L) }
    val lastTransientReloadNs = remember(mpv, videoUri) { longArrayOf(0L) }
    val blockingRendererRisks = remember(renderDocument) {
        AssRendererRiskAnalyzer.inspect(renderDocument)
    }
    LaunchedEffect(mpv) {
        if (BuildConfig.ASSWB_RENDERER_EXPERIMENTAL) {
            writeStartupProbe(configDir, "7_preview_core", "success", "rememberMpv returned")
            writeStartupProbe(configDir, "8_content_protocol", "starting")
        }
        mpv.addStreamProtocol("content", ContentResolverStreamProvider(context))
        protocolReady = true
        if (BuildConfig.ASSWB_RENDERER_EXPERIMENTAL) {
            writeStartupProbe(configDir, "8_content_protocol", "success")
        }
    }

    LaunchedEffect(mpv, videoUri, protocolReady) {
        if (!protocolReady) return@LaunchedEffect
        mpv.command(MpvCommands.stop())
        subtitleAttached = false
        if (!videoUri.isNullOrBlank()) {
            mpv.command(MpvCommands.loadFile(videoUri))
            mpv.command("set", "sid", "no")
            mpv.command("set", "secondary-sid", "no")
            if (initialPositionMs > 0) {
                delay(180)
                mpv.command("seek", (initialPositionMs / 1000.0).toString(), "absolute+exact")
            }
        }
    }

    LaunchedEffect(mpv, videoUri, protocolReady, playback.durationSeconds) {
        if (!protocolReady || videoUri.isNullOrBlank() || playback.durationSeconds == null) return@LaunchedEffect
        delay(160)
        mpv[MpvProperties.OsdDimensions].getOrNull()?.let { osd ->
            osdWidth = osd.width.coerceAtLeast(0)
            osdHeight = osd.height.coerceAtLeast(0)
            osdMarginTop = osd.marginTop.coerceAtLeast(0)
            osdMarginBottom = osd.marginBottom.coerceAtLeast(0)
            osdMarginLeft = osd.marginLeft.coerceAtLeast(0)
            osdMarginRight = osd.marginRight.coerceAtLeast(0)
            val videoWidth = (osd.width - osd.marginLeft - osd.marginRight).coerceAtLeast(0)
            val videoHeight = (osd.height - osd.marginTop - osd.marginBottom).coerceAtLeast(0)
            if (videoWidth > 0 && videoHeight > 0) {
                onVideoAspectRatio(videoWidth.toFloat() / videoHeight.toFloat())
            }
        }
    }

    LaunchedEffect(videoUri, positionEditEventId) {
        if (videoUri.isNullOrBlank() || positionEditEventId != null) {
            targetPickerOpen = false
            targetCandidates = emptyList()
        }
    }

    LaunchedEffect(playbackControlsVisible, playback.status, positionEditEventId) {
        if (!playbackControlsVisible || positionEditEventId != null) return@LaunchedEffect
        delay(2_600)
        playbackControlsVisible = false
    }

    LaunchedEffect(mpv, videoUri, renderDocument, protocolReady, fontRevision, blockingRendererRisks) {
        val generation = previewPublishGeneration.incrementAndGet()
        if (!protocolReady || videoUri.isNullOrBlank()) return@LaunchedEffect
        if (blockingRendererRisks.isNotEmpty()) {
            if (subtitleAttached) {
                mpv.command("sub-remove")
                subtitleAttached = false
            }
            onRendererDiagnostics(
                listOf(
                    "Preview safety：已暂停 ASS native 预览；Raw ASS 仍保持可编辑/可保存。",
                    "风险 Event：" + blockingRendererRisks.map { "#" + it.eventId }.distinct().joinToString(", "),
                ) + blockingRendererRisks.take(4).map { it.message }
            )
            return@LaunchedEffect
        }
        val transientRendering = renderDocument != document
        if (transientRendering) {
            val elapsedNs = System.nanoTime() - lastTransientReloadNs[0]
            val remainingNs = 33_000_000L - elapsedNs
            if (remainingNs > 0L) {
                delay((remainingNs + 999_999L) / 1_000_000L)
            }
        } else {
            delay(120)
        }

        val tmp = File(previewFile.parentFile, "current.ass.tmp-" + generation)
        try {
            withContext(Dispatchers.IO) {
                tmp.writeText(AssCodec.write(renderDocument), Charsets.UTF_8)
            }
            if (previewPublishGeneration.get() != generation) return@LaunchedEffect

            withContext(Dispatchers.IO) {
                if (previewFile.exists() && !previewFile.delete()) {
                    error("无法替换 ASS 预览缓存")
                }
                if (!tmp.renameTo(previewFile)) {
                    tmp.copyTo(previewFile, overwrite = true)
                }
            }
        } finally {
            if (tmp.exists()) runCatching { tmp.delete() }
        }
        if (previewPublishGeneration.get() != generation) return@LaunchedEffect
        if (!subtitleAttached) {
            delay(120)
            if (previewPublishGeneration.get() != generation) return@LaunchedEffect
            mpv.command(MpvCommands.subAdd(previewFile.absolutePath))
            subtitleAttached = true
        } else {
            mpv.command(MpvCommands.subReload())
        }
        if (transientRendering) {
            lastTransientReloadNs[0] = System.nanoTime()
            return@LaunchedEffect
        }
        delay(300)
        if (previewPublishGeneration.get() != generation) return@LaunchedEffect
        val previewSource = mpv.getString("current-tracks/sub/external-filename")
        val activeSid = mpv.getString("sid") ?: "unknown"
        onRendererDiagnostics(
            readRendererFontDiagnostics(rendererLogFile) +
                "Preview subtitle：sid=$activeSid · " +
                (previewSource?.let { "external=$it" } ?: "external source 未报告") +
                "ASS canvas：${document.playResX}×${document.playResY} · " +
                "OSD=${osdWidth}×${osdHeight} margins=" +
                "${osdMarginLeft},${osdMarginTop},${osdMarginRight},${osdMarginBottom}"
        )
    }

    LaunchedEffect(seekRequestNonce, mpv) {
        val target = seekRequestMs ?: return@LaunchedEffect
        mpv.command("seek", (target / 1000.0).toString(), "absolute+exact")
    }

    LaunchedEffect(playback.positionSeconds, playback.status) {
        val seconds = playback.positionSeconds ?: return@LaunchedEffect
        val positionMs = (seconds * 1000.0).toLong().coerceAtLeast(0L)
        val activelyPlaying = playback.status == MpvPlaybackState.Status.Playing ||
            playback.status == MpvPlaybackState.Status.Buffering
        if (!activelyPlaying || kotlin.math.abs(positionMs - lastReportedPositionMs) >= 250L) {
            lastReportedPositionMs = positionMs
            onPosition(positionMs)
        }
    }

    BoxWithConstraints(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val reportedAspect = run {
            val videoWidth = (osdWidth - osdMarginLeft - osdMarginRight).coerceAtLeast(0)
            val videoHeight = (osdHeight - osdMarginTop - osdMarginBottom).coerceAtLeast(0)
            if (videoWidth > 0 && videoHeight > 0) {
                (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.25f, 4.0f)
            } else {
                16f / 9f
            }
        }
        val canvasWidth = if (fillViewport) minOf(maxWidth, maxHeight * reportedAspect) else maxWidth
        val canvasModifier = Modifier
            .width(canvasWidth)
            .aspectRatio(reportedAspect)
            .clipToBounds()
            .graphicsLayer {
                scaleX = viewportScale
                scaleY = viewportScale
                translationX = viewportPan.x
                translationY = viewportPan.y
            }
            .then(
                if (!videoUri.isNullOrBlank() && viewportGesturesEnabled && positionEditEventId == null) {
                    Modifier
                        .pointerInput(videoUri, viewportGesturesEnabled) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                viewportScale = (viewportScale * zoom).coerceIn(1f, 4f)
                                viewportPan += pan
                            }
                        }
                        .pointerInput(videoUri, positionEditEventId) {
                            detectTapGestures(
                                onTap = {
                                    targetPickerOpen = false
                                    playbackControlsVisible = true
                                },
                                onLongPress = { offset ->
                                    val positionMs = ((playback.positionSeconds ?: (lastReportedPositionMs / 1000.0)) * 1000.0)
                                        .toLong()
                                        .coerceAtLeast(0L)
                                    val playX = (offset.x / size.width.coerceAtLeast(1) * document.playResX).toDouble()
                                    val playY = (offset.y / size.height.coerceAtLeast(1) * document.playResY).toDouble()
                                    val candidates = PreviewTargetResolver.candidates(
                                        document = document,
                                        positionMs = positionMs,
                                        x = playX,
                                        y = playY,
                                    )
                                    if (onObjectLongPress != null) {
                                        targetPickerOpen = false
                                        targetCandidates = emptyList()
                                        onObjectLongPress(
                                            PreviewObjectPick(
                                                frozenPositionMs = positionMs,
                                                playX = playX,
                                                playY = playY,
                                                viewportFractionX = offset.x / size.width.coerceAtLeast(1),
                                                viewportFractionY = offset.y / size.height.coerceAtLeast(1),
                                                candidates = candidates,
                                            )
                                        )
                                    } else {
                                        targetCandidates = candidates
                                        targetPickerOpen = true
                                    }
                                    playbackControlsVisible = false
                                },
                                onDoubleTap = {
                                    targetPickerOpen = false
                                    viewportScale = 1f
                                    viewportPan = Offset.Zero
                                    playbackControlsVisible = true
                                },
                            )
                        }
                } else {
                    Modifier
                },
            )
        Box(canvasModifier, contentAlignment = Alignment.Center) {
            if (videoUri.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onOpenVideo),
                    color = Color.Black,
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.70f),
                        )
                        Text(
                            "未载入参考视频",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "点击此处选择参考视频；编辑内嵌 ASS 请使用“打开 MKV 工程”",
                            color = Color.White.copy(alpha = 0.60f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            } else {
                MpvSurface(mpv, Modifier.fillMaxSize())
                val positionEvent = positionEditEventId?.let { id ->
                    document.events.firstOrNull { it.id == id }
                }
                val renderPositionEvent = positionEditEventId?.let { id ->
                    renderDocument.events.firstOrNull { it.id == id }
                }
                if (blockingRendererRisks.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(12.dp),
                        color = Color.Black.copy(alpha = 0.86f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "ASS 预览已安全暂停",
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                blockingRendererRisks.first().message,
                                color = Color.White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (blockingRendererRisks.size > 1) {
                                Text(
                                    "另有 " + (blockingRendererRisks.size - 1) + " 个 renderer 风险；请在 QC 中查看。",
                                    color = Color.White.copy(alpha = 0.66f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                "字幕文本未被修改；修正危险参数后预览会自动恢复。",
                                color = Color.White.copy(alpha = 0.66f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                if (positionEvent != null && blockingRendererRisks.isEmpty()) {
                    PositionDragOverlay(
                        document = document,
                        event = positionEvent,
                        renderEvent = renderPositionEvent,
                        onPreview = onPreviewEventPosition,
                        onCommit = onSetEventPosition,
                        onPreviewMove = onPreviewEventMove,
                        onCommitMove = onSetEventMove,
                        onPreviewOrigin = onPreviewEventOrigin,
                        onCommitOrigin = onSetEventOrigin,
                        onPreviewRotation = onPreviewEventRotation,
                        onCommitRotation = onSetEventRotation,
                        scaleLocked = scaleLocked,
                        onPreviewScale = onPreviewEventScale,
                        onCommitScale = onSetEventScale,
                        onPreviewShear = onPreviewEventShear,
                        onCommitShear = onSetEventShear,
                        onCancel = onCancelEventPositionPreview,
                        interactionRegistry = interactionRegistry,
                        modifier = Modifier.fillMaxSize(),
                    )
                    RectClipOverlay(
                        document = document,
                        event = positionEvent,
                        onPreview = onPreviewEventClip,
                        onCommit = onSetEventClip,
                        onCancel = onCancelEventPositionPreview,
                        interactionRegistry = interactionRegistry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (targetPickerOpen && positionEditEventId == null) {
                PreviewTargetPicker(
                    candidates = targetCandidates,
                    onDismiss = {
                        targetPickerOpen = false
                        playbackControlsVisible = true
                    },
                    onFocus = { id ->
                        targetPickerOpen = false
                        onFocusEvent(id)
                    },
                    onEditPosition = { id ->
                        targetPickerOpen = false
                        onEditEventPosition(id)
                    },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                )
            }
            if (!videoUri.isNullOrBlank() && positionEditEventId == null) {
                AnimatedVisibility(
                    visible = playbackControlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Surface(color = Color.Black.copy(alpha = 0.62f)) {
                        PlaybackBar(
                            playback = playback,
                            estimatedFrameNumber = estimatedFrameNumber,
                            estimatedVideoFps = estimatedVideoFps,
                            document = document,
                            focusedEventId = focusedEventId,
                            onFocusEvent = onFocusEvent,
                            onSetEventTiming = onSetEventTiming,
                            onPlayPause = {
                                playbackControlsVisible = true
                                val shouldPause = playback.status == MpvPlaybackState.Status.Playing ||
                                    playback.status == MpvPlaybackState.Status.Buffering
                                mpv[MpvProperties.Pause] = shouldPause
                            },
                            onFrameBack = {
                                playbackControlsVisible = true
                                mpv.command("frame-back-step")
                            },
                            onFrameForward = {
                                playbackControlsVisible = true
                                mpv.command("frame-step")
                            },
                            onSeek = { seconds ->
                                playbackControlsVisible = true
                                mpv.command("seek", seconds.toString(), "absolute+exact")
                            },
                            onOpenTimeline = onOpenTimeline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTargetPicker(
    candidates: List<PreviewTargetCandidate>,
    onDismiss: () -> Unit,
    onFocus: (Long) -> Unit,
    onEditPosition: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(0.92f).testTag("preview-target-picker"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前画面对象", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (candidates.isEmpty()) {
                            "当前时间没有可定位的 Dialogue。"
                        } else {
                            "按 ASS anchor 排序；不是 libass 字形边界命中。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            candidates.forEach { candidate ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("#${candidate.eventId} · ${candidate.styleName} · L${candidate.layer}")
                            Text(
                                candidate.textLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                when (candidate.confidence) {
                                    PreviewTargetConfidence.EXACT_ANCHOR -> "Anchor：ASS 明确坐标"
                                    PreviewTargetConfidence.APPROXIMATE_ANCHOR -> "Anchor：按对齐与边距推导"
                                    PreviewTargetConfidence.UNRESOLVED -> "Anchor：语义冲突 / 无法可靠定位"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (candidate.confidence) {
                                    PreviewTargetConfidence.UNRESOLVED -> MaterialTheme.colorScheme.error
                                    PreviewTargetConfidence.APPROXIMATE_ANCHOR -> MaterialTheme.colorScheme.tertiary
                                    PreviewTargetConfidence.EXACT_ANCHOR -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        TextButton(onClick = { onFocus(candidate.eventId) }) { Text("聚焦") }
                        Button(onClick = { onEditPosition(candidate.eventId) }) { Text("位置") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionDragOverlay(
    document: AssDocument,
    event: AssEvent,
    renderEvent: AssEvent?,
    onPreview: (Double, Double) -> Unit,
    onCommit: (Double, Double) -> Unit,
    onPreviewMove: (Double, Double, Double, Double) -> Unit,
    onCommitMove: (Double, Double, Double, Double) -> Unit,
    onPreviewOrigin: (Double, Double) -> Unit,
    onCommitOrigin: (Double, Double) -> Unit,
    onPreviewRotation: (Double) -> Unit,
    onCommitRotation: (Double) -> Unit,
    scaleLocked: Boolean,
    onPreviewScale: (Double, Double) -> Unit,
    onCommitScale: (Double, Double) -> Unit,
    onPreviewShear: (Double, Double) -> Unit,
    onCommitShear: (Double, Double) -> Unit,
    onCancel: () -> Unit,
    interactionRegistry: InteractionOverlayRegistry? = null,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(event.text) { AssGeometrySemantic.inspect(event.text) }
    val renderGeometry = remember(renderEvent?.text, event.text) {
        AssGeometrySemantic.inspect(renderEvent?.text ?: event.text)
    }
    val style = document.styles.firstOrNull { it.name == event.style }
    val marginL = if (event.marginL > 0) event.marginL else style?.marginL ?: 10
    val marginR = if (event.marginR > 0) event.marginR else style?.marginR ?: 10
    val marginV = if (event.marginV > 0) event.marginV else style?.marginV ?: 10
    val overrideAlignment = remember(event.text) {
        io.github.assworkbench.domain.AssInlineSyntax.analyze(event.text).tags
            .lastOrNull { it.name.equals("an", ignoreCase = true) }
            ?.value?.toIntOrNull()
    }
    val alignment = overrideAlignment ?: style?.alignment ?: 2
    val baseX = when (alignment) {
        1, 4, 7 -> marginL.toDouble()
        3, 6, 9 -> (document.playResX - marginR).toDouble()
        else -> document.playResX / 2.0
    }
    val baseY = when (alignment) {
        7, 8, 9 -> marginV.toDouble()
        4, 5, 6 -> document.playResY / 2.0
        else -> (document.playResY - marginV).toDouble()
    }
    var x by remember(event.id, event.text) { mutableStateOf(geometry.position?.x ?: baseX) }
    var y by remember(event.id, event.text) { mutableStateOf(geometry.position?.y ?: baseY) }
    var originX by remember(event.id, event.text) { mutableStateOf(geometry.origin?.x) }
    var originY by remember(event.id, event.text) { mutableStateOf(geometry.origin?.y) }
    var directRotation by remember(event.id, event.text) { mutableStateOf<Double?>(null) }
    val displayRotation = directRotation ?: renderGeometry.rotationZ ?: style?.angle ?: 0.0
    var directScaleX by remember(event.id, event.text) { mutableStateOf<Double?>(null) }
    var directScaleY by remember(event.id, event.text) { mutableStateOf<Double?>(null) }
    val displayScaleX = directScaleX ?: renderGeometry.scaleX ?: style?.scaleX ?: 100.0
    val displayScaleY = directScaleY ?: renderGeometry.scaleY ?: style?.scaleY ?: 100.0
    var directShearX by remember(event.id, event.text) { mutableStateOf<Double?>(null) }
    var directShearY by remember(event.id, event.text) { mutableStateOf<Double?>(null) }
    val displayShearX = directShearX ?: renderGeometry.shearX ?: 0.0
    val displayShearY = directShearY ?: renderGeometry.shearY ?: 0.0
    val guideColor = MaterialTheme.colorScheme.tertiary

    if (geometry.positionMode == AssPositionMode.CONFLICT) {
        val move = geometry.move
        DisposableEffect(event.id) { onDispose { onCancel() } }
        BoxWithConstraints(
            if (originX != null && originY != null) {
                modifier.pointerInput(event.id, event.text, document.playResX, document.playResY) {
                    var activeHandle = 0 // 1=origin, 2=rotation
                    var lastPreviewAt = 0L
                    detectDragGestures(
                        onDragStart = { touch ->
                            val ox = (originX!! / document.playResX.coerceAtLeast(1)) * size.width
                            val oy = (originY!! / document.playResY.coerceAtLeast(1)) * size.height
                            val originDx = touch.x - ox.toFloat()
                            val originDy = touch.y - oy.toFloat()
                            val originDistance = kotlin.math.sqrt((originDx * originDx + originDy * originDy).toDouble())
                            val theta = Math.toRadians(displayRotation - 90.0)
                            val radius = 56.dp.toPx()
                            val hx = ox + kotlin.math.cos(theta) * radius
                            val hy = oy + kotlin.math.sin(theta) * radius
                            val handleDx = touch.x - hx.toFloat()
                            val handleDy = touch.y - hy.toFloat()
                            val rotationDistance = kotlin.math.sqrt((handleDx * handleDx + handleDy * handleDy).toDouble())
                            val threshold = 32.dp.toPx().toDouble()
                            activeHandle = when {
                                rotationDistance <= threshold -> 2
                                originDistance <= threshold -> 1
                                else -> 0
                            }
                        },
                        onDrag = { change, _ ->
                            if (activeHandle == 0) return@detectDragGestures
                            change.consume()
                            if (activeHandle == 1) {
                                val px = change.position.x.coerceIn(0f, size.width.toFloat())
                                val py = change.position.y.coerceIn(0f, size.height.toFloat())
                                originX = (px / size.width.coerceAtLeast(1) * document.playResX).toDouble()
                                originY = (py / size.height.coerceAtLeast(1) * document.playResY).toDouble()
                            } else {
                                val ox = (originX!! / document.playResX.coerceAtLeast(1)) * size.width
                                val oy = (originY!! / document.playResY.coerceAtLeast(1)) * size.height
                                val rawAngle = Math.toDegrees(kotlin.math.atan2((change.position.y - oy).toDouble(), (change.position.x - ox).toDouble())) + 90.0
                                directRotation = ((rawAngle + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                            }
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastPreviewAt >= 80L) {
                                lastPreviewAt = now
                                if (activeHandle == 1) onPreviewOrigin(originX!!, originY!!) else onPreviewRotation(directRotation!!)
                            }
                        },
                        onDragEnd = {
                            when (activeHandle) {
                                1 -> onCommitOrigin(originX!!, originY!!)
                                2 -> directRotation?.let(onCommitRotation)
                            }
                            directRotation = null
                            activeHandle = 0
                        },
                        onDragCancel = { onCancel(); directRotation = null; activeHandle = 0 },
                    )
                }
            } else modifier
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (move != null) {
                    val sx = (move.start.x / document.playResX.coerceAtLeast(1)) * size.width
                    val sy = (move.start.y / document.playResY.coerceAtLeast(1)) * size.height
                    val ex = (move.end.x / document.playResX.coerceAtLeast(1)) * size.width
                    val ey = (move.end.y / document.playResY.coerceAtLeast(1)) * size.height
                    drawLine(guideColor.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()), androidx.compose.ui.geometry.Offset(ex.toFloat(), ey.toFloat()), 2.dp.toPx())
                    drawCircle(guideColor, 7.dp.toPx(), androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    drawCircle(guideColor, 7.dp.toPx(), androidx.compose.ui.geometry.Offset(ex.toFloat(), ey.toFloat()))
                }
                if (originX != null && originY != null) {
                    val ox = (originX!! / document.playResX.coerceAtLeast(1)) * size.width
                    val oy = (originY!! / document.playResY.coerceAtLeast(1)) * size.height
                    val center = androidx.compose.ui.geometry.Offset(ox.toFloat(), oy.toFloat())
                    drawCircle(guideColor, 10.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    drawLine(guideColor, center + androidx.compose.ui.geometry.Offset(-8.dp.toPx(), -8.dp.toPx()), center + androidx.compose.ui.geometry.Offset(8.dp.toPx(), 8.dp.toPx()), 2.dp.toPx())
                    drawLine(guideColor, center + androidx.compose.ui.geometry.Offset(-8.dp.toPx(), 8.dp.toPx()), center + androidx.compose.ui.geometry.Offset(8.dp.toPx(), -8.dp.toPx()), 2.dp.toPx())
                    val theta = Math.toRadians(displayRotation - 90.0)
                    val rotationHandle = center + androidx.compose.ui.geometry.Offset(
                        (kotlin.math.cos(theta) * 56.dp.toPx()).toFloat(),
                        (kotlin.math.sin(theta) * 56.dp.toPx()).toFloat(),
                    )
                    drawLine(guideColor.copy(alpha = 0.55f), center, rotationHandle, 1.5.dp.toPx())
                    drawCircle(guideColor, 7.dp.toPx(), rotationHandle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                }
            }
            Text(
                if (originX != null) "pos + move 冲突 · 位置只读 · org 可拖动" else "pos + move 冲突 · 已暂停直接位置编辑",
                modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 6.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        return
    }

    val moveGeometry = geometry.move
    if (geometry.positionMode == AssPositionMode.MOVE && moveGeometry != null) {
        var startX by remember(event.id, event.text) { mutableStateOf(moveGeometry.start.x) }
        var startY by remember(event.id, event.text) { mutableStateOf(moveGeometry.start.y) }
        var endX by remember(event.id, event.text) { mutableStateOf(moveGeometry.end.x) }
        var endY by remember(event.id, event.text) { mutableStateOf(moveGeometry.end.y) }

        DisposableEffect(event.id) {
            onDispose { onCancel() }
        }

        val moveProxyOwner = "move-${event.id}"
        ClearInteractionOwnerOnDispose(interactionRegistry, moveProxyOwner)
        val moveProxyDensity = androidx.compose.ui.platform.LocalDensity.current
        var moveOverlayOrigin by remember(event.id) { mutableStateOf(Offset.Zero) }
        var moveOverlaySize by remember(event.id) { mutableStateOf(IntSize.Zero) }
        // Read layout state during composition so onGloballyPositioned invalidates this scope.
        // Reading it only inside SideEffect does not register a Compose state dependency,
        // which can leave the interaction registry unpublished until an unrelated recompose.
        val publishedMoveOverlayOrigin = moveOverlayOrigin
        val publishedMoveOverlaySize = moveOverlaySize
        SideEffect {
            val registry = interactionRegistry
            if (registry != null && publishedMoveOverlaySize.width > 0 && publishedMoveOverlaySize.height > 0) {
                val w = publishedMoveOverlaySize.width.toFloat()
                val h = publishedMoveOverlaySize.height.toFloat()
                fun target(px: Double, py: Double): Offset =
                    publishedMoveOverlayOrigin + Offset(
                        (px / document.playResX.coerceAtLeast(1) * w).toFloat(),
                        (py / document.playResY.coerceAtLeast(1) * h).toFloat(),
                    )
                fun docDelta(delta: Offset): Pair<Double, Double> =
                    (delta.x / w * document.playResX.coerceAtLeast(1)).toDouble() to
                        (delta.y / h * document.playResY.coerceAtLeast(1)).toDouble()
                val baseOffset = with(moveProxyDensity) { Offset(118.dp.toPx(), 34.dp.toPx()) }
                val handles = mutableListOf<InteractionProxySpec>()
                handles += InteractionProxySpec(
                    id = "$moveProxyOwner-start",
                    label = "起点",
                    targetInWindow = target(startX, startY),
                    preferredOffsetPx = baseOffset,
                    onDragDelta = { delta ->
                        val (dx, dy) = docDelta(delta)
                        startX += dx
                        startY += dy
                        onPreviewMove(startX, startY, endX, endY)
                    },
                    onCommit = { onCommitMove(startX, startY, endX, endY) },
                    onCancel = onCancel,
                )
                handles += InteractionProxySpec(
                    id = "$moveProxyOwner-end",
                    label = "终点",
                    targetInWindow = target(endX, endY),
                    preferredOffsetPx = baseOffset + Offset(0f, with(moveProxyDensity) { 68.dp.toPx() }),
                    onDragDelta = { delta ->
                        val (dx, dy) = docDelta(delta)
                        endX += dx
                        endY += dy
                        onPreviewMove(startX, startY, endX, endY)
                    },
                    onCommit = { onCommitMove(startX, startY, endX, endY) },
                    onCancel = onCancel,
                )
                if (originX != null && originY != null) {
                    handles += InteractionProxySpec(
                        id = "$moveProxyOwner-org",
                        label = "原点",
                        targetInWindow = target(originX!!, originY!!),
                        preferredOffsetPx = baseOffset + Offset(with(moveProxyDensity) { 76.dp.toPx() }, 0f),
                        onDragDelta = { delta ->
                            val (dx, dy) = docDelta(delta)
                            originX = originX!! + dx
                            originY = originY!! + dy
                            onPreviewOrigin(originX!!, originY!!)
                        },
                        onCommit = { onCommitOrigin(originX!!, originY!!) },
                        onCancel = onCancel,
                    )
                    handles += InteractionProxySpec(
                        id = "$moveProxyOwner-rotation",
                        label = "旋转",
                        targetInWindow = target(originX!!, originY!!),
                        preferredOffsetPx = baseOffset + Offset(with(moveProxyDensity) { 76.dp.toPx() }, with(moveProxyDensity) { 68.dp.toPx() }),
                        onDragDelta = { delta ->
                            directRotation = ((directRotation ?: displayRotation) + delta.x * 0.35).coerceIn(-3600.0, 3600.0)
                            onPreviewRotation(directRotation!!)
                        },
                        onCommit = { directRotation?.let(onCommitRotation) },
                        onCancel = onCancel,
                    )
                }
                registry.publish(moveProxyOwner, handles)
            }
        }

        BoxWithConstraints(
            modifier
                .onGloballyPositioned {
                    moveOverlayOrigin = it.positionInWindow()
                    moveOverlaySize = it.size
                }
                .pointerInput(event.id, event.text, document.playResX, document.playResY) {
                var activeHandle = 0 // 1=start, 2=end, 3=origin, 4=rotation
                var lastPreviewAt = 0L
                fun distanceTo(px: Double, py: Double, touchX: Float, touchY: Float): Double {
                    val hx = (px / document.playResX.coerceAtLeast(1)) * size.width
                    val hy = (py / document.playResY.coerceAtLeast(1)) * size.height
                    val dx = touchX - hx.toFloat()
                    val dy = touchY - hy.toFloat()
                    return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                }
                detectDragGestures(
                    onDragStart = { touch ->
                        val startDistance = distanceTo(startX, startY, touch.x, touch.y)
                        val endDistance = distanceTo(endX, endY, touch.x, touch.y)
                        val originDistance = if (originX != null && originY != null) distanceTo(originX!!, originY!!, touch.x, touch.y) else Double.POSITIVE_INFINITY
                        val rotationDistance = if (originX != null && originY != null) {
                            val ox = (originX!! / document.playResX.coerceAtLeast(1)) * size.width
                            val oy = (originY!! / document.playResY.coerceAtLeast(1)) * size.height
                            val theta = Math.toRadians(displayRotation - 90.0)
                            val hx = ox + kotlin.math.cos(theta) * 56.dp.toPx()
                            val hy = oy + kotlin.math.sin(theta) * 56.dp.toPx()
                            val dx = touch.x - hx.toFloat()
                            val dy = touch.y - hy.toFloat()
                            kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                        } else Double.POSITIVE_INFINITY
                        val threshold = 36.dp.toPx().toDouble()
                        activeHandle = when {
                            rotationDistance <= threshold -> 4
                            originDistance <= threshold && originDistance <= startDistance && originDistance <= endDistance -> 3
                            startDistance <= threshold && startDistance <= endDistance -> 1
                            endDistance <= threshold -> 2
                            else -> 0
                        }
                    },
                    onDrag = { change, _ ->
                        if (activeHandle == 0) return@detectDragGestures
                        change.consume()
                        val px = change.position.x.coerceIn(0f, size.width.toFloat())
                        val py = change.position.y.coerceIn(0f, size.height.toFloat())
                        var nx = px / size.width.coerceAtLeast(1) * document.playResX
                        var ny = py / size.height.coerceAtLeast(1) * document.playResY
                        val xTargets = listOf(marginL.toDouble(), document.playResX / 2.0, (document.playResX - marginR).toDouble())
                        val yTargets = listOf(marginV.toDouble(), document.playResY / 2.0, (document.playResY - marginV).toDouble())
                        val xThreshold = document.playResX * 0.015
                        val yThreshold = document.playResY * 0.015
                        xTargets.minByOrNull { kotlin.math.abs(nx - it) }?.let { target ->
                            if (kotlin.math.abs(nx - target) < xThreshold) nx = target.toFloat()
                        }
                        yTargets.minByOrNull { kotlin.math.abs(ny - it) }?.let { target ->
                            if (kotlin.math.abs(ny - target) < yThreshold) ny = target.toFloat()
                        }
                        if (activeHandle == 1) {
                            startX = nx.toDouble()
                            startY = ny.toDouble()
                        } else if (activeHandle == 2) {
                            endX = nx.toDouble()
                            endY = ny.toDouble()
                        } else if (activeHandle == 3) {
                            originX = nx.toDouble()
                            originY = ny.toDouble()
                        } else {
                            val ox = (originX!! / document.playResX.coerceAtLeast(1)) * size.width
                            val oy = (originY!! / document.playResY.coerceAtLeast(1)) * size.height
                            val rawAngle = Math.toDegrees(kotlin.math.atan2((change.position.y - oy).toDouble(), (change.position.x - ox).toDouble())) + 90.0
                            directRotation = ((rawAngle + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                        }
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastPreviewAt >= 80L) {
                            lastPreviewAt = now
                            when (activeHandle) {
                                3 -> onPreviewOrigin(originX!!, originY!!)
                                4 -> onPreviewRotation(directRotation!!)
                                else -> onPreviewMove(startX, startY, endX, endY)
                            }
                        }
                    },
                    onDragEnd = {
                        when (activeHandle) {
                            1, 2 -> onCommitMove(startX, startY, endX, endY)
                            3 -> onCommitOrigin(originX!!, originY!!)
                            4 -> directRotation?.let(onCommitRotation)
                        }
                        directRotation = null
                        activeHandle = 0
                    },
                    onDragCancel = {
                        onCancel()
                        directRotation = null
                        activeHandle = 0
                    },
                )
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val sx = (startX / document.playResX.coerceAtLeast(1)) * size.width
                val sy = (startY / document.playResY.coerceAtLeast(1)) * size.height
                val ex = (endX / document.playResX.coerceAtLeast(1)) * size.width
                val ey = (endY / document.playResY.coerceAtLeast(1)) * size.height
                drawLine(
                    color = guideColor.copy(alpha = 0.78f),
                    start = androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()),
                    end = androidx.compose.ui.geometry.Offset(ex.toFloat(), ey.toFloat()),
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(
                    color = guideColor,
                    radius = 9.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
                drawCircle(
                    color = guideColor,
                    radius = 9.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(ex.toFloat(), ey.toFloat()),
                )
                if (originX != null && originY != null) {
                    val ox = (originX!! / document.playResX.coerceAtLeast(1)) * size.width
                    val oy = (originY!! / document.playResY.coerceAtLeast(1)) * size.height
                    val center = androidx.compose.ui.geometry.Offset(ox.toFloat(), oy.toFloat())
                    drawCircle(guideColor, 10.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    drawLine(guideColor, center + androidx.compose.ui.geometry.Offset(-8.dp.toPx(), -8.dp.toPx()), center + androidx.compose.ui.geometry.Offset(8.dp.toPx(), 8.dp.toPx()), 2.dp.toPx())
                    drawLine(guideColor, center + androidx.compose.ui.geometry.Offset(-8.dp.toPx(), 8.dp.toPx()), center + androidx.compose.ui.geometry.Offset(8.dp.toPx(), -8.dp.toPx()), 2.dp.toPx())
                    val theta = Math.toRadians(displayRotation - 90.0)
                    val rotationHandle = center + androidx.compose.ui.geometry.Offset(
                        (kotlin.math.cos(theta) * 56.dp.toPx()).toFloat(),
                        (kotlin.math.sin(theta) * 56.dp.toPx()).toFloat(),
                    )
                    drawLine(guideColor.copy(alpha = 0.55f), center, rotationHandle, 1.5.dp.toPx())
                    drawCircle(guideColor, 7.dp.toPx(), rotationHandle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                }
            }
            Text(
                "move S ${startX.toInt()},${startY.toInt()} → E ${endX.toInt()},${endY.toInt()}" + (if (originX != null) " · org ${originX!!.toInt()},${originY!!.toInt()} · rot ${displayRotation.toInt()}°" else "") + " · 拖动操控杆",
                modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 6.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        return
    }

    DisposableEffect(event.id) {
        onDispose { onCancel() }
    }

    val proxyOwner = "position-${event.id}"
    ClearInteractionOwnerOnDispose(interactionRegistry, proxyOwner)
    val proxyDensity = androidx.compose.ui.platform.LocalDensity.current
    var overlayOriginInWindow by remember(event.id) { mutableStateOf(Offset.Zero) }
    var overlaySizePx by remember(event.id) { mutableStateOf(IntSize.Zero) }
    val playResXProxy = document.playResX.coerceAtLeast(1)
    val playResYProxy = document.playResY.coerceAtLeast(1)
    // Layout coordinates must participate in composition invalidation before SideEffect.
    val publishedOverlayOriginInWindow = overlayOriginInWindow
    val publishedOverlaySizePx = overlaySizePx
    SideEffect {
        val registry = interactionRegistry
        if (registry != null && publishedOverlaySizePx.width > 0 && publishedOverlaySizePx.height > 0) {
            val w = publishedOverlaySizePx.width.toFloat()
            val h = publishedOverlaySizePx.height.toFloat()
            fun target(px: Double, py: Double): Offset =
                publishedOverlayOriginInWindow + Offset(
                    (px / playResXProxy * w).toFloat(),
                    (py / playResYProxy * h).toFloat(),
                )
            fun documentDelta(delta: Offset): Pair<Double, Double> =
                (delta.x / w * playResXProxy).toDouble() to (delta.y / h * playResYProxy).toDouble()
            val preferred = with(proxyDensity) { Offset(118.dp.toPx(), 34.dp.toPx()) }
            fun cancelDraft() {
                x = geometry.position?.x ?: baseX
                y = geometry.position?.y ?: baseY
                originX = geometry.origin?.x
                originY = geometry.origin?.y
                directRotation = null
                directScaleX = null
                directScaleY = null
                directShearX = null
                directShearY = null
                onCancel()
            }
            val handles = mutableListOf<InteractionProxySpec>()
            handles += InteractionProxySpec(
                id = "$proxyOwner-pos",
                label = "位置",
                targetInWindow = target(x, y),
                preferredOffsetPx = preferred,
                onDragDelta = { delta ->
                    val (dx, dy) = documentDelta(delta)
                    x += dx
                    y += dy
                    onPreview(x, y)
                },
                onCommit = { onCommit(x, y) },
                onCancel = ::cancelDraft,
            )
            if (originX != null && originY != null) {
                handles += InteractionProxySpec(
                    id = "$proxyOwner-org",
                    label = "原点",
                    targetInWindow = target(originX!!, originY!!),
                    preferredOffsetPx = preferred + Offset(0f, with(proxyDensity) { 66.dp.toPx() }),
                    onDragDelta = { delta ->
                        val (dx, dy) = documentDelta(delta)
                        originX = originX!! + dx
                        originY = originY!! + dy
                        onPreviewOrigin(originX!!, originY!!)
                    },
                    onCommit = { onCommitOrigin(originX!!, originY!!) },
                    onCancel = ::cancelDraft,
                )
            }
            handles += InteractionProxySpec(
                id = "$proxyOwner-rotation",
                label = "旋转",
                targetInWindow = target(originX ?: x, originY ?: y),
                preferredOffsetPx = preferred + Offset(0f, with(proxyDensity) { 132.dp.toPx() }),
                onDragDelta = { delta ->
                    directRotation = ((directRotation ?: displayRotation) + delta.x * 0.35).coerceIn(-3600.0, 3600.0)
                    onPreviewRotation(directRotation!!)
                },
                onCommit = { directRotation?.let(onCommitRotation) },
                onCancel = ::cancelDraft,
            )
            handles += InteractionProxySpec(
                id = "$proxyOwner-scale",
                label = "缩放",
                targetInWindow = target(x, y),
                preferredOffsetPx = preferred + Offset(with(proxyDensity) { 78.dp.toPx() }, 0f),
                onDragDelta = { delta ->
                    val sx = ((directScaleX ?: displayScaleX) + delta.x * 0.45).coerceIn(1.0, 1000.0)
                    val syCandidate = ((directScaleY ?: displayScaleY) - delta.y * 0.45).coerceIn(1.0, 1000.0)
                    if (scaleLocked) {
                        val merged = ((sx + syCandidate) / 2.0).coerceIn(1.0, 1000.0)
                        directScaleX = merged
                        directScaleY = merged
                    } else {
                        directScaleX = sx
                        directScaleY = syCandidate
                    }
                    onPreviewScale(directScaleX!!, directScaleY!!)
                },
                onCommit = {
                    if (directScaleX != null && directScaleY != null) {
                        onCommitScale(directScaleX!!, directScaleY!!)
                    }
                },
                onCancel = ::cancelDraft,
            )
            handles += InteractionProxySpec(
                id = "$proxyOwner-shear",
                label = "倾斜",
                targetInWindow = target(x, y),
                preferredOffsetPx = preferred + Offset(with(proxyDensity) { 78.dp.toPx() }, with(proxyDensity) { 66.dp.toPx() }),
                onDragDelta = { delta ->
                    directShearX = ((directShearX ?: displayShearX) + delta.x / 260f).coerceIn(-10.0, 10.0)
                    directShearY = ((directShearY ?: displayShearY) + delta.y / 260f).coerceIn(-10.0, 10.0)
                    onPreviewShear(directShearX!!, directShearY!!)
                },
                onCommit = {
                    if (directShearX != null && directShearY != null) {
                        onCommitShear(directShearX!!, directShearY!!)
                    }
                },
                onCancel = ::cancelDraft,
            )
            registry.publish(proxyOwner, handles)
        }
    }

    BoxWithConstraints(
        modifier
            .onGloballyPositioned {
                overlayOriginInWindow = it.positionInWindow()
                overlaySizePx = it.size
            }
            .pointerInput(event.id, document.playResX, document.playResY, scaleLocked) {
            var activeHandle = 0 // 1=position, 2=origin, 3=rotation, 4=scale, 5=fax, 6=fay
            var lastPreviewAt = 0L
            var scaleStartX = displayScaleX
            var scaleStartY = displayScaleY
            detectDragGestures(
                onDragStart = { start ->
                    fun distanceTo(px: Double, py: Double): Double {
                        val hx = (px / document.playResX.coerceAtLeast(1)) * size.width
                        val hy = (py / document.playResY.coerceAtLeast(1)) * size.height
                        val dx = start.x - hx.toFloat()
                        val dy = start.y - hy.toFloat()
                        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                    }
                    val positionDistance = distanceTo(x, y)
                    val originDistance = if (originX != null && originY != null) distanceTo(originX!!, originY!!) else Double.POSITIVE_INFINITY
                    val pivotX = originX ?: x
                    val pivotY = originY ?: y
                    val pivotPx = (pivotX / document.playResX.coerceAtLeast(1)) * size.width
                    val pivotPy = (pivotY / document.playResY.coerceAtLeast(1)) * size.height
                    val theta = Math.toRadians(displayRotation - 90.0)
                    val handleX = pivotPx + kotlin.math.cos(theta) * 56.dp.toPx()
                    val handleY = pivotPy + kotlin.math.sin(theta) * 56.dp.toPx()
                    val handleDx = start.x - handleX.toFloat()
                    val handleDy = start.y - handleY.toFloat()
                    val rotationDistance = kotlin.math.sqrt((handleDx * handleDx + handleDy * handleDy).toDouble())
                    val rotationRadians = Math.toRadians(displayRotation)
                    val sourceDx = x - (originX ?: x)
                    val sourceDy = y - (originY ?: y)
                    val scaleAnchorAssX = if (originX != null) originX!! + kotlin.math.cos(rotationRadians) * sourceDx - kotlin.math.sin(rotationRadians) * sourceDy else x
                    val scaleAnchorAssY = if (originY != null) originY!! + kotlin.math.sin(rotationRadians) * sourceDx + kotlin.math.cos(rotationRadians) * sourceDy else y
                    val scaleAnchorPx = (scaleAnchorAssX / document.playResX.coerceAtLeast(1)) * size.width
                    val scaleAnchorPy = (scaleAnchorAssY / document.playResY.coerceAtLeast(1)) * size.height
                    val localScaleX = 70.dp.toPx() * (displayScaleX / 100.0)
                    val localScaleY = -40.dp.toPx() * (displayScaleY / 100.0)
                    val scaleHandleX = scaleAnchorPx + kotlin.math.cos(rotationRadians) * localScaleX - kotlin.math.sin(rotationRadians) * localScaleY
                    val scaleHandleY = scaleAnchorPy + kotlin.math.sin(rotationRadians) * localScaleX + kotlin.math.cos(rotationRadians) * localScaleY
                    val scaleDx = start.x - scaleHandleX.toFloat()
                    val scaleDy = start.y - scaleHandleY.toFloat()
                    val scaleDistance = kotlin.math.sqrt((scaleDx * scaleDx + scaleDy * scaleDy).toDouble())
                    fun localToWorld(lx: Double, ly: Double): Pair<Double, Double> =
                        (scaleAnchorPx + kotlin.math.cos(rotationRadians) * lx - kotlin.math.sin(rotationRadians) * ly) to
                            (scaleAnchorPy + kotlin.math.sin(rotationRadians) * lx + kotlin.math.cos(rotationRadians) * ly)
                    val faxLocalX = localScaleX / 2.0 + displayShearX * localScaleY
                    val faxLocalY = localScaleY
                    val fayLocalX = localScaleX
                    val fayLocalY = localScaleY / 2.0 + displayShearY * localScaleX
                    val faxWorld = localToWorld(faxLocalX, faxLocalY)
                    val fayWorld = localToWorld(fayLocalX, fayLocalY)
                    val faxDx = start.x - faxWorld.first.toFloat()
                    val faxDy = start.y - faxWorld.second.toFloat()
                    val fayDx = start.x - fayWorld.first.toFloat()
                    val fayDy = start.y - fayWorld.second.toFloat()
                    val faxDistance = kotlin.math.sqrt((faxDx * faxDx + faxDy * faxDy).toDouble())
                    val fayDistance = kotlin.math.sqrt((fayDx * fayDx + fayDy * fayDy).toDouble())
                    val threshold = 36.dp.toPx().toDouble()
                    activeHandle = when {
                        scaleDistance <= threshold -> 4
                        faxDistance <= threshold -> 5
                        fayDistance <= threshold -> 6
                        rotationDistance <= threshold -> 3
                        originDistance <= threshold && originDistance <= positionDistance -> 2
                        positionDistance <= threshold -> 1
                        else -> 0
                    }
                    if (activeHandle == 4) {
                        scaleStartX = displayScaleX.coerceAtLeast(1.0)
                        scaleStartY = displayScaleY.coerceAtLeast(1.0)
                    }
                },
                onDrag = { change, _ ->
                    if (activeHandle != 0) {
                        change.consume()
                        val px = change.position.x.coerceIn(0f, size.width.toFloat())
                        val py = change.position.y.coerceIn(0f, size.height.toFloat())
                        var nx = px / size.width.coerceAtLeast(1) * document.playResX
                        var ny = py / size.height.coerceAtLeast(1) * document.playResY
                        val xTargets = listOf(
                            marginL.toDouble(),
                            document.playResX / 2.0,
                            (document.playResX - marginR).toDouble(),
                        )
                        val yTargets = listOf(
                            marginV.toDouble(),
                            document.playResY / 2.0,
                            (document.playResY - marginV).toDouble(),
                        )
                        val xThreshold = document.playResX * 0.015
                        val yThreshold = document.playResY * 0.015
                        xTargets.minByOrNull { kotlin.math.abs(nx - it) }?.let { target ->
                            if (kotlin.math.abs(nx - target) < xThreshold) nx = target.toFloat()
                        }
                        yTargets.minByOrNull { kotlin.math.abs(ny - it) }?.let { target ->
                            if (kotlin.math.abs(ny - target) < yThreshold) ny = target.toFloat()
                        }
                        if (activeHandle == 1) {
                            x = nx.toDouble()
                            y = ny.toDouble()
                        } else if (activeHandle == 2) {
                            originX = nx.toDouble()
                            originY = ny.toDouble()
                        } else if (activeHandle == 3) {
                            val pivotX = originX ?: x
                            val pivotY = originY ?: y
                            val pivotPx = (pivotX / document.playResX.coerceAtLeast(1)) * size.width
                            val pivotPy = (pivotY / document.playResY.coerceAtLeast(1)) * size.height
                            val rawAngle = Math.toDegrees(kotlin.math.atan2((change.position.y - pivotPy).toDouble(), (change.position.x - pivotPx).toDouble())) + 90.0
                            directRotation = ((rawAngle + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                        } else {
                            val rotationRadians = Math.toRadians(displayRotation)
                            val sourceDx = x - (originX ?: x)
                            val sourceDy = y - (originY ?: y)
                            val scaleAnchorAssX = if (originX != null) originX!! + kotlin.math.cos(rotationRadians) * sourceDx - kotlin.math.sin(rotationRadians) * sourceDy else x
                            val scaleAnchorAssY = if (originY != null) originY!! + kotlin.math.sin(rotationRadians) * sourceDx + kotlin.math.cos(rotationRadians) * sourceDy else y
                            val anchorPx = (scaleAnchorAssX / document.playResX.coerceAtLeast(1)) * size.width
                            val anchorPy = (scaleAnchorAssY / document.playResY.coerceAtLeast(1)) * size.height
                            val worldDx = change.position.x - anchorPx.toFloat()
                            val worldDy = change.position.y - anchorPy.toFloat()
                            val localX = kotlin.math.cos(rotationRadians) * worldDx + kotlin.math.sin(rotationRadians) * worldDy
                            val localY = -kotlin.math.sin(rotationRadians) * worldDx + kotlin.math.cos(rotationRadians) * worldDy
                            if (activeHandle == 4) {
                                val rawScaleX = (kotlin.math.abs(localX) / 70.dp.toPx() * 100.0).coerceIn(1.0, 1000.0)
                                val rawScaleY = (kotlin.math.abs(localY) / 40.dp.toPx() * 100.0).coerceIn(1.0, 1000.0)
                                if (scaleLocked) {
                                    val factorX = rawScaleX / scaleStartX.coerceAtLeast(1.0)
                                    val factorY = rawScaleY / scaleStartY.coerceAtLeast(1.0)
                                    val factor = ((factorX + factorY) / 2.0).coerceIn(0.01, 10.0)
                                    directScaleX = (scaleStartX * factor).coerceIn(1.0, 1000.0)
                                    directScaleY = (scaleStartY * factor).coerceIn(1.0, 1000.0)
                                } else {
                                    directScaleX = rawScaleX
                                    directScaleY = rawScaleY
                                }
                            } else if (activeHandle == 5) {
                                val width = 70.dp.toPx() * (displayScaleX / 100.0)
                                val height = -40.dp.toPx() * (displayScaleY / 100.0)
                                if (kotlin.math.abs(height) > 0.001) {
                                    directShearX = ((localX - width / 2.0) / height).coerceIn(-10.0, 10.0)
                                }
                            } else {
                                val width = 70.dp.toPx() * (displayScaleX / 100.0)
                                val height = -40.dp.toPx() * (displayScaleY / 100.0)
                                if (kotlin.math.abs(width) > 0.001) {
                                    directShearY = ((localY - height / 2.0) / width).coerceIn(-10.0, 10.0)
                                }
                            }
                        }
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastPreviewAt >= 80L) {
                            lastPreviewAt = now
                            when (activeHandle) {
                                1 -> onPreview(x, y)
                                2 -> onPreviewOrigin(originX!!, originY!!)
                                3 -> onPreviewRotation(directRotation!!)
                                4 -> onPreviewScale(directScaleX!!, directScaleY!!)
                                5 -> onPreviewShear(directShearX!!, displayShearY)
                                6 -> onPreviewShear(displayShearX, directShearY!!)
                            }
                        }
                    }
                },
                onDragEnd = {
                    when (activeHandle) {
                        1 -> onCommit(x, y)
                        2 -> onCommitOrigin(originX!!, originY!!)
                        3 -> directRotation?.let(onCommitRotation)
                        4 -> if (directScaleX != null && directScaleY != null) onCommitScale(directScaleX!!, directScaleY!!)
                        5 -> directShearX?.let { onCommitShear(it, displayShearY) }
                        6 -> directShearY?.let { onCommitShear(displayShearX, it) }
                    }
                    directRotation = null
                    directScaleX = null
                    directScaleY = null
                    directShearX = null
                    directShearY = null
                    activeHandle = 0
                },
                onDragCancel = {
                    onCancel()
                    directRotation = null
                    directScaleX = null
                    directScaleY = null
                    directShearX = null
                    directShearY = null
                    activeHandle = 0
                },
            )
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val px = (x / document.playResX.coerceAtLeast(1)) * size.width
            val py = (y / document.playResY.coerceAtLeast(1)) * size.height
            if (interactionRegistry != null) {
                drawCircle(guideColor, 4.dp.toPx(), Offset(px.toFloat(), py.toFloat()))
                return@Canvas
            }
            drawCircle(
                color = guideColor,
                radius = 8.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(px.toFloat(), py.toFloat()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
            drawLine(
                color = guideColor.copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(px.toFloat() - 12.dp.toPx(), py.toFloat()),
                end = androidx.compose.ui.geometry.Offset(px.toFloat() + 12.dp.toPx(), py.toFloat()),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = guideColor.copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(px.toFloat(), py.toFloat() - 12.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(px.toFloat(), py.toFloat() + 12.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            if (originX != null && originY != null) {
                val ox = (originX!! / document.playResX.coerceAtLeast(1)) * size.width
                val oy = (originY!! / document.playResY.coerceAtLeast(1)) * size.height
                val center = androidx.compose.ui.geometry.Offset(ox.toFloat(), oy.toFloat())
                drawLine(guideColor.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(px.toFloat(), py.toFloat()), center, 1.dp.toPx())
                drawCircle(guideColor, 10.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                drawLine(guideColor, center + androidx.compose.ui.geometry.Offset(-8.dp.toPx(), -8.dp.toPx()), center + androidx.compose.ui.geometry.Offset(8.dp.toPx(), 8.dp.toPx()), 2.dp.toPx())
                drawLine(guideColor, center + androidx.compose.ui.geometry.Offset(-8.dp.toPx(), 8.dp.toPx()), center + androidx.compose.ui.geometry.Offset(8.dp.toPx(), -8.dp.toPx()), 2.dp.toPx())
            }
            val pivotX = originX ?: x
            val pivotY = originY ?: y
            val pivotPx = (pivotX / document.playResX.coerceAtLeast(1)) * size.width
            val pivotPy = (pivotY / document.playResY.coerceAtLeast(1)) * size.height
            val pivot = androidx.compose.ui.geometry.Offset(pivotPx.toFloat(), pivotPy.toFloat())
            val theta = Math.toRadians(displayRotation - 90.0)
            val rotationHandle = pivot + androidx.compose.ui.geometry.Offset(
                (kotlin.math.cos(theta) * 56.dp.toPx()).toFloat(),
                (kotlin.math.sin(theta) * 56.dp.toPx()).toFloat(),
            )
            drawLine(guideColor.copy(alpha = 0.55f), pivot, rotationHandle, 1.5.dp.toPx())
            drawCircle(guideColor, 7.dp.toPx(), rotationHandle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            val scaleRotation = Math.toRadians(displayRotation)
            val sourceDx = x - (originX ?: x)
            val sourceDy = y - (originY ?: y)
            val scaleAnchorAssX = if (originX != null) originX!! + kotlin.math.cos(scaleRotation) * sourceDx - kotlin.math.sin(scaleRotation) * sourceDy else x
            val scaleAnchorAssY = if (originY != null) originY!! + kotlin.math.sin(scaleRotation) * sourceDx + kotlin.math.cos(scaleRotation) * sourceDy else y
            val scaleAnchor = androidx.compose.ui.geometry.Offset(
                ((scaleAnchorAssX / document.playResX.coerceAtLeast(1)) * size.width).toFloat(),
                ((scaleAnchorAssY / document.playResY.coerceAtLeast(1)) * size.height).toFloat(),
            )
            fun rotatedLocal(lx: Double, ly: Double): androidx.compose.ui.geometry.Offset =
                scaleAnchor + androidx.compose.ui.geometry.Offset(
                    (kotlin.math.cos(scaleRotation) * lx - kotlin.math.sin(scaleRotation) * ly).toFloat(),
                    (kotlin.math.sin(scaleRotation) * lx + kotlin.math.cos(scaleRotation) * ly).toFloat(),
                )
            val scaleXLength = 70.dp.toPx() * (displayScaleX / 100.0)
            val scaleYLength = -40.dp.toPx() * (displayScaleY / 100.0)
            fun shearedLocal(lx: Double, ly: Double): androidx.compose.ui.geometry.Offset =
                rotatedLocal(
                    lx + displayShearX * ly,
                    ly + displayShearY * lx,
                )
            val frameOrigin = shearedLocal(0.0, 0.0)
            val frameX = shearedLocal(scaleXLength, 0.0)
            val frameY = shearedLocal(0.0, scaleYLength)
            val frameCorner = shearedLocal(scaleXLength, scaleYLength)
            val faxHandle = shearedLocal(scaleXLength / 2.0, scaleYLength)
            val fayHandle = shearedLocal(scaleXLength, scaleYLength / 2.0)
            drawLine(guideColor.copy(alpha = 0.32f), frameOrigin, frameX, 1.dp.toPx())
            drawLine(guideColor.copy(alpha = 0.32f), frameOrigin, frameY, 1.dp.toPx())
            drawLine(guideColor.copy(alpha = 0.45f), frameX, frameCorner, 1.dp.toPx())
            drawLine(guideColor.copy(alpha = 0.45f), frameY, frameCorner, 1.dp.toPx())
            drawCircle(guideColor, 6.dp.toPx(), frameCorner)
            drawCircle(guideColor.copy(alpha = 0.9f), 5.dp.toPx(), faxHandle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            drawCircle(guideColor.copy(alpha = 0.9f), 5.dp.toPx(), fayHandle, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
        Text(
            "pos ${x.toInt()},${y.toInt()}" + (if (originX != null) " · org ${originX!!.toInt()},${originY!!.toInt()}" else "") + " · rot ${displayRotation.toInt()}° · scale ${displayScaleX.toInt()}×${displayScaleY.toInt()}% · shear ${"%.2f".format(java.util.Locale.US, displayShearX)},${"%.2f".format(java.util.Locale.US, displayShearY)} · 拖动操控杆",
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )

    }
}

@Composable
private fun RectClipOverlay(
    document: AssDocument,
    event: AssEvent,
    onPreview: (Double, Double, Double, Double, Boolean) -> Unit,
    onCommit: (Double, Double, Double, Double, Boolean) -> Unit,
    onCancel: () -> Unit,
    interactionRegistry: InteractionOverlayRegistry? = null,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(event.text) { AssGeometrySemantic.inspect(event.text) }
    val initial = geometry.clipRect ?: return
    var left by remember(event.id, event.text) { mutableStateOf(initial.left) }
    var top by remember(event.id, event.text) { mutableStateOf(initial.top) }
    var right by remember(event.id, event.text) { mutableStateOf(initial.right) }
    var bottom by remember(event.id, event.text) { mutableStateOf(initial.bottom) }
    val inverted = geometry.clipInverted
    val guideColor = MaterialTheme.colorScheme.secondary
    val clipProxyOwner = "clip-${event.id}"
    ClearInteractionOwnerOnDispose(interactionRegistry, clipProxyOwner)
    val clipProxyDensity = androidx.compose.ui.platform.LocalDensity.current
    var clipOverlayOrigin by remember(event.id) { mutableStateOf(Offset.Zero) }
    var clipOverlaySize by remember(event.id) { mutableStateOf(IntSize.Zero) }
    val publishedClipOverlayOrigin = clipOverlayOrigin
    val publishedClipOverlaySize = clipOverlaySize
    SideEffect {
        val registry = interactionRegistry
        if (registry != null && publishedClipOverlaySize.width > 0 && publishedClipOverlaySize.height > 0) {
            val w = publishedClipOverlaySize.width.toFloat()
            val h = publishedClipOverlaySize.height.toFloat()
            val playResX = document.playResX.coerceAtLeast(1)
            val playResY = document.playResY.coerceAtLeast(1)
            fun target(px: Double, py: Double): Offset =
                publishedClipOverlayOrigin + Offset(
                    (px / playResX * w).toFloat(),
                    (py / playResY * h).toFloat(),
                )
            fun docDelta(delta: Offset): Pair<Double, Double> =
                (delta.x / w * playResX).toDouble() to (delta.y / h * playResY).toDouble()
            val base = with(clipProxyDensity) { Offset(104.dp.toPx(), 34.dp.toPx()) }
            fun handle(
                index: Int,
                label: String,
                px: Double,
                py: Double,
                extra: Offset,
                update: (Double, Double) -> Unit,
            ) = InteractionProxySpec(
                id = "$clipProxyOwner-$index",
                label = label,
                targetInWindow = target(px, py),
                preferredOffsetPx = base + extra,
                onDragDelta = { delta ->
                    val (dx, dy) = docDelta(delta)
                    update(dx, dy)
                    onPreview(left, top, right, bottom, inverted)
                },
                onCommit = { onCommit(left, top, right, bottom, inverted) },
                onCancel = onCancel,
            )
            val d = with(clipProxyDensity) { 64.dp.toPx() }
            registry.publish(
                clipProxyOwner,
                listOf(
                    handle(0, "左上", left, top, Offset.Zero) { dx, dy ->
                        left = (left + dx).coerceIn(0.0, (right - 1.0).coerceAtLeast(0.0))
                        top = (top + dy).coerceIn(0.0, (bottom - 1.0).coerceAtLeast(0.0))
                    },
                    handle(1, "右上", right, top, Offset(0f, d)) { dx, dy ->
                        right = (right + dx).coerceIn((left + 1.0).coerceAtMost(playResX.toDouble()), playResX.toDouble())
                        top = (top + dy).coerceIn(0.0, (bottom - 1.0).coerceAtLeast(0.0))
                    },
                    handle(2, "左下", left, bottom, Offset(d, 0f)) { dx, dy ->
                        left = (left + dx).coerceIn(0.0, (right - 1.0).coerceAtLeast(0.0))
                        bottom = (bottom + dy).coerceIn((top + 1.0).coerceAtMost(playResY.toDouble()), playResY.toDouble())
                    },
                    handle(3, "右下", right, bottom, Offset(d, d)) { dx, dy ->
                        right = (right + dx).coerceIn((left + 1.0).coerceAtMost(playResX.toDouble()), playResX.toDouble())
                        bottom = (bottom + dy).coerceIn((top + 1.0).coerceAtMost(playResY.toDouble()), playResY.toDouble())
                    },
                ),
            )
        }
    }

    BoxWithConstraints(
        modifier.onGloballyPositioned {
            clipOverlayOrigin = it.positionInWindow()
            clipOverlaySize = it.size
        },
    ) {
        val parentWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val parentHeightPx = constraints.maxHeight.coerceAtLeast(1)
        val playResX = document.playResX.coerceAtLeast(1)
        val playResY = document.playResY.coerceAtLeast(1)

        Canvas(Modifier.fillMaxSize()) {
            val l = (left / playResX) * size.width
            val t = (top / playResY) * size.height
            val r = (right / playResX) * size.width
            val b = (bottom / playResY) * size.height
            val topLeft = androidx.compose.ui.geometry.Offset(l.toFloat(), t.toFloat())
            val rectSize = androidx.compose.ui.geometry.Size(
                (r - l).coerceAtLeast(0.0).toFloat(),
                (b - t).coerceAtLeast(0.0).toFloat(),
            )
            drawRect(
                color = guideColor.copy(alpha = if (inverted) 0.16f else 0.08f),
                topLeft = topLeft,
                size = rectSize,
            )
            drawRect(
                color = guideColor,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = 2.dp.toPx()),
            )
            listOf(
                androidx.compose.ui.geometry.Offset(l.toFloat(), t.toFloat()),
                androidx.compose.ui.geometry.Offset(r.toFloat(), t.toFloat()),
                androidx.compose.ui.geometry.Offset(l.toFloat(), b.toFloat()),
                androidx.compose.ui.geometry.Offset(r.toFloat(), b.toFloat()),
            ).forEach { point ->
                drawCircle(guideColor, 6.dp.toPx(), point)
            }
        }

        val corners = listOf(
            0 to (left to top),
            1 to (right to top),
            2 to (left to bottom),
            3 to (right to bottom),
        )
        corners.forEach { (corner, point) ->
            val xDp = maxWidth * (point.first / playResX).toFloat()
            val yDp = maxHeight * (point.second / playResY).toFloat()
            Box(
                Modifier
                    .offset(x = xDp - 18.dp, y = yDp - 18.dp)
                    .width(36.dp)
                    .height(36.dp)
                    .pointerInput(event.id, event.text, corner) {
                        var lastPreviewAt = 0L
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x / parentWidthPx * playResX
                                val dy = dragAmount.y / parentHeightPx * playResY
                                when (corner) {
                                    0 -> {
                                        left = (left + dx).coerceIn(0.0, (right - 1.0).coerceAtLeast(0.0))
                                        top = (top + dy).coerceIn(0.0, (bottom - 1.0).coerceAtLeast(0.0))
                                    }
                                    1 -> {
                                        right = (right + dx).coerceIn((left + 1.0).coerceAtMost(playResX.toDouble()), playResX.toDouble())
                                        top = (top + dy).coerceIn(0.0, (bottom - 1.0).coerceAtLeast(0.0))
                                    }
                                    2 -> {
                                        left = (left + dx).coerceIn(0.0, (right - 1.0).coerceAtLeast(0.0))
                                        bottom = (bottom + dy).coerceIn((top + 1.0).coerceAtMost(playResY.toDouble()), playResY.toDouble())
                                    }
                                    3 -> {
                                        right = (right + dx).coerceIn((left + 1.0).coerceAtMost(playResX.toDouble()), playResX.toDouble())
                                        bottom = (bottom + dy).coerceIn((top + 1.0).coerceAtMost(playResY.toDouble()), playResY.toDouble())
                                    }
                                }
                                val now = android.os.SystemClock.uptimeMillis()
                                if (now - lastPreviewAt >= 80L) {
                                    lastPreviewAt = now
                                    onPreview(left, top, right, bottom, inverted)
                                }
                            },
                            onDragEnd = { onCommit(left, top, right, bottom, inverted) },
                            onDragCancel = onCancel,
                        )
                    }
            )
        }

        Text(
            if (inverted) "iclip · 矩形内隐藏" else "clip · 矩形内显示",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackBar(
    playback: MpvPlaybackState,
    estimatedFrameNumber: Long?,
    estimatedVideoFps: Double?,
    document: AssDocument,
    focusedEventId: Long?,
    onFocusEvent: (Long) -> Unit,
    onSetEventTiming: (Long, Long, Long) -> Unit,
    onPlayPause: () -> Unit,
    onFrameBack: () -> Unit,
    onFrameForward: () -> Unit,
    onSeek: (Double) -> Unit,
    onOpenTimeline: () -> Unit,
) {
    val duration = (playback.durationSeconds ?: 0.0).coerceAtLeast(0.0)
    val position = (playback.positionSeconds ?: 0.0)
        .coerceIn(0.0, if (duration > 0.0) duration else Double.MAX_VALUE)
    var scrubPosition by remember { mutableStateOf<Double?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    val displayPosition = scrubPosition ?: position
    LaunchedEffect(actionHint) {
        if (actionHint != null) {
            delay(850)
            actionHint = null
        }
    }

    BoxWithConstraints {
    val showMetrics = maxWidth >= 560.dp
    val showClock = maxWidth >= 440.dp
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides WorkbenchDimens.MinTouchTarget) {
        Row(
            Modifier.fillMaxWidth()
                .height(WorkbenchDimens.TransportHeight)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = WorkbenchDimens.Micro),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TransportTooltipButton("后退 1 帧 · mpv frame-back-step", {
                onFrameBack()
                actionHint = "后退 1 帧"
            }) {
                Text("−1帧", style = MaterialTheme.typography.labelSmall)
            }
            TransportTooltipButton(
                if (playback.status == MpvPlaybackState.Status.Playing ||
                    playback.status == MpvPlaybackState.Status.Buffering) "暂停" else "播放",
                {
                    onPlayPause()
                    actionHint = if (playback.status == MpvPlaybackState.Status.Playing ||
                        playback.status == MpvPlaybackState.Status.Buffering) "暂停" else "播放"
                },
            ) {
                val playing = playback.status == MpvPlaybackState.Status.Playing ||
                    playback.status == MpvPlaybackState.Status.Buffering
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
            }
            TransportTooltipButton("前进 1 帧 · mpv frame-step", {
                onFrameForward()
                actionHint = "前进 1 帧"
            }) {
                Text("+1帧", style = MaterialTheme.typography.labelSmall)
            }
            if (showClock) Text(formatClock(displayPosition), style = MaterialTheme.typography.labelSmall)
            estimatedFrameNumber?.takeIf { showMetrics && it >= 0L }?.let { frameNumber ->
                Text(
                    "F$frameNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            estimatedVideoFps?.takeIf { showMetrics && it.isFinite() && it > 0.0 }?.let { fps ->
                Text(
                    "%.3f".format(java.util.Locale.US, fps) + "fps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actionHint?.takeIf { showMetrics }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = WorkbenchDimens.Micro),
                )
            }
            TimelineProgressStrip(
                durationSeconds = duration,
                positionSeconds = displayPosition,
                events = document.events,
                focusedEventId = focusedEventId,
                onFocusEvent = onFocusEvent,
                onSetEventTiming = onSetEventTiming,
                onScrub = { scrubPosition = it },
                onScrubFinished = {
                    val target = scrubPosition
                    if (target != null) onSeek(target)
                    scrubPosition = null
                },
                onOpenTimeline = onOpenTimeline,
                modifier = Modifier.weight(1f).height(WorkbenchDimens.TransportHeight),
            )
            if (showClock) Text(formatClock(duration), style = MaterialTheme.typography.labelSmall)
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportTooltipButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun TimelineProgressStrip(
    durationSeconds: Double,
    positionSeconds: Double,
    events: List<AssEvent>,
    focusedEventId: Long?,
    onFocusEvent: (Long) -> Unit,
    onSetEventTiming: (Long, Long, Long) -> Unit,
    onScrub: (Double) -> Unit,
    onScrubFinished: () -> Unit,
    onOpenTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf("scrub") }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    val focusedEvent = events.firstOrNull { it.id == focusedEventId }
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier
            .pointerInput(durationSeconds) {
                detectTapGestures(
                    onTap = { offset ->
                        val timelineHotspot = WorkbenchDimens.MinTouchTarget.toPx()
                        if (durationSeconds <= 0.0 || offset.x >= size.width - timelineHotspot) {
                            onOpenTimeline()
                        } else {
                            val usableWidth = (size.width - timelineHotspot).coerceAtLeast(1f)
                            val fraction = (offset.x / usableWidth).coerceIn(0f, 1f)
                            val targetMs = (durationSeconds * 1000.0 * fraction).toLong()
                            val hit = events
                                .filter { targetMs in it.start.millis..it.end.millis }
                                .minByOrNull { it.end.millis - it.start.millis }
                            if (hit != null) {
                                onFocusEvent(hit.id)
                                onScrub(hit.start.millis / 1000.0)
                            } else {
                                onScrub(durationSeconds * fraction)
                            }
                            onScrubFinished()
                        }
                    },
                    onDoubleTap = { onOpenTimeline() },
                )
            }
            .pointerInput(durationSeconds, focusedEventId, focusedEvent?.start?.millis, focusedEvent?.end?.millis) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        val usableWidth = (size.width - WorkbenchDimens.MinTouchTarget.toPx()).coerceAtLeast(1f)
                        val edgeThreshold = 14.dp.toPx()
                        val startX = focusedEvent?.let {
                            (it.start.millis / 1000.0 / durationSeconds.coerceAtLeast(0.001)).toFloat() * usableWidth
                        }
                        val endX = focusedEvent?.let {
                            (it.end.millis / 1000.0 / durationSeconds.coerceAtLeast(0.001)).toFloat() * usableWidth
                        }
                        dragMode = when {
                            startX != null && kotlin.math.abs(offset.x - startX) <= edgeThreshold -> "trim-start"
                            endX != null && kotlin.math.abs(offset.x - endX) <= edgeThreshold -> "trim-end"
                            else -> "scrub"
                        }
                        focusedEvent?.let {
                            trimStartMs = it.start.millis
                            trimEndMs = it.end.millis
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (durationSeconds > 0.0) {
                            change.consume()
                            val usableWidth = (size.width - WorkbenchDimens.MinTouchTarget.toPx()).coerceAtLeast(1f)
                            val fraction = (change.position.x / usableWidth).coerceIn(0f, 1f)
                            val candidateMs = (durationSeconds * 1000.0 * fraction).toLong()
                            when (dragMode) {
                                "trim-start" -> trimStartMs = candidateMs.coerceIn(
                                    0L,
                                    (trimEndMs - 10L).coerceAtLeast(0L),
                                )
                                "trim-end" -> trimEndMs = candidateMs
                                    .coerceAtLeast(trimStartMs + 10L)
                                    .coerceAtMost((durationSeconds * 1000.0).toLong())
                                else -> onScrub(durationSeconds * fraction)
                            }
                        }
                    },
                    onDragEnd = {
                        val current = focusedEvent
                        when {
                            current != null && dragMode == "trim-start" ->
                                onSetEventTiming(current.id, trimStartMs, current.end.millis)
                            current != null && dragMode == "trim-end" ->
                                onSetEventTiming(current.id, current.start.millis, trimEndMs)
                            dragMode == "scrub" && dragging -> onScrubFinished()
                        }
                        dragging = false
                        dragMode = "scrub"
                    },
                    onDragCancel = {
                        dragging = false
                        dragMode = "scrub"
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = (size.width - WorkbenchDimens.MinTouchTarget.toPx()).coerceAtLeast(1f)
            val h = size.height
            val centerY = h * 0.58f
            drawRoundRect(
                color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.20f),
                size = androidx.compose.ui.geometry.Size(w, h * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
            )
            drawLine(
                color = colorScheme.outlineVariant.copy(alpha = 0.75f),
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(w, centerY),
                strokeWidth = 1.dp.toPx(),
            )
            if (durationSeconds > 0.0) {
                events.forEach { event ->
                    val previewStartMs = if (event.id == focusedEventId && dragMode == "trim-start") trimStartMs else event.start.millis
                    val previewEndMs = if (event.id == focusedEventId && dragMode == "trim-end") trimEndMs else event.end.millis
                    val start = (previewStartMs / 1000.0 / durationSeconds).coerceIn(0.0, 1.0)
                    val end = (previewEndMs / 1000.0 / durationSeconds).coerceIn(start, 1.0)
                    val x1 = (start * w).toFloat()
                    val x2 = (end * w).toFloat().coerceAtLeast(x1 + 1.dp.toPx())
                    val focused = event.id == focusedEventId
                    val top = if (focused) h * 0.12f else h * 0.32f
                    val barHeight = if (focused) h * 0.72f else h * 0.40f
                    drawRoundRect(
                        color = if (focused) colorScheme.primary else colorScheme.secondary.copy(alpha = 0.46f),
                        topLeft = androidx.compose.ui.geometry.Offset(x1, top),
                        size = androidx.compose.ui.geometry.Size(
                            (x2 - x1).coerceAtLeast(1.dp.toPx()),
                            barHeight,
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (focused) 3.dp.toPx() else 2.dp.toPx()),
                    )
                    if (focused) {
                        val handleWidth = 2.dp.toPx()
                        drawRect(
                            color = colorScheme.onPrimary,
                            topLeft = androidx.compose.ui.geometry.Offset(x1, top),
                            size = androidx.compose.ui.geometry.Size(handleWidth, barHeight),
                        )
                        drawRect(
                            color = colorScheme.onPrimary,
                            topLeft = androidx.compose.ui.geometry.Offset((x2 - handleWidth).coerceAtLeast(x1), top),
                            size = androidx.compose.ui.geometry.Size(handleWidth, barHeight),
                        )
                    }
                }
                val px = (positionSeconds / durationSeconds).coerceIn(0.0, 1.0).toFloat() * w
                drawLine(
                    color = colorScheme.onSurface,
                    start = androidx.compose.ui.geometry.Offset(px, h * 0.04f),
                    end = androidx.compose.ui.geometry.Offset(px, h * 0.96f),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawCircle(
                    color = colorScheme.onSurface,
                    radius = 3.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(px, h * 0.05f),
                )
            }
        }
        if (focusedEvent != null && dragMode != "scrub") {
            Text(
                text = if (dragMode == "trim-start") {
                    "Start ${formatClock(trimStartMs / 1000.0)}"
                } else {
                    "End ${formatClock(trimEndMs / 1000.0)}"
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = WorkbenchDimens.Micro, top = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(WorkbenchDimens.MinTouchTarget)
                .height(WorkbenchDimens.MinTouchTarget)
                .clickable(onClick = onOpenTimeline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Timeline,
                contentDescription = "打开时间轴",
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                modifier = Modifier.width(22.dp),
            )
        }
    }
}

private fun formatClock(seconds: Double): String {
    val totalMs = (seconds * 1000).toLong().coerceAtLeast(0)
    val totalSeconds = totalMs / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}


private fun writeStartupProbe(configDir: File, stage: String, status: String, detail: String = "") {
    runCatching {
        configDir.mkdirs()
        val body = buildString {
            appendLine("stage=$stage")
            appendLine("status=$status")
            appendLine("provider=${BuildConfig.ASSWB_RENDERER_FONT_PROVIDER}")
            appendLine("renderer=${BuildConfig.ASSWB_RENDERER_VERSION}")
            if (detail.isNotBlank()) appendLine("detail=$detail")
        }
        val target = File(configDir, "renderer-startup-probe.txt")
        val tmp = File(configDir, "renderer-startup-probe.txt.tmp")
        tmp.writeText(body, Charsets.UTF_8)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }
}

private fun readRendererFontDiagnostics(file: File): List<String> {
    if (!file.isFile || file.length() <= 0L) return emptyList()
    return runCatching {
        val maxBytes = 512L * 1024L
        val bytes = java.io.RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            val start = (length - maxBytes).coerceAtLeast(0L)
            input.seek(start)
            ByteArray((length - start).toInt()).also(input::readFully)
        }
        val lines = String(bytes, Charsets.UTF_8).lineSequence().toList()
        val snapshot = RendererLogParser.parse(
            lines = lines,
            requestedProvider = BuildConfig.ASSWB_RENDERER_FONT_PROVIDER,
        )
        snapshot.summaryLines(limitSelections = 3)
    }.getOrDefault(emptyList())
}
