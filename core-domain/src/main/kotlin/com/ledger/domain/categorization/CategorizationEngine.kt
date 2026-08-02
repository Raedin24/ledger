package com.ledger.domain.categorization

import com.ledger.domain.model.Transaction

/**
 * Applies local rules to transactions. Pure and side-effect-free: it reports
 * what should happen (auto-categorize, review, propose a new rule) and lets the
 * caller persist. This keeps the engine trivially testable and keeps all I/O
 * out of the domain layer.
 */
class CategorizationEngine(
    private val staleRuleThresholdMillis: Long = 183L * 24 * 60 * 60 * 1000, // ~6 months
) {

    sealed interface Outcome {
        /** A rule matched. The categorized transaction plus the matched rule id
         *  (so the caller can bump its lastMatchedAt). `needsReconfirm` is true
         *  when the matched rule has been dormant 6+ months — a recycled phone
         *  number could now belong to a stranger, so nudge once. */
        data class Categorized(
            val transaction: Transaction,
            val ruleId: Long,
            val needsReconfirm: Boolean,
        ) : Outcome

        /** No rule matched this {counterparty, direction}. Goes to the review
         *  queue; confirming there should mint a new Rule (see proposeRule). */
        data class Review(val transaction: Transaction, val reason: String) : Outcome
    }

    fun categorize(txn: Transaction, rules: List<Rule>, nowMillis: Long): Outcome {
        val counterparty = txn.counterparty?.trim()
        if (counterparty.isNullOrEmpty()) {
            return Outcome.Review(txn.copy(needsReview = true), "no counterparty to match on")
        }

        val match = rules
            .filter { it.matches(counterparty, txn.direction) }
            .maxByOrNull { it.priority } // highest priority wins ties broken by first
            ?: return Outcome.Review(txn.copy(needsReview = true), "first-seen counterparty: $counterparty")

        val stale = match.lastMatchedAtMillis?.let { nowMillis - it > staleRuleThresholdMillis } ?: false

        return Outcome.Categorized(
            transaction = txn.copy(
                category = match.category,
                person = match.person,
                needsReview = stale, // surface for a one-time re-confirmation
            ),
            ruleId = match.id,
            needsReconfirm = stale,
        )
    }

    /**
     * Builds the rule to persist when a user confirms a reviewed transaction.
     * Called by the caller after the user picks category/person — encodes the
     * "always categorize this sender this way" default-on toggle as a rule with
     * EXACT match on the exact counterparty seen (substring-safe by construction).
     */
    fun proposeRule(
        nextId: Long,
        txn: Transaction,
        category: String,
        person: String?,
    ): Rule = Rule(
        id = nextId,
        counterparty = txn.counterparty?.trim().orEmpty(),
        direction = txn.direction,
        category = category,
        person = person,
        matchType = Rule.MatchType.EXACT,
    )
}
