package com.ledger.domain.model

import java.time.Instant

/**
 * A parsed transaction, and the only thing ever saved.
 *
 * Privacy rule: the raw SMS text is never a field here, and is never stored,
 * logged or cached. Only the separate fields below are kept, and the original
 * text is dropped as soon as parsing finishes.
 *
 * `category`, `person` and `notes` are empty at capture and filled in later
 * by the user's rules or by the user.
 */
data class Transaction(
    /** The provider's reference. The main dedup key when there is one. */
    val reference: String?,
    val institution: Institution,
    val direction: Direction,
    val amount: Money,
    /** The wallet balance after the transaction, if the SMS gave one. */
    val balanceAfter: Money?,
    val fee: Money?,
    val currency: String,
    /**
     * The other party: a phone number or a merchant name. Rules match on this,
     * never on the free-text narration.
     */
    val counterparty: String?,
    /** When the transaction happened. Currently the time the SMS arrived. */
    val occurredAt: Instant,
    /** When the device received and processed the SMS. */
    val capturedAt: Instant,

    /**
     * The provider's "Ref:", "Reference:" or "Message:" narration, if any. It is
     * kept only to help the user during review ("uber", "hospital"). It is never
     * used for matching or dedup, because the sender writes it and it can't be
     * relied on.
     */
    val referenceHint: String? = null,

    // ---- Filled in later, not from the SMS ----
    val category: String? = null,
    val person: String? = null,
    /** The user's own note. Separate from the SMS narration in [referenceHint]. */
    val notes: String? = null,
    /** True while this waits in the review queue for the user to confirm it. */
    val needsReview: Boolean = false,
    /**
     * The message is about cash changing hands with an agent: a cash-out on a
     * debit, a cash-in on a credit. The money moved between the wallet and the
     * user's pocket rather than to someone else, so the app asks whether to move
     * it to the cash ledger instead of counting it as spending or income.
     */
    val cashMovement: Boolean = false,
)
