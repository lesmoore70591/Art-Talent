package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TypewriterCream,
    onPrimary = TypewriterBlack,
    secondary = TypewriterCream,
    onSecondary = TypewriterBlack,
    background = TypewriterBlack,
    onBackground = TypewriterCream,
    surface = TypewriterBlackAlt,
    onSurface = TypewriterCream,
    secondaryContainer = TypewriterBlackAlt,
    onSecondaryContainer = TypewriterCream,
    outline = TypewriterCream
)

private val LightColorScheme = lightColorScheme(
    primary = TypewriterBlack,
    onPrimary = TypewriterCream,
    secondary = TypewriterBlack,
    onSecondary = TypewriterCream,
    background = TypewriterCream,
    onBackground = TypewriterBlack,
    surface = TypewriterCreamAlt,
    onSurface = TypewriterBlack,
    secondaryContainer = TypewriterCreamAlt,
    onSecondaryContainer = TypewriterBlack,
    outline = TypewriterBlack
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
