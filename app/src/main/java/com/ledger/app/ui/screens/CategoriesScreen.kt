package com.ledger.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.CategoryKind
import com.ledger.app.ui.components.CategoryMark
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.ReorderState
import com.ledger.app.ui.components.dragHandle
import com.ledger.app.ui.components.rememberReorderState
import com.ledger.app.ui.components.reorderableItem
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.vm.CategoriesViewModel

/** The kinds, in the order the screen lists them: out, both, in. */
private val KIND_ORDER = CategoryKind.DISPLAY_ORDER

private val KIND_BLURB = mapOf(
    CategoryKind.OUT to "Offered when you sort money going out.",
    CategoryKind.SHARED to "Offered in both directions.",
    CategoryKind.IN to "Offered when you sort money coming in.",
)

/** Options for the side-of-the-ledger control, shared by add and edit. */
private val KIND_OPTIONS = KIND_ORDER.map { it.name to it.label }

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onPickUsual: () -> Unit,
    vm: CategoriesViewModel = hiltViewModel(),
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf(CategoryKind.OUT) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }

    // The order on screen. Seeded from the database and reordered locally as a
    // row is dragged across its neighbours, so the list keeps up with the finger;
    // the result is written once, when the finger lifts.
    var order by remember { mutableStateOf(categories) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState = listState,
        // Only rows may be displaced, and only by a row from the same side of the
        // ledger — a category dragged across a section boundary would appear to
        // change kind, which is what the edit dialog is for. Section headers are
        // keyed by string and fail this on the first test.
        canDrop = { from, to ->
            from is Long && to is Long && order.kindOf(from) == order.kindOf(to)
        },
        onMove = { from, to -> order = order.moveBefore(from, to) },
    )

    // Adopt whatever the database says whenever we aren't mid-drag; taking it
    // during a drag would yank the row out from under the finger on the first
    // unrelated write (a rename, a switch flipped elsewhere).
    LaunchedEffect(categories) {
        if (reorder.draggingKey == null) order = categories
    }

    // Grouped once per change rather than inside the list's content lambda, which
    // would re-partition the whole set on every scroll frame.
    val bySide = remember(order) { order.groupBy { CategoryKind.from(it.kind) } }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            ScreenHeader("Categories", onBack)
            Text(
                "The labels you sort transactions into. Money in and money out " +
                    "get their own sets, so a payment is never offered \"Salary\" " +
                    "and a deposit is never offered \"Rent\". Drag a row by its " +
                    "handle to reorder — the first few are the ones Review offers " +
                    "you without opening the picker.",
                style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkMuted,
            )
            // Turning thirty labels on and off one switch at a time is the slow
            // way round; the setup screen does the same job as one pass.
            Text(
                "Pick your usual categories",
                style = MaterialTheme.typography.labelLarge,
                color = LedgerPalette.Gold,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = onPickUsual)
                    .padding(vertical = 6.dp),
            )
        }

        // Add a new category
        item {
            LedgerCard {
                Text("Add a category", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlainField(
                        value = newName,
                        onChange = { newName = it },
                        placeholder = "e.g. Groceries",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                vm.add(newName, newKind)
                                newName = ""
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add category", tint = LedgerPalette.Gold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("WHICH SIDE?")
                Spacer(Modifier.height(6.dp))
                Segmented(KIND_OPTIONS, newKind.name) { newKind = CategoryKind.from(it) }
            }
        }

        KIND_ORDER.forEach { kind ->
            val rows = bySide[kind].orEmpty()
            item(key = "head-${kind.name}") {
                Column(Modifier.padding(top = 6.dp)) {
                    Text(
                        kind.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerPalette.InkMuted,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        KIND_BLURB.getValue(kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerPalette.InkMuted,
                    )
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty-${kind.name}") {
                    Text(
                        "Nothing here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LedgerPalette.InkMuted,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            } else {
                items(rows, key = { it.id }, contentType = { "category-row" }) { cat ->
                    // Position within its own side — the accessibility move
                    // actions step past the neighbour the user can actually see.
                    val index = rows.indexOfFirst { it.id == cat.id }
                    CategoryRow(
                        category = cat,
                        reorder = reorder,
                        canMoveUp = index > 0,
                        canMoveDown = index < rows.lastIndex,
                        onEdit = { editing = cat },
                        onMove = { delta -> vm.move(cat, delta) },
                        onDrop = { vm.reorder(order.map { it.id }) },
                        onEnabled = { vm.setEnabled(cat, it) },
                        onDelete = { vm.delete(cat) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }

    editing?.let { cat ->
        CategoryEditDialog(
            initial = cat,
            onConfirm = { name, kind ->
                if (name != cat.name) vm.rename(cat, name)
                if (kind != CategoryKind.from(cat.kind)) vm.setKind(cat, kind)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    reorder: ReorderState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDrop: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val dragging = reorder.draggingKey == category.id
    Box(Modifier.reorderableItem(reorder, category.id)) {
        LedgerCard(padding = PaddingValues(12.dp, 8.dp), elevation = if (dragging) 10.dp else 0.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Grab first, in reading order and under the thumb: a handle at the
                // end of a row this crowded is a hard target next to Delete.
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Reorder ${category.name}",
                    tint = if (dragging) LedgerPalette.Gold else LedgerPalette.InkMuted,
                    modifier = Modifier
                        .size(34.dp)
                        .dragHandle(reorder, category.id, onDrop)
                        // Dragging is unavailable to a screen reader, so the
                        // handle carries the one-slot moves the arrows used to.
                        .semantics {
                            customActions = buildList {
                                if (canMoveUp) add(CustomAccessibilityAction("Move up") { onMove(-1); true })
                                if (canMoveDown) add(CustomAccessibilityAction("Move down") { onMove(+1); true })
                            }
                        }
                        .padding(end = 6.dp),
                )
                CategoryMark(category.name, size = 30.dp, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f).padding(end = 4.dp).clickable(onClick = onEdit)) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (category.enabled) LedgerPalette.Ink else LedgerPalette.InkMuted,
                    )
                    Text(
                        if (category.enabled) "Tap to rename or move sides" else "Hidden from pickers",
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerPalette.InkMuted,
                    )
                }
                Switch(
                    checked = category.enabled,
                    onCheckedChange = onEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = LedgerPalette.Income),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LedgerPalette.Danger)
                }
            }
        }
    }
}

/** The side of the ledger the row [id] sits on, or null if it isn't in the list. */
private fun List<CategoryEntity>.kindOf(id: Long): String? =
    firstOrNull { it.id == id }?.kind

/**
 * Moves the row [from] to the slot the row [to] currently occupies.
 *
 * Both rows are always in the same section (the drag can't cross one), so taking
 * the row out and putting it back at the target's index lands it next to the row
 * it was dropped on in the grouped view as well as in this flat list.
 */
private fun List<CategoryEntity>.moveBefore(from: Any, to: Any): List<CategoryEntity> {
    val i = indexOfFirst { it.id == from }
    val j = indexOfFirst { it.id == to }
    if (i < 0 || j < 0 || i == j) return this
    return toMutableList().apply { add(j, removeAt(i)) }
}

/**
 * Rename a category and/or move it to the other side of the ledger.
 *
 * Both live in one dialog because they are the same decision from the user's
 * point of view — "this label is wrong for what I'm sorting" — and splitting them
 * would need two more affordances in an already-crowded row.
 */
@Composable
private fun CategoryEditDialog(
    initial: CategoryEntity,
    onConfirm: (name: String, kind: CategoryKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(CategoryKind.from(initial.kind)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LedgerPalette.Surface,
        title = { Text("Edit category", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                FieldLabel("NAME")
                Spacer(Modifier.height(6.dp))
                PlainField(value = name, onChange = { name = it }, placeholder = "")
                Spacer(Modifier.height(14.dp))
                FieldLabel("WHICH SIDE?")
                Spacer(Modifier.height(6.dp))
                Segmented(KIND_OPTIONS, kind.name) { kind = CategoryKind.from(it) }
                Spacer(Modifier.height(6.dp))
                Text(
                    KIND_BLURB.getValue(kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerPalette.InkMuted,
                )
                Spacer(Modifier.height(8.dp))
                // Renaming doesn't rewrite the transactions already filed under
                // the old label, so say so rather than let it surprise them.
                Text(
                    "Transactions already sorted keep the name they were saved with.",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerPalette.InkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), kind) },
            ) { Text("Save", color = LedgerPalette.Gold, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LedgerPalette.InkMuted) } },
    )
}
