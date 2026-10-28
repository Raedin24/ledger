package com.ledger.app.tutorial

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The setup guide's current state. Only [current] is shown, so the user never
 * sees two guide cards at once.
 */
data class TutorialState(
    val running: Boolean = false,
    val done: Set<TutorialStep> = emptySet(),
    /** True when the user came back through Settings → Replay setup guide. */
    val resumed: Boolean = false,
) {
    /** The first unfinished step, or null when all are done. */
    val current: TutorialStep? =
        if (!running) null else TutorialStep.entries.firstOrNull { it !in done }

    /** Whether [step] is the one its screen should show right now. */
    fun showing(step: TutorialStep): Boolean = running && current == step

    /** 1-based position for the "step N of M" label. */
    val position: Int get() = current?.number ?: 0
}

/**
 * Saves the setup guide's progress and decides which step comes next.
 *
 * The guide starts once, on first launch. After that the user is in control:
 * skipping moves to the next step, closing it pauses the whole guide, and
 * Settings can replay it from the first unfinished step. Finished steps are
 * never repeated.
 *
 * Progress is kept in SharedPreferences rather than the encrypted database.
 * It isn't financial data, and it must be readable before the database opens.
 */
@Singleton
class TutorialManager @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    /**
     * The screen the guide wants the user on, because the current step points at
     * something there. The host navigates and then calls [navigationHandled].
     */
    private val _navigateTo = MutableStateFlow<TutorialStep?>(null)
    val navigateTo: StateFlow<TutorialStep?> = _navigateTo.asStateFlow()

    fun navigationHandled() { _navigateTo.value = null }

    private fun read(): TutorialState {
        val done = prefs.getStringSet(KEY_DONE, emptySet()).orEmpty()
            .mapNotNull { name -> runCatching { TutorialStep.valueOf(name) }.getOrNull() }
            .toSet()
        return TutorialState(
            running = prefs.getBoolean(KEY_RUNNING, false),
            done = done,
            resumed = prefs.getBoolean(KEY_RESUMED, false),
        )
    }

    private fun write(state: TutorialState) {
        prefs.edit()
            .putBoolean(KEY_RUNNING, state.running)
            .putStringSet(KEY_DONE, state.done.map { it.name }.toSet())
            .putBoolean(KEY_RESUMED, state.resumed)
            .apply()
        _state.value = state
    }

    /**
     * Starts the guide on first launch. It never offers itself again after that;
     * the user can bring it back with [replay].
     *
     * The exception is an update that adds steps. Someone who finished the guide
     * is shown just the new steps, once. Someone who closed it partway is left
     * alone.
     */
    fun startIfFirstRun() {
        val all = TutorialStep.entries.map { it.name }.toSet()
        if (!prefs.getBoolean(KEY_OFFERED, false)) {
            prefs.edit().putBoolean(KEY_OFFERED, true).putStringSet(KEY_KNOWN, all).apply()
            write(_state.value.copy(running = true, resumed = false))
            return
        }
        // Installs from before known steps were recorded had the legacy set.
        val known = prefs.getStringSet(KEY_KNOWN, null) ?: LEGACY_STEPS
        if (known.containsAll(all)) return
        prefs.edit().putStringSet(KEY_KNOWN, all).apply()
        val state = _state.value
        val finished = TutorialStep.entries
            .filter { it.name in known }
            .all { it in state.done }
        if (!state.running && finished) write(state.copy(running = true, resumed = false))
    }

    /** The user did what this step asked. */
    fun complete(step: TutorialStep) = advance(step)

    /**
     * "Skip": move on without doing it. The step still counts as done, so the
     * guide keeps going.
     */
    fun skip(step: TutorialStep) = advance(step)

    private fun advance(step: TutorialStep) {
        val next = _state.value.copy(done = _state.value.done + step, resumed = false)
        // Finishing the last step ends the guide.
        val settled = if (next.current == null) next.copy(running = false) else next
        write(settled)
        // Take the user to the screen of the next step.
        _navigateTo.value = settled.current
    }

    /** The close button: pause the whole guide, keeping progress for a replay. */
    fun dismiss() {
        _navigateTo.value = null
        write(_state.value.copy(running = false, resumed = false))
    }

    /**
     * Settings → "Replay setup guide". Resumes at the first unfinished step, or
     * starts over if all are done. It also navigates there, so the tap visibly
     * does something.
     */
    fun replay() {
        val allDone = _state.value.done.containsAll(TutorialStep.entries)
        val next = _state.value.copy(
            running = true,
            done = if (allDone) emptySet() else _state.value.done,
            resumed = true,
        )
        write(next)
        _navigateTo.value = next.current
    }

    private companion object {
        const val PREFS = "ledger_tutorial_prefs"
        const val KEY_RUNNING = "running"
        const val KEY_DONE = "done_steps"
        const val KEY_RESUMED = "resumed"
        const val KEY_OFFERED = "offered"
        /** Step names that existed when the guide was last offered. */
        const val KEY_KNOWN = "known_steps"

        /** The steps that existed before [KEY_KNOWN] was recorded. */
        val LEGACY_STEPS = setOf("IMPORT_PAST", "REVIEW_RULE", "OWN_ACCOUNTS", "HISTORY_WINDOW", "APP_LOCK")
    }
}
