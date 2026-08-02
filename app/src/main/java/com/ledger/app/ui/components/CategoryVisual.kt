package com.ledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.LedgerPalette

/**
 * A category's visual identity: a soft [tint] backing, a saturated [accent], and
 * the glyph that stands for it. Every default category gets a real icon; anything
 * the user creates falls back to a generic tag so the shape stays consistent.
 */
data class CategoryVisual(val tint: Color, val accent: Color, val icon: ImageVector)

private fun cv(tint: Long, accent: Long, icon: ImageVector) =
    CategoryVisual(Color(tint), Color(accent), icon)

// Tint/accent pairs lifted from the design mock's `cats` table and extended in
// the same key for the wider default set (see DefaultCategories): spending in the
// paper-and-earth family, income in greens, so which side of the ledger a row
// belongs to reads from its colour alone.
private val KNOWN: Map<String, CategoryVisual> = mapOf(
    // ---- money out ----
    "food & drink" to cv(0xFFF0E2D8, 0xFFA9552F, Icons.Default.Restaurant),
    "groceries" to cv(0xFFEDE6D4, 0xFF8C7A2E, Icons.Default.ShoppingCart),
    "transport" to cv(0xFFF2E9D3, 0xFFB07D1E, Icons.Default.DirectionsBus),
    "fuel" to cv(0xFFF0DFD5, 0xFF9C5A33, Icons.Default.LocalGasStation),
    "car" to cv(0xFFE3E6EA, 0xFF5C6B7A, Icons.Default.DirectionsCar),
    "bike" to cv(0xFFE0E8E2, 0xFF56796B, Icons.Default.DirectionsBike),
    "rent" to cv(0xFFE8E1D6, 0xFF8A6E45, Icons.Default.Home),
    "bills & utilities" to cv(0xFFDDE5EC, 0xFF4A6B8A, Icons.Default.ReceiptLong),
    "data & airtime" to cv(0xFFDDE8E4, 0xFF4F766E, Icons.Default.SignalCellularAlt),
    "airtime & data" to cv(0xFFDDE8E4, 0xFF4F766E, Icons.Default.SignalCellularAlt),
    "health" to cv(0xFFDFE8EA, 0xFF5F7F8A, Icons.Default.LocalHospital),
    "personal care" to cv(0xFFEEE1E6, 0xFF8E6376, Icons.Default.Spa),
    "family" to cv(0xFFEDDFE1, 0xFF9C5F6B, Icons.Default.Groups),
    "gifts & donations" to cv(0xFFEFE1DE, 0xFFA05B54, Icons.Default.CardGiftcard),
    "shopping" to cv(0xFFE6E1EE, 0xFF7A6B9C, Icons.Default.ShoppingBag),
    "fitness" to cv(0xFFE1E8DE, 0xFF5E7A52, Icons.Default.FitnessCenter),
    "entertainment" to cv(0xFFE7E0EC, 0xFF6F5F91, Icons.Default.Movie),
    "debt repayment" to cv(0xFFEEDEDA, 0xFF9A4F3F, Icons.Default.CreditCard),
    "savings" to cv(0xFFDFE9DC, 0xFF4F7A4D, Icons.Default.AccountBalanceWallet),
    "investment" to cv(0xFFDEE7E9, 0xFF3F6F78, Icons.Default.TrendingUp),
    "misc" to cv(0xFFE8E3DA, 0xFF8A8175, Icons.Default.MoreHoriz),
    // Not a category the user owns — the breakdowns' pooled provider charges.
    // Slate rather than an earth tone, so a cost of moving money doesn't read as
    // another thing money was spent *on*.
    "transaction fees" to cv(0xFFE1E4E8, 0xFF6B7280, Icons.Default.Percent),
    // ---- both ----
    "transfers" to cv(0xFFDCE3EC, 0xFF52708C, Icons.Default.SwapHoriz),
    // ---- money in ----
    "salary" to cv(0xFFDFE9DC, 0xFF4F7A4D, Icons.Default.Payments),
    "income" to cv(0xFFDFE9DC, 0xFF4F7A4D, Icons.Default.Payments),
    "bonus" to cv(0xFFE6EBD9, 0xFF60793C, Icons.Default.Star),
    "freelance" to cv(0xFFDEE8E1, 0xFF447060, Icons.Default.Work),
    "investment return" to cv(0xFFDCE8E4, 0xFF3E7466, Icons.Default.TrendingUp),
    "interest income" to cv(0xFFDDE6E2, 0xFF4A7263, Icons.Default.AccountBalance),
    "reimbursement" to cv(0xFFE2E7DD, 0xFF5A7350, Icons.AutoMirrored.Filled.Undo),
    "gift received" to cv(0xFFE4E9D8, 0xFF667A3E, Icons.Default.Redeem),
    "family support" to cv(0xFFE1E8DF, 0xFF4F7558, Icons.Default.Groups),
    "other" to cv(0xFFE8E3DA, 0xFF8A8175, Icons.Default.MoreHoriz),
)

