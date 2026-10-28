package com.ledger.app.tutorial

/**
 * The steps of the setup guide. Each step points at one control on one screen.
 *
 * The text lives here rather than in the screens, so the guide reads in one
 * voice: short, an instruction first, and always a way out. Steps run in order,
 * and the guide shows the first unfinished one on its screen.
 *
 * [APP_LOCK] is optional, so it isn't counted in "step N of M".
 */
enum class TutorialStep(
    /** Position in the counted steps; 0 for the optional last step. */
    val number: Int,
    /**
     * The screen this step lives on. The guide navigates there when the step
     * becomes current.
     */
    val route: String,
    val title: String,
    val body: String,
    val primary: String,
    val secondary: String?,
) {
    TOP_CATEGORIES(
        number = 1,
        route = "overview",
        title = "Pick your top categories",
        body = "They show first when you sort a transaction.",
        primary = "Pick",
        secondary = "Skip",
    ),
    IMPORT_PAST(
        number = 2,
        route = "data",
        title = "Import your past alerts",
        body = "Reads your old MoMo, GhanaPay and Telecel Cash messages once.",
        // Shown while no import is running. During an import the screen hides
        // both buttons and the import dialog handles the actions.
        primary = "Import now",
        secondary = "Skip",
    ),
    REVIEW_RULE(
        number = 3,
        route = "review",
        title = "Sort once",
        body = "Turn this on to file every message from this sender the same way.",
        primary = "Try it",
        secondary = "Skip",
    ),
    OWN_ACCOUNTS(
        number = 4,
        route = "settings",
        title = "Add your own numbers",
        body = "Transfers between them won't count as spending.",
        primary = "Add mine",
        secondary = "Skip",
    ),
    MONTH_START(
        number = 5,
        route = "settings",
        title = "When does your month start?",
        body = "Pick your payday, and months run from it.",
        primary = "Pick a day",
        secondary = "Keep the 1st",
    ),
    BUDGETS(
        number = 6,
        route = "settings",
        title = "Set a budget",
        body = "A monthly limit per category. You'll get one alert as you near it.",
        primary = "Set one",
        secondary = "Skip",
    ),
    CASH_ON_HAND(
        number = 7,
        route = "cash",
        title = "Count your cash",
        body = "Enter what's in your pocket now. It won't count as income.",
        primary = "Count it",
        secondary = "Skip",
    ),
    HISTORY_WINDOW(
        number = 8,
        route = "history",
        title = "Showing the last 90 days",
        body = "Tap \"All time\" to see everything.",
        primary = "Done",
        secondary = "Show all time",
    ),
    APP_LOCK(
        number = 0,
        route = "settings",
        title = "Add a lock?",
        body = "Your data is already encrypted. A PIN adds a lock screen.",
        primary = "Set a PIN",
        secondary = "Finish",
    ),
    ;

    val counted: Boolean get() = number > 0

    companion object {
        /** How many steps "step N of M" counts. */
        val COUNTED = entries.count { it.counted }
    }
}

