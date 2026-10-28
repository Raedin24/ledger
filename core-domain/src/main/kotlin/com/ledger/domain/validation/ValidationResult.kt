package com.ledger.domain.validation

sealed interface ValidationResult {
    /** Every required field is present, so it is safe to save. */
    data object Valid : ValidationResult

    /**
     * Not a transaction (no currency amount, or only a bare code). OTPs and
     * marketing messages end up here. Dropped.
     */
    data class NotTransaction(val reason: String) : ValidationResult

    /**
     * Looks like a transaction (a currency amount plus a balance or reference) but
     * is missing a required field, probably because the format changed. Sent to
     * review, never saved automatically.
     */
    data class Incomplete(val reason: String) : ValidationResult
}
