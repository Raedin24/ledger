package com.ledger.app.data.prefs

import android.content.Context
import com.ledger.domain.model.FiscalMonth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The day the user's month starts on (see [FiscalMonth]).
 *
 * It is observable, because the dashboard, budgets and breakdown all have to
 * move to the new dates together when it changes.
 */
@Singleton
class PeriodPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _startDay = MutableStateFlow(FiscalMonth(prefs.getInt(KEY_START_DAY, 1)).startDay)
    val startDay: StateFlow<Int> = _startDay.asStateFlow()

    fun fiscal(): FiscalMonth = FiscalMonth(_startDay.value)

    private val _carryOver = MutableStateFlow(prefs.getBoolean(KEY_CARRY_OVER, false))

    /** Whether savings left over from earlier months are carried into this month's view. */
    val carryOver: StateFlow<Boolean> = _carryOver.asStateFlow()

    fun setCarryOver(on: Boolean) {
        prefs.edit().putBoolean(KEY_CARRY_OVER, on).apply()
        _carryOver.value = on
    }

    fun setStartDay(day: Int) {
        val clamped = FiscalMonth(day).startDay
        prefs.edit().putInt(KEY_START_DAY, clamped).apply()
        _startDay.value = clamped
    }

    private companion object {
        const val PREFS = "ledger_period"
        const val KEY_START_DAY = "month_start_day"
        const val KEY_CARRY_OVER = "carry_over_savings"
    }
}

/** Inclusive start and end, in epoch millis, of the period starting on [start]. */
fun FiscalMonth.bounds(start: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = endOf(start).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return from to to
}

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)
// Locale.ENGLISH, not the UK locale, which shortens September to "Sept"
// and breaks the three-letter pattern used everywhere else.
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * "September 2026" for a calendar month. Otherwise the dates, "28 Aug - 27 Sep",
 * since a period across two months has no single name. The year is added only
 * when the period ends in a different year from today.
 */
fun FiscalMonth.label(start: LocalDate): String {
    if (startDay == 1) return monthTitle.format(start)
    val end = endOf(start)
    val endFmt = if (end.year == LocalDate.now().year) dayMonth else dayMonthYear
    return "${dayMonth.format(start)} – ${endFmt.format(end)}"
}

/** "1st", "2nd", "28th", for showing the setting's value. */
fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
    }
    return "$day$suffix"
}
