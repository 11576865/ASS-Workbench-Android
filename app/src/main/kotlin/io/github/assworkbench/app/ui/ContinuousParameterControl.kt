package io.github.assworkbench.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.round

/**
 * 0.26 reusable controller for continuous visual parameters.
 *
 * Transient preview and committed document edits are deliberately separated:
 * - onPreview may run repeatedly while the slider moves.
 * - onGestureActive lets the owner suppress history commits during the gesture.
 * - the ordinary owner commit path can run after the gesture ends.
 *
 * Exact text input remains available beside the slider for professional use.
 */
@Composable
internal fun ContinuousParameterControl(
    label: String,
    valueText: String,
    onValueTextChange: (String) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Double,
    suffix: String = "",
    supportingText: String? = null,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
    onPreview: (Double) -> Unit = {},
    onGestureActive: (Boolean) -> Unit = {},
    readOnly: Boolean = false,
    testTagPrefix: String? = null,
    modifier: Modifier = Modifier,
) {
    val parsed = valueText.toDoubleOrNull()
    val sliderValue = (parsed ?: range.start.toDouble())
        .coerceIn(range.start.toDouble(), range.endInclusive.toDouble())
        .toFloat()
    var lastPreviewAt by remember { mutableLongStateOf(0L) }
    var latestGestureValue by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(readOnly) { if (readOnly) latestGestureValue = null }

    fun format(value: Double): String {
        if (step >= 1.0) return round(value).toLong().toString()
        val decimals = when {
            step >= 0.1 -> 1
            step >= 0.01 -> 2
            else -> 3
        }
        return ("%." + decimals + "f").format(java.util.Locale.US, value)
            .trimEnd('0')
            .trimEnd('.')
    }

    fun update(value: Double, preview: Boolean) {
        val clamped = value.coerceIn(range.start.toDouble(), range.endInclusive.toDouble())
        onValueTextChange(format(clamped))
        if (preview) onPreview(clamped)
    }

    fun discreteUpdate(value: Double) {
        if (readOnly) return
        onGestureActive(true)
        update(value, preview = true)
        onGestureActive(false)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Small),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label)
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        supportingText,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onReset != null && resetLabel != null) {
                TextButton(onClick = onReset, enabled = !readOnly) { Text(resetLabel) }
            }
            OutlinedTextField(
                value = valueText,
                onValueChange = { if (!readOnly) onValueTextChange(it) },
                readOnly = readOnly,
                singleLine = true,
                suffix = if (suffix.isBlank()) null else ({ Text(suffix) }),
                modifier = Modifier.width(112.dp).then(if (testTagPrefix != null) Modifier.testTag("$testTagPrefix-value") else Modifier),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WorkbenchDimens.Micro),
        ) {
            OutlinedButton(
                enabled = !readOnly,
                onClick = { discreteUpdate((parsed ?: sliderValue.toDouble()) - step) },
            ) { Text("−") }

            Slider(
                enabled = !readOnly,
                value = sliderValue,
                onValueChange = { raw ->
                    if (readOnly) return@Slider
                    onGestureActive(true)
                    val snapped = if (step > 0.0) {
                        round(raw.toDouble() / step) * step
                    } else raw.toDouble()
                    latestGestureValue = snapped
                    onValueTextChange(format(snapped))
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastPreviewAt >= 70L) {
                        lastPreviewAt = now
                        onPreview(snapped)
                    }
                },
                onValueChangeFinished = {
                    if (readOnly) return@Slider
                    val finalValue = latestGestureValue
                        ?: valueText.toDoubleOrNull()
                        ?: sliderValue.toDouble()
                    onPreview(finalValue)
                    latestGestureValue = null
                    onGestureActive(false)
                },
                valueRange = range,
                modifier = Modifier.weight(1f).then(if (testTagPrefix != null) Modifier.testTag("$testTagPrefix-slider") else Modifier),
            )

            OutlinedButton(
                enabled = !readOnly,
                onClick = { discreteUpdate((parsed ?: sliderValue.toDouble()) + step) },
            ) { Text("+") }
        }
    }
}
