package com.example.rbccounter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// BIMBO RBCcounter — розовая палитра, акцент на читаемость и современный минимализм
private val PinkLight = Color(0xFFC2185B)
private val PinkLightSoft = Color(0xFFE91E63)
private val PinkContainerLight = Color(0xFFFCE4EC)
private val PinkContainerDark = Color(0xFFAD1457)
private val PinkDark = Color(0xFFF48FB1)
private val SurfaceTintLight = Color(0xFFFFF5F8)
private val SurfaceVariantLight = Color(0xFFFFECF1)
private val SurfaceTintDark = Color(0xFF1F1418)
private val SurfaceVariantDark = Color(0xFF2D1F24)

private val LightColors = lightColorScheme(
    primary = PinkLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PinkContainerLight,
    onPrimaryContainer = Color(0xFF4A0A24),
    secondary = Color(0xFF880E4F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD6E6),
    onSecondaryContainer = Color(0xFF3D0525),
    tertiary = PinkLightSoft,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDE8),
    onTertiaryContainer = Color(0xFF3D0A1C),
    background = SurfaceTintLight,
    onBackground = Color(0xFF1C1115),
    surface = SurfaceTintLight,
    onSurface = Color(0xFF1C1115),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF4A2D35),
    outline = Color(0xFF8B6B75),
    outlineVariant = Color(0xFFE7D6DB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = PinkDark,
    onPrimary = Color(0xFF5C0A2E),
    primaryContainer = PinkContainerDark,
    onPrimaryContainer = Color(0xFFFFD6E6),
    secondary = Color(0xFFF8B4CC),
    onSecondary = Color(0xFF5C1A38),
    secondaryContainer = Color(0xFF78324E),
    onSecondaryContainer = Color(0xFFFFD6E6),
    tertiary = Color(0xFFFFB1C4),
    onTertiary = Color(0xFF5C1228),
    tertiaryContainer = Color(0xFF7A2A42),
    onTertiaryContainer = Color(0xFFFFD9E1),
    background = SurfaceTintDark,
    onBackground = Color(0xFFF6DDE4),
    surface = SurfaceTintDark,
    onSurface = Color(0xFFF6DDE4),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFE7D6DB),
    outline = Color(0xFF9F8A91),
    outlineVariant = Color(0xFF4A2D35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
