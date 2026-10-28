package com.ledger.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ledger.app.ui.screens.BreakdownScreen
import com.ledger.app.ui.screens.BudgetsScreen
import com.ledger.app.ui.screens.CashEntryScreen
import com.ledger.app.ui.screens.CashScreen
import com.ledger.app.ui.screens.CategoriesScreen
import com.ledger.app.ui.screens.CategorySetupScreen
import com.ledger.app.ui.screens.DataScreen
import com.ledger.app.ui.screens.DetailScreen
import com.ledger.app.ui.screens.HistoryScreen
import com.ledger.app.ui.screens.OverviewScreen
import com.ledger.app.ui.screens.ReviewScreen
import com.ledger.app.ui.screens.RulesScreen
import com.ledger.app.ui.screens.SettingsScreen
import com.ledger.app.ui.screens.TransactionSheet
import com.ledger.app.data.db.TransactionEntity
import com.ledger.domain.model.Institution
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.tutorial.TutorialStep
import com.ledger.app.ui.vm.TutorialViewModel

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Overview("overview", "Overview", Icons.Default.Home),
    Review("review", "Review", Icons.Default.Inbox),
    History("history", "History", Icons.Default.Receipt),
    Settings("settings", "Settings", Icons.Default.Settings),
}

/** Built once, not on every navigation. */
private val TAB_ROUTES: Set<String> = Dest.entries.map { it.route }.toSet()

/**
 * Screen transitions are kept short. The default 700ms cross-fade keeps both
 * screens drawn together for most of a second, which makes moving around feel
 * slow.
 */
private const val ENTER_MS = 130
private const val EXIT_MS = 90

/**
 * @param openTab the bottom-tab route a capture notification asked to open, or
 *   null. It is checked against [Dest], so an unknown value is ignored.
 * @param onTabOpened called once [openTab] has been handled, so the caller can
 *   clear it and it doesn't navigate again on the next recomposition.
 */
