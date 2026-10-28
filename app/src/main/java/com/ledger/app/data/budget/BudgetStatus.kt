package com.ledger.app.data.budget

/**
 * How close a category is to its monthly limit.
 *
 * [spentMinor] comes from the same query the dashboard uses, so a budget
 * counts exactly what "Top spending" counts: self-transfers left out, and fees
 * grouped in their own row.
 */
data class BudgetStatus(
    val category: String,
    val limitMinor: Long,
    val spentMinor: Long,
) {
    /**
     * Spending as a share of the limit. Not capped: the bar stops at full, but
     * the number can show "180%", which is the useful part of being over.
     */
    val fraction: Float
        get() = if (limitMinor <= 0) 0f else spentMinor.toFloat() / limitMinor

    val remainingMinor: Long get() = limitMinor - spentMinor

    val level: BudgetLevel get() = BudgetLevel.of(fraction)
}

/**
 * The three levels a budget can be at. Notifications are sent when the level
 * goes up, not as the percentage changes, so there are at most two per
 * category per month: one passing [NEAR] and one passing [OVER].
 */
enum class BudgetLevel {
    OK,
    /** Close enough that the rest of the month needs thinking about. */
    NEAR,
    OVER,
    ;

    companion object {
        /** Where "getting close" starts. At 80% there is still time in the month to act. */
        const val NEAR_AT = 0.8f

        fun of(fraction: Float): BudgetLevel = when {
            fraction >= 1f -> OVER
            fraction >= NEAR_AT -> NEAR
            else -> OK
        }
    }
}
