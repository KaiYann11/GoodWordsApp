package com.codex.appgoodwords.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = CardWhite,
    primaryContainer = PanelBlue,
    onPrimaryContainer = MidnightBlue,
    secondary = CoralOrange,
    onSecondary = CardWhite,
    secondaryContainer = PanelCoral,
    onSecondaryContainer = MidnightBlue,
    tertiary = SunGlow,
    onTertiary = CardWhite,
    tertiaryContainer = PanelYellow,
    onTertiaryContainer = MidnightBlue,
    background = CloudWhite,
    onBackground = SoftInk,
    surface = CardWhite,
    onSurface = SoftInk,
    surfaceVariant = PanelBlue,
    onSurfaceVariant = SoftSlate,
    outline = Color(0xFF748198),
    outlineVariant = Color(0xFFDCE2ED),
    surfaceTint = OceanBlue,
    surfaceDim = Color(0xFFE1E6EF),
    surfaceBright = CardWhite,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF0F3F9),
    surfaceContainerHigh = Color(0xFFEAF0F7),
    surfaceContainerHighest = Color(0xFFE3EAF4)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppGoodWordsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
