package com.ledger.domain.model

import java.time.Instant

/**
 * A parsed financial transaction — the ONLY thing that is ever persisted.
 *
 * Privacy invariant (roadmap, non-negotiable): the raw SMS body is never a
 * field here and is never stored, logged, or cached anywhere. Only the
 * discrete fields below are extracted; the original in-memory string is
 * discarded immediately after parsing.
 *
 * `category`, `person` and `notes` are null at capture time and filled in by
 * the categorization engine or the user later.
 */
data class Transaction(
    /** Provider-supplied reference. Primary idempotency key when present. */
    val reference: String?,
    val institution: Institution,
    val direction: Direction,
    val amount: Money,
    /** Account/wallet balance after the transaction, if the SMS carried one. */
    val balanceAfter: Money?,
    val fee: Money?,
    val currency: String,
    /**
     * The other party: a phone number or registered merchant/institution name.
     * This is the PRIMARY categorization signal (never the free-text reason).
     */
    val counterparty: String?,
    /** When the transaction occurred, per the SMS (falls back to receipt time). */
    val occurredAt: Instant,
    /** When the SMS was received/processed by the device. */
    val capturedAt: Instant,

    /**
     * The provider's "Ref:"/"Reference:"/"Message:" narration, when present.
     * Stored ONLY as a display hint to help the user during review (e.g. "uber",
     * "hospital", "data from GhanaPay"). It is never a matching or dedup key —
     * it is user/terminal-authored and unreliable for that — but it is genuinely
     * useful context, so it is kept when available. May be null / empty.
     */
    val referenceHint: String? = null,

    // ---- Assigned later, not from the SMS ----
    val category: String? = null,
    val person: String? = null,
    /** User-authored context. Distinct from any SMS reference text, which is
     *  intentionally NOT persisted as it is unreliable / attacker-controlled. */
    val notes: String? = null,
    /** True while this sits in the review queue awaiting user confirmation. */
    val needsReview: Boolean = false,
)
