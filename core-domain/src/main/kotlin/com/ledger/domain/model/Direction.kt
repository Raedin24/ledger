package com.ledger.domain.model

/**
 * Direction of money movement relative to the user's account.
 *
 * DEBIT  = money leaving the account (an expense / outgoing transfer).
 * CREDIT = money entering the account (income / incoming transfer).
 *
 * Direction is a first-class part of a categorization rule key, not merely an
 * output: the same counterparty can be both a gift recipient (DEBIT) and a
 * reimbursement source (CREDIT). See CategorizationEngine.
 */
enum class Direction {
    DEBIT,
    CREDIT;

    companion object {
        /**
         * Normalises the many textual markers telcos/banks use for direction.
         * Returns null if the token is not a recognised direction marker, which
         * lets the validator reject a message rather than guessing.
         */
        fun fromMarker(raw: String): Direction? = when (raw.trim().uppercase()) {
            "DR", "DEBIT", "DEBITED", "PAYMENT", "SENT", "WITHDRAWAL", "-" -> DEBIT
            "CR", "CREDIT", "CREDITED", "RECEIVED", "DEPOSIT", "+" -> CREDIT
            else -> null
        }
    }
}
