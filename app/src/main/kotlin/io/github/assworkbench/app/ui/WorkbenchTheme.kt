package io.github.assworkbench.app.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** The workspace has one dark presentation; system theme cannot change it. */
internal fun workbenchColors() = darkColorScheme(
    primary = Color(0xFF9F91FF), onPrimary = Color(0xFF211447),
    primaryContainer = Color(0xFF35305C), onPrimaryContainer = Color(0xFFE9E1FF),
    secondary = Color(0xFF8ED8DE), onSecondary = Color(0xFF00373B),
    secondaryContainer = Color(0xFF193F48), onSecondaryContainer = Color(0xFFBCF1F4),
    tertiary = Color(0xFFE5BE83), onTertiary = Color(0xFF432D0C),
    background = Color(0xFF080B12), onBackground = Color(0xFFE8EBF5),
    surface = Color(0xFF101521), onSurface = Color(0xFFE8EBF5),
    surfaceVariant = Color(0xFF20273A), onSurfaceVariant = Color(0xFFBBC2D8),
    surfaceContainer = Color(0xFF131A29), surfaceContainerHigh = Color(0xFF1A2133),
    surfaceContainerHighest = Color(0xFF252D43),
    outline = Color(0xFF818BA8), outlineVariant = Color(0xFF3B4563),
)
