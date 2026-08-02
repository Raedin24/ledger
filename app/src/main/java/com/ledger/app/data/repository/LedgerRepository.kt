package com.ledger.app.data.repository

import com.ledger.app.data.backup.BackupFile
import com.ledger.app.data.backup.LedgerTransfer
import com.ledger.app.data.backup.toEntity
import com.ledger.app.data.backup.toNameEntities
import com.ledger.app.data.db.CategoryDao
import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.CategoryKind
import com.ledger.app.data.db.CategoryOptions
import com.ledger.app.data.db.DefaultCategories
import com.ledger.app.data.db.OwnAccountDao
import com.ledger.app.data.db.OwnAccountEntity
import com.ledger.app.data.db.OwnAccountNameDao
import com.ledger.app.data.db.OwnAccountNameEntity
import com.ledger.app.data.db.RuleDao
import com.ledger.app.data.db.RuleEntity
import com.ledger.app.data.db.SenderDao
import com.ledger.app.data.db.TransactionDao
import com.ledger.app.data.db.TransactionEntity
import com.ledger.app.data.mapper.asString
import com.ledger.app.data.mapper.packReferenceKeys
import com.ledger.app.data.mapper.toDomain
import com.ledger.app.data.mapper.toEntity
import com.ledger.domain.categorization.CategorizationEngine
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
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single seam between the framework-free domain pipeline and Android storage.
 * The BroadcastReceiver hands raw SMS here; everything downstream (parse,
 * validate, dedup, categorize, persist) happens in this one auditable place.
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val parser: SmsParser,
    private val duplicateDetector: DuplicateDetector,
    private val categorizer: CategorizationEngine,
    private val transactionDao: TransactionDao,
    private val ruleDao: RuleDao,
    private val senderDao: SenderDao,
    private val categoryDao: CategoryDao,
    private val ownAccountDao: OwnAccountDao,
    private val ownAccountNameDao: OwnAccountNameDao,
) {

    sealed interface Ingested {
        data object Saved : Ingested
        data object Review : Ingested
        data object Duplicate : Ingested
        data object Ignored : Ingested
    }

    /**
     * Full capture path for one inbound SMS. The [sms] body is used transiently
     * and never persisted. Returns the outcome for optional user notification.
     */
    suspend fun ingest(sms: IncomingSms): Ingested {
        return when (val result = parser.parse(sms, enabledInstitutions())) {
            is ParseResult.Ignored -> Ingested.Ignored
            is ParseResult.Discarded -> Ingested.Ignored   // OTP / marketing — nothing stored
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
                if (persist(txn)) Ingested.Review else Ingested.Duplicate
            }
            is ParseResult.Parsed -> {
                val categorized = applyRules(result.transaction)
                if (persist(categorized)) {
                    if (categorized.needsReview) Ingested.Review else Ingested.Saved
                } else Ingested.Duplicate
            }
        }
    }

    /** Runs the categorization engine and touches the matched rule's timestamp. */
    private suspend fun applyRules(txn: Transaction): Transaction {
        val rules = ruleDao.all().map { it.toDomain() }
        return when (val out = categorizer.categorize(txn, rules, System.currentTimeMillis())) {
            is CategorizationEngine.Outcome.Categorized -> {
                ruleDao.touchLastMatched(out.ruleId, System.currentTimeMillis())
                out.transaction
            }
            is CategorizationEngine.Outcome.Review -> out.transaction
        }
    }

    /**
     * Inserts respecting the unique dedup key. Returns false if it was a duplicate.
     *
     * Transfers between the user's own accounts are flagged here, at the one
     * place every capture funnels through, so the dashboard can leave them out
     * of spending without the UI having to know the rule.
     */
    private suspend fun persist(txn: Transaction): Boolean {
        val key = duplicateDetector.keyFor(txn).asString()
        val isSelf = ownAccountMatcher().isOwn(txn.counterparty, txn.referenceHint)
        // A recognised self-transfer needs no decision from the user, so it is
        // filed straight away instead of joining the review queue.
        val settled = if (isSelf) {
            txn.copy(category = txn.category ?: TRANSFERS_CATEGORY, needsReview = false)
        } else txn
        // A break in the balance chain outranks the self-transfer shortcut: the
        // whole point is that a transaction claiming to be routine gets looked at.
        val checked = if (breaksBalanceChain(settled)) settled.copy(needsReview = true) else settled
        val rowId = transactionDao.insertIgnoringDuplicates(checked.toEntity(key, selfTransfer = isSelf))
        return rowId != -1L
    }

    /**
     * True when [txn]'s reported balance does not follow from the balance this
     * institution last reported.
     *
     * Sender IDs are unauthenticated and spoofable, so a message that parses
     * cleanly is not evidence it came from the provider. The running balance is:
     * an injected transaction has to guess the victim's current balance to land
     * without contradicting the chain, and a suppression attempt shows up the
     * same way, as arithmetic that no longer follows.
     *
     * A genuinely missed message — phone off, deleted SMS, an inbox import with
     * gaps — breaks the chain identically, and nothing can distinguish the two
     * from a single message. That is why this only routes to the review queue
     * and never rejects: the cost of a false positive is one confirmation tap,
     * the cost of a false negative is a silently corrupted ledger.
     */
    private suspend fun breaksBalanceChain(txn: Transaction): Boolean {
        val balanceAfter = txn.balanceAfter ?: return false
        val previous = transactionDao.lastBalanceBefore(
            institution = txn.institution.name,
            at = txn.occurredAt.toEpochMilli(),
        ) ?: return false          // nothing to chain against yet

        return !BalanceChain.follows(
            previousBalance = Money(previous),
            direction = txn.direction,
            amount = txn.amount,
            fee = txn.fee,
            balanceAfter = balanceAfter,
        )
    }

    // ---- The user's own accounts (self-transfer detection) ----

    fun observeOwnAccounts() = ownAccountDao.observeAll()

    /** Every registered alias, for showing each account's names in Settings. */
    fun observeOwnAccountNames() = ownAccountNameDao.observeAll()

    private suspend fun ownAccountMatcher(): OwnAccountMatcher =
        OwnAccountMatcher(ownAccountDao.keys().toSet(), ownAccountNameDao.names().toSet())

    /**
     * Registers one of the user's own numbers/accounts. Returns false when it
     * carries no usable account number. Adding one re-marks history, so past
     * internal transfers stop counting as spending immediately.
     *
     * [institution] records which wallet the number belongs to (display only).
     * [names] are the labels providers print for this account instead of its
     * number — the thing that makes an MTN "received from AMA OWUSU" alert
     * recognisable as the user's own money arriving.
     */
    suspend fun addOwnAccount(
        identifier: String,
        label: String,
        institution: String? = null,
        names: List<String> = emptyList(),
    ): Boolean {
        val key = AccountKey.of(identifier) ?: return false
        val provider = institution ?: OwnAccountEntity.ANY_PROVIDER
        // Re-adding the same wallet edits it; the same number under a different
        // provider is a different wallet and gets its own row. Resolving the id
        // first is what makes those two cases distinguishable — an upsert on the
        // number alone could only ever do the first.
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
        // The dialog hands back the account's whole alias set, so replace rather
        // than merge — otherwise a name removed there would live on in matching.
        ownAccountNameDao.deleteForAccount(id)
        names.forEach { raw ->
            val normalised = OwnAccountMatcher.normaliseName(raw) ?: return@forEach
            ownAccountNameDao.upsert(
                OwnAccountNameEntity(name = normalised, accountId = id, display = raw.trim()),
            )
        }
        refreshSelfTransfers()
        return true
    }

    suspend fun removeOwnAccount(id: Long) {
        ownAccountDao.delete(id)
        ownAccountNameDao.deleteForAccount(id)
        refreshSelfTransfers()
    }

    /**
     * Re-marks every transaction against the current own-account set. Rows
     * captured before a match column existed are backfilled first (a one-off,
     * after which neither key column is ever null), then the marking itself is a
     * single SQL UPDATE rather than a read-modify-write over the whole table.
     */
    suspend fun refreshSelfTransfers(): Int {
        transactionDao.rowsMissingCounterpartyKey().forEach { row ->
            transactionDao.setCounterpartyKey(row.id, AccountKey.of(row.counterparty).orEmpty())
        }
        transactionDao.rowsMissingReferenceKeys().forEach { row ->
            transactionDao.setReferenceKeys(row.id, packReferenceKeys(row.referenceHint))
        }
        val marked = transactionDao.recomputeSelfTransfers()
        // Moving your own money needs no decision from the user — file it rather
        // than leaving it in the review queue.
        transactionDao.fileSelfTransfers(TRANSFERS_CATEGORY)
        return marked
    }

    /**
     * Called when the user confirms a reviewed transaction. Updates the row and,
     * if requested, mints a {counterparty, direction} rule so future messages
     * from this sender auto-categorize.
     */
    data class ConfirmResult(val alsoCleared: Int)

    suspend fun confirmReview(
        entity: TransactionEntity,
        category: String,
        person: String?,
        note: String?,
        createRule: Boolean,
    ): ConfirmResult {
        // The confirmed transaction always gets the user's note (notes are
        // per-transaction; a rule never carries one).
        transactionDao.update(
            entity.copy(category = category, person = person, notes = note, needsReview = false)
        )

        var alsoCleared = 0
        if (createRule && !entity.counterparty.isNullOrBlank()) {
            // Dedup: reuse the existing {counterparty, direction} rule if one is
            // already present, so confirming a second message from the same sender
            // updates that rule instead of minting a duplicate.
            val existing = ruleDao.findFor(entity.counterparty, entity.direction)
            val rule = categorizer.proposeRule(existing?.id ?: 0L, entity.toDomain(), category, person)
            ruleDao.upsert(rule.toEntity().copy(id = existing?.id ?: 0L))

            // Retroactively clear the rest of the review backlog for this sender +
            // direction so repeated charges (data/airtime, etc.) don't have to be
            // sorted one by one.
            alsoCleared = transactionDao.applyRuleToPending(
                counterparty = entity.counterparty,
                direction = entity.direction,
                category = category,
                person = person,
            )
        }
        return ConfirmResult(alsoCleared = alsoCleared)
    }

    // ---- Read side (exposed as Flows to the ViewModels) ----

    fun observeTransaction(id: Long) = transactionDao.observeById(id)
    fun observeReviewQueue() = transactionDao.observeReviewQueue()
    fun observeReviewCount() = transactionDao.observeReviewCount()

    /** Total rows in the ledger — the first-run signal for the dashboard. */
    fun observeTransactionCount() = transactionDao.observeCount()
    fun observeRecent(limit: Int = 6) = transactionDao.observeRecent(limit)
    fun search(query: String) = transactionDao.search(query)

    /** SQL-side filtered + sorted history (all work off the main thread). */
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

    /** Institutions the ledger actually holds data from (History sender filter). */
    fun observeInstitutions() = transactionDao.observeInstitutions()
    fun observeTotal(direction: String, from: Long, to: Long) =
        transactionDao.observeTotal(direction, from, to)
    fun observeTopCategories(from: Long, to: Long, limit: Int = 5) =
        transactionDao.observeTopCategories(from, to, limit)

    /** Every category for one direction in a window — the dashboard's top five
     *  without the limit, and with uncategorised rows kept in. */
    fun observeCategoryBreakdown(direction: String, from: Long, to: Long) =
        transactionDao.observeCategoryBreakdown(direction, from, to)
    fun observeDailyFlow(from: Long, to: Long) = transactionDao.observeDailyFlow(from, to)
    fun observeLargestExpenses(from: Long, to: Long, limit: Int = 5) =
        transactionDao.observeLargestExpenses(from, to, limit)
    fun observeRules() = ruleDao.observeAll()
    fun observeSenders() = senderDao.observeAll()

    // ---- Transaction editing (the detail screen + bulk actions) ----

    /** Persists an edited transaction. Editing a category clears the review flag. */
    suspend fun updateTransaction(txn: TransactionEntity) = transactionDao.update(txn)

    suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)

    suspend fun deleteTransactions(ids: List<Long>) = transactionDao.deleteByIds(ids)

    /** Bulk-categorises the given transactions, clearing their review flag. */
    suspend fun setCategoryFor(ids: List<Long>, category: String) {
        transactionDao.getByIds(ids).forEach {
            transactionDao.update(it.copy(category = category, needsReview = false))
        }
    }

    // ---- Export / import (Phase 5) ----

    suspend fun transactionCount(): Int = transactionDao.count()

    suspend fun exportJson(): String = LedgerTransfer.toJson(
        transactions = transactionDao.getAll(),
        rules = ruleDao.all(),
        categories = categoryDao.getAll(),
        ownAccounts = ownAccountDao.getAll(),
        ownAccountNames = ownAccountNameDao.getAll(),
    )

    suspend fun exportCsv(): String = LedgerTransfer.toCsv(transactionDao.getAll())

    data class ImportResult(val added: Int, val skipped: Int)

    /**
     * Merges (or, when [replace], first wipes) a decoded backup into the DB.
     * Transactions are inserted respecting the unique dedup key, so re-importing
     * the same file is a safe no-op. Categories are added only when their name is
     * new; rules are only rebuilt on a full replace to avoid priority conflicts.
     */
    suspend fun importBackup(backup: BackupFile, replace: Boolean): ImportResult {
        if (replace) {
            transactionDao.clearAll()
            ruleDao.clearAll()
            categoryDao.clearAll()
            ownAccountDao.clearAll()
            ownAccountNameDao.clearAll()
        }
        var added = 0
        var skipped = 0
        backup.transactions.forEach { dto ->
            val rowId = transactionDao.insertIgnoringDuplicates(dto.toEntity())
            if (rowId != -1L) added++ else skipped++
        }
        backup.categories.forEach { dto ->
            if (categoryDao.findByName(dto.name) == null) categoryDao.upsert(dto.toEntity())
        }
        if (replace) backup.rules.forEach { ruleDao.upsert(it.toEntity()) }
        // Own accounts must land before the recompute below, or every restored
        // internal transfer would be marked as ordinary spending. Merged by key
        // on a non-replace import, so restoring alongside existing accounts
        // neither duplicates nor drops either side's aliases.
        backup.ownAccounts.forEach { dto ->
            val provider = dto.institution ?: OwnAccountEntity.ANY_PROVIDER
            val existingId = ownAccountDao.idFor(dto.key, provider)
            val id = ownAccountDao.upsert(dto.toEntity(existingId ?: 0))
                .let { rowId -> if (rowId == -1L) existingId!! else rowId }
            ownAccountNameDao.deleteForAccount(id)
            dto.toNameEntities(id).forEach { ownAccountNameDao.upsert(it) }
        }
        // Imported rows arrive without match keys — derive them and mark any that
        // land on one of the user's own accounts.
        refreshSelfTransfers()
        return ImportResult(added, skipped)
    }

    // ---- Rule management (the manual rule editor) ----

    /** Insert or update a rule. New rules pass id = 0 (Room auto-generates). */
    suspend fun saveRule(rule: RuleEntity): Long = ruleDao.upsert(rule)

    suspend fun deleteRule(id: Long) = ruleDao.delete(id)

    // ---- Category management (the category editor) ----

    fun observeCategories() = categoryDao.observeAll()

    /**
     * Enabled category names, in display order, already split by direction —
     * what the pickers show.
     *
     * One query feeds both lists: partitioning here rather than at each call site
     * means a Review screen with a card per queued transaction isn't re-filtering
     * the whole set on every recomposition.
     */
    fun observeCategoryOptions(): kotlinx.coroutines.flow.Flow<CategoryOptions> =
        categoryDao.observeEnabled().map { rows ->
            if (rows.isEmpty()) return@map CategoryOptions()   // pre-seed fallback
            CategoryOptions(
                spending = rows.filter { it.kind != CategoryKind.IN.name }.map { it.name },
                income = rows.filter { it.kind != CategoryKind.OUT.name }.map { it.name },
            )
        }

    /** Adds a category at the end of the list. No-op if the name already exists
     *  (case-insensitive). Returns true when a new category was created. */
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

    /** Moves a category to the other side of the ledger (or to both). */
    suspend fun setCategoryKind(category: CategoryEntity, kind: CategoryKind) {
        categoryDao.upsert(category.copy(kind = kind.name))
    }

    /** Swaps two categories' positions to reorder them (drives the accessibility
     *  move actions, which step a row one slot at a time). */
    suspend fun swapCategoryOrder(a: CategoryEntity, b: CategoryEntity) {
        categoryDao.upsert(a.copy(position = b.position))
        categoryDao.upsert(b.copy(position = a.position))
    }

    /**
     * Renumbers the whole table to match [ids] — how a drag-and-drop reorder is
     * committed.
     *
     * Takes the entire order rather than a from/to pair because a drag ends
     * having crossed any number of rows, and because renumbering densely from
     * zero is what keeps the stored positions meaning the same thing as the
     * order the editor shows. Rows already sitting at their new index are left
     * alone, so the common case (a row moved two slots) writes two rows, not
     * thirty.
     */
    suspend fun reorderCategories(ids: List<Long>) {
        val byId = categoryDao.getAll().associateBy { it.id }
        val moved = ids.mapIndexedNotNull { index, id ->
            byId[id]?.takeIf { it.position != index }?.copy(position = index)
        }
        if (moved.isNotEmpty()) categoryDao.upsertAll(moved)
    }

    /**
     * Applies the shortlist from setup: [keep] stays enabled and moves to the
     * front of its side of the ledger, everything else is switched off.
     *
     * Disabled rather than deleted, deliberately. The categories the user didn't
     * pick are still the sensible defaults, still carry their icon and colour,
     * and a single switch in the editor brings any of them back — whereas a
     * delete is unrecoverable and would orphan the label on any transaction
     * already filed under it.
     *
     * Ordering is the other half of the point: the picked ones are what Review
     * offers inline without opening the sheet, and that list is taken off the
     * front of this order.
     */
    suspend fun applyCategoryShortlist(keep: Set<String>) {
        val all = categoryDao.getAll()
        if (all.isEmpty()) return
        // Stable within each half: the seed order is already roughly
        // most-reached-for first, so preserving it beats anything we'd invent.
        val (picked, rest) = all.partition { it.name in keep }
        val ordered = CategoryKind.DISPLAY_ORDER.flatMap { kind ->
            val side = { row: CategoryEntity -> row.kind == kind.name }
            picked.filter(side) + rest.filter(side)
        }
        val rows = ordered.mapIndexed { index, row ->
            row.copy(position = index, enabled = row.name in keep)
        }.filter { updated ->
            all.first { it.id == updated.id }.let { it.position != updated.position || it.enabled != updated.enabled }
        }
        if (rows.isNotEmpty()) categoryDao.upsertAll(rows)
    }

    /** Seeds the default category set on first run (idempotent — a no-op once
     *  any category exists, so it also safely covers the v1 -> v2 migration). */
    suspend fun ensureCategoriesSeeded() {
        if (categoryDao.count() > 0) return
        categoryDao.insertAll(DefaultCategories.entities())
    }

    /** Institutions the user has enabled for live capture (empty = capture all). */
    suspend fun enabledInstitutions(): Set<Institution> =
        senderDao.enabled().mapNotNull { runCatching { Institution.valueOf(it.institution) }.getOrNull() }.toSet()

    // ---- Onboarding: "paste one message so the app can learn the format" ----

    data class Detection(
        val recognised: Boolean,
        val institution: Institution?,
        val preview: Transaction?,
    )

    /**
     * Runs the pasted sample through the parser against every known institution
     * and reports whether the strict template recognises it. Used by the
     * "Add a sender" onboarding step before enabling live capture.
     */
    fun detectSample(body: String, now: Instant = Instant.now()): Detection {
        for (inst in Institution.entries) {
            val senderId = inst.senderIds.first()
            when (val r = parser.parse(IncomingSms(senderId, body, now))) {
                is ParseResult.Parsed -> return Detection(true, inst, r.transaction)
                is ParseResult.NeedsAttention -> return Detection(false, inst, null)
                else -> Unit
            }
        }
        return Detection(false, null, null)
    }

    suspend fun enableSender(institution: Institution) {
        senderDao.upsert(
            com.ledger.app.data.db.SenderEntity(
                institution = institution.name,
                displayName = institution.displayName,
                liveCaptureEnabled = true,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    companion object {
        /** Where recognised own-account movements are filed. The one label that
         *  belongs to both directions — see [DefaultCategories]. */
        const val TRANSFERS_CATEGORY = DefaultCategories.TRANSFERS
    }
}
