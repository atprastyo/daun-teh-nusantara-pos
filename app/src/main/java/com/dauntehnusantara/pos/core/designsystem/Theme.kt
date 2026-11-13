package com.dauntehnusantara.pos.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF28633E), onPrimary = Color.White,
    primaryContainer = Color(0xFFC4ECCE), onPrimaryContainer = Color(0xFF092711),
    background = Color(0xFFFAFBF6), surface = Color(0xFFFAFBF6),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF91D5A2), onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF15522D), onPrimaryContainer = Color(0xFFC4ECCE),
    background = Color(0xFF111510), surface = Color(0xFF111510),
)

@Composable
fun DaunTehTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography(), content = content)
}
