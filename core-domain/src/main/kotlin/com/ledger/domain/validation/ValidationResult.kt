package com.ledger.domain.validation

sealed interface ValidationResult {
    /** All required transaction fields present — safe to persist. */
    data object Valid : ValidationResult

    /**
     * Structurally NOT a transaction (no currency amount, or nothing but a bare
     * code). This is the branch OTPs and marketing texts fall into. Discard.
     */
    data class NotTransaction(val reason: String) : ValidationResult

    /**
     * Looks transaction-like (has a currency amount plus a balance or reference)
     * but is missing a required field — likely format drift. Route to review,
     * never auto-persist.
     */
    data class Incomplete(val reason: String) : ValidationResult
}
