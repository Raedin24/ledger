package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.db.TransactionEntity
import com.ledger.app.tutorial.TutorialStep
import com.ledger.app.ui.components.CoachBeak
import com.ledger.app.ui.components.CoachMark
import com.ledger.app.ui.components.CoachMarkHost
import com.ledger.app.ui.components.CoachScrim
import com.ledger.app.ui.components.CoachTone
import com.ledger.app.ui.components.DateRangeDialog
import com.ledger.app.ui.components.EmptyBadge
import com.ledger.app.ui.components.EmptyState
import com.ledger.app.ui.components.EnvelopeIllustration
import com.ledger.app.ui.components.HistorySkeleton
import com.ledger.app.ui.components.MONEY_IN
import com.ledger.app.ui.components.MONEY_OUT
import com.ledger.app.ui.components.SearchIllustration
import com.ledger.app.ui.components.coachLabel
import com.ledger.app.ui.components.customRangeLabel
import com.ledger.app.ui.components.directionLabel
import com.ledger.app.ui.components.spotlight
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.theme.LedgerThemeTokens
import com.ledger.app.ui.vm.DatePreset
import com.ledger.app.ui.vm.HistoryFilters
import com.ledger.app.ui.vm.HistoryViewModel
import com.ledger.app.ui.vm.SenderOption
import com.ledger.app.ui.vm.SortOrder
import com.ledger.app.ui.vm.TutorialViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    onAddSender: () -> Unit = {},
    onOpen: (Long) -> Unit = {},
    vm: HistoryViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    // Null until the first query returns — "no matches" and "not read yet" look
    // identical as an empty list, and showing the former was a lie for a frame.
    val results by vm.results.collectAsStateWithLifecycle()
    val rows = results.orEmpty()
    val loading = results == null
    val filters by vm.filters.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val senders by vm.senders.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selectionMode = selection.isNotEmpty()

    var pickingBulkCategory by remember { mutableStateOf(false) }
    var confirmingBulkDelete by remember { mutableStateOf(false) }
    var pickingDateRange by remember { mutableStateOf(false) }
    var editingFilters by remember { mutableStateOf(false) }

    // Day grouping walks the whole result set, so it is keyed to the results
    // rather than left in the LazyColumn's content lambda, where it re-ran on
    // every recomposition — including mid-scroll, over thousands of rows. Skipped
    // outright when sorting by amount, which renders a flat list.
    val grouped = remember(rows, filters.sort) {
        if (filters.sort == SortOrder.LARGEST) emptyList() else groupByDay(rows)
    }

    // Beat 6 — explain the 90-day default, or an imported user reads the missing
    // older rows as a failed import.
    val step = TutorialStep.HISTORY_WINDOW
    val showBeat6 = tutorial.showing(step) && !selectionMode && !pickingDateRange

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            SelectionBar(
                count = selection.size,
                onClear = vm::clearSelection,
                onCategorise = { pickingBulkCategory = true },
                onDelete = { confirmingBulkDelete = true },
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(18.dp))
                Text("History", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(filters.query, vm::onQueryChange, Modifier.weight(1f))
                    FilterButton(count = filters.activeCount) { editingFilters = true }
                }
            }

            item {
                DateRow(
                    preset = filters.datePreset,
                    rangeLabel = customRangeLabel(filters.customFrom, filters.customTo),
                    onPreset = vm::setDatePreset,
                    onPickRange = { pickingDateRange = true },
                    spotlightPreset = if (showBeat6) DatePreset.LAST_90_DAYS else null,
                )
            }
            // Everything chosen in the sheet stays visible — and individually
            // removable — so a narrowed list is never unexplained.
            if (filters.activeCount > 0) {
                item { ActiveFilterRow(filters, senders, vm) }
            }

            if (loading) {
                item { HistorySkeleton(Modifier.padding(top = 4.dp)) }
            } else if (rows.isEmpty()) {
                item {
                    val onlyDateNarrows = filters.query.isBlank() && filters.direction == null &&
                        filters.category == null && filters.institution == null && !filters.reviewOnly
                    when {
                        // Nothing anywhere — the ledger really is empty.
                        onlyDateNarrows && filters.datePreset == DatePreset.ALL_TIME -> EmptyState(
                            title = "No transactions yet",
                            message = "As mobile-money SMS arrive, they'll be captured and listed here automatically.",
                            modifier = Modifier.padding(top = 24.dp),
                            actionLabel = "Add a sender",
                            onAction = onAddSender,
                            illustration = { EnvelopeIllustration(EmptyBadge.Plus) },
                        )
                        // Only the date window is narrowing things — offer to widen it
                        // rather than implying there's no data at all.
                        onlyDateNarrows -> EmptyState(
                            title = "Nothing in this window",
                            message = "No transactions in ${
                                customRangeLabel(filters.customFrom, filters.customTo)
                                    ?.takeIf { filters.datePreset == DatePreset.CUSTOM }
                                    ?: filters.datePreset.label.lowercase()
                            }. Older ones are just outside this window.",
                            modifier = Modifier.padding(top = 24.dp),
                            actionLabel = "Show all time",
                            onAction = { vm.setDatePreset(DatePreset.ALL_TIME) },
                            illustration = { SearchIllustration() },
                        )
                        else -> EmptyState(
                            title = "No matches",
                            message = "Nothing here fits those filters. Try widening or clearing them.",
                            modifier = Modifier.padding(top = 24.dp),
                            actionLabel = "Clear filters",
                            onAction = { vm.onQueryChange(""); vm.clearFilters() },
                            illustration = { SearchIllustration() },
                        )
                    }
                }
            } else if (filters.sort == SortOrder.LARGEST) {
                // Amount ordering — a flat list reads better than day groups.
                items(rows, key = { it.id }, contentType = { "txn" }) { txn ->
                    TxnRow(txn, selection, selectionMode, vm, onOpen)
                }
            } else {
                grouped.forEach { (label, dayItems) ->
                    item(key = "hdr-$label", contentType = "day-header") { DayHeader(label) }
                    items(dayItems, key = { it.id }, contentType = { "txn" }) { txn ->
                        TxnRow(txn, selection, selectionMode, vm, onOpen)
                    }
                }
            }

            item { Spacer(Modifier.height(if (showBeat6) 200.dp else 90.dp)) }
        }
    }

        CoachScrim(showBeat6)
        CoachMarkHost(
            visible = showBeat6,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            CoachMark(
                label = coachLabel(step, if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL),
                title = step.title,
                body = step.body,
                primaryLabel = step.primary,
                onPrimary = { tutorialVm.complete(step) },
                secondaryLabel = step.secondary,
                onSecondary = { vm.setDatePreset(DatePreset.ALL_TIME); tutorialVm.complete(step) },
                onDismiss = tutorialVm::dismiss,
                tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL,
                beak = CoachBeak.Top(0.22f),
                step = step.number,
            )
        }
    }

    if (editingFilters) {
        FilterSheet(
            filters = filters,
            // Filtering spans both directions, so every label is offered.
            categories = categories.all,
            senders = senders,
            resultCount = rows.size,
            vm = vm,
            onDismiss = { editingFilters = false },
        )
    }
    if (pickingDateRange) {
        DateRangeDialog(
            initialFrom = filters.customFrom,
            initialTo = filters.customTo,
            onConfirm = { from, to -> vm.setCustomRange(from, to); pickingDateRange = false },
            onDismiss = { pickingDateRange = false },
        )
    }
    if (pickingBulkCategory) {
        CategoryPickerSheet(
            // A bulk selection can hold both directions, so all labels are shown.
            categories = categories.all,
            current = null,
            onPick = { vm.categoriseSelected(it); pickingBulkCategory = false },
            onDismiss = { pickingBulkCategory = false },
            title = "Sort ${selection.size} transaction${if (selection.size == 1) "" else "s"} as…",
        )
    }
    if (confirmingBulkDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingBulkDelete = false },
            containerColor = LedgerPalette.Surface,
            title = { Text("Delete ${selection.size} transaction${if (selection.size == 1) "" else "s"}?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("They'll be removed from this device. This can't be undone.", style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkSoft) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmingBulkDelete = false; vm.deleteSelected() }) {
                    Text("Delete", color = LedgerPalette.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmingBulkDelete = false }) {
                    Text("Cancel", color = LedgerPalette.InkMuted)
                }
            },
        )
    }
}

