package com.example.aura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Aura Design Tokens: Luxury Deep Obsidian & Luminous Cyber Accents
val AuraBgDeep = Color(0xFF070A12)
val AuraBgCard = Color(0xFF101524)
val AuraBgCardElevated = Color(0xFF161C30)
val AuraBorder = Color(0xFF1E2842)
val AuraBorderGlow = Color(0xFF2E3E66)

val AuraCyanPrimary = Color(0xFF00E5FF)
val AuraCyanDim = Color(0xFF0097A7)
val AuraVioletSecondary = Color(0xFF9D4EDD)
val AuraVioletDim = Color(0xFF5A189A)

val AuraSafeGreen = Color(0xFF00E676)
val AuraWarningAmber = Color(0xFFFFB300)
val AuraCriticalRed = Color(0xFFFF5252)

val AuraTextPrimary = Color(0xFFF1F5F9)
val AuraTextSecondary = Color(0xFF94A3B8)
val AuraTextMuted = Color(0xFF64748B)

private val AuraColorScheme = darkColorScheme(
    primary = AuraCyanPrimary,
    onPrimary = Color(0xFF002026),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = AuraVioletSecondary,
    onSecondary = Color(0xFF28004D),
    secondaryContainer = AuraVioletDim,
    onSecondaryContainer = Color(0xFFE0AAFF),
    background = AuraBgDeep,
    onBackground = AuraTextPrimary,
    surface = AuraBgCard,
    onSurface = AuraTextPrimary,
    surfaceVariant = AuraBgCardElevated,
    onSurfaceVariant = AuraTextSecondary,
    outline = AuraBorder,
    error = AuraCriticalRed,
    onError = Color.White
)

@Composable
fun AuraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AuraColorScheme,
        content = content
    )
}
