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

    /**
     * Duplicates are dropped by the unique dedupKey index. Returns -1 when the row
     * was a duplicate and wasn't inserted.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(txn: TransactionEntity): Long

    /**
     * The balance this provider last reported at or before [at], for the balance
     * check in the repository. Ties on time are broken by capture order, so the
     * result is always the same.
     */
    @Query(
        """
        SELECT balanceMinor, chainBreak, needsReview FROM transactions
        WHERE institution = :institution AND balanceMinor IS NOT NULL
          AND occurredAt <= :at
        ORDER BY occurredAt DESC, capturedAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun lastBalanceBefore(institution: String, at: Long): BalanceAnchor?

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

    @Query("SELECT * FROM transactions WHERE dedupKey = :key LIMIT 1")
    suspend fun findByDedupKey(key: String): TransactionEntity?

    @Query("DELETE FROM transactions WHERE dedupKey IN (:keys)")
    suspend fun deleteByDedupKeys(keys: List<String>)

    /**
     * Wallet rows whose cash entry is gone go back to "not moved", and back into
     * the review queue to be filed again.
     */
    @Query(
        """
        UPDATE transactions SET cashMove = 'KEPT', needsReview = 1
        WHERE dedupKey IN (:sourceKeys) AND cashMove = 'MOVED'
        """
    )
    suspend fun unmoveCash(sourceKeys: List<String>)

    /**
     * Marks every wallet row that has a cash entry as moved. A restore can bring
     * in the cash entry while skipping the wallet row as a duplicate, and an
     * unmarked wallet row would count the money twice.
     */
    @Query(
        """
        UPDATE transactions SET cashMove = 'MOVED', needsReview = 0
        WHERE cashMove IS NOT 'MOVED'
          AND 'CASH|' || dedupKey IN (SELECT dedupKey FROM transactions WHERE cashMove = 'PAIR')
        """
    )
    suspend fun linkMovedCash()

    /** Flags an existing row as a possible cash movement, unless it was already answered. */
    @Query(
        """
        UPDATE transactions SET cashMove = 'OFFER', needsReview = 1
        WHERE dedupKey = :key AND cashMove IS NULL AND selfTransfer = 0
        """
    )
    suspend fun offerCash(key: String)

    /** Cash on hand: everything that came into cash minus everything that left. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amountMinor ELSE -amountMinor END), 0)
        FROM transactions WHERE institution = 'CASH'
        """
    )
    fun observeCashBalance(): Flow<Long>

    /** [observeCashBalance], once. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amountMinor ELSE -amountMinor END), 0)
        FROM transactions WHERE institution = 'CASH'
        """
    )
    suspend fun cashBalance(): Long

    /** The cash ledger, newest first. */
    @Query("SELECT * FROM transactions WHERE institution = 'CASH' ORDER BY occurredAt DESC, id DESC")
    fun observeCash(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /**
     * Whether anything has ever been captured. Drives the first-run welcome, which
     * must not depend on the current month having activity.
     */
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY occurredAt DESC")
    fun observeReviewQueue(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    fun observeReviewCount(): Flow<Int>

    /** Queued rows dated before [before]: what "clear older" would release. */
    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1 AND occurredAt < :before")
    fun observeReviewCountBefore(before: Long): Flow<Int>

    /**
     * Takes rows out of the review queue without filing them: they stay in the
     * ledger, uncategorised, and any open cash question is answered "no".
     * [before] null clears the whole queue.
     */
    @Query(
        """
        UPDATE transactions
        SET needsReview = 0,
            cashMove = CASE WHEN cashMove = 'OFFER' THEN 'KEPT' ELSE cashMove END
        WHERE needsReview = 1 AND (:before IS NULL OR occurredAt < :before)
        """
    )
    suspend fun clearReview(before: Long?): Int

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /**
     * Filtered and sorted history. It all runs in SQL, so a large result is never
     * processed on the main thread. Every filter is optional. [sort] is "NEWEST",
     * "OLDEST" or "LARGEST".
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

    /** Providers that appear in the ledger, for the History filter chips. */
    @Query("SELECT DISTINCT institution FROM transactions ORDER BY institution ASC")
    fun observeInstitutions(): Flow<List<String>>

    /**
     * Applies a new rule to the rows still waiting in review for the same sender
     * and direction, so the user doesn't have to sort each past message by hand.
     * The counterparty match is exact but ignores case, the same as a live rule.
     */
    @Query(
        """
        UPDATE transactions
        SET category = :category, person = :person, needsReview = 0
        WHERE needsReview = 1
          AND direction = :direction
          AND counterparty = :counterparty COLLATE NOCASE
          AND cashMove IS NOT 'OFFER'
        """
    )
    suspend fun applyRuleToPending(
        counterparty: String,
        direction: String,
        category: String,
        person: String?,
    ): Int

    /**
     * Money in or money out for a window.
     *
     * A self-transfer moves money between the user's own accounts, so only its fee
     * counts, never the amount moved.
     *
     * Any other debit counts its amount plus its fee. The parser stores the two
     * separately, so `amountMinor` never includes the fee. Credits count the amount
     * alone, because receiving money isn't charged.
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
     * Re-marks every transaction against the current list of own accounts. Run
     * after the user adds or removes one, so the change also applies to history.
     *
     * A move to or from cash is a self-transfer because the user said so, whatever
     * the account list says. Any other cash row is real spending or income.
     *
     * Otherwise this follows the same three checks as
     * [com.ledger.domain.model.OwnAccountMatcher]: the counterparty is the number,
     * the counterparty is one of the account's names, or the number appears in the
     * narration. That keeps a row marked here in agreement with how it was marked
     * when captured. Name matching ignores the provider; the matcher explains why.
     */
    @Query(
        """
        UPDATE transactions
        SET selfTransfer = CASE
            WHEN cashMove IN ('MOVED', 'PAIR', 'OPENING') THEN 1
            WHEN institution = 'CASH' THEN 0
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
     * Files self-transfers still waiting in review under "Transfers". Only queued
     * rows are touched, so a category the user picked is never overwritten.
     */
    @Query(
        """
        UPDATE transactions SET category = :category, needsReview = 0
        WHERE selfTransfer = 1 AND needsReview = 1
        """
    )
    suspend fun fileSelfTransfers(category: String): Int

    /** Rows with no `counterpartyKey` yet, for the key backfill. */
    @Query("SELECT id, counterparty FROM transactions WHERE counterpartyKey IS NULL")
    suspend fun rowsMissingCounterpartyKey(): List<CounterpartyRow>

    /**
     * Writes the normalised account number. It is "" when the counterparty has no
     * number, so the backfill doesn't visit the row again.
     */
    @Query("UPDATE transactions SET counterpartyKey = :key WHERE id = :id")
    suspend fun setCounterpartyKey(id: Long, key: String)

    /** Rows with no `referenceKeys` yet, for the key backfill. */
    @Query("SELECT id, referenceHint FROM transactions WHERE referenceKeys IS NULL")
    suspend fun rowsMissingReferenceKeys(): List<ReferenceHintRow>

    /**
     * Writes the numbers found in the narration, separated by pipes. It is "" when
     * there were none, so the backfill doesn't visit the row again.
     */
    @Query("UPDATE transactions SET referenceKeys = :keys WHERE id = :id")
    suspend fun setReferenceKeys(id: Long, keys: String)

    /**
     * The dashboard's top categories.
     *
     * Self-transfer amounts are left out, since moving your own money isn't
     * spending. Their fees are included. All fees are grouped into one
     * "Transaction Fees" row instead of being added to each category, so the user
     * can see what moving money costs them.
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
     * Every category for one direction in a window: the full breakdown behind the
     * dashboard's top five. Unlike [observeTopCategories], uncategorised rows are
     * kept (with an empty label), so the slices add up to the total.
     *
     * On the money-out side, the categories plus the "Transaction Fees" row add up
     * to [observeTotal]. The outer GROUP BY merges the fee row into a real category
     * if the user ever creates one with the same name.
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
     * Money in and money out per day, for the cash-flow timeline. Days use the
     * device's time zone (`occurredAt` is epoch millis, converted to seconds for
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

/** A label in use that the category list is missing. See [CategoryDao.orphaned]. */
data class OrphanCategory(val name: String, val kind: String)

/** The row a new message's balance is checked against. See [TransactionDao.lastBalanceBefore]. */
data class BalanceAnchor(val balanceMinor: Long, val chainBreak: Boolean, val needsReview: Boolean) {
    /** A row whose own balance broke the chain, not yet confirmed or cleared. */
    val untrusted: Boolean get() = chainBreak && needsReview
}

data class CategoryTotal(val label: String, val totalMinor: Long)

/** One slice of a full breakdown. [label] is empty for uncategorised rows. */
data class CategorySlice(val label: String, val totalMinor: Long, val txnCount: Int)

/** Just the columns the `counterpartyKey` backfill needs. */
data class CounterpartyRow(val id: Long, val counterparty: String?)

/** Just the columns the `referenceKeys` backfill needs. */
data class ReferenceHintRow(val id: Long, val referenceHint: String?)

@Dao
interface OwnAccountDao {
    @Query("SELECT * FROM own_accounts ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<OwnAccountEntity>>

    @Query("SELECT * FROM own_accounts ORDER BY addedAt ASC")
    suspend fun getAll(): List<OwnAccountEntity>

    @Query("SELECT key FROM own_accounts")
    suspend fun keys(): List<String>

    /**
     * The existing row for this wallet, so adding it again edits it. The same
     * number with a different provider is a different wallet.
     */
    @Query("SELECT id FROM own_accounts WHERE key = :key AND institution = :institution LIMIT 1")
    suspend fun idFor(key: String, institution: String): Long?

    @Upsert
    suspend fun upsert(account: OwnAccountEntity): Long

    @Query("DELETE FROM own_accounts WHERE id = :id")
    suspend fun delete(id: Long)
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

    /** Clears an account's names before the edit dialog's full list is saved. */
    @Query("DELETE FROM own_account_names WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

/** One day's totals in the cash-flow timeline. `day` is `YYYY-MM-DD` (local). */
data class DayFlow(val day: String, val spentMinor: Long, val receivedMinor: Long)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    suspend fun all(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    /**
     * The existing rule for a counterparty and direction, if any, so confirming
     * another message from the same sender updates it instead of adding a second
     * one. Exact match, ignoring case.
     */
    @Query("SELECT * FROM rules WHERE counterparty = :counterparty COLLATE NOCASE AND direction = :direction LIMIT 1")
    suspend fun findFor(counterparty: String, direction: String): RuleEntity?

    @Upsert
    suspend fun upsert(rule: RuleEntity): Long

    @Query("UPDATE rules SET lastMatchedAtMillis = :ts WHERE id = :id")
    suspend fun touchLastMatched(id: Long, ts: Long)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)
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

    /**
     * Labels still used by a transaction, rule or budget that the category list no
     * longer has, with the side they were used on. The "Transaction Fees" row is
     * computed rather than stored, so it is left out.
     */
    @Query(
        """
        SELECT name,
               CASE WHEN MAX(isOut) = 1 AND MAX(isIn) = 1 THEN 'SHARED'
                    WHEN MAX(isIn) = 1 THEN 'IN' ELSE 'OUT' END AS kind
        FROM (
            SELECT category AS name, direction = 'DEBIT' AS isOut, direction = 'CREDIT' AS isIn
            FROM transactions WHERE category IS NOT NULL
            UNION ALL
            SELECT category, direction = 'DEBIT', direction = 'CREDIT' FROM rules
            UNION ALL
            SELECT category, 1, 0 FROM budgets
        )
        WHERE TRIM(name) <> '' AND name <> 'Transaction Fees'
          AND LOWER(name) NOT IN (SELECT LOWER(name) FROM categories)
        GROUP BY LOWER(name)
        ORDER BY name
        """
    )
    suspend fun orphaned(): List<OrphanCategory>

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    /**
     * Saves many rows in one transaction. A reorder renumbers most of the table,
     * and saving row by row would refresh the screen at every step.
     */
    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * The monthly limits, one row per budgeted category. The table is small, so it
 * is read whole and matched to the month's spending in Kotlin. That reuses the
 * spending query instead of repeating its date logic in SQL.
 */
@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY category ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY category ASC")
    suspend fun getAll(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE category = :category LIMIT 1")
    suspend fun find(category: String): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: String)
}
