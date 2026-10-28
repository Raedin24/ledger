package com.ledger.app.data.budget

import android.content.Context
import com.ledger.app.data.prefs.PeriodPrefs
import com.ledger.app.data.prefs.bounds
import com.ledger.app.data.repository.LedgerRepository
import com.ledger.app.notifications.BudgetNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when a budget is worth a notification, and remembers what it has
 * already said.
 *
 * It runs after each capture rather than on a timer, because spending only
 * changes when a transaction arrives. It checks every budget, not just the
 * captured transaction's category, so fees and re-categorised rows are
 * counted too.
 *
 * Only a rise in level is announced, once per level per month. That means at
 * most two alerts per category per month, so the alerts stay useful instead
 * of firing on every purchase.
 *
 * What has been announced is kept in SharedPreferences, keyed by category and
 * month. It holds no financial data (just a category name and a level), and
 * it has to be readable from the SMS receiver without opening the database.
 */
@Singleton
class BudgetAlerter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LedgerRepository,
    private val period: PeriodPrefs,
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Checks this month's budgets and posts an alert for each category that has
     * moved up a level since the last check.
     *
     * @return the statuses that were alerted on.
     */
    suspend fun checkAndNotify(today: LocalDate = LocalDate.now(ZoneId.systemDefault())): List<BudgetStatus> {
        val fiscal = period.fiscal()
        val start = fiscal.startOf(today)
        val (from, to) = fiscal.bounds(start)
        // Calendar months use "2026-09"; other months use their start date.
        val month = if (fiscal.startDay == 1) YearMonth.from(start).toString() else start.toString()

        val alerted = mutableListOf<BudgetStatus>()
        repository.budgetStatuses(from, to).forEach { status ->
            val level = status.level
            val announced = lastLevel(status.category, month)
            // Only announce a rise. Raising a limit or deleting a transaction can
            // lower the level, and announcing the same level again would be noise.
            if (level.ordinal <= announced.ordinal) return@forEach
            BudgetNotifier.notify(context, status, level)
            remember(status.category, month, level)
            alerted += status
        }
        // Earlier months' entries are no longer needed.
        forgetOtherMonths(month)
        return alerted
    }

    private fun lastLevel(category: String, month: String): BudgetLevel =
        prefs.getString(key(category, month), null)
            ?.let { name -> BudgetLevel.entries.firstOrNull { it.name == name } }
            ?: BudgetLevel.OK

    private fun remember(category: String, month: String, level: BudgetLevel) {
        prefs.edit().putString(key(category, month), level.name).apply()
    }

    private fun forgetOtherMonths(month: String) {
        val stale = prefs.all.keys.filterNot { it.endsWith("@$month") }
        if (stale.isEmpty()) return
        prefs.edit().apply { stale.forEach(::remove) }.apply()
    }

    /** Category first, so the month is a fixed suffix to match on. */
    private fun key(category: String, month: String) = "$category@$month"

    private companion object {
        const val PREFS = "ledger_budget_alerts"
    }
}
