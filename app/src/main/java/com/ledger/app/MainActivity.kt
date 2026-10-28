package com.ledger.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.ledger.app.notifications.CaptureNotifier
import com.ledger.app.security.AppLockManager
import com.ledger.app.security.BiometricGate
import com.ledger.app.security.LockMode
import com.ledger.app.ui.LedgerApp
import com.ledger.app.ui.screens.LockScreen
import com.ledger.app.ui.screens.SplashScreen
import com.ledger.app.ui.theme.LedgerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The app's only Activity. It is a FragmentActivity because BiometricPrompt
 * needs one.
 *
 * It sets FLAG_SECURE, which hides the app from the Recents preview and blocks
 * screenshots and screen recording. The `screenshot` build type is the only
 * exception. It installs as a separate package with its own database, so it
 * can never show the real ledger. `BuildConfig.ALLOW_SCREENSHOTS` is false by
 * default, so release and any new build type keep the flag.
 *
 * The app lock is optional ([AppLockManager]). With no PIN set, the app opens
 * straight to the dashboard. With a PIN (and optionally biometrics), the lock
 * shows on a cold start and again when the app returns from the background
 * after a short grace period (see [onStart]).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLock: AppLockManager

    @Inject lateinit var keyProvider: com.ledger.app.data.crypto.DatabaseKeyProvider

    /** Only read by the screenshot-build demo seed; see [DemoSeed]. */
    @Inject lateinit var repository: com.ledger.app.data.repository.LedgerRepository

    /**
     * The tab a capture notification asked to open, held until the UI can act on it.
     *
     * It is Compose state because the request can arrive while the app is already
     * showing (through [onNewIntent]). It also has to wait behind the lock screen:
     * with a PIN set, the tap opens the PIN pad first.
     */
    private var pendingDest by mutableStateOf<String?>(null)

    /**
     * The current lock setting. Read again on every [onStart], so turning the lock
     * on in Settings takes effect without a restart.
     */
    private var lockMode by mutableStateOf(LockMode.NONE)

    /** False while the PIN pad needs to be shown. */
    private var unlocked by mutableStateOf(true)

    /**
     * [SystemClock.elapsedRealtime] when the app last went to the background, or 0.
     * This clock can't be changed by the user, so changing the device time can't
     * stretch the grace period.
     */
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come before super.onCreate. It swaps the splash theme for
        // Theme.Ledger and keeps the system splash empty.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!BuildConfig.ALLOW_SCREENSHOTS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        pendingDest = intent?.getStringExtra(CaptureNotifier.EXTRA_DEST)
        lockMode = appLock.mode()
        unlocked = lockMode == LockMode.NONE

        // Screenshot builds only, and only when asked for with the intent extra.
        // Launching without it leaves the app empty, so the empty states can be
        // photographed first. In release ALLOW_SCREENSHOTS is false, so this is
        // compiled out.
        if (BuildConfig.ALLOW_SCREENSHOTS && intent?.getBooleanExtra(DemoSeed.EXTRA, false) == true) {
            lifecycleScope.launch { DemoSeed.seed(repository) }
        }
        // Not on a recreate (rotation, theme change), which would run it twice.
        val benchCount = if (BuildConfig.ALLOW_SCREENSHOTS && savedInstanceState == null) {
            intent?.getIntExtra(DemoSeed.EXTRA_BENCH, 0) ?: 0
        } else 0
        if (benchCount > 0) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) { DemoSeed.bench(repository, benchCount) }
        }

        // Check this before anything touches the database. Without the key, every
        // screen would fail on its first query.
        val dataAvailable = keyProvider.canUnlock()

        setContent {
            if (!dataAvailable) {
                LedgerTheme { com.ledger.app.ui.screens.KeyLostScreen() }
                return@setContent
            }
            LedgerTheme {
                // The splash animation finishes before the lock shows, so the PIN pad
                // doesn't appear over it.
                var branded by remember { mutableStateOf(false) }
                if (!branded) {
                    SplashScreen(onFinished = { branded = true })
                } else if (unlocked) {
                    LedgerApp(
                        openTab = pendingDest,
                        onTabOpened = { pendingDest = null },
                    )
                } else {
                    LockScreen(
                        pinLength = appLock.pinLength(),
                        showBiometric = lockMode == LockMode.BIOMETRIC,
                        onVerifyPin = { appLock.verifyPin(it) },
                        lockoutRemainingMillis = { appLock.lockoutRemainingMillis() },
                        onUnlocked = { unlocked = true },
                        onBiometric = {
                            BiometricGate.promptBiometric(
                                activity = this@MainActivity,
                                onSuccess = { unlocked = true },
                                onUsePin = { /* stay on the PIN pad */ },
                            )
                        },
                    )
                }
            }
        }
    }

    /**
     * Turns the lock back on if the app has been in the background long enough.
     *
     * The grace period matters because picking a backup file or answering a call
     * also leaves this Activity, and the PIN pad shouldn't appear every time. A
     * minute is enough to come back from a system picker, and short enough that a
     * phone put down and picked up by someone else is locked.
     */
    override fun onStart() {
        super.onStart()
        lockMode = appLock.mode()
        if (lockMode == LockMode.NONE) {
            unlocked = true
        } else if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > RELOCK_AFTER_MILLIS) {
            unlocked = false
        }
        leftAt = 0L
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    /**
     * A notification tapped while the app is already running.
     *
     * The Activity is `singleTop`, so this instance is reused and `onCreate`
     * doesn't run again. This is the only place the new destination arrives.
     * `setIntent` keeps `getIntent()` correct for anything that reads it later.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(CaptureNotifier.EXTRA_DEST)?.let { pendingDest = it }
    }

    private companion object {
        /** How long the app may sit in the background before the lock re-arms. */
        const val RELOCK_AFTER_MILLIS = 60_000L
    }
}
