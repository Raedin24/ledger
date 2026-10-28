package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.db.CategorySlice
import com.ledger.app.ui.components.BreakdownSkeleton
import com.ledger.app.ui.components.CategoryBar
import com.ledger.app.ui.components.CategoryMark
import com.ledger.app.ui.components.DonutChart
import com.ledger.app.ui.components.DonutSlice
import com.ledger.app.ui.components.DateRangeDialog
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.MONEY_IN
import com.ledger.app.ui.components.MONEY_OUT
import com.ledger.app.ui.components.categoryVisual
import com.ledger.app.ui.components.customRangeLabel
import com.ledger.app.ui.components.formatCedis
import com.ledger.app.data.prefs.label
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.domain.model.FiscalMonth
import com.ledger.app.ui.theme.LedgerThemeTokens
import com.ledger.app.ui.vm.BreakdownViewModel
import com.ledger.app.ui.vm.BreakdownPeriod


/**
 * How many slices the ring draws before grouping the rest into one. Beyond
 * this, slices get thinner than the gaps between them.
 */
private const val RING_SLICES = 8

/**
 * Every category for one period: the "and the rest?" behind the dashboard's
 * top five.
 *
 * The user can step back through months, since "where did it go?" is often
 * asked about a month that has ended, and switch between money out and money
 * in. Tapping the period label opens a date range picker for longer or
 * custom periods. Stepping is turned off while a custom range is set.
 */
@Composable
fun BreakdownScreen(
    onBack: () -> Unit,
    vm: BreakdownViewModel = hiltViewModel(),
) {
    val slices by vm.slices.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val direction by vm.direction.collectAsStateWithLifecycle()
    val colors = LedgerThemeTokens.colors
    var pickingRange by remember { mutableStateOf(false) }

    val rows = slices.orEmpty()
    val loading = slices == null
    val total = remember(rows) { rows.sumOf { it.totalMinor } }
    val accent = if (direction == "CREDIT") colors.income else colors.spend

    // The ring's slices: the biggest categories on their own, the rest grouped
    // so a long list doesn't break the ring into slivers.
    val wedges = remember(rows) {
        val head = rows.take(RING_SLICES).map { DonutSlice(it.totalMinor, categoryVisual(it.label).accent) }
        val tail = rows.drop(RING_SLICES).sumOf { it.totalMinor }
        if (tail > 0) head + DonutSlice(tail, LedgerPalette.InkMuted) else head
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            ScreenHeader("Breakdown", onBack)
        }

        item {
            PeriodControl(
                period = period,
                canStepForward = vm.canStepForward(),
                onStep = vm::stepMonth,
                onPickRange = { pickingRange = true },
                onClearRange = vm::clearRange,
            )
        }

        item {
            Segmented(
                listOf("DEBIT" to MONEY_OUT, "CREDIT" to MONEY_IN),
                direction,
                vm::setDirection,
            )
        }

        if (loading) {
            item { BreakdownSkeleton(Modifier.padding(top = 4.dp)) }
            item { Spacer(Modifier.height(90.dp)) }
            return@LazyColumn
        }

        item {
            LedgerCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DonutChart(
                        slices = wedges,
                        modifier = Modifier.size(196.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                (if (direction == "CREDIT") MONEY_IN else MONEY_OUT).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMuted,
                            )
                            Text(
                                formatCedis(total),
                                style = MaterialTheme.typography.headlineMedium,
                                color = accent,
                            )
                            Text(
                                "${rows.size} categor${if (rows.size == 1) "y" else "ies"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMuted,
                            )
                        }
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                LedgerCard {
                    val window = if (period is BreakdownPeriod.Month) "this month" else "this period"
                    Text(
                        if (direction == "CREDIT") "Nothing came in $window."
                        else "Nothing went out $window.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.inkSoft,
                    )
                }
            }
        } else {
            // Largest first, so the bars get shorter down the list.
            items(rows, key = { it.label }, contentType = { "breakdown-row" }) { slice ->
                BreakdownRow(slice, total)
            }
            item {
                Text(
                    "Excludes transfers between your own accounts.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                )
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }

    if (pickingRange) {
        val current = period as? BreakdownPeriod.Range
        DateRangeDialog(
            initialFrom = current?.fromMillis,
            initialTo = current?.toMillis,
            onConfirm = { from, to -> vm.setRange(from, to); pickingRange = false },
            onDismiss = { pickingRange = false },
        )
    }
}

/**
 * The period control: "‹ July 2026 ›", stepping one month at a time and never
 * into the future, or the custom range that replaced it.
 *
 * Tapping the label opens the range picker in either case. With a custom
 * range, the arrows are replaced by a close button that goes back to months.
 */
@Composable
private fun PeriodControl(
    period: BreakdownPeriod,
    canStepForward: Boolean,
    onStep: (Long) -> Unit,
    onPickRange: () -> Unit,
    onClearRange: () -> Unit,
) {
    val colors = LedgerThemeTokens.colors
    val isMonth = period is BreakdownPeriod.Month
    Column {
        Row(
            Modifier.fillMaxWidth().background(LedgerPalette.Surface, StepperShape).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMonth) StepArrow("‹", enabled = true) { onStep(-1) }
            Text(
                text = when (period) {
                    is BreakdownPeriod.Month -> FiscalMonth(period.startDay).label(period.first)
                    is BreakdownPeriod.Range ->
                        customRangeLabel(period.fromMillis, period.toMillis).orEmpty()
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(StepArrowShape)
                    .clickable(onClick = onPickRange)
                    .padding(vertical = 6.dp),
            )
            if (isMonth) {
                StepArrow("›", enabled = canStepForward) { onStep(+1) }
            } else {
                StepArrow("✕", enabled = true, onClick = onClearRange)
            }
        }
        Text(
            text = when {
                !isMonth -> "Custom range · tap to change"
                !canStepForward -> "This month so far · tap the month for a range"
                else -> "Tap the month to pick a range"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
        )
    }
}

private val StepperShape = RoundedCornerShape(14.dp)
private val StepArrowShape = RoundedCornerShape(10.dp)

@Composable
private fun StepArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LedgerThemeTokens.colors
    Box(
        Modifier
            .size(38.dp)
            .clip(StepArrowShape)
            .background(colors.surfaceSunken)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) LedgerPalette.Ink else colors.inkMuted.copy(alpha = 0.4f),
        )
    }
}

/** One category's row: its name, amount, share, and number of transactions. */
@Composable
private fun BreakdownRow(slice: CategorySlice, total: Long) {
    val colors = LedgerThemeTokens.colors
    val label = slice.label.ifEmpty { "Uncategorised" }
    val share = if (total > 0) slice.totalMinor.toFloat() / total else 0f

    LedgerCard(padding = PaddingValues(14.dp, 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryMark(slice.label.ifEmpty { null }, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LedgerPalette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${(share * 100).toInt()}% · ${slice.txnCount} " +
                        if (slice.txnCount == 1) "transaction" else "transactions",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                )
            }
            Text(
                formatCedis(slice.totalMinor),
                style = MaterialTheme.typography.titleMedium,
                color = LedgerPalette.Ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(8.dp))
        CategoryBar(share, categoryVisual(slice.label.ifEmpty { null }).accent)
    }
}
