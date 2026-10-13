package io.github.assworkbench.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        title = { Text("工作区") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

@OptIn(ExperimentalMaterial3Api::class)
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
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(variant.description) } },
        state = rememberTooltipState(),
    ) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null)
                Text(
                    variant.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (variant.status == UiVariantStatus.EXPERIMENTAL) {
                    Text(
                        "实验",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "当前使用")
                } else {
                    OutlinedButton(
                        onClick = onSelect,
                        modifier = Modifier.testTag("ui-variant-use-${variant.name}"),
                    ) {
                        Text("切换")
                    }
                }
            }
        }
    }
}
