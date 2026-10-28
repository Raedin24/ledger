package com.ledger.app.ui.screens

import com.ledger.app.data.db.TransactionEntity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.data.budget.BudgetLevel
import com.ledger.app.data.budget.BudgetStatus
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
    onPickCategories: () -> Unit = {},
    onReview: () -> Unit = {},
    onOpenTransaction: (TransactionEntity) -> Unit = {},
    onOpenData: () -> Unit = {},
    onOpenBreakdown: () -> Unit = {},
    onOpenBudgets: () -> Unit = {},
    onOpenCash: () -> Unit = {},
    vm: OverviewViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    val colors = LedgerThemeTokens.colors
    val context = LocalContext.current

    // Read the clock once per composition, not on every scroll frame, since the
    // header is inside the LazyColumn.
    val monthLabel = remember {
        val now = LocalDate.now()
        now.month.name.lowercase().replaceFirstChar { it.uppercase() } + " ${now.year}"
    }
    val greetingText = remember { greeting() }

    // Starts the guide on first launch (or for new steps after an update).
    LaunchedEffect(Unit) { tutorialVm.startIfFirstRun() }

    val showCategoriesStep = tutorial.showing(TutorialStep.TOP_CATEGORIES) && state.loaded

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            // Shown only while RECEIVE_SMS isn't granted.
            CapturePermissionBanner(Modifier.padding(bottom = 14.dp))
            Text(state.periodLabel.ifEmpty { monthLabel }, style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
            Text(greetingText, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(6.dp))
        }

        // Nothing loaded yet: opening the encrypted database takes a moment. Show
        // placeholder shapes, so the screen doesn't look stuck or empty.
        if (!state.loaded) {
            item { OverviewSkeleton(Modifier.padding(top = 4.dp)) }
            item { Spacer(Modifier.height(90.dp)) }
            return@LazyColumn
        }

        // First run: show the welcome instead of the dashboard.
        if (state.isFirstRun) {
            item {
                EmptyState(
                    title = "Welcome to Ledger",
                    message = "MoMo, GhanaPay and Telecel Cash alerts are recorded as they arrive.",
                    modifier = Modifier.padding(top = 20.dp),
                    titleStyle = MaterialTheme.typography.headlineMedium,
                    actionLabel = "Import past alerts",
                    onAction = onOpenData,
                    footnote = { PrivacyFootnote("Nothing leaves this device") },
                    illustration = { EnvelopeIllustration(EmptyBadge.Check) },
                )
            }
            item { CashCard(state.cashMinor, onOpenCash) }
            // Room for the guide's card without covering the action.
            item { Spacer(Modifier.height(if (showCategoriesStep) 210.dp else 90.dp)) }
            return@LazyColumn
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Same wording as everywhere else; see directionLabel.
                MoneyStat(MONEY_OUT.uppercase(), formatCedis(state.spentMinor), "this month", colors.spend, Modifier.weight(1f))
                MoneyStat(MONEY_IN.uppercase(), formatCedis(state.receivedMinor), "this month", colors.income, Modifier.weight(1f))
            }
        }

        if (state.loadFailed) {
            item { EmptyHint("Some figures couldn't be loaded.") }
        }

        if (state.reviewCount > 0) {
            item { ReviewBanner(state.reviewCount, onReview) }
        }

        item { CashCard(state.cashMinor, onOpenCash) }

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
                // Shown apart from Money In, so carried-over savings never look like
                // income earned this month.
                state.carriedMinor?.let { carried ->
                    Spacer(Modifier.height(8.dp))
                    SavingsLine("Carried over", carried)
                    SavingsLine("Balance", carried + state.savingsMinor, bold = true)
                }
            }
        }

        // Cash-flow timeline: daily spending across the month, with the average
        // and the change from last month.
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
                    elapsed = state.elapsedDays,
                )
            }
        }

        // Budgets sit above Top spending, because a limit about to be passed
        // matters more. The card only appears once a budget exists.
        if (state.budgets.isNotEmpty()) {
            item { SectionHeader("Budgets", action = "Manage", onAction = onOpenBudgets) }
            item {
                LedgerCard {
                    state.budgets.take(BUDGETS_ON_DASHBOARD).forEachIndexed { i, budget ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        BudgetLine(budget)
                    }
                    // The list is worst first, so the hidden ones are usually fine. Count
                    // the ones that aren't, rather than assuming.
                    val hidden = state.budgets.drop(BUDGETS_ON_DASHBOARD)
                    if (hidden.isNotEmpty()) {
                        val needAttention = hidden.count { it.level != BudgetLevel.OK }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (needAttention == 0) "${hidden.size} more within budget"
                            else "${hidden.size} more · $needAttention over or close",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (needAttention == 0) colors.inkMuted else colors.spend,
                            modifier = Modifier.clickable(onClick = onOpenBudgets),
                        )
                    }
                }
            }
        }

        // "All categories" opens the full breakdown behind the top five.
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
                TransactionRow(txn, onClick = { onOpenTransaction(txn) })
            }
        }

        item { SectionHeader("Recent activity", action = "See all", onAction = onSeeAll) }
        if (state.recent.isEmpty()) {
            item { EmptyHint("Your transactions will appear here.") }
        } else {
            items(state.recent, key = { "recent-${it.id}" }, contentType = { "txn-row" }) { txn ->
                TransactionRow(txn, onClick = { onOpenTransaction(txn) })
            }
        }
        // Room for the guide's card over a full dashboard.
        item { Spacer(Modifier.height(if (showCategoriesStep) 210.dp else 90.dp)) }
    }

        CoachScrim(showCategoriesStep)
        CoachMarkHost(
            visible = showCategoriesStep,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            val step = TutorialStep.TOP_CATEGORIES
            val tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL
            CoachMark(
                label = coachLabel(step, tone),
                title = step.title,
                body = step.body,
                primaryLabel = step.primary,
                // Completed when the picker is saved (see LedgerApp).
                onPrimary = onPickCategories,
                secondaryLabel = step.secondary,
                onSecondary = { tutorialVm.skip(step) },
                onDismiss = tutorialVm::dismiss,
                tone = tone,
                beak = CoachBeak.None,
                step = step.number,
            )
        }
    }
}

