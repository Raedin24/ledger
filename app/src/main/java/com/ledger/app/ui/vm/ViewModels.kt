package com.ledger.app.ui.vm

import android.content.Context
import android.net.Uri
import com.ledger.app.data.backup.BackupCrypto
import com.ledger.app.data.backup.LedgerTransfer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.budget.BudgetAlerter
import com.ledger.app.data.budget.BudgetStatus
import com.ledger.app.data.db.CashMove.answeredNo
import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.CategoryKind
import com.ledger.app.data.db.CategoryOptions
import com.ledger.app.data.db.CategorySlice
import com.ledger.app.data.db.CategoryTotal
import com.ledger.app.data.db.DayFlow
import com.ledger.app.data.db.DefaultCategories
import com.ledger.app.data.prefs.PeriodPrefs
import com.ledger.app.data.prefs.SetupPrefs
import com.ledger.app.data.prefs.bounds
import com.ledger.app.data.prefs.label
import com.ledger.app.data.db.OwnAccountEntity
import com.ledger.app.data.db.RuleEntity
import com.ledger.app.data.db.TransactionEntity
import com.ledger.app.data.repository.LedgerRepository
import com.ledger.app.security.AppLockManager
import com.ledger.app.security.LockMode
import com.ledger.app.ui.components.institutionLabel
import com.ledger.app.tutorial.TutorialManager
import com.ledger.app.tutorial.TutorialState
import com.ledger.app.tutorial.TutorialStep
import com.ledger.domain.model.Direction
import com.ledger.domain.model.FiscalMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The user's current month and the dates it covers. The month either follows
 * the calendar or starts on a day the user chose (see [FiscalMonth]).
 */
private data class PeriodWindow(val fiscal: FiscalMonth, val start: LocalDate) {
    val bounds: Pair<Long, Long> get() = fiscal.bounds(start)
    val previous: PeriodWindow get() = PeriodWindow(fiscal, fiscal.shift(start, -1))
    val label: String get() = fiscal.label(start)
}

/**
 * The period containing today, worked out again whenever the start day changes.
 *
 * Collect this once per subscription, not once per ViewModel. A ViewModel can
 * live past the end of a month, and it would then keep showing last month's
 * figures under this month's heading.
 */
private fun PeriodPrefs.current(): Flow<PeriodWindow> = startDay.map { day ->
    val fiscal = FiscalMonth(day)
    PeriodWindow(fiscal, fiscal.startOf(LocalDate.now(ZoneId.systemDefault())))
}

data class OverviewState(
    val loaded: Boolean = false,
    /** True once the ledger holds at least one transaction, ever. */
    val hasAnyTransaction: Boolean = false,
    val spentMinor: Long = 0,
    val receivedMinor: Long = 0,
    val lastMonthSpentMinor: Long = 0,
    val avgDailyMinor: Long = 0,
    val reviewCount: Int = 0,
    /** One value per day of the whole month (index 0 is the first day), for the timeline. */
    val dailySpendSeries: List<Long> = emptyList(),
    /** How many of those days have happened, so the rest can be drawn as pending. */
    val elapsedDays: Int = 0,
    val topCategories: List<CategoryTotal> = emptyList(),
    val largestExpenses: List<TransactionEntity> = emptyList(),
    val recent: List<TransactionEntity> = emptyList(),
    /** This month's limits, worst first. Empty until the user sets one. */
    val budgets: List<BudgetStatus> = emptyList(),
    /** Cash on hand, from the cash ledger. */
    val cashMinor: Long = 0,
    /** The period on screen, such as "September 2026" or "28 Aug - 27 Sep". */
    val periodLabel: String = "",
    /** Savings left over from earlier months when carry-over is on, otherwise null. */
    val carriedMinor: Long? = null,
    /** A dashboard query failed. The screen shows what it can and says so. */
    val loadFailed: Boolean = false,
) {
    val savingsMinor: Long get() = receivedMinor - spentMinor

    /**
     * True when the database has been read and nothing has ever been captured.
     * This shows the first-run welcome. It checks the whole ledger, not this
     * month, so a quiet month still shows the dashboard.
     */
    val isFirstRun: Boolean
        get() = loaded && !hasAnyTransaction

    /**
     * Percent change in spending compared with the same days last month. Positive
     * means spending more. Null when there is no earlier month to compare with.
     */
    val spendChangePct: Int?
        get() = if (lastMonthSpentMinor <= 0) null
        else ((spentMinor - lastMonthSpentMinor) * 100.0 / lastMonthSpentMinor).roundToInt()
}

private data class OverviewCore(
    val spent: Long, val received: Long,
    val top: List<CategoryTotal>, val recent: List<TransactionEntity>,
)

