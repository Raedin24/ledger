package com.ledger.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extra semantic colors that don't map onto Material's slots. */
data class LedgerColors(
    val income: Color = LedgerPalette.Income,
    val spend: Color = LedgerPalette.Spend,
    val gold: Color = LedgerPalette.Gold,
    val inkSoft: Color = LedgerPalette.InkSoft,
    val inkMuted: Color = LedgerPalette.InkMuted,
    val surfaceAlt: Color = LedgerPalette.SurfaceAlt,
    val surfaceSunken: Color = LedgerPalette.SurfaceSunken,
    val hairline: Color = LedgerPalette.Hairline,
    val chipBg: Color = LedgerPalette.ChipBg,
)

val LocalLedgerColors = staticCompositionLocalOf { LedgerColors() }

private val LedgerColorScheme = lightColorScheme(
    primary = LedgerPalette.Ink,
    onPrimary = LedgerPalette.Background,
    secondary = LedgerPalette.Gold,
    background = LedgerPalette.Background,
    onBackground = LedgerPalette.Ink,
    surface = LedgerPalette.Surface,
    onSurface = LedgerPalette.Ink,
    surfaceVariant = LedgerPalette.SurfaceAlt,
    onSurfaceVariant = LedgerPalette.InkSoft,
    error = LedgerPalette.Spend,
    outline = LedgerPalette.InkMuted,
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LedgerColorScheme,
        typography = LedgerTypography,
        content = content,
    )
}

/** Convenience accessor: `LedgerTheme.colors.income`. */
object LedgerThemeTokens {
    val colors: LedgerColors
        @Composable get() = LocalLedgerColors.current
}
