package com.ledger.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * A month that starts on a day the user chooses.
 *
 * Someone paid on the 28th really lives from the 28th to the 27th. With
 * calendar months, their salary would land in one month and most of the
 * spending it pays for in the next.
 *
 * Any day from 1 to 31 can be chosen. In a month too short for that day, the
 * period starts on the month's last day instead (the 31st becomes the 28th in
 * February), so each period still starts in the month it is named after.
 * Because of this, periods vary in length. Step between them with [shift], not
 * [LocalDate.plusMonths], which would carry February's 28th into March.
 */
class FiscalMonth(startDay: Int) {

    val startDay: Int = startDay.coerceIn(1, MAX_START_DAY)

    /** The day this period starts on within [month]. */
    private fun startIn(month: YearMonth): LocalDate =
        month.atDay(minOf(startDay, month.lengthOfMonth()))

    /** First day of the period [date] falls in. */
    fun startOf(date: LocalDate): LocalDate {
        val month = YearMonth.from(date)
        val here = startIn(month)
        return if (date.isBefore(here)) startIn(month.minusMonths(1)) else here
    }

    /** The start of the period [months] after (or before) the one starting on [start]. */
    fun shift(start: LocalDate, months: Long): LocalDate = startIn(YearMonth.from(start).plusMonths(months))

    /** Last day of the period starting on [start], inclusive. */
    fun endOf(start: LocalDate): LocalDate = shift(start, 1).minusDays(1)

    /** Number of days in the period starting on [start]. */
    fun lengthOf(start: LocalDate): Int = (endOf(start).toEpochDay() - start.toEpochDay() + 1).toInt()

    companion object {
        const val MAX_START_DAY = 31
        val CALENDAR = FiscalMonth(1)
    }
}
