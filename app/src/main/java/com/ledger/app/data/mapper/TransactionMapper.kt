package com.ledger.app.data.mapper

import com.ledger.app.data.db.CashMove
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

// Converts between the domain types and Room entities.

/**
 * Writes the account numbers found in an SMS narration in the form
 * `TransactionEntity.referenceKeys` stores (`"|244123456|"`), so a SQL
 * `LIKE '%|key|%'` test can't match part of a number. Returns an empty string
 * when there are none; null is reserved for "not worked out yet".
 */
fun packReferenceKeys(referenceHint: String?): String {
    val keys = AccountKey.allIn(referenceHint)
    return if (keys.isEmpty()) "" else keys.joinToString("|", prefix = "|", postfix = "|")
}

/**
 * [selfTransfer] is decided by the repository. The match keys are worked out
 * here, so every way a row is saved (live capture, inbox import, restore)
 * stores them the same way.
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
    cashMove = if (cashMovement) CashMove.OFFER else null,
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
    cashMovement = cashMove == CashMove.OFFER,
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

/** Turns a DuplicateDetector.Key into the string stored in the database. */
fun DuplicateDetector.Key.asString(): String = when (this) {
    is DuplicateDetector.Key.Reference -> "R|$institution|$reference"
    is DuplicateDetector.Key.Composite ->
        "C|$institution|$direction|$amountMinor|${balanceMinor ?: "-"}|$timeBucket"
}
