package com.ledger.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.db.CategoryOptions
import com.ledger.app.data.db.TransactionEntity
import com.ledger.app.tutorial.TutorialStep
import com.ledger.app.ui.components.CheckCircleIllustration
import com.ledger.app.ui.components.CoachBeak
import com.ledger.app.ui.components.CoachMark
import com.ledger.app.ui.components.CoachMarkHost
import com.ledger.app.ui.components.CoachScrim
import com.ledger.app.ui.components.CoachTone
import com.ledger.app.ui.components.EmptyState
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.ReviewSkeleton
import com.ledger.app.ui.components.coachLabel
import com.ledger.app.ui.components.directionLabel
import com.ledger.app.ui.components.formatCedis
import com.ledger.app.ui.components.institutionLabel
import com.ledger.app.ui.components.spotlight
import com.ledger.app.data.db.CashMove
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.theme.LedgerThemeTokens
import com.ledger.app.ui.vm.ReviewViewModel
import com.ledger.app.ui.vm.TutorialViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The full date and time, because telling apart two same-day transactions with
 * the same person often decides how to file them.
 */
private val reviewStamp =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(
    vm: ReviewViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    // Null until the first read arrives, so the screen doesn't say "All caught
    // up" before it knows whether anything is waiting.
    val queue by vm.queue.collectAsStateWithLifecycle()
    val items = queue.orEmpty()
    val loading = queue == null
    val message by vm.message.collectAsStateWithLifecycle()
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val olderCount by vm.olderCount.collectAsStateWithLifecycle()
    val cleared by vm.cleared.collectAsStateWithLifecycle()
    var clearing by remember { mutableStateOf(false) }
    val colors = LedgerThemeTokens.colors

    // The guide's rule step. It needs something in the queue to point at.
    val step = TutorialStep.REVIEW_RULE
    // "Try it" hides the guide's card so the user can work, but the step stays
    // open until a rule is actually created.
    var ruleStepTucked by remember { mutableStateOf(false) }
    val ruleStepLive = tutorial.showing(step) && items.isNotEmpty()
    val showRuleStep = ruleStepLive && !ruleStepTucked
    // The banner after saving with a rule means the step is done.
    LaunchedEffect(message) { if (message != null && tutorial.showing(step)) tutorialVm.complete(step) }
    // With nothing to sort, the step has nothing to point at, so skip it. Wait
    // until the queue is known to be empty, not just not loaded yet.
    LaunchedEffect(loading, items.isEmpty(), tutorial.current) {
        if (tutorial.showing(step) && !loading && items.isEmpty()) tutorialVm.skip(step)
    }

    // The rule step points at the rule switch, near the bottom of a tall card,
    // so it starts below the fold and behind the guide's card. Scrolling to the
    // card's position isn't enough, so the switch's row asks to be brought into
    // view, with extra space so it isn't hidden under the guide's card.
    val ruleRow = remember { BringIntoViewRequester() }
    val coachReservePx = with(LocalDensity.current) { RULE_STEP_RESERVE.toPx() }
    LaunchedEffect(showRuleStep, items.firstOrNull()?.id) {
        if (!showRuleStep) return@LaunchedEffect
        // Wait one frame so the card has been laid out.
        withFrameNanos { }
        ruleRow.bringIntoView(Rect(0f, 0f, 1f, coachReservePx))
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("Review", style = MaterialTheme.typography.displayMedium)
            Text(
                when {
                    loading -> "Checking what's waiting…"
                    items.isEmpty() -> "All caught up"
                    else -> "${items.size} to review"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
            if (items.isNotEmpty()) {
                Text(
                    "Clear queue",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.gold,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { clearing = true }.padding(top = 6.dp, bottom = 2.dp),
                )
            }
        }

        // A new rule also sorts the sender's other queued rows. Say so, or the
        // queue shrinks with no explanation.
        message?.let { text ->
            item { RuleSweepBanner(text, onDismiss = vm::clearMessage) }
        }
        cleared?.let { text ->
            item { RuleSweepBanner(text, onDismiss = vm::clearClearedMessage) }
        }
        if (loading) {
            item { ReviewSkeleton(Modifier.padding(top = 4.dp)) }
        } else if (items.isEmpty()) {
            item {
                EmptyState(
                    title = "All caught up",
                    message = "Nothing to sort.",
                    modifier = Modifier.padding(top = 40.dp),
                    illustration = { CheckCircleIllustration() },
                )
            }
        } else {
            items(items, key = { it.id }, contentType = { "review-card" }) { txn ->
                val spotlit = ruleStepLive && txn.id == items.first().id
                ReviewCard(
                    txn = txn,
                    vm = vm,
                    categories = categories,
                    spotlightRule = spotlit,
                    ruleRowRequester = ruleRow.takeIf { spotlit },
                )
            }
        }
        item { Spacer(Modifier.height(if (showRuleStep) RULE_STEP_RESERVE else 90.dp)) }
    }

        if (clearing) {
            ClearQueueDialog(
                total = items.size,
                older = olderCount,
                onClear = { olderOnly -> clearing = false; vm.clearQueue(olderOnly) },
                onDismiss = { clearing = false },
            )
        }

        CoachScrim(showRuleStep)
        CoachMarkHost(
            visible = showRuleStep,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            CoachMark(
                label = coachLabel(step, if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL),
                title = step.title,
                body = step.body,
                primaryLabel = step.primary,
                onPrimary = { ruleStepTucked = true },
                secondaryLabel = step.secondary,
                onSecondary = { tutorialVm.skip(step) },
                onDismiss = tutorialVm::dismiss,
                tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL,
                beak = CoachBeak.Top(0.5f),
                step = step.number,
            )
        }
    }
}

