package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberColorScheme = darkColorScheme(
    primary = CyberGlowPurple,
    secondary = CyberGlowBlue,
    tertiary = CyberGlowCyan,
    background = CyberDark,
    surface = CyberNavy,
    onPrimary = CyberText,
    onSecondary = CyberText,
    onBackground = CyberText,
    onSurface = CyberText,
    surfaceVariant = CyberNavyLight,
    outline = CyberLine
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = Typography,
        content = content
    )
}
