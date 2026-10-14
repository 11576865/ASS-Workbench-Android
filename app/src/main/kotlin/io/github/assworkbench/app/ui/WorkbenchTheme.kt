package io.github.assworkbench.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class WorkbenchAppearance(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    fun next(): WorkbenchAppearance = entries[(ordinal + 1) % entries.size]
}

private val DarkWorkbenchColors = darkColorScheme(
    // R3 reference: dark navy shell, restrained cyan selection and actionable
    // controls. The same color tokens are shared by FIXED and independent SPATIAL.
    primary = Color(0xFF34D0E7), onPrimary = Color(0xFF032132),
    primaryContainer = Color(0xFF123A4C), onPrimaryContainer = Color(0xFFD8F6FF),
    secondary = Color(0xFF77C9E6), onSecondary = Color(0xFF042131),
    secondaryContainer = Color(0xFF193D54), onSecondaryContainer = Color(0xFFD4F1FF),
    tertiary = Color(0xFFFFC05C), onTertiary = Color(0xFF392100),
    background = Color(0xFF0A1929), onBackground = Color(0xFFE8F0F6),
    surface = Color(0xFF0A1929), onSurface = Color(0xFFE8F0F6),
    surfaceVariant = Color(0xFF102A3C), onSurfaceVariant = Color(0xFF97B3C5),
    surfaceContainerLowest = Color(0xFF071726),
    surfaceContainerLow = Color(0xFF0B1F30),
    surfaceContainer = Color(0xFF102638),
    surfaceContainerHigh = Color(0xFF132E42),
    surfaceContainerHighest = Color(0xFF19394D),
    outline = Color(0xFF48677C), outlineVariant = Color(0xFF23465C),
    error = Color(0xFFFFAFA5), onError = Color(0xFF571A16),
)

private val LightWorkbenchColors = lightColorScheme(
    primary = Color(0xFF5547A4), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF), onPrimaryContainer = Color(0xFF19005D),
    secondary = Color(0xFF27646A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFACEBF1), onSecondaryContainer = Color(0xFF002023),
    tertiary = Color(0xFF745A24), onTertiary = Color.White,
    background = Color(0xFFF9F9FD), onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFFFBFF), onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE4E1EC), onSurfaceVariant = Color(0xFF46464F),
    surfaceContainer = Color(0xFFF1EFF7),
    surfaceContainerHigh = Color(0xFFEBE9F1),
    surfaceContainerHighest = Color(0xFFE5E2EA),
    outline = Color(0xFF777680), outlineVariant = Color(0xFFC8C5D0),
    error = Color(0xFFBA1A1A), onError = Color.White,
)

@Composable
internal fun workbenchColors(appearance: WorkbenchAppearance): ColorScheme {
    val dark = when (appearance) {
        WorkbenchAppearance.SYSTEM -> isSystemInDarkTheme()
        WorkbenchAppearance.LIGHT -> false
        WorkbenchAppearance.DARK -> true
    }
    return if (dark) DarkWorkbenchColors else LightWorkbenchColors
}
