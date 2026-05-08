package com.example.rokidglassesappcenter.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = darkColorScheme(
    primary = Green00,
    background = Black,
    surface = Black,
    onPrimary = Black,
    onBackground = Green00,
    onSurface = Green00,
)

@Composable
fun ClientTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
