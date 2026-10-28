package com.ledger.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric unlock for the app lock. Like the PIN, this is a screen lock,
 * separate from the database key, which has to stay available so the SMS
 * receiver can save messages while the phone is locked (see
 * DatabaseKeyProvider).
 *
 * Biometrics are an optional extra on top of the app PIN (see
 * [AppLockManager]). The prompt has a "Use PIN" button, and any error falls
 * back to the PIN screen rather than the phone's own lock.
 */
object BiometricGate {

    /** Whether the device has a biometric the user could use for this app. */
    fun canUseBiometric(context: Context): Boolean {
        val strongOrWeak = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        return BiometricManager.from(context).canAuthenticate(strongOrWeak) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the biometric prompt. [onUsePin] is called when the user picks the
     * PIN instead or biometric unlock fails, so the caller can show the PIN pad.
     */
    fun promptBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onUsePin: () -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // "Use PIN", lockout or cancel: fall back to the PIN.
                    onUsePin()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Ledger")
            .setSubtitle("Use your fingerprint or face")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        prompt.authenticate(info)
    }
}