/**
 * Clears the backlog an inbox import leaves behind. "Older only" is offered
 * first, since this month's rows are still worth sorting.
 */
@Composable
private fun ClearQueueDialog(total: Int, older: Int, onClear: (olderOnly: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LedgerPalette.Surface,
        title = { Text("Clear the review queue?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "Cleared items stay in History, uncategorised.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerPalette.InkSoft,
                )
                Spacer(Modifier.height(10.dp))
                if (older > 0) {
                    ClearChoice("Before this month ($older)") { onClear(true) }
                }
                ClearChoice("Everything ($total)") { onClear(false) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LedgerPalette.InkMuted) } },
    )
}

@Composable
private fun ClearChoice(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = LedgerPalette.Gold,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

@Composable
private fun RuleSweepBanner(text: String, onDismiss: () -> Unit) {
    val colors = LedgerThemeTokens.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.income.copy(alpha = 0.14f), BannerShape)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.Ink, modifier = Modifier.weight(1f))
        Text("✕", style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ReviewCard(
    txn: TransactionEntity,
    vm: ReviewViewModel,
    // Collected once by the screen and passed down, instead of every card
    // subscribing on its own.
    categories: CategoryOptions,
    spotlightRule: Boolean = false,
    /**
     * Set only on the card the guide's rule step points at, so the guide can
     * scroll its rule switch into view. Null on every other card.
     */
    ruleRowRequester: BringIntoViewRequester? = null,
) {
    val colors = LedgerThemeTokens.colors
    var selected by remember { mutableStateOf<String?>(null) }
    var person by remember { mutableStateOf(txn.person ?: "") }
    var note by remember { mutableStateOf("") }
    // Off by default. A rule files everything this sender sends from now on,
    // so it should be something the user chooses, not something they miss.
    var alwaysRule by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    // Keyed to the row's data, so it is formatted once per row and not again
    // on every keystroke in the card's fields.
    val stamp = remember(txn.institution, txn.occurredAt) {
        "${institutionLabel(txn.institution)} · ${reviewStamp.format(Instant.ofEpochMilli(txn.occurredAt))}"
    }
    val amount = remember(txn.amountMinor, txn.direction) {
        (if (txn.direction == "CREDIT") "+" else "−") + formatCedis(txn.amountMinor)
    }

    LedgerCard {
        // The amount comes first and never shrinks. A long counterparty is cut
        // short instead of pushing the amount off the screen.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                txn.counterparty ?: "Unknown sender",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 10.dp),
            )
            Text(
                amount,
                style = MaterialTheme.typography.titleLarge,
                color = if (txn.direction == "CREDIT") colors.income else colors.spend,
                maxLines = 1,
                softWrap = false,
            )
        }
        // Which wallet and when. Without these, the same counterparty appearing
        // twice (once per wallet, or twice in a day) is hard to tell apart.
        Text(
            stamp,
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (txn.cashMove == CashMove.OFFER) {
            Spacer(Modifier.height(10.dp))
            CashMovePrompt(
                direction = txn.direction,
                onMove = { vm.moveToCash(txn) },
                onKeep = { vm.keepOutOfCash(txn) },
            )
        }
        Spacer(Modifier.height(4.dp))
        // Selectable, so the user can copy a reference or an amount.
        SelectionContainer {
            Column {
                DetailRow("Direction", directionLabel(txn.direction))
                txn.balanceMinor?.let { DetailRow("Balance after", formatCedis(it)) }
                txn.reference?.let { DetailRow("Reference", it) }
                txn.referenceHint?.let { DetailRow("Note from SMS", it) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (txn.direction == "CREDIT") "Where did it come from?" else "Tap a category",
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkSoft,
        )
        Spacer(Modifier.height(8.dp))
        // Only categories for this direction: money in is never "Rent", money out
        // never "Salary". The user's favourites show as chips; the rest are behind
        // "More".
        val offered = categories.forDirection(txn.direction)
        val quick = remember(categories, txn.direction, selected) {
            val head = categories.quickForDirection(txn.direction)
            // Keep a choice made from the sheet visible, or it looks unset.
            if (selected != null && selected !in head) head + selected!! else head
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { cat ->
                CategoryChip(cat, cat == selected, colors.gold) { selected = cat }
            }
            if (offered.any { it !in quick }) {
                CategoryChip("More…", active = false, accent = colors.gold) { picking = true }
            }
        }
        if (picking) {
            CategoryPickerSheet(
                categories = offered,
                current = selected,
                onPick = { selected = it; picking = false },
                onDismiss = { picking = false },
                title = if (txn.direction == "CREDIT") "Where did it come from?" else "Choose a category",
            )
        }

        Spacer(Modifier.height(12.dp))
        LabeledField("Person", person, "Who was this with?") { person = it }
        Spacer(Modifier.height(8.dp))
        LabeledField("Your note", note, "Optional") { note = it }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (ruleRowRequester != null) Modifier.bringIntoViewRequester(ruleRowRequester)
                    else Modifier,
                )
                .spotlight(spotlightRule, RuleRowShape)
                .padding(if (spotlightRule) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Always sort this sender this way",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = alwaysRule, onCheckedChange = { alwaysRule = it })
        }

        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (selected != null) colors.income else colors.surfaceSunken, SaveShape)
                .clickable(enabled = selected != null) {
                    vm.confirm(txn, selected!!, person.ifBlank { null }, note.ifBlank { null }, alwaysRule)
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Save & categorise",
                style = MaterialTheme.typography.titleMedium,
                color = if (selected != null) Color.White else colors.inkMuted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LedgerThemeTokens.colors.inkMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * Space kept free at the bottom of the list for the guide's card during the
 * rule step. The rule switch is scrolled clear of it by the same amount.
 */
private val RULE_STEP_RESERVE = 220.dp

private val ChipShape = RoundedCornerShape(11.dp)
private val RuleRowShape = RoundedCornerShape(12.dp)
private val SaveShape = RoundedCornerShape(14.dp)
private val BannerShape = RoundedCornerShape(12.dp)

@Composable
private fun CategoryChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    // background(colour, shape) draws the same rounded shape as
    // clip(shape).background(colour), with one less layer per chip.
    Box(
        Modifier
            .background(if (active) accent else LedgerThemeTokens.colors.surfaceSunken, ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) Color.White else LedgerThemeTokens.colors.inkSoft,
        )
    }
}

@Composable
private fun LabeledField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LedgerThemeTokens.colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        // PlainField rather than Material3's TextField; see PlainField's doc comment.
        PlainField(value, onChange, placeholder, Modifier.fillMaxWidth())
    }
}
