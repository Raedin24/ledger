package com.ledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** IGNORE on conflict = the unique dedupKey index silently drops duplicate
     *  broadcasts. Returns -1 when the row was a duplicate and not inserted. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(txn: TransactionEntity): Long

    /**
     * The balance this institution last reported at or before [at], for the
     * balance-chain check in the repository. Ordered by capture as well as
     * occurrence so two messages sharing a timestamp resolve deterministically.
     */
    @Query(
        """
        SELECT balanceMinor FROM transactions
        WHERE institution = :institution AND balanceMinor IS NOT NULL
          AND occurredAt <= :at
        ORDER BY occurredAt DESC, capturedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun lastBalanceBefore(institution: String, at: Long): Long?

    @Update
    suspend fun update(txn: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun clearAll()

    @Query("SELECT dedupKey FROM transactions")
    suspend fun allDedupKeys(): List<String>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** Whether the ledger has ever captured anything. Drives the first-run
     *  welcome, which must not depend on the *current month* having activity. */
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY occurredAt DESC")
    fun observeReviewQueue(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    fun observeReviewCount(): Flow<Int>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:query = '' OR counterparty LIKE '%' || :query || '%'
               OR notes LIKE '%' || :query || '%'
               OR referenceHint LIKE '%' || :query || '%'
               OR category LIKE '%' || :query || '%')
        ORDER BY occurredAt DESC
        """
    )
    fun search(query: String): Flow<List<TransactionEntity>>

    /**
     * Filtered + sorted history, done entirely in SQL so the (potentially large)
     * result set is never sorted or filtered on the main thread. Every filter is
     * nullable / opt-out; [sort] is one of "NEWEST" / "OLDEST" / "LARGEST".
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:query = '' OR counterparty LIKE '%' || :query || '%'
               OR notes LIKE '%' || :query || '%'
               OR referenceHint LIKE '%' || :query || '%'
               OR category LIKE '%' || :query || '%')
          AND (:direction   IS NULL OR direction = :direction)
          AND (:category    IS NULL OR category = :category)
          AND (:institution IS NULL OR institution = :institution)
          AND (:reviewOnly = 0 OR needsReview = 1)
          AND (:from IS NULL OR occurredAt >= :from)
          AND (:to   IS NULL OR occurredAt <= :to)
        ORDER BY
          CASE :sort WHEN 'LARGEST' THEN amountMinor ELSE 0 END DESC,
          CASE :sort WHEN 'OLDEST'  THEN occurredAt END ASC,
          occurredAt DESC
        """
    )
    fun filter(
        query: String,
        direction: String?,
        category: String?,
        institution: String?,
        reviewOnly: Boolean,
        from: Long?,
        to: Long?,
        sort: String,
    ): Flow<List<TransactionEntity>>

    /** Distinct institutions that actually appear in the ledger — powers the
     *  History "sender" filter chips (only senders you have data from). */
    @Query("SELECT DISTINCT institution FROM transactions ORDER BY institution ASC")
    fun observeInstitutions(): Flow<List<String>>

    /**
     * Retroactively applies a freshly-created rule to the transactions still
     * waiting in the review queue for the same sender + direction. This is what
     * makes "always sort this sender this way" clear the backlog instead of
     * leaving every past message to be reviewed one by one. EXACT (case-
     * insensitive) counterparty match mirrors how a learned Rule matches live.
     */
    @Query(
        """
        UPDATE transactions
        SET category = :category, person = :person, needsReview = 0
        WHERE needsReview = 1
          AND direction = :direction
          AND counterparty = :counterparty COLLATE NOCASE
        """
    )
    suspend fun applyRuleToPending(
        counterparty: String,
        direction: String,
        category: String,
        person: String?,
    ): Int

    /**
     * Spend/receive total for a window.
     *
     * A self-transfer moves the user's own money between their own accounts, so
     * it contributes only whatever the provider charged for it — never the
     * transferred amount itself.
     *
     * Every other debit contributes its amount **plus** its fee. The parser holds
     * the two apart on purpose (`transactionAmount` skips the span the fee match
     * occupies), so `amountMinor` is always net of the charge and summing it
     * alone quietly understated spending by every fee ever paid. Credits take the
     * amount alone: money coming in is not charged at the receiving end.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN selfTransfer = 1 THEN
                     CASE WHEN :direction = 'DEBIT' THEN COALESCE(feeMinor, 0) ELSE 0 END
                 WHEN :direction = 'DEBIT' THEN amountMinor + COALESCE(feeMinor, 0)
                 ELSE amountMinor END
        ), 0) FROM transactions
        WHERE direction = :direction AND occurredAt BETWEEN :from AND :to
        """
    )
    fun observeTotal(direction: String, from: Long, to: Long): Flow<Long>

    /**
     * Re-marks every transaction against the current set of own accounts. Run
     * after the user adds or removes one of their own numbers or names, so the
     * change applies to history and not just to messages captured from now on.
     *
     * Mirrors [com.ledger.domain.model.OwnAccountMatcher]'s three routes — the
     * counterparty *is* the number, the counterparty is a registered *name*, or
     * the number rides in the narration — so a row marked live and the same row
     * re-marked here always agree. Name matching is intentionally not scoped by
     * institution; see the matcher for why.
     */
    @Query(
        """
        UPDATE transactions
        SET selfTransfer = CASE
            WHEN counterpartyKey IS NOT NULL AND counterpartyKey <> ''
             AND counterpartyKey IN (SELECT key FROM own_accounts) THEN 1
            WHEN counterparty IS NOT NULL
             AND LOWER(TRIM(counterparty)) IN (SELECT name FROM own_account_names) THEN 1
            WHEN referenceKeys IS NOT NULL AND referenceKeys <> ''
             AND EXISTS (
                SELECT 1 FROM own_accounts
                WHERE transactions.referenceKeys LIKE '%|' || own_accounts.key || '|%'
             ) THEN 1
            ELSE 0 END
        """
    )
    suspend fun recomputeSelfTransfers(): Int

    /**
     * Files self-transfers still sitting in the review queue under "Transfers".
     * Scoped to `needsReview = 1` so a category the user picked deliberately is
     * never overwritten — this only clears the backlog they'd otherwise have to
     * sort by hand.
     */
    @Query(
        """
        UPDATE transactions SET category = :category, needsReview = 0
        WHERE selfTransfer = 1 AND needsReview = 1
        """
    )
    suspend fun fileSelfTransfers(category: String): Int

    /** Ids + counterparties for rows captured before `counterpartyKey` existed.
     *  Narrow projection so the one-off backfill stays cheap. */
    @Query("SELECT id, counterparty FROM transactions WHERE counterpartyKey IS NULL")
    suspend fun rowsMissingCounterpartyKey(): List<CounterpartyRow>

    /** Writes the canonical key ("" when the counterparty holds no number, so the
     *  backfill never re-visits the row). */
    @Query("UPDATE transactions SET counterpartyKey = :key WHERE id = :id")
    suspend fun setCounterpartyKey(id: Long, key: String)

    /** Ids + narrations for rows captured before `referenceKeys` existed. */
    @Query("SELECT id, referenceHint FROM transactions WHERE referenceKeys IS NULL")
    suspend fun rowsMissingReferenceKeys(): List<ReferenceHintRow>

    /** Writes the pipe-delimited key set ("" when the narration held no number,
     *  so the backfill never re-visits the row). */
    @Query("UPDATE transactions SET referenceKeys = :keys WHERE id = :id")
    suspend fun setReferenceKeys(id: Long, keys: String)

    /**
     * Self-transfer *amounts* are excluded outright — moving your own money isn't
     * spend in any category. Their fees are not: those are charges like any other
     * and land in the synthetic "Transaction Fees" row alongside the rest.
     *
     * Fees are pooled into that one row rather than added to the category that
     * incurred them. Charges are their own kind of outgoing — the question they
     * answer is "what is this costing me to move?", which is worth a line of its
     * own and would be invisible smeared a few pesewas at a time across Groceries
     * and Transport.
     */
    @Query(
        """
        SELECT label, SUM(totalMinor) AS totalMinor FROM (
            SELECT category AS label, SUM(amountMinor) AS totalMinor
            FROM transactions
            WHERE direction = 'DEBIT' AND category IS NOT NULL AND selfTransfer = 0
              AND occurredAt BETWEEN :from AND :to
            GROUP BY category
            UNION ALL
            SELECT 'Transaction Fees', SUM(COALESCE(feeMinor, 0))
            FROM transactions
            WHERE direction = 'DEBIT' AND COALESCE(feeMinor, 0) > 0
              AND occurredAt BETWEEN :from AND :to
        )
        GROUP BY label HAVING totalMinor > 0
        ORDER BY totalMinor DESC LIMIT :limit
        """
    )
    fun observeTopCategories(from: Long, to: Long, limit: Int): Flow<List<CategoryTotal>>

    /**
     * Every category for one direction in a window — the full breakdown behind
     * the dashboard's top five. Unlike [observeTopCategories] this keeps
     * uncategorised rows (as an empty label) rather than dropping them, because a
     * breakdown that silently omits part of the total doesn't add up.
     *
     * On the debit side that now holds exactly: category amounts plus the pooled
     * "Transaction Fees" row sum to the Money Out figure [observeTotal] reports,
     * self-transfer fees included. It did not before — the fees were in the total
     * and in no slice.
     *
     * The outer regroup is not redundant: it folds the fee row into a real
     * category should the user ever create one by the same name, rather than
     * listing the label twice.
     */
    @Query(
        """
        SELECT label, SUM(totalMinor) AS totalMinor, SUM(txnCount) AS txnCount FROM (
            SELECT IFNULL(category, '') AS label,
                   SUM(amountMinor) AS totalMinor,
                   COUNT(*) AS txnCount
            FROM transactions
            WHERE direction = :direction AND selfTransfer = 0
              AND occurredAt BETWEEN :from AND :to
            GROUP BY label
            UNION ALL
            SELECT 'Transaction Fees', SUM(COALESCE(feeMinor, 0)), COUNT(*)
            FROM transactions
            WHERE :direction = 'DEBIT' AND direction = 'DEBIT'
              AND COALESCE(feeMinor, 0) > 0
              AND occurredAt BETWEEN :from AND :to
        )
        GROUP BY label HAVING totalMinor > 0
        ORDER BY totalMinor DESC
        """
    )
    fun observeCategoryBreakdown(direction: String, from: Long, to: Long): Flow<List<CategorySlice>>

    /**
     * Per-day spend/receive buckets for the cash-flow timeline. Days are bucketed
     * in the device's local zone (`occurredAt` is epoch millis → seconds for
     * SQLite's date functions). Only days with activity are returned; the caller
     * fills the gaps.
     */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', occurredAt / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(CASE WHEN direction = 'DEBIT'  THEN
                   CASE WHEN selfTransfer = 1 THEN COALESCE(feeMinor, 0)
                        ELSE amountMinor + COALESCE(feeMinor, 0) END
                   ELSE 0 END), 0) AS spentMinor,
               COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN
                   CASE WHEN selfTransfer = 1 THEN 0 ELSE amountMinor END
                   ELSE 0 END), 0) AS receivedMinor
        FROM transactions
        WHERE occurredAt BETWEEN :from AND :to
        GROUP BY day ORDER BY day ASC
        """
    )
    fun observeDailyFlow(from: Long, to: Long): Flow<List<DayFlow>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE direction = 'DEBIT' AND selfTransfer = 0 AND occurredAt BETWEEN :from AND :to
        ORDER BY amountMinor DESC LIMIT :limit
        """
    )
    fun observeLargestExpenses(from: Long, to: Long, limit: Int): Flow<List<TransactionEntity>>
}

