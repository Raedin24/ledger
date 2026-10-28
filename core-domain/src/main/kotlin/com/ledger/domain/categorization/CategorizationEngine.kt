package com.ledger.domain.categorization

import com.ledger.domain.model.Transaction

/**
 * Applies the user's rules to transactions. It has no side effects: it says
 * what should happen (file it, send it to review, propose a rule) and the
 * caller saves the result. That keeps it easy to test and keeps I/O out of the
 * domain layer.
 */
class CategorizationEngine(
    private val staleRuleThresholdMillis: Long = 183L * 24 * 60 * 60 * 1000,  // about 6 months
) {

    sealed interface Outcome {
        /**
         * A rule matched. Holds the categorised transaction and the rule's id, so the
         * caller can update the rule's last-matched time. `needsReconfirm` is true when
         * the rule hasn't matched for six months or more: phone numbers get reassigned,
         * so the user is asked once to confirm.
         */
        data class Categorized(
            val transaction: Transaction,
            val ruleId: Long,
            val needsReconfirm: Boolean,
        ) : Outcome

        /**
         * No rule matched this counterparty and direction. The transaction goes to
         * the review queue, where confirming it can create a rule (see proposeRule).
         */
        data class Review(val transaction: Transaction, val reason: String) : Outcome
    }

    fun categorize(txn: Transaction, rules: List<Rule>, nowMillis: Long): Outcome {
        val counterparty = txn.counterparty?.trim()
        if (counterparty.isNullOrEmpty()) {
            return Outcome.Review(txn.copy(needsReview = true), "no counterparty to match on")
        }

        val match = rules
            .filter { it.matches(counterparty, txn.direction) }
            .maxByOrNull { it.priority }  // highest priority wins; ties go to the first
            ?: return Outcome.Review(txn.copy(needsReview = true), "first-seen counterparty: $counterparty")

        val stale = match.lastMatchedAtMillis?.let { nowMillis - it > staleRuleThresholdMillis } ?: false

        return Outcome.Categorized(
            transaction = txn.copy(
                category = match.category,
                person = match.person,
                needsReview = stale,  // ask the user to confirm once
            ),
            ruleId = match.id,
            needsReconfirm = stale,
        )
    }

    /**
     * Builds the rule to save when the user confirms a reviewed transaction with
     * "always sort this sender this way" turned on. It uses an EXACT match on the
     * counterparty as seen, so it can't accidentally match a longer name.
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
