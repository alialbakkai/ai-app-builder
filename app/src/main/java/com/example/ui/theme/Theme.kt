package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = AntiqueGoldSecondary,
    onSecondary = AntiqueGoldOnSecondary,
    secondaryContainer = AntiqueGoldContainer,
    onSecondaryContainer = AntiqueGoldOnContainer,
    background = ParchmentBackground,
    onBackground = UthmaniInkLight,
    surface = ParchmentSurfaceElevated,
    onSurface = UthmaniInkLight,
    surfaceVariant = ParchmentSurface,
    onSurfaceVariant = MutedInkLight,
    surfaceContainerLowest = ParchmentSurfaceElevated,
    surfaceContainerLow = ParchmentBackground,
    surfaceContainer = ParchmentSurface,
    outline = ParchmentBorder,
    error = LightTasmeeSemanticColors.errorWord,
    errorContainer = LightTasmeeSemanticColors.errorWordContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = EmeraldDarkOnPrimary,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldDarkOnContainer,
    secondary = GoldDarkSecondary,
    onSecondary = GoldDarkOnSecondary,
    secondaryContainer = GoldDarkContainer,
    onSecondaryContainer = GoldDarkOnContainer,
    background = NocturnalBackground,
    onBackground = UthmaniInkDark,
    surface = NocturnalSurface,
    onSurface = UthmaniInkDark,
    surfaceVariant = NocturnalSurfaceElevated,
    onSurfaceVariant = MutedInkDark,
    surfaceContainerLowest = NocturnalBackground,
    surfaceContainerLow = NocturnalSurface,
    surfaceContainer = NocturnalSurfaceElevated,
    outline = NocturnalBorder,
    error = DarkTasmeeSemanticColors.errorWord,
    errorContainer = DarkTasmeeSemanticColors.errorWordContainer
)

private val TasmeeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun TasmeeTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (useDarkTheme) DarkTasmeeSemanticColors else LightTasmeeSemanticColors

    CompositionLocalProvider(LocalTasmeeColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TasmeeTypography,
            shapes = TasmeeShapes,
            content = content
        )
    }
}
