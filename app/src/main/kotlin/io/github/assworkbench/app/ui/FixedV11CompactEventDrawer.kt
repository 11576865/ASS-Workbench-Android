package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Drawer presentation for the *existing* EventWorkspace in logical dual-pane
 * tablet windows. No new Event list, selection, document, or text editor is
 * created here: [eventList] is the same single owner as the old FIXED list pane.
 *
 * The drawer is a navigation surface, NOT another editable inspector. The
 * existing inspector and media/timeline remain composed underneath it.
 */
@Composable
internal fun FixedV11CompactEventDrawer(
    drawerState: DrawerState,
    total: Int,
    selected: Int,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    eventList: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(328.dp).fillMaxHeight()
                    .testTag("fixed-v11-event-drawer"),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "字幕事件 · $total 条",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("fixed-v11-drawer-close"),
                        ) { Text("关闭") }
                    }
                    // Finite constraints are essential: EventWorkspace's LazyColumn
                    // must not inherit infinite height from an outer scroll layout.
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        eventList(Modifier.fillMaxSize())
                    }
                    Text(
                        "已选 $selected 条 · 长按选择，点击定位",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().testTag("fixed-v11-compact-editor")) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.testTag("fixed-v11-drawer-open"),
                ) { Text("字幕列表 · $total") }
                Text(
                    "FIXED · 选中 $selected",
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}