private data class OverviewExtras(
    val lastSpent: Long, val daily: List<DayFlow>,
    val largest: List<TransactionEntity>, val reviewCount: Int,
    val totalCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val period: PeriodPrefs,
) : ViewModel() {

    private fun core(from: Long, to: Long) = combine(
        repo.observeTotal("DEBIT", from, to),
        repo.observeTotal("CREDIT", from, to),
        repo.observeTopCategories(from, to),
        repo.observeRecent(6),
    ) { spent, received, top, recent -> OverviewCore(spent, received, top, recent) }

    private fun extras(window: PeriodWindow): Flow<OverviewExtras> {
        val (from, to) = window.bounds
        val (lastFrom, lastTo) = window.previous.bounds
        return combine(
            repo.observeTotal("DEBIT", lastFrom, lastTo),
            repo.observeDailyFlow(from, to),
            repo.observeLargestExpenses(from, to),
            repo.observeReviewCount(),
            repo.observeTransactionCount(),
        ) { lastSpent, daily, largest, reviewCount, totalCount ->
            OverviewExtras(lastSpent, daily, largest, reviewCount, totalCount)
        }
    }

    /**
     * Nine database queries feed the dashboard, and every insert refreshes all of
     * them. During an inbox import that would redraw the screen hundreds of times.
     *
     * The debounce waits for a burst of updates to settle, then emits once.
     * `flowOn` moves the work below off the main thread, because `stateIn` collects
     * on the main thread by default.
     */
    val state: StateFlow<OverviewState> = period.current().flatMapLatest { window ->
        val (from, to) = window.bounds
        combine(
            core(from, to),
            extras(window),
            repo.observeBudgetStatuses(from, to),
            repo.observeCashBalance(),
            period.carryOver.flatMapLatest { on ->
                if (on) repo.observeSavingsBefore(from).map<Long, Long?> { it } else flowOf(null)
            },
        ) { c, e, budgets, cash, carried ->
            val today = LocalDate.now(ZoneId.systemDefault())
            val first = window.start.toEpochDay()
            val daysElapsed = (today.toEpochDay() - first + 1).toInt()
            // The series always covers the whole period, so the bars keep the same
            // width all month and the days still to come show as empty.
            val daysInPeriod = window.fiscal.lengthOf(window.start)
            // Keyed by days since the period started, not by day of month, because a
            // period that starts on the 28th crosses into the next calendar month.
            val spentByOffset = e.daily.associate {
                (LocalDate.parse(it.day).toEpochDay() - first).toInt() to it.spentMinor
            }
            OverviewState(
                loaded = true,
                hasAnyTransaction = e.totalCount > 0,
                spentMinor = c.spent,
                receivedMinor = c.received,
                lastMonthSpentMinor = e.lastSpent,
                avgDailyMinor = c.spent / daysElapsed.coerceAtLeast(1),
                reviewCount = e.reviewCount,
                dailySpendSeries = (0 until daysInPeriod).map { spentByOffset[it] ?: 0L },
                elapsedDays = daysElapsed,
                topCategories = c.top,
                largestExpenses = e.largest,
                recent = c.recent,
                budgets = budgets,
                cashMinor = cash,
                periodLabel = window.label,
                carriedMinor = carried,
            )
        }
    }
        .debounce(DASHBOARD_SETTLE_MS)
        .flowOn(Dispatchers.Default)
        // The dashboard is the start screen. If a query fails, show what we can
        // instead of crashing, so History and Data stay reachable.
        .catch { emit(OverviewState(loaded = true, hasAnyTransaction = true, loadFailed = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverviewState())

    private companion object {
        /** Long enough to swallow an import burst, short enough to feel live. */
        const val DASHBOARD_SETTLE_MS = 120L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val period: PeriodPrefs,
) : ViewModel() {

    /** How many queued rows are older than the current month. */
    val olderCount: StateFlow<Int> =
        period.current().flatMapLatest { window -> repo.observeReviewCountBefore(window.bounds.first) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** @param olderOnly true clears only rows before this month; false, everything. */
    fun clearQueue(olderOnly: Boolean) = viewModelScope.launch {
        val before = if (olderOnly) {
            val fiscal = period.fiscal()
            fiscal.bounds(fiscal.startOf(LocalDate.now(ZoneId.systemDefault()))).first
        } else null
        val n = repo.clearReview(before)
        _cleared.value = "Cleared $n from the queue · they stay in History"
    }

    /** Separate from [message], which the setup guide reads as "a rule was made". */
    private val _cleared = MutableStateFlow<String?>(null)
    val cleared: StateFlow<String?> = _cleared.asStateFlow()
    fun clearClearedMessage() { _cleared.value = null }

    /**
     * The review queue. Null until the first read arrives, so the screen can tell
     * "not loaded yet" apart from "empty" and doesn't flash "All caught up".
     */
    val queue: StateFlow<List<TransactionEntity>?> =
        repo.observeReviewQueue()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Enabled categories for the quick-pick chips, split by direction so money
     * coming in is never offered "Rent". Uses the defaults until the seed runs.
     */
    val categories: StateFlow<CategoryOptions> =
        repo.observeCategoryOptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryOptions())

    /** A short confirmation after a rule sorts other queued rows ("Also sorted N"). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun confirm(
        entity: TransactionEntity,
        category: String,
        person: String?,
        note: String?,
        createRule: Boolean,
    ) = viewModelScope.launch {
        val result = repo.confirmReview(entity, category, person, note, createRule)
        if (result.alsoCleared > 0) {
            _message.value =
                "Rule created · also sorted ${result.alsoCleared} more from this sender"
        }
    }

    fun moveToCash(entity: TransactionEntity) = viewModelScope.launch { repo.moveToCash(entity) }
    fun keepOutOfCash(entity: TransactionEntity) = viewModelScope.launch { repo.keepOutOfCash(entity) }
}

enum class SortOrder(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), LARGEST("Largest") }

/**
 * The History date window. The default is a recent slice, so the list never
 * loads the whole ledger at once. [CUSTOM] comes from the date range picker.
 */
enum class DatePreset(val label: String) {
    THIS_MONTH("This month"),
    LAST_90_DAYS("Last 90 days"),
    THIS_YEAR("This year"),
    ALL_TIME("All time"),
    CUSTOM("Custom"),
}

/** A sender the ledger holds data from, for the History filter chips. */
data class SenderOption(val institution: String, val label: String)

data class HistoryFilters(
    val query: String = "",
    val direction: String? = null,  // "DEBIT", "CREDIT", or null for both
    val category: String? = null,
    val institution: String? = null,  // Institution.name, or null for all
    val reviewOnly: Boolean = false,
    val sort: SortOrder = SortOrder.NEWEST,
    val datePreset: DatePreset = DatePreset.LAST_90_DAYS,
    val customFrom: Long? = null,  // epoch millis, inclusive (CUSTOM only)
    val customTo: Long? = null,  // epoch millis, inclusive (CUSTOM only)
) {
    val anyActive: Boolean
        get() = direction != null || category != null || institution != null ||
            reviewOnly || datePreset != DatePreset.LAST_90_DAYS || sort != SortOrder.NEWEST

    /**
     * How many filters are set behind the filter button, for its badge. The date
     * window and the search box are on the screen itself, so they don't count.
     */
    val activeCount: Int
        get() = listOf(direction != null, category != null, institution != null, reviewOnly)
            .count { it } + if (sort != SortOrder.NEWEST) 1 else 0

    /**
     * The start and end of the current window in epoch millis. Either can be null,
     * meaning no limit on that side. "This month" means the user's month, which
     * may start on a day other than the 1st.
     */
    fun bounds(fiscal: FiscalMonth): Pair<Long?, Long?> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        fun endOf(d: LocalDate) = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return when (datePreset) {
            DatePreset.THIS_MONTH -> startOf(fiscal.startOf(today)) to endOf(today)
            DatePreset.LAST_90_DAYS -> startOf(today.minusDays(89)) to endOf(today)
            DatePreset.THIS_YEAR -> startOf(today.withDayOfYear(1)) to endOf(today)
            DatePreset.ALL_TIME -> null to null
            DatePreset.CUSTOM -> customFrom to customTo
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val period: PeriodPrefs,
) : ViewModel() {
    private val _filters = MutableStateFlow(HistoryFilters())
    val filters: StateFlow<HistoryFilters> = _filters.asStateFlow()

    val categories: StateFlow<CategoryOptions> =
        repo.observeCategoryOptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryOptions())

    /** Providers in the ledger, with readable labels for the filter chips. */
    val senders: StateFlow<List<SenderOption>> =
        repo.observeInstitutions()
            .map { names -> names.map { SenderOption(it, institutionLabel(it)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtering and sorting run in SQL, off the main thread, and the date window
    // limits how much is read. Null until the first result arrives, so the screen
    // shows a placeholder instead of briefly saying there are no transactions.
    val results: StateFlow<List<TransactionEntity>?> =
        combine(_filters, period.startDay) { f, day -> f to FiscalMonth(day) }
            // Wait for typing to pause before searching. Filter chips apply at once,
            // since there is no typing to wait for.
            .debounce { (f, _) -> if (f.query.isBlank()) 0L else SEARCH_SETTLE_MS }
            .flatMapLatest { (f, fiscal) ->
                val (from, to) = f.bounds(fiscal)
                repo.filterHistory(
                    query = f.query.trim(),
                    direction = f.direction,
                    category = f.category,
                    institution = f.institution,
                    reviewOnly = f.reviewOnly,
                    from = from,
                    to = to,
                    sort = f.sort.name,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onQueryChange(value: String) { _filters.value = _filters.value.copy(query = value) }
    fun toggleDirection(dir: String) {
        _filters.value = _filters.value.copy(direction = _filters.value.direction.toggle(dir))
        clearSelection()
    }
    fun toggleCategory(cat: String) {
        _filters.value = _filters.value.copy(category = _filters.value.category.toggle(cat))
        clearSelection()
    }
    fun toggleInstitution(inst: String) {
        _filters.value = _filters.value.copy(institution = _filters.value.institution.toggle(inst))
        clearSelection()
    }
    fun toggleReviewOnly() {
        _filters.value = _filters.value.copy(reviewOnly = !_filters.value.reviewOnly)
        clearSelection()
    }
    fun setSort(sort: SortOrder) { _filters.value = _filters.value.copy(sort = sort) }

    /** Applies a preset window. [CUSTOM] is set via [setCustomRange] instead. */
    fun setDatePreset(preset: DatePreset) {
        if (preset == DatePreset.CUSTOM) return
        _filters.value = _filters.value.copy(datePreset = preset, customFrom = null, customTo = null)
        clearSelection()
    }

    /** Sets an explicit calendar range (inclusive day bounds). */
    fun setCustomRange(fromMillis: Long, toMillis: Long) {
        val zone = ZoneId.systemDefault()
        val from = java.time.Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val to = java.time.Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        _filters.value = _filters.value.copy(datePreset = DatePreset.CUSTOM, customFrom = from, customTo = to)
        clearSelection()
    }

    fun clearFilters() { _filters.value = HistoryFilters(query = _filters.value.query) }

    private fun String?.toggle(value: String): String? = if (this == value) null else value

    // ---- Bulk selection ----
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    fun toggleSelected(id: Long) {
        val cur = _selection.value
        _selection.value = if (id in cur) cur - id else cur + id
    }
    fun clearSelection() { _selection.value = emptySet() }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _selection.value.toList()
        if (ids.isNotEmpty()) repo.deleteTransactions(ids)
        _selection.value = emptySet()
    }
    fun categoriseSelected(category: String) = viewModelScope.launch {
        val ids = _selection.value.toList()
        if (ids.isNotEmpty()) repo.setCategoryFor(ids, category)
        _selection.value = emptySet()
    }

    private companion object {
        /** Long enough to wait out a word being typed, short enough to feel responsive. */
        const val SEARCH_SETTLE_MS = 220L
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: LedgerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /**
     * The full screen reads the id from its route. The sheet has no route
     * argument, so it sets the id with [bind].
     */
    private val id = MutableStateFlow(savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1L)

    fun bind(transactionId: Long) { id.value = transactionId }

    val transaction: StateFlow<TransactionEntity?> =
        id.flatMapLatest { repo.observeTransaction(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<CategoryOptions> =
        repo.observeCategoryOptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryOptions())

    /** Filing a transaction also answers an open cash question with "no". */
    fun setCategory(category: String) = edit {
        it.copy(category = category, needsReview = false, cashMove = it.cashMove.answeredNo())
    }
    fun setPerson(person: String?) = edit { it.copy(person = person?.trim()?.ifBlank { null }) }
    fun setNote(note: String?) = edit { it.copy(notes = note?.trim()?.ifBlank { null }) }

    private fun edit(transform: (TransactionEntity) -> TransactionEntity) = viewModelScope.launch {
        transaction.value?.let { repo.updateTransaction(transform(it)) }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteTransaction(id.value)
        onDone()
    }

    fun moveToCash() = viewModelScope.launch { transaction.value?.let { repo.moveToCash(it) } }
    fun keepOutOfCash() = viewModelScope.launch { transaction.value?.let { repo.keepOutOfCash(it) } }
    fun undoMoveToCash() = viewModelScope.launch { transaction.value?.let { repo.undoMoveToCash(it) } }
}

/** The day the user's month starts on (Settings). */
@HiltViewModel
class PeriodViewModel @Inject constructor(private val period: PeriodPrefs) : ViewModel() {
    val startDay: StateFlow<Int> = period.startDay
    val carryOver: StateFlow<Boolean> = period.carryOver
    fun setStartDay(day: Int) = period.setStartDay(day)
    fun setCarryOver(on: Boolean) = period.setCarryOver(on)
}

data class CashState(
    val loaded: Boolean = false,
    val balanceMinor: Long = 0,
    val entries: List<TransactionEntity> = emptyList(),
)

/** The cash ledger and its hand-entry form. */
@HiltViewModel
class CashViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val budgetAlerter: BudgetAlerter,
) : ViewModel() {

    val state: StateFlow<CashState> =
        combine(repo.observeCashBalance(), repo.observeCash()) { balance, entries ->
            CashState(loaded = true, balanceMinor = balance, entries = entries)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashState())

    val categories: StateFlow<CategoryOptions> =
        repo.observeCategoryOptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryOptions())

    fun setOnHand(targetMinor: Long, onSaved: () -> Unit) = viewModelScope.launch {
        repo.setCashOnHand(targetMinor)
        onSaved()
    }

    fun add(
        direction: String,
        amountMinor: Long,
        category: String?,
        description: String,
        note: String,
        occurredAt: Long,
        onSaved: () -> Unit,
        /**
         * Cash the user already had, recorded first so this spend doesn't take the
         * balance below zero (see [com.ledger.app.data.db.CashMove.OPENING]).
         */
        topUpMinor: Long = 0,
    ) = viewModelScope.launch {
        if (amountMinor <= 0) return@launch
        if (topUpMinor > 0) repo.adjustCash(topUpMinor)
        repo.addCashEntry(Direction.valueOf(direction), amountMinor, category, description, note, occurredAt)
        // Cash spending counts against budgets like any other.
        if (direction == Direction.DEBIT.name) budgetAlerter.checkAndNotify()
        // Call this last. Closing the screen clears this ViewModel and cancels its
        // scope, which would stop the budget check above if it ran first.
        onSaved()
    }
}

/**
 * The setup guide's state, used by every screen that has a step. Each screen
 * gets its own instance, but they all share the one [TutorialManager], so only
 * one step shows at a time.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val tutorial: TutorialManager,
) : ViewModel() {

    val state: StateFlow<TutorialState> = tutorial.state

    /** The screen the guide wants next. The host navigates there and acknowledges. */
    val navigateTo: StateFlow<TutorialStep?> = tutorial.navigateTo
    fun navigationHandled() = tutorial.navigationHandled()

    /** Called by the host on launch; see [TutorialManager.startIfFirstRun]. */
    fun startIfFirstRun() = tutorial.startIfFirstRun()

    fun complete(step: TutorialStep) = tutorial.complete(step)
    fun skip(step: TutorialStep) = tutorial.skip(step)
    fun dismiss() = tutorial.dismiss()
    fun replay() = tutorial.replay()
}

/** One of the user's accounts plus the names providers print for it. */
data class OwnAccountRow(
    val account: OwnAccountEntity,
    val names: List<String>,
) {
    /**
     * The wallet and the name providers print for it, such as "MTN MoMo · known as
     * AMA OWUSU". Together they explain why a transfer was or wasn't recognised as
     * the user's own. Null when the user didn't say which wallet.
     */
    val senderLabel: String?
        get() = account.institution
            .takeIf { it != OwnAccountEntity.ANY_PROVIDER }
            ?.let { institutionLabel(it) }
}

/**
 * The user's own wallets. Knowing them lets the app tell "I moved my own
 * money" apart from "I paid someone", so a transfer between them doesn't count
 * as spending.
 */
@HiltViewModel
class OwnAccountsViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    /** Accounts joined to their aliases, so each row can show both. */
    val accounts: StateFlow<List<OwnAccountRow>> =
        combine(repo.observeOwnAccounts(), repo.observeOwnAccountNames()) { accounts, names ->
            val byAccount = names.groupBy { it.accountId }
            accounts.map { account ->
                OwnAccountRow(
                    account = account,
                    names = byAccount[account.id]?.map { it.display }.orEmpty(),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() { _error.value = null }

    /**
     * @param institution which wallet the number belongs to, as an
     *  [com.ledger.domain.model.Institution] name, or null for any.
     * @param names comma-separated names providers show for this account.
     */
    fun add(
        identifier: String,
        label: String,
        institution: String?,
        names: String,
    ) = viewModelScope.launch {
        val aliases = names.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (!repo.addOwnAccount(identifier, label, institution, aliases)) {
            _error.value = "That doesn't look like a phone or account number."
        }
    }

    fun remove(account: OwnAccountEntity) = viewModelScope.launch { repo.removeOwnAccount(account.id) }
}

/** What the Settings "Security" card renders. */
data class SecurityState(
    val mode: LockMode = LockMode.NONE,
    val pinLength: Int = 0,
    val biometricAvailable: Boolean = false,
) {
    val hasPin: Boolean get() = mode != LockMode.NONE
    val biometricEnabled: Boolean get() = mode == LockMode.BIOMETRIC
}

/**
 * Settings controls for the optional app lock. [AppLockManager] writes
 * synchronously and this card is its only writer, so the state is simply read
 * again after each change.
 */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val lock: AppLockManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SecurityState> = _state.asStateFlow()

    private fun read() = SecurityState(
        mode = lock.mode(),
        pinLength = lock.pinLength(),
        biometricAvailable = com.ledger.app.security.BiometricGate.canUseBiometric(context),
    )

    private fun refresh() { _state.value = read() }

    /** Turns the lock on, or changes an existing PIN. */
    fun setPin(pin: String) { lock.setPin(pin); refresh() }

    fun verifyPin(pin: String): Boolean = lock.verifyPin(pin)

    fun setBiometricEnabled(enabled: Boolean) { lock.setBiometricEnabled(enabled); refresh() }

    fun disableLock() { lock.disableLock(); refresh() }
}

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    val rules: StateFlow<List<RuleEntity>> =
        repo.observeRules()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<CategoryOptions> =
        repo.observeCategoryOptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryOptions())

    fun save(rule: RuleEntity) = viewModelScope.launch { repo.saveRule(rule) }
    fun setEnabled(rule: RuleEntity, enabled: Boolean) =
        viewModelScope.launch { repo.saveRule(rule.copy(enabled = enabled)) }
    fun delete(rule: RuleEntity) = viewModelScope.launch { repo.deleteRule(rule.id) }
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> =
        repo.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, kind: CategoryKind) =
        viewModelScope.launch { repo.addCategory(name, kind) }

    /** How many categories the last "restore defaults" added, for the screen to report. */
    private val _restored = MutableStateFlow<Int?>(null)
    val restored: StateFlow<Int?> = _restored.asStateFlow()

    fun restoreDefaults() = viewModelScope.launch { _restored.value = repo.restoreDefaultCategories() }

    fun rename(category: CategoryEntity, name: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isNotEmpty()) repo.saveCategory(category.copy(name = clean))
    }
    fun setEnabled(category: CategoryEntity, enabled: Boolean) =
        viewModelScope.launch {
            // A hidden category can't stay a favourite.
            repo.saveCategory(
                category.copy(enabled = enabled, favorite = category.favorite && enabled),
            )
        }

    /** Adds or removes a category from the inline row the pickers offer. */
    fun setFavorite(category: CategoryEntity, favorite: Boolean) =
        viewModelScope.launch { repo.setCategoryFavorite(category, favorite) }
    fun setKind(category: CategoryEntity, kind: CategoryKind) =
        viewModelScope.launch { repo.setCategoryKind(category, kind) }
    fun delete(category: CategoryEntity) = viewModelScope.launch { repo.deleteCategory(category.id) }

    /**
     * Moves a category one place up (-1) or down (+1).
     *
     * The editor groups categories by side, so this swaps with the nearest
     * neighbour on the same side. Swapping with the next row in the raw list could
     * move it into another section.
     */
    fun move(category: CategoryEntity, delta: Int) = viewModelScope.launch {
        val peers = categories.value.filter { it.kind == category.kind }
        val index = peers.indexOfFirst { it.id == category.id }
        val target = index + delta
        if (index >= 0 && target in peers.indices) repo.swapCategoryOrder(category, peers[target])
    }

    /**
     * Saves a drag-and-drop reorder. [ids] is the whole list in its new order.
     * The screen reorders its own copy while dragging and calls this once on drop.
     */
    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorderCategories(ids) }
}

/**
 * The full category breakdown behind the dashboard's top five. The user can
 * step back through earlier months, or pick a date range.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BreakdownViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val periodPrefs: PeriodPrefs,
) : ViewModel() {

    private fun currentMonth(): BreakdownPeriod.Month {
        val fiscal = periodPrefs.fiscal()
        return BreakdownPeriod.Month(fiscal.startOf(LocalDate.now(ZoneId.systemDefault())), fiscal.startDay)
    }

    private val _period = MutableStateFlow<BreakdownPeriod>(currentMonth())
    val period: StateFlow<BreakdownPeriod> = _period.asStateFlow()

    private val _direction = MutableStateFlow("DEBIT")
    val direction: StateFlow<String> = _direction.asStateFlow()

    /**
     * Null until the first read arrives, so the screen shows a placeholder instead
     * of briefly saying the period was empty.
     */
    val slices: StateFlow<List<CategorySlice>?> =
        combine(_period, _direction) { period, direction -> period to direction }
            .flatMapLatest { (period, direction) ->
                val (from, to) = period.bounds()
                repo.observeCategoryBreakdown(direction, from, to)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDirection(value: String) { _direction.value = value }

    /**
     * Steps one month at a time, never past the current month. Does nothing while
     * a custom range is showing.
     */
    fun stepMonth(delta: Long) {
        val current = _period.value as? BreakdownPeriod.Month ?: return
        val next = FiscalMonth(current.startDay).shift(current.first, delta)
        if (!next.isAfter(currentMonth().first)) _period.value = current.copy(first = next)
    }

    fun canStepForward(): Boolean {
        val current = (_period.value as? BreakdownPeriod.Month)?.first ?: return false
        return current.isBefore(currentMonth().first)
    }

    /**
     * Shows the breakdown for a custom date range.
     *
     * The range is widened to whole days, the same way
     * [HistoryViewModel.setCustomRange] does it, so the same range covers the same
     * transactions on both screens.
     */
    fun setRange(fromMillis: Long, toMillis: Long) {
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val to = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        _period.value = BreakdownPeriod.Range(from, to)
    }

    /** Back to the steppable month view, at the current month. */
    fun clearRange() {
        _period.value = currentMonth()
    }
}

/**
 * The window a breakdown covers. A month is its own case because only a month
 * can be stepped, and it gets a month label instead of two dates.
 */
sealed interface BreakdownPeriod {
    /**
     * [first] is the first day of the user's month. The month starts on [startDay],
     * except in a month too short for it, where it starts on the last day.
     */
    data class Month(val first: LocalDate, val startDay: Int = 1) : BreakdownPeriod
    /** Inclusive epoch-milli bounds, already snapped to whole days. */
    data class Range(val fromMillis: Long, val toMillis: Long) : BreakdownPeriod
}

private fun BreakdownPeriod.bounds(): Pair<Long, Long> = when (this) {
    is BreakdownPeriod.Month -> FiscalMonth(startDay).bounds(first)
    is BreakdownPeriod.Range -> fromMillis to toMillis
}

@HiltViewModel
class DataViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val smsImporter: com.ledger.app.sms.SmsInboxImporter,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    fun clearStatus() { _status.value = null }

    /**
     * True while the inbox import is running, so the import row can show a
     * spinner. It doesn't block the screen: the import is safe to leave running.
     */
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /**
     * Goes up by one each time an import finishes. The setup guide watches it to
     * complete its import step.
     */
    private val _importsFinished = MutableStateFlow(0)
    val importsFinished: StateFlow<Int> = _importsFinished.asStateFlow()

    /** Handles the READ_SMS permission result: import if granted, explain if not. */
    fun onSmsPermissionResult(granted: Boolean) {
        if (granted) importSms() else _status.value = "SMS permission is needed to import past messages."
    }

    /**
     * Imports past transactions from the SMS inbox. Needs READ_SMS. Runs in the
     * background, and the result appears in [status].
     */
    fun importSms() {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            _status.value = "Scanning past messages…"
            runCatching { smsImporter.importAll() }
                .onSuccess { r ->
                    _status.value = "Imported ${r.imported} · ${r.duplicates} already here · " +
                        "scanned ${r.scanned} message${if (r.scanned == 1) "" else "s"}"
                }
                .onFailure { _status.value = "Couldn't read past messages: ${it.message}" }
            _importing.value = false
            _importsFinished.value++
        }
    }

    fun exportCsv(uri: Uri) = export(uri, "CSV") { repo.exportCsv() }
    fun exportJson(uri: Uri) = export(uri, "JSON") { repo.exportJson() }

    private fun export(uri: Uri, label: String, build: suspend () -> String) = viewModelScope.launch {
        runCatching {
            val content = build()
            writeText(uri, content)
            repo.transactionCount()
        }.onSuccess { n -> _status.value = "$label export saved · $n transaction${if (n == 1) "" else "s"}" }
            .onFailure { _status.value = "Export failed: ${it.message}" }
    }

    /** Encrypts the full JSON backup with [passphrase] and writes it to [uri]. */
    fun backup(uri: Uri, passphrase: String) = viewModelScope.launch {
        runCatching {
            val json = repo.exportJson()
            writeText(uri, BackupCrypto.encrypt(json, passphrase.toCharArray()))
            repo.transactionCount()
        }.onSuccess { n -> _status.value = "Encrypted backup saved · $n transaction${if (n == 1) "" else "s"}" }
            .onFailure { _status.value = "Backup failed: ${it.message}" }
    }

    /**
     * Imports a `.ledger` (encrypted) or `.json` (plain) file.
     *
     * This always merges. Transactions the device already has are skipped, and
     * nothing captured since the file was made is touched. [restoring] only
     * changes the wording of the result.
     */
    fun importFrom(uri: Uri, passphrase: String?, restoring: Boolean = false) = viewModelScope.launch {
        runCatching {
            val raw = readText(uri)
            val jsonText = if (BackupCrypto.looksLikeBackup(raw)) {
                val pass = passphrase?.takeIf { it.isNotEmpty() } ?: error("This backup is encrypted. Enter its passphrase.")
                BackupCrypto.decrypt(raw, pass.toCharArray())
            } else {
                raw
            }
            repo.importBackup(LedgerTransfer.fromJson(jsonText))
        }.onSuccess { r ->
            val verb = if (restoring) "Restored" else "Imported"
            // Skipped rows are ones the device already had, so say "already here"
            // rather than "duplicates".
            _status.value = "$verb ${r.added} · ${r.skipped} already here"
        }.onFailure {
            _status.value = when (it) {
                is BackupCrypto.WrongPassphraseException -> it.message ?: "Wrong passphrase."
                else -> "Import failed: ${it.message}"
            }
        }
    }

    private fun writeText(uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: error("Couldn't open the destination file.")
    }

    private fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Couldn't read the selected file.")
}

/**
 * The setup shortlist: the categories this user reaches for most.
 *
 * There are about thirty default categories, more than a picker should show at
 * once. The user picks a shortlist up front, with sensible ones already ticked.
 *
 * The shortlist only decides what the pickers show first. Every other
 * category is still available behind "More".
 */
@HiltViewModel
class CategorySetupViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val setup: SetupPrefs,
) : ViewModel() {

    /** Every category, including ones the user added, for when this is reopened. */
    val categories: StateFlow<List<CategoryEntity>> =
        repo.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    init {
        // Use only the first read. Later updates would overwrite the user's taps.
        viewModelScope.launch {
            val rows = categories.first { it.isNotEmpty() }
            _selected.value =
                // Reopened later: show what they chose last time.
                if (setup.categoriesChosen) rows.filter { it.favorite }.map { it.name }.toSet()
                else rows.map { it.name }.filterTo(mutableSetOf()) { it in DefaultCategories.SUGGESTED }
        }
    }

    fun toggle(name: String) {
        _selected.value = _selected.value.let { if (name in it) it - name else it + name }
    }

    fun selectAll(names: List<String>) { _selected.value = names.toSet() }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        repo.applyCategoryFavourites(_selected.value)
        setup.categoriesChosen = true
        onDone()
    }

    /**
     * Skipping counts as an answer. The pickers use the default order, and the
     * step isn't offered again.
     */
    fun skip(onDone: () -> Unit) {
        setup.categoriesChosen = true
        onDone()
    }
}

