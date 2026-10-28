package com.ledger.app.data.repository

import com.ledger.app.data.backup.BackupFile
import com.ledger.app.data.backup.LedgerTransfer
import com.ledger.app.data.backup.toEntity
import com.ledger.app.data.backup.toNameEntities
import com.ledger.app.data.budget.BudgetStatus
import com.ledger.app.data.db.BalanceAnchor
import com.ledger.app.data.db.BudgetDao
import com.ledger.app.data.db.BudgetEntity
import com.ledger.app.data.db.CashMove
import com.ledger.app.data.db.CashMove.answeredNo
import com.ledger.app.data.db.CategoryDao
import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.CategoryKind
import com.ledger.app.data.db.CategoryOptions
import com.ledger.app.data.db.DefaultCategories
import com.ledger.app.data.db.LedgerDatabase
import androidx.room.withTransaction
import com.ledger.app.data.db.OwnAccountDao
import com.ledger.app.data.db.OwnAccountEntity
import com.ledger.app.data.db.OwnAccountNameDao
import com.ledger.app.data.db.OwnAccountNameEntity
import com.ledger.app.data.db.RuleDao
import com.ledger.app.data.db.RuleEntity
import com.ledger.app.data.db.TransactionDao
import com.ledger.app.data.db.TransactionEntity
import com.ledger.app.data.mapper.asString
import com.ledger.app.data.mapper.packReferenceKeys
import com.ledger.app.data.mapper.toDomain
import com.ledger.app.data.mapper.toEntity
import com.ledger.domain.categorization.CategorizationEngine
import com.ledger.domain.categorization.Rule
import com.ledger.domain.dedup.DuplicateDetector
import com.ledger.domain.model.AccountKey
import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution
import com.ledger.domain.model.OwnAccountMatcher
import com.ledger.domain.model.Money
import com.ledger.domain.model.Transaction
import com.ledger.domain.validation.BalanceChain
import com.ledger.domain.parser.IncomingSms
import com.ledger.domain.parser.ParseResult
import com.ledger.domain.parser.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the domain pipeline to storage. Every captured SMS is parsed,
 * validated, deduplicated, categorised and saved here.
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val parser: SmsParser,
    private val duplicateDetector: DuplicateDetector,
    private val categorizer: CategorizationEngine,
    private val transactionDao: TransactionDao,
    private val ruleDao: RuleDao,
    private val categoryDao: CategoryDao,
    private val ownAccountDao: OwnAccountDao,
    private val ownAccountNameDao: OwnAccountNameDao,
    private val budgetDao: BudgetDao,
    private val database: LedgerDatabase,
) {

    /**
     * Every captured message is checked against the user's rules and their own
     * accounts. Both lists are cached here so an inbox import doesn't re-read
     * them for each message.
     *
     * A cache is cleared by the methods that change its data. Volatile is
     * enough: if two threads race, the worst case is loading the list twice.
     */
    @Volatile private var ruleCache: List<Rule>? = null

    @Volatile private var matcherCache: OwnAccountMatcher? = null

    private fun invalidateRules() { ruleCache = null }

    private fun invalidateOwnAccounts() { matcherCache = null }

    sealed interface Ingested {
        data object Saved : Ingested
        data object Review : Ingested
        data object Duplicate : Ingested
        data object Ignored : Ingested
    }

    /**
     * Captures one SMS. The body is used here and never stored.
     *
     * @param live true for a message that just arrived, false for the inbox import.
     *   Only live messages are held back by an unconfirmed balance break.
     */
    suspend fun ingest(sms: IncomingSms, live: Boolean = true): Ingested {
        return when (val result = parser.parse(sms)) {
            is ParseResult.Ignored -> Ingested.Ignored
            is ParseResult.Discarded -> Ingested.Ignored  // OTP or marketing, nothing stored
            is ParseResult.NeedsAttention -> {
                val partial = result.partial
                val txn = Transaction(
                    reference = partial.reference,
                    institution = result.institution,
                    direction = partial.direction ?: Direction.DEBIT,
                    amount = partial.amount ?: Money.ZERO,
                    balanceAfter = partial.balanceAfter,
                    fee = null,
                    currency = "GHS",
                    counterparty = partial.counterparty,
                    referenceHint = partial.referenceHint,
                    occurredAt = result.receivedAt,
                    capturedAt = result.receivedAt,
                    needsReview = true,
                )
                if (persist(txn, live)) Ingested.Review else Ingested.Duplicate
            }
            is ParseResult.Parsed -> {
                val categorized = applyRules(result.transaction)
                if (persist(categorized, live)) {
                    if (categorized.needsReview) Ingested.Review else Ingested.Saved
                } else {
                    // Already stored, but the message is a cash-out: ask about it.
                    if (categorized.cashMovement) {
                        transactionDao.offerCash(duplicateDetector.keyFor(categorized).asString())
                    }
                    Ingested.Duplicate
                }
            }
        }
    }

    /**
     * [ingest] for many messages in one transaction. A cancelled batch rolls back
     * whole; running the import again picks up where it stopped.
     */
    suspend fun ingestBatch(messages: List<IncomingSms>): List<Ingested> =
        try {
            database.withTransaction { messages.map { ingest(it, live = false) } }
        } catch (t: Throwable) {
            // applyRules updated rule timestamps in the cache as it went. The
            // rollback undid those writes, so reload the cache next time.
            invalidateRules()
            throw t
        }

    /** Runs the categorization engine and touches the matched rule's timestamp. */
    private suspend fun applyRules(txn: Transaction): Transaction {
        val rules = ruleCache ?: ruleDao.all().map { it.toDomain() }.also { ruleCache = it }
        val now = System.currentTimeMillis()
        return when (val out = categorizer.categorize(txn, rules, now)) {
            is CategorizationEngine.Outcome.Categorized -> {
                ruleDao.touchLastMatched(out.ruleId, now)
                // Update the cached rule instead of clearing the cache, which
                // would force a reload on almost every message of an import.
                ruleCache = rules.map {
                    if (it.id == out.ruleId) it.copy(lastMatchedAtMillis = now) else it
                }
                out.transaction
            }
            is CategorizationEngine.Outcome.Review -> out.transaction
        }
    }

    /**
     * Inserts unless the dedup key exists; returns false for a duplicate. Transfers
     * between the user's own accounts are flagged here.
     */
    private suspend fun persist(txn: Transaction, live: Boolean): Boolean {
        val key = duplicateDetector.keyFor(txn).asString()
        val isSelf = ownAccountMatcher().isOwn(txn.counterparty, txn.referenceHint)
        // A self-transfer is filed without review, and is never a cash movement:
        // moving it to cash would count it twice.
        val settled = if (isSelf) {
            txn.copy(category = txn.category ?: TRANSFERS_CATEGORY, needsReview = false, cashMovement = false)
        } else txn
        // Each alert reports the wallet's new balance, so consecutive alerts form
        // a chain. If this balance doesn't follow from the previous one, the
        // message goes to review, even when it looks like a self-transfer.
        //
        // A live message that follows an unconfirmed break is also held, until
        // the user confirms or clears the earlier row. The inbox import skips
        // this second check, because real inboxes often have gaps.
        val anchor = transactionDao.lastBalanceBefore(txn.institution.name, txn.occurredAt.toEpochMilli())
        val chainBreak = breaksBalanceChain(settled, anchor) || (live && anchor?.untrusted == true && settled.balanceAfter != null)
        val checked = if (chainBreak) settled.copy(needsReview = true) else settled
            // Cash-outs and cash-ins wait for the user to say whether they moved cash.
            .let { if (it.cashMovement && !isSelf) it.copy(needsReview = true) else it }
        val rowId = transactionDao.insertIgnoringDuplicates(
            checked.toEntity(key, selfTransfer = isSelf).copy(chainBreak = chainBreak),
        )
        return rowId != -1L
    }

    /**
     * True when [txn]'s balance doesn't follow from the last balance this
     * provider reported.
     *
     * Sender names on SMS can be faked, so a message that parses cleanly isn't
     * proof it came from the provider. A balance that doesn't add up is a sign
     * something is wrong. A missed message looks the same, though, so this only
     * sends the row to review and never rejects it.
     */
    private fun breaksBalanceChain(txn: Transaction, anchor: BalanceAnchor?): Boolean {
        val balanceAfter = txn.balanceAfter ?: return false
        val previous = anchor ?: return false  // nothing to compare with yet

        return !BalanceChain.follows(
            previousBalance = Money(previous.balanceMinor),
            direction = txn.direction,
            amount = txn.amount,
            fee = txn.fee,
            balanceAfter = balanceAfter,
        )
    }

    // ---- The user's own accounts ----

    fun observeOwnAccounts() = ownAccountDao.observeAll()

    fun observeOwnAccountNames() = ownAccountNameDao.observeAll()

    private suspend fun ownAccountMatcher(): OwnAccountMatcher =
        matcherCache ?: OwnAccountMatcher(
            ownAccountDao.keys().toSet(),
            ownAccountNameDao.names().toSet(),
        ).also { matcherCache = it }

    /**
     * Adds one of the user's own wallets and re-marks history, so past transfers
     * between them stop counting as spending. Returns false without a usable number.
     *
     * [names] are the labels providers print instead of the number, such as the
     * account holder's name on a "received from" alert.
     */
    suspend fun addOwnAccount(
        identifier: String,
        label: String,
        institution: String? = null,
        names: List<String> = emptyList(),
    ): Boolean {
        val key = AccountKey.of(identifier) ?: return false
        val provider = institution ?: OwnAccountEntity.ANY_PROVIDER
        // Same number and provider edits the wallet; another provider is a new wallet.
        val existingId = ownAccountDao.idFor(key, provider)
        val id = ownAccountDao.upsert(
            OwnAccountEntity(
                id = existingId ?: 0,
                key = key,
                identifier = identifier.trim(),
                label = label.trim().ifEmpty { "My wallet" },
                addedAt = System.currentTimeMillis(),
                institution = provider,
            )
        ).let { rowId -> if (rowId == -1L) existingId!! else rowId }
        // The dialog sends the full set of names, so replace rather than merge.
        ownAccountNameDao.deleteForAccount(id)
        names.forEach { raw ->
            val normalised = OwnAccountMatcher.normaliseName(raw) ?: return@forEach
            ownAccountNameDao.upsert(
                OwnAccountNameEntity(name = normalised, accountId = id, display = raw.trim()),
            )
        }
        invalidateOwnAccounts()
        refreshSelfTransfers()
        return true
    }

    suspend fun removeOwnAccount(id: Long) {
        ownAccountDao.delete(id)
        ownAccountNameDao.deleteForAccount(id)
        invalidateOwnAccounts()
        refreshSelfTransfers()
    }

    /**
     * Re-marks every transaction against the current own accounts, filling in
     * missing match keys first.
     */
    suspend fun refreshSelfTransfers(): Int {
        transactionDao.rowsMissingCounterpartyKey().forEach { row ->
            transactionDao.setCounterpartyKey(row.id, AccountKey.of(row.counterparty).orEmpty())
        }
        transactionDao.rowsMissingReferenceKeys().forEach { row ->
            transactionDao.setReferenceKeys(row.id, packReferenceKeys(row.referenceHint))
        }
        val marked = transactionDao.recomputeSelfTransfers()
        transactionDao.fileSelfTransfers(TRANSFERS_CATEGORY)
        return marked
    }

    /**
     * Confirms a reviewed transaction and, if asked, creates a rule for this
     * sender and direction.
     */
    data class ConfirmResult(val alsoCleared: Int)

    suspend fun confirmReview(
        entity: TransactionEntity,
        category: String,
        person: String?,
        note: String?,
        createRule: Boolean,
    ): ConfirmResult {
        // Notes belong to one transaction; rules never carry them.
        transactionDao.update(
            entity.copy(
                category = category, person = person, notes = note, needsReview = false,
                cashMove = entity.cashMove.answeredNo(),
            )
        )

        var alsoCleared = 0
        if (createRule && !entity.counterparty.isNullOrBlank()) {
            // Reuse the sender's existing rule rather than adding a second one.
            val existing = ruleDao.findFor(entity.counterparty, entity.direction)
            val rule = categorizer.proposeRule(existing?.id ?: 0L, entity.toDomain(), category, person)
            ruleDao.upsert(rule.toEntity().copy(id = existing?.id ?: 0L))
            invalidateRules()

            // File the rest of this sender's queue the same way.
            alsoCleared = transactionDao.applyRuleToPending(
                counterparty = entity.counterparty,
                direction = entity.direction,
                category = category,
                person = person,
            )
        }
        return ConfirmResult(alsoCleared = alsoCleared)
    }

    // ---- Reads ----

    fun observeTransaction(id: Long) = transactionDao.observeById(id)
    fun observeReviewQueue() = transactionDao.observeReviewQueue()
    fun observeReviewCount() = transactionDao.observeReviewCount()
    fun observeReviewCountBefore(before: Long) = transactionDao.observeReviewCountBefore(before)

    /**
     * Empties the review queue, or only the part dated before [before]. Nothing is
     * deleted; the rows stay in History and in the totals.
     */
    suspend fun clearReview(before: Long?): Int = transactionDao.clearReview(before)

    /** Total rows; zero means a first run. */
    fun observeTransactionCount() = transactionDao.observeCount()
    fun observeRecent(limit: Int = 6) = transactionDao.observeRecent(limit)

    fun filterHistory(
        query: String,
        direction: String?,
        category: String?,
        institution: String?,
        reviewOnly: Boolean,
        from: Long?,
        to: Long?,
        sort: String,
    ) = transactionDao.filter(query, direction, category, institution, reviewOnly, from, to, sort)

    /** Providers the ledger has data from, for the History filter. */
    fun observeInstitutions() = transactionDao.observeInstitutions()
    fun observeTotal(direction: String, from: Long, to: Long) =
        transactionDao.observeTotal(direction, from, to)
    fun observeTopCategories(from: Long, to: Long, limit: Int = 5) =
        transactionDao.observeTopCategories(from, to, limit)

    /** Every category for one direction in a window, uncategorised included. */
    fun observeCategoryBreakdown(direction: String, from: Long, to: Long) =
        transactionDao.observeCategoryBreakdown(direction, from, to)
    fun observeDailyFlow(from: Long, to: Long) = transactionDao.observeDailyFlow(from, to)
    fun observeLargestExpenses(from: Long, to: Long, limit: Int = 5) =
        transactionDao.observeLargestExpenses(from, to, limit)
    fun observeRules() = ruleDao.observeAll()

    // ---- Editing ----

    suspend fun updateTransaction(txn: TransactionEntity) = transactionDao.update(txn)

    suspend fun deleteTransaction(id: Long) = deleteTransactions(listOf(id))

    /**
     * Deletes rows. Deleting a moved withdrawal removes its cash entry, and
     * deleting the cash entry puts the withdrawal back.
     */
    suspend fun deleteTransactions(ids: List<Long>) {
        val rows = transactionDao.getByIds(ids)
        transactionDao.deleteByIds(ids)
        val mirrors = rows.filter { it.cashMove == CashMove.MOVED }.map { CashMove.pairKey(it.dedupKey) }
        if (mirrors.isNotEmpty()) transactionDao.deleteByDedupKeys(mirrors)
        val sources = rows.filter { it.cashMove == CashMove.PAIR }.map { CashMove.sourceKey(it.dedupKey) }
        if (sources.isNotEmpty()) {
            transactionDao.unmoveCash(sources)
            refreshSelfTransfers()
        }
    }

    // ---- Cash ----

    fun observeCashBalance() = transactionDao.observeCashBalance()

    fun observeCash() = transactionDao.observeCash()

    suspend fun addCashEntry(
        direction: Direction,
        amountMinor: Long,
        category: String?,
        description: String?,
        note: String?,
        occurredAt: Long,
    ) {
        require(amountMinor > 0) { "amount must be positive" }
        transactionDao.insertIgnoringDuplicates(
            cashRow(
                dedupKey = CashMove.manualKey(),
                direction = direction,
                amountMinor = amountMinor,
                counterparty = description?.trim()?.ifBlank { null },
                occurredAt = occurredAt,
                category = category,
            ).copy(notes = note?.trim()?.ifBlank { null }),
        )
    }

    /**
     * Moves a wallet withdrawal or deposit to cash: the wallet row becomes a
     * self-transfer and a matching cash entry is added. Safe to repeat.
     */
    suspend fun moveToCash(source: TransactionEntity) {
        if (source.institution == Institution.CASH.name || source.cashMove == CashMove.MOVED) return
        val wallet = runCatching { Institution.valueOf(source.institution).displayName }
            .getOrDefault(source.institution)
        transactionDao.insertIgnoringDuplicates(
            cashRow(
                dedupKey = CashMove.pairKey(source.dedupKey),
                direction = if (source.direction == Direction.DEBIT.name) Direction.CREDIT else Direction.DEBIT,
                amountMinor = source.amountMinor,
                counterparty = wallet,
                occurredAt = source.occurredAt,
                category = TRANSFERS_CATEGORY,
            ).copy(selfTransfer = true, cashMove = CashMove.PAIR),
        )
        transactionDao.update(
            source.copy(
                cashMove = CashMove.MOVED, selfTransfer = true,
                category = TRANSFERS_CATEGORY, needsReview = false,
            ),
        )
    }

    suspend fun cashBalance(): Long = transactionDao.cashBalance()

    /** Sets cash on hand by recording the difference as an adjustment. */
    suspend fun setCashOnHand(targetMinor: Long) {
        adjustCash(targetMinor - transactionDao.cashBalance())
    }

    /** Changes cash on hand without counting as income or spending. */
    suspend fun adjustCash(deltaMinor: Long) {
        if (deltaMinor == 0L) return
        transactionDao.insertIgnoringDuplicates(
            cashRow(
                dedupKey = CashMove.manualKey(),
                direction = if (deltaMinor > 0) Direction.CREDIT else Direction.DEBIT,
                amountMinor = kotlin.math.abs(deltaMinor),
                counterparty = CASH_ADJUSTMENT,
                occurredAt = System.currentTimeMillis(),
                category = null,
            ).copy(selfTransfer = true, cashMove = CashMove.OPENING),
        )
    }

    /** Money in minus money out before [before], for the savings carry-over. */
    fun observeSavingsBefore(before: Long): Flow<Long> = combine(
        transactionDao.observeTotal("CREDIT", 0, before - 1),
        transactionDao.observeTotal("DEBIT", 0, before - 1),
    ) { credit, debit -> credit - debit }

    /**
     * "Not cash": stop asking. A row that already has a category leaves the
     * queue; an unsorted one stays to be filed.
     */
    suspend fun keepOutOfCash(source: TransactionEntity) {
        if (source.cashMove != CashMove.OFFER) return
        transactionDao.update(
            source.copy(
                cashMove = CashMove.KEPT,
                needsReview = source.needsReview && source.category == null,
            ),
        )
    }

    /** Undoes [moveToCash]; the wallet row goes back to the queue. */
    suspend fun undoMoveToCash(source: TransactionEntity) {
        if (source.cashMove != CashMove.MOVED) return
        transactionDao.deleteByDedupKeys(listOf(CashMove.pairKey(source.dedupKey)))
        transactionDao.unmoveCash(listOf(source.dedupKey))
        refreshSelfTransfers()
    }

    private fun cashRow(
        dedupKey: String,
        direction: Direction,
        amountMinor: Long,
        counterparty: String?,
        occurredAt: Long,
        category: String?,
    ) = TransactionEntity(
        dedupKey = dedupKey,
        reference = null,
        institution = Institution.CASH.name,
        direction = direction.name,
        amountMinor = amountMinor,
        balanceMinor = null,
        feeMinor = null,
        currency = "GHS",
        counterparty = counterparty,
        referenceHint = null,
        occurredAt = occurredAt,
        capturedAt = System.currentTimeMillis(),
        category = category,
        person = null,
        notes = null,
        needsReview = false,
        // Cash has no account number, so it never matches one of the user's
        // wallets. It is empty rather than null so the key backfill skips it.
        counterpartyKey = "",
        referenceKeys = "",
    )

    suspend fun setCategoryFor(ids: List<Long>, category: String) {
        transactionDao.getByIds(ids).forEach {
            transactionDao.update(it.copy(category = category, needsReview = false, cashMove = it.cashMove.answeredNo()))
        }
    }

    // ---- Export and import ----

    suspend fun transactionCount(): Int = transactionDao.count()

    suspend fun exportJson(): String = LedgerTransfer.toJson(
        transactions = transactionDao.getAll(),
        rules = ruleDao.all(),
        categories = categoryDao.getAll(),
        ownAccounts = ownAccountDao.getAll(),
        ownAccountNames = ownAccountNameDao.getAll(),
        budgets = budgetDao.getAll(),
    )

    suspend fun exportCsv(): String = LedgerTransfer.toCsv(transactionDao.getAll())

    data class ImportResult(val added: Int, val skipped: Int)

    /**
     * Merges a backup into the ledger. Nothing already here is deleted.
     *
     * Transactions match on their dedup key; categories on name, rules on
     * counterparty and direction, wallets on number and provider. Where both sides
     * have a row, the device's copy wins. [ImportResult.skipped] counts rows that
     * were already here.
     */
    suspend fun importBackup(backup: BackupFile): ImportResult {
        val result = database.withTransaction { importBackupRows(backup) }
        // Clear the caches again now the restore is committed. A message captured
        // during the restore could have refilled them with the old data.
        invalidateRules()
        invalidateOwnAccounts()
        return result
    }

    /** The body of [importBackup], in one transaction so it lands whole or not at all. */
    private suspend fun importBackupRows(backup: BackupFile): ImportResult {
        var added = 0
        var skipped = 0
        backup.transactions.forEach { dto ->
            // Rows above the ceiling are skipped.
            val cap = Money.CEILING.minor
            if (dto.amountMinor !in 0..cap || (dto.balanceMinor ?: 0) > cap || (dto.feeMinor ?: 0) !in 0..cap) {
                skipped++
                return@forEach
            }
            val rowId = transactionDao.insertIgnoringDuplicates(dto.toEntity())
            if (rowId != -1L) added++ else skipped++
        }
        // An existing category is kept as is; the backup can only switch it back on.
        backup.categories.forEach { dto ->
            val existing = categoryDao.findByName(dto.name)
            if (existing == null) {
                categoryDao.upsert(dto.toEntity().copy(position = categoryDao.maxPosition() + 1))
            } else if (dto.enabled && !existing.enabled) {
                categoryDao.upsert(existing.copy(enabled = true))
            }
        }
        // Rules match on counterparty and direction, so none are duplicated.
        backup.rules.forEach { dto ->
            if (ruleDao.findFor(dto.counterparty, dto.direction) == null) {
                ruleDao.upsert(dto.toEntity())
            }
        }
        // Own accounts go in before the recompute below, or restored transfers would
        // count as spending.
        backup.ownAccounts.forEach { dto ->
            val provider = dto.institution ?: OwnAccountEntity.ANY_PROVIDER
            val existingId = ownAccountDao.idFor(dto.key, provider)
            val id = ownAccountDao.upsert(dto.toEntity(existingId ?: 0))
                .let { rowId -> if (rowId == -1L) existingId!! else rowId }
            // Only replace a wallet's names when the backup has some.
            val restored = dto.toNameEntities(id)
            if (restored.isNotEmpty()) {
                ownAccountNameDao.deleteForAccount(id)
                restored.forEach { ownAccountNameDao.upsert(it) }
            }
        }
        // The device's limit wins; the backup only fills in missing ones.
        backup.budgets.forEach { dto ->
            if (budgetDao.find(dto.category) == null) budgetDao.upsert(dto.toEntity())
        }
        // Imported rows have no match keys yet.
        invalidateRules()
        invalidateOwnAccounts()
        transactionDao.linkMovedCash()
        refreshSelfTransfers()
        repairCategories()
        return ImportResult(added, skipped)
    }

    // ---- Budgets ----

    fun observeBudgets() = budgetDao.observeAll()

    /**
     * Each budget against its spending in [from, to], using the dashboard's own
     * query so the two always agree. A budget with no spending shows at zero.
     */
    fun observeBudgetStatuses(from: Long, to: Long): Flow<List<BudgetStatus>> =
        combine(
            budgetDao.observeAll(),
            transactionDao.observeCategoryBreakdown("DEBIT", from, to),
        ) { budgets, slices ->
            val spentByCategory = slices.associate { it.label to it.totalMinor }
            budgets.map { budget ->
                BudgetStatus(
                    category = budget.category,
                    limitMinor = budget.limitMinor,
                    spentMinor = spentByCategory[budget.category] ?: 0L,
                )
            }.sortedByDescending { it.fraction }
        }

    /** One-shot read, for the alert check after a capture. */
    suspend fun budgetStatuses(from: Long, to: Long): List<BudgetStatus> =
        observeBudgetStatuses(from, to).first()

    /**
     * Sets a category's monthly limit. A limit of zero or less is ignored, not
     * treated as a removal; [removeBudget] does that.
     */
    suspend fun setBudget(category: String, limitMinor: Long) {
        if (limitMinor <= 0) return
        budgetDao.upsert(
            BudgetEntity(
                category = category,
                limitMinor = limitMinor,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeBudget(category: String) = budgetDao.delete(category)

    // ---- Rules ----

    /** Inserts or updates a rule; new rules pass id = 0. */
    suspend fun saveRule(rule: RuleEntity): Long = ruleDao.upsert(rule).also { invalidateRules() }

    suspend fun deleteRule(id: Long) = ruleDao.delete(id).also { invalidateRules() }

    // ---- Categories ----

    fun observeCategories() = categoryDao.observeAll()

    /** Enabled category names in display order, split by direction, for the pickers. */
    fun observeCategoryOptions(): Flow<CategoryOptions> =
        categoryDao.observeEnabled().map { rows ->
            if (rows.isEmpty()) return@map CategoryOptions()  // not seeded yet
            val out = rows.filter { it.kind != CategoryKind.IN.name }
            val income = rows.filter { it.kind != CategoryKind.OUT.name }
            CategoryOptions(
                spending = out.map { it.name },
                income = income.map { it.name },
                quickSpending = out.filter { it.favorite }.map { it.name },
                quickIncome = income.filter { it.favorite }.map { it.name },
            )
        }

    /** Adds a category at the end. Returns false if the name exists (any case). */
    suspend fun addCategory(name: String, kind: CategoryKind = CategoryKind.OUT): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || categoryDao.findByName(clean) != null) return false
        categoryDao.upsert(
            CategoryEntity(name = clean, position = categoryDao.maxPosition() + 1, kind = kind.name),
        )
        return true
    }

    suspend fun saveCategory(category: CategoryEntity) { categoryDao.upsert(category) }

    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    /** Moves a category to money in, money out, or both. */
    suspend fun setCategoryKind(category: CategoryEntity, kind: CategoryKind) {
        categoryDao.upsert(category.copy(kind = kind.name))
    }

    /** Swaps two categories, for the one-step accessibility move actions. */
    suspend fun swapCategoryOrder(a: CategoryEntity, b: CategoryEntity) {
        categoryDao.upsert(a.copy(position = b.position))
        categoryDao.upsert(b.copy(position = a.position))
    }

    /**
     * Saves a drag-and-drop reorder by renumbering to match [ids]. Rows already in
     * place are not rewritten.
     */
    suspend fun reorderCategories(ids: List<Long>) {
        val byId = categoryDao.getAll().associateBy { it.id }
        val moved = ids.mapIndexedNotNull { index, id ->
            byId[id]?.takeIf { it.position != index }?.copy(position = index)
        }
        if (moved.isNotEmpty()) categoryDao.upsertAll(moved)
    }

    /**
     * Applies the setup shortlist: [top] become favourites and move to the front
     * of their side. Nothing is switched off; the rest stay behind "More". A
     * hidden category that is picked is shown again.
     */
    suspend fun applyCategoryFavourites(top: Set<String>) {
        val all = categoryDao.getAll()
        if (all.isEmpty()) return
        // Keeps the seed order within each half.
        val (picked, rest) = all.partition { it.name in top }
        val ordered = CategoryKind.DISPLAY_ORDER.flatMap { kind ->
            val side = { row: CategoryEntity -> row.kind == kind.name }
            picked.filter(side) + rest.filter(side)
        }
        val rows = ordered.mapIndexed { index, row ->
            val isTop = row.name in top
            row.copy(position = index, favorite = isTop, enabled = row.enabled || isTop)
        }.filter { updated -> all.first { it.id == updated.id } != updated }
        if (rows.isNotEmpty()) categoryDao.upsertAll(rows)
    }

    /** Adds or removes one favourite, from the editor. */
    suspend fun setCategoryFavorite(category: CategoryEntity, favorite: Boolean) {
        // A favourite can't be hidden.
        categoryDao.upsert(
            category.copy(favorite = favorite, enabled = category.enabled || favorite),
        )
    }

    /**
     * Adds back labels still used by transactions, rules or budgets but missing
     * from the category list. Run at start-up and after a restore.
     */
    suspend fun repairCategories(): Int {
        val orphans = categoryDao.orphaned()
        if (orphans.isEmpty()) return 0
        val first = categoryDao.maxPosition() + 1
        categoryDao.insertAll(
            orphans.mapIndexed { i, o -> CategoryEntity(name = o.name, position = first + i, kind = o.kind) },
        )
        return orphans.size
    }

    /** Adds back any missing default categories at the end. Returns how many. */
    suspend fun restoreDefaultCategories(): Int {
        val have = categoryDao.getAll().map { it.name.lowercase() }.toSet()
        val missing = DefaultCategories.ALL.filter { it.name.lowercase() !in have }
        if (missing.isEmpty()) return 0
        val first = categoryDao.maxPosition() + 1
        categoryDao.insertAll(
            missing.mapIndexed { i, c -> CategoryEntity(name = c.name, position = first + i, kind = c.kind.name) },
        )
        return missing.size
    }

    /**
     * Adds the defaults introduced after version [since] (see
     * [DefaultCategories.ADDED]) at the end of the list, skipping any name the
     * ledger already has. Returns how many were added.
     */
    suspend fun addNewDefaultCategories(since: Int): Int {
        val names = DefaultCategories.ADDED.filterKeys { it > since }.values.flatten()
        val have = categoryDao.getAll().map { it.name.lowercase() }.toSet()
        val missing = DefaultCategories.ALL.filter { it.name in names && it.name.lowercase() !in have }
        if (missing.isEmpty()) return 0
        val first = categoryDao.maxPosition() + 1
        categoryDao.insertAll(
            missing.mapIndexed { i, c -> CategoryEntity(name = c.name, position = first + i, kind = c.kind.name) },
        )
        return missing.size
    }

    /** Seeds the default categories when there are none. */
    suspend fun ensureCategoriesSeeded() {
        if (categoryDao.count() > 0) return
        categoryDao.insertAll(DefaultCategories.entities())
    }

    companion object {
        /** Where own-account transfers are filed; used for both directions. */
        const val TRANSFERS_CATEGORY = DefaultCategories.TRANSFERS

        /** The name of a cash-on-hand adjustment. */
        const val CASH_ADJUSTMENT = "Cash on hand"
    }
}
