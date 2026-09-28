package io.github.assworkbench.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.BuildConfig
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.TypesettingMath
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
    seekRequestMs: Long?,
    seekRequestNonce: Long,
    onPosition: (Long) -> Unit,
    onRendererDiagnostics: (List<String>) -> Unit,
    configDir: File,
    fontsDir: File,
    fontRevision: Long,
    initialPositionMs: Long,
    showLayoutGuides: Boolean,
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

    key(fontRevision) {
        AuthoritativeMpvPreview(
            videoUri = videoUri,
            document = document,
            seekRequestMs = seekRequestMs,
            seekRequestNonce = seekRequestNonce,
            onPosition = { positionMs ->
                resumePositionMs = positionMs
                onPosition(positionMs)
            },
            onRendererDiagnostics = onRendererDiagnostics,
            configDir = configDir,
            fontsDir = fontsDir,
            initialPositionMs = resumePositionMs,
            showLayoutGuides = showLayoutGuides,
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
    seekRequestMs: Long?,
    seekRequestNonce: Long,
    onPosition: (Long) -> Unit,
    onRendererDiagnostics: (List<String>) -> Unit,
    configDir: File,
    fontsDir: File,
    initialPositionMs: Long,
    showLayoutGuides: Boolean,
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
                "sid" to "no",
                "secondary-sid" to "no",
                "embeddedfonts" to "yes",
                "sub-fonts-dir" to fontsDir.absolutePath,
                "sub-font-provider" to BuildConfig.ASSWB_RENDERER_FONT_PROVIDER,
                "log-file" to rendererLogFile.absolutePath,
                "msg-level" to "all=v",
            ),
        )
    }
    val mpv = rememberMpv(options)
    val playback by mpv.playback.collectAsState()
    var protocolReady by remember(mpv) { mutableStateOf(false) }
    var subtitleAttached by remember(mpv, videoUri) { mutableStateOf(false) }
    var videoDisplayWidth by remember(mpv, videoUri) { mutableLongStateOf(0L) }
    var videoDisplayHeight by remember(mpv, videoUri) { mutableLongStateOf(0L) }
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
        delay(120)
        videoDisplayWidth = mpv[MpvProperties.Dwidth].getOrNull()?.coerceAtLeast(0L) ?: 0L
        videoDisplayHeight = mpv[MpvProperties.Dheight].getOrNull()?.coerceAtLeast(0L) ?: 0L
    }

    LaunchedEffect(mpv, videoUri, document, protocolReady) {
        if (!protocolReady || videoUri.isNullOrBlank()) return@LaunchedEffect
        delay(120)
        val tmp = File(previewFile.parentFile, "current.ass.tmp")
        tmp.writeText(AssCodec.write(document), Charsets.UTF_8)
        if (previewFile.exists()) previewFile.delete()
        tmp.renameTo(previewFile)
        if (!subtitleAttached) {
            delay(120)
            mpv.command(MpvCommands.subAdd(previewFile.absolutePath))
            subtitleAttached = true
        } else {
            mpv.command(MpvCommands.subReload())
        }
        delay(300)
        val previewSource = mpv.getString("current-tracks/sub/external-filename")
        val activeSid = mpv.getString("sid") ?: "unknown"
        onRendererDiagnostics(
            readRendererFontDiagnostics(rendererLogFile) +
                "Preview subtitle：sid=$activeSid · " +
                (previewSource?.let { "external=$it" } ?: "external source 未报告")
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
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            if (videoUri.isNullOrBlank()) {
                Text("未选择参考视频", color = Color.White)
            } else {
                MpvSurface(mpv, Modifier.fillMaxSize())
                if (showLayoutGuides) {
                    LayoutGuideOverlay(
                        document = document,
                        videoDisplayWidth = videoDisplayWidth,
                        videoDisplayHeight = videoDisplayHeight,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        PlaybackBar(
            playback = playback,
            onPlayPause = {
                val shouldPause = playback.status == MpvPlaybackState.Status.Playing || playback.status == MpvPlaybackState.Status.Buffering
                mpv[MpvProperties.Pause] = shouldPause
            },
            onSeek = { seconds -> mpv.command("seek", seconds.toString(), "absolute+exact") },
        )
    }
}

@Composable
private fun PlaybackBar(
    playback: MpvPlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Double) -> Unit,
) {
    val duration = (playback.durationSeconds ?: 0.0).coerceAtLeast(0.0)
    val position = (playback.positionSeconds ?: 0.0).coerceIn(0.0, if (duration > 0.0) duration else Double.MAX_VALUE)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onPlayPause) {
            val playing = playback.status == MpvPlaybackState.Status.Playing || playback.status == MpvPlaybackState.Status.Buffering
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "暂停" else "播放")
        }
        Text(formatClock(position), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = if (duration > 0.0) position.toFloat() else 0f,
            onValueChange = { if (duration > 0.0) onSeek(it.toDouble()) },
            valueRange = 0f..duration.coerceAtLeast(1.0).toFloat(),
            enabled = duration > 0.0,
            modifier = Modifier.weight(1f),
        )
        Text(formatClock(duration), style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun LayoutGuideOverlay(
    document: AssDocument,
    videoDisplayWidth: Long,
    videoDisplayHeight: Long,
    modifier: Modifier = Modifier,
) {
    val layout = TypesettingMath.bilingual6040(document.playResX, document.playResY)
    Canvas(modifier) {
        val contentLeft: Float
        val contentTop: Float
        val contentWidth: Float
        val contentHeight: Float
        if (videoDisplayWidth > 0L && videoDisplayHeight > 0L && size.width > 0f && size.height > 0f) {
            val videoAspect = videoDisplayWidth.toFloat() / videoDisplayHeight.toFloat()
            val boxAspect = size.width / size.height
            if (boxAspect > videoAspect) {
                contentHeight = size.height
                contentWidth = contentHeight * videoAspect
                contentLeft = (size.width - contentWidth) / 2f
                contentTop = 0f
            } else {
                contentWidth = size.width
                contentHeight = contentWidth / videoAspect
                contentLeft = 0f
                contentTop = (size.height - contentHeight) / 2f
            }
        } else {
            contentLeft = 0f
            contentTop = 0f
            contentWidth = size.width
            contentHeight = size.height
        }

        val sx = contentWidth / layout.playResX.toFloat().coerceAtLeast(1f)
        val sy = contentHeight / layout.playResY.toFloat().coerceAtLeast(1f)
        val left = contentLeft + layout.marginHorizontal * sx
        val right = contentLeft + contentWidth - layout.marginHorizontal * sx
        val top = contentTop + layout.marginVertical * sy
        val bottom = contentTop + contentHeight - layout.marginVertical * sy
        val sourceY = contentTop + layout.sourceBoundaryY * sy
        val targetY = contentTop + layout.targetBoundaryY * sy
        drawRect(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        drawLine(
            color = Color.Cyan.copy(alpha = 0.75f),
            start = androidx.compose.ui.geometry.Offset(left, sourceY),
            end = androidx.compose.ui.geometry.Offset(right, sourceY),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawLine(
            color = Color.Magenta.copy(alpha = 0.75f),
            start = androidx.compose.ui.geometry.Offset(left, targetY),
            end = androidx.compose.ui.geometry.Offset(right, targetY),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
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