@Composable
fun LedgerApp(
    openTab: String? = null,
    onTabOpened: () -> Unit = {},
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val onTab = current in TAB_ROUTES

    // Cash entries and rows waiting in review open as a sheet over the list.
    // Everything else opens the full transaction screen.
    var sheetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val openTransaction: (TransactionEntity) -> Unit = { txn ->
        if (txn.needsReview || txn.institution == Institution.CASH.name) sheetId = txn.id
        else nav.navigate("detail/${txn.id}")
    }

    // Tapping a notification takes priority over the guide. The guide's effect
    // below waits while a notification tap is pending.
    LaunchedEffect(openTab) {
        val target = Dest.entries.firstOrNull { it.route == openTab }
        if (target == null) {
            // No tab asked for, or one we don't recognise. Still clear it, or the
            // guide's navigation would stay blocked.
            if (openTab != null) onTabOpened()
            return@LaunchedEffect
        }
        if (current != target.route) nav.switchTab(target.route)
        onTabOpened()
    }

    // The setup guide drives navigation. Each step points at a control on one
    // screen, so the guide takes the user to that screen.
    val pendingStep by tutorialVm.navigateTo.collectAsStateWithLifecycle()
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(pendingStep, current, openTab) {
        // Opening Review from a notification runs this effect again. Without this
        // check the guide would navigate straight back to its own step.
        if (openTab != null) return@LaunchedEffect
        val target = pendingStep?.route ?: return@LaunchedEffect
        if (current != target) nav.switchTab(target)
        tutorialVm.navigationHandled()
    }

    Scaffold(
        // Transparent rather than the page colour. The window background is
        // already the same cream (so there is no white flash at start-up), and
        // painting it again on every screen costs GPU time for nothing.
        containerColor = Color.Transparent,
        bottomBar = {
            if (onTab) NavigationBar(containerColor = LedgerPalette.Surface) {
                Dest.entries.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = { nav.switchTab(d.route) },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LedgerPalette.Spend,
                            selectedTextColor = LedgerPalette.Spend,
                            indicatorColor = LedgerPalette.SurfaceSunken,
                            unselectedIconColor = LedgerPalette.InkMuted,
                            unselectedTextColor = LedgerPalette.InkMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                nav,
                startDestination = Dest.Overview.route,
                enterTransition = { fadeIn(tween(ENTER_MS)) },
                exitTransition = { fadeOut(tween(EXIT_MS)) },
                popEnterTransition = { fadeIn(tween(ENTER_MS)) },
                popExitTransition = { fadeOut(tween(EXIT_MS)) },
            ) {
                composable(Dest.Overview.route) {
                    OverviewScreen(
                        onSeeAll = { nav.switchTab(Dest.History.route) },
                        onPickCategories = { nav.navigate("category-setup") },
                        onReview = { nav.switchTab(Dest.Review.route) },
                        onOpenTransaction = openTransaction,
                        // The guide sends users here when SMS access was declined.
                        onOpenData = { nav.navigate("data") },
                        onOpenBreakdown = { nav.navigate("breakdown") },
                        onOpenBudgets = { nav.navigate("budgets") },
                        onOpenCash = { nav.navigate("cash") },
                    )
                }
                composable(Dest.Review.route) { ReviewScreen() }
                composable(Dest.History.route) {
                    HistoryScreen(
                        onImport = { nav.navigate("data") },
                        onOpen = openTransaction,
                    )
                }
                composable("detail/{id}") { DetailScreen(onBack = { nav.popBackStack() }) }
                composable("data") { DataScreen(onBack = { nav.popBackStack() }) }
                composable("breakdown") { BreakdownScreen(onBack = { nav.popBackStack() }) }
                composable("budgets") {
                    // The guide's budgets step is done once the user visits this screen,
                    // however they leave it (Back included).
                    DisposableEffect(Unit) {
                        onDispose {
                            if (tutorialVm.state.value.showing(TutorialStep.BUDGETS)) {
                                tutorialVm.complete(TutorialStep.BUDGETS)
                            }
                        }
                    }
                    BudgetsScreen(onBack = { nav.popBackStack() })
                }
                composable("cash") {
                    CashScreen(
                        onBack = { nav.popBackStack() },
                        onAdd = { direction -> nav.navigate("cash-entry/$direction") },
                        onOpen = openTransaction,
                    )
                }
                composable("cash-entry/{direction}") { entry ->
                    CashEntryScreen(
                        direction = entry.arguments?.getString("direction") ?: "DEBIT",
                        onDone = { nav.popBackStack() },
                        // The guide's cash step is done once a count is saved.
                        onCounted = {
                            if (tutorial.showing(TutorialStep.CASH_ON_HAND)) {
                                tutorialVm.complete(TutorialStep.CASH_ON_HAND)
                            }
                        },
                        // Withdrawals are moved to cash from their own screen, and History lists
                        // them.
                        onFindWithdrawal = {
                            nav.popBackStack()
                            nav.switchTab(Dest.History.route)
                        },
                    )
                }
                composable(Dest.Settings.route) {
                    SettingsScreen(
                        onManageRules = { nav.navigate("rules") },
                        onManageCategories = { nav.navigate("categories") },
                        onManageBudgets = { nav.navigate("budgets") },
                        onOpenData = { nav.navigate("data") },
                    )
                }
                composable("category-setup") {
                    CategorySetupScreen(
                        onDone = {
                            nav.popBackStack()
                            // The guide's first step is done once this screen is saved, wherever it
                            // was opened from.
                            if (tutorial.showing(TutorialStep.TOP_CATEGORIES)) {
                                tutorialVm.complete(TutorialStep.TOP_CATEGORIES)
                            }
                        },
                    )
                }
                composable("rules") { RulesScreen(onBack = { nav.popBackStack() }) }
                composable("categories") {
                    CategoriesScreen(
                        onBack = { nav.popBackStack() },
                        onPickUsual = { nav.navigate("category-setup") },
                    )
                }
            }
            sheetId?.let { id -> TransactionSheet(id, onDismiss = { sheetId = null }) }
        }
    }
}

/**
 * Switches to a bottom tab without stacking duplicates. `saveState` and
 * `restoreState` keep each tab's state, so coming back to History keeps the
 * user's scroll position.
 */
private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
