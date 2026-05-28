package com.codex.appgoodwords.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onTertiary = MidnightBlue,
    tertiaryContainer = PanelYellow,
    onTertiaryContainer = MidnightBlue,
    background = CloudWhite,
    onBackground = SoftInk,
    surface = CardWhite,
    onSurface = SoftInk,
    surfaceVariant = PanelBlue,
    onSurfaceVariant = SoftSlate,
    outline = PanelBlue
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(26.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp)
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
