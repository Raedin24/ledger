package com.ledger.app.ui.vm

import android.content.Context
import android.net.Uri
import com.ledger.app.data.backup.BackupCrypto
import com.ledger.app.data.backup.LedgerTransfer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.CategoryKind
import com.ledger.app.data.db.CategoryOptions
import com.ledger.app.data.db.CategorySlice
import com.ledger.app.data.db.CategoryTotal
import com.ledger.app.data.db.DayFlow
import com.ledger.app.data.db.DefaultCategories
import com.ledger.app.data.prefs.SetupPrefs
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

private fun monthBounds(ref: LocalDate = LocalDate.now(ZoneId.systemDefault())): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val from = ref.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val to = ref.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return from to to
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
    /** One value per elapsed day of the month (index 0 = the 1st), for the timeline. */
    val dailySpendSeries: List<Long> = emptyList(),
    val topCategories: List<CategoryTotal> = emptyList(),
    val largestExpenses: List<TransactionEntity> = emptyList(),
    val recent: List<TransactionEntity> = emptyList(),
) {
    val savingsMinor: Long get() = receivedMinor - spentMinor

    /** True once the DB has been read and there is genuinely nothing captured —
     *  the signal for the first-run welcome. Keyed on the whole ledger rather
     *  than this month's totals, so a quiet month still shows the dashboard.
     *  [loaded] guards against a welcome flash for users who do have data. */
    val isFirstRun: Boolean
        get() = loaded && !hasAnyTransaction

    /** Percent change in spend vs the same-length window last month; null until
     *  there is a prior month to compare against. Positive = spending more. */
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

@HiltViewModel
class OverviewViewModel @Inject constructor(repo: LedgerRepository) : ViewModel() {
    private val bounds = monthBounds()
    private val lastBounds = monthBounds(LocalDate.now(ZoneId.systemDefault()).minusMonths(1))

    private val core = combine(
        repo.observeTotal("DEBIT", bounds.first, bounds.second),
        repo.observeTotal("CREDIT", bounds.first, bounds.second),
        repo.observeTopCategories(bounds.first, bounds.second),
        repo.observeRecent(6),
    ) { spent, received, top, recent -> OverviewCore(spent, received, top, recent) }

    private val extras = combine(
        repo.observeTotal("DEBIT", lastBounds.first, lastBounds.second),
        repo.observeDailyFlow(bounds.first, bounds.second),
        repo.observeLargestExpenses(bounds.first, bounds.second),
        repo.observeReviewCount(),
        repo.observeTransactionCount(),
    ) { lastSpent, daily, largest, reviewCount, totalCount ->
        OverviewExtras(lastSpent, daily, largest, reviewCount, totalCount)
    }

    /**
     * Nine Room queries feed this dashboard, and every insert invalidates all of
     * them. A background SMS backfill therefore fired hundreds of full
     * recompositions of the heaviest screen in the app — the source of the
     * multi-hundred-millisecond frames in the traces.
     *
     * The debounce collapses a burst into one update. It also delays the very
     * first value, which is fine: the screen is showing its skeleton until this
     * arrives, and the wait is dominated by the cold SQLCipher open anyway.
     *
     * `flowOn` matters as much as the debounce: `stateIn(viewModelScope)` collects
     * on `Main.immediate`, so without it the transform below — a map build, an int
     * parse per day of the month and a fresh list — ran on the main thread every
     * time any of the nine queries fired.
     */
    val state: StateFlow<OverviewState> = combine(core, extras) { c, e ->
        val daysElapsed = LocalDate.now(ZoneId.systemDefault()).dayOfMonth
        val spentByDom = e.daily.associate { it.day.substringAfterLast('-').toInt() to it.spentMinor }
        OverviewState(
            loaded = true,
            hasAnyTransaction = e.totalCount > 0,
            spentMinor = c.spent,
            receivedMinor = c.received,
            lastMonthSpentMinor = e.lastSpent,
            avgDailyMinor = c.spent / daysElapsed.coerceAtLeast(1),
            reviewCount = e.reviewCount,
            dailySpendSeries = (1..daysElapsed).map { spentByDom[it] ?: 0L },
            topCategories = c.top,
            largestExpenses = e.largest,
            recent = c.recent,
        )
    }
        .debounce(DASHBOARD_SETTLE_MS)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverviewState())

    private companion object {
        /** Long enough to swallow an import burst, short enough to feel live. */
        const val DASHBOARD_SETTLE_MS = 120L
    }
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    /**
     * The review backlog. Null until the first read lands — "empty" and "not
     * loaded yet" are genuinely different states, and conflating them is what
     * flashed "All caught up" over a queue that was about to arrive.
     */
    val queue: StateFlow<List<TransactionEntity>?> =
        repo.observeReviewQueue()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Enabled categories offered as quick taps, split by direction so a credit
     *  is never offered "Rent". Falls back to the defaults until the first-run
     *  seed lands. */
    val categories: StateFlow<CategoryOptions> =
        repo.observeCategoryOptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryOptions())

    /** Transient confirmation of a retroactive rule sweep ("Also sorted N…"). */
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
}

