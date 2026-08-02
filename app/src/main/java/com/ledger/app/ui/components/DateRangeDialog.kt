package com.ledger.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.LedgerPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendar range picker, shared by the History window and the Breakdown period.
 *
 * `weight(1f)` on the picker is what makes the dialog work at all. [DatePickerDialog]
 * caps its surface at 568dp and lays the buttons out after the content in a
 * `SpaceBetween` column; a [DateRangePicker] — headline, weekday row, then a
 * scrolling list of whole months — is far taller than that, so unweighted it ate
 * the entire column and Apply and Cancel were pushed past the bottom edge and
 * clipped away. They weren't merely off-screen, they were absent from the view
 * tree: a range could be chosen but never applied. Weighted, the buttons are
 * measured first and keep their space however tall the calendar wants to be.
 *
 * The mode toggle is on (M3's default; it had been switched off) because scrolling
 * is a poor way to reach a distant month — it is one row of weeks at a time, with
 * no way to jump a year. The toggle swaps the calendar for two date fields, so a
 * range spanning years is typed in a couple of seconds instead of scrolled to.
 *
 * Those fields are day-first. M3 takes the input mask from the short date pattern
 * of `LocalConfiguration`'s locale, and the phone's is en-US, which asks for
 * `MM/DD/YYYY` — an invitation to misread every date in a Ghanaian ledger. The
 * picker is handed en-GH instead, whose pattern is `dd/MM/y`. This also moves the
 * calendar's first column to Monday, which is Ghana's week start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    initialFrom: Long?,
    initialTo: Long?,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom,
        initialSelectedEndDateMillis = initialTo,
    )
    // A start with no end is a single-day window, so Apply only needs a start.
    val canApply = state.selectedStartDateMillis != null
    val pickerColors = DatePickerDefaults.colors(containerColor = LedgerPalette.Background)

    val configuration = LocalConfiguration.current
    val ghanaian = remember(configuration) {
        Configuration(configuration).apply {
            setLocale(Locale.Builder().setLanguage("en").setRegion("GH").build())
        }
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = pickerColors,
        confirmButton = {
            TextButton(
                enabled = canApply,
                onClick = {
                    state.selectedStartDateMillis?.let { start ->
                        onConfirm(start, state.selectedEndDateMillis ?: start)
                    }
                },
            ) {
                // Tinted by hand: a flat colour would paint the disabled button in
                // full gold and invite a tap that does nothing.
                Text(
                    "Apply",
                    color = if (canApply) LedgerPalette.Gold else LedgerPalette.InkMuted.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LedgerPalette.InkMuted)
            }
        },
    ) {
        // Provided inside the dialog, not around it: Dialog hosts its own
        // AndroidComposeView, which re-provides LocalConfiguration from its context
        // and would discard an override made in the parent composition.
        val column = this
        CompositionLocalProvider(LocalConfiguration provides ghanaian) {
        with(column) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    "Pick a date range",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 18.dp, top = 14.dp),
                )
            },
            headline = {
                // M3 spells both endpoints out in full — "May 28, 2026 – Jul 29,
                // 2026" — which no longer fits on one line once the mode toggle has
                // taken the right of a 360dp dialog, so the range wrapped and looked
                // squeezed. The chip's own short form says the same thing in half the
                // width and drops the year while the range sits inside this one.
                val start = state.selectedStartDateMillis
                val end = state.selectedEndDateMillis
                Text(
                    text = when {
                        start == null -> "Select dates"
                        end == null -> "${customRangeLabel(start, start)} – …"
                        else -> customRangeLabel(start, end).orEmpty()
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = LedgerPalette.Ink,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 18.dp, end = 12.dp, bottom = 12.dp),
                )
            },
            colors = pickerColors,
        )
        }
        }
    }
}

/** "12 Mar – 4 Apr" for a chosen range; years appear when the range leaves the
 *  current one. Null when the range is not fully set. */
fun customRangeLabel(from: Long?, to: Long?): String? {
    if (from == null || to == null) return null
    val zone = ZoneId.systemDefault()
    val thisYear = LocalDate.now(zone).year
    val start = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
    fun label(d: LocalDate) =
        if (d.year == thisYear) chipFmt.format(d) else chipFmtWithYear.format(d)
    return if (start == end) label(start) else "${label(start)} – ${label(end)}"
}

private val chipFmt = DateTimeFormatter.ofPattern("d MMM")
private val chipFmtWithYear = DateTimeFormatter.ofPattern("d MMM yyyy")
