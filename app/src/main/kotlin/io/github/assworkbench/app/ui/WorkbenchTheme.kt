package io.github.assworkbench.app.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Stable workbench palette: cool accent, neutral readable panels, distinct borders. */
internal fun workbenchColors(dark: Boolean) = if (dark) darkColorScheme(
    primary = Color(0xFF77D9DD), onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF164D53), onPrimaryContainer = Color(0xFFB6F4F6),
    secondary = Color(0xFFB5C6F2), onSecondary = Color(0xFF1A2C52),
    secondaryContainer = Color(0xFF283B60), onSecondaryContainer = Color(0xFFDCE5FF),
    tertiary = Color(0xFFE5BE83), onTertiary = Color(0xFF432D0C),
    background = Color(0xFF0C111A), onBackground = Color(0xFFE5EBF5),
    surface = Color(0xFF151D2A), onSurface = Color(0xFFE5EBF5),
    surfaceVariant = Color(0xFF253247), onSurfaceVariant = Color(0xFFB7C5D8),
    outline = Color(0xFF8293AB), outlineVariant = Color(0xFF354359),
) else lightColorScheme(
    primary = Color(0xFF006B72), onPrimary = Color.White,
    primaryContainer = Color(0xFFC2F0F1), onPrimaryContainer = Color(0xFF00363B),
    secondary = Color(0xFF425D8C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E5FF), onSecondaryContainer = Color(0xFF142D52),
    tertiary = Color(0xFF805600), onTertiary = Color.White,
    background = Color(0xFFEDF2F8), onBackground = Color(0xFF142234),
    surface = Color(0xFFFAFCFF), onSurface = Color(0xFF142234),
    surfaceVariant = Color(0xFFE2EAF3), onSurfaceVariant = Color(0xFF42556D),
    outline = Color(0xFF677D94), outlineVariant = Color(0xFFBECDD9),
)
