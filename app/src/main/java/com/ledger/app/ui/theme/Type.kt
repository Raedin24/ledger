package com.ledger.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The mock pairs 'Newsreader' (serif display) with 'Public Sans' (UI/body).
 *
 * To keep the app fully offline (no downloadable-fonts network call), bundle the
 * TTFs and swap the families below:
 *
 *   val Newsreader = FontFamily(Font(R.font.newsreader_regular), Font(R.font.newsreader_medium, FontWeight.Medium))
 *   val PublicSans = FontFamily(Font(R.font.public_sans_regular), ...)
 *
 * Until the TTFs are dropped into res/font, we fall back to the system serif and
 * sans-serif so the app still builds and renders in the right spirit.
 */
val Newsreader = FontFamily.Serif
val PublicSans = FontFamily.SansSerif

val LedgerTypography = Typography(
    // Big serif numbers / greetings
    displayLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp),
    // Body / UI
    titleMedium = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = PublicSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
)
