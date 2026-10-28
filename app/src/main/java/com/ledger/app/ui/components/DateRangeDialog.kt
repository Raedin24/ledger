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
 * A date range picker, shared by the History window and the Breakdown period.
 *
 * - `weight(1f)` on the picker keeps the Apply and Cancel buttons on screen.
 *   [DatePickerDialog] has a maximum height, and the calendar is much taller,
 *   so without the weight the calendar takes all the space and the buttons are
 *   left out. With it, the buttons are measured first.
 * - The mode toggle is on. Scrolling to a distant month is slow (one row of
 *   weeks at a time), so the toggle lets the user type the two dates instead.
 * - The typed dates are day first. Material3 takes the date format from the
 *   phone's locale, which is often en-US (`MM/DD/YYYY`) and easy to misread in
 *   Ghana. The picker is given en-GH instead (`dd/MM/y`), which also starts
 *   the week on Monday.
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
    // A start with no end is a single day, so Apply only needs a start.
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
                // Coloured by hand, so the disabled button doesn't look tappable.
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
        // Provided inside the dialog, not around it. A Dialog has its own view,
        // which would replace an override made outside it.
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
                // Material3 writes both dates out in full, which doesn't fit on one line
                // next to the mode toggle. Use the same short form as the chip, which
                // leaves out the year when the range is within this year.
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

/**
 * "12 Mar - 4 Apr" for a chosen range, with years shown when the range isn't
 * within this year. Null when the range isn't fully set.
 */
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
