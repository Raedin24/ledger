package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution
import com.ledger.domain.model.Money
import com.ledger.domain.model.Transaction
import java.time.Instant

/**
 * The fields the parser could read from a message that didn't fully match the
 * strict template. Used only to fill the review queue. It holds no SMS text,
 * just the separate fields plus the sender and time.
 */
data class PartialFields(
    val direction: Direction?,
    val amount: Money?,
    val balanceAfter: Money?,
    val reference: String?,
    val counterparty: String?,
    val referenceHint: String? = null,
)

/**
 * The result of parsing one SMS. It is a sealed type, so callers have to
 * handle every case.
 */
sealed interface ParseResult {

    /** Not from a supported provider. Ignored, and nothing is recorded. */
    data class Ignored(val senderId: String, val reason: String) : ParseResult

    /**
     * From a supported provider, but not a transaction (an OTP, a marketing
     * message, a balance reply). Dropped without keeping any fields. This is the
     * branch that keeps OTPs out of the app.
     */
    data class Discarded(val institution: Institution, val reason: String) : ParseResult

    /**
     * From a supported provider and looks like a transaction (a currency amount
     * plus a balance or reference), but didn't fully match the strict template.
     * Sent to the review queue, so format changes are caught without weakening
     * the OTP rule.
     */
    data class NeedsAttention(
        val institution: Institution,
        val receivedAt: Instant,
        val partial: PartialFields,
        val reason: String,
    ) : ParseResult

    /** The strict template matched, and a transaction was read. */
    data class Parsed(val transaction: Transaction) : ParseResult
}
