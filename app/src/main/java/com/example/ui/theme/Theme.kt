package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val KasirLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = MintSoft,
    onPrimaryContainer = EmeraldDeep,
    secondary = AmberGold,
    onSecondary = Color(0xFF1F1600),
    secondaryContainer = AmberSoft,
    onSecondaryContainer = AmberDark,
    tertiary = TealAccent,
    onTertiary = Color.White,
    tertiaryContainer = TealSoft,
    onTertiaryContainer = EmeraldDeep,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceContainerLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerHighLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    outline = OutlineLight,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerSoft,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val KasirDarkColorScheme = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color(0xFF002B20),
    primaryContainer = MintContainerDark,
    onPrimaryContainer = MintSoft,
    secondary = AmberGold,
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = AmberDark,
    onSecondaryContainer = AmberSoft,
    tertiary = TealAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = TealSoft,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceContainerDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = DangerSoft
)

val KasirShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KasirProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KasirDarkColorScheme else KasirLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KasirTypography,
        shapes = KasirShapes,
        content = content
    )
}
