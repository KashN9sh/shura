package com.example.rbccounter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF5F5F5),
    outline = Color(0xFFE6E6E6),
    outlineVariant = Color(0xFFF0F0F0),
    secondary = Color(0xFF8A8A8A),
    onSecondary = Color(0xFF111111)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEAEAEA),
    onPrimary = Color(0xFF0E0E0E),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF1C1C1C),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1E1E1E),
    secondary = Color(0xFFA0A0A0),
    onSecondary = Color(0xFF0E0E0E)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun RbcTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
