package io.github.assworkbench.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun UiVariantLabDialog(
    selected: WorkspacePresentationMode,
    onSelect: (WorkspacePresentationMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("UI 实验室") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "这里保存完整编辑器的不同呈现。新增设计不会覆盖旧设计；可随时回来对比。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UiVariantRegistry.entries.forEach { variant ->
                    UiVariantCard(
                        variant = variant,
                        selected = variant == selected,
                        onSelect = { onSelect(variant) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        modifier = Modifier.testTag("ui-variant-lab"),
    )
}

@Composable
private fun UiVariantCard(
    variant: WorkspacePresentationMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val icon = when (variant.status) {
        UiVariantStatus.STABLE -> Icons.Filled.Verified
        UiVariantStatus.EXPERIMENTAL -> Icons.Filled.Science
        UiVariantStatus.ARCHIVED -> Icons.Filled.History
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("ui-variant-${variant.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, null)
                Column(Modifier.weight(1f)) {
                    Text(variant.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        variant.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(variant.status.label) },
                )
            }
            if (selected) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CheckCircle, null)
                    Text("当前使用", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth().testTag("ui-variant-use-${variant.name}"),
                ) {
                    Text("使用此界面")
                }
            }
        }
    }
}
