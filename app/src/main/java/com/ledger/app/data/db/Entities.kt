package com.ledger.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted transaction. Mirrors the domain [com.ledger.domain.model.Transaction]
 * but stores primitives Room understands. NOTE: there is no `rawBody` column —
 * the raw SMS is never written to disk, by design.
 *
 * `dedupKey` is the stable idempotency key computed by DuplicateDetector
 * (reference-based, or composite fallback). Unique index => duplicate broadcasts
 * can never insert twice.
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
    val direction: String,           // "DEBIT" / "CREDIT"
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
     * [OwnAccountEntity]) — money moving between their own wallets, not spending.
     * Such rows are excluded from the spend/received totals; only the provider
     * [feeMinor] is counted, since that genuinely leaves the user's money.
     */
    @ColumnInfo(defaultValue = "0")
    val selfTransfer: Boolean = false,
    /**
     * Canonical [com.ledger.domain.model.AccountKey] form of [counterparty],
     * stored so "is this one of my accounts?" is a plain SQL join rather than a
     * Kotlin sweep over every row. Empty string = the counterparty carries no
     * account number (a merchant name); null = not computed yet (pre-v3 rows,
     * backfilled once on first use).
     */
    val counterpartyKey: String? = null,
    /**
     * Canonical account keys found in the SMS narration, pipe-delimited and
     * pipe-terminated (`"|244123456|"`) so a `LIKE '%|key|%'` test is exact.
     *
     * Cross-network transfers put the counterparty's number in the reference
     * rather than the sender field, so this is the only place that number
     * survives — the narration text itself is a review hint, never a match key.
     * Empty string = no number in the narration; null = not computed yet (pre-v4
     * rows, backfilled once on first use).
     */
    val referenceKeys: String? = null,
)

/**
 * One of the user's own wallets. Used to recognise "moving my own money" so an
 * internal transfer isn't counted as spending.
 *
 * Identity is **(number, provider)**, not the number alone. One MSISDN routinely
 * carries more than one wallet — the same line commonly holds both an MTN MoMo
 * and a GhanaPay account — and each prints a different name, so they need
 * separate alias sets. Keying the table on the number alone meant adding the
 * second wallet silently overwrote the first, taking its names with it.
 *
 * `key` is the canonical [com.ledger.domain.model.AccountKey] form, so the same
 * number typed two different ways still collapses within one provider.
 * `identifier` keeps what the user actually typed, for display.
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
     * Which provider this wallet lives with ([com.ledger.domain.model.Institution]
     * name), or [ANY_PROVIDER] for "not sure".
     *
     * Part of the unique index, hence non-null: SQLite treats NULLs as distinct,
     * so a nullable column would let "not sure" be added over and over.
     *
     * It never narrows *matching* — a name registered against a GhanaPay wallet
     * is exactly what appears inside an MTN alert, so scoping matches by provider
     * would break the cross-wallet case this whole feature exists to catch. See
     * [com.ledger.domain.model.OwnAccountMatcher].
     */
    val institution: String = ANY_PROVIDER,
) {
    companion object {
        /** "Not sure which wallet" — stored rather than null so it can be indexed. */
        const val ANY_PROVIDER = ""
    }
}

/**
 * A name a provider prints for one of the user's own accounts.
 *
 * MTN identifies a counterparty by name ("payment received from AMA OWUSU")
 * where GhanaPay gives a number. Without the name, an incoming transfer from the
 * user's own other wallet is indistinguishable from a stranger paying them, so
 * it counts as income and the matching debit counts as spending — the same money
 * booked twice.
 *
 * [name] is the normalised (trimmed, lower-cased, single-spaced) form and the
 * primary key, so the same name registered twice in different casing collapses.
 * [display] keeps what the user typed.
 */
@Entity(
    tableName = "own_account_names",
    indices = [Index(value = ["accountId"])],
)
data class OwnAccountNameEntity(
    @PrimaryKey val name: String,
    /** The owning [OwnAccountEntity.id] — per wallet, not per number, because
     *  two wallets on one number print different names. */
    val accountId: Long,
    val display: String,
)

/**
 * A categorization rule keyed on {counterparty, direction}. Mirrors
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
    val matchType: String,           // "EXACT" / "CONTAINS"
    val priority: Int,
    val enabled: Boolean,
    val lastMatchedAtMillis: Long?,
)

/**
 * An in-scope sender the user has onboarded (allow-list entry). Live capture is
 * only enabled per-sender after the "learn from a sample" onboarding step.
 */
@Entity(tableName = "senders")
data class SenderEntity(
    @PrimaryKey val institution: String,   // Institution.name
    val displayName: String,
    val liveCaptureEnabled: Boolean,
    val addedAt: Long,
)

/**
 * A user-managed category (the labels offered when reviewing a transaction or
 * writing a rule). Seeded with sensible defaults on first run, then fully
 * editable — add, rename, reorder, disable, or delete.
 *
 * `position` drives display order; `enabled = false` hides a category from the
 * pickers without deleting rules/transactions that already reference it.
 *
 * `kind` is a [CategoryKind] name and decides which direction offers it: money
 * out, money in, or both. The column default is declared explicitly so the schema
 * Room expects matches what [LedgerDatabase.MIGRATION_4_5]'s `ALTER TABLE ... ADD
 * COLUMN ... DEFAULT 'OUT'` actually produces — leaving it to the Kotlin
 * parameter default alone would describe the constructor, not the column.
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
)