data class CategoryTotal(val label: String, val totalMinor: Long)

/** One slice of a full breakdown. [label] is empty for uncategorised rows. */
data class CategorySlice(val label: String, val totalMinor: Long, val txnCount: Int)

/** Narrow projection for the one-off `counterpartyKey` backfill. */
data class CounterpartyRow(val id: Long, val counterparty: String?)

/** Narrow projection for the one-off `referenceKeys` backfill. */
data class ReferenceHintRow(val id: Long, val referenceHint: String?)

@Dao
interface OwnAccountDao {
    @Query("SELECT * FROM own_accounts ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<OwnAccountEntity>>

    @Query("SELECT * FROM own_accounts ORDER BY addedAt ASC")
    suspend fun getAll(): List<OwnAccountEntity>

    @Query("SELECT key FROM own_accounts")
    suspend fun keys(): List<String>

    /** The existing row for this wallet, so re-adding it edits rather than
     *  duplicates. Scoped by provider — the same number under a *different*
     *  provider is a different wallet and must get its own row. */
    @Query("SELECT id FROM own_accounts WHERE key = :key AND institution = :institution LIMIT 1")
    suspend fun idFor(key: String, institution: String): Long?

    @Upsert
    suspend fun upsert(account: OwnAccountEntity): Long

    @Query("DELETE FROM own_accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM own_accounts")
    suspend fun clearAll()
}

@Dao
interface OwnAccountNameDao {
    @Query("SELECT * FROM own_account_names ORDER BY display ASC")
    fun observeAll(): Flow<List<OwnAccountNameEntity>>

