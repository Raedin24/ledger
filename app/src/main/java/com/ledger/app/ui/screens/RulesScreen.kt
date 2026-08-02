package com.ledger.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.db.CategoryOptions
import com.ledger.app.data.db.RuleEntity
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.MONEY_IN
import com.ledger.app.ui.components.MONEY_OUT
import com.ledger.app.ui.components.directionLabel
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.vm.RulesViewModel

@Composable
fun RulesScreen(
    onBack: () -> Unit,
    vm: RulesViewModel = hiltViewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    // Non-null = the editor is open (a fresh entity for "add", an existing one for "edit").
    var editing by remember { mutableStateOf<RuleEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            ScreenHeader("Rules", onBack)
            Text(
                "Rules auto-sort a sender every time. Highest priority wins when more than one matches.",
                style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkMuted,
            )
        }

        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(LedgerPalette.SurfaceSunken)
                    .clickable { editing = blankRule(categories.spending.firstOrNull().orEmpty()) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = LedgerPalette.Gold)
                    Spacer(Modifier.size(6.dp))
                    Text("Add a rule", style = MaterialTheme.typography.titleMedium, color = LedgerPalette.Gold, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                LedgerCard {
                    Text("No rules yet.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Confirming a transaction in Review with \"Always sort this sender this way\" creates one automatically — or add one here.",
                        style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkMuted,
                    )
                }
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RuleRow(rule, onEdit = { editing = rule }, onToggle = { vm.setEnabled(rule, it) }, onDelete = { vm.delete(rule) })
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }

    editing?.let { rule ->
        RuleEditorDialog(
            initial = rule,
            categories = categories,
            onSave = { vm.save(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

private fun blankRule(defaultCategory: String) = RuleEntity(
    id = 0, counterparty = "", direction = "DEBIT", category = defaultCategory,
    person = null, matchType = "EXACT", priority = 0, enabled = true, lastMatchedAtMillis = null,
)

@Composable
private fun RuleRow(rule: RuleEntity, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    LedgerCard(padding = androidx.compose.foundation.layout.PaddingValues(12.dp, 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(end = 4.dp)) {
                Text(
                    rule.counterparty.ifBlank { "(any)" },
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (rule.enabled) LedgerPalette.Ink else LedgerPalette.InkMuted,
                )
                Text(
                    buildString {
                        append(directionLabel(rule.direction))
                        append(" → ").append(rule.category)
                        rule.person?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                        append("  ·  ").append(if (rule.matchType == "CONTAINS") "contains" else "exact")
                        append("  ·  p").append(rule.priority)
                    },
                    style = MaterialTheme.typography.labelSmall, color = LedgerPalette.InkMuted,
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = LedgerPalette.Income),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LedgerPalette.Danger)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorDialog(
    initial: RuleEntity,
    categories: CategoryOptions,
    onSave: (RuleEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var counterparty by remember { mutableStateOf(initial.counterparty) }
    var category by remember { mutableStateOf(initial.category) }
    var person by remember { mutableStateOf(initial.person ?: "") }
    var direction by remember { mutableStateOf(initial.direction) }
    var matchType by remember { mutableStateOf(initial.matchType) }
    var priority by remember { mutableIntStateOf(initial.priority) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LedgerPalette.Surface)
                .verticalScroll(rememberScrollState()).padding(18.dp),
        ) {
            Text(if (initial.id == 0L) "New rule" else "Edit rule", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            FieldLabel("Sender (counterparty)")
            PlainField(counterparty, { counterparty = it }, "Merchant name or number", Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            FieldLabel("Direction")
            // Same two words the rest of the app uses — see directionLabel.
            Segmented(listOf("DEBIT" to MONEY_OUT, "CREDIT" to MONEY_IN), direction) { direction = it }

            Spacer(Modifier.height(12.dp))
            FieldLabel("Match")
            Segmented(listOf("EXACT" to "Exact", "CONTAINS" to "Contains"), matchType) { matchType = it }

            Spacer(Modifier.height(12.dp))
            FieldLabel("Category")
            // Follows the direction above: a rule that files credits shouldn't
            // offer "Rent". Switching direction can strand the current pick, so
            // it falls back to the first label on the new side.
            val offered = categories.forDirection(direction)
            LaunchedEffect(direction) {
                if (category !in offered) category = offered.firstOrNull().orEmpty()
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                offered.forEach { c -> Pill(c, c == category) { category = c } }
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel("Person (optional)")
            PlainField(person, { person = it }, "Who this is usually with", Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("Priority")
                Spacer(Modifier.weight(1f))
                Stepper(priority, onMinus = { if (priority > 0) priority-- }, onPlus = { priority++ })
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", color = LedgerPalette.InkMuted, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp))
                Spacer(Modifier.size(8.dp))
                val valid = counterparty.isNotBlank() && category.isNotBlank()
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (valid) LedgerPalette.Income else LedgerPalette.SurfaceSunken)
                        .clickable(enabled = valid) {
                            onSave(
                                initial.copy(
                                    counterparty = counterparty.trim(), category = category, direction = direction,
                                    matchType = matchType, priority = priority,
                                    person = person.trim().ifBlank { null },
                                ),
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Save", color = if (valid) Color.White else LedgerPalette.InkMuted, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", onMinus)
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp))
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(LedgerPalette.SurfaceSunken).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = LedgerPalette.InkSoft)
    }
}
