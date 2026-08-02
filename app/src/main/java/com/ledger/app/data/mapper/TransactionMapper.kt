package com.ledger.app.data.mapper

import com.ledger.app.data.db.RuleEntity
import com.ledger.app.data.db.TransactionEntity
import com.ledger.domain.categorization.Rule
import com.ledger.domain.dedup.DuplicateDetector
import com.ledger.domain.model.AccountKey
import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution
import com.ledger.domain.model.Money
import com.ledger.domain.model.Transaction
import java.time.Instant

/** Converts between the framework-free domain types and Room entities. */

/**
 * Packs the account numbers hidden in an SMS narration into the pipe-delimited,
 * pipe-terminated form `TransactionEntity.referenceKeys` stores, so a SQL
 * `LIKE '%|key|%'` test can never match a partial key. Empty string when the
 * narration holds no number — distinct from null, which means "not computed".
 */
fun packReferenceKeys(referenceHint: String?): String {
    val keys = AccountKey.allIn(referenceHint)
    return if (keys.isEmpty()) "" else keys.joinToString("|", prefix = "|", postfix = "|")
}

/**
 * [selfTransfer] is decided by the repository against the user's own accounts;
 * the canonical match keys are derived here so every insert path (live capture,
 * SMS backfill, backup import) stores them consistently.
 */
fun Transaction.toEntity(dedupKey: String, selfTransfer: Boolean = false): TransactionEntity = TransactionEntity(
    dedupKey = dedupKey,
    selfTransfer = selfTransfer,
    counterpartyKey = AccountKey.of(counterparty).orEmpty(),
    referenceKeys = packReferenceKeys(referenceHint),
    reference = reference,
    institution = institution.name,
    direction = direction.name,
    amountMinor = amount.minor,
    balanceMinor = balanceAfter?.minor,
    feeMinor = fee?.minor,
    currency = currency,
    counterparty = counterparty,
    referenceHint = referenceHint,
    occurredAt = occurredAt.toEpochMilli(),
    capturedAt = capturedAt.toEpochMilli(),
    category = category,
    person = person,
    notes = notes,
    needsReview = needsReview,
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    reference = reference,
    institution = Institution.valueOf(institution),
    direction = Direction.valueOf(direction),
    amount = Money(amountMinor),
    balanceAfter = balanceMinor?.let { Money(it) },
    fee = feeMinor?.let { Money(it) },
    currency = currency,
    counterparty = counterparty,
    referenceHint = referenceHint,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    capturedAt = Instant.ofEpochMilli(capturedAt),
    category = category,
    person = person,
    notes = notes,
    needsReview = needsReview,
)

fun RuleEntity.toDomain(): Rule = Rule(
    id = id,
    counterparty = counterparty,
    direction = Direction.valueOf(direction),
    category = category,
    person = person,
    matchType = Rule.MatchType.valueOf(matchType),
    priority = priority,
    enabled = enabled,
    lastMatchedAtMillis = lastMatchedAtMillis,
)

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    counterparty = counterparty,
    direction = direction.name,
    category = category,
    person = person,
    matchType = matchType.name,
    priority = priority,
    enabled = enabled,
    lastMatchedAtMillis = lastMatchedAtMillis,
)

/** Serialises a DuplicateDetector.Key to the stable string stored in the DB. */
fun DuplicateDetector.Key.asString(): String = when (this) {
    is DuplicateDetector.Key.Reference -> "R|$institution|$reference"
    is DuplicateDetector.Key.Composite ->
        "C|$institution|$direction|$amountMinor|${balanceMinor ?: "-"}|$timeBucket"
}
