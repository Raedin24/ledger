package com.ledger.domain.categorization

import com.ledger.domain.model.Transaction
import java.time.Duration

/**
 * Amount-recurrence heuristic: same counterparty + same amount + same direction
 * appearing on a roughly regular cadence looks like a subscription/recurring
 * charge. Flags it for a "looks like a recurring charge — confirm?" prompt
 * rather than relying on the user to notice.
 *
 * Pure function over history; no persistence.
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
