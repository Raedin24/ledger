package com.ledger.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.budget.BudgetLevel
import com.ledger.app.data.budget.BudgetStatus
import com.ledger.app.ui.components.CategoryBar
import com.ledger.app.ui.components.CategoryMark
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.formatCedis
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.vm.BudgetsViewModel
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Monthly limits, one per spending category.
 *
 * This always shows the current month. Earlier months are what the breakdown
 * screen is for.
 *
 * The list is sorted worst first, so the categories that need attention are
 * at the top. That means rows move during the month, which is fine because
 * this list is read from the top.
 */
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    vm: BudgetsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    // The category whose limit is being entered. The dialog is the same for a
    // new or existing budget, filled with the current limit if there is one.
    var editing by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            ScreenHeader("Budgets", onBack)
            Text(
                "You'll get an alert at 80% and when a limit is passed.",
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerPalette.InkMuted,
            )
        }

        if (state.budgets.isNotEmpty()) {
            item { TotalCard(state.totalSpentMinor, state.totalLimitMinor) }
        }

        items(state.budgets, key = { it.category }, contentType = { "budget-row" }) { status ->
            BudgetRow(
                status = status,
                onEdit = { editing = status.category },
                onDelete = { vm.remove(status.category) },
            )
        }

        if (state.loaded && state.budgets.isEmpty()) {
            item {
                LedgerCard {
                    Text(
                        "No budgets yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = LedgerPalette.Ink,
                    )
                }
            }
        }

        item {
            Text(
                if (state.available.isEmpty() && state.budgets.isNotEmpty()) {
                    "Every spending category has a limit"
                } else {
                    "Add a budget"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.available.isEmpty()) LedgerPalette.InkMuted else LedgerPalette.Gold,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = state.available.isNotEmpty()) { picking = true }
                    .padding(vertical = 10.dp),
            )
        }

        item { Spacer(Modifier.height(90.dp)) }
    }

    if (picking) {
        CategoryPickerSheet(
            categories = state.available,
            current = null,
            onPick = { picking = false; editing = it },
            onDismiss = { picking = false },
            title = "Budget which category?",
        )
    }

    editing?.let { category ->
        val existing = state.budgets.firstOrNull { it.category == category }
        BudgetAmountDialog(
            category = category,
            // Locale.UK like every other amount in the app. The default locale could
            // write "120,00", which the parser below would refuse.
            initialMajor = existing?.let { "%.2f".format(Locale.UK, it.limitMinor / 100.0) }.orEmpty(),
            onConfirm = { amount ->
                vm.setBudget(category, amount)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/**
 * All of the month's limits added up, in one line that shows whether the plan
 * as a whole is holding.
 */
@Composable
private fun TotalCard(spentMinor: Long, limitMinor: Long) {
    val fraction = if (limitMinor <= 0) 0f else spentMinor.toFloat() / limitMinor
    LedgerCard {
        Text("Budgeted this month", style = MaterialTheme.typography.labelSmall, color = LedgerPalette.InkMuted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatCedis(spentMinor),
                style = MaterialTheme.typography.headlineMedium,
                color = LedgerPalette.Ink,
            )
            Text(
                " of ${formatCedis(limitMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerPalette.InkMuted,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(9.dp))
        CategoryBar(fraction, levelColor(BudgetLevel.of(fraction)))
    }
}

@Composable
private fun BudgetRow(status: BudgetStatus, onEdit: () -> Unit, onDelete: () -> Unit) {
    val level = status.level
    val accent = levelColor(level)
    LedgerCard(padding = PaddingValues(14.dp, 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryMark(status.category, size = 34.dp, modifier = Modifier.padding(end = 11.dp))
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(
                    status.category,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LedgerPalette.Ink,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${formatCedis(status.spentMinor)} of ${formatCedis(status.limitMinor)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LedgerPalette.InkMuted,
                )
            }
            Text(
                "${(status.fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove the ${status.category} budget",
                    tint = LedgerPalette.Danger,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        CategoryBar(status.fraction, accent)
        Spacer(Modifier.height(6.dp))
        Text(
            when (level) {
                BudgetLevel.OVER -> "${formatCedis(-status.remainingMinor)} over"
                BudgetLevel.NEAR -> "${formatCedis(status.remainingMinor)} left"
                BudgetLevel.OK -> "${formatCedis(status.remainingMinor)} left"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (level == BudgetLevel.OK) LedgerPalette.InkMuted else accent,
        )
    }
}

/**
 * Green while there is room, gold when close, terracotta when over: the same
 * colours the rest of the app uses for money.
 */
private fun levelColor(level: BudgetLevel): Color = when (level) {
    BudgetLevel.OK -> LedgerPalette.Income
    BudgetLevel.NEAR -> LedgerPalette.Gold
    BudgetLevel.OVER -> LedgerPalette.Spend
}

/**
 * Amount entry in cedis, not pesewas; the repository converts. An empty or
 * unreadable amount removes the budget, which is what clearing the field means.
 */
@Composable
private fun BudgetAmountDialog(
    category: String,
    initialMajor: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialMajor) }
    // Commas and spaces are removed rather than rejected, since "1,200" is a
    // normal way to type a limit.
    val amount = text.replace(",", "").replace(" ", "").trim().toDoubleOrNull()
    // Check what will actually be stored: 0.004 is positive but rounds to zero.
    val limitMinor = amount?.let { (it * 100).roundToLong() } ?: 0L
    val valid = limitMinor > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LedgerPalette.Surface,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Monthly limit for $category", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                PlainField(
                    value = text,
                    onChange = { text = it },
                    placeholder = "GHS 0.00",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onConfirm(amount!!) },
                enabled = valid,
            ) {
                Text(
                    "Save",
                    color = if (valid) LedgerPalette.Gold else LedgerPalette.InkMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = LedgerPalette.InkMuted) }
        },
    )
}