    @Query("SELECT * FROM own_account_names ORDER BY display ASC")
    suspend fun getAll(): List<OwnAccountNameEntity>

    @Query("SELECT name FROM own_account_names")
    suspend fun names(): List<String>

    @Upsert
    suspend fun upsert(name: OwnAccountNameEntity)

    /** Replaces one account's whole alias set — the edit dialog hands back the
     *  full list, so removed names must actually disappear. */
    @Query("DELETE FROM own_account_names WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)

    @Query("DELETE FROM own_account_names")
    suspend fun clearAll()
}

/** One day's totals in the cash-flow timeline. `day` is `YYYY-MM-DD` (local). */
data class DayFlow(val day: String, val spentMinor: Long, val receivedMinor: Long)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    suspend fun all(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    /** Existing rule for a {counterparty, direction} pair, if any — used to
     *  dedup so confirming a second message from the same sender updates the
     *  rule instead of inserting a duplicate. EXACT (case-insensitive) match. */
    @Query("SELECT * FROM rules WHERE counterparty = :counterparty COLLATE NOCASE AND direction = :direction LIMIT 1")
    suspend fun findFor(counterparty: String, direction: String): RuleEntity?

    @Upsert
    suspend fun upsert(rule: RuleEntity): Long

    @Query("UPDATE rules SET lastMatchedAtMillis = :ts WHERE id = :id")
    suspend fun touchLastMatched(id: Long, ts: Long)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM rules")
    suspend fun clearAll()
}

@Dao
interface SenderDao {
    @Query("SELECT * FROM senders")
    fun observeAll(): Flow<List<SenderEntity>>

    @Query("SELECT * FROM senders WHERE liveCaptureEnabled = 1")
    suspend fun enabled(): List<SenderEntity>

    @Upsert
    suspend fun upsert(sender: SenderEntity)

    @Query("DELETE FROM senders WHERE institution = :institution")
    suspend fun delete(institution: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE enabled = 1 ORDER BY position ASC, name ASC")
    fun observeEnabled(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories")
    suspend fun maxPosition(): Int

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    /** One statement batch in one transaction — a drag-and-drop reorder renumbers
     *  most of the table at once, and row-at-a-time upserts would have the
     *  observers fire on every intermediate order. */
    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM categories")
    suspend fun clearAll()
}
