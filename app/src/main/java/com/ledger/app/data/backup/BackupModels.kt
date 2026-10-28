package com.ledger.app.data.backup

import com.ledger.app.data.db.BudgetEntity
import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.CategoryKind
import com.ledger.app.data.db.OwnAccountEntity
import com.ledger.app.data.db.OwnAccountNameEntity
import com.ledger.app.data.db.RuleEntity
import com.ledger.app.data.db.TransactionEntity
import com.ledger.domain.model.OwnAccountMatcher
import kotlinx.serialization.Serializable

/**
 * The backup file format, used for both `.json` and encrypted `.ledger` files.
 * It holds everything needed to rebuild the ledger on another device:
 * transactions, rules, categories, budgets and the user's own accounts.
 *
 * Database row ids are not included. On import, rows are matched by
 * [TxnDto.dedupKey] and by natural keys such as names.
 *
 * Fields have only ever been added (the schema number counts the additions),
 * and every field has a default. An older file restores on a newer build, and
 * a newer file still opens on an older build. A file without own accounts
 * restores fine, but past transfers between the user's wallets will count as
 * spending until the accounts are added again.
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
    val budgets: List<BudgetDto> = emptyList(),
) {
    companion object { const val SCHEMA = 4 }
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
    /** [com.ledger.app.data.db.CashMove] value, or null. */
    val cashMove: String? = null,
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
    /**
     * A [com.ledger.app.data.db.CategoryKind] name. Defaults to money out, which is
     * what every category was in older backups.
     */
    val kind: String = CategoryKind.OUT.name,
    /** One of the user's favourite categories, which pickers show first. */
    val favorite: Boolean = false,
)

/** A monthly limit, keyed by category name (see [BudgetEntity]). */
@Serializable
data class BudgetDto(
    val category: String,
    val limitMinor: Long,
    val updatedAt: Long = 0,
)

/**
 * One of the user's own accounts, with its names nested inside it. Nesting
 * means a restore can never produce a name that belongs to no account.
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

// ---- Entity <-> DTO ----

fun TransactionEntity.toDto() = TxnDto(
    dedupKey = dedupKey, reference = reference, institution = institution, direction = direction,
    amountMinor = amountMinor, balanceMinor = balanceMinor, feeMinor = feeMinor, currency = currency,
    counterparty = counterparty, referenceHint = referenceHint, occurredAt = occurredAt,
    capturedAt = capturedAt, category = category, person = person, notes = notes, needsReview = needsReview,
    cashMove = cashMove,
)

/** Rebuilds an entity for insert. Room assigns the id; dedupKey prevents duplicates. */
fun TxnDto.toEntity() = TransactionEntity(
    dedupKey = dedupKey, reference = reference, institution = institution, direction = direction,
    amountMinor = amountMinor, balanceMinor = balanceMinor, feeMinor = feeMinor, currency = currency,
    counterparty = counterparty, referenceHint = referenceHint, occurredAt = occurredAt,
    capturedAt = capturedAt, category = category, person = person, notes = notes, needsReview = needsReview,
    cashMove = cashMove,
)

fun RuleEntity.toDto() = RuleDto(counterparty, direction, category, person, matchType, priority, enabled)

fun RuleDto.toEntity() = RuleEntity(
    id = 0, counterparty = counterparty, direction = direction, category = category, person = person,
    matchType = matchType, priority = priority, enabled = enabled, lastMatchedAtMillis = null,
)

fun CategoryEntity.toDto() = CategoryDto(name, position, enabled, kind, favorite)

fun CategoryDto.toEntity() =
    CategoryEntity(name = name, position = position, enabled = enabled, kind = kind, favorite = favorite)

fun BudgetEntity.toDto() = BudgetDto(category, limitMinor, updatedAt)

fun BudgetDto.toEntity() = BudgetEntity(
    category = category,
    limitMinor = limitMinor,
    updatedAt = updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
)

/**
 * [names] are the account's names, which live in their own table.
 *
 * The row id isn't saved, because it only means something in one database. A
 * restore finds the wallet by number and provider instead. "Not sure which
 * provider" is saved as null, so older files still restore.
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

/** Rebuilds this account's name rows, dropping any that are empty once normalised. */
fun OwnAccountDto.toNameEntities(accountId: Long): List<OwnAccountNameEntity> = names.mapNotNull { raw ->
    OwnAccountMatcher.normaliseName(raw)?.let {
        OwnAccountNameEntity(name = it, accountId = accountId, display = raw.trim())
    }
}