// Gold question mark, matching the mock's uncategorised mark.
private val UNCATEGORISED = cv(0xFFF0E6CF, 0xFFA9772A, Icons.AutoMirrored.Filled.HelpOutline)

// Stable palette for user-created categories not in the known set.
private val FALLBACK = listOf(
    cv(0xFFF0E2D8, 0xFFA9552F, Icons.AutoMirrored.Filled.Label),
    cv(0xFFE6E1EE, 0xFF7A6B9C, Icons.AutoMirrored.Filled.Label),
    cv(0xFFF2E9D3, 0xFFB07D1E, Icons.AutoMirrored.Filled.Label),
    cv(0xFFDDE5EC, 0xFF4A6B8A, Icons.AutoMirrored.Filled.Label),
    cv(0xFFDDE8E4, 0xFF4F766E, Icons.AutoMirrored.Filled.Label),
    cv(0xFFEDDFE1, 0xFF9C5F6B, Icons.AutoMirrored.Filled.Label),
    cv(0xFFDFE8EA, 0xFF5F7F8A, Icons.AutoMirrored.Filled.Label),
    cv(0xFFDFE9DC, 0xFF4F7A4D, Icons.AutoMirrored.Filled.Label),
    cv(0xFFE8E3DA, 0xFF8A8175, Icons.AutoMirrored.Filled.Label),
)

/** Deterministic tint/accent/icon for a category name (case-insensitive). */
fun categoryVisual(name: String?): CategoryVisual {
    val key = name?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return UNCATEGORISED
    KNOWN[key]?.let { return it }
    return FALLBACK[(key.hashCode() and 0x7fffffff) % FALLBACK.size]
}

/** A rounded, tinted square holding the category's icon — the list/summary mark. */
@Composable
fun CategoryMark(name: String?, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val visual = categoryVisual(name)
    Box(
        modifier.size(size).background(visual.tint, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            visual.icon,
            contentDescription = null,
            tint = visual.accent,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

/** Hoisted so the pill in every transaction row shares one shape instance. */
private val PillShape = RoundedCornerShape(20.dp)

/** An inline tinted pill (icon + name) used in transaction rows. */
@Composable
fun CategoryPill(name: String?, modifier: Modifier = Modifier) {
    val visual = categoryVisual(name)
    Row(
        modifier
            .background(visual.tint, PillShape)
            .padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(visual.icon, contentDescription = null, tint = visual.accent, modifier = Modifier.size(12.dp))
        Text(
            name?.trim().takeUnless { it.isNullOrEmpty() } ?: "Uncategorised",
            style = MaterialTheme.typography.labelSmall,
            color = LedgerPalette.InkSoft,
            fontWeight = FontWeight.Medium,
        )
    }
}
