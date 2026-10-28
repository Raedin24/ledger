package com.ledger.domain.model

/**
 * A normalised mobile-money number, used to decide whether a counterparty is
 * one of the user's own wallets.
 *
 * Providers write the same number many ways: `0244000111`, `+233244000111`,
 * `233 244 000 111`, `024-400-0111`. The key keeps only the digits and then
 * the last [SIGNIFICANT_DIGITS] of them. Every local and international form
 * of one number gives the same key, and it is still long enough that two
 * different accounts won't clash.
 *
 * A counterparty with no usable digits (a merchant name like "MTN Airtime")
 * has no key and can never be a self-transfer.
 */
object AccountKey {

    /** Enough digits to tell numbers apart, few enough to drop the country code. */
    const val SIGNIFICANT_DIGITS = 9

    /** Fewer digits than this isn't an account number (a "5" in "GHS 5.00"). */
    private const val MIN_DIGITS = 6

    /**
     * A run of digits that looks like an account number. Dashes (`024-400-0111`)
     * and a leading `+` are allowed. Spaces are not, or "GHS 2.38 to 0244000111"
     * would join the end of the amount onto the number.
     */
    private val NUMBER_RUN = Regex("\\+?\\d[\\d-]{4,}\\d")

    /** The key for [raw], or null when it has no usable account number. */
    fun of(raw: String?): String? {
        val digits = raw?.filter(Char::isDigit).orEmpty()
        if (digits.length < MIN_DIGITS) return null
        return digits.takeLast(SIGNIFICANT_DIGITS)
    }

    /**
     * Keys for every account-like number in [text]. Used on the SMS narration,
     * where providers put the other party's number when the sender field only has
     * a name, such as Telecel's "Transfer from: 0244000111-AMA" or MTN's trailing
     * "name, number, reference".
     */
    fun allIn(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return NUMBER_RUN.findAll(text).mapNotNull { of(it.value) }.distinct().toList()
    }

    /** True when [counterparty]'s key is one of [ownKeys], which are already keys. */
    fun isOwnAccount(counterparty: String?, ownKeys: Set<String>): Boolean {
        if (ownKeys.isEmpty()) return false
        val key = of(counterparty) ?: return false
        return key in ownKeys
    }
}
