package com.dauntehnusantara.pos.core.designsystem

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Leaf-green branding, with darker action colors for readable white labels.
private val LightColors = lightColorScheme(
    primary = Color(0xFF506B24), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2ECA6), onPrimaryContainer = Color(0xFF152000),
    inversePrimary = Color(0xFFB4D184),
    secondary = Color(0xFF586348), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8C8), onSecondaryContainer = Color(0xFF161F0C),
    tertiary = Color(0xFF3D6655), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBFECD5), onTertiaryContainer = Color(0xFF002115),
    background = Color.White, onBackground = Color(0xFF1A1D15),
    surface = Color.White, onSurface = Color(0xFF1A1D15),
    surfaceVariant = Color(0xFFE1E5D5), onSurfaceVariant = Color(0xFF44483C),
    surfaceTint = Color.Transparent,
    surfaceDim = Color(0xFFDADDD2), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF5F5F5),
    outline = Color(0xFF74796A), outlineVariant = Color(0xFFC4C8B8),
    inverseSurface = Color(0xFF2F3229), inverseOnSurface = Color(0xFFF0F2E9),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4D184), onPrimary = Color(0xFF233600),
    primaryContainer = Color(0xFF384F0F), onPrimaryContainer = Color(0xFFD2ECA6),
    inversePrimary = Color(0xFF506B24),
    secondary = Color(0xFFC0CCAD), onSecondary = Color(0xFF2B351F),
    secondaryContainer = Color(0xFF414C33), onSecondaryContainer = Color(0xFFDCE8C8),
    tertiary = Color(0xFFA3D0BA), onTertiary = Color(0xFF0B3727),
    tertiaryContainer = Color(0xFF254E3D), onTertiaryContainer = Color(0xFFBFECD5),
    background = Color(0xFF12150E), onBackground = Color(0xFFE1E3DA),
    surface = Color(0xFF12150E), onSurface = Color(0xFFE1E3DA),
    surfaceVariant = Color(0xFF44483C), onSurfaceVariant = Color(0xFFC4C8B8),
    surfaceTint = Color(0xFFB4D184),
    surfaceDim = Color(0xFF12150E), surfaceBright = Color(0xFF383B32),
    surfaceContainerLowest = Color(0xFF0D1009), surfaceContainerLow = Color(0xFF1A1D15),
    surfaceContainer = Color(0xFF1E2119), surfaceContainerHigh = Color(0xFF282B23),
    surfaceContainerHighest = Color(0xFF33362D),
    outline = Color(0xFF8E9382), outlineVariant = Color(0xFF44483C),
    inverseSurface = Color(0xFFE1E3DA), inverseOnSurface = Color(0xFF2F3229),
)

// Default to the requested white light appearance, independently of device night mode.
@Composable
fun DaunTehTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography(), content = content)
}
