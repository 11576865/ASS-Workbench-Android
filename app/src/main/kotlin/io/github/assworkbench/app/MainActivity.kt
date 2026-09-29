package io.github.assworkbench.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 0.24 A-stage startup isolation activity.
 *
 * Intentionally does not reference EditorViewModel, EditorScreen, mpv/libass,
 * FontStore, Fontconfig, ActivityResult launchers, edge-to-edge, or system-bar
 * controller code. If this screen cannot appear on-device, the crash is below
 * the editor/runtime stack and must be investigated at Activity/theme/resource/
 * packaging level.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        StartupProbe.mark(this, "a_probe_activity_onCreate", "starting")
        super.onCreate(savedInstanceState)
        StartupProbe.mark(this, "a_probe_super_onCreate", "success")

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var tapped by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("ASS Workbench · A 级启动探针")
                    Text("Activity + Compose 根节点已成功显示。")
                    Text("此 APK 未进入 EditorViewModel / EditorScreen / mpv / libass / Fontconfig。")
                    Button(onClick = { tapped = true }) {
                        Text(if (tapped) "按钮响应正常" else "测试 Compose 交互")
                    }
                }
            }
        }

        StartupProbe.mark(this, "a_probe_setContent", "success")
    }
}
