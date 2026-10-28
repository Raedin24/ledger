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
import androidx.compose.foundation.text.KeyboardOptions
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

/** A screen title with a back arrow, used by the editor screens. */
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
 * The app's plain text field, with no underline or label.
 *
 * It is built on [BasicTextField] rather than Material3's `TextField`.
 * `TextField` adds an animated label, a container and an indicator, all of
 * which this design turns off, and it is costly to redraw. The Review screen
 * shows two fields on every queued card, so the lighter field keeps scrolling
 * smooth.
 */
@Composable
fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val typography = MaterialTheme.typography
    val textStyle = remember(typography) { typography.bodyMedium.copy(color = LedgerPalette.Ink) }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        cursorBrush = CursorBrush,
        // Always full width. A bare BasicTextField is only as wide as its text.
        // A caller's weight or padding still applies.
        modifier = modifier
            .fillMaxWidth()
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

/** Created once rather than for every field on every redraw. */
private val CursorBrush = SolidColor(LedgerPalette.Gold)

/** Small-caps label above a form field. */
@Composable
fun FieldLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelSmall, color = LedgerPalette.InkMuted)

/**
 * A row of pills where exactly one is selected. [options] maps each stored
 * value to the label shown for it.
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
 * The shared category picker: a bottom sheet with one category per row.
 *
 * Each row shows the category's coloured icon first, so the user can find it
 * by icon and colour before reading. The sheet rises from the bottom, within
 * reach of the thumb, scrolls when the list is long, and leaves the card it
 * belongs to visible above it.
 *
 * Set [title] when the picker does something more specific than choosing,
 * such as re-filing a transaction or labelling a selection.
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
        // Only show a search box once the list is long enough to need one.
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

        // weight(fill = false) takes only the space it needs: a short list makes a
        // short sheet, and a long one stops at the sheet's maximum and scrolls.
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
                    // Lines between rows, but not under the last one.
                    divider = index < shown.lastIndex,
                    onClick = { onPick(name) },
                )
            }
        }
    }
}

/** How many categories before the picker shows a search box. */
private const val SEARCHABLE_FROM = 12

@Composable
private fun PickerRow(name: String, selected: Boolean, divider: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 11dp around a 34dp icon meets the 48dp minimum touch target.
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

/** A dialog with one line of text, used to rename or quickly add. */
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
