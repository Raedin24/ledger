
package com.ledger.domain.validation

import com.ledger.domain.model.Money
import com.ledger.domain.parser.PartialFields

/**
 * Decides whether a message is a real transaction. This is the only thing that
 * keeps OTPs out of the app.
 *
 * A message is accepted only when it has all three of:
 *
 *   a direction (money in or out)  +  a currency amount  +  a balance
 *
 * The balance is what separates transactions from OTPs. Every real wallet
 * movement reports the new balance, and OTPs, verification codes and marketing
 * messages never do. That includes an OTP that quotes an amount ("approval
 * code for payment of GHS50.00 ... is 456123").
 *
 * The transaction id is optional. Some real transactions have none (interest
 * credits, some bundle purchases); they are accepted and deduplicated with a
 * key built from their other fields. There is no OTP blocklist. The rule rests
 * on requiring direction, amount and balance.
 *
 * A message that isn't fully valid is classified like this:
 *  - no currency amount                       -> NotTransaction (dropped)
 *  - amount and balance, no clear direction    -> Incomplete (review, e.g. a
 *    "request received, awaiting confirmation" message)
 *  - amount and reference, no balance          -> Incomplete (review: the format
 *    may have changed)
 *  - amount only                               -> NotTransaction (dropped: OTP)
 */
class TransactionValidator {

    fun validate(fields: PartialFields, hasCurrencyAmount: Boolean): ValidationResult {
        // Check 1: no currency amount means it can't be a transaction. This is
        // why a plain OTP ("Your OTP is 123456") fails: 123456 has no currency
        // before it.
        if (!hasCurrencyAmount || fields.amount == null) {
            return ValidationResult.NotTransaction("no currency-tagged amount present")
        }

        // Check 1b: more than any wallet can hold or move (see Money.CEILING).
        // Dropped rather than sent to review, because it can't be a real
        // transaction and would break the totals.
        if (!Money.withinCeiling(fields.amount) || !Money.withinCeiling(fields.balanceAfter)) {
            return ValidationResult.NotTransaction("amount beyond any wallet limit")
        }

        val hasDirection = fields.direction != null
        val hasBalance = fields.balanceAfter != null
        val hasReference = !fields.reference.isNullOrBlank()

        // Check 2: the strict match. The balance is what rules out OTPs.
        if (hasDirection && hasBalance) {
            return ValidationResult.Valid
        }

        // Looks like a transaction but something is unclear: send to review.
        if (hasBalance || hasReference) {
            val missing = buildList {
                if (!hasDirection) add("direction")
                if (!hasBalance) add("balance")
            }
            return ValidationResult.Incomplete("ambiguous transaction, missing: ${missing.joinToString(", ")}")
        }

        // An amount with no balance and no reference: an OTP or marketing text.
        return ValidationResult.NotTransaction("amount without balance, not transaction-shaped")
    }
}
