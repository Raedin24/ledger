package com.ledger.domain.model

/**
 * Decides whether a counterparty is one of the user's own accounts. This is
 * what stops moving your own money from counting as spending.
 *
 * Providers describe the same transfer in three ways, so there are three
 * checks:
 *
 *  1. The counterparty is a number. GhanaPay puts the account number in the
 *     sender field, so comparing normalised numbers is enough.
 *  2. The counterparty is a name. MTN writes "payment received from AMA
 *     OWUSU", with no number at all. [names] holds the names providers print
 *     for the user's accounts.
 *  3. The number is in the narration. Cross-network transfers put the other
 *     party's number in the reference instead of the sender field (Telecel's
 *     "Transfer from: 0244000111-AMA", MTN's trailing name, number and
 *     reference), so the narration is checked too.
 *
 * Name matching ignores which provider sent the message. A name saved against
 * a GhanaPay account is exactly what appears in an MTN alert when money moves
 * between the two, so limiting it by provider would miss that case.
 */
class OwnAccountMatcher(
    private val keys: Set<String>,
    private val names: Set<String>,
) {
    /** True when nothing is registered, so every check can return false at once. */
    private val empty = keys.isEmpty() && names.isEmpty()

    /**
     * @param counterparty the parsed sender/recipient, a name or a number.
     * @param referenceHint the free-text narration, which may hide a number.
     */
    fun isOwn(counterparty: String?, referenceHint: String?): Boolean {
        if (empty) return false
        if (AccountKey.isOwnAccount(counterparty, keys)) return true
        normaliseName(counterparty)?.let { if (it in names) return true }
        if (keys.isNotEmpty()) {
            return AccountKey.allIn(referenceHint).any { it in keys }
        }
        return false
    }

    companion object {
        /**
         * A provider-printed name in comparable form, ignoring case and extra spaces,
         * since the same account arrives as "AMA OWUSU", "Ama  Owusu" and "ama owusu".
         * Returns null for anything too short to match on.
         */
        fun normaliseName(raw: String?): String? {
            val clean = raw?.trim()?.lowercase()?.replace(WHITESPACE, " ") ?: return null
            return clean.takeIf { it.length >= MIN_NAME_LENGTH }
        }

        /** Short enough to allow "ama", long enough to ignore stray initials. */
        private const val MIN_NAME_LENGTH = 3

        private val WHITESPACE = Regex("\\s+")
    }
}
