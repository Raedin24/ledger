package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.components.CategoryMark
import com.ledger.app.ui.theme.LedgerPalette

/** Screen title with a leading back arrow — shared by the editor screens. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LedgerPalette.Ink)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.displayMedium)
    }
}

/**
 * The app's flat, indicator-less text field.
 *
 * Built on [BasicTextField] rather than Material3's `TextField`, which was the
 * heaviest repeating composable in the app. `TextField` brings a decoration box
 * with an animated label, container and indicator — all of which this design
 * immediately turns off — and `TextFieldDefaults.colors(...)` allocates a
 * thirty-odd-colour object on *every* recomposition. Review lays out two of these
 * per queued transaction inside cards half a screen tall, so composing one card
 * as it scrolled in was paying for all of that twice over.
 */
@Composable
fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val typography = MaterialTheme.typography
    val textStyle = remember(typography) { typography.bodyMedium.copy(color = LedgerPalette.Ink) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = CursorBrush,
        modifier = modifier
            .background(LedgerPalette.SurfaceAlt, FieldShape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) { field ->
        Box {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerPalette.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            field()
        }
    }
}

private val FieldShape = RoundedCornerShape(12.dp)

/** Hoisted: a `SolidColor` per recomposition, per field, otherwise. */
private val CursorBrush = SolidColor(LedgerPalette.Gold)

/** Small caps label above a form field. */
@Composable
fun FieldLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = LedgerPalette.InkMuted)

/**
 * A row of mutually-exclusive pills — the app's one-of-N control. [options] maps
 * each stored value to the label shown for it.
 */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Pill(label, value == selected) { onSelect(value) } }
    }
}

@Composable
fun Pill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(PillShape)
            .background(if (active) LedgerPalette.Gold else LedgerPalette.SurfaceSunken)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) Color.White else LedgerPalette.InkSoft)
    }
}

private val PillShape = RoundedCornerShape(11.dp)

/**
 * The shared category picker: a bottom sheet of full-width rows.
 *
 * This was a dialog full of wrapped chips, which is what the design mock calls a
 * bottom sheet and what the default set had outgrown. The chip cloud had three
 * problems and they compound: the labels are wildly different widths ("Fuel" next
 * to "Investment Return"), so the rows ragged and there was no column for the eye
 * to run down; a dialog is sized to its content, so twenty-odd chips filled the
 * screen edge to edge with no way to scroll what didn't fit; and the whole thing
 * opened in the middle of the screen, furthest from the thumb that has to reach
 * it — on the Review card, directly on top of the transaction being sorted.
 *
 * Rows fix all three. One category per line, left-aligned on its tinted mark, so
 * the name is scanned by its icon and colour before it is read. The sheet rises
 * from the bottom edge, lands under the thumb, scrolls when it has to, and leaves
 * the card it belongs to visible above it.
 *
 * [title] is worth setting where the picker means something more specific than
 * choosing — re-filing a transaction, or applying one label to a selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Choose a category",
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(categories, query) {
        val q = query.trim()
        if (q.isEmpty()) categories else categories.filter { it.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LedgerPalette.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = LedgerPalette.Hairline) },
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        // Only once the list is long enough that reading it beats typing. Below
        // that a search box is one more thing to look past on the way to a label
        // that was already on screen.
        if (categories.size > SEARCHABLE_FROM) {
            Spacer(Modifier.height(12.dp))
            PlainField(
                value = query,
                onChange = { query = it },
                placeholder = "Search categories",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // weight(fill = false): takes the space it needs and no more, so a short
        // list makes a short sheet, and a long one stops at the sheet's own
        // ceiling and scrolls inside it.
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        "No category matches “${query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LedgerPalette.InkMuted,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    )
                }
            }
            itemsIndexed(shown, key = { _, name -> name }) { index, name ->
                PickerRow(
                    name = name,
                    selected = name == current,
                    // Hairlines between rows, not under the last one — a rule
                    // above the sheet's bottom padding reads as a cut-off list.
                    divider = index < shown.lastIndex,
                    onClick = { onPick(name) },
                )
            }
        }
    }
}

/** Category count past which the picker offers a search field. */
private const val SEARCHABLE_FROM = 12

@Composable
private fun PickerRow(name: String, selected: Boolean, divider: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 11dp on a 34dp mark clears the 48dp minimum touch target, which the
            // chips it replaced did not.
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        CategoryMark(name, size = 34.dp)
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = LedgerPalette.Ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Currently chosen",
                tint = LedgerPalette.Gold,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (divider) {
        Box(
            Modifier
                .padding(start = 18.dp + 34.dp + 13.dp, end = 18.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(LedgerPalette.Hairline),
        )
    }
}

/** Single-line text-entry dialog (used for rename / quick add). */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LedgerPalette.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { PlainField(value = text, onChange = { text = it }, placeholder = "") },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                Text(confirmLabel, color = LedgerPalette.Gold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = LedgerPalette.InkMuted) }
        },
    )
}
