package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.assworkbench.app.EditorState
import io.github.assworkbench.app.EditorViewModel
import io.github.assworkbench.domain.AssEvent

/** Figma 85:322 placement; Raw editing until bilingual source data exists. */
@Composable
internal fun FixedV11TextDock(
    event: AssEvent?,
    state: EditorState,
    viewModel: EditorViewModel,
    draftOwnerId: String?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.testTag("fixed-v11-text-dock"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(event?.let { "Event Text · #${it.id} · Raw" } ?: "Event Text · 无可编辑对象",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("fixed-v11-text-target"))
            if (event == null) {
                Text("绑定目标不存在，请选择字幕或检查固定绑定。",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                EventRawTextEditor(event, state, viewModel, draftOwnerId, compact = true)
            }
        }
    }
}
