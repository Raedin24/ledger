package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.CategoryKind
import com.ledger.app.ui.components.categoryVisual
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.vm.CategorySetupViewModel

/**
 * "Which of these do you actually use?" — offered once, after the first sender is
 * added, and reachable from the category editor afterwards.
 *
 * Chips rather than the rows the picker sheet uses: the job here is comparing a
 * whole set at a glance and tapping several, which wants density and wants the
 * eye to move in two dimensions. The picker's job is finding one known label in a
 * list, which wants a single column. Same marks, same colours, different task.
 *
 * Nothing here is destructive — an unpicked category is switched off, not
 * deleted — so the screen can afford to be quick rather than careful, and says so
 * rather than making the user wonder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySetupScreen(
    onDone: () -> Unit,
    vm: CategorySetupViewModel = hiltViewModel(),
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    val bySide = remember(categories) { categories.groupBy { CategoryKind.from(it.kind) } }
    // Both directions need something to offer. A side left empty falls back to
    // the entire seeded list in the pickers, so saving it would silently do the
    // opposite of what this screen just promised. Shared labels count for both.
    val ready = remember(categories, selected) {
        val picked = categories.filter { it.name in selected }
        picked.any { it.kind != CategoryKind.IN.name } && picked.any { it.kind != CategoryKind.OUT.name }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text("Pick your categories", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap the ones you'll actually use. They go to the front of every " +
                    "picker; the rest are switched off and can be turned back on " +
                    "any time in Settings → Categories. Nothing is deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerPalette.InkMuted,
            )
        }

        CategoryKind.DISPLAY_ORDER.forEach { kind ->
            val rows = bySide[kind].orEmpty()
            if (rows.isEmpty()) return@forEach
            item(key = "head-${kind.name}") {
                Spacer(Modifier.height(6.dp))
                Text(
                    kind.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerPalette.InkMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
            item(key = "chips-${kind.name}") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rows.forEach { cat ->
                        ChoiceChip(cat, cat.name in selected) { vm.toggle(cat.name) }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Text(
                "Keep all ${categories.size}",
                style = MaterialTheme.typography.labelLarge,
                color = LedgerPalette.Gold,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.selectAll(categories.map { it.name }) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            SetupButton(
                label = if (ready) "Use these ${selected.size}" else "Pick at least one of each",
                enabled = ready,
            ) { vm.save(onDone) }
            Text(
                "Skip for now",
                style = MaterialTheme.typography.titleMedium,
                color = LedgerPalette.InkMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.skip(onDone) }
                    .padding(vertical = 14.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val ChipShape = RoundedCornerShape(20.dp)

/**
 * A category as a togglable chip: its own tint when picked, flat and drained when
 * not, so the picked set reads as a block of colour rather than a set of
 * checkboxes to audit one at a time.
 */
@Composable
private fun ChoiceChip(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val visual = categoryVisual(category.name)
    Row(
        Modifier
            .clip(ChipShape)
            .background(if (selected) visual.tint else LedgerPalette.SurfaceSunken)
            .then(if (selected) Modifier.border(1.5.dp, visual.accent, ChipShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 13.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            if (selected) Icons.Default.Check else visual.icon,
            contentDescription = null,
            tint = if (selected) visual.accent else LedgerPalette.InkMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            category.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) LedgerPalette.Ink else LedgerPalette.InkSoft,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SetupButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) LedgerPalette.Income else LedgerPalette.SurfaceSunken)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White else LedgerPalette.InkMuted,
            fontWeight = FontWeight.Bold,
        )
    }
}
