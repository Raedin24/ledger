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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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

/** The sides in the order the screen lists them: out, both, in. */
private val KIND_ORDER = CategoryKind.DISPLAY_ORDER

private val KIND_BLURB = mapOf(
    CategoryKind.OUT to "Offered when you sort money going out.",
    CategoryKind.SHARED to "Offered in both directions.",
    CategoryKind.IN to "Offered when you sort money coming in.",
)

/** Options for the side control, shared by the add and edit dialogs. */
private val KIND_OPTIONS = KIND_ORDER.map { it.name to it.label }

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onPickUsual: () -> Unit,
    vm: CategoriesViewModel = hiltViewModel(),
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val restored by vm.restored.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var newKind by remember { mutableStateOf(CategoryKind.OUT) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }

    // The order on screen. It starts from the database and is reordered here
    // while a row is dragged, then saved once when the finger lifts.
    var order by remember { mutableStateOf(categories) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(
        listState = listState,
        // A row can only swap with a row on the same side. Dragging across a
        // section would look like changing the side, which the edit dialog is for.
        // Section headers have string keys and fail this check.
        canDrop = { from, to ->
            from is Long && to is Long && order.kindOf(from) == order.kindOf(to)
        },
        onMove = { from, to -> order = order.moveBefore(from, to) },
    )

    // Take the database order whenever no drag is in progress. Taking it during
    // a drag would move the row out from under the finger.
    LaunchedEffect(categories) {
        if (reorder.draggingKey == null) order = categories
    }

    // Grouped once per change, not on every scroll frame.
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
                "Star the ones you use most. Switch one off to hide it. Drag to reorder.",
                style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkMuted,
            )
            // The setup screen picks favourites in one go, faster than starring rows
            // one at a time.
            Text(
                "Pick your top categories",
                style = MaterialTheme.typography.labelLarge,
                color = LedgerPalette.Gold,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = onPickUsual)
                    .padding(vertical = 6.dp),
            )
            Text(
                when (restored) {
                    null -> "Restore default categories"
                    0 -> "All default categories are here"
                    1 -> "Restored 1 category"
                    else -> "Restored $restored categories"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (restored == null) LedgerPalette.Gold else LedgerPalette.InkMuted,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = restored == null, onClick = vm::restoreDefaults)
                    .padding(vertical = 6.dp),
            )
        }

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
                    // Position within its own side, so the accessibility move actions step
                    // past the neighbour the user can see.
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
                        onFavorite = { vm.setFavorite(cat, it) },
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
    onFavorite: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val dragging = reorder.draggingKey == category.id
    Box(Modifier.reorderableItem(reorder, category.id)) {
        LedgerCard(padding = PaddingValues(12.dp, 8.dp), elevation = if (dragging) 10.dp else 0.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The handle comes first, under the thumb. At the end of this busy row it
                // would sit right next to Delete.
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Reorder ${category.name}",
                    tint = if (dragging) LedgerPalette.Gold else LedgerPalette.InkMuted,
                    modifier = Modifier
                        .size(34.dp)
                        .dragHandle(reorder, category.id, onDrop)
                        // Screen readers can't drag, so the handle offers one-step move actions.
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
                        when {
                            !category.enabled -> "Hidden"
                            category.favorite -> "Starred · tap to edit"
                            else -> "Tap to edit"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerPalette.InkMuted,
                    )
                }
                // The star and the switch mean different things ("do I use this a lot?"
                // and "should this show at all?"), so they stay separate controls.
                IconButton(
                    onClick = { onFavorite(!category.favorite) },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        if (category.favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (category.favorite) {
                            "Remove ${category.name} from the ones offered up front"
                        } else {
                            "Offer ${category.name} up front"
                        },
                        tint = if (category.favorite) LedgerPalette.Gold else LedgerPalette.InkMuted,
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
 * Moves the row [from] to the slot the row [to] is in.
 *
 * Both rows are always in the same section, so this puts the row next to the
 * one it was dropped on, both in this flat list and in the grouped view.
 */
private fun List<CategoryEntity>.moveBefore(from: Any, to: Any): List<CategoryEntity> {
    val i = indexOfFirst { it.id == from }
    val j = indexOfFirst { it.id == to }
    if (i < 0 || j < 0 || i == j) return this
    return toMutableList().apply { add(j, removeAt(i)) }
}

/**
 * Renames a category or moves it to the other side, or both. They share one
 * dialog because to the user they are the same fix: this label is wrong.
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
                // Renaming doesn't change transactions already filed under the old name,
                // so say so.
                Text(
                    "Existing transactions keep the old name.",
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
