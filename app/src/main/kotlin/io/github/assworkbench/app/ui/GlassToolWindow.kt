package io.github.assworkbench.app.ui

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer
import kotlin.math.roundToInt

@Composable
internal fun rememberSystemBackdropBlurEnabled(): Boolean {
    val context = LocalContext.current
    val windowManager = remember(context) {
        context.getSystemService(WindowManager::class.java)
    }
    var enabled by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                windowManager?.isCrossWindowBlurEnabled == true
        )
    }

    DisposableEffect(windowManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || windowManager == null) {
            onDispose { }
        } else {
            val listener = Consumer<Boolean> { enabled = it }
            windowManager.addCrossWindowBlurEnabledListener(listener)
            onDispose {
                windowManager.removeCrossWindowBlurEnabledListener(listener)
            }
        }
    }
    return enabled
}

@Composable
internal fun GlassToolWindow(
    id: String,
    title: String,
    subtitle: String?,
    visible: Boolean,
    active: Boolean,
    width: Dp,
    height: Dp,
    offset: IntOffset,
    renderPlan: GlassRenderPlan,
    containerColor: Color,
    onOffsetChange: (IntOffset) -> Unit,
    onActivate: () -> Unit,
    onCycleMaterial: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!visible) return

    var seeThrough by remember(id) { mutableStateOf(false) }
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val currentOnActivate by rememberUpdatedState(onActivate)
    val currentOnClose by rememberUpdatedState(onClose)
    val density = LocalDensity.current
    val cornerRadius = 22.dp
    val backgroundAlpha = if (seeThrough) 0.08f else renderPlan.effectiveAlpha
    val effectiveColor = containerColor.copy(alpha = backgroundAlpha)
    val activeBorderColor = MaterialTheme.colorScheme.primary
    val inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val widthPx = with(density) { width.roundToPx() }
        val heightPx = with(density) { height.roundToPx() }
        val cornerPx = with(density) { cornerRadius.toPx() }
        val blurPx = with(density) { renderPlan.effectiveBlurDp.dp.roundToPx() }

        SideEffect {
            dialogWindow?.let { window ->
                window.setGravity(Gravity.TOP or Gravity.START)
                window.setLayout(widthPx, heightPx)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

                val attrs = window.attributes
                attrs.x = offset.x
                attrs.y = offset.y
                attrs.dimAmount = 0f
                window.attributes = attrs

                val background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerPx
                    setColor(effectiveColor.toArgb())
                    setStroke(
                        with(density) { (if (active) 1.5.dp else 1.dp).roundToPx() },
                        (if (active) activeBorderColor else inactiveBorderColor)
                            .copy(alpha = if (active) 0.92f else 0.58f)
                            .toArgb(),
                    )
                }
                window.setBackgroundDrawable(background)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.setBackgroundBlurRadius(
                        if (renderPlan.trueBackdropBlurActive && !seeThrough) blurPx else 0
                    )
                }
            }
        }

        Surface(
            modifier = modifier
                .fillMaxSize()
                .testTag("glass-window-" + id.replace(':', '-')),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(
                if (active) 1.5.dp else 1.dp,
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
                },
            ),
            shadowElevation = if (active && !seeThrough) 10.dp else 3.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                alpha = if (seeThrough) 0.08f else 0.36f
                            )
                        )
                        .pointerInput(id, offset) {
                            detectDragGestures(
                                onDragStart = { currentOnActivate() },
                                onDrag = { change, delta ->
                                    change.consume()
                                    currentOnOffsetChange(
                                        IntOffset(
                                            x = offset.x + delta.x.roundToInt(),
                                            y = offset.y + delta.y.roundToInt(),
                                        )
                                    )
                                },
                            )
                        }
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.DragIndicator, contentDescription = null)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    IconButton(
                        onClick = onCycleMaterial,
                        modifier = Modifier.testTag("glass-material-" + id.replace(':', '-')),
                    ) {
                        Icon(Icons.Filled.Cyclone, contentDescription = "切换工具材质")
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .testTag("glass-see-through-" + id.replace(':', '-'))
                            .pointerInput(id) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitFirstDown(requireUnconsumed = false)
                                        seeThrough = true
                                        try {
                                            do {
                                                val event = awaitPointerEvent()
                                            } while (event.changes.any { it.pressed })
                                        } finally {
                                            seeThrough = false
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = "按住临时看穿工具",
                        )
                    }
                    IconButton(
                        onClick = currentOnClose,
                        modifier = Modifier.testTag("glass-close-" + id.replace(':', '-')),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭 $title")
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}
