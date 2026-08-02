package com.ledger.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledger.app.data.db.TransactionEntity
import com.ledger.app.ui.components.CategoryPill
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.Monogram
import com.ledger.app.ui.components.formatCedis
import com.ledger.app.ui.components.institutionLabel
import com.ledger.app.ui.theme.LedgerThemeTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault())
private val dateFmtWithYear = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())

/** Matches [com.ledger.app.ui.components.LedgerCard]'s silhouette, hoisted so the
 *  selection border doesn't allocate a shape per row. */
private val CardShape = RoundedCornerShape(16.dp)

/** Rows only carry the year when the transaction falls outside the current one —
 *  otherwise an imported 2023 alert would read as if it happened this month. */
private fun rowDate(occurredAt: Long): String {
    val zone = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(occurredAt)
    val sameYear = instant.atZone(zone).year == java.time.LocalDate.now(zone).year
    return if (sameYear) dateFmt.format(instant) else dateFmtWithYear.format(instant)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    txn: TransactionEntity,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LedgerThemeTokens.colors
    val isCredit = txn.direction == "CREDIT"
    val title = txn.counterparty ?: txn.category ?: "Unknown"
    // Both of these format a string and read the clock. Keyed to the row's own
    // data so a recycled row recomputes but a scroll frame doesn't: this runs
    // once per row instead of once per row per recomposition.
    val date = remember(txn.occurredAt) { rowDate(txn.occurredAt) }
    val amount = remember(txn.amountMinor, isCredit) {
        (if (isCredit) "+" else "−") + formatCedis(txn.amountMinor)
    }

    val interaction = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else Modifier
    val selection = if (selected) {
        Modifier.border(2.dp, colors.gold, CardShape)
    } else Modifier

    LedgerCard(modifier = interaction.then(selection), padding = PaddingValues(14.dp, 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Monogram(title)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (txn.selfTransfer) {
                        // Says why this one isn't in the spend total.
                        CategoryPill("Transfers")
                        Text(
                            "Between your accounts · $date",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (txn.category != null) {
                        CategoryPill(txn.category)
                        Text(date, style = MaterialTheme.typography.labelSmall, color = colors.inkMuted, maxLines = 1)
                    } else {
                        // Falls back to the wallet's friendly name, not the stored
                        // enum — an uncategorised row read "MTN_MOMO".
                        Text(
                            "${txn.referenceHint ?: institutionLabel(txn.institution)} · $date",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                amount,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCredit) colors.income else colors.spend,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = LedgerThemeTokens.colors.inkMuted,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 6.dp),
    )
}
