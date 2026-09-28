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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import io.github.yuroyami.libmpvkt.MpvCommands
import io.github.yuroyami.libmpvkt.MpvPlaybackState
import io.github.yuroyami.libmpvkt.MpvProperties
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
    var rendererArmed by remember { mutableStateOf(!BuildConfig.ASSWB_RENDERER_EXPERIMENTAL) }

    if (BuildConfig.ASSWB_RENDERER_EXPERIMENTAL && !rendererArmed) {
        Box(
            modifier.background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Fontconfig renderer 尚未启动", color = Color.White)
                Text(
                    "先确认应用本体可稳定打开；点击后才创建 mpv/libass/Fontconfig。",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                )
                Button(onClick = { rendererArmed = true }) {
                    Text("启动 Fontconfig renderer")
                }
            }
        }
        return
    }

    key(fontRevision) {
        AuthoritativeMpvPreview(
            videoUri = videoUri,
            document = document,
            seekRequestMs = seekRequestMs,
            seekRequestNonce = seekRequestNonce,
            onPosition = onPosition,
            onRendererDiagnostics = onRendererDiagnostics,
            configDir = configDir,
            fontsDir = fontsDir,
            initialPositionMs = initialPositionMs,
            showLayoutGuides = showLayoutGuides,
            modifier = modifier,
        )
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
    val previewFile = remember(mpv) { File(context.cacheDir, "ass-preview/current.ass").apply { parentFile?.mkdirs() } }

    LaunchedEffect(mpv) {
        mpv.addStreamProtocol("content", ContentResolverStreamProvider(context))
        protocolReady = true
    }

    LaunchedEffect(mpv, videoUri, protocolReady) {
        if (!protocolReady) return@LaunchedEffect
        mpv.command(MpvCommands.stop())
        subtitleAttached = false
        if (!videoUri.isNullOrBlank()) {
            mpv.command(MpvCommands.loadFile(videoUri))
            if (initialPositionMs > 0) {
                delay(180)
                mpv.command("seek", (initialPositionMs / 1000.0).toString(), "absolute+exact")
            }
        }
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
        onRendererDiagnostics(readRendererFontDiagnostics(rendererLogFile))
    }

    LaunchedEffect(seekRequestNonce, mpv) {
        val target = seekRequestMs ?: return@LaunchedEffect
        mpv.command("seek", (target / 1000.0).toString(), "absolute+exact")
    }

    LaunchedEffect(playback.positionSeconds) {
        onPosition(((playback.positionSeconds ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L))
    }

    Column(modifier.background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (videoUri.isNullOrBlank()) {
                Text("未选择参考视频", color = Color.White)
            } else {
                MpvSurface(mpv, Modifier.fillMaxSize())
                if (showLayoutGuides) {
                    LayoutGuideOverlay(document, Modifier.fillMaxSize())
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
private fun LayoutGuideOverlay(document: AssDocument, modifier: Modifier = Modifier) {
    val layout = TypesettingMath.bilingual6040(document.playResX, document.playResY)
    Canvas(modifier) {
        val sx = size.width / layout.playResX.toFloat().coerceAtLeast(1f)
        val sy = size.height / layout.playResY.toFloat().coerceAtLeast(1f)
        val left = layout.marginHorizontal * sx
        val right = size.width - layout.marginHorizontal * sx
        val top = layout.marginVertical * sy
        val bottom = size.height - layout.marginVertical * sy
        val sourceY = layout.sourceBoundaryY * sy
        val targetY = layout.targetBoundaryY * sy
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


private fun readRendererFontDiagnostics(file: File): List<String> {
    if (!file.isFile || file.length() <= 0L) return emptyList()
    return runCatching {
        val maxBytes = 256L * 1024L
        val bytes = java.io.RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            val start = (length - maxBytes).coerceAtLeast(0L)
            input.seek(start)
            ByteArray((length - start).toInt()).also(input::readFully)
        }
        String(bytes, Charsets.UTF_8)
            .lineSequence()
            .map { it.trim() }
            .filter { line ->
                val lower = line.lowercase()
                lower.contains("fontselect") ||
                    lower.contains("[sub/ass]") ||
                    lower.contains("libass") ||
                    lower.contains("font provider") ||
                    lower.contains("fontconfig")
            }
            .filter(String::isNotBlank)
            .toList()
            .takeLast(12)
    }.getOrDefault(emptyList())
}
