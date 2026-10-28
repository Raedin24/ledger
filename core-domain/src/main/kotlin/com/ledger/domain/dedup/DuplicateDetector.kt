package com.ledger.domain.dedup

import com.ledger.domain.model.Transaction
import java.time.temporal.ChronoUnit

/**
 * Makes sure one transaction is never saved twice, for example when Android
 * delivers the same SMS again.
 *
 * The key is the provider's reference number, which is unique per
 * transaction. When a message has no clean reference (some promo credits and
 * card payments), the key is built from provider, direction, amount, balance
 * and a time bucket instead.
 *
 * This class only computes the key. The unique index on
 * `transactions.dedupKey` decides whether it has been seen. That also handles
 * two inserts racing each other, which a check in Kotlin couldn't.
 */
class DuplicateDetector(
    /**
     * Time bucket for the fallback key: otherwise identical messages within this
     * window are treated as the same transaction.
     */
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

}
