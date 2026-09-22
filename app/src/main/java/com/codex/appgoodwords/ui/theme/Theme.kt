package com.codex.appgoodwords.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Surface,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = IndigoDeep,
    secondary = Mint,
    onSecondary = Surface,
    secondaryContainer = MintSoft,
    onSecondaryContainer = Ink,
    tertiary = Amber,
    onTertiary = Surface,
    tertiaryContainer = AmberSoft,
    onTertiaryContainer = Ink,
    error = Rose,
    onError = Surface,
    errorContainer = RoseSoft,
    onErrorContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceSunken,
    onSurfaceVariant = InkMuted,
    outline = InkFaint,
    outlineVariant = Hairline,
    surfaceTint = Color.Transparent,
    surfaceDim = SurfaceSunken,
    surfaceBright = Surface,
    surfaceContainerLowest = Surface,
    surfaceContainerLow = Surface,
    surfaceContainer = Canvas,
    surfaceContainerHigh = SurfaceSunken,
    surfaceContainerHighest = SurfaceSunken
)

/**
 * 모서리.
 *
 * 크게 둥글립니다. 카드가 손에 쥐는 물건처럼 보이고, 화면이 부드러워 보입니다.
 * 작은 것(태그·칩)은 덜 둥글려야 글자와 테두리 사이가 답답해 보이지 않습니다.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
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
