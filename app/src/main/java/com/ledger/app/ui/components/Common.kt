package com.ledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.theme.LedgerThemeTokens
import java.text.NumberFormat
import java.util.Locale

/** The card shape, created once and shared. */
private val CardShape = RoundedCornerShape(16.dp)

/**
 * A card with the app's paper look: soft surface, 16dp corners.
 *
 * The corners come from a shaped background instead of `clip`, because `clip`
 * gives every card its own graphics layer, and History shows dozens at once.
 */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    /**
     * Above zero only while a card is picked up and dragged. A shadow adds a
     * graphics layer, which is fine for one card but not a whole screen of them.
     */
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, CardShape) else Modifier)
            .background(LedgerPalette.Surface, CardShape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = LedgerThemeTokens.colors.gold,
                modifier = if (onAction != null)
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAction() }.padding(4.dp)
                else Modifier,
            )
        }
    }
}

/** A round avatar showing initials, used in list rows. */
@Composable
fun Monogram(text: String, size: Int = 40) {
    // Remembered, because every transaction row in the app has one of these.
    val initials = remember(text) { text.filter { it.isLetterOrDigit() }.take(2).uppercase() }
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(LedgerThemeTokens.colors.surfaceSunken, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelLarge,
            color = LedgerThemeTokens.colors.inkSoft,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * One [NumberFormat] per thread, because NumberFormat isn't thread-safe and
 * creating a new one for every amount is slow while scrolling.
 */
private val cedisFormat = object : ThreadLocal<NumberFormat>() {
    override fun initialValue(): NumberFormat =
        NumberFormat.getNumberInstance(Locale.UK).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
}

/**
 * Formats pesewas as cedis, for example 134020 as "₵1,340.20". A negative
 * amount puts the sign first ("−₵45.00"), not "₵-45.00".
 */
fun formatCedis(minor: Long, withSymbol: Boolean = true): String {
    val s = cedisFormat.get()!!.format(kotlin.math.abs(minor) / 100.0)
    val sign = if (minor < 0) "−" else ""
    return if (withSymbol) "$sign₵$s" else "$sign$s"
}

/**
 * The one set of words the app uses for which way money moved. Every screen
 * uses this, so a transaction is described the same way everywhere.
 */
fun directionLabel(direction: String): String =
    if (direction == "CREDIT") MONEY_IN else MONEY_OUT

const val MONEY_IN = "Money in"
const val MONEY_OUT = "Money out"

/**
 * A readable name for a stored [com.ledger.domain.model.Institution] value.
 * The column holds the enum name ("MTN_MOMO"), which isn't what people call
 * their wallet. Unknown values are shown as they are rather than crashing.
 */
fun institutionLabel(name: String): String =
    runCatching { com.ledger.domain.model.Institution.valueOf(name).displayName }
        .getOrDefault(name)
