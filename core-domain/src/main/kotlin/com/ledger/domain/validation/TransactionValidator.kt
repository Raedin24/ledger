
package com.ledger.domain.validation

import com.ledger.domain.parser.PartialFields

/**
 * The sole OTP-safety mechanism: strict POSITIVE template matching.
 *
 * A message is only accepted as a transaction when it satisfies the strict
 * positive shape:
 *
 *   direction (DR/CR)  +  amount (currency-tagged)  +  balance
 *
 * The BALANCE is the true OTP discriminator: every real wallet/account
 * movement reports a post-transaction balance, and an OTP / verification code
 * / marketing blast never does. Requiring a balance is what makes an OTP
 * structurally fail this positive match — even the hard case of an OTP that
 * quotes an amount ("approval code for payment of GHS50.00 ... is 456123"),
 * because it carries no balance.
 *
 * The provider transaction id (reference) is corroborating but OPTIONAL: real
 * captured samples show legitimate balance-bearing transactions with no
 * reference at all (e.g. interest credits, some bundle purchases). Those are
 * accepted and deduplicated via the composite fallback key rather than being
 * bounced to review. There is still NO OTP blocklist — the guarantee rests
 * entirely on the positive balance+amount+direction requirement.
 *
 * Classification of a message that is not fully valid:
 *  - no currency amount at all                 -> NotTransaction (discard)
 *  - amount + balance but no clear direction    -> Incomplete (review: e.g. a
 *    "request received, awaiting confirmation" pending message)
 *  - amount + reference but no balance          -> Incomplete (review: format drift)
 *  - amount only (no balance, no reference)     -> NotTransaction (discard: OTP)
 */
class TransactionValidator {

    fun validate(fields: PartialFields, hasCurrencyAmount: Boolean): ValidationResult {
        // Gate 1: no currency-tagged amount anywhere -> cannot be a transaction.
        // This is what makes a bare OTP ("Your OTP is 123456") fail: 123456 is
        // not preceded by a currency token.
        if (!hasCurrencyAmount || fields.amount == null) {
            return ValidationResult.NotTransaction("no currency-tagged amount present")
        }

        val hasDirection = fields.direction != null
        val hasBalance = fields.balanceAfter != null
        val hasReference = !fields.reference.isNullOrBlank()

        // Gate 2: strict positive match. Balance is the OTP discriminator.
        if (hasDirection && hasBalance) {
            return ValidationResult.Valid
        }

        // Transaction-like but ambiguous -> human review, never auto-persist.
        if (hasBalance || hasReference) {
            val missing = buildList {
                if (!hasDirection) add("direction")
                if (!hasBalance) add("balance")
            }
            return ValidationResult.Incomplete("ambiguous transaction, missing: ${missing.joinToString(", ")}")
        }

        // Amount but no balance and no reference: an OTP/marketing text. Discard.
        return ValidationResult.NotTransaction("amount without balance — not transaction-shaped")
    }
}