enum class SortOrder(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), LARGEST("Largest") }

/** History date window. Defaults to a recent slice so the list never loads the
 *  whole ledger up front; [CUSTOM] is driven by the calendar range picker. */
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
    val direction: String? = null,   // "DEBIT" / "CREDIT" / null
    val category: String? = null,
    val institution: String? = null, // Institution.name / null
    val reviewOnly: Boolean = false,
    val sort: SortOrder = SortOrder.NEWEST,
    val datePreset: DatePreset = DatePreset.LAST_90_DAYS,
    val customFrom: Long? = null,    // epoch millis, inclusive (CUSTOM only)
    val customTo: Long? = null,      // epoch millis, inclusive (CUSTOM only)
) {
    val anyActive: Boolean
        get() = direction != null || category != null || institution != null ||
            reviewOnly || datePreset != DatePreset.LAST_90_DAYS || sort != SortOrder.NEWEST

    /** How many narrowing choices sit behind the filter button — the date window
     *  and the search box stay on the screen itself, so they don't count. Drives
     *  the button's badge and the summary row of removable chips. */
    val activeCount: Int
        get() = listOf(direction != null, category != null, institution != null, reviewOnly)
            .count { it } + if (sort != SortOrder.NEWEST) 1 else 0

    /** Resolved [from, to] epoch-milli bounds for the current window; either may
     *  be null (open-ended). ALL_TIME is fully open. */
    val bounds: Pair<Long?, Long?>
        get() {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
            fun endOf(d: LocalDate) = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            return when (datePreset) {
                DatePreset.THIS_MONTH -> startOf(today.withDayOfMonth(1)) to endOf(today)
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
) : ViewModel() {
    private val _filters = MutableStateFlow(HistoryFilters())
    val filters: StateFlow<HistoryFilters> = _filters.asStateFlow()

    val categories: StateFlow<CategoryOptions> =
        repo.observeCategoryOptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryOptions())

    /** Senders present in the ledger, mapped to friendly labels for the chips. */
    val senders: StateFlow<List<SenderOption>> =
        repo.observeInstitutions()
            .map { names -> names.map { SenderOption(it, institutionLabel(it)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All filtering + sorting happens in SQL (off the main thread); the window
    // bounds cap how much is ever read, so the list stays fast as data grows.
    // Null until the first result set lands, so the screen shows a wireframe
    // rather than briefly claiming there are no transactions.
    val results: StateFlow<List<TransactionEntity>?> =
        _filters.flatMapLatest { f ->
            val (from, to) = f.bounds
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
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: LedgerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1L

    val transaction: StateFlow<TransactionEntity?> =
        repo.observeTransaction(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<CategoryOptions> =
        repo.observeCategoryOptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryOptions())

    fun setCategory(category: String) = edit { it.copy(category = category, needsReview = false) }
    fun setPerson(person: String?) = edit { it.copy(person = person?.trim()?.ifBlank { null }) }
    fun setNote(note: String?) = edit { it.copy(notes = note?.trim()?.ifBlank { null }) }

    private fun edit(transform: (TransactionEntity) -> TransactionEntity) = viewModelScope.launch {
        transaction.value?.let { repo.updateTransaction(transform(it)) }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteTransaction(id)
        onDone()
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repo: LedgerRepository,
) : ViewModel() {
    val senders = repo.observeSenders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/**
 * The first-run guide's state, shared by every screen that owns a beat. Each
 * screen gets its own instance, but they all read and write the singleton
 * [TutorialManager], so only one coach-mark is ever live.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val tutorial: TutorialManager,
) : ViewModel() {

    val state: StateFlow<TutorialState> = tutorial.state

    /** The screen the guide wants next; the host navigates and acknowledges. */
    val navigateTo: StateFlow<TutorialStep?> = tutorial.navigateTo
    fun navigationHandled() = tutorial.navigationHandled()

    /** Called once from the host, on a genuine first launch. */
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
    /** "MTN MoMo · known as AMA OWUSU" — the two facts that explain why a
     *  transfer was, or wasn't, recognised as the user's own money. Null when the
     *  user didn't say which wallet, which is allowed and changes no matching. */
    val senderLabel: String?
        get() = account.institution
            .takeIf { it != OwnAccountEntity.ANY_PROVIDER }
            ?.let { institutionLabel(it) }
}

/**
 * The user's own numbers/accounts. Naming them is what lets the app tell
 * "I moved my own money" apart from "I paid someone", so an internal transfer
 * stops counting as spending.
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
     * @param institution [com.ledger.domain.model.Institution] name, or null for
     *  "any" — which wallet the number belongs to.
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
 * Settings-side controls for the opt-in app lock. [AppLockManager] writes to
 * SharedPreferences synchronously, so state is re-read after each change rather
 * than observed — the card is the only writer.
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

    fun rename(category: CategoryEntity, name: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isNotEmpty()) repo.saveCategory(category.copy(name = clean))
    }
    fun setEnabled(category: CategoryEntity, enabled: Boolean) =
        viewModelScope.launch { repo.saveCategory(category.copy(enabled = enabled)) }
    fun setKind(category: CategoryEntity, kind: CategoryKind) =
        viewModelScope.launch { repo.setCategoryKind(category, kind) }
    fun delete(category: CategoryEntity) = viewModelScope.launch { repo.deleteCategory(category.id) }

    /**
     * Moves a category one slot up (-1) or down (+1).
     *
     * Swaps with the nearest neighbour *of the same kind*, because the editor
     * groups by side: swapping with the raw list neighbour would make a category
     * hop out of its own section and appear not to move at all.
     */
    fun move(category: CategoryEntity, delta: Int) = viewModelScope.launch {
        val peers = categories.value.filter { it.kind == category.kind }
        val index = peers.indexOfFirst { it.id == category.id }
        val target = index + delta
        if (index >= 0 && target in peers.indices) repo.swapCategoryOrder(category, peers[target])
    }

    /**
     * Commits a drag-and-drop reorder. [ids] is the whole list in its new order.
     *
     * The screen reorders its own copy as the row is dragged and calls this once
     * on drop, so the list follows the finger at frame rate without a database
     * round-trip per row crossed.
     */
    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorderCategories(ids) }
}

/**
 * The full category breakdown behind the dashboard's top five.
 *
 * Owns the month being viewed, so the screen can be stepped back through history
 * rather than being a fixed snapshot of the current month — the question "where
 * did it go?" is usually asked about a month that has already closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BreakdownViewModel @Inject constructor(
    private val repo: LedgerRepository,
) : ViewModel() {

    private val _period = MutableStateFlow<BreakdownPeriod>(
        BreakdownPeriod.Month(LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1)),
    )
    val period: StateFlow<BreakdownPeriod> = _period.asStateFlow()

    private val _direction = MutableStateFlow("DEBIT")
    val direction: StateFlow<String> = _direction.asStateFlow()

    /** Null until the first read lands, so the screen shows a wireframe rather
     *  than briefly claiming the period was empty. */
    val slices: StateFlow<List<CategorySlice>?> =
        combine(_period, _direction) { period, direction -> period to direction }
            .flatMapLatest { (period, direction) ->
                val (from, to) = period.bounds()
                repo.observeCategoryBreakdown(direction, from, to)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDirection(value: String) { _direction.value = value }

    /** Steps a whole month at a time; never past the current month, since there
     *  is nothing to show in the future. A no-op on an explicit range, which has
     *  no next one to step to. */
    fun stepMonth(delta: Long) {
        val current = (_period.value as? BreakdownPeriod.Month)?.first ?: return
        val next = current.plusMonths(delta)
        val thisMonth = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1)
        if (!next.isAfter(thisMonth)) _period.value = BreakdownPeriod.Month(next)
    }

    fun canStepForward(): Boolean {
        val current = (_period.value as? BreakdownPeriod.Month)?.first ?: return false
        return current.isBefore(LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1))
    }

    /**
     * Widens the breakdown to an explicit window.
     *
     * The endpoints are snapped out to whole days — start of the first, last
     * millisecond of the last — the same normalisation [HistoryViewModel.setCustomRange]
     * does, so a range picked in one screen covers the same transactions in the
     * other rather than silently dropping the final day.
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
        _period.value = BreakdownPeriod.Month(LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1))
    }
}

/**
 * The window a breakdown covers.
 *
 * A month is kept as its own case rather than collapsed into a pair of millis
 * because it is the only one that can be stepped, and the label differs — "July
 * 2026" reads better than the two dates that bound it.
 */
sealed interface BreakdownPeriod {
    /** [first] is the first day of the month. */
    data class Month(val first: LocalDate) : BreakdownPeriod
    /** Inclusive epoch-milli bounds, already snapped to whole days. */
    data class Range(val fromMillis: Long, val toMillis: Long) : BreakdownPeriod
}

private fun BreakdownPeriod.bounds(): Pair<Long, Long> = when (this) {
    is BreakdownPeriod.Month -> monthBounds(first)
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
     * True while the SMS backfill is scanning, so the import row can show an
     * inline spinner. Deliberately *not* a blocking modal: the scan is
     * idempotent and safe to leave running, so trapping the user behind a
     * dialog they were told not to close bought nothing.
     */
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** Bumped when a scan finishes. Lets the setup guide's import beat complete
     *  without this ViewModel knowing the guide exists. */
    private val _importsFinished = MutableStateFlow(0)
    val importsFinished: StateFlow<Int> = _importsFinished.asStateFlow()

    /** Routes the READ_SMS permission result: import on grant, explain on denial. */
    fun onSmsPermissionResult(granted: Boolean) {
        if (granted) importSms() else _status.value = "SMS permission is needed to import past messages."
    }

    /** Backfills past transactions from the SMS inbox (requires READ_SMS granted).
     *  Runs in the background; the result lands in [status]. */
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
     * Imports a `.ledger` (encrypted) or `.json` (plain) file. [replace] wipes
     * existing data first (a full restore); otherwise it merges by dedup key.
     */
    fun importFrom(uri: Uri, passphrase: String?, replace: Boolean) = viewModelScope.launch {
        runCatching {
            val raw = readText(uri)
            val jsonText = if (BackupCrypto.looksLikeBackup(raw)) {
                val pass = passphrase?.takeIf { it.isNotEmpty() } ?: error("This backup is encrypted — enter its passphrase.")
                BackupCrypto.decrypt(raw, pass.toCharArray())
            } else {
                raw
            }
            repo.importBackup(LedgerTransfer.fromJson(jsonText), replace)
        }.onSuccess { r ->
            val verb = if (replace) "Restored" else "Imported"
            _status.value = "$verb ${r.added} · skipped ${r.skipped} duplicate${if (r.skipped == 1) "" else "s"}"
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

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val setup: SetupPrefs,
) : ViewModel() {
    val sample = MutableStateFlow("")
    val detection = MutableStateFlow<LedgerRepository.Detection?>(null)

    fun onSampleChange(v: String) { sample.value = v }

    fun detect() { detection.value = repo.detectSample(sample.value) }

    /**
     * @param onDone receives true when the category shortlist still has to be
     *   offered. It is asked here, at the end of adding a sender, because that is
     *   the moment the app first has something to sort — and only once, so adding
     *   a second sender later goes straight back where it came from.
     */
    fun enableAndFinish(onDone: (pickCategories: Boolean) -> Unit) = viewModelScope.launch {
        detection.value?.institution?.let { repo.enableSender(it) }
        onDone(!setup.categoriesChosen)
    }
}

/**
 * The setup shortlist: which categories this user actually wants offered.
 *
 * The full default set is thirty labels, and a picker showing all of them is
 * mostly labels this particular user will never reach for. Rather than make them
 * prune it later — in an editor they have to find first — the choice is offered
 * once, up front, pre-answered with a sensible shortlist so it can be dismissed
 * with a single tap.
 */
@HiltViewModel
class CategorySetupViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val setup: SetupPrefs,
) : ViewModel() {

    /** Every label that exists, so the screen offers the user's own additions
     *  alongside the seeded ones when this is reopened from the editor. */
    val categories: StateFlow<List<CategoryEntity>> =
        repo.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    init {
        // The first read that lands, and only that one: a later emission is the
        // table changing under us, and re-seeding from it would wipe out the taps
        // the user has made since.
        viewModelScope.launch {
            val rows = categories.first { it.isNotEmpty() }
            _selected.value =
                // Reopened after the first pass — show what they chose last time,
                // not the generic suggestion.
                if (setup.categoriesChosen) rows.filter { it.enabled }.map { it.name }.toSet()
                else rows.map { it.name }.filterTo(mutableSetOf()) { it in DefaultCategories.SUGGESTED }
        }
    }

    fun toggle(name: String) {
        _selected.value = _selected.value.let { if (name in it) it - name else it + name }
    }

    fun selectAll(names: List<String>) { _selected.value = names.toSet() }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        repo.applyCategoryShortlist(_selected.value)
        setup.categoriesChosen = true
        onDone()
    }

    /** Skipping is an answer too — everything stays on, and the step doesn't
     *  come back to ask again. */
    fun skip(onDone: () -> Unit) {
        setup.categoriesChosen = true
        onDone()
    }
}