@Composable
private fun TxnRow(
    txn: TransactionEntity,
    selection: Set<Long>,
    selectionMode: Boolean,
    vm: HistoryViewModel,
    onOpen: (Long) -> Unit,
) {
    TransactionRow(
        txn = txn,
        selected = txn.id in selection,
        onClick = { if (selectionMode) vm.toggleSelected(txn.id) else onOpen(txn.id) },
        onLongClick = { vm.toggleSelected(txn.id) },
    )
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LedgerThemeTokens.colors.inkMuted) },
        placeholder = { Text("Search people, notes…") },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LedgerThemeTokens.colors.surfaceAlt,
            unfocusedContainerColor = LedgerThemeTokens.colors.surfaceAlt,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Date-window chips. "Custom" opens the calendar range picker; once a range is
 *  chosen the chip shows it, so the active window is always legible. */
@Composable
private fun DateRow(
    preset: DatePreset,
    rangeLabel: String?,
    onPreset: (DatePreset) -> Unit,
    onPickRange: () -> Unit,
    spotlightPreset: DatePreset? = null,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DatePreset.entries.filter { it != DatePreset.CUSTOM }.forEach { option ->
            Chip(option.label, preset == option, spotlit = option == spotlightPreset) { onPreset(option) }
        }
        Chip(
            label = if (preset == DatePreset.CUSTOM && rangeLabel != null) rangeLabel else "Custom",
            active = preset == DatePreset.CUSTOM,
            onClick = onPickRange,
        )
    }
}

