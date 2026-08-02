package com.ledger.app

import android.content.Intent
import android.os.Bundle
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
 * FragmentActivity (required by BiometricPrompt). Sets FLAG_SECURE so the app's
 * financial screens are excluded from the Recents preview and blocked from
 * screenshots / screen recording — a one-line hardening worth doing from v1.
 *
 * The sole exception is the `screenshot` build type, which cannot be
 * photographed otherwise. That variant carries its own applicationId suffix, so
 * it is a different package with a different database and cannot reach the real
 * ledger. `BuildConfig.ALLOW_SCREENSHOTS` defaults to false in `defaultConfig`,
 * so release and every future build type keep the flag without opting in.
 *
 * The app-open lock is opt-in ([AppLockManager]): with no PIN configured the app
 * opens straight to the dashboard; a PIN (optionally fronted by biometrics) gates
 * it otherwise.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLock: AppLockManager

    /** Only read by the screenshot-build demo seed; see [DemoSeed]. */
    @Inject lateinit var repository: com.ledger.app.data.repository.LedgerRepository

    /**
     * Tab a capture notification asked for, held until the UI can act on it.
     *
     * State rather than a plain field because the request can arrive while the
     * app is already on screen (via [onNewIntent]) and has to recompose the host
     * when it does. It also has to *survive* the lock screen: with a PIN set, the
     * tap lands on the pad, and the destination is still owed once that clears.
     */
    private var pendingDest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede super.onCreate: this is what swaps the splash theme out for
        // Theme.Ledger and gives us the (deliberately empty) system splash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!BuildConfig.ALLOW_SCREENSHOTS) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        pendingDest = intent?.getStringExtra(CaptureNotifier.EXTRA_DEST)

        // Screenshot builds only, and only when explicitly asked: launching
        // without the extra leaves the app empty, so the empty states can be
        // photographed before the demo data lands. Constant-folded away in
        // release, where ALLOW_SCREENSHOTS is false.
        if (BuildConfig.ALLOW_SCREENSHOTS && intent?.getBooleanExtra(DemoSeed.EXTRA, false) == true) {
            lifecycleScope.launch { DemoSeed.seed(repository) }
        }

        setContent {
            LedgerTheme {
                val mode = remember { appLock.mode() }
                var unlocked by remember { mutableStateOf(mode == LockMode.NONE) }
                // The brand moment runs ahead of the lock, so the PIN pad doesn't
                // appear over a half-struck seal.
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
                        showBiometric = mode == LockMode.BIOMETRIC,
                        onVerifyPin = { appLock.verifyPin(it) },
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
     * A notification tapped while the app is already running.
     *
     * `singleTop` means this instance is reused instead of a second one being
     * created, so `onCreate` does not run again and this is the only place the
     * new destination arrives. `setIntent` keeps `getIntent()` honest for
     * anything that reads it later.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(CaptureNotifier.EXTRA_DEST)?.let { pendingDest = it }
    }
}
