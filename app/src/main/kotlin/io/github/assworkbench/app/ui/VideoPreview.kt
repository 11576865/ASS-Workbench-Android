package io.github.assworkbench.app.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.assworkbench.domain.AssCodec
import io.github.assworkbench.domain.AssDocument
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
    configDir: File,
    fontsDir: File,
    fontRevision: Long,
    initialPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    key(fontRevision) {
        AuthoritativeMpvPreview(
            videoUri = videoUri,
            document = document,
            seekRequestMs = seekRequestMs,
            seekRequestNonce = seekRequestNonce,
            onPosition = onPosition,
            configDir = configDir,
            fontsDir = fontsDir,
            initialPositionMs = initialPositionMs,
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
    configDir: File,
    fontsDir: File,
    initialPositionMs: Long,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val options = remember(configDir, fontsDir) {
        MpvOptions(
            configDir = configDir,
            cacheDir = File(context.cacheDir, "mpv-cache").apply { mkdirs() },
            extra = mapOf(
                "sub-auto" to "no",
                "sub-ass-override" to "no",
                "embeddedfonts" to "yes",
                "sub-fonts-dir" to fontsDir.absolutePath,
                "sub-font-provider" to "none",
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
        // Parsing/editing is independent from video. Rendering waits for reference media
        // only because there is no video surface to composite onto otherwise.
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
