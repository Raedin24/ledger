package com.ledger.domain.dedup

import com.ledger.domain.model.Transaction
import java.time.temporal.ChronoUnit

/**
 * Idempotency guard. Duplicate SMS broadcasts (the OS can redeliver
 * `SMS_RECEIVED`) or reprocessing must never create two rows for one event.
 *
 * Primary key: the provider reference number, which is globally unique per
 * transaction. When a message carries no clean reference (some loyalty/promo
 * credits, POS variants), fall back to a composite key of
 * institution + direction + amount + balance + occurred-time bucket.
 */
class DuplicateDetector(
    /** Time bucket for the composite fallback: two otherwise-identical amounts
     *  within this window are treated as the same event. */
    private val fallbackWindowMinutes: Long = 3,
) {

    sealed interface Key {
        data class Reference(val institution: String, val reference: String) : Key
        data class Composite(
            val institution: String,
            val direction: String,
            val amountMinor: Long,
            val balanceMinor: Long?,
            val timeBucket: Long,
        ) : Key
    }

    fun keyFor(txn: Transaction): Key {
        val ref = txn.reference
        return if (!ref.isNullOrBlank()) {
            Key.Reference(txn.institution.name, ref.trim().uppercase())
        } else {
            Key.Composite(
                institution = txn.institution.name,
                direction = txn.direction.name,
                amountMinor = txn.amount.minor,
                balanceMinor = txn.balanceAfter?.minor,
                timeBucket = txn.occurredAt.truncatedTo(ChronoUnit.SECONDS).epochSecond / (fallbackWindowMinutes * 60),
            )
        }
    }

    /**
     * @param existingKeys keys already persisted (from a cheap indexed lookup,
     *  NOT the full transaction table). Returns true if [txn] is a duplicate.
     */
    fun isDuplicate(txn: Transaction, existingKeys: Set<Key>): Boolean =
        keyFor(txn) in existingKeys

    /**
     * Filters a batch, keeping only first-seen transactions. Handles duplicates
     * within the batch itself as well as against [existingKeys].
     */
    fun deduplicate(txns: List<Transaction>, existingKeys: Set<Key> = emptySet()): List<Transaction> {
        val seen = existingKeys.toMutableSet()
        val out = ArrayList<Transaction>(txns.size)
        for (t in txns) {
            val k = keyFor(t)
            if (seen.add(k)) out.add(t)
        }
        return out
    }
}
