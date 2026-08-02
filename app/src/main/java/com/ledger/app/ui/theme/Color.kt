package com.ledger.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Ledger palette: warm, editorial, paper-like. These values are the design,
 * not a copy of it — nothing else defines the colours, so this object is the
 * place to change them.
 */
object LedgerPalette {
    val Background = Color(0xFFF7F2E7)   // cream page
    val Surface = Color(0xFFFCF8EF)      // slightly lighter card
    val SurfaceAlt = Color(0xFFEFE9DC)   // muted panel
    val SurfaceSunken = Color(0xFFECE5D3)

    val Ink = Color(0xFF2A251D)          // near-black brown, primary text
    val InkSoft = Color(0xFF6B6255)      // secondary text
    val InkMuted = Color(0xFF9A8F7D)     // tertiary / captions
    val Hairline = Color(0x142A251D)     // 8% ink for dividers/borders

    val Income = Color(0xFF4F7A4D)       // green — money in / RECEIVED
    val IncomeDeep = Color(0xFF40663A)
    val Spend = Color(0xFF8F4A28)        // terracotta — money out / SPENT
    val Gold = Color(0xFFA9772A)         // accents, top-spending marks
    val GoldDeep = Color(0xFF9A6C1E)
    val Blue = Color(0xFF4A6B8A)         // info
    val ChipBg = Color(0xFFEADDB8)       // review "?" chip
    val Danger = Color(0xFFFF8A80)
}
