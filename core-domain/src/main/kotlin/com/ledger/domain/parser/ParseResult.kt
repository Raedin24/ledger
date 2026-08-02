package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution
import com.ledger.domain.model.Money
import com.ledger.domain.model.Transaction
import java.time.Instant

/**
 * Whatever partial fields the parser managed to pull from a message that did
 * NOT fully satisfy the strict template. Used only to populate the
 * "needs attention" queue for format-drift review. Deliberately holds no raw
 * SMS body — just the discrete fields plus sender/time context.
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
 * Outcome of parsing a single inbound SMS. Exhaustive by design so the caller
 * must consciously handle each case.
 */
sealed interface ParseResult {

    /** Sender is not in scope at all — silently ignored, nothing recorded. */
    data class Ignored(val senderId: String, val reason: String) : ParseResult

    /**
     * Sender is in scope but the message is not transaction-like (e.g. an OTP,
     * a marketing blast, a balance-enquiry reply). Discarded with no partial
     * fields — this is the branch that guarantees OTPs never surface anywhere.
     */
    data class Discarded(val institution: Institution, val reason: String) : ParseResult

    /**
     * Sender is in scope and the message looks like a transaction (carries a
     * currency amount plus a balance or reference) but did NOT fully satisfy
     * the strict template. Routed to the review queue to catch format drift
     * without weakening the OTP guarantee.
     */
    data class NeedsAttention(
        val institution: Institution,
        val receivedAt: Instant,
        val partial: PartialFields,
        val reason: String,
    ) : ParseResult

    /** Strict template fully matched — a real transaction was extracted. */
    data class Parsed(val transaction: Transaction) : ParseResult
}
