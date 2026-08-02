package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.security.CapturePermission
import com.ledger.app.tutorial.CaptureDeniedCopy
import com.ledger.app.tutorial.TutorialStep
import com.ledger.app.ui.components.CapturePermissionBanner
import com.ledger.app.ui.components.CategoryBar
import com.ledger.app.ui.components.CategoryMark
import com.ledger.app.ui.components.CoachBeak
import com.ledger.app.ui.components.CoachMark
import com.ledger.app.ui.components.CoachMarkHost
import com.ledger.app.ui.components.CoachScrim
import com.ledger.app.ui.components.CoachTone
import com.ledger.app.ui.components.EmptyBadge
import com.ledger.app.ui.components.EmptyState
import com.ledger.app.ui.components.EnvelopeIllustration
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.MONEY_IN
import com.ledger.app.ui.components.MONEY_OUT
import com.ledger.app.ui.components.coachLabel
import com.ledger.app.ui.components.MiniBarChart
import com.ledger.app.ui.components.MonthDeltaBadge
import com.ledger.app.ui.components.OverviewSkeleton
import com.ledger.app.ui.components.PrivacyFootnote
import com.ledger.app.ui.components.SectionHeader
import com.ledger.app.ui.components.categoryVisual
import com.ledger.app.ui.components.formatCedis
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.theme.LedgerThemeTokens
import com.ledger.app.ui.vm.OverviewViewModel
import com.ledger.app.ui.vm.TutorialViewModel
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun OverviewScreen(
    onSeeAll: () -> Unit,
    onAddSender: () -> Unit = {},
    onReview: () -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {},
    onOpenData: () -> Unit = {},
    onOpenBreakdown: () -> Unit = {},
    vm: OverviewViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    val colors = LedgerThemeTokens.colors
    val context = LocalContext.current

    // Wall-clock reads, done once per composition rather than on every scroll
    // frame — the header is inside the LazyColumn's content lambda.
    val monthLabel = remember {
        val now = LocalDate.now()
        now.month.name.lowercase().replaceFirstChar { it.uppercase() } + " ${now.year}"
    }
    val greetingText = remember { greeting() }

    // The guide offers itself once, on a genuine first launch.
    LaunchedEffect(Unit) { tutorialVm.startIfFirstRun() }

    // Beat 1 only makes sense while the welcome is on screen — that's where its
    // anchor lives. Capture state is re-read on resume so granting permission
    // swaps the amber "no SMS access" branch back to the ordinary beat.
    var captureOn by remember { mutableStateOf(CapturePermission.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) captureOn = CapturePermission.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Beat 1 shows on any loaded dashboard, not only an empty one.
    //
    // Gating it on the first-run welcome meant replaying the guide with data
    // already captured auto-skipped it, so the walkthrough appeared to start at
    // step 2 and step 1 was never seen. Its lesson — add a sender and the ledger
    // fills itself — applies just as well to a second sender, so the beat stays
    // and only its anchor changes: it spotlights the welcome CTA when that CTA is
    // on screen, and stands on its own when it isn't.
    val showBeat1 = tutorial.showing(TutorialStep.ADD_SENDER) && state.loaded

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            // Shows only while RECEIVE_SMS is not granted; renders nothing (and
            // costs no layout) once capture is live.
            CapturePermissionBanner(Modifier.padding(bottom = 14.dp))
            Text(monthLabel, style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
            Text(greetingText, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(6.dp))
        }

        // Nothing is known yet (a cold SQLCipher open takes a beat, and longer
        // still on a freshly-backfilled ledger). Show the shape of the dashboard
        // that's coming: the header alone read as a hang, and a dashboard full of
        // zeros read as an empty ledger instead of the welcome.
        if (!state.loaded) {
            item { OverviewSkeleton(Modifier.padding(top = 4.dp)) }
            item { Spacer(Modifier.height(90.dp)) }
            return@LazyColumn
        }

        // First run — replace the whole dashboard body with the welcome state.
        if (state.isFirstRun) {
            item {
                EmptyState(
                    title = "Welcome to Ledger",
                    message = "Add a trusted sender and your spending fills in automatically as SMS alerts arrive — all on this phone.",
                    modifier = Modifier.padding(top = 20.dp),
                    titleStyle = MaterialTheme.typography.headlineMedium,
                    actionLabel = "Add your first sender",
                    onAction = onAddSender,
                    spotlightAction = showBeat1 && captureOn,
                    footnote = { PrivacyFootnote("Nothing leaves this device") },
                    illustration = { EnvelopeIllustration(EmptyBadge.Check) },
                )
            }
            // Room for the coach-mark to sit without covering the action.
            item { Spacer(Modifier.height(if (showBeat1) 210.dp else 90.dp)) }
            return@LazyColumn
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Same vocabulary as every other surface — see directionLabel.
                MoneyStat(MONEY_OUT.uppercase(), formatCedis(state.spentMinor), "this month", colors.spend, Modifier.weight(1f))
                MoneyStat(MONEY_IN.uppercase(), formatCedis(state.receivedMinor), "this month", colors.income, Modifier.weight(1f))
            }
        }

        if (state.reviewCount > 0) {
            item { ReviewBanner(state.reviewCount, onReview) }
        }

        item {
            LedgerCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Savings this month", style = MaterialTheme.typography.titleMedium, color = colors.inkSoft)
                    Text(
                        formatCedis(state.savingsMinor),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.savingsMinor >= 0) colors.income else colors.spend,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Cash-flow timeline — daily spend across the month, with the running
        // average and a month-over-month change badge.
        item {
            LedgerCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Daily spend", style = MaterialTheme.typography.titleMedium, color = colors.inkSoft)
                        Text(
                            "Avg ${formatCedis(state.avgDailyMinor)}/day",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMuted,
                        )
                    }
                    state.spendChangePct?.let { pct ->
                        Column(horizontalAlignment = Alignment.End) {
                            MonthDeltaBadge(pct)
                            Text(
                                "vs last month",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MiniBarChart(
                    values = state.dailySpendSeries,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    barColor = colors.spend,
                    trackColor = colors.surfaceSunken,
                )
            }
        }

        // The top five always provokes "and the rest?" — so the header answers it.
        item { SectionHeader("Top spending", action = "All categories", onAction = onOpenBreakdown) }
        if (state.topCategories.isEmpty()) {
            item { EmptyHint("No categorised spending yet this month.") }
        } else {
            item {
                val max = state.topCategories.first().totalMinor.coerceAtLeast(1)
                LedgerCard {
                    state.topCategories.forEachIndexed { i, cat ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        TopCategoryRow(
                            label = cat.label,
                            amount = formatCedis(cat.totalMinor),
                            fraction = cat.totalMinor.toFloat() / max,
                        )
                    }
                }
            }
        }

        if (state.largestExpenses.isNotEmpty()) {
            item { SectionHeader("Largest this month") }
            items(state.largestExpenses, key = { "largest-${it.id}" }, contentType = { "txn-row" }) { txn ->
                TransactionRow(txn, onClick = { onOpenTransaction(txn.id) })
            }
        }

        item { SectionHeader("Recent activity", action = "See all", onAction = onSeeAll) }
        if (state.recent.isEmpty()) {
            item { EmptyHint("Your transactions will appear here.") }
        } else {
            items(state.recent, key = { "recent-${it.id}" }, contentType = { "txn-row" }) { txn ->
                TransactionRow(txn, onClick = { onOpenTransaction(txn.id) })
            }
        }
        // Room for beat 1's coach-mark when it's shown over a populated dashboard.
        item { Spacer(Modifier.height(if (showBeat1) 210.dp else 90.dp)) }
    }

        // Beat 1. Two faces: the ordinary "add a sender" prompt, and — when the
        // user has declined SMS access — an amber reroute to importing, so
        // declining never dead-ends the guide.
        CoachScrim(showBeat1)
        CoachMarkHost(
            visible = showBeat1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            val step = TutorialStep.ADD_SENDER
            if (captureOn) {
                CoachMark(
                    label = coachLabel(step, if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL),
                    title = step.title,
                    body = step.body,
                    primaryLabel = step.primary,
                    onPrimary = { tutorialVm.complete(step); onAddSender() },
                    secondaryLabel = step.secondary,
                    onSecondary = { tutorialVm.skip(step) },
                    onDismiss = tutorialVm::dismiss,
                    tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL,
                    // Only point at something when there is something to point at.
                    beak = if (state.isFirstRun) CoachBeak.Top(0.5f) else CoachBeak.None,
                    step = step.number,
                )
            } else {
                CoachMark(
                    label = CaptureDeniedCopy.LABEL,
                    title = CaptureDeniedCopy.TITLE,
                    body = CaptureDeniedCopy.BODY,
                    primaryLabel = CaptureDeniedCopy.PRIMARY,
                    onPrimary = { tutorialVm.complete(step); onOpenData() },
                    secondaryLabel = CaptureDeniedCopy.SECONDARY,
                    onSecondary = { tutorialVm.skip(step) },
                    onDismiss = tutorialVm::dismiss,
                    tone = CoachTone.WARNING,
                    beak = CoachBeak.None,
                )
            }
        }
    }
}

