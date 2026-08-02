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

/** The card silhouette, hoisted so it is allocated once rather than per row. */
private val CardShape = RoundedCornerShape(16.dp)

/**
 * Card with the mock's paper look: soft surface, 16dp radius.
 *
 * Draws its rounded corners with a shaped background rather than `clip`:
 * `clip` promotes every card to its own graphics layer, and a History screen
 * holds dozens at once, which was a measurable share of the scroll jank.
 */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    /** Non-zero only while a card is being picked up and dragged. `shadow` does
     *  promote a layer, which is exactly why it is off by default — one lifted
     *  card at a time is affordable, a screenful of them is not. */
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

/** A round monogram avatar (initials), as used in the mock's list rows. */
@Composable
fun Monogram(text: String, size: Int = 40) {
    // Three string allocations, once per row per recomposition otherwise — and
    // there is one of these on every transaction row in the app.
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
 * [NumberFormat] is not thread-safe, so it is held per-thread rather than in a
 * plain singleton. Building a fresh one inside [formatCedis] meant allocating
 * (and parsing a locale pattern) once per amount per recomposition — several
 * hundred times a frame while scrolling History.
 */
private val cedisFormat = object : ThreadLocal<NumberFormat>() {
    override fun initialValue(): NumberFormat =
        NumberFormat.getNumberInstance(Locale.UK).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
}

/** Formats minor units (pesewas) as a cedi string, e.g. 134020 -> "₵1,340.20". */
fun formatCedis(minor: Long, withSymbol: Boolean = true): String {
    val s = cedisFormat.get()!!.format(minor / 100.0)
    return if (withSymbol) "₵$s" else s
}

/**
 * The app's single vocabulary for which way money moved.
 *
 * Every surface routes through here: the same transaction previously read as
 * "PAYMENT" while being reviewed, "Money out" on its detail screen, and "SPENT"
 * in the dashboard totals, which made Review and History look like they
 * disagreed about the transaction itself.
 */
fun directionLabel(direction: String): String =
    if (direction == "CREDIT") MONEY_IN else MONEY_OUT

const val MONEY_IN = "Money in"
const val MONEY_OUT = "Money out"

/**
 * Friendly name for a stored [com.ledger.domain.model.Institution] value.
 * The column holds the enum name ("MTN_MOMO"), which is not what a person calls
 * their wallet. Unknown values pass through so a renamed enum degrades to
 * something readable rather than crashing.
 */
fun institutionLabel(name: String): String =
    runCatching { com.ledger.domain.model.Institution.valueOf(name).displayName }
        .getOrDefault(name)
