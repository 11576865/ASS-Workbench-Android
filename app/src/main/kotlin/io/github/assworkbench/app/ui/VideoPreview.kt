package io.github.assworkbench.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.BuildConfig
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent
import io.github.assworkbench.domain.AssGeometrySemantic
import io.github.assworkbench.domain.AssPositionMode
import io.github.assworkbench.fonts.RendererLogParser
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
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoPreview(
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
    positionEditEventId: Long?,
    onPreviewEventPosition: (Double, Double) -> Unit,
    onSetEventPosition: (Double, Double) -> Unit,
    onCancelEventPositionPreview: () -> Unit,
    onFocusEvent: (Long) -> Unit,
    onSetEventTiming: (Long, Long, Long) -> Unit,
    onOpenVideo: () -> Unit,
    onOpenTimeline: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
            positionEditEventId = positionEditEventId,
            onPreviewEventPosition = onPreviewEventPosition,
            onSetEventPosition = onSetEventPosition,
            onCancelEventPositionPreview = onCancelEventPositionPreview,
            onFocusEvent = onFocusEvent,
            onSetEventTiming = onSetEventTiming,
            onOpenVideo = onOpenVideo,
            onOpenTimeline = onOpenTimeline,
            modifier = modifier,
        )
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
    positionEditEventId: Long?,
    onPreviewEventPosition: (Double, Double) -> Unit,
    onSetEventPosition: (Double, Double) -> Unit,
    onCancelEventPositionPreview: () -> Unit,
    onFocusEvent: (Long) -> Unit,
    onSetEventTiming: (Long, Long, Long) -> Unit,
    onOpenVideo: () -> Unit,
    onOpenTimeline: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val rendererLogFile = remember(configDir) { File(configDir, "renderer-font.log") }
    val options = remember(configDir, fontsDir, rendererLogFile) {
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
    val mpv = rememberMpv(options)
    val playback by mpv.playback.collectAsState()
    var protocolReady by remember(mpv) { mutableStateOf(false) }
    var subtitleAttached by remember(mpv, videoUri) { mutableStateOf(false) }
    var osdWidth by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdHeight by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdMarginTop by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdMarginBottom by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdMarginLeft by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var osdMarginRight by remember(mpv, videoUri) { mutableIntStateOf(0) }
    var lastReportedPositionMs by remember(mpv) { mutableLongStateOf(initialPositionMs.coerceAtLeast(0L)) }
    val previewFile = remember(mpv) { File(context.cacheDir, "ass-preview/current.ass").apply { parentFile?.mkdirs() } }
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
        }
    }

    LaunchedEffect(mpv, videoUri, renderDocument, protocolReady, fontRevision) {
        if (!protocolReady || videoUri.isNullOrBlank()) return@LaunchedEffect
        val transientRendering = renderDocument != document
        if (!transientRendering) delay(120)
        val tmp = File(previewFile.parentFile, "current.ass.tmp")
        tmp.writeText(AssCodec.write(renderDocument), Charsets.UTF_8)
        if (previewFile.exists()) previewFile.delete()
        tmp.renameTo(previewFile)
        if (!subtitleAttached) {
            delay(120)
            mpv.command(MpvCommands.subAdd(previewFile.absolutePath))
            subtitleAttached = true
        } else {
            mpv.command(MpvCommands.subReload())
        }
        if (transientRendering) return@LaunchedEffect
        delay(300)
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

    Column(modifier.background(Color.Black)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
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
                if (positionEvent != null) {
                    PositionDragOverlay(
                        document = document,
                        event = positionEvent,
                        onPreview = onPreviewEventPosition,
                        onCommit = onSetEventPosition,
                        onCancel = onCancelEventPositionPreview,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (!videoUri.isNullOrBlank()) {
            PlaybackBar(
                playback = playback,
                document = document,
                focusedEventId = focusedEventId,
                onFocusEvent = onFocusEvent,
                onSetEventTiming = onSetEventTiming,
                onPlayPause = {
                    val shouldPause = playback.status == MpvPlaybackState.Status.Playing ||
                        playback.status == MpvPlaybackState.Status.Buffering
                    mpv[MpvProperties.Pause] = shouldPause
                },
                onFrameBack = { mpv.command("frame-back-step") },
                onFrameForward = { mpv.command("frame-step") },
                onSeek = { seconds -> mpv.command("seek", seconds.toString(), "absolute+exact") },
                onOpenTimeline = onOpenTimeline,
            )
        }
    }
}

@Composable
private fun PositionDragOverlay(
    document: AssDocument,
    event: AssEvent,
    onPreview: (Double, Double) -> Unit,
    onCommit: (Double, Double) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(event.text) { AssGeometrySemantic.inspect(event.text) }
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
    val guideColor = MaterialTheme.colorScheme.tertiary

    if (geometry.positionMode == AssPositionMode.MOVE || geometry.positionMode == AssPositionMode.CONFLICT) {
        val move = geometry.move
        BoxWithConstraints(modifier) {
            if (move != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val sx = (move.start.x / document.playResX.coerceAtLeast(1)) * size.width
                    val sy = (move.start.y / document.playResY.coerceAtLeast(1)) * size.height
                    val ex = (move.end.x / document.playResX.coerceAtLeast(1)) * size.width
                    val ey = (move.end.y / document.playResY.coerceAtLeast(1)) * size.height
                    drawLine(
                        color = guideColor.copy(alpha = 0.7f),
                        start = androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()),
                        end = androidx.compose.ui.geometry.Offset(ex.toFloat(), ey.toFloat()),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawCircle(
                        color = guideColor,
                        radius = 7.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(
                        color = guideColor,
                        radius = 7.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(ex.toFloat(), ey.toFloat()),
                    )
                }
            }
            Text(
                if (geometry.positionMode == AssPositionMode.CONFLICT) {
                    "pos + move 冲突 · 已暂停直接位置编辑"
                } else {
                    "move 路径 · 当前只读，下一步开放端点编辑"
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        return
    }

    DisposableEffect(event.id) {
        onDispose { onCancel() }
    }

    BoxWithConstraints(
        modifier.pointerInput(event.id, document.playResX, document.playResY) {
            var armed = false
            var lastPreviewAt = 0L
            detectDragGestures(
                onDragStart = { start ->
                    val currentPx = (x / document.playResX.coerceAtLeast(1)) * size.width
                    val currentPy = (y / document.playResY.coerceAtLeast(1)) * size.height
                    val dx = start.x - currentPx.toFloat()
                    val dy = start.y - currentPy.toFloat()
                    armed = kotlin.math.sqrt(dx * dx + dy * dy) <= 36.dp.toPx()
                },
                onDrag = { change, _ ->
                    if (armed) {
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
                        x = nx.toDouble()
                        y = ny.toDouble()
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastPreviewAt >= 80L) {
                            lastPreviewAt = now
                            onPreview(x, y)
                        }
                    }
                },
                onDragEnd = {
                    if (armed) onCommit(x, y)
                    armed = false
                },
                onDragCancel = {
                    onCancel()
                    armed = false
                },
            )
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val px = (x / document.playResX.coerceAtLeast(1)) * size.width
            val py = (y / document.playResY.coerceAtLeast(1)) * size.height
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
        }
        Text(
            "pos ${x.toInt()},${y.toInt()} · 拖十字定位",
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackBar(
    playback: MpvPlaybackState,
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

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides WorkbenchDimens.MinTouchTarget) {
        Row(
            Modifier.fillMaxWidth()
                .height(WorkbenchDimens.TransportHeight)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = WorkbenchDimens.Micro),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TransportTooltipButton("后退 1 帧", {
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
            TransportTooltipButton("前进 1 帧", {
                onFrameForward()
                actionHint = "前进 1 帧"
            }) {
                Text("+1帧", style = MaterialTheme.typography.labelSmall)
            }
            Text(formatClock(displayPosition), style = MaterialTheme.typography.labelSmall)
            actionHint?.let {
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
            Text(formatClock(duration), style = MaterialTheme.typography.labelSmall)
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
