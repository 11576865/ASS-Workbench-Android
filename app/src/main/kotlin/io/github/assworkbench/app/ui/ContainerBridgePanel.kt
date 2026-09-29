package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.ContainerBridgeState
import io.github.assworkbench.app.EditorViewModel

@Composable
fun ContainerBridgePanel(
    state: ContainerBridgeState,
    viewModel: EditorViewModel,
    onSaveMkv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.uri == null) return
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().padding(vertical = WorkbenchDimens.Micro),
        verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
    ) {
            Text("MKV Container Bridge")
            Text(state.name)
            when {
                state.loading -> Text("正在扫描字幕轨与附件……")
                state.error != null -> Text("错误：" + state.error)
                else -> {
                    Text(
                        "ASS 轨 " + state.tracks.size +
                            " · 已注册字体 " + state.extractedFontCount +
                            " · 其他/未支持附件 " + state.skippedAttachmentCount
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
                    ) {
                        val selected = state.tracks.firstOrNull { it.number == state.selectedTrackNumber }
                        Text(
                            selected?.name ?: "选择 ASS 轨",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { menuOpen = true },
                            enabled = state.tracks.isNotEmpty(),
                        ) { Text("轨道") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            state.tracks.forEach { track ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(track.name)
                                            Text("Track #" + track.number + " · " + track.eventCount + " events")
                                        }
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.selectContainerTrack(track.number)
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
                    ) {
                        Text(
                            if (state.writeBackAvailable) "无重编码 MKV 写回可用" else "当前 ABI 暂无 MKV 写回工具",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onSaveMkv,
                            enabled = state.writeBackAvailable &&
                                !state.writeBackBusy &&
                                state.selectedTrackNumber != null,
                        ) {
                            Text(if (state.writeBackBusy) "处理中…" else "保存为新 MKV")
                        }
                    }
                    Text("写回会替换所选 ASS 轨并保留视频、音频及附件；源 MKV 不会被原地修改。")
                }
            }
        }
}

