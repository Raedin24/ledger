package com.ledger.app.data.backup

import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.CategoryKind
import com.ledger.app.data.db.OwnAccountEntity
import com.ledger.app.data.db.OwnAccountNameEntity
import com.ledger.app.data.db.RuleEntity
import com.ledger.app.data.db.TransactionEntity
import com.ledger.domain.model.OwnAccountMatcher
import kotlinx.serialization.Serializable

/**
 * The JSON/`.ledger` backup document. Everything needed to rebuild the ledger on
 * another device: transactions (with their stable dedup keys, so a re-import
 * merges cleanly), rules, categories, and the user's own accounts. Deliberately
 * excludes DB row ids — identity is re-established from [TxnDto.dedupKey] /
 * natural keys on import.
 *
 * Schema 2 added [ownAccounts]. Restoring a schema-1 file still works — the field
 * defaults to empty — but such a restore lands with no own accounts registered,
 * so every past internal transfer counts as spending until they are re-entered.
 * That is exactly the data loss carrying them here prevents going forward.
 */
@Serializable
data class BackupFile(
    val schema: Int = SCHEMA,
    val app: String = "Ledger",
    val exportedAt: Long,
    val transactions: List<TxnDto> = emptyList(),
    val rules: List<RuleDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val ownAccounts: List<OwnAccountDto> = emptyList(),
) {
    companion object { const val SCHEMA = 2 }
}

@Serializable
data class TxnDto(
    val dedupKey: String,
    val reference: String? = null,
    val institution: String,
    val direction: String,
    val amountMinor: Long,
    val balanceMinor: Long? = null,
    val feeMinor: Long? = null,
    val currency: String,
    val counterparty: String? = null,
    val referenceHint: String? = null,
    val occurredAt: Long,
    val capturedAt: Long,
    val category: String? = null,
    val person: String? = null,
    val notes: String? = null,
    val needsReview: Boolean,
)

@Serializable
data class RuleDto(
    val counterparty: String,
    val direction: String,
    val category: String,
    val person: String? = null,
    val matchType: String,
    val priority: Int,
    val enabled: Boolean,
)

@Serializable
data class CategoryDto(
    val name: String,
    val position: Int,
    val enabled: Boolean = true,
    /** [com.ledger.app.data.db.CategoryKind] name. Defaults to spending so a
     *  pre-split backup restores as it was written. */
    val kind: String = CategoryKind.OUT.name,
)

/**
 * One of the user's own accounts, with the provider names it answers to nested
 * rather than held in a parallel list — there is no way to serialise a dangling
 * alias, so a restore can't produce one.
 */
@Serializable
data class OwnAccountDto(
    val key: String,
    val identifier: String,
    val label: String,
    val addedAt: Long,
    val institution: String? = null,
    val names: List<String> = emptyList(),
)

// ---- entity <-> DTO ----

fun TransactionEntity.toDto() = TxnDto(
    dedupKey = dedupKey, reference = reference, institution = institution, direction = direction,
    amountMinor = amountMinor, balanceMinor = balanceMinor, feeMinor = feeMinor, currency = currency,
    counterparty = counterparty, referenceHint = referenceHint, occurredAt = occurredAt,
    capturedAt = capturedAt, category = category, person = person, notes = notes, needsReview = needsReview,
)

/** Rebuilds an entity for insert (id = 0 → Room assigns; dedupKey guards duplicates). */
fun TxnDto.toEntity() = TransactionEntity(
    dedupKey = dedupKey, reference = reference, institution = institution, direction = direction,
    amountMinor = amountMinor, balanceMinor = balanceMinor, feeMinor = feeMinor, currency = currency,
    counterparty = counterparty, referenceHint = referenceHint, occurredAt = occurredAt,
    capturedAt = capturedAt, category = category, person = person, notes = notes, needsReview = needsReview,
)

fun RuleEntity.toDto() = RuleDto(counterparty, direction, category, person, matchType, priority, enabled)

fun RuleDto.toEntity() = RuleEntity(
    id = 0, counterparty = counterparty, direction = direction, category = category, person = person,
    matchType = matchType, priority = priority, enabled = enabled, lastMatchedAtMillis = null,
)

fun CategoryEntity.toDto() = CategoryDto(name, position, enabled, kind)

fun CategoryDto.toEntity() =
    CategoryEntity(name = name, position = position, enabled = enabled, kind = kind)

/**
 * [names] are the account's aliases, which live in their own table.
 *
 * The row id is deliberately not serialised — it is local to one database, and a
 * restore resolves the owning wallet by (key, provider) instead. "Not sure which
 * provider" travels as null so files written before wallets were distinguished
 * by provider still restore.
 */
fun OwnAccountEntity.toDto(names: List<String>) = OwnAccountDto(
    key = key, identifier = identifier, label = label, addedAt = addedAt,
    institution = institution.takeIf { it != OwnAccountEntity.ANY_PROVIDER }, names = names,
)

/** @param id the existing row to overwrite, or 0 to let Room assign one. */
fun OwnAccountDto.toEntity(id: Long = 0) = OwnAccountEntity(
    id = id, key = key, identifier = identifier, label = label, addedAt = addedAt,
    institution = institution ?: OwnAccountEntity.ANY_PROVIDER,
)

/** Rebuilds this account's alias rows, dropping any that don't normalise. */
fun OwnAccountDto.toNameEntities(accountId: Long): List<OwnAccountNameEntity> = names.mapNotNull { raw ->
    OwnAccountMatcher.normaliseName(raw)?.let {
        OwnAccountNameEntity(name = it, accountId = accountId, display = raw.trim())
    }
}
