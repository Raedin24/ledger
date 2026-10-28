package com.ledger.domain.model

/**
 * Which way money moved, from the user's point of view.
 *
 * DEBIT is money leaving the account (spending or a transfer out).
 * CREDIT is money arriving (income or a transfer in).
 *
 * Direction is part of what a categorisation rule matches on. The same
 * counterparty can be a gift recipient (DEBIT) and a source of repayment
 * (CREDIT). See CategorizationEngine.
 */
enum class Direction {
    DEBIT,
    CREDIT;

    companion object {
        /**
         * Turns the many direction markers providers use into a Direction. Returns
         * null for an unknown marker, so the validator can reject the message instead
         * of guessing.
         */
        fun fromMarker(raw: String): Direction? = when (raw.trim().uppercase()) {
            "DR", "DEBIT", "DEBITED", "PAYMENT", "SENT", "WITHDRAWAL", "-" -> DEBIT
            "CR", "CREDIT", "CREDITED", "RECEIVED", "DEPOSIT", "+" -> CREDIT
            else -> null
        }
    }
}
