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
import com.ledger.app.ui.screens.CategoriesScreen
import com.ledger.app.ui.screens.CategorySetupScreen
import com.ledger.app.ui.screens.DataScreen
import com.ledger.app.ui.screens.DetailScreen
import com.ledger.app.ui.screens.HistoryScreen
import com.ledger.app.ui.screens.OnboardingSenderScreen
import com.ledger.app.ui.screens.OverviewScreen
import com.ledger.app.ui.screens.ReviewScreen
import com.ledger.app.ui.screens.RulesScreen
import com.ledger.app.ui.screens.SettingsScreen
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.vm.TutorialViewModel

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Overview("overview", "Overview", Icons.Default.Home),
    Review("review", "Review", Icons.Default.Inbox),
    History("history", "History", Icons.Default.Receipt),
    Settings("settings", "Settings", Icons.Default.Settings),
}

/** Hoisted: this was rebuilt on every recomposition of the host, which is every
 *  navigation. */
private val TAB_ROUTES: Set<String> = Dest.entries.map { it.route }.toSet()

/**
 * Screen transition, deliberately brisk.
 *
 * Navigation-Compose defaults to a 700ms cross-fade, which means both screens are
 * composed, laid out and drawn together for most of a second on every tab switch
 * — the largest single contributor to the app feeling heavy to move around in. A
 * short fade reads as immediate and cuts the overlap to a fraction.
 */
private const val ENTER_MS = 130
private const val EXIT_MS = 90

/**
 * @param openTab bottom-tab route a capture notification asked to open, or null.
 *   Matched against [Dest] rather than navigated to blindly, so a stale or
 *   unrecognised extra is ignored instead of throwing.
 * @param onTabOpened called once [openTab] has been honoured, so the caller can
 *   clear it and a later recomposition doesn't navigate all over again.
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

    // Tapping a notification is a direct instruction and outranks the guide —
    // see the tutorial effect below, which stands aside while one is pending.
    LaunchedEffect(openTab) {
        val target = Dest.entries.firstOrNull { it.route == openTab }
        if (target == null) {
            // Nothing asked for, or an extra we don't recognise: still clear it,
            // or a bad value would block the guide's navigation indefinitely.
            if (openTab != null) onTabOpened()
            return@LaunchedEffect
        }
        if (current != target.route) nav.switchTab(target.route)
        onTabOpened()
    }

    // The first-run guide drives navigation: each beat is anchored to a control
    // on one screen, so the guide has to take the user there — otherwise it
    // stalls silently the moment they're standing somewhere else.
    val pendingStep by tutorialVm.navigateTo.collectAsStateWithLifecycle()
    LaunchedEffect(pendingStep, current, openTab) {
        // Landing on Review from a notification re-fires this effect (current
        // changed); without this the guide would immediately steer back to its
        // own beat and the tap would look like it did nothing.
        if (openTab != null) return@LaunchedEffect
        val target = pendingStep?.route ?: return@LaunchedEffect
        if (current != target) nav.switchTab(target)
        tutorialVm.navigationHandled()
    }

    Scaffold(
        // Transparent, not the page colour: `android:windowBackground` is already
        // this exact cream (it has to be, to avoid a white flash before Compose
        // starts). Painting it again here, and a third time in the Box below, meant
        // filling 2.6M pixels three times before a single card was drawn. On this
        // hardware the GPU was taking ~13ms of a 16.7ms frame on *every* screen,
        // including ones with almost nothing on them — the app was fill-rate bound,
        // not composition bound.
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
                        onAddSender = { nav.navigate("onboarding") },
                        onReview = { nav.switchTab(Dest.Review.route) },
                        onOpenTransaction = { id -> nav.navigate("detail/$id") },
                        // The guide's "no SMS access" branch reroutes here.
                        onOpenData = { nav.navigate("data") },
                        onOpenBreakdown = { nav.navigate("breakdown") },
                    )
                }
                composable(Dest.Review.route) { ReviewScreen() }
                composable(Dest.History.route) {
                    HistoryScreen(
                        onAddSender = { nav.navigate("onboarding") },
                        onOpen = { id -> nav.navigate("detail/$id") },
                    )
                }
                composable("detail/{id}") { DetailScreen(onBack = { nav.popBackStack() }) }
                composable("data") { DataScreen(onBack = { nav.popBackStack() }) }
                composable("breakdown") { BreakdownScreen(onBack = { nav.popBackStack() }) }
                composable(Dest.Settings.route) {
                    SettingsScreen(
                        onAddSender = { nav.navigate("onboarding") },
                        onManageRules = { nav.navigate("rules") },
                        onManageCategories = { nav.navigate("categories") },
                        onOpenData = { nav.navigate("data") },
                    )
                }
                composable("onboarding") {
                    OnboardingSenderScreen(
                        // Straight on to the category shortlist the first time,
                        // replacing this screen on the stack: backing out of it
                        // should land where "Add a sender" was tapped, not back
                        // in a sender that has already been added.
                        onDone = { pickCategories ->
                            if (pickCategories) {
                                nav.navigate("category-setup") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            } else {
                                nav.popBackStack()
                            }
                        },
                        onSkip = { nav.popBackStack() },
                    )
                }
                composable("category-setup") { CategorySetupScreen(onDone = { nav.popBackStack() }) }
                composable("rules") { RulesScreen(onBack = { nav.popBackStack() }) }
                composable("categories") {
                    CategoriesScreen(
                        onBack = { nav.popBackStack() },
                        onPickUsual = { nav.navigate("category-setup") },
                    )
                }
            }
        }
    }
}

/**
 * Switches to a bottom-tab destination without stacking duplicates.
 *
 * `saveState`/`restoreState` are the point: without them every tab was rebuilt
 * from scratch on each visit, so leaving History and coming back dropped the user
 * at the top of the list and paid to lay out the first screenful all over again.
 */
private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
