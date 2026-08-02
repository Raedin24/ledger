package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution

/**
 * Per-institution direction detection and counterparty hints.
 *
 * Direction keywords are ordered and evaluated case-insensitively. Only the
 * keyword sets differ between providers; the numeric field extraction is shared
 * (see FieldExtractors) because amount/balance/reference formatting is broadly
 * consistent across Ghanaian providers.
 */
data class SenderTemplate(
    val institution: Institution,
    val debitMarkers: List<String>,
    val creditMarkers: List<String>,
    val debitPrepositions: List<String> = listOf("to", "at", "for"),
    val creditPrepositions: List<String> = listOf("from"),
) {
    /** Determines direction from marker phrases, or null if none are present. */
    fun detectDirection(body: String): Direction? {
        val lower = body.lowercase()
        val debitAt = debitMarkers.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val creditAt = creditMarkers.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        return when {
            debitAt != null && (creditAt == null || debitAt <= creditAt) -> Direction.DEBIT
            creditAt != null -> Direction.CREDIT
            else -> null
        }
    }

    fun prepositionsFor(direction: Direction): List<String> = when (direction) {
        Direction.DEBIT -> debitPrepositions
        Direction.CREDIT -> creditPrepositions
    }

    companion object {
        // Marker lists are derived from real captured samples (see
        // core-domain/README and the parser test corpus). "payment received"
        // and "you have received" must precede plain "payment"/"received" so a
        // credit is never mis-read as a debit.
        /**
         * Money coming back. A refund reverses a payment the user already made,
         * so it is an inflow — but its wording ("...has refunded GHS 3.00 **to**
         * your mobile money account") reads like an outflow to anything scanning
         * for a preposition, and matched no marker at all before, which left
         * refunds stuck in review with no direction.
         */
        private val REFUND_MARKERS = listOf("refunded", "refund of", "reversed", "reversal of")

        private val MTN_MOMO = SenderTemplate(
            institution = Institution.MTN_MOMO,
            debitMarkers = listOf("payment made", "payment for", "payment of", "you have sent", "sent to", "debited", "withdraw", "cash out", "paid to", "transfer to", "transferred"),
            creditMarkers = listOf("you have received", "received from", "payment received", "credited", "cash in", "deposit") + REFUND_MARKERS,
        )

        private val GHANAPAY = SenderTemplate(
            institution = Institution.GHANAPAY,
            debitMarkers = listOf("debited", "payment", "you have sent", "sent to", "transfer to", "transferred", "withdraw"),
            creditMarkers = listOf("credited", "you have received", "received from", "deposit") + REFUND_MARKERS,
        )

        private val TELECEL_CASH = SenderTemplate(
            institution = Institution.TELECEL_CASH,
            debitMarkers = listOf("payment made", "you have sent", "sent to", "debited", "withdraw", "withdrawn", "cash out", "paid", "bought", "transferred"),
            creditMarkers = listOf("you have received", "received from", "credited", "cash in", "deposit") + REFUND_MARKERS,
        )

        private val byInstitution: Map<Institution, SenderTemplate> = listOf(
            MTN_MOMO, GHANAPAY, TELECEL_CASH,
        ).associateBy { it.institution }

        fun forInstitution(institution: Institution): SenderTemplate? = byInstitution[institution]
    }
}
