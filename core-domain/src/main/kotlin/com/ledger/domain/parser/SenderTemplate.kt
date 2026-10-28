package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution

/**
 * Per-provider direction detection and counterparty hints.
 *
 * Direction keywords are checked in order, ignoring case. Only the keywords
 * differ between providers. Amounts, balances and references look much the
 * same everywhere, so their extraction is shared (see FieldExtractors).
 */
data class SenderTemplate(
    val institution: Institution,
    val debitMarkers: List<String>,
    val creditMarkers: List<String>,
    val debitPrepositions: List<String> = listOf("to", "at", "for"),
    val creditPrepositions: List<String> = listOf("from"),
) {
    /**
     * Works out the direction from marker phrases, or null if there are none.
     *
     * Normally the earliest marker in the message wins. A refund or reversal is
     * the exception and always means money in. Those messages name the original
     * purchase first ("Your bundle purchase of GHS5.00 has been reversed"), so the
     * earliest marker would wrongly say money out.
     */
    fun detectDirection(body: String): Direction? {
        val lower = body.lowercase()
        if (REFUND_MARKERS.any { it in lower }) return Direction.CREDIT
        val debitAt = debitMarkers.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val creditAt = creditMarkers.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
        return when {
            debitAt != null && (creditAt == null || debitAt <= creditAt) -> Direction.DEBIT
            creditAt != null -> Direction.CREDIT
            else -> null
        }
    }

    /**
     * Whether [body] is a withdrawal to cash, or a deposit from cash.
     *
     * Only the wording for [direction] counts, so a reversed cash-out (money in
     * that still says "cash out") isn't treated as a deposit.
     */
    fun isCashMovement(body: String, direction: Direction): Boolean {
        val lower = body.lowercase()
        val markers = if (direction == Direction.DEBIT) CASH_OUT_MARKERS else CASH_IN_MARKERS
        return markers.any { it in lower }
    }

    fun prepositionsFor(direction: Direction): List<String> = when (direction) {
        Direction.DEBIT -> debitPrepositions
        Direction.CREDIT -> creditPrepositions
    }

    companion object {
        // Marker lists come from real messages (see core-domain/README and the
        // parser tests). "payment received" and "you have received" must come
        // before plain "payment" and "received", so money in is never read as
        // money out.
        /**
         * Money coming back. A refund returns money the user already paid, so it is
         * money in, even though its wording ("has refunded GHS 3.00 to your mobile
         * money account") can look like money out.
         *
         * [detectDirection] checks these first. They also stay in each template's
         * credit list.
         */
        private val REFUND_MARKERS = listOf("refunded", "refund of", "reversed", "reversal of")

        /** Taking cash from an agent. The wording is the same across providers. */
        private val CASH_OUT_MARKERS = listOf("cash out", "cashout", "cash-out", "withdraw")

        /**
         * Handing cash to an agent. Plain "deposit" is left out, because banks use it
         * for transfers that never involved cash.
         */
        private val CASH_IN_MARKERS = listOf("cash in", "cashin", "cash-in", "cash deposit")

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
            // Telecel words a data bundle two ways: "bought airtime" but also "your
            // bundle purchase request of GHS3.00", so both phrases are listed. They
            // are full phrases, not a bare "purchase", because that word also appears
            // in refund wording.
            debitMarkers = listOf("payment made", "you have sent", "sent to", "debited", "withdraw", "withdrawn", "cash out", "paid", "bought", "bundle purchase", "purchase request", "transferred"),
            creditMarkers = listOf("you have received", "received from", "credited", "cash in", "deposit") + REFUND_MARKERS,
        )

        private val byInstitution: Map<Institution, SenderTemplate> = listOf(
            MTN_MOMO, GHANAPAY, TELECEL_CASH,
        ).associateBy { it.institution }

        fun forInstitution(institution: Institution): SenderTemplate? = byInstitution[institution]
    }
}
