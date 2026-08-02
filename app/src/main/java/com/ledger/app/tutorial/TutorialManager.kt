package com.ledger.app.tutorial

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the guide is currently doing. [current] is the one beat allowed to show;
 * everything else stays quiet, so the user never meets two coach-marks at once.
 */
data class TutorialState(
    val running: Boolean = false,
    val done: Set<TutorialStep> = emptySet(),
    /** True when the user came back via Settings → Replay setup guide. */
    val resumed: Boolean = false,
) {
    /** First unfinished beat, or null once every beat is behind us. */
    val current: TutorialStep? =
        if (!running) null else TutorialStep.entries.firstOrNull { it !in done }

    /** Whether [step] is the beat its screen should render right now. */
    fun showing(step: TutorialStep): Boolean = running && current == step

    /** 1-based position for the "step N of 6" label. */
    val position: Int get() = current?.number ?: 0
}

/**
 * Persistence and sequencing for the first-run guide.
 *
 * The guide starts itself once, on first launch, and thereafter is entirely
 * under the user's control: skipping advances to the next beat, dismissing parks
 * the whole thing, and Settings can replay it from the first unfinished beat.
 * Completed beats are never repeated.
 *
 * Deliberately SharedPreferences rather than the encrypted DB — this is UI
 * bookkeeping, not financial data, and it must be readable before the database
 * has finished opening.
 */
@Singleton
class TutorialManager @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<TutorialState> = _state.asStateFlow()

    /**
     * The screen the guide wants the user on, because that's where the current
     * beat's anchor lives. The host navigates and then calls [navigationHandled].
     *
     * Without this the guide dead-stops after the first beat: every later beat is
     * bound to a screen, and nothing was taking the user there.
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
     * Offers the guide on a genuine first run. Idempotent, and never re-offers
     * itself once the user has started, finished, or dismissed it — coming back
     * is their call, via [replay].
     */
    fun startIfFirstRun() {
        if (prefs.getBoolean(KEY_OFFERED, false)) return
        prefs.edit().putBoolean(KEY_OFFERED, true).apply()
        write(_state.value.copy(running = true, resumed = false))
    }

    /** The user did the thing this beat taught. */
    fun complete(step: TutorialStep) = advance(step)

    /** "Skip" — move on without doing it. The beat still counts as behind us,
     *  so the guide progresses rather than stalling on an ignored step. */
    fun skip(step: TutorialStep) = advance(step)

    private fun advance(step: TutorialStep) {
        val next = _state.value.copy(done = _state.value.done + step, resumed = false)
        // Finishing the last beat ends the guide rather than leaving it armed.
        val settled = if (next.current == null) next.copy(running = false) else next
        write(settled)
        // Take the user to wherever the next beat lives.
        _navigateTo.value = settled.current
    }

    /** The ✕ — park the whole guide, keeping progress for a later replay. */
    fun dismiss() {
        _navigateTo.value = null
        write(_state.value.copy(running = false, resumed = false))
    }

    /** Settings → "Replay setup guide". Re-enters at the first unfinished beat;
     *  once everything is done, starts the whole thing over. Navigating is the
     *  point — otherwise tapping it from Settings looks like nothing happened. */
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

    /** Whether the guide has anything left to show — drives the Settings row. */
    fun hasUnfinished(): Boolean = !_state.value.done.containsAll(TutorialStep.entries)

    private companion object {
        const val PREFS = "ledger_tutorial_prefs"
        const val KEY_RUNNING = "running"
        const val KEY_DONE = "done_steps"
        const val KEY_RESUMED = "resumed"
        const val KEY_OFFERED = "offered"
    }
}
