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
    primary = Color(0xFFB5A8FF), onPrimary = Color(0xFF27165B),
    primaryContainer = Color(0xFF3A3466), onPrimaryContainer = Color(0xFFE9E1FF),
    secondary = Color(0xFF91D9DF), onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF1B424A), onSecondaryContainer = Color(0xFFBDF2F5),
    tertiary = Color(0xFFE8BE7B), onTertiary = Color(0xFF432D09),
    background = Color(0xFF0B0E14), onBackground = Color(0xFFE8EBF3),
    surface = Color(0xFF11151E), onSurface = Color(0xFFE8EBF3),
    surfaceVariant = Color(0xFF202633), onSurfaceVariant = Color(0xFFC1C7D4),
    surfaceContainer = Color(0xFF151A24),
    surfaceContainerHigh = Color(0xFF1B202C),
    surfaceContainerHighest = Color(0xFF232A38),
    outline = Color(0xFF8C94A7), outlineVariant = Color(0xFF3B4353),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
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
