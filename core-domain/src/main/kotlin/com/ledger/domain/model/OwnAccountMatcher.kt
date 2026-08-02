package com.ledger.domain.model

/**
 * Decides whether a transaction's counterparty is one of the user's own accounts
 * — the test that keeps moving your own money from counting as spending.
 *
 * Providers describe the same movement three different ways, so this needs three
 * routes to the same answer:
 *
 *  1. **The number is the counterparty.** GhanaPay writes the account number in
 *     the sender field, so canonicalising it and comparing keys is enough. This
 *     is why GhanaPay transfers were already recognised.
 *  2. **The name is the counterparty.** MTN writes "payment received from AMA
 *     OWUSU" — a *name*, with no number to canonicalise. Nothing about it
 *     looks like an account, so the number route can never match it. Hence
 *     [names]: the labels a provider prints for an account the user owns.
 *  3. **The number is in the narration.** Cross-network transfers put the
 *     counterparty's number in the reference rather than the sender field
 *     (Telecel's "Transfer from: 0244123456-AMA", MTN's trailing name/number/
 *     reference triple), so the narration is scanned too.
 *
 * Name matching is deliberately **not** scoped to the institution of the message
 * being read. The whole point is cross-wallet: a name registered against a
 * GhanaPay account is precisely what shows up inside an *MTN* alert when money
 * moves between the two. Scoping the match by the reading institution would fail
 * the only case it exists to catch.
 */
class OwnAccountMatcher(
    private val keys: Set<String>,
    private val names: Set<String>,
) {
    /** True when nothing has been registered, so every lookup is a cheap no. */
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
         * The comparable form of a provider-printed account name: case- and
         * spacing-insensitive, since the same account arrives as "AMA OWUSU",
         * "Ama  Owusu" and "ama owusu" from different senders. Returns
         * null for anything too short to be a name worth matching on.
         */
        fun normaliseName(raw: String?): String? {
            val clean = raw?.trim()?.lowercase()?.replace(WHITESPACE, " ") ?: return null
            return clean.takeIf { it.length >= MIN_NAME_LENGTH }
        }

        /** Short enough to admit "ama", long enough to reject stray initials. */
        private const val MIN_NAME_LENGTH = 3

        private val WHITESPACE = Regex("\\s+")
    }
}