/** Opens the filter sheet. Carries a count so a narrowed list is obvious even
 *  after the summary row has scrolled away. */
@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    val active = count > 0
    var box = Modifier
        .size(56.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (active) LedgerPalette.Ink else LedgerThemeTokens.colors.surfaceAlt)
    if (!active) box = box.border(1.dp, LedgerPalette.Hairline, RoundedCornerShape(14.dp))
    Box(box.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.FilterList,
            contentDescription = if (active) "Filters ($count active)" else "Filters",
            tint = if (active) LedgerPalette.Background else LedgerPalette.InkSoft,
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(LedgerPalette.Gold),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerPalette.Background,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** The active sheet filters, each removable in one tap. */
@Composable
private fun ActiveFilterRow(filters: HistoryFilters, senders: List<SenderOption>, vm: HistoryViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filters.reviewOnly) RemovableChip("Needs review") { vm.toggleReviewOnly() }
        filters.direction?.let { dir ->
            RemovableChip(directionLabel(dir)) { vm.toggleDirection(dir) }
        }
        filters.institution?.let { inst ->
            val label = senders.firstOrNull { it.institution == inst }?.label ?: inst
            RemovableChip(label) { vm.toggleInstitution(inst) }
        }
        filters.category?.let { cat -> RemovableChip(cat) { vm.toggleCategory(cat) } }
        if (filters.sort != SortOrder.NEWEST) {
            RemovableChip("${filters.sort.label} first") { vm.setSort(SortOrder.NEWEST) }
        }
        Text(
            "Clear all",
            style = MaterialTheme.typography.labelLarge,
            color = LedgerPalette.Spend,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { vm.clearFilters() }
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

/** Hoisted: chips are re-created on every filter change and every scroll frame. */
private val PillShape = RoundedCornerShape(20.dp)

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(PillShape)
            .background(LedgerPalette.Ink)
            .clickable(onClick = onRemove)
            .padding(start = 13.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = LedgerPalette.Background,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove $label filter",
            tint = LedgerPalette.Background,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Everything that narrows the list apart from the date window and the search
 * box. Four rows of chips on the screen itself buried the transactions they were
 * meant to help find, so they live here and the screen keeps only what's active.
 *
 * Choices apply live — the footer counts the matches as you tap, so the sheet
 * needs no Apply step, only a way out.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: HistoryFilters,
    categories: List<String>,
    senders: List<SenderOption>,
    resultCount: Int,
    vm: HistoryViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LedgerPalette.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = LedgerPalette.Hairline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                if (filters.anyActive) {
                    Text(
                        "Reset",
                        style = MaterialTheme.typography.labelLarge,
                        color = LedgerPalette.Spend,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.clearFilters() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            FilterSection("Show") {
                Chip("Everything", !filters.reviewOnly) { if (filters.reviewOnly) vm.toggleReviewOnly() }
                Chip("Needs review", filters.reviewOnly) { if (!filters.reviewOnly) vm.toggleReviewOnly() }
            }

            FilterSection("Direction") {
                Chip(MONEY_IN, filters.direction == "CREDIT") { vm.toggleDirection("CREDIT") }
                Chip(MONEY_OUT, filters.direction == "DEBIT") { vm.toggleDirection("DEBIT") }
            }

            // Shown for a single sender too: it's how you confirm which provider a
            // row came from, and hiding it read as the filter not existing at all.
            if (senders.isNotEmpty()) {
                FilterSection("Sender") {
                    senders.forEach { s ->
                        Chip(s.label, filters.institution == s.institution) { vm.toggleInstitution(s.institution) }
                    }
                }
            }

            FilterSection("Category") {
                categories.forEach { c -> Chip(c, filters.category == c) { vm.toggleCategory(c) } }
            }

            FilterSection("Sort by") {
                SortOrder.entries.forEach { o -> Chip(o.label, filters.sort == o) { vm.setSort(o) } }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(LedgerPalette.Ink)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (resultCount) {
                        0 -> "No matches"
                        1 -> "Show 1 transaction"
                        else -> "Show $resultCount transactions"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = LedgerPalette.Background,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LedgerPalette.InkMuted,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun Chip(label: String, active: Boolean, spotlit: Boolean = false, onClick: () -> Unit) {
    var modifier = Modifier
        .spotlight(spotlit, PillShape)
        .clip(PillShape)
        .background(if (active) LedgerPalette.Ink else LedgerPalette.SurfaceAlt)
    if (!active) modifier = modifier.border(1.dp, LedgerPalette.Hairline, PillShape)
    Box(modifier.clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) LedgerPalette.Background else LedgerPalette.InkSoft,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LedgerPalette.InkMuted,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onCategorise: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(LedgerPalette.Surface).padding(start = 8.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = LedgerPalette.Ink)
        }
        Text("$count selected", style = MaterialTheme.typography.titleMedium, color = LedgerPalette.Ink, modifier = Modifier.weight(1f))
        Text(
            "Categorise",
            style = MaterialTheme.typography.labelLarge,
            color = LedgerPalette.Gold,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCategorise).padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Text(
            "Delete",
            style = MaterialTheme.typography.labelLarge,
            color = LedgerPalette.Danger,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

// Groups a date-sorted list into (Today / Yesterday / "Wed 16 Jul") sections,
// preserving the incoming order. Days outside the current year carry the year,
// so a backfill of old messages never reads as recent.
private val groupFmt = DateTimeFormatter.ofPattern("EEE d MMM").withZone(ZoneId.systemDefault())
private val groupFmtWithYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy").withZone(ZoneId.systemDefault())

private fun groupByDay(list: List<TransactionEntity>): List<Pair<String, List<TransactionEntity>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return list.groupBy {
        val instant = Instant.ofEpochMilli(it.occurredAt)
        when (val d = instant.atZone(zone).toLocalDate()) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> if (d.year == today.year) groupFmt.format(instant) else groupFmtWithYear.format(instant)
        }
    }.toList()
}
