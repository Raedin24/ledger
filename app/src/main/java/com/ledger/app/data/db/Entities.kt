package com.ledger.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved transaction. It mirrors the domain [com.ledger.domain.model.Transaction]
 * using types Room can store. There is no column for the SMS text: the raw
 * message is never written to disk.
 *
 * `dedupKey` identifies the transaction, using its reference when there is one
 * (see DuplicateDetector). It has a unique index, so the same message can never
 * be saved twice.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dedupKey"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["counterparty", "direction"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupKey: String,
    val reference: String?,
    val institution: String,
    val direction: String,  // "DEBIT" or "CREDIT"
    val amountMinor: Long,
    val balanceMinor: Long?,
    val feeMinor: Long?,
    val currency: String,
    val counterparty: String?,
    val referenceHint: String?,
    val occurredAt: Long,            // epoch millis
    val capturedAt: Long,
    val category: String?,
    val person: String?,
    val notes: String?,
    val needsReview: Boolean,
    /**
     * True when the counterparty is one of the user's own accounts (see
     * [OwnAccountEntity]), so this is money moving between their own wallets.
     * These rows are left out of the totals, except for [feeMinor], which the user
     * really did pay.
     */
    @ColumnInfo(defaultValue = "0")
    val selfTransfer: Boolean = false,
    /**
     * [counterparty] as a normalised account number (see
     * [com.ledger.domain.model.AccountKey]), so own-account matching can be done in
     * SQL. An empty string means the counterparty has no number, such as a merchant
     * name. Null means it hasn't been worked out yet; it is filled in on first use.
     */
    val counterpartyKey: String? = null,
    /**
     * Normalised account numbers found in the SMS narration, written as
     * `"|244123456|"` so a `LIKE '%|key|%'` test matches exactly.
     *
     * Cross-network transfers put the other party's number in the reference
     * instead of the sender field, so this is where that number is kept. An empty
     * string means there was no number. Null means it hasn't been worked out yet.
     */
    val referenceKeys: String? = null,
    /** How this row relates to the cash ledger: a [CashMove] value, or null if not at all. */
    val cashMove: String? = null,
    /**
     * True when this row's balance didn't follow from the one before it. For a live
     * message, it is also set when the row before it had that problem (see the
     * balance check in [com.ledger.app.data.repository.LedgerRepository]). While
     * such a row waits in review, the next message isn't checked against it.
     */
    @ColumnInfo(defaultValue = "0")
    val chainBreak: Boolean = false,
)

/**
 * One of the user's own wallets. Used to recognise "moving my own money" so a
 * transfer between wallets isn't counted as spending.
 *
 * A wallet is identified by number and provider together. One phone number
 * often has more than one wallet, such as MTN MoMo and GhanaPay, and each
 * prints a different name, so each needs its own row and its own names.
 *
 * `key` is the normalised number (see [com.ledger.domain.model.AccountKey]), so
 * the same number typed two ways still matches. `identifier` keeps what the
 * user typed, for display.
 */
@Entity(
    tableName = "own_accounts",
    indices = [Index(value = ["key", "institution"], unique = true)],
)
data class OwnAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val identifier: String,
    val label: String,
    val addedAt: Long,
    /**
     * The provider this wallet belongs to (an [com.ledger.domain.model.Institution]
     * name), or [ANY_PROVIDER] for "not sure".
     *
     * It is part of the unique index, so it can't be null: SQLite treats every
     * null as different, which would let "not sure" be added again and again.
     *
     * It doesn't limit matching. A name saved against a GhanaPay wallet is exactly
     * what appears in an MTN alert when money arrives from it. See
     * [com.ledger.domain.model.OwnAccountMatcher].
     */
    val institution: String = ANY_PROVIDER,
) {
    companion object {
        /** "Not sure which wallet". Stored instead of null so it can be indexed. */
        const val ANY_PROVIDER = ""
    }
}

/**
 * A name a provider prints for one of the user's own accounts.
 *
 * MTN names the other party ("payment received from AMA OWUSU") where GhanaPay
 * gives a number. Without the name, money arriving from the user's other wallet
 * looks like a stranger paying them. It would count as income, and the matching
 * debit as spending.
 *
 * [name] is the normalised form (trimmed, lower case, single spaces) and the
 * primary key, so the same name in different casing is stored once. [display]
 * keeps what the user typed.
 */
@Entity(
    tableName = "own_account_names",
    indices = [Index(value = ["accountId"])],
)
data class OwnAccountNameEntity(
    @PrimaryKey val name: String,
    /**
     * The [OwnAccountEntity.id] this name belongs to. Names belong to a wallet,
     * not a number, because two wallets on one number print different names.
     */
    val accountId: Long,
    val display: String,
)

/**
 * A categorisation rule for a counterparty and direction. Mirrors
 * [com.ledger.domain.categorization.Rule].
 */
@Entity(
    tableName = "rules",
    indices = [Index(value = ["counterparty", "direction"])],
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterparty: String,
    val direction: String,
    val category: String,
    val person: String?,
    val matchType: String,  // "EXACT" or "CONTAINS"
    val priority: Int,
    val enabled: Boolean,
    val lastMatchedAtMillis: Long?,
)

/**
 * A category the user can file transactions under. A default set is seeded on
 * first run, and the user can add, rename, reorder, hide or delete them.
 *
 * - `position` sets the display order.
 * - `enabled = false` hides a category from the pickers without touching the
 *   rules and transactions that use it.
 * - `kind` is a [CategoryKind] name: money out, money in, or both. Its column
 *   default is declared so the schema matches what [LedgerDatabase.MIGRATION_4_5]
 *   creates.
 * - `favorite` marks the categories the user uses most. Pickers show these
 *   first; every other enabled category is behind "More". It is separate from
 *   `enabled`, so choosing favourites never hides anything.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "OUT") val kind: String = CategoryKind.OUT.name,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
)

/**
 * A monthly spending limit for one category.
 *
 * It refers to the category by name, like transactions do, because names
 * survive a backup and restore while row ids don't. The trade-off is that
 * renaming a category leaves its budget under the old name, just as it does
 * for transactions.
 *
 * Only spending categories have budgets.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    /** The monthly limit in pesewas. Always above zero; removing a budget deletes the row. */
    val limitMinor: Long,
    val updatedAt: Long,
)
