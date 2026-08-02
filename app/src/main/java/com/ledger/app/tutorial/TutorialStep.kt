package com.ledger.app.tutorial

/**
 * The first-run guide, one beat per teaching moment.
 *
 * Copy is held here rather than in the screens because the guide's voice is the
 * point: every beat leads with the *benefit*, never the mechanic ("Sort once,
 * the rest file themselves" — not "tap the switch"), and every beat offers a way
 * out. Beats run in order; the guide shows the first unfinished one, on the
 * screen that owns it.
 *
 * [APP_LOCK] is an epilogue — genuinely optional, so it sits outside the "of 6"
 * count rather than implying the user is unfinished without it.
 */
enum class TutorialStep(
    /** Position in the counted sequence; 0 for the uncounted epilogue. */
    val number: Int,
    /**
     * The screen that owns this beat. The guide navigates here when the beat
     * becomes current — without that, a sequential screen-bound guide stalls the
     * moment the user is standing somewhere else.
     */
    val route: String,
    val title: String,
    val body: String,
    val primary: String,
    val secondary: String?,
) {
    ADD_SENDER(
        number = 1,
        route = "overview",
        title = "Add one sender, your ledger fills itself",
        body = "It reads the alerts you already get — no typing in transactions.",
        primary = "Add a sender",
        secondary = "Skip",
    ),
    DETECT_FORMAT(
        number = 2,
        route = "onboarding",
        title = "One message teaches the whole format",
        body = "Paste any alert from this sender. From then on Ledger reads them for you.",
        primary = "Got it",
        secondary = "Skip for now",
    ),
    IMPORT_PAST(
        number = 3,
        route = "data",
        title = "Your history, filled in from day one",
        body = "This reads old alerts once. Leave it running — it's quick and never leaves the phone.",
        // Offered while idle. Once the scan is running the screen drops both, so
        // the mark is purely informational and the modal owns the actions.
        primary = "Import now",
        secondary = "Skip",
    ),
    REVIEW_RULE(
        number = 4,
        route = "review",
        title = "Sort once, the rest file themselves",
        body = "Flip this on and every other message from this sender clears too — and next time, no review at all.",
        primary = "Try it",
        secondary = "Skip",
    ),
    OWN_ACCOUNTS(
        number = 5,
        route = "settings",
        title = "Stop counting your own money as spending",
        body = "Add your numbers and moving cash between them won't inflate your totals — only real fees show.",
        primary = "Add mine",
        secondary = "Skip",
    ),
    HISTORY_WINDOW(
        number = 6,
        route = "history",
        title = "You're seeing the last 90 days",
        body = "That's the default so History loads fast — older imports aren't missing. Tap \"All time\" to see everything.",
        primary = "Done",
        secondary = "Show all time",
    ),
    APP_LOCK(
        number = 0,
        route = "settings",
        title = "Want a lock? Add one. Or don't.",
        body = "Your data's already encrypted on-device. A PIN just adds a door. Biometrics is a layer on top.",
        primary = "Set a PIN",
        secondary = "Finish",
    ),
    ;

    val counted: Boolean get() = number > 0

    companion object {
        /** How many beats the progress indicator counts against. */
        val COUNTED = entries.count { it.counted }
    }
}

/**
 * Beat 1 has a second face. A user who declines SMS access must not hit a dead
 * end, so the same slot reroutes to importing rather than insisting on capture.
 */
object CaptureDeniedCopy {
    const val LABEL = "CAPTURE IS OFF"
    const val TITLE = "No SMS access? Ledger still works."
    // Promised "or add transactions by hand" until 2026-08-02. There is no manual
    // entry — ingest() and importBackup() are the only ways a transaction reaches
    // the database — so the offer sent anyone who declined capture looking for a
    // button that was never built.
    const val BODY = "Import your past messages once, or restore a backup. Turn capture on whenever you like."
    const val PRIMARY = "Import instead"
    const val SECONDARY = "Turn on capture"
}
