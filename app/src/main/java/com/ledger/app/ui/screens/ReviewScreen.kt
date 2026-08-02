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
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.theme.LedgerThemeTokens
import com.ledger.app.ui.vm.ReviewViewModel
import com.ledger.app.ui.vm.TutorialViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full stamp: a review decision often hinges on telling two same-day
 *  transactions from the same person apart. */
private val reviewStamp =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(
    vm: ReviewViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    // Null while the first read is still in flight; the screen must not claim
    // "All caught up" before it knows whether anything is waiting.
    val queue by vm.queue.collectAsStateWithLifecycle()
    val items = queue.orEmpty()
    val loading = queue == null
    val message by vm.message.collectAsStateWithLifecycle()
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val colors = LedgerThemeTokens.colors

    // Beat 4 — the payoff. Only meaningful with something in the queue to sort.
    val step = TutorialStep.REVIEW_RULE
    // "Try it" tucks the card away so the user can work, but leaves the beat
    // open: it completes for real when the rule sweep actually fires.
    var beat4Tucked by remember { mutableStateOf(false) }
    val beat4Live = tutorial.showing(step) && items.isNotEmpty()
    val showBeat4 = beat4Live && !beat4Tucked
    // Saving with a rule is the completion moment; the sweep banner proves it.
    LaunchedEffect(message) { if (message != null && tutorial.showing(step)) tutorialVm.complete(step) }
    // Nothing to sort means this beat has no anchor and nothing to demonstrate.
    // Step past it rather than parking the guide on an empty screen — but only
    // once the queue is actually known to be empty, not merely unread.
    LaunchedEffect(loading, items.isEmpty(), tutorial.current) {
        if (tutorial.showing(step) && !loading && items.isEmpty()) tutorialVm.skip(step)
    }

    // The control beat 4 teaches is the rule switch, which sits near the bottom of
    // a card half a screen tall — so it starts out below the fold *and* behind the
    // coach-mark. Scrolling to the card's item index isn't enough (that puts the
    // card's top at the viewport top and the switch off the bottom), so the row
    // asks to be brought into view itself, with a rect padded by the coach-mark's
    // footprint so the scroll clears it rather than tucking the row underneath.
    val ruleRow = remember { BringIntoViewRequester() }
    val coachReservePx = with(LocalDensity.current) { BEAT4_RESERVE.toPx() }
    LaunchedEffect(showBeat4, items.firstOrNull()?.id) {
        if (!showBeat4) return@LaunchedEffect
        // One frame so the card is composed and positioned before we ask.
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
                    else -> "${items.size} to review · new senders waiting to be sorted"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        }

        // A new rule sweeps the rest of the backlog from that sender — say so,
        // otherwise the queue just shrinks unexplained.
        message?.let { text ->
            item { RuleSweepBanner(text, onDismiss = vm::clearMessage) }
        }
        if (loading) {
            item { ReviewSkeleton(Modifier.padding(top = 4.dp)) }
        } else if (items.isEmpty()) {
            item {
                EmptyState(
                    title = "All caught up",
                    message = "Nothing waiting. New unrecognised transactions will appear here.",
                    modifier = Modifier.padding(top = 40.dp),
                    illustration = { CheckCircleIllustration() },
                )
            }
        } else {
            items(items, key = { it.id }, contentType = { "review-card" }) { txn ->
                val spotlit = beat4Live && txn.id == items.first().id
                ReviewCard(
                    txn = txn,
                    vm = vm,
                    categories = categories,
                    spotlightRule = spotlit,
                    ruleRowRequester = ruleRow.takeIf { spotlit },
                )
            }
        }
        item { Spacer(Modifier.height(if (showBeat4) BEAT4_RESERVE else 90.dp)) }
    }

        CoachScrim(showBeat4)
        CoachMarkHost(
            visible = showBeat4,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            CoachMark(
                label = coachLabel(step, if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL),
                title = step.title,
                body = step.body,
                primaryLabel = step.primary,
                onPrimary = { beat4Tucked = true },
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
    // Collected once by the screen and passed down: every card reads the same
    // flow, and a collector per card meant N subscriptions to one StateFlow.
    categories: CategoryOptions,
    spotlightRule: Boolean = false,
    /** Set only on the card beat 4 is pointing at, so the guide can scroll its
     *  rule switch into view. Null on every other card. */
    ruleRowRequester: BringIntoViewRequester? = null,
) {
    val colors = LedgerThemeTokens.colors
    var selected by remember { mutableStateOf<String?>(null) }
    var person by remember { mutableStateOf(txn.person ?: "") }
    var note by remember { mutableStateOf("") }
    // Off by default. Creating a standing rule is a bigger commitment than
    // filing one transaction — it silently sorts everything that sender sends
    // from then on — so it should be something the user reaches for, not
    // something that happens because they didn't notice a switch.
    var alwaysRule by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    // Both format a string; the stamp also parses a pattern and reads the zone.
    // Keyed to the row's own data so a card recomputes when it's reused but not
    // once per recomposition — a card recomposes on every keystroke in its two
    // fields, and this was running for both of them.
    val stamp = remember(txn.institution, txn.occurredAt) {
        "${institutionLabel(txn.institution)} · ${reviewStamp.format(Instant.ofEpochMilli(txn.occurredAt))}"
    }
    val amount = remember(txn.amountMinor, txn.direction) {
        (if (txn.direction == "CREDIT") "+" else "−") + formatCedis(txn.amountMinor)
    }

    LedgerCard {
        // Parsed details header. The amount is laid out first and never shrinks —
        // a long counterparty ellipsises instead of pushing the figure off-screen.
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
        // Which wallet this landed in, and when. Without these two facts the same
        // counterparty appearing twice — once per account, or twice on one day —
        // is impossible to tell apart from the History row you're comparing it to.
        Text(
            stamp,
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
            modifier = Modifier.padding(top = 3.dp),
        )
        Spacer(Modifier.height(4.dp))
        // The parsed facts are worth copying out (a reference to paste into a
        // dispute, an amount to check), so this block is selectable.
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
        // Only this direction's labels: a credit is never "Rent", a debit never
        // "Salary". Capped at QUICK_PICKS because the full set runs to twenty-odd
        // and laying every chip out in every queued card was the single heaviest
        // thing on this screen; the rest are one tap away.
        val offered = categories.forDirection(txn.direction)
        val quick = remember(offered, selected) {
            val head = offered.take(QUICK_PICKS)
            // Keep a choice made from the dialog visible, or it reads as unset.
            if (selected != null && selected !in head) head + selected!! else head
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { cat ->
                CategoryChip(cat, cat == selected, colors.gold) { selected = cat }
            }
            if (offered.size > QUICK_PICKS) {
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
        LabeledField("Your note", note, "Optional context, searchable later") { note = it }

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
            Column(Modifier.weight(1f)) {
                Text("Always sort this sender this way", style = MaterialTheme.typography.titleMedium)
                Text("Creates a rule so it's automatic next time", style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
            }
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

/** How many category chips a card lays out inline before deferring to the
 *  picker. Enough to cover the everyday cases without paying to measure the
 *  whole set in every queued card. */
private const val QUICK_PICKS = 8

/** Space kept clear at the bottom of the list for beat 4's coach-mark, and the
 *  amount the spotlit row is scrolled clear of it by. One constant so the two
 *  can't drift apart. */
private val BEAT4_RESERVE = 220.dp

private val ChipShape = RoundedCornerShape(11.dp)
private val RuleRowShape = RoundedCornerShape(12.dp)
private val SaveShape = RoundedCornerShape(14.dp)
private val BannerShape = RoundedCornerShape(12.dp)

@Composable
private fun CategoryChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    // background(colour, shape) rather than clip(shape).background(colour): same
    // rounded rect, one fewer render layer per chip, and a queued card carries nine.
    // Measured as a wash on this device — kept because it's the cheaper form, not
    // because it fixed anything.
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
        // PlainField, not Material3's TextField — see its doc comment. Two of these
        // per queued card was the bulk of the cost of scrolling this screen.
        PlainField(value, onChange, placeholder, Modifier.fillMaxWidth())
    }
}
