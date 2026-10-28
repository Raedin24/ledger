package com.ledger.domain.categorization

import com.ledger.domain.model.Direction

/**
 * A categorisation rule.
 *
 * A rule matches a counterparty and a direction together, not the
 * counterparty alone. The same person can receive a gift from the user (money
 * out) and pay them back (money in), and those belong in different categories.
 *
 * Matching uses the counterparty (a phone number or merchant name), never the
 * free-text SMS narration, which the sender writes and can't be relied on.
 *
 * `matchType` stops short names matching inside longer ones (a "MOM" rule
 * matching "MOMOUSER"). EXACT is the default for rules created from review.
 * CONTAINS is for broad merchant rules the user sets up on purpose, and it
 * still only matches whole words.
 */
data class Rule(
    val id: Long,
    val counterparty: String,
    val direction: Direction,
    val category: String,
    val person: String?,
    val matchType: MatchType = MatchType.EXACT,
    val priority: Int = 0,
    val enabled: Boolean = true,
    /**
     * Epoch millis of the last transaction this rule matched. A rule unused for
     * six months asks to be confirmed again.
     */
    val lastMatchedAtMillis: Long? = null,
) {
    enum class MatchType { EXACT, CONTAINS }

    /** Compiled once per rule, since every message is tested against every rule. */
    private val wordPattern: Regex by lazy {
        Regex(
            "(?<![A-Za-z0-9])${Regex.escape(counterparty.trim())}(?![A-Za-z0-9])",
            RegexOption.IGNORE_CASE,
        )
    }

    fun matches(counterparty: String, direction: Direction): Boolean {
        if (!enabled) return false
        if (direction != this.direction) return false
        val target = counterparty.trim()
        val key = this.counterparty.trim()
        if (target.isEmpty() || key.isEmpty()) return false
        return when (matchType) {
            MatchType.EXACT -> target.equals(key, ignoreCase = true)
            MatchType.CONTAINS -> containsWord(target)
        }
    }

    /** Whole words only: "MOM" matches "PAYMENT TO MOM" but not "MOMOUSER". */
    private fun containsWord(haystack: String): Boolean = wordPattern.containsMatchIn(haystack)
}