/** What the budgets screen renders: the limits, and what is left to budget. */
data class BudgetsState(
    val loaded: Boolean = false,
    val budgets: List<BudgetStatus> = emptyList(),
    /** Spending categories with no limit yet, offered by "Add a budget". */
    val available: List<String> = emptyList(),
) {
    val totalLimitMinor: Long get() = budgets.sumOf { it.limitMinor }
    val totalSpentMinor: Long get() = budgets.sumOf { it.spentMinor }
}

/**
 * Monthly limits, one per category.
 *
 * This screen always shows the current month. Looking back at earlier months
 * is what the breakdown screen is for. Only spending categories can have a
 * limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val repo: LedgerRepository,
    period: PeriodPrefs,
) : ViewModel() {

    /**
     * Worked out per subscription (see [current]), so the screen and the budget
     * alerts always describe the same month.
     */
    val state: StateFlow<BudgetsState> =
        period.current().flatMapLatest { window ->
            val (from, to) = window.bounds
            combine(
                repo.observeBudgetStatuses(from, to),
                repo.observeCategoryOptions(),
            ) { budgets, options ->
                val budgeted = budgets.map { it.category }.toSet()
                BudgetsState(
                    loaded = true,
                    budgets = budgets,
                    available = options.spending.filterNot { it in budgeted },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetsState())

    /**
     * @param limitMajor the amount as typed, in cedis. An amount that rounds to
     *  zero is ignored, so a typo can't remove an existing budget.
     */
    fun setBudget(category: String, limitMajor: Double) = viewModelScope.launch {
        val minor = (limitMajor * 100).roundToLong()
        if (minor > 0) repo.setBudget(category, minor)
    }

    fun remove(category: String) = viewModelScope.launch { repo.removeBudget(category) }
}
