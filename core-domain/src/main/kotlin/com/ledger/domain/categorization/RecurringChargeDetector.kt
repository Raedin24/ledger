package com.ledger.domain.categorization

import com.ledger.domain.model.Transaction
import java.time.Duration

/**
 * Spots likely recurring charges: the same counterparty, amount and direction
 * appearing at roughly regular intervals, like a subscription. Meant to drive
 * a "looks like a recurring charge, confirm?" prompt.
 *
 * Not used by any screen yet. It works over history and stores nothing.
 */
class RecurringChargeDetector(
    private val minOccurrences: Int = 3,
    private val cadenceToleranceRatio: Double = 0.35,
) {
    data class Candidate(
        val counterparty: String,
        val amountMinor: Long,
        val medianIntervalDays: Long,
        val occurrences: Int,
    )

    fun detect(history: List<Transaction>): List<Candidate> {
        return history
            .filter { it.counterparty != null }
            .groupBy { Triple(it.counterparty, it.amount.minor, it.direction) }
            .mapNotNull { (key, group) ->
                if (group.size < minOccurrences) return@mapNotNull null
                val times = group.map { it.occurredAt }.sorted()
                val intervals = times.zipWithNext { a, b -> Duration.between(a, b).toDays() }
                    .filter { it > 0 }
                if (intervals.size < minOccurrences - 1) return@mapNotNull null

                val median = intervals.sorted()[intervals.size / 2]
                if (median <= 0) return@mapNotNull null

                // Regular if every interval is within tolerance of the median.
                val regular = intervals.all { kotlin.math.abs(it - median) <= median * cadenceToleranceRatio }
                if (!regular) return@mapNotNull null

                Candidate(
                    counterparty = key.first!!,
                    amountMinor = key.second,
                    medianIntervalDays = median,
                    occurrences = group.size,
                )
            }
    }
}