@Composable
private fun SavingsLine(label: String, minor: Long, bold: Boolean = false) {
    val colors = LedgerThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
        Text(
            formatCedis(minor),
            style = MaterialTheme.typography.bodyMedium,
            color = if (minor >= 0) LedgerPalette.Ink else colors.spend,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** Cash on hand, and the way into the cash ledger. */
@Composable
private fun CashCard(balanceMinor: Long, onClick: () -> Unit) {
    val colors = LedgerThemeTokens.colors
    LedgerCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Cash on hand", style = MaterialTheme.typography.titleMedium, color = colors.inkSoft, modifier = Modifier.weight(1f))
            Text(
                formatCedis(balanceMinor),
                style = MaterialTheme.typography.titleMedium,
                color = if (balanceMinor < 0) colors.spend else LedgerPalette.Ink,
                fontWeight = FontWeight.Bold,
            )
            Text("  ›", style = MaterialTheme.typography.titleMedium, color = colors.inkMuted)
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
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerPalette.GoldDeep)
    }
}

/**
 * How many budgets the dashboard shows. They are sorted worst first, so three
 * usually covers everything that needs attention.
 */
private const val BUDGETS_ON_DASHBOARD = 3

/**
 * One budget on the dashboard. The bar is coloured by how close the limit is,
 * not by category, because that is what this card is for.
 */
@Composable
private fun BudgetLine(status: BudgetStatus) {
    val accent = when (status.level) {
        BudgetLevel.OK -> LedgerPalette.Income
        BudgetLevel.NEAR -> LedgerPalette.Gold
        BudgetLevel.OVER -> LedgerPalette.Spend
    }
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CategoryMark(status.category)
                Text(status.category, style = MaterialTheme.typography.bodyLarge, color = LedgerPalette.Ink)
            }
            Text(
                if (status.level == BudgetLevel.OVER) {
                    "${formatCedis(-status.remainingMinor)} over"
                } else {
                    "${formatCedis(status.remainingMinor)} left"
                },
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(7.dp))
        CategoryBar(status.fraction, accent)
    }
}

/** One row of the Top spending list: the category and amount over a proportional bar. */
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
