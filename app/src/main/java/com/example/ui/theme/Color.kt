package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Warm Mushaf Parchment & Emerald — Light Palette
val ParchmentBackground = Color(0xFFFAF6EE)
val ParchmentSurface = Color(0xFFF3ECE0)
val ParchmentSurfaceElevated = Color(0xFFFFFCF7)
val ParchmentBorder = Color(0xFFE0D5C1)

val EmeraldPrimary = Color(0xFF145A46)
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldContainer = Color(0xFFD5EDE4)
val EmeraldOnContainer = Color(0xFF0A3327)

val AntiqueGoldSecondary = Color(0xFFB68D40)
val AntiqueGoldOnSecondary = Color(0xFFFFFFFF)
val AntiqueGoldContainer = Color(0xFFF5EAD2)
val AntiqueGoldOnContainer = Color(0xFF42300D)

val UthmaniInkLight = Color(0xFF1C1B18)
val MutedInkLight = Color(0xFF5F5A50)

// Nocturnal Slate & Luminous Emerald — Dark Palette
val NocturnalBackground = Color(0xFF101614)
val NocturnalSurface = Color(0xFF18211E)
val NocturnalSurfaceElevated = Color(0xFF202B27)
val NocturnalBorder = Color(0xFF2C3B36)

val EmeraldDarkPrimary = Color(0xFF4DB38E)
val EmeraldDarkOnPrimary = Color(0xFF072B1E)
val EmeraldDarkContainer = Color(0xFF144535)
val EmeraldDarkOnContainer = Color(0xFFC5F2DF)

val GoldDarkSecondary = Color(0xFFD9B263)
val GoldDarkOnSecondary = Color(0xFF332406)
val GoldDarkContainer = Color(0xFF4B3711)
val GoldDarkOnContainer = Color(0xFFF6E3B7)

val UthmaniInkDark = Color(0xFFEFEAE0)
val MutedInkDark = Color(0xFFA8A196)

@Immutable
data class TasmeeSemanticColors(
    val correctWord: Color,
    val correctWordContainer: Color,
    val errorWord: Color,
    val errorWordContainer: Color,
    val uncertainWord: Color,
    val uncertainWordContainer: Color,
    val unreadWord: Color,
    val hiddenPillBackground: Color,
    val goldAccent: Color,
    val subtleBorder: Color,
    val mushafPageGlow: Color
)

val LightTasmeeSemanticColors = TasmeeSemanticColors(
    correctWord = Color(0xFF1B7A56),
    correctWordContainer = Color(0xFFE1F4EC),
    errorWord = Color(0xFFB83B3B),
    errorWordContainer = Color(0xFFFBE7E7),
    uncertainWord = Color(0xFFB3740E),
    uncertainWordContainer = Color(0xFFFCF1D6),
    unreadWord = Color(0xFF8D867B),
    hiddenPillBackground = Color(0xFFE7DFCF),
    goldAccent = AntiqueGoldSecondary,
    subtleBorder = ParchmentBorder,
    mushafPageGlow = Color(0xFFFFFDF8)
)

val DarkTasmeeSemanticColors = TasmeeSemanticColors(
    correctWord = Color(0xFF5CD4A5),
    correctWordContainer = Color(0xFF15382B),
    errorWord = Color(0xFFF27979),
    errorWordContainer = Color(0xFF401B1B),
    uncertainWord = Color(0xFFF2BC57),
    uncertainWordContainer = Color(0xFF3D2E12),
    unreadWord = Color(0xFF808883),
    hiddenPillBackground = Color(0xFF25322D),
    goldAccent = GoldDarkSecondary,
    subtleBorder = NocturnalBorder,
    mushafPageGlow = Color(0xFF151D1A)
)

val LocalTasmeeColors = staticCompositionLocalOf { LightTasmeeSemanticColors }
