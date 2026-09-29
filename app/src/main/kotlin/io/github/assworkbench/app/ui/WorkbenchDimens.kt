package io.github.assworkbench.app.ui

import androidx.compose.ui.unit.dp

/**
 * 0.25 workbench layout rhythm.
 *
 * Android design guidance uses an 8dp primary grid with 4dp for smaller alignment
 * adjustments. Keep permanent surfaces on this rhythm unless a media geometry or
 * accessibility requirement gives a concrete reason to diverge.
 */
internal object WorkbenchDimens {
    val Micro = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp

    val MinTouchTarget = 48.dp
    val AppBarHeight = 48.dp
    val PaneHeaderHeight = 48.dp
    val ListRowMinHeight = 48.dp
    val TransportHeight = 48.dp

    val CompactWidth = 600.dp
    val ExpandedWidth = 840.dp
}
