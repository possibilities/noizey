package com.noizey.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NoizeyDarkColors = darkColorScheme(
    primary = NoizeyAccent,
    onPrimary = NoizeyOnAccent,
    primaryContainer = NoizeyAccentContainer,
    onPrimaryContainer = NoizeyOnAccentContainer,
    secondary = NoizeyTextSecondary,
    onSecondary = NoizeyBackground,
    secondaryContainer = NoizeySurfaceRaised,
    onSecondaryContainer = NoizeyText,
    tertiary = NoizeyTextSecondary,
    onTertiary = NoizeyBackground,
    background = NoizeyBackground,
    onBackground = NoizeyText,
    surface = NoizeySurface,
    onSurface = NoizeyText,
    surfaceVariant = NoizeySurfaceVariant,
    onSurfaceVariant = NoizeyTextSecondary,
    surfaceContainerLowest = NoizeyBackground,
    surfaceContainerLow = NoizeySurface,
    surfaceContainer = NoizeySurfaceVariant,
    surfaceContainerHigh = NoizeySurfaceRaised,
    surfaceContainerHighest = Color(0xFF232326),
    outline = NoizeyOutline,
    outlineVariant = NoizeyOutline.copy(alpha = 0.72f),
    error = NoizeyError,
    onError = NoizeyOnError,
    scrim = Color.Black,
)

private val NoizeyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NoizeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoizeyDarkColors,
        typography = NoizeyTypography,
        shapes = NoizeyShapes,
        content = content,
    )
}
