package com.ledger.domain.model

/**
 * Canonical form of a mobile-money number, used to decide whether a
 * transaction's counterparty is one of the user's *own* wallets.
 *
 * Providers write the same number many ways — `0244123456`, `+233244123456`,
 * `233 244 123 456`, `024-412-3456` — so matching on the raw string is hopeless.
 * The key is digits-only, reduced to the last [SIGNIFICANT_DIGITS], which makes
 * every Ghanaian national/international spelling of one number collapse to the
 * same value while staying long enough that two unrelated accounts realistically
 * never collide.
 *
 * A counterparty that carries no usable digits (a merchant name like "MTN
 * Airtime") has no key and can never be a self-transfer.
 */
object AccountKey {

    /** Enough to disambiguate; short enough to absorb country-code prefixes. */
    const val SIGNIFICANT_DIGITS = 9

    /** Fewer digits than this isn't an account number (a "5" in "GHS 5.00"). */
    private const val MIN_DIGITS = 6

    /**
     * An account-like run of digits. Internal separators are limited to dashes
     * (`024-412-3456`) and a leading `+`; spaces are deliberately excluded, or
     * "GHS 2.38 to 0244123456" would fuse the tail of the amount onto the number
     * and yield a key for an account that does not exist.
     */
    private val NUMBER_RUN = Regex("\\+?\\d[\\d-]{4,}\\d")

    /** Canonical key for [raw], or null when it holds no usable account number. */
    fun of(raw: String?): String? {
        val digits = raw?.filter(Char::isDigit).orEmpty()
        if (digits.length < MIN_DIGITS) return null
        return digits.takeLast(SIGNIFICANT_DIGITS)
    }

    /**
     * Canonical keys for every account-like number in [text]. Used on the SMS
     * narration, where providers hide the counterparty's number when the sender
     * field carries only a name — Telecel's "Transfer from: 0244123456-AMA",
     * MTN's trailing "name, number, reference" triple.
     */
    fun allIn(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return NUMBER_RUN.findAll(text).mapNotNull { of(it.value) }.distinct().toList()
    }

    /** True when [counterparty] resolves to one of [ownKeys] (already canonical). */
    fun isOwnAccount(counterparty: String?, ownKeys: Set<String>): Boolean {
        if (ownKeys.isEmpty()) return false
        val key = of(counterparty) ?: return false
        return key in ownKeys
    }
}
