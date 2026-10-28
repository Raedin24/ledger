package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.db.CashMove
import com.ledger.app.ui.components.CategoryMark
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.directionLabel
import com.ledger.app.ui.components.formatCedis
import com.ledger.app.ui.components.institutionLabel
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.vm.DetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    vm: DetailViewModel = hiltViewModel(),
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LedgerPalette.Ink)
            }
            Text("Transaction", style = MaterialTheme.typography.titleMedium, color = LedgerPalette.Ink, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmingDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LedgerPalette.Danger)
            }
        }
        TransactionDetails(
            vm = vm,
            onGone = onBack,
            confirmingDelete = confirmingDelete,
            onConfirmingDelete = { confirmingDelete = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The same details as a half-screen sheet over the list it was opened from.
 *
 * Used for cash entries and for transactions waiting in review. Both are a
 * quick look and fix, so the user keeps their place in the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(id: Long, onDismiss: () -> Unit) {
    // One ViewModel for the sheet, pointed at each transaction as it opens. The
    // sheet is outside the nav graph, so a ViewModel per id would stay alive as
    // long as the app is open.
    val vm: DetailViewModel = hiltViewModel(key = "txn-sheet")
    LaunchedEffect(id) { vm.bind(id) }
    val txn by vm.transaction.collectAsStateWithLifecycle()
    // Wait until this row has loaded. The sheet measures its content once, as
    // it opens, to decide where half-open is. Opened around an empty or old
    // body, it would end up stuck at the bottom edge.
    if (txn?.id != id) return
    var confirmingDelete by remember(id) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fresh per transaction, so no state carries over from the last sheet.
        sheetState = key(id) { rememberModalBottomSheetState() },
        containerColor = LedgerPalette.Background,
    ) {
        TransactionDetails(
            vm = vm,
            onGone = onDismiss,
            confirmingDelete = confirmingDelete,
            onConfirmingDelete = { confirmingDelete = it },
            modifier = Modifier.fillMaxWidth(),
            expectedId = id,
        )
    }
}

@Composable
private fun TransactionDetails(
    vm: DetailViewModel,
    onGone: () -> Unit,
    confirmingDelete: Boolean,
    onConfirmingDelete: (Boolean) -> Unit,
    modifier: Modifier,
    /**
     * The sheet's transaction. Until the ViewModel catches up it still holds the
     * previous one, which must not flash up.
     */
    expectedId: Long? = null,
) {
    val txn by vm.transaction.collectAsStateWithLifecycle()
    if (expectedId != null && txn != null && txn?.id != expectedId) return
    val categories by vm.categories.collectAsStateWithLifecycle()

    var pickingCategory by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }

    val t = txn
    if (t == null) {
        Text(
            "This transaction is no longer here.",
            style = MaterialTheme.typography.bodyMedium,
            color = LedgerPalette.InkMuted,
            modifier = Modifier.padding(24.dp),
        )
        return
    }

    val isCredit = t.direction == "CREDIT"
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
    ) {
        // Top: status, amount, who and when.
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StatusPill(t.needsReview)
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
                Text(
                    (if (isCredit) "+" else "−") + formatCedis(t.amountMinor),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (isCredit) LedgerPalette.Income else LedgerPalette.Spend,
                )
            }
            Spacer(Modifier.height(6.dp))
            SelectionContainer {
                Text(
                    listOfNotNull(
                        t.counterparty,
                        institutionLabel(t.institution),
                        stamp.format(Instant.ofEpochMilli(t.occurredAt)),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerPalette.InkSoft,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Editable: category and person.
        LedgerCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            EditRow(
                label = "Category",
                onClick = { pickingCategory = true },
                trailing = { CategoryMark(t.category, size = 24.dp) },
                value = t.category ?: "Uncategorised",
                divider = true,
            )
            EditRow(
                label = "Person",
                onClick = { editingPerson = true },
                value = t.person?.takeUnless { it.isBlank() } ?: "Add a person",
                muted = t.person.isNullOrBlank(),
            )
        }

        if (t.institution != "CASH") {
            CashSection(
                direction = t.direction,
                cashMove = t.cashMove,
                selfTransfer = t.selfTransfer,
                onMove = vm::moveToCash,
                onKeep = vm::keepOutOfCash,
                onUndo = vm::undoMoveToCash,
            )
        }

        Spacer(Modifier.height(14.dp))
        FieldCaption("Your note")
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LedgerPalette.Surface)
                .clickable { editingNote = true }.padding(14.dp),
        ) {
            Text(
                t.notes?.takeUnless { it.isBlank() } ?: "Add a note…",
                style = MaterialTheme.typography.bodyLarge,
                color = if (t.notes.isNullOrBlank()) LedgerPalette.InkMuted else LedgerPalette.Ink,
            )
        }

        Spacer(Modifier.height(16.dp))
        FieldCaption("Details")
        // Selectable, so the user can copy a reference or an exact amount.
        SelectionContainer {
            LedgerCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                FactRow("Amount", formatCedis(t.amountMinor), divider = true)
                FactRow("Date & time", stamp.format(Instant.ofEpochMilli(t.occurredAt)), divider = true)
                FactRow("Direction", directionLabel(t.direction), divider = true)
                FactRow("Account", institutionLabel(t.institution), divider = true)
                FactRow("Fee", t.feeMinor?.let { formatCedis(it) } ?: "None", divider = t.reference != null || t.balanceMinor != null)
                t.balanceMinor?.let { FactRow("Balance after", formatCedis(it), divider = t.reference != null) }
                t.reference?.let { FactRow("Reference", it, divider = false) }
            }
        }

        t.referenceHint?.let { matched ->
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LedgerPalette.SurfaceSunken).padding(14.dp)) {
                Column {
                    FieldCaption("Matched reference")
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer {
                        Text("\"$matched\"", style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkSoft)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(LedgerPalette.SurfaceSunken).clickable { onConfirmingDelete(true) }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Delete transaction", style = MaterialTheme.typography.titleMedium, color = LedgerPalette.Danger, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(40.dp))
    }

    if (pickingCategory) {
        CategoryPickerSheet(
            // Only categories for this transaction's direction (see CategoryOptions).
            categories = categories.forDirection(txn?.direction ?: "DEBIT"),
            current = txn?.category,
            onPick = { vm.setCategory(it); pickingCategory = false },
            onDismiss = { pickingCategory = false },
            title = if (txn?.category == null) "Choose a category" else "Re-file this transaction",
        )
    }
    if (editingPerson) {
        TextInputDialog(
            title = "Person",
            initial = txn?.person.orEmpty(),
            confirmLabel = "Save",
            onConfirm = { vm.setPerson(it); editingPerson = false },
            onDismiss = { editingPerson = false },
        )
    }
    if (editingNote) {
        TextInputDialog(
            title = "Your note",
            initial = txn?.notes.orEmpty(),
            confirmLabel = "Save",
            onConfirm = { vm.setNote(it); editingNote = false },
            onDismiss = { editingNote = false },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onConfirmingDelete(false) },
            containerColor = LedgerPalette.Surface,
            title = { Text("Delete this transaction?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("This can't be undone.", style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkSoft) },
            confirmButton = {
                TextButton(onClick = { onConfirmingDelete(false); vm.delete(onGone) }) {
                    Text("Delete", color = LedgerPalette.Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { onConfirmingDelete(false) }) { Text("Cancel", color = LedgerPalette.InkMuted) } },
        )
    }
}

/**
 * The cash control for a wallet transaction. For a detected withdrawal it asks
 * the question; once moved it offers undo. Otherwise it lets the user move the
 * transaction by hand, for withdrawals that weren't detected.
 */
@Composable
private fun CashSection(
    direction: String,
    cashMove: String?,
    selfTransfer: Boolean,
    onMove: () -> Unit,
    onKeep: () -> Unit,
    onUndo: () -> Unit,
) {
    val debit = direction != "CREDIT"
    when {
        cashMove == CashMove.OFFER -> {
            Spacer(Modifier.height(14.dp))
            CashMovePrompt(direction, onMove, onKeep)
        }
        cashMove == CashMove.MOVED -> {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LedgerPalette.Surface)
                    .padding(start = 15.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (debit) "Moved to cash" else "Taken from cash",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LedgerPalette.Ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onUndo) { Text("Undo", color = LedgerPalette.Gold, fontWeight = FontWeight.Bold) }
            }
        }
        // A transfer to one of the user's own wallets is already not spending.
        !selfTransfer -> {
            Spacer(Modifier.height(10.dp))
            Text(
                if (debit) "Move to cash" else "Take from cash",
                style = MaterialTheme.typography.titleMedium,
                color = LedgerPalette.Gold,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onMove).padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(needsReview: Boolean) {
    val (label, color, bg) = if (needsReview) {
        Triple("Needs review", LedgerPalette.GoldDeep, LedgerPalette.ChipBg)
    } else {
        Triple("Recorded", LedgerPalette.Income, androidx.compose.ui.graphics.Color(0xFFE5ECDD))
    }
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    divider: Boolean = false,
    muted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LedgerPalette.InkMuted, modifier = Modifier.width(76.dp))
        if (trailing != null) {
            trailing()
            Spacer(Modifier.width(9.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) LedgerPalette.InkMuted else LedgerPalette.Ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerPalette.InkMuted)
    }
    if (divider) Divider()
}

@Composable
private fun FactRow(key: String, value: String, divider: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.Ink, fontWeight = FontWeight.Medium)
    }
    if (divider) Divider()
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LedgerPalette.Hairline))
}

@Composable
private fun FieldCaption(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LedgerPalette.InkMuted,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}
