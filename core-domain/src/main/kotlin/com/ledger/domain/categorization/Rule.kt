package com.ledger.domain.categorization

import com.ledger.domain.model.Direction

/**
 * A categorization rule.
 *
 * Keyed on {counterparty, direction} — NOT counterparty alone — because the
 * same contact can be both a gift recipient (DEBIT) and a reimbursement source
 * (CREDIT); direction is part of the match, not just the produced type.
 *
 * Matching is on the counterparty identifier (phone/merchant), never on the
 * free-text SMS reason, which is unreliable and often attacker/terminal
 * controlled.
 *
 * `matchType` guards against the known substring bug (a bare "MOM" rule wrongly
 * matching "MOMOUSER"). EXACT is the safe default for learned rules; CONTAINS
 * is opt-in for deliberately broad merchant rules and still word-boundary aware.
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
    /** Epoch millis of the last transaction this rule matched. Drives the
     *  stale-rule re-confirmation nudge (6+ months unused). */
    val lastMatchedAtMillis: Long? = null,
) {
    enum class MatchType { EXACT, CONTAINS }

    fun matches(counterparty: String, direction: Direction): Boolean {
        if (!enabled) return false
        if (direction != this.direction) return false
        val target = counterparty.trim()
        val key = this.counterparty.trim()
        if (target.isEmpty() || key.isEmpty()) return false
        return when (matchType) {
            MatchType.EXACT -> target.equals(key, ignoreCase = true)
            MatchType.CONTAINS -> containsWord(target, key)
        }
    }

    private fun containsWord(haystack: String, needle: String): Boolean {
        // Word-boundary aware substring match: "MOM" must not match "MOMOUSER"
        // but should match "PAYMENT TO MOM".
        val pattern = Regex("(?<![A-Za-z0-9])${Regex.escape(needle)}(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(haystack)
    }
}