/** The "N to review" banner that deep-links into the Review queue. */
@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF5ECD6))
            .border(1.dp, LedgerPalette.Gold.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).background(LedgerPalette.ChipBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = MaterialTheme.typography.titleMedium, color = LedgerPalette.GoldDeep, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$count to review",
                style = MaterialTheme.typography.titleMedium,
                color = LedgerPalette.GoldDeep,
                fontWeight = FontWeight.SemiBold,
            )
            Text("New senders waiting to be sorted", style = MaterialTheme.typography.labelSmall, color = LedgerPalette.Gold)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerPalette.GoldDeep)
    }
}

/** One row of the Top-spending list: category mark + amount, over a proportional bar. */
@Composable
private fun TopCategoryRow(label: String, amount: String, fraction: Float) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CategoryMark(label)
                Text(label, style = MaterialTheme.typography.bodyLarge, color = LedgerPalette.Ink)
            }
            Text(amount, style = MaterialTheme.typography.titleMedium, color = LedgerPalette.Ink, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(7.dp))
        CategoryBar(fraction, categoryVisual(label).accent)
    }
}

@Composable
private fun MoneyStat(label: String, amount: String, sub: String, accent: Color, modifier: Modifier) {
    LedgerCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LedgerThemeTokens.colors.inkMuted)
        Spacer(Modifier.height(6.dp))
        Text(amount, style = MaterialTheme.typography.headlineMedium, color = accent)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = LedgerThemeTokens.colors.inkMuted)
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
